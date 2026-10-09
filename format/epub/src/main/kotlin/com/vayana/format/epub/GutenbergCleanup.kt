package com.vayana.format.epub

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/** Opt-in cleanup of marked Gutenberg sections and unambiguous prose artifacts. */
object GutenbergCleanup {
    @JvmOverloads
    fun clean(
        source: File,
        destination: File,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
        checkCancelled: () -> Unit = {},
    ): Int {
        require(source.canonicalFile != destination.canonicalFile)
        var removed = 0
        try {
            ZipFile(source).use { zip ->
                ZipOutputStream(destination.outputStream().buffered()).use { output ->
                    val entries = zip.entries().asSequence().toList()
                    require(entries.map { it.name }.distinct().size == entries.size) { "Duplicate EPUB entries" }
                    val mime = zip.getEntry("mimetype") ?: error("Missing EPUB mimetype")
                    val ordered = listOf(mime) + entries.filter { it.name != "mimetype" }
                    var expanded = 0L
                    val chunk = ByteArray(8192)
                    fun transfer(input: InputStream, sink: OutputStream) {
                        while (true) {
                            checkCancelled()
                            val count = input.read(chunk)
                            if (count < 0) break
                            expanded += count
                            require(expanded <= 128L * 1024 * 1024) { "EPUB is too large to clean" }
                            sink.write(chunk, 0, count)
                        }
                    }
                    onProgress(0, ordered.size)
                    for ((index, entry) in ordered.withIndex()) {
                        checkCancelled()
                        val chapter = entry.name.substringAfterLast('.').lowercase() in ChapterExtensions
                        val content = if (chapter || entry.name == "mimetype") {
                            val bytes = ByteArrayOutputStream().also { buffer ->
                                zip.getInputStream(entry).use { transfer(it, buffer) }
                            }.toByteArray()
                            if (chapter) cleanChapter(bytes).also { removed += it.second }.first else bytes
                        } else null
                        val target = ZipEntry(entry.name)
                        if (entry.name == "mimetype") {
                            require(content != null && content.toString(Charsets.UTF_8) == "application/epub+zip")
                            target.method = ZipEntry.STORED
                            target.size = content.size.toLong()
                            target.crc = CRC32().apply { update(content) }.value
                        }
                        output.putNextEntry(target)
                        if (content != null) output.write(content)
                        else zip.getInputStream(entry).use { transfer(it, output) }
                        output.closeEntry()
                        onProgress(index + 1, ordered.size)
                    }
                }
            }
            return removed
        } catch (failure: Throwable) {
            destination.delete()
            throw failure
        }
    }

