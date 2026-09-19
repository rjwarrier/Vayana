package com.vayana.feature.library

import com.vayana.core.common.quoteMatchKey
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class GoodreadsParsingTest {

    @Test
    fun searchUrlIncludesEncodedBookTitleAndAuthor() {
        assertEquals(
            "https://www.goodreads.com/search?q=Countdown+City+Ben+H.+Winters",
            goodreadsSearchUrl("Countdown City Ben H. Winters"),
        )
    }

    @Test
    fun bookIdComesFromEveryShapeOfLinkPeoplePaste() {
        val id = "16046748"
        assertEquals(id, goodreadsBookIdOf("https://www.goodreads.com/book/show/16046748-countdown-city"))
        assertEquals(id, goodreadsBookIdOf("goodreads.com/book/show/16046748"))
        assertEquals(id, goodreadsBookIdOf("https://www.goodreads.com/en/book/show/16046748.Countdown_City?ref=nav_sb"))
        assertEquals(id, goodreadsBookIdOf("Check out Countdown City (https://www.goodreads.com/book/show/16046748-countdown-city)!"))
        assertEquals(id, goodreadsBookIdOf("  16046748 "))
    }

    @Test
    fun lookalikeHostsAndNonBookPagesAreRefused() {
        assertNull(goodreadsBookIdOf("https://notgoodreads.com/book/show/1"))
        assertNull(goodreadsBookIdOf("https://www.goodreads.com.evil.example/book/show/1"))
        assertNull(goodreadsBookIdOf("https://evil.example/goodreads.com/book/show/1"))
        assertNull(goodreadsBookIdOf("https://evil.example/?next=https://www.goodreads.com/book/show/1"))
        assertNull(goodreadsBookIdOf("https://evil.example/@www.goodreads.com/book/show/1"))
        assertNull(goodreadsBookIdOf("mailto:user@www.goodreads.com/book/show/1"))
        assertNull(goodreadsBookIdOf("https://www.goodreads.com@evil.example/book/show/1"))
        assertNull(goodreadsBookIdOf("https://www.goodreads.com/%62ook/show/16046748"))
        assertNull(goodreadsBookIdOf("https://www.goodreads.com/author/show/735413.Ben_H_Winters"))
        assertNull(goodreadsBookIdOf("1234567890123"))
        assertNull(goodreadsBookIdOf(""))
    }

    @Test
    fun nextDataFixtureYieldsMetadataFields() {
        val metadata = parseBookPage(
            html = fixture("goodreads/book_next_data.html"),
            bookId = "16046748",
            canonicalUrl = goodreadsBookUrl("16046748"),
        )

        requireNotNull(metadata)
        assertEquals("https://www.goodreads.com/book/show/16046748", metadata.canonicalUrl)
        assertEquals("The Last Policeman", metadata.series)
        assertEquals("2", metadata.seriesNumber)
        assertEquals(listOf("Science Fiction", "Mystery"), metadata.genres)
        assertEquals("A detective story & an asteroid countdown.\nSecond line.", metadata.description)
        assertEquals("https://i.gr-assets.com/images/S/compressed.photo.goodreads.com/books/fixture.jpg", metadata.coverUrl)
        assertEquals(2012, metadata.originalPublicationYear)
        assertEquals(4.05f, metadata.averageRating)
        assertEquals(12345, metadata.ratingsCount)
        assertEquals("53271919", metadata.workId)
    }

    @Test
    fun nextDataFixtureForWrongBookIdIsRejected() {
        assertNull(parseBookPage(fixture("goodreads/book_next_data.html"), bookId = "1", canonicalUrl = goodreadsBookUrl("1")))
    }

    @Test
    fun quotesPageYieldsTextAuthorBookTagsAndLikes() {
        val quotes = parseQuotesPage(fixture("goodreads/quotes_page.html"))

        assertEquals(1, quotes.size, "the one-word fragment is dropped")
        val (id, quote) = quotes.single()
        assertEquals("868223", id)
        assertEquals("Respectfully, sir, the asteroid did not make you leave her.\nIt's just a rock & nothing more.", quote.quoteText)
        assertEquals("Ben H. Winters", quote.author)
        assertEquals("Countdown City (The Last Policeman, #2)", quote.sourceTitle)
        assertEquals(listOf("choices", "responsibility"), quote.tags)
        assertEquals(1214, quote.likesCount)
        assertEquals(1215, quote.highlightsCount)
    }

    @Test
    fun browserSerializedMarkupWithDoubleQuotesParsesTheSame() {
        // A WebView's outerHTML rewrites class='x' as class="x"; the in-app browser path hands us that.
        val serialized = QuotesPageHtml.replace("class='", "class=\"").replace(Regex("""class="([a-zA-Z]+)'"""), "class=\"$1\"")
        assertEquals(parseQuotesPage(fixture("goodreads/quotes_page.html")), parseQuotesPage(serialized))
    }

    @Test
    fun challengePageIsRecognised() {
        assertTrue("<script>window.awsWafCookieDomainList = []; window.gokuProps = {}</script>".looksLikeGoodreadsChallenge())
        assertTrue(fixture("goodreads/challenge_page.html").looksLikeGoodreadsChallenge())
        assertTrue(fixture("goodreads/challenge_page_mixed_case.html").looksLikeGoodreadsChallenge())
    }

    @Test
    fun aPageWithoutQuotesMarksTheEnd() {
        assertTrue(parseQuotesPage("<html><body><p>No quotes yet.</p></body></html>").isEmpty())
    }

    @Test
    fun quoteLengthFloorIsTwelveCharacters() {
        assertTrue(parseQuotesPage(quotesPage(id = "1", text = "a b c d e f")).isEmpty())
        assertEquals("a b c d e f!", parseQuotesPage(quotesPage(id = "2", text = "a b c d e f!")).single().second.quoteText)
    }

    @Test
    fun quoteCollectionFollowsTheAdvertisedNextPage() = runBlocking {
        val requestedPages = mutableListOf<Int>()
        val pages = mapOf(
            1 to quotesPage(id = "1", text = "Too short", nextPage = 2),
            2 to quotesPage(id = "2", text = "Second English quote."),
        )

        val quotes = collectGoodreadsQuotes(
            loadPage = { page -> requestedPages += page; pages[page] },
            acceptsQuote = { true },
        )

        assertEquals(listOf(1, 2), requestedPages)
        assertEquals(listOf("Second English quote."), quotes?.map { it.quoteText })
    }

    @Test
    fun quoteCollectionReportsGoodreadsProcessedAndTotalCounts() = runBlocking {
        val progress = mutableListOf<GoodreadsQuoteProgress>()
        val pages = mapOf(
            1 to "Showing 1 - 30 of 77" + quotesPage(id = "1", text = "First English quote.", nextPage = 2),
            2 to "Showing 31–60 of 77" + quotesPage(id = "2", text = "Second English quote."),
        )

        collectGoodreadsQuotes(
            loadPage = pages::get,
            acceptsQuote = { true },
            onProgress = progress::add,
        )

        assertEquals(
            listOf(GoodreadsQuoteProgress(30, 77), GoodreadsQuoteProgress(60, 77)),
            progress,
        )
    }

    @Test
    fun quoteCollectionClassifiesConcurrentlyWithoutReordering() = runBlocking {
        val bothStarted = CompletableDeferred<Unit>()
        val releaseClassifiers = CompletableDeferred<Unit>()
        val started = AtomicInteger()
        val page = quotesPage(id = "1", text = "First English quote.") +
            quotesPage(id = "2", text = "Second English quote.")

        val collection = async {
            collectGoodreadsQuotes(
                loadPage = { page },
                acceptsQuote = {
                    if (started.incrementAndGet() == 2) bothStarted.complete(Unit)
                    releaseClassifiers.await()
                    true
                },
            )
        }

        withTimeout(1_000) { bothStarted.await() }
        releaseClassifiers.complete(Unit)
        assertEquals(
            listOf("First English quote.", "Second English quote."),
            collection.await()?.map { it.quoteText },
        )
    }

    @Test
    fun quoteProgressSupportsThousandsSeparators() {
        assertEquals(
            GoodreadsQuoteProgress(processed = 60, total = 1_234),
            goodreadsQuotePageProgress("<div class='mediumText'>Showing 31 - 60 of 1,234</div>"),
        )
    }

    @Test
    fun nextQuotePageSupportsMobileAndStandardPaginationMarkup() {
        assertEquals(
            2,
            nextGoodreadsQuotesPage(
                """<a class="jsLoadMore btnSecondary" href="/work/quotes/1?mobile_xhr=1&amp;page=2">Load More</a>""",
                currentPage = 1,
            ),
        )
        assertEquals(
            4,
            nextGoodreadsQuotesPage(
                """<a class="next_page" rel="next" href="?page=4">next</a>""",
                currentPage = 3,
            ),
        )
        assertNull(nextGoodreadsQuotesPage("<span class=\"disabled next_page\">next</span>", currentPage = 4))
        assertNull(nextGoodreadsQuotesPage("""<a class="next_page" href="?page=501">next</a>""", currentPage = 500))
    }

    @Test
    fun onlyEnglishLanguageResultsAreAccepted() = runBlocking {
        assertTrue(isEnglishGoodreadsQuote("This is an English quote.") { "en" })
        assertTrue(isEnglishGoodreadsQuote("This is an English quote.") { "en-US" })
        assertFalse(isEnglishGoodreadsQuote("هذا اقتباس عربي") { "ar" })
        assertFalse(isEnglishGoodreadsQuote("Esta es una cita.") { "es" })
        assertFalse(isEnglishGoodreadsQuote("Ambiguous text") { "und" })
        assertFalse(isEnglishGoodreadsQuote("Detection failed") { null })
    }

    @Test
    fun htmlDecodingKeepsEscapedMarkupAsText() {
        assertEquals("<b>bold</b> — ok", "&lt;b&gt;bold&lt;/b&gt; &#x2014; ok".htmlToPlainText())
        assertEquals("one\ntwo", "<p>one</p><p>two</p>".htmlToPlainText())
        assertEquals("&bogus; stays", "&bogus; stays".htmlToPlainText())
    }

    @Test
    fun quoteKeysIgnoreQuoteMarksCaseAndSpacing() {
        assertEquals(quoteMatchKey("\"hello world\""), quoteMatchKey("“Hello,  World!”"))
    }

    private companion object {
        fun fixture(path: String): String =
            requireNotNull(GoodreadsParsingTest::class.java.classLoader?.getResource(path)) { "Missing fixture: $path" }
                .readText()

        fun quotesPage(id: String, text: String, nextPage: Int? = null): String = """
            <div class="quotesList">
            <article>
            <blockquote class="quoteBody">$text</blockquote>
            <a id="like_id_quote_$id" href="/quotes/$id"><span class="likesCount">0</span></a>
            </article>
            </div>
            ${nextPage?.let { """<a class="jsLoadMore" href="?page=$it">Load More</a>"""}.orEmpty()}
        """.trimIndent()

        val QuotesPageHtml = """
            <div class='quotesList'>
            <article>
            <div class='quoteContainer'>
            <blockquote class='quoteBody'>Respectfully, sir, the asteroid did not make you leave her.<br>It&#39;s just a rock &amp; nothing more.</blockquote>
            <span class='quoteAuthor'>
            Ben H. Winters,
            </span>
            <span class='quoteBook'>
            <a class="gr-hyperlink" href="/book/show/16046748-countdown-city">Countdown City (The Last Policeman, #2)</a>
            </span>
            <div class='quoteTags'>Tags:
            <a title="Choices quotes" href="/quotes/tag/choices">choices</a>,
            <a title="Responsibility quotes" href="/quotes/tag/responsibility">responsibility</a>
            </div>
            <a id="like_id_quote_868223" href="/quotes/868223-respectfully"><span class='likesIndicator'>likes:</span><span class='likesCount'>1,214</span></a>
            </div>
            </article>
            <article>
            <div class='quoteContainer'>
            <blockquote class='quoteBody'>they</blockquote>
            <a id="like_id_quote_7324470" href="/quotes/7324470-they"><span class='likesCount'>0</span></a>
            </div>
            </article>
        """.trimIndent()
    }
}
