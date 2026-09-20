package com.vayana.feature.library

import android.graphics.BitmapFactory
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.common.ParsedQuote
import com.vayana.core.common.quoteMatchKey
import com.vayana.core.common.runCatchingCancellable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What a Goodreads book page yields for the "Import from Goodreads" flow. Every field is optional. */
data class GoodreadsBookMetadata(
    val canonicalUrl: String,
    val series: String?,
    /** The book's position in [series], without Goodreads' leading "#". */
    val seriesNumber: String?,
    val genres: List<String>,
    val description: String?,
    val coverUrl: String?,
    val originalPublicationYear: Int?,
    val averageRating: Float?,
    val ratingsCount: Int?,
    /** Goodreads' id for the work (all editions); its quotes page hangs off this, not the book id. */
    val workId: String?,
)

/** [BLOCKED]: Goodreads answered with its bot challenge instead of the page - retrying straight away won't help. */
enum class GoodreadsFetchError { INVALID_LINK, NOT_FOUND, BLOCKED, FAILED }

sealed interface GoodreadsFetchResult {
    data class Success(val metadata: GoodreadsBookMetadata) : GoodreadsFetchResult
    data class Failure(val error: GoodreadsFetchError) : GoodreadsFetchResult
}

enum class GoodreadsImportStep { FETCHING_BOOK, FETCHING_COVER_AND_QUOTES }

data class GoodreadsQuoteProgress(val processed: Int, val total: Int)

sealed interface GoodreadsImportState {
    data object Idle : GoodreadsImportState
    data class Working(
        val step: GoodreadsImportStep,
        val quoteProgress: GoodreadsQuoteProgress? = null,
    ) : GoodreadsImportState
    data class Failed(val error: GoodreadsFetchError) : GoodreadsImportState
    data class Preview(val metadata: GoodreadsBookMetadata, val capturedQuotes: List<ParsedQuote>? = null) : GoodreadsImportState
    /** The import landed; the dialog closes itself and the result shows as a snackbar. */
    data object Done : GoodreadsImportState
}

data class GoodreadsImportOptions(
    val series: Boolean = true,
    val description: Boolean = true,
    val genres: Boolean = true,
    val cover: Boolean = true,
    val goodreadsInfo: Boolean = true,
    val quotes: Boolean = true,
) {
    val hasAnySelection: Boolean
        get() = series || description || genres || cover || goodreadsInfo || quotes
}

/** The two covers a book can switch between once Goodreads has supplied one. */
enum class CoverSource { CUSTOM, GOODREADS }

/** Goodreads lists a long tail of genres; only the leading ones become tags. */
internal const val GoodreadsMaxGenreTags = 5
internal const val GoodreadsQuoteProgressThreshold = 50

/**
 * Reads the one Goodreads book page the user pasted, plus its cover and quote pages. Goodreads has no public API,
 * so the book page's embedded Next.js data (`__NEXT_DATA__` → `apolloState`) and the quote pages' HTML are
 * parsed directly. Both are internal and can change without notice, which is why every field is optional: a
 * missing one drops out of the import instead of failing it.
 *
 * Only `https://www.goodreads.com/book/show/<id>` and `.../work/quotes/<id>` are ever requested - pasted text is
 * reduced to a numeric id first - covers only come from Goodreads' own image hosts, and a redirect elsewhere is
 * refused, so this can't be pointed at arbitrary URLs. Transient failures (timeouts, 429, 5xx) get one retry.
 */
