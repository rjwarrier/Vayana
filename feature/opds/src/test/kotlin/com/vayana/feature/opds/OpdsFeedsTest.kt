package com.vayana.feature.opds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpdsFeedsTest {
    private val base = "https://books.example.com/opds/"

    private val catalogue = """<?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/terms/">
          <title>Library</title>
          <link rel="search" href="search/{searchTerms}" type="application/atom+xml"/>
          <link rel="next" href="?offset=20" type="application/atom+xml;profile=opds-catalog"/>
          <entry>
            <title>By author</title><id>authors</id>
            <content type="text">Browse authors</content>
            <link href="authors" type="application/atom+xml;profile=opds-catalog;kind=navigation" rel="subsection"/>
          </entry>
          <entry>
            <title>Pride and Prejudice</title><id>urn:book:1</id>
            <author><name>Jane Austen</name></author>
            <author><name>Anon</name></author>
            <dc:language>en</dc:language>
            <summary type="html">&lt;p&gt;A &amp;amp; B&lt;/p&gt;&lt;p&gt;Second&lt;/p&gt;</summary>
            <link rel="http://opds-spec.org/image" href="/cover/1.jpg" type="image/jpeg"/>
            <link rel="http://opds-spec.org/image/thumbnail" href="/thumb/1.jpg" type="image/jpeg"/>
            <link rel="http://opds-spec.org/acquisition" href="/get/1.pdf" type="application/pdf" length="2048"/>
            <link rel="http://opds-spec.org/acquisition/open-access" href="/get/1.epub" type="application/epub+zip"/>
            <link rel="http://opds-spec.org/acquisition/buy" href="/buy/1" type="application/epub+zip"/>
            <link rel="alternate" href="/book/1" type="application/atom+xml;type=entry;profile=opds-catalog"/>
          </entry>
          <entry>
            <title>Only a kindle file</title><id>urn:book:2</id>
            <link rel="http://opds-spec.org/acquisition" href="/get/2.azw3" type="application/x-mobi8-ebook"/>
          </entry>
        </feed>"""

    @Test
    fun readsFoldersAndBooksWithLinksResolvedAgainstTheFeed() {
        val feed = OpdsFeeds.parseFeed(catalogue, base)
        assertEquals("Library", feed.title)
        assertEquals("https://books.example.com/opds/?offset=20", feed.nextUrl)
        assertEquals(listOf("By author", "Pride and Prejudice"), feed.entries.map { it.title })
        val folder = feed.entries[0]
        assertFalse(folder.isBook)
        assertEquals("https://books.example.com/opds/authors", folder.navigationUrl)
        val book = feed.entries[1]
        assertTrue(book.isBook)
        assertNull(book.navigationUrl)
        assertEquals("Jane Austen, Anon", book.author)
        assertEquals("en", book.language)
        assertEquals("A & B\nSecond", book.summary)
        assertEquals("https://books.example.com/cover/1.jpg", book.coverUrl)
        assertEquals("https://books.example.com/thumb/1.jpg", book.thumbnailUrl)
    }

    @Test
    fun offersOnlyFreeEpubAndPdfAcquisitionsWithEpubFirst() {
        val book = OpdsFeeds.parseFeed(catalogue, base).entries[1]
        assertEquals(listOf(OpdsFormat.EPUB, OpdsFormat.PDF), book.acquisitions.map { it.format })
        assertEquals("https://books.example.com/get/1.epub", book.acquisitions[0].url)
        assertEquals(2048L, book.acquisitions[1].sizeBytes)
    }

    @Test
    fun findsASearchTemplateInTheFeed() {
        val search = OpdsFeeds.parseFeed(catalogue, base).search
        assertEquals(OpdsSearch.Template("https://books.example.com/opds/search/{searchTerms}"), search)
    }

    @Test
    fun readsAnOpenSearchDescriptionAndFillsItsTemplate() {
        val description = """<OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
            <Url type="text/html" template="https://x/web?q={searchTerms}"/>
            <Url type="application/atom+xml" template="/opds/search?query={searchTerms}&amp;page={startPage?}&amp;lang={language?}"/>
        </OpenSearchDescription>"""
        val template = OpdsFeeds.searchTemplateFromDescription(description)
        assertEquals("/opds/search?query={searchTerms}&page={startPage?}&lang={language?}", template)
        assertEquals(
            "https://books.example.com/opds/search?query=war%20%26%20peace&page=1",
            OpdsFeeds.searchUrl(template!!, "war & peace", "https://books.example.com/opds"),
        )
        assertEquals(
            "https://books.example.com/opds/search/dune",
            OpdsFeeds.searchUrl("search/{searchTerms}", "dune", base),
        )
    }

    @Test
    fun linksToAnOpenSearchDescriptionAreKeptForLater() {
        val feed = OpdsFeeds.parseFeed(
            """<feed xmlns="http://www.w3.org/2005/Atom"><title>t</title>
               <link rel="search" href="/opds/opensearch.xml" type="application/opensearchdescription+xml"/></feed>""",
            base,
        )
        assertEquals(OpdsSearch.Description("https://books.example.com/opds/opensearch.xml"), feed.search)
    }

    @Test
    fun rejectsDocumentTypesAndOtherXml() {
        val dtd = "<!DOCTYPE feed [<!ENTITY x \"y\">]><feed xmlns=\"http://www.w3.org/2005/Atom\"/>"
        assertTrue(runCatching { OpdsFeeds.parseFeed(dtd, base) }.isFailure)
        assertTrue(runCatching { OpdsFeeds.parseFeed("<html/>", base) }.isFailure)
    }

    @Test
    fun catalogAddressesGetAnHttpsSchemeAndOnlyAllowWebAddresses() {
        assertEquals("https://books.example.com/opds", normalizedCatalogUrl("books.example.com/opds"))
        assertEquals("http://192.168.1.5:8083/opds", normalizedCatalogUrl(" http://192.168.1.5:8083/opds "))
        assertNull(normalizedCatalogUrl("ftp://example.com"))
        assertNull(normalizedCatalogUrl("not a url"))
        assertNull(normalizedCatalogUrl(""))
    }

    @Test
    fun aLoginOnlyGoesToTheCatalogsOwnServer() {
        assertTrue(OpdsClient.receivesLogin("https://a.example/opds", "https://a.example/get/1.epub"))
        assertTrue(OpdsClient.receivesLogin("https://a.example/opds", "https://a.example:443/x"))
        assertFalse(OpdsClient.receivesLogin("https://a.example/opds", "https://cdn.example/x"))
        assertFalse(OpdsClient.receivesLogin("https://a.example/opds", "http://a.example/x"))
        assertTrue(OpdsClient.receivesLogin("http://a.example/opds", "https://a.example/x"))
        assertFalse(OpdsClient.receivesLogin("http://a.example:8083/opds", "http://a.example:9000/x"))
    }

    @Test
    fun serverFieldsBuildTheCatalogAddressAndParseBack() {
        val calibre = OpdsAddress(host = "192.168.1.105", port = "8080", secure = false)
        assertEquals("http://192.168.1.105:8080/opds", buildAddress(calibre))
        assertEquals(calibre, parseAddress("http://192.168.1.105:8080/opds"))
        assertEquals("https://books.example.com/opds", buildAddress(OpdsAddress(host = " books.example.com ")))
        assertEquals("https://nas.local:8083/opds/x", buildAddress(OpdsAddress("nas.local", "8083", true, "opds/x")))
        assertEquals("http://nas", buildAddress(OpdsAddress("nas", "", false, "")))
        assertEquals(OpdsAddress("books.example.com", "", true, ""), parseAddress("https://books.example.com/"))
    }

    @Test
    fun badHostsAndPortsAreRejected() {
        assertNull(buildAddress(OpdsAddress(host = "")))
        assertNull(buildAddress(OpdsAddress(host = "a b")))
        assertNull(buildAddress(OpdsAddress(host = "http://x")))
        assertNull(buildAddress(OpdsAddress(host = "x", port = "0")))
        assertNull(buildAddress(OpdsAddress(host = "x", port = "70000")))
        assertNull(buildAddress(OpdsAddress(host = "x", port = "80a")))
    }

    @Test
    fun localNamesAreRecognisedSoPlainHttpCanBePreselected() {
        assertTrue(looksLikeLocalServer("192.168.1.105"))
        assertTrue(looksLikeLocalServer("nas.local"))
        assertTrue(looksLikeLocalServer("homeserver"))
        assertFalse(looksLikeLocalServer("books.example.com"))
        assertFalse(looksLikeLocalServer(""))
    }

    private fun fixture(name: String) = checkNotNull(javaClass.getResourceAsStream("/$name")).readBytes().toString(Charsets.UTF_8)

    @Test
    fun readsARealCalibreRootFeed() {
        val feed = OpdsFeeds.parseFeed(fixture("calibre-root.xml"), "http://192.168.1.105:8080/opds")
        assertEquals("calibre Library", feed.title)
        assertTrue(feed.entries.isNotEmpty() && feed.entries.none { it.isBook })
        assertEquals("http://192.168.1.105:8080/opds/navcatalog/4f6e6577657374?library_id=Books", feed.entries.first().navigationUrl)
        assertEquals(
            OpdsSearch.Template("http://192.168.1.105:8080/opds/search/{searchTerms}?library_id=Books"),
            feed.search,
        )
    }

    @Test
    fun readsARealCalibreBookPage() {
        val feed = OpdsFeeds.parseFeed(fixture("calibre-newest.xml"), "http://192.168.1.105:8080/opds/navcatalog/4f6e6577657374?library_id=Books")
        assertEquals("http://192.168.1.105:8080/opds/navcatalog/4f6e6577657374?library_id=Books&offset=30", feed.nextUrl)
        val book = feed.entries.first()
        assertTrue(book.isBook)
        assertEquals("The Order of Time", book.title)
        assertEquals("Rovelli, Carlo", book.author)
        assertEquals("http://192.168.1.105:8080/get/epub/312/Books", book.acquisitions.single().url)
        assertEquals(5127436L, book.acquisitions.single().sizeBytes)
        assertEquals("http://192.168.1.105:8080/get/thumb/312/Books", book.thumbnailUrl)
        assertEquals(
            "http://192.168.1.105:8080/opds/search/war%20%26%20peace?library_id=Books",
            OpdsFeeds.searchUrl("http://192.168.1.105:8080/opds/search/{searchTerms}?library_id=Books", "war & peace", "http://x/"),
        )
    }

    @Test
    fun discoveryScansTheOtherAddressesOfThePhonesSubnet() {
        val hosts = OpdsDiscovery.subnetHosts(byteArrayOf(192.toByte(), 168.toByte(), 1, 109))
        assertEquals(253, hosts.size)
        assertEquals("192.168.1.1", hosts.first())
        assertEquals("192.168.1.254", hosts.last())
        assertFalse("192.168.1.109" in hosts)
        assertTrue(OpdsDiscovery.subnetHosts(byteArrayOf(1, 2)).isEmpty())
    }

    @Test
    fun discoveryReadsTheFeedsOwnTitleFromTheFirstBytes() {
        val head = fixture("calibre-root.xml").take(4096)
        assertEquals("calibre Library", OpdsDiscovery.feedTitle(head))
        assertNull(OpdsDiscovery.feedTitle("<feed><title> </title>"))
    }
}
