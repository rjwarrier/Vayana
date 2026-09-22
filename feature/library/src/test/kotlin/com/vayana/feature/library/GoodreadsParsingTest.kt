package com.vayana.feature.library

import com.vayana.core.common.quoteMatchKey
import com.vayana.core.common.ParsedQuote
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.yield
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
    fun collectionRetainsLanguagesForThePreviewToChoose() = runBlocking {
        val pages = mapOf(
            1 to quotesPage("1", "Esta es una cita en español.", nextPage = 2),
            2 to quotesPage("2", "This is an English quote."),
        )
        assertEquals(2, collectGoodreadsQuotes(loadPage = pages::get)?.size)
    }

    @Test
    fun languageSelectionGroupsDeduplicatesAndExcludesUnknownText() = runBlocking {
        val english = ParsedQuote("This is an English quote.")
        val malayalam = ParsedQuote("ഇത് മലയാളത്തിലുള്ള ഒരു ഉദ്ധരണിയാണ്.")
        val spanish = ParsedQuote("Esta es una cita en español.")
        val unknown = ParsedQuote("An undetermined language quote.")
        val failed = ParsedQuote("Language detection failed here.")
        val tags = mapOf(english.quoteText to "en-US", malayalam.quoteText to "ml", spanish.quoteText to "es", unknown.quoteText to "und")
        val calls = AtomicInteger()
        val progress = mutableListOf<GoodreadsQuoteProgress>()
        val detected = detectGoodreadsQuoteLanguages(
            listOf(english, malayalam, spanish, unknown, failed, english.copy(quoteText = "“This is an English quote!”")),
            onProgress = progress::add,
            identifyLanguage = { calls.incrementAndGet(); tags[it] },
        )
        assertEquals(setOf("en", "ml", "es"), detected.quotesByLanguage.keys)
        assertEquals("en", detected.defaultLanguageTag)
        assertEquals(listOf(malayalam), detected.quotesFor("ml"))
        assertEquals(listOf(english), detected.quotesFor("en"))
        assertEquals(2, detected.undeterminedCount)
        assertEquals(5, calls.get())
        assertEquals(GoodreadsQuoteProgress(5, 5), progress.last())
        assertTrue(detected.quotesFor("fr").isEmpty())
        assertTrue(detected.quotesFor(null).isEmpty())
    }

    @Test
    fun languageSelectionDefaultsToLargestGroupWhenEnglishIsAbsent() = runBlocking {
        val quotes = listOf("First Malayalam quote.", "Second Malayalam quote.", "A Spanish quote.").map(::ParsedQuote)
        val detected = detectGoodreadsQuoteLanguages(quotes) { if (it.contains("Malayalam")) "ml" else "es" }
        assertEquals("ml", detected.defaultLanguageTag)
        assertEquals(quotes.take(2), detected.quotesFor("ml"))
    }

    @Test
    fun languageSelectionHandlesEmptyUnknownAndTooShortBatches() = runBlocking {
        val empty = detectGoodreadsQuoteLanguages(emptyList()) { error("No detection should run") }
        assertNull(empty.defaultLanguageTag)
        val unknown = detectGoodreadsQuoteLanguages(listOf(ParsedQuote("Unknown language here."), ParsedQuote("short"))) { "und" }
        assertNull(unknown.defaultLanguageTag)
        assertEquals(1, unknown.undeterminedCount)
        assertTrue(unknown.quotesFor("und").isEmpty())
    }

    @Test
    fun detectionKeepsOrderAndBoundsConcurrentRequests() = runBlocking {
        val quotes = (1..25).map { ParsedQuote("English quote number $it.") }
        var active = 0
        var peak = 0
        val detected = detectGoodreadsQuoteLanguages(quotes) {
            active++
            peak = maxOf(peak, active)
            yield()
            active--
            "en"
        }
        assertTrue(peak in 2..8)
        assertEquals(quotes, detected.quotesFor("en"))
    }

    @Test
    fun cancellingDetectionCancelsThePendingBatch() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        val detection = async {
            detectGoodreadsQuoteLanguages(listOf(ParsedQuote("An English quote to detect."))) {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } catch (exception: CancellationException) {
                    cancelled = true
                    throw exception
                }
            }
        }
        withTimeout(1_000) { started.await() }
        detection.cancelAndJoin()
        assertTrue(cancelled)
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