@Singleton
class GoodreadsMetadataFetcher @Inject constructor(
    private val dispatchers: DispatcherProvider,
) {

    suspend fun fetch(link: String): GoodreadsFetchResult = withContext(dispatchers.io) {
        val bookId = goodreadsBookIdOf(link)
            ?: return@withContext GoodreadsFetchResult.Failure(GoodreadsFetchError.INVALID_LINK)
        val canonicalUrl = goodreadsBookUrl(bookId)
        val page = try {
            getWithRetry(canonicalUrl, MaxPageBytes, expectImage = false, isAllowedHost = ::isGoodreadsHost)
        } catch (e: GoodreadsHttpException) {
            val error = when {
                e.statusCode == HttpURLConnection.HTTP_NOT_FOUND -> GoodreadsFetchError.NOT_FOUND
                e.isBotChallenge -> GoodreadsFetchError.BLOCKED
                else -> GoodreadsFetchError.FAILED
            }
            return@withContext GoodreadsFetchResult.Failure(error)
        } catch (_: IOException) {
            return@withContext GoodreadsFetchResult.Failure(GoodreadsFetchError.FAILED)
        }
        runCatchingCancellable { parseBookPage(page.decodeToString(), bookId, canonicalUrl) }
            .getOrNull()
            ?.let { GoodreadsFetchResult.Success(it) }
            ?: GoodreadsFetchResult.Failure(GoodreadsFetchError.FAILED)
    }

    /**
     * Downloads a cover found by [fetch]. Null if it isn't on Goodreads' image hosts, the download fails, or the
     * bytes don't decode as an image - so a truncated file or an HTML error page never becomes a book's cover.
     */
    suspend fun downloadCover(coverUrl: String): ByteArray? = withContext(dispatchers.io) {
        if (!isAllowedCoverUrl(coverUrl)) return@withContext null
        runCatchingCancellable { getWithRetry(coverUrl, MaxCoverBytes, expectImage = true, isAllowedHost = ::isCoverHost) }
            .getOrNull()
            ?.takeIf { it.isDecodableImage() }
    }

    /**
     * Every English quote Goodreads lists for [workId], following the next-page link until Goodreads stops
     * advertising another page, with near-identical texts collapsed. Null only if the first page can't be read;
     * a later page failing just ends the list early.
     */
    suspend fun fetchQuotes(
        workId: String,
        onProgress: (GoodreadsQuoteProgress) -> Unit = {},
    ): List<ParsedQuote>? = withContext(dispatchers.io) {
        if (workId.isEmpty() || !workId.all(Char::isDigit)) return@withContext null
        collectGoodreadsQuotes(onProgress = onProgress) { page ->
            try {
                getWithRetry(goodreadsQuotesUrl(workId, page), MaxPageBytes, expectImage = false, isAllowedHost = ::isGoodreadsHost)
                    .decodeToString()
            } catch (_: IOException) {
                null
            }
        }
    }

    private suspend fun getWithRetry(
        url: String,
        maxBytes: Int,
        expectImage: Boolean,
        isAllowedHost: (String) -> Boolean,
    ): ByteArray {
        repeat(MaxAttempts - 1) {
            try {
                return httpGet(url, maxBytes, expectImage, isAllowedHost)
            } catch (e: GoodreadsHttpException) {
                if (!e.isTransient) throw e
            } catch (_: IOException) {
                // Timeouts and dropped connections are worth one more try.
            }
            delay(RetryDelayMillis)
        }
        return httpGet(url, maxBytes, expectImage, isAllowedHost)
    }

    private fun httpGet(url: String, maxBytes: Int, expectImage: Boolean, isAllowedHost: (String) -> Boolean): ByteArray {
        var uri = url.validatedGoodreadsRequestUri(isAllowedHost) ?: throw GoodreadsHttpException(PolicyViolation)
        repeat(MaxRedirects + 1) { redirectCount ->
            val connection = URL(uri.toASCIIString()).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TimeoutMillis
                connection.readTimeout = TimeoutMillis
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", UserAgent)
                // Fixed language so the page (and the markup parsed from it) doesn't vary with the device locale.
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.8")
                connection.setRequestProperty("Accept", if (expectImage) "image/*" else "text/html")
                val status = connection.responseCode
                if (status in 300..399) {
                    if (redirectCount == MaxRedirects) throw GoodreadsHttpException(PolicyViolation)
                    val location = connection.getHeaderField("Location") ?: throw GoodreadsHttpException(PolicyViolation)
                    uri = uri.resolve(location).toString().validatedGoodreadsRequestUri(isAllowedHost)
                        ?: throw GoodreadsHttpException(PolicyViolation)
                    return@repeat
                }
                // Goodreads' AWS WAF answers suspected bots with a 202 JavaScript challenge page instead of the content.
                if (status == HttpURLConnection.HTTP_ACCEPTED) throw GoodreadsHttpException(BotChallenge)
                if (status !in 200..299) throw GoodreadsHttpException(status)
                if (expectImage && connection.contentType?.startsWith("image/") != true) {
                    throw GoodreadsHttpException(PolicyViolation)
                }
                val declaredLength = connection.contentLengthLong
                if (declaredLength > maxBytes) throw GoodreadsHttpException(PolicyViolation)
                val bytes = connection.inputStream.use { it.readAtMost(maxBytes, declaredLength) }
                    ?: throw GoodreadsHttpException(PolicyViolation)
                // The same challenge can also arrive as a 200; it's tiny and carries the WAF's own markers.
                if (!expectImage && bytes.size < ChallengePageMaxBytes && bytes.decodeToString().looksLikeGoodreadsChallenge()) {
                    throw GoodreadsHttpException(BotChallenge)
                }
                return bytes
            } finally {
                connection.disconnect()
            }
        }
        throw GoodreadsHttpException(PolicyViolation)
    }

    private fun ByteArray.isDecodableImage(): Boolean {
        if (isEmpty()) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(this, 0, size, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0
    }
}

