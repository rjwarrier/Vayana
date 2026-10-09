package com.vayana.format.epub

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
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

/** Opt-in cleanup of explicitly marked Gutenberg sections; never guesses from story text. */
object GutenbergCleanup {
    fun clean(source: File, destination: File): Int {
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
                    for (entry in ordered) {
                        val bytes = zip.getInputStream(entry).use { input ->
                            val buffer = ByteArrayOutputStream()
                            val chunk = ByteArray(8192)
                            while (true) {
                                val count = input.read(chunk)
                                if (count < 0) break
                                expanded += count
                                require(expanded <= 128L * 1024 * 1024) { "EPUB is too large to clean" }
                                buffer.write(chunk, 0, count)
                            }
                            buffer.toByteArray()
                        }
                        val content = if (entry.name.substringAfterLast('.').lowercase() in setOf("xhtml", "html", "htm")) {
                            cleanChapter(bytes).also { removed += it.second }.first
                        } else bytes
                        val target = ZipEntry(entry.name)
                        if (entry.name == "mimetype") {
                            require(content.toString(Charsets.UTF_8) == "application/epub+zip")
                            target.method = ZipEntry.STORED
                            target.size = content.size.toLong()
                            target.crc = CRC32().apply { update(content) }.value
                        }
                        output.putNextEntry(target)
                        output.write(content)
                        output.closeEntry()
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
        if (!text.contains("pg-header") && !text.contains("pg-footer")) return bytes to 0
        require(!text.contains("<!ENTITY", ignoreCase = true)) { "Unsupported EPUB entity declaration" }
        val builder = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
        }.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        val document = builder.parse(ByteArrayInputStream(bytes))
        var removed = 0
        fun visit(parent: Node) {
            var child = parent.firstChild
            while (child != null) {
                val next = child.nextSibling
                val element = child as? Element
                if (element != null && element.localName in setOf("div", "section") &&
                    element.getAttribute("id") in setOf("pg-header", "pg-footer")) {
                    parent.removeChild(child)
                    removed++
                } else visit(child)
                child = next
            }
        }
        visit(document)
        if (removed == 0) return bytes to 0
        val output = ByteArrayOutputStream()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(output))
        return output.toByteArray() to removed
    }
}
