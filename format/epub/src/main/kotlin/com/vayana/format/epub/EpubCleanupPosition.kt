package com.vayana.format.epub

import java.io.File
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/** Best-effort conversion of the original CFI into a passage locator before its DOM changes. */
object EpubCleanupPosition {
    fun capture(source: File, locator: String?, checkCancelled: () -> Unit = {}): String? {
        if (locator.isNullOrBlank()) return null
        if (locator.startsWith("text:search:")) return locator
        if (!locator.startsWith("epubcfi(") || !locator.endsWith(')')) return null
        try {
            checkCancelled()
            // Assertions can contain escaped brackets and commas; remove them before splitting a range.
            val cfi = withoutAssertions(locator.removePrefix("epubcfi(").dropLast(1))
            val packagePath = cfi.substringBefore('!')
            val spineStep = packagePath.substringAfterLast('/').toIntOrNull() ?: return null
            if (spineStep < 2 || spineStep % 2 != 0) return null
            val parts = cfi.substringAfter('!', "").split(',')
            val path = parts[0] + if (parts.size == 3) parts[1] else ""
            val steps = Step.findAll(path).toList()
            if (steps.isEmpty() || steps.joinToString("") { it.value } != path) return null
            ZipFile(source).use { zip ->
                val builder = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    isExpandEntityReferences = false
                }.newDocumentBuilder()
                builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
                fun document(entry: String): org.w3c.dom.Document {
                    checkCancelled()
                    val item = zip.getEntry(entry) ?: error("Missing EPUB entry")
                    val text = zip.getInputStream(item).use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            checkCancelled()
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 8 * 1024 * 1024)
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    }
                    require(!text.contains("<!ENTITY", ignoreCase = true))
                    return builder.parse(InputSource(StringReader(text)))
                }
                val opf = (document("META-INF/container.xml").getElementsByTagNameNS("*", "rootfile")
                    .item(0) as? Element)?.getAttribute("full-path") ?: return null
                val packageDoc = document(opf)
                val id = (packageDoc.getElementsByTagNameNS("*", "itemref").item(spineStep / 2 - 1) as? Element)
                    ?.getAttribute("idref") ?: return null
                val items = packageDoc.getElementsByTagNameNS("*", "item")
                val href = (0 until items.length).asSequence().map { items.item(it) as Element }
                    .firstOrNull { it.getAttribute("id") == id }?.getAttribute("href") ?: return null
                val chapter = URI(null, null, opf, null).resolve(href).normalize().path
                require(!chapter.startsWith('/') && !chapter.startsWith("../"))
                val chapterDocument = document(chapter)
                var node: Node = chapterDocument.documentElement
                var offset = 0
                for (step in steps) {
                    val index = step.groupValues[1].toInt()
                    offset = step.groupValues[2].toIntOrNull() ?: 0
                    if (index % 2 == 0) {
                        val children = (0 until node.childNodes.length).asSequence().map { node.childNodes.item(it) }
                            .filterIsInstance<Element>().toList()
                        node = children.getOrNull(index / 2 - 1) ?: return null
                    } else {
                        var elements = 0
                        var found: Node? = null
                        var child = node.firstChild
                        while (child != null) {
                            if (child is Element) elements++
                            else if (child.nodeType in TextNodeTypes && elements == (index - 1) / 2) {
                                if (offset <= child.nodeValue.length) { found = child; break }
                                offset -= child.nodeValue.length
                            }
                            child = child.nextSibling
                        }
                        node = found ?: return null
                    }
                }
                var ancestor: Node? = node
                var paragraph: Node? = null
                while (ancestor != null) {
                    if (ancestor is Element) {
                        if (ancestor.getAttribute("id") in setOf("pg-header", "pg-footer") ||
                            ancestor.getAttribute("id").startsWith("img_images_")) return null
                        if (paragraph == null && ancestor.localName == "p") paragraph = ancestor
                    }
                    ancestor = ancestor.parentNode
                }
                val passage = StringBuilder()
                var reached = false
                fun visit(current: Node) {
                    checkCancelled()
                    if (passage.length >= 240) return
                    if (current === node) reached = true
                    if (current.nodeType in TextNodeTypes && reached) {
                        val text = current.nodeValue
                        passage.append((if (current === node) text.drop(offset) else text).take(240 - passage.length))
                    } else {
                        var child = current.firstChild
                        while (child != null) { visit(child); child = child.nextSibling }
                    }
                }
                visit(paragraph ?: if (node is Element) node else node.parentNode)
                val quote = GutenbergCleanup.cleanProseText(passage.toString()).replace(Whitespace, " ").trim().take(200)
                if (quote.length < 12) return null
                // The reader matches without accents, punctuation or case. Reject duplicates
                // under those same rules rather than restoring the first similar occurrence.
                val chapterText = normalizeMatch(chapterDocument.documentElement.textContent)
                val matchingQuote = normalizeMatch(quote)
                if (matchingQuote.length < 12) return null
                val firstMatch = chapterText.indexOf(matchingQuote)
                if (firstMatch < 0 || firstMatch != chapterText.lastIndexOf(matchingQuote)) return null
                fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
                return "text:search:${encode(chapter)}:${encode(quote)}"
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            return null
        }
    }

    private fun withoutAssertions(cfi: String): String {
        val output = StringBuilder()
        var assertion = false
        var escaped = false
        for (char in cfi) {
            if (escaped) { escaped = false; continue }
            if (char == '^') { escaped = true; continue }
            if (char == '[') assertion = true
            else if (char == ']') assertion = false
            else if (!assertion) output.append(char)
        }
        return output.toString()
    }

    private val Step = Regex("/(\\d+)(?::(\\d+))?")
    private fun normalizeMatch(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD)
        .lowercase(Locale.ROOT).replace(NonAlphanumeric, "")
    private val NonAlphanumeric = Regex("[^\\p{L}\\p{N}]")
    private val Whitespace = Regex("[\\s\u00a0]+")
    private val TextNodeTypes = setOf(Node.TEXT_NODE, Node.CDATA_SECTION_NODE)
}
