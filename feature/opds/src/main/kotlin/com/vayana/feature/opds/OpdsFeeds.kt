package com.vayana.feature.opds

import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** One way to get a book: a file link, in a format Vayana can read. */
data class OpdsAcquisition(val url: String, val format: OpdsFormat, val sizeBytes: Long?)

enum class OpdsFormat(val mimeType: String, val extension: String) {
    EPUB("application/epub+zip", "epub"),
    PDF("application/pdf", "pdf"),
}

/** A catalogue entry: either a book to download, or a folder ([navigationUrl]) leading to another feed. */
data class OpdsEntry(
    val id: String,
    val title: String,
    val author: String?,
    val summary: String?,
    val coverUrl: String?,
    val thumbnailUrl: String?,
    val language: String?,
    val navigationUrl: String?,
    val acquisitions: List<OpdsAcquisition>,
) {
    val isBook: Boolean get() = acquisitions.isNotEmpty()
}

/** Where a feed's search lives: a URL with `{searchTerms}`, or an OpenSearch description to read first. */
sealed interface OpdsSearch {
    data class Template(val template: String) : OpdsSearch
    data class Description(val url: String) : OpdsSearch
}

data class OpdsFeed(
    val title: String,
    val entries: List<OpdsEntry>,
    val nextUrl: String?,
    val search: OpdsSearch?,
)

/**
 * Reads OPDS 1.x (Atom) feeds as served by Calibre-Web, Calibre's content server, Standard Ebooks, COPS, Kavita and
 * the like. Every link is resolved against the feed's own address. Only free acquisitions in EPUB or PDF are offered;
 * purchase, borrow and sample links are ignored.
 */
object OpdsFeeds {
    fun parseFeed(xml: String, baseUrl: String): OpdsFeed {
        val root = parse(xml)
        if (root.localName != "feed") throw IllegalArgumentException("Not an Atom feed")
        val links = root.children("link")
        return OpdsFeed(
            title = root.childText("title") ?: "",
            entries = root.children("entry").mapNotNull { entry(it, baseUrl) },
            nextUrl = links.firstOrNull { it.rel() == "next" }?.href(baseUrl),
            search = links.firstNotNullOfOrNull { searchOf(it, baseUrl) },
        )
    }

    /** The Atom search template from an OpenSearch description document; null when it has none. */
    fun searchTemplateFromDescription(xml: String): String? {
        val urls = parse(xml).getElementsByTagNameNS("*", "Url")
        for (index in 0 until urls.length) {
            val url = urls.item(index) as? Element ?: continue
            val template = url.getAttribute("template")
            if (url.getAttribute("type").contains("atom+xml", ignoreCase = true) && template.contains("{searchTerms")) {
                return template
            }
        }
        return null
    }

    /** Fills an OpenSearch template: the words URL-encoded, page parameters at 1, anything optional dropped. */
    fun searchUrl(template: String, terms: String, baseUrl: String): String {
        val encoded = URLEncoder.encode(terms.trim(), "UTF-8").replace("+", "%20")
        var url = template
            .replace(Regex("\\{searchTerms\\??\\}"), encoded)
            .replace(Regex("\\{(?:startPage|startIndex)\\??\\}"), "1")
            .replace(Regex("\\{[^}]*\\}"), "")
        url = url.replace(Regex("[?&][^=&?#]+=(?=&|#|$)"), "")
        if ('?' !in url && '&' in url) url = url.replaceFirst('&', '?')
        return resolve(baseUrl, url)
    }

    fun resolve(baseUrl: String, href: String): String = runCatching {
        // Templates hold braces that java.net.URI rejects; resolve around them.
        val safe = href.replace("{", "%7B").replace("}", "%7D")
        URI(baseUrl).resolve(safe).toString().replace("%7B", "{").replace("%7D", "}")
    }.getOrDefault(href)

