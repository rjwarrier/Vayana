package com.vayana.feature.gutenberg

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.vayana.core.common.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Talks to gutenberg.org only, and only for what the reader opens: no prefetching or crawling, as Gutenberg asks of
 * apps. Pages seen in the last few minutes are served from memory, covers from a small memory cache.
 */
@Singleton
class GutenbergClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    private val pages = object : LruCache<String, Pair<Long, String>>(PageCacheEntries) {}
    private val covers = object : LruCache<String, ImageBitmap>(CoverCacheBytes) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * BytesPerPixel
    }
    private val coverDownloads = Semaphore(ConcurrentCoverDownloads)

    // Books without a cover answer 404: remember that, so scrolling past them again doesn't ask again.
    private val missingCovers: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** A cover already in memory, so a tile scrolled back into view draws it at once. */
    fun cachedCover(url: String): ImageBitmap? = covers.get(url)

    suspend fun listing(url: String): GutenbergListing = GutenbergFeeds.parseListing(page(url))

    suspend fun book(id: Long): GutenbergBook? = GutenbergFeeds.parseBook(page(GutenbergFeeds.bookUrl(id)), id)

    /** A cover, or null when the book has none (Gutenberg answers 404) or it can't be fetched right now. */
    suspend fun cover(url: String): ImageBitmap? {
        covers.get(url)?.let { return it }
        if (url in missingCovers) return null
        return coverDownloads.withPermit {
            covers.get(url) ?: withContext(dispatchers.io) {
                val bytes = try {
                    get(url, MaxCoverBytes)
                } catch (_: NotFoundException) {
                    missingCovers += url
                    null
                } catch (_: IOException) {
                    null
                }
                bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }?.also { covers.put(url, it) }
            }
        }
    }

    /**
     * Downloads [edition] into the app's cache as `pg<id>.epub` (the name tells the importer the format), reporting
     * the fraction done; storage maintenance clears these once imported. Cancelling stops it and removes the part.
     */
    suspend fun download(id: Long, edition: GutenbergEdition, onProgress: (Float) -> Unit): File = withContext(dispatchers.io) {
        val directory = File(context.cacheDir, CacheDirectory).apply { mkdirs() }
        val suffix = if (edition.kind == GutenbergEditionKind.WITH_IMAGES) "-images" else ""
        val target = File(directory, "pg$id$suffix.epub")
        val part = File(directory, "${target.name}.part")
        // Fetched a moment ago and not yet cleared (the import skipped it, or it's tried again): no need to fetch twice.
        if (edition.sizeBytes != null && target.length() == edition.sizeBytes) return@withContext target
        try {
            open(edition.url).useConnection { connection ->
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: edition.sizeBytes ?: -1L
                if (total > MaxBookBytes) throw IOException("Book is too large")
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
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
                            // Whole percents only: a 25 MB book would otherwise update the screen hundreds of times.
                            val percent = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else -1
                            if (percent > reported) {
                                reported = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
            if (!part.renameTo(target)) throw IOException("Could not save the book")
            target
        } finally {
            part.delete()
        }
    }

    private suspend fun page(url: String): String = withContext(dispatchers.io) {
        val now = System.currentTimeMillis()
        pages.get(url)?.takeIf { now - it.first < PageCacheMillis }?.let { return@withContext it.second }
        get(url, MaxFeedBytes).decodeToString().also { pages.put(url, now to it) }
    }

    private fun get(url: String, maxBytes: Int): ByteArray =
        open(url).useConnection { connection -> connection.inputStream.use { it.readAtMost(maxBytes) } }

    /**
     * Connects to [url], following Gutenberg's own redirects (a download goes to its `/cache/` copy) but never off
     * gutenberg.org or away from HTTPS; a 404 or other failure throws.
     */
    private fun open(url: String): HttpURLConnection {
        var current = url
        repeat(MaxRedirects + 1) {
            if (!GutenbergFeeds.isGutenbergUrl(current)) throw IOException("Not a Gutenberg address")
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = TimeoutMillis
                readTimeout = TimeoutMillis
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", UserAgent)
            }
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> return connection
                in RedirectCodes -> {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    current = URI(current).resolve(location ?: throw IOException("Redirect without a location")).toString()
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    connection.disconnect()
                    throw NotFoundException()
                }
                else -> {
                    connection.disconnect()
                    throw IOException("HTTP $status")
                }
            }
        }
        throw IOException("Too many redirects")
    }

    private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }

    private fun InputStream.readAtMost(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > maxBytes) throw IOException("Response too large")
            output.write(buffer, 0, count)
        }
    }

    private class NotFoundException : IOException("Not found")

    internal companion object {
        const val CacheDirectory = "gutenberg"
        private const val UserAgent = "Vayana/1.0 (https://github.com/rjwarrier/Vayana)"
        private const val TimeoutMillis = 20_000
        private const val MaxRedirects = 3
        private val RedirectCodes = setOf(301, 302, 303, 307, 308)
        private const val MaxFeedBytes = 2 * 1024 * 1024
        private const val MaxCoverBytes = 2 * 1024 * 1024
        private const val MaxBookBytes = 150L * 1024 * 1024
        private const val DownloadBufferBytes = 64 * 1024
        private const val PageCacheEntries = 24
        private const val PageCacheMillis = 10 * 60 * 1000L
        private const val CoverCacheBytes = 24 * 1024 * 1024
        private const val BytesPerPixel = 4
        private const val ConcurrentCoverDownloads = 4
    }
}