/**
 * [statusCode] is the HTTP status, [PolicyViolation] for a response refused on our side (host, type, size), or
 * [BotChallenge] when Goodreads served its bot challenge instead of content. A challenge is never retried:
 * hammering it only prolongs the block.
 */
private class GoodreadsHttpException(val statusCode: Int) : IOException("Goodreads request failed: HTTP $statusCode") {
    val isBotChallenge: Boolean get() = statusCode == BotChallenge
    val isTransient: Boolean
        get() = statusCode == HttpTooManyRequests || statusCode == HttpURLConnection.HTTP_CLIENT_TIMEOUT || statusCode in 500..599
}

/**
 * The numeric Goodreads book id from whatever the user pasted: a full link (with or without the scheme, `www.`,
 * a locale segment like `/en/`, a slug or query), share-sheet text with the link somewhere inside it, or just the
 * id itself. The match is anchored so look-alike hosts such as `notgoodreads.com` are refused.
 */
internal fun goodreadsBookIdOf(input: String): String? {
    val text = input.trim()
    if (text.length in 1..MaxBookIdDigits && text.all(Char::isDigit)) return text
    return BookLinkRegex.find(text)?.groupValues?.get(1)
}

internal fun goodreadsBookUrl(bookId: String): String = "https://www.goodreads.com/book/show/$bookId"

internal fun goodreadsQuotesUrl(workId: String, page: Int): String =
    "https://www.goodreads.com/work/quotes/$workId?page=$page"

internal fun goodreadsSearchUrl(query: String): String =
    "https://www.goodreads.com/search?q=" + URLEncoder.encode(query.trim(), Charsets.UTF_8.name())

/** Goodreads' AWS WAF bot check: a tiny page that runs JavaScript and then reloads into the real one. */
internal fun String.looksLikeGoodreadsChallenge(): Boolean =
    contains("awsWafCookieDomainList", ignoreCase = true) || contains("gokuProps", ignoreCase = true)

/**
 * Walks a work's quote pages through [loadPage] (the HTML of page N, or null if it couldn't be read), following
 * the next-page link exposed by Goodreads instead of imposing a fixed page limit. Results are keyed by quote id,
 * filtered to English, and have near-identical texts collapsed. Null only if the first page couldn't be read; a
 * later failure just ends the list early. Shared by the direct fetch and the in-app browser, which load pages
 * differently but read them the same way.
 */
