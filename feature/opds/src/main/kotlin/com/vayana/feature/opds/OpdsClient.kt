package com.vayana.feature.opds

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.vayana.core.common.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Why a catalogue request failed, in terms the screen can tell the reader. */
enum class OpdsFailure { UNAUTHORIZED, NOT_A_CATALOGUE, UNREACHABLE }

class OpdsException(val failure: OpdsFailure, message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Talks to the catalogue servers the reader added, and only to them: a login is sent to the catalogue's own host and
 * port, never to another address a feed or redirect points at, and never downgraded from HTTPS to HTTP.
 */
@Singleton
class OpdsClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    private val covers = object : LruCache<String, ImageBitmap>(CoverCacheBytes) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * BytesPerPixel
    }
    private val missingCovers = object : LruCache<String, Boolean>(MissingCoverEntries) {}
    private val coverDownloads = Semaphore(ConcurrentCoverDownloads)
    private val feeds = object : LruCache<String, CachedFeed>(FeedCacheEntries) {}

    /** Drops what is remembered of catalogues, after one was edited or removed. */
    fun forget() {
        feeds.evictAll()
        covers.evictAll()
        missingCovers.evictAll()
    }

    fun cachedCover(catalog: OpdsCatalog, url: String): ImageBitmap? = covers.get(coverKey(catalog, url))

    /**
     * The feed at [url]. One fetched in the last few minutes is reused, so going back into a folder or paging again
     * costs nothing; [refresh] (a retry) always asks the server.
     */
    suspend fun feed(catalog: OpdsCatalog, url: String, refresh: Boolean = false): OpdsFeed {
        val key = "${catalog.id}|${catalog.username}|$url"
        val now = System.currentTimeMillis()
        if (!refresh) feeds.get(key)?.takeIf { now - it.fetchedAt < FeedCacheMillis }?.let { return it.feed }
        val xml = get(catalog, url, MaxFeedBytes).toString(Charsets.UTF_8)
        val parsed = try {
            OpdsFeeds.parseFeed(xml, url)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            throw OpdsException(OpdsFailure.NOT_A_CATALOGUE, "Not an OPDS feed", error)
        }
        // Servers sometimes repeat an entry id, and the list keys its rows by it.
        val feed = parsed.copy(entries = parsed.entries.distinctBy { it.id })
        feeds.put(key, CachedFeed(now, feed))
        return feed
    }

    /**
     * Checks an address before it is saved: it must answer with an OPDS feed. A bare server address is also tried with
     * /opds (where Calibre-Web and Calibre serve it). Returns the working address and the feed.
     */
    suspend fun check(catalog: OpdsCatalog): Pair<String, OpdsFeed> {
        val candidates = listOfNotNull(
            catalog.url,
            catalog.url.trimEnd('/').takeIf { !it.contains("opds", ignoreCase = true) }?.plus("/opds"),
        )
        var failure: OpdsException? = null
        for (candidate in candidates) {
            try {
                return candidate to feed(catalog.copy(url = candidate), candidate, refresh = true)
            } catch (error: OpdsException) {
                // Only a page that is not a catalogue is worth another address; an unreachable server or a wrong
                // login would fail the same way.
                if (failure == null || error.failure == OpdsFailure.UNAUTHORIZED) failure = error
                if (error.failure != OpdsFailure.NOT_A_CATALOGUE) throw error
            }
        }
        throw failure ?: OpdsException(OpdsFailure.UNREACHABLE, "No address")
    }

    /** The URL that lists books matching [terms] in the feed at [feedUrl], or null when the catalogue can't search. */
    suspend fun searchUrl(catalog: OpdsCatalog, search: OpdsSearch, terms: String): String? {
        val template = when (search) {
            is OpdsSearch.Template -> search.template
            is OpdsSearch.Description -> OpdsFeeds.searchTemplateFromDescription(
                get(catalog, search.url, MaxFeedBytes).toString(Charsets.UTF_8),
            )
        } ?: return null
        val base = (search as? OpdsSearch.Description)?.url ?: catalog.url
        return OpdsFeeds.searchUrl(template, terms, base)
    }

    suspend fun cover(catalog: OpdsCatalog, url: String): ImageBitmap? {
        val key = coverKey(catalog, url)
        covers.get(key)?.let { return it }
        if (missingCovers.get(key) == true) return null
        return coverDownloads.withPermit {
            val bitmap = try {
                val bytes = get(catalog, url, MaxCoverBytes)
                withContext(dispatchers.io) { decode(bytes) }
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                null
            }
            if (bitmap != null) covers.put(key, bitmap) else missingCovers.put(key, true)
            bitmap
        }
    }

    /** Fetches [acquisition] into the cache and returns the file, named so the importer recognises its format. */
    suspend fun download(
        catalog: OpdsCatalog,
        entry: OpdsEntry,
        acquisition: OpdsAcquisition,
        onProgress: (Float) -> Unit,
    ): File = withContext(dispatchers.io) {
        val directory = File(context.cacheDir, CacheDirectory).apply { mkdirs() }
        val target = File(directory, "${fileStem(entry)}.${acquisition.format.extension}")
        val part = File(directory, "${target.name}.part")
        part.delete()
        withConnection(catalog, acquisition.url) { connection ->
            val total = acquisition.sizeBytes ?: connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            if (total > MaxBookBytes) throw IOException("Book is too large")
            connection.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(DownloadBufferBytes)
                    var done = 0L
                    var reported = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        done += read
                        if (done > MaxBookBytes) throw IOException("Book is too large")
                        output.write(buffer, 0, read)
                        // Whole percents only: a big book would otherwise update the screen hundreds of times.
                        val percent = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else -1
                        if (percent > reported) {
                            reported = percent
                            if (percent >= 0) onProgress(percent / 100f)
                        }
                    }
                }
            }
        }
        if (part.length() == 0L) {
            part.delete()
            throw IOException("Empty download")
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException("Could not save the book")
        target
    }

    // Callers run on the main thread (view models): blocking network reads belong on the IO dispatcher.
    private suspend fun get(catalog: OpdsCatalog, url: String, maxBytes: Int): ByteArray = withContext(dispatchers.io) {
        withConnection(catalog, url) { connection -> connection.inputStream.use { it.readAtMost(maxBytes) } }
    }

    private suspend fun <T> withConnection(
        catalog: OpdsCatalog,
        url: String,
        block: suspend (HttpURLConnection) -> T,
    ): T {
        var current = url
        repeat(MaxRedirects + 1) {
            currentCoroutineContext().ensureActive()
            val target = URI(current)
            if (target.scheme?.lowercase() !in setOf("http", "https") || target.host.isNullOrEmpty()) {
                throw OpdsException(OpdsFailure.UNREACHABLE, "Not a web address")
            }
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = TimeoutMillis
                readTimeout = TimeoutMillis
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", UserAgent)
                setRequestProperty("Accept", "application/atom+xml, application/xml;q=0.9, */*;q=0.8")
                if (catalog.hasLogin && receivesLogin(catalog.url, current)) {
                    val login = "${catalog.username}:${catalog.password}".toByteArray(Charsets.UTF_8)
                    setRequestProperty("Authorization", "Basic " + Base64.encodeToString(login, Base64.NO_WRAP))
                }
            }
            // responseCode and reads block; disconnecting wakes them when the screen moves on to something else.
            val closeOnCancel = currentCoroutineContext()[Job]?.invokeOnCompletion { connection.disconnect() }
            try {
                when (val status = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> return block(connection)
                    in RedirectCodes -> {
                        val location = connection.getHeaderField("Location")
                            ?: throw OpdsException(OpdsFailure.UNREACHABLE, "Redirect without a location")
                        val next = URI(current).resolve(location).toString()
                        if (current.startsWith("https://", ignoreCase = true) && next.startsWith("http://", ignoreCase = true)) {
                            throw OpdsException(OpdsFailure.UNREACHABLE, "Redirect from HTTPS to HTTP")
                        }
                        current = next
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                        throw OpdsException(OpdsFailure.UNAUTHORIZED, "HTTP $status")
                    else -> throw OpdsException(OpdsFailure.UNREACHABLE, "HTTP $status")
                }
            } catch (error: OpdsException) {
                throw error
            } catch (error: IOException) {
                // disconnect() is how cancellation wakes a blocking read; keep cancellation semantics for the caller.
                currentCoroutineContext().ensureActive()
                throw OpdsException(OpdsFailure.UNREACHABLE, error.message ?: "Network error", error)
            } finally {
                closeOnCancel?.dispose()
                connection.disconnect()
            }
        }
        throw OpdsException(OpdsFailure.UNREACHABLE, "Too many redirects")
    }

    private suspend fun InputStream.readAtMost(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > maxBytes) throw IOException("Response too large")
            output.write(buffer, 0, count)
        }
    }

    private fun decode(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MaxCoverEdge && bounds.outHeight / (sample * 2) >= MaxCoverEdge) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }

    private fun coverKey(catalog: OpdsCatalog, url: String) = "${catalog.id}|$url"

    private fun fileStem(entry: OpdsEntry): String {
        val readable = entry.title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().take(60).ifEmpty { "book" }
        val digest = MessageDigest.getInstance("SHA-256").digest(entry.id.toByteArray()).joinToString("") { "%02x".format(it) }
        return "$readable-${digest.take(8)}"
    }

    private class CachedFeed(val fetchedAt: Long, val feed: OpdsFeed)

    internal companion object {
        const val CacheDirectory = "opds"
        private const val UserAgent = "Vayana/1.0 (https://github.com/rjwarrier/Vayana)"
        private const val TimeoutMillis = 20_000
        private const val MaxRedirects = 5
        private val RedirectCodes = setOf(301, 302, 303, 307, 308)
        private const val MaxFeedBytes = 5 * 1024 * 1024
        private const val MaxCoverBytes = 3 * 1024 * 1024
        private const val MaxBookBytes = 300L * 1024 * 1024
        private const val DownloadBufferBytes = 64 * 1024
        private const val CoverCacheBytes = 16 * 1024 * 1024
        private const val MaxCoverEdge = 400
        private const val BytesPerPixel = 4
        private const val MissingCoverEntries = 256
        private const val ConcurrentCoverDownloads = 4

        /**
         * Only the catalogue's own server gets its login: same scheme, host and port, or the same host once the server
         * has moved to HTTPS (a redirect that upgrades is safe; one that downgrades never gets it).
         */
        fun receivesLogin(catalogUrl: String, target: String): Boolean = runCatching {
            val first = URI(catalogUrl)
            val second = URI(target)
            fun port(uri: URI) = if (uri.port >= 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
            val sameHost = first.host.equals(second.host, true)
            val sameOrigin = first.scheme.equals(second.scheme, true) && port(first) == port(second)
            val upgraded = first.scheme.equals("http", true) && second.scheme.equals("https", true)
            sameHost && (sameOrigin || upgraded)
        }.getOrDefault(false)

        private const val FeedCacheEntries = 24
        private const val FeedCacheMillis = 5 * 60 * 1000L
    }
}