    private fun entry(entry: Element, baseUrl: String): OpdsEntry? {
        val title = entry.childText("title") ?: return null
        val links = entry.children("link")
        val acquisitions = links.mapNotNull { acquisition(it, baseUrl) }
            .distinctBy { it.format }
            .sortedBy { it.format.ordinal }
        // A folder is a link to another Atom feed; a book may also link to its own entry, which is not one.
        val navigation = if (acquisitions.isNotEmpty()) null else links.firstOrNull { link ->
            link.getAttribute("type").contains("atom+xml", ignoreCase = true) &&
                !link.rel().startsWith(AcquisitionPrefix) && link.rel() != "self"
        }?.href(baseUrl)
        if (acquisitions.isEmpty() && navigation == null) return null
        val cover = links.firstOrNull { it.rel() in ImageRels }?.href(baseUrl)
        val thumbnail = links.firstOrNull { it.rel() in ThumbnailRels }?.href(baseUrl)
        val authors = entry.children("author").mapNotNull { it.childText("name") }.distinct()
        return OpdsEntry(
            id = entry.childText("id") ?: (navigation ?: acquisitions.first().url),
            title = title,
            author = authors.joinToString(", ").ifEmpty { null },
            summary = (entry.childText("summary") ?: entry.childText("content"))?.let(::plainText)?.ifEmpty { null },
            coverUrl = cover ?: thumbnail,
            thumbnailUrl = thumbnail ?: cover,
            language = entry.firstDescendantText("language"),
            navigationUrl = navigation,
            acquisitions = acquisitions,
        )
    }

    private fun acquisition(link: Element, baseUrl: String): OpdsAcquisition? {
        val rel = link.rel()
        if (!rel.startsWith(AcquisitionPrefix)) return null
        // Direct downloads only: buying, borrowing, subscribing and samples need a store.
        val kind = rel.removePrefix(AcquisitionPrefix)
        if (kind.isNotEmpty() && kind != "/open-access") return null
        val type = link.getAttribute("type").substringBefore(';').trim().lowercase()
        val format = OpdsFormat.entries.firstOrNull { it.mimeType == type } ?: return null
        return OpdsAcquisition(
            url = link.href(baseUrl) ?: return null,
            format = format,
            sizeBytes = link.getAttribute("length").toLongOrNull()?.takeIf { it > 0 },
        )
    }

    private fun searchOf(link: Element, baseUrl: String): OpdsSearch? {
        if (link.rel() != "search") return null
        val href = link.getAttribute("href").ifEmpty { return null }
        return when {
            link.getAttribute("type").contains("opensearchdescription", ignoreCase = true) ->
                OpdsSearch.Description(resolve(baseUrl, href))
            "{searchTerms" in href -> OpdsSearch.Template(resolve(baseUrl, href))
            else -> null
        }
    }

    /** Summaries are often HTML; the details sheet shows plain text. */
    internal fun plainText(source: String): String = source
        .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</li>"), "\n")
        .replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")
        .lines().joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private fun parse(xml: String): Element {
        // No DTDs, so no entities. Checked here because Android's parser rejects the Xerces feature that would do it.
        if (DoctypeRegex.containsMatchIn(xml)) throw IllegalArgumentException("Feed has a DTD")
        val builder = synchronized(ParserFactory) { ParserFactory.newDocumentBuilder() }
        return builder.parse(InputSource(StringReader(xml.trimStart('﻿')))).documentElement
    }

    private fun Element.children(localName: String): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { index ->
            (nodes.item(index) as? Element)?.takeIf { it.localName == localName && it.namespaceURI == AtomNamespace }
        }
    }

    private fun Element.childText(localName: String): String? =
        children(localName).firstOrNull()?.textContent?.trim()?.ifEmpty { null }

    private fun Element.firstDescendantText(localName: String): String? {
        val nodes = getElementsByTagNameNS("*", localName)
        return if (nodes.length == 0) null else nodes.item(0).textContent.trim().ifEmpty { null }
    }

    private fun Element.rel(): String = getAttribute("rel")

    private fun Element.href(baseUrl: String): String? =
        getAttribute("href").takeIf { it.isNotBlank() }?.let { resolve(baseUrl, it) }

    private const val AtomNamespace = "http://www.w3.org/2005/Atom"
    private const val AcquisitionPrefix = "http://opds-spec.org/acquisition"
    private val ImageRels = setOf("http://opds-spec.org/image", "x-stanza-cover-image")
    private val ThumbnailRels = setOf("http://opds-spec.org/image/thumbnail", "x-stanza-cover-image-thumbnail")
    private val DoctypeRegex = Regex("<!(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE)
    private val ParserFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }
}
