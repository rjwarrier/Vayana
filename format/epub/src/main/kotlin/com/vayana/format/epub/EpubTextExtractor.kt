package com.vayana.format.epub

import android.text.Html
import android.util.Xml
import java.io.File
import java.net.URI
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser

data class EpubTextChapter(val href: String, val title: String, val text: String)

/** Reads only spine documents, with per-entry and total expansion limits for untrusted EPUBs. */
object EpubTextExtractor {
    fun extract(file: File): List<EpubTextChapter> = ZipFile(file).use { zip ->
        fun entry(path: String): String {
            val item = zip.getEntry(path) ?: error("Missing EPUB entry: $path")
            require(item.size <= MaxEntryBytes)
            return zip.getInputStream(item).use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MaxEntryBytes)
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
        }
        val container = Xml.newPullParser().apply { setInput(entry("META-INF/container.xml").reader()) }
        var opf: String? = null
        while (container.eventType != XmlPullParser.END_DOCUMENT) {
            if (container.eventType == XmlPullParser.START_TAG && container.name == "rootfile") {
                opf = container.getAttributeValue(null, "full-path"); break
            }
            container.next()
        }
        val opfPath = requireNotNull(opf)
        val parser = Xml.newPullParser().apply { setInput(entry(opfPath).reader()) }
        val manifest = mutableMapOf<String, String>()
        val spine = mutableListOf<String>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) when (parser.name) {
                "item" -> {
                    val id = parser.getAttributeValue(null, "id")
                    val href = parser.getAttributeValue(null, "href")
                    val type = parser.getAttributeValue(null, "media-type")
                    if (id != null && href != null && type in setOf("application/xhtml+xml", "text/html")) manifest[id] = href
                }
                "itemref" -> if (parser.getAttributeValue(null, "linear") != "no") {
                    parser.getAttributeValue(null, "idref")?.let(spine::add)
                }
            }
            require(spine.size <= MaxChapters)
            parser.next()
        }
        var expanded = 0
        spine.mapNotNull { id ->
            val href = manifest[id] ?: return@mapNotNull null
            val path = URI(null, null, opfPath, null).resolve(href).normalize().path
            require(!path.startsWith("/") && !path.startsWith("../"))
            val html = entry(path)
            expanded += html.length
            require(expanded <= MaxBookChars)
            val visible = html.replace(HiddenElements, " ")
            val titleHtml = Heading.find(visible)?.groupValues?.get(1)
                ?: Title.find(html)?.groupValues?.get(1)
            val title = titleHtml?.let(::plain).orEmpty()
            val text = plain(visible)
            EpubTextChapter(path, title.ifBlank { path.substringAfterLast('/') }, text).takeIf { text.isNotBlank() }
        }
    }

    private fun plain(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
        .replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()
    private val HiddenElements = Regex("<(head|script|style)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val Heading = Regex("<h[1-6]\\b[^>]*>(.*?)</h[1-6]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val Title = Regex("<title\\b[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private const val MaxEntryBytes = 4 * 1024 * 1024
    private const val MaxBookChars = 32 * 1024 * 1024
    private const val MaxChapters = 3000
}
