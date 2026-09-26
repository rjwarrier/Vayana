package com.vayana.format.epub

import android.util.Xml
import java.io.File
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser

/**
 * Minimal EPUB2/EPUB3 metadata + cover extractor: reads `META-INF/container.xml` to locate the
 * OPF package document, then reads `<metadata>`/`<manifest>` from it. No third-party EPUB
 * library — the app is 100% offline and this is a small, well-defined format to hand-parse
 * (docs/PRODUCT_SPEC.md §3: "open EPUB natively").
 */
object EpubParser {

    fun parse(file: File): EpubMetadata {
        ZipFile(file).use { zip ->
            val opfPath = findOpfPath(zip)
            val opfEntry = zip.getEntry(opfPath) ?: error("EPUB missing OPF at $opfPath")
            val opfDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")

            var title: String? = null
            var author: String? = null
            var series: String? = null
            var seriesNumber: String? = null
            var description: String? = null
            val tags = linkedSetOf<String>()
            var coverId: String? = null
            var coverHref: String? = null
            val manifestHrefById = mutableMapOf<String, String>()
            val manifestPropertiesById = mutableMapOf<String, String>()
            val collectionsById = linkedMapOf<String, EpubCollectionMetadata>()

            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(zip.getInputStream(opfEntry), null)
            }

            var currentTag: String? = null
            var currentMeta: ActiveMeta? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        when (parser.name) {
                            "meta" -> {
                                val name = parser.getAttributeValue(null, "name")
                                val content = parser.getAttributeValue(null, "content")?.cleanMetadataValue()
                                if (name == "cover") {
                                    coverId = content
                                }
                                when (name) {
                                    "calibre:series" -> series = content
                                    "calibre:series_index" -> seriesNumber = content
                                }
                                val property = parser.getAttributeValue(null, "property")
                                if (property != null) {
                                    currentMeta = ActiveMeta(
                                        property = property,
                                        id = parser.getAttributeValue(null, "id"),
                                        refines = parser.getAttributeValue(null, "refines")?.removePrefix("#"),
                                    )
                                    content?.let { value -> collectionsById.applyMeta(currentMeta, value) }
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
                            val activeMeta = currentMeta
                            if (activeMeta != null) {
                                collectionsById.applyMeta(activeMeta, text)
                            } else when (currentTag) {
                                "title" -> if (title == null) title = text
                                "creator" -> if (author == null) author = text
                                "description" -> if (description == null) description = text
                                "subject" -> tags.addAll(text.toTags())
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "meta") currentMeta = null
                        currentTag = null
                    }
                }
                event = parser.next()
            }

            if (series == null) {
                val collection = collectionsById.values.firstOrNull { it.type == "series" }
                    ?: collectionsById.values.firstOrNull()
                series = collection?.name
                seriesNumber = seriesNumber ?: collection?.position
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
                series = series,
                seriesNumber = seriesNumber,
                description = description,
                tags = tags.toList(),
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

private data class ActiveMeta(
    val property: String,
    val id: String?,
    val refines: String?,
)

private data class EpubCollectionMetadata(
    val name: String? = null,
    val type: String? = null,
    val position: String? = null,
)

private fun MutableMap<String, EpubCollectionMetadata>.applyMeta(meta: ActiveMeta, rawValue: String) {
    val value = rawValue.cleanMetadataValue() ?: return
    when (meta.property) {
        "belongs-to-collection" -> {
            val id = meta.id ?: "collection:${size + 1}"
            this[id] = (this[id] ?: EpubCollectionMetadata()).copy(name = value)
        }
        "collection-type" -> {
            val id = meta.refines ?: return
            this[id] = (this[id] ?: EpubCollectionMetadata()).copy(type = value)
        }
        "group-position" -> {
            val id = meta.refines ?: return
            this[id] = (this[id] ?: EpubCollectionMetadata()).copy(position = value)
        }
    }
}

private fun String?.cleanMetadataValue(): String? =
    this?.trim()?.ifBlank { null }

private fun String.toTags(): List<String> =
    split(',', ';')
        .mapNotNull { it.cleanMetadataValue() }
