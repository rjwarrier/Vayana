package com.vayana.feature.gutenberg

import java.io.StringReader
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** A book as a Gutenberg list shows it: enough for a cover tile. */
data class GutenbergBookSummary(val id: Long, val title: String, val author: String?) {
    val coverUrl: String get() = gutenbergCoverUrl(id)
}

/** One page of a Gutenberg list (Popular, Latest, a search), and where the next page is, if any. */
data class GutenbergListing(val books: List<GutenbergBookSummary>, val nextUrl: String?)

/** The two EPUB editions Gutenberg makes of a book: plain text, or with its illustrations. */
enum class GutenbergEditionKind { WITHOUT_IMAGES, WITH_IMAGES }

data class GutenbergEdition(val kind: GutenbergEditionKind, val url: String, val sizeBytes: Long?)

/** A book's own page: what the details sheet shows and the editions to choose from. */
data class GutenbergBook(
    val id: Long,
    val title: String,
    val author: String?,
    val language: String?,
    val summary: String?,
    val subjects: List<String>,
    val published: String?,
    val editions: List<GutenbergEdition>,
) {
    val coverUrl: String get() = gutenbergCoverUrl(id, large = true)
}

/**
 * Reads Project Gutenberg's OPDS 1.x (Atom) feeds. Lists link each book to its own feed (`/ebooks/1342.opds`); a
 * book's feed has one entry per edition - one without images, one with - each offering EPUB and Kindle files.
 */
object GutenbergFeeds {
    const val BaseUrl = "https://www.gutenberg.org"

    fun listUrl(list: GutenbergList): String = "$BaseUrl/ebooks/search.opds/?sort_order=${list.sortOrder}"

    fun searchUrl(query: String): String =
        "$BaseUrl/ebooks/search.opds/?query=${java.net.URLEncoder.encode(query.trim(), Charsets.UTF_8.name())}"

    fun bookUrl(id: Long): String = "$BaseUrl/ebooks/$id.opds"

    fun parseListing(xml: String): GutenbergListing {
        val feed = parse(xml)
        val books = feed.children("entry").mapNotNull { entry ->
            val href = entry.links().firstOrNull { it.getAttribute("rel") == "subsection" }?.getAttribute("href")
            val id = href?.let(BookIdRegex::find)?.groupValues?.get(1)?.toLongOrNull() ?: return@mapNotNull null
            GutenbergBookSummary(
                id = id,
                title = entry.childText("title") ?: return@mapNotNull null,
                // A list entry's content is just the author.
                author = entry.childText("content"),
            )
        }
        val next = feed.links().firstOrNull { it.getAttribute("rel") == "next" }?.getAttribute("href")?.let(::absolute)
        return GutenbergListing(books.distinctBy { it.id }, next)
    }

    fun parseBook(xml: String, id: Long): GutenbergBook? {
        val entries = parse(xml).children("entry")
        val first = entries.firstOrNull() ?: return null
        // The richer description ("Summary: ...", "Subject: ...") is in the content's paragraphs, the same in both.
        val fields = entries.flatMap { it.contentParagraphs() }.mapNotNull { paragraph ->
            val separator = paragraph.indexOf(':')
            if (separator <= 0) null else paragraph.substring(0, separator).trim() to paragraph.substring(separator + 1).trim()
        }
        val editions = entries.mapNotNull { entry ->
            val epub = entry.links().firstOrNull {
                it.getAttribute("rel") == AcquisitionRel && it.getAttribute("type") == EpubType
            } ?: return@mapNotNull null
            val withImages = !epub.getAttribute("title").contains("no images", ignoreCase = true)
            GutenbergEdition(
                kind = if (withImages) GutenbergEditionKind.WITH_IMAGES else GutenbergEditionKind.WITHOUT_IMAGES,
                url = absolute(epub.getAttribute("href")),
                sizeBytes = epub.getAttribute("length").toLongOrNull(),
            )
        }.distinctBy { it.kind }.sortedBy { it.kind.ordinal }
        return GutenbergBook(
            id = id,
            title = first.childText("title") ?: return null,
            author = first.child("author")?.childText("name")?.let(::readableAuthor),
            language = first.childText("dcterms:language", DctermsNamespace),
            summary = fields.firstOrNull { it.first == "Summary" }?.second?.removeSuffix("(This is an automatically generated summary.)")?.trim(),
            subjects = fields.filter { it.first == "Subject" }.map { it.second }.distinct(),
            published = fields.firstOrNull { it.first == "Published" }?.second,
            editions = editions,
        )
    }

    /** Only Gutenberg's own site: every link a feed gives is checked against it before it's fetched. */
    fun isGutenbergUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && (uri.host == "www.gutenberg.org" || uri.host == "gutenberg.org")
    }.getOrDefault(false)

    private fun absolute(href: String): String = URI(BaseUrl).resolve(href).toString()

    /** "Austen, Jane" reads better as "Jane Austen"; anything else ("Anonymous", three parts) is left as it is. */
    private fun readableAuthor(name: String): String {
        val parts = name.split(", ")
        return if (parts.size == 2) "${parts[1]} ${parts[0]}" else name
    }

    private fun parse(xml: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Feeds come from the network: no external entities or DTDs.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
    }

    private fun Element.children(localName: String, namespace: String = AtomNamespace): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { index ->
            (nodes.item(index) as? Element)?.takeIf { it.localName == localName && it.namespaceURI == namespace }
        }
    }

    private fun Element.child(localName: String) = children(localName).firstOrNull()

    private fun Element.childText(qualified: String, namespace: String = AtomNamespace): String? =
        children(qualified.substringAfter(':'), namespace).firstOrNull()?.textContent?.trim()?.ifEmpty { null }

    private fun Element.links() = children("link")

    private fun Element.contentParagraphs(): List<String> {
        val content = child("content") ?: return emptyList()
        val paragraphs = content.getElementsByTagNameNS("*", "p")
        return (0 until paragraphs.length).map { paragraphs.item(it).textContent.replace(WhitespaceRegex, " ").trim() }
    }

    private const val AtomNamespace = "http://www.w3.org/2005/Atom"
    private const val DctermsNamespace = "http://purl.org/dc/terms/"
    private const val AcquisitionRel = "http://opds-spec.org/acquisition"
    private const val EpubType = "application/epub+zip"
    private val BookIdRegex = Regex("/ebooks/(\\d+)\\.opds")
    private val WhitespaceRegex = Regex("\\s+")
}

/** Gutenberg's own lists, by what its catalogue sorts on. */
enum class GutenbergList(val sortOrder: String) { POPULAR("downloads"), LATEST("release_date"), RANDOM("random") }

/** Gutenberg keeps every book's cover at a fixed address; lists don't carry it. */
internal fun gutenbergCoverUrl(id: Long, large: Boolean = false): String =
    "${GutenbergFeeds.BaseUrl}/cache/epub/$id/pg$id.cover.${if (large) "medium" else "small"}.jpg"
