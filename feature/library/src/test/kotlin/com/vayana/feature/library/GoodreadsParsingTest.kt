package com.vayana.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoodreadsParsingTest {

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
        assertNull(goodreadsBookIdOf("https://www.goodreads.com/author/show/735413.Ben_H_Winters"))
        assertNull(goodreadsBookIdOf("1234567890123"))
        assertNull(goodreadsBookIdOf(""))
    }

    @Test
    fun quotesPageYieldsTextAuthorBookTagsAndLikes() {
        val quotes = parseQuotesPage(QuotesPageHtml)

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
        assertEquals(parseQuotesPage(QuotesPageHtml), parseQuotesPage(serialized))
    }

    @Test
    fun challengePageIsRecognised() {
        assertTrue("<script>window.awsWafCookieDomainList = []; window.gokuProps = {}</script>".looksLikeGoodreadsChallenge())
    }

    @Test
    fun aPageWithoutQuotesMarksTheEnd() {
        assertTrue(parseQuotesPage("<html><body><p>No quotes yet.</p></body></html>").isEmpty())
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