internal suspend fun collectGoodreadsQuotes(
    acceptsQuote: suspend (String) -> Boolean = ::isEnglishGoodreadsQuote,
    onProgress: (GoodreadsQuoteProgress) -> Unit = {},
    loadPage: suspend (page: Int) -> String?,
): List<ParsedQuote>? {
    val byId = LinkedHashMap<String, ParsedQuote>()
    val visitedPages = HashSet<Int>()
    var processedFallback = 0
    var knownTotal: Int? = null
    var page = 1
    while (visitedPages.add(page)) {
        val html = loadPage(page) ?: if (page == 1) return null else break
        val articleFragments = html.split("<article>")
        val pageQuotes = parseQuoteArticles(articleFragments)
        val advertisedProgress = goodreadsQuotePageProgress(html)
        knownTotal = advertisedProgress?.total ?: knownTotal
        val rawPageCount = articleFragments.size - 1
        val pageEnd = maxOf(processedFallback + rawPageCount, advertisedProgress?.processed ?: 0)
        var processed = (pageEnd - rawPageCount).coerceAtLeast(processedFallback)
        val acceptedQuotes = classifyGoodreadsQuotes(pageQuotes.map { it.second.quoteText }, acceptsQuote)
        pageQuotes.forEachIndexed { index, (id, quote) ->
            if (acceptedQuotes[index]) byId.putIfAbsent(id, quote)
            processed += 1
            knownTotal?.let { total ->
                onProgress(GoodreadsQuoteProgress(processed.coerceAtMost(total), total))
            }
        }
        processedFallback = pageEnd
        knownTotal?.let { total ->
            if (processed != pageEnd) {
                onProgress(GoodreadsQuoteProgress(pageEnd.coerceAtMost(total), total))
            }
        }
        page = nextGoodreadsQuotesPage(html, page) ?: break
    }
    return byId.values.distinctBy { quoteMatchKey(it.quoteText) }
}

/** Goodreads renders this above quote results, for example "Showing 31 - 60 of 143". */
internal fun goodreadsQuotePageProgress(html: String): GoodreadsQuoteProgress? {
    val match = QuoteResultsRangeRegex.find(html) ?: return null
    val processed = match.groupValues[1].replace(",", "").toIntOrNull() ?: return null
    val total = match.groupValues[2].replace(",", "").toIntOrNull() ?: return null
    if (processed < 0 || total <= 0) return null
    return GoodreadsQuoteProgress(processed.coerceAtMost(total), total)
}

/** The next quote page Goodreads advertises through its mobile load-more or standard next-page link. */
internal fun nextGoodreadsQuotesPage(html: String, currentPage: Int): Int? =
    QuoteNextAnchorRegex.findAll(html)
        .mapNotNull { anchor -> QuotePageParamRegex.find(anchor.value)?.groupValues?.get(1)?.toIntOrNull() }
        .filter { it > currentPage && it <= MaxGoodreadsQuotePage }
        .minOrNull()

internal fun parseBookPage(html: String, bookId: String, canonicalUrl: String): GoodreadsBookMetadata? {
    val nextData = NextDataRegex.find(html)?.groupValues?.get(1) ?: return null
    return parseBookNextData(nextData, bookId, canonicalUrl)
}

/** Reads a book page's `__NEXT_DATA__` JSON - from a fetched page, or straight out of the in-app browser's DOM. */
internal fun parseBookNextData(nextData: String, bookId: String, canonicalUrl: String): GoodreadsBookMetadata? {
    val state = JSONObject(nextData)
        .optJSONObject("props")
        ?.optJSONObject("pageProps")
        ?.optJSONObject("apolloState")
        ?: return null
    val legacyId = bookId.toLongOrNull() ?: return null
    val book = state.keys().asSequence()
        .filter { it.startsWith("Book:") }
        .mapNotNull { state.optJSONObject(it) }
        .firstOrNull { it.optLong("legacyId") == legacyId }
        ?: return null
    fun deref(link: JSONObject?): JSONObject? = link?.optStringOrNull("__ref")?.let { state.optJSONObject(it) }

    val seriesEdge = book.optJSONArray("bookSeries")?.optJSONObject(0)
    val genres = book.optJSONArray("bookGenres")?.let { edges ->
        (0 until edges.length()).mapNotNull { edges.optJSONObject(it)?.optJSONObject("genre")?.optStringOrNull("name") }
    }.orEmpty()
    val work = deref(book.optJSONObject("work"))
    val stats = work?.optJSONObject("stats")
    return GoodreadsBookMetadata(
        canonicalUrl = canonicalUrl,
        series = deref(seriesEdge?.optJSONObject("series"))?.optStringOrNull("title"),
        seriesNumber = seriesEdge?.optStringOrNull("userPosition")?.trimStart('#')?.trim()?.takeIf { it.isNotEmpty() },
        genres = genres.distinctBy { it.lowercase() },
        description = (book.optStringOrNull("description({\"stripped\":true})") ?: book.optStringOrNull("description"))
            ?.htmlToPlainText()
            ?.takeIf { it.isNotEmpty() },
        coverUrl = book.optStringOrNull("imageUrl")?.takeIf(::isAllowedCoverUrl),
        originalPublicationYear = work?.optJSONObject("details")?.optLongOrNull("publicationTime")
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).year }
            ?.takeIf { it in MinPublicationYear..MaxPublicationYear },
        averageRating = stats?.optDoubleOrNull("averageRating")?.toFloat()?.takeIf { it in 0.01f..5f },
        ratingsCount = stats?.optLongOrNull("ratingsCount")?.takeIf { it >= 0 }?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
        workId = work?.optLongOrNull("legacyId")?.takeIf { it > 0 }?.toString(),
    )
}

