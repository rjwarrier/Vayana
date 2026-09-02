package com.vayana.format.epub

import android.util.Xml
import java.io.File
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser

/**
 * Minimal EPUB2/EPUB3 metadata + cover extractor: reads `META-INF/container.xml` to locate the
 * OPF package document, then reads `<metadata>`/`<manifest>` from it. No third-party EPUB
 * library — the app is 100% offline and this is a small, well-defined format to hand-parse
 * (PROMPT2appbuild.md §3: "open EPUB natively").
 */
object EpubParser {

    fun parse(file: File): EpubMetadata {
        ZipFile(file).use { zip ->
            val opfPath = findOpfPath(zip)
            val opfEntry = zip.getEntry(opfPath) ?: error("EPUB missing OPF at $opfPath")
            val opfDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")

            var title: String? = null
            var author: String? = null
            var description: String? = null
            var coverId: String? = null
            var coverHref: String? = null
            val manifestHrefById = mutableMapOf<String, String>()
            val manifestPropertiesById = mutableMapOf<String, String>()

            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(zip.getInputStream(opfEntry), null)
            }

            var currentTag: String? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        when (parser.name) {
                            "meta" -> {
                                if (parser.getAttributeValue(null, "name") == "cover") {
                                    coverId = parser.getAttributeValue(null, "content")
                                }
                            }
                            "item" -> {
                                val id = parser.getAttributeValue(null, "id")
                                val href = parser.getAttributeValue(null, "href")
                                val properties = parser.getAttributeValue(null, "properties")
                                if (id != null && href != null) manifestHrefById[id] = href
                                if (id != null && properties != null) manifestPropertiesById[id] = properties
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "title" -> if (title == null) title = text
                                "creator" -> if (author == null) author = text
                                "description" -> if (description == null) description = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> currentTag = null
                }
                event = parser.next()
            }

            coverHref = manifestPropertiesById.entries
                .firstOrNull { (_, properties) -> "cover-image" in properties.split(' ') }
                ?.key
                ?.let { manifestHrefById[it] }
                ?: coverId?.let { manifestHrefById[it] }

            val coverBytes = coverHref?.let { href ->
                val coverPath = if (opfDir.isEmpty()) href else "$opfDir/$href"
                zip.getEntry(coverPath)?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } }
            }

            return EpubMetadata(
                title = title ?: file.nameWithoutExtension,
                author = author,
                description = description,
                coverBytes = coverBytes,
            )
        }
    }

    private fun findOpfPath(zip: ZipFile): String {
        val containerEntry = zip.getEntry("META-INF/container.xml") ?: error("Not a valid EPUB: missing container.xml")
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(zip.getInputStream(containerEntry), null)
        }
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "rootfile") {
                parser.getAttributeValue(null, "full-path")?.let { return it }
            }
            event = parser.next()
        }
        error("Not a valid EPUB: no rootfile in container.xml")
    }
}
