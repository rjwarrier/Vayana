package com.vayana.feature.gutenberg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Against feeds saved from gutenberg.org (search, the Popular list, Pride and Prejudice's book page). */
class GutenbergFeedsTest {
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/gutenberg/$name")).readText()

    @Test
    fun readsASearchPageOfBooksWithIdsAndAuthors() {
        val listing = GutenbergFeeds.parseListing(fixture("search.xml"))

        val first = listing.books.first()
        assertEquals(GutenbergBookSummary(1342, "Pride and Prejudice", "Jane Austen"), first)
        assertEquals("https://www.gutenberg.org/cache/epub/1342/pg1342.cover.small.jpg", first.coverUrl)
        assertTrue(listing.books.size >= 5)
        // Fewer than a page of results: no next page.
        assertNull(listing.nextUrl)
    }

    @Test
    fun followsAListToItsNextPage() {
        val listing = GutenbergFeeds.parseListing(fixture("popular.xml"))

        assertEquals(25, listing.books.size)
        assertEquals("https://www.gutenberg.org/ebooks/search.opds/?sort_order=downloads&start_index=26", listing.nextUrl)
    }

    @Test
    fun readsBothEpubEditionsWithSizesAndTheDescription() {
        val book = GutenbergFeeds.parseBook(fixture("book-1342.xml"), 1342)!!

        assertEquals("Pride and Prejudice", book.title)
        assertEquals("Jane Austen", book.author)
        assertEquals("en", book.language)
        assertEquals(
            listOf(
                GutenbergEdition(GutenbergEditionKind.WITHOUT_IMAGES, "https://www.gutenberg.org/ebooks/1342.epub.noimages", 558_381),
                GutenbergEdition(GutenbergEditionKind.WITH_IMAGES, "https://www.gutenberg.org/ebooks/1342.epub3.images", 24_835_578),
            ),
            book.editions,
        )
        assertTrue(book.summary!!.startsWith("\"Pride and Prejudice\" by Jane Austen"))
        assertTrue("Love stories" in book.subjects)
        assertEquals("https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg", book.coverUrl)
    }

    @Test
    fun onlyGutenbergOverHttpsIsFetched() {
        assertTrue(GutenbergFeeds.isGutenbergUrl("https://www.gutenberg.org/cache/epub/1342/pg1342.epub"))
        assertFalse(GutenbergFeeds.isGutenbergUrl("http://www.gutenberg.org/ebooks/1342.epub.noimages"))
        assertFalse(GutenbergFeeds.isGutenbergUrl("https://www.gutenberg.org.evil.example/x"))
        assertFalse(GutenbergFeeds.isGutenbergUrl("not a url"))
    }

    @Test
    fun searchQueriesAreEncoded() {
        assertEquals("https://www.gutenberg.org/ebooks/search.opds/?query=pride+%26+prejudice", GutenbergFeeds.searchUrl(" pride & prejudice "))
    }

    @Test
    fun feedsWithADtdAreRefused() {
        val xml = "<?xml version=\"1.0\"?><!DOCTYPE feed [<!ENTITY x \"y\">]><feed xmlns=\"http://www.w3.org/2005/Atom\"/>"
        assertFailsWith<IllegalArgumentException> { GutenbergFeeds.parseListing(xml) }
    }
}