/**
 * One page of a work's quotes, keyed by Goodreads' quote id so overlapping pages don't duplicate. Parses the
 * mobile layout (`<article>` → `blockquote.quoteBody`, `quoteAuthor`, `quoteBook`, tag links, `likesCount`),
 * which is what [GoodreadsMetadataFetcher]'s phone user agent is served; an empty result marks the last page.
 */
internal fun parseQuotesPage(html: String): List<Pair<String, ParsedQuote>> = parseQuoteArticles(html.split("<article>"))

private fun parseQuoteArticles(articleFragments: List<String>): List<Pair<String, ParsedQuote>> =
    articleFragments.drop(1).mapNotNull { article ->
        val body = QuoteBodyRegex.find(article)?.groupValues?.get(1) ?: return@mapNotNull null
        val text = body.htmlToPlainText()
        if (!hasGoodreadsQuoteMinimumLength(text)) return@mapNotNull null
        val id = QuoteIdRegex.find(article)?.groupValues?.get(1) ?: "text:${quoteMatchKey(text)}"
        val likes = QuoteLikesRegex.find(article)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        id to ParsedQuote(
            quoteText = text,
            author = QuoteAuthorRegex.find(article)?.groupValues?.get(1)?.htmlToPlainText()?.trimEnd(',', ' ')?.takeIf { it.isNotEmpty() },
            sourceTitle = QuoteBookRegex.find(article)?.groupValues?.get(1)?.htmlToPlainText()?.takeIf { it.isNotEmpty() },
            tags = QuoteTagRegex.findAll(article).map { it.groupValues[1].htmlToPlainText() }.filter { it.isNotEmpty() }.toList(),
            likesCount = likes,
        )
    }

/** Runs ML-backed quote classification in bounded batches while retaining input order. */
internal suspend fun classifyGoodreadsQuotes(
    texts: List<String>,
    acceptsQuote: suspend (String) -> Boolean = ::isEnglishGoodreadsQuote,
): List<Boolean> = texts.chunked(GoodreadsQuoteClassificationConcurrency).flatMap { batch ->
    coroutineScope {
        batch.map { text -> async { acceptsQuote(text) } }.awaitAll()
    }
}

/**
 * Markup to plain text without Android's `Html` (keeps this parsing unit-testable): line breaks and paragraph ends
 * become newlines, other tags go, then entities are decoded - in that order, so an escaped `&lt;b&gt;` stays text.
 */
internal fun String.htmlToPlainText(): String =
    replace(BreakTagRegex, "\n")
        .replace(TagRegex, "")
        .decodeHtmlEntities()
        .replace(InlineSpaceRegex, " ")
        .lines()
        .joinToString("\n") { it.trim() }
        .replace(ExtraBlankLinesRegex, "\n\n")
        .trim()

private fun String.decodeHtmlEntities(): String =
    EntityRegex.replace(this) { match ->
        val entity = match.groupValues[1]
        val decoded = when {
            entity.startsWith("#x", ignoreCase = true) -> entity.drop(2).toIntOrNull(16)?.toCodePointString()
            entity.startsWith("#") -> entity.drop(1).toIntOrNull()?.toCodePointString()
            else -> NamedEntities[entity]
        }
        decoded ?: match.value
    }

private fun Int.toCodePointString(): String? =
    if (Character.isValidCodePoint(this) && this != 0) String(Character.toChars(this)) else null

internal fun isGoodreadsHost(host: String): Boolean = host.isHostOrSubdomainOf("goodreads.com")