    private fun cleanChapter(bytes: ByteArray): Pair<ByteArray, Int> {
        // Internal entity declarations are unnecessary for this operation and can expand exponentially.
        val text = bytes.toString(Charsets.UTF_8)
        if (!text.contains("pg-header") && !text.contains("pg-footer") && !text.contains("img_images_") &&
            !text.hasTextArtifacts() && !TextArtifactEntity.containsMatchIn(text)) return bytes to 0
        require(!text.contains("<!ENTITY", ignoreCase = true)) { "Unsupported EPUB entity declaration" }
        val builder = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
        }.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        val document = builder.parse(ByteArrayInputStream(bytes))
        var removed = 0
        fun visit(parent: Node, prose: Boolean = false, protected: Boolean = false) {
            var child = parent.firstChild
            while (child != null) {
                val next = child.nextSibling
                val element = child as? Element
                val boilerplate = element != null && element.localName in BoilerplateTags &&
                    element.getAttribute("id") in BoilerplateIds
                // Gutenberg's no-images editions replace illustrations with filename-like labels.
                // Remove an entire otherwise-empty figure, but retain real images and captions.
                val emptyIllustration = element?.isEmptyIllustration() == true
                if (boilerplate || emptyIllustration || element?.isImagePlaceholder() == true) {
                    parent.removeChild(child)
                    removed++
                } else if (element != null) {
                    val preserve = protected || element.protectsTextFormatting()
                    visit(child, prose || element.localName == "p", preserve)
                } else if (child.nodeType == Node.TEXT_NODE && prose && !protected) {
                    val cleaned = cleanProseText(child.nodeValue)
                    if (cleaned != child.nodeValue) {
                        child.nodeValue = cleaned
                        removed++
                    }
                }
                child = next
            }
        }
        visit(document)
        if (removed == 0) return bytes to 0
        val output = ByteArrayOutputStream()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(output))
        return output.toByteArray() to removed
    }

    private fun Element.isFigure(): Boolean = localName == "div" &&
        FigureClass.containsMatchIn(getAttribute("class"))

    private fun Element.isEmptyIllustration(): Boolean {
        if (!isFigure()) return false
        var placeholder: Element? = null
        var child = firstChild
        while (child != null) {
            if (!(child.nodeType == Node.TEXT_NODE && child.textContent.isBlank())) {
                if (placeholder != null || child !is Element || !child.isImagePlaceholder()) return false
                placeholder = child
            }
            child = child.nextSibling
        }
        return placeholder != null
    }

    private fun Element.isImagePlaceholder(): Boolean = localName == "span" &&
        (parentNode as? Element)?.isFigure() == true &&
        ImagePlaceholderId.matches(getAttribute("id")) &&
        (0 until childNodes.length).all { childNodes.item(it).nodeType == Node.TEXT_NODE } &&
        ImagePlaceholderText.matches(textContent.trim())

    private val ImagePlaceholderId = Regex("img_images_\\d{3,}[A-Za-z]{1,3}\\.(?:jpg|jpeg|png|gif)", RegexOption.IGNORE_CASE)
    private val ImagePlaceholderText = Regex("\\d{3,}[A-Za-z]{1,3}")

    private fun Element.protectsTextFormatting(): Boolean =
        localName in ProtectedTextTags ||
            getAttributeNS("http://www.w3.org/XML/1998/namespace", "space") == "preserve" ||
            getAttribute("style").isNotBlank() ||
            (localName == "p" && getAttribute("class").isNotBlank()) ||
            ProtectedTextClass.containsMatchIn(getAttribute("class"))

    internal fun cleanProseText(text: String): String {
        if (!text.hasTextArtifacts()) return text
        var cleaned = SoftHyphenBreak.replace(text, "")
        cleaned = InsideWordArtifacts.replace(cleaned, "")
        return RepeatedNonbreakingSpace.replace(cleaned, " ")
    }

    private fun String.hasTextArtifacts(): Boolean = any { it in "\u00ad\u200b\ufeff\u00a0" }

    private val ChapterExtensions = setOf("xhtml", "html", "htm")
    private val BoilerplateTags = setOf("div", "section")
    private val BoilerplateIds = setOf("pg-header", "pg-footer")
    private val ProtectedTextTags = setOf("pre", "code", "kbd", "samp", "table", "svg", "math", "script", "style", "textarea")
    private val FigureClass = Regex("(?:^|\\s)fig(?:\\s|$)")

    private val SoftHyphenBreak = Regex("(?<=[A-Za-z])\u00ad[ \\t\\r\\n]*(?=[A-Za-z])")
    private val InsideWordArtifacts = Regex("(?<=[A-Za-z])[\u00ad\u200b\ufeff]+(?=[A-Za-z])")
    private val RepeatedNonbreakingSpace = Regex("(?<=[A-Za-z])\u00a0{2,}(?=[A-Za-z])")
    private val ProtectedTextClass = Regex("(?:poem|poetry|verse|stanza|letter|asterism|signature|center|right)", RegexOption.IGNORE_CASE)
    private val TextArtifactEntity = Regex("&#(?:0*(?:173|160|8203|65279)|x0*(?:ad|a0|200b|feff));", RegexOption.IGNORE_CASE)
}