private fun isCoverHost(host: String): Boolean =
    CoverHosts.any { host.isHostOrSubdomainOf(it) }

private fun isAllowedCoverUrl(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    return uri.scheme == "https" && isCoverHost(uri.host.orEmpty())
}

private fun String.validatedGoodreadsRequestUri(isAllowedHost: (String) -> Boolean): URI? =
    toSafeHttpsUri()?.takeIf { uri -> isAllowedHost(uri.host.orEmpty()) }

private fun String.isHostOrSubdomainOf(domain: String): Boolean {
    val host = lowercase()
    return host == domain || host.endsWith(".$domain")
}

private fun JSONObject.optStringOrNull(name: String): String? =
    if (has(name) && !isNull(name)) optString(name).trim().takeIf { it.isNotEmpty() } else null

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name).takeIf { it.isFinite() } else null

private val BookLinkRegex = Regex(
    """(?:^|[\s"'(<])(?:https?://)?(?:www\.)?goodreads\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?book/show/(\d{1,$MaxBookIdDigits})(?=$|[^\d])""",
    RegexOption.IGNORE_CASE,
)
private val NextDataRegex = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
private val CoverHosts = listOf("media-amazon.com", "gr-assets.com", "goodreads.com")
// Either quote style: the served page uses class='...', but a browser's serialized DOM (outerHTML) uses class="...".
private val QuoteBodyRegex = Regex("""<blockquote class=["']quoteBody["']>(.*?)</blockquote>""", RegexOption.DOT_MATCHES_ALL)
private val QuoteIdRegex = Regex("""like_id_quote_(\d+)""")
private val QuoteLikesRegex = Regex("""<span class=["']likesCount["']>([\d,]+)</span>""")
private val QuoteAuthorRegex = Regex("""<span class=["']quoteAuthor["']>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
private val QuoteBookRegex = Regex("""<span class=["']quoteBook["']>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
private val QuoteTagRegex = Regex("""href="/quotes/tag/[^"]+">([^<]+)<""")
private val QuoteNextAnchorRegex = Regex(
    """<a\b(?=[^>]*(?:class=["'][^"']*(?:jsLoadMore|next_page)[^"']*["']|rel=["']next["']))[^>]*>""",
    RegexOption.IGNORE_CASE,
)
private val QuotePageParamRegex = Regex("""(?:[?&]|&amp;)page=(\d+)""", RegexOption.IGNORE_CASE)
private val QuoteResultsRangeRegex = Regex(
    """Showing\s+[\d,]+\s*[-–]\s*([\d,]+)\s+of\s+([\d,]+)""",
    RegexOption.IGNORE_CASE,
)
private val BreakTagRegex = Regex("""<br\s*/?>|</p\s*>""", RegexOption.IGNORE_CASE)
private val TagRegex = Regex("""<[^>]*>""")
private val EntityRegex = Regex("""&(#\d{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]{2,8});""")
private val InlineSpaceRegex = Regex("""[ \t ]+""")
private val ExtraBlankLinesRegex = Regex("""\n{3,}""")
private val NamedEntities = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
    "hellip" to "…", "mdash" to "—", "ndash" to "–",
)

/** Very short fragments are noisy and cannot be matched reliably in the reader. */
internal fun hasGoodreadsQuoteMinimumLength(text: String): Boolean =
    text.codePointCount(0, text.length) >= MinQuoteChars

private const val MinQuoteChars = 12
private const val MaxBookIdDigits = 12
private const val GoodreadsQuoteClassificationConcurrency = 8
private const val MinPublicationYear = 1000
private const val MaxPublicationYear = 2200
private const val MaxAttempts = 2
private const val MaxRedirects = 3
private const val MaxGoodreadsQuotePage = 500
private const val RetryDelayMillis = 800L
private const val HttpTooManyRequests = 429
private const val PolicyViolation = -1
private const val BotChallenge = -2

/** Real book and quote pages run to tens of kilobytes; the WAF challenge is a couple of kilobytes. */
private const val ChallengePageMaxBytes = 16 * 1024
private const val TimeoutMillis = 15_000
private const val MaxPageBytes = 8 * 1024 * 1024
private const val MaxCoverBytes = 10 * 1024 * 1024
private const val UserAgent =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
