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
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Talks to gutenberg.org only for what the reader opens, plus one launch-time refresh of the first Popular page: no
 * catalogue crawling. Pages seen in the last few minutes are served from memory. Every page and cover fetched is also
 * kept on disk (in the cache, which Android may clear), so a list opened before shows at once while it's refreshed,
 * and still shows offline; the Popular list ships with the app for the very first open.
 */
@Singleton
class GutenbergClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    private val pages = object : LruCache<String, CachedPage>(PageCacheEntries) {}
    private val listings = object : LruCache<String, GutenbergListing>(PageCacheEntries) {}
    private val covers = object : LruCache<String, ImageBitmap>(CoverCacheBytes) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * BytesPerPixel
    }
    private val coverDownloads = Semaphore(ConcurrentCoverDownloads)

    // Books without a cover answer 404: remember a bounded set, so scrolling back doesn't ask again without retaining
    // every missing cover encountered in a long browsing session.
    private val missingCovers = object : LruCache<String, Boolean>(MissingCoverEntries) {}

    // A tile and the details sheet can ask for the same cover together. Let them share one disk decode/download.
    private val coverLoads = ConcurrentHashMap<String, CompletableDeferred<ImageBitmap?>>()
    // The launch warm-up and the catalogue screen can overlap. Share the parse/fetch instead of doing both twice.
    private val listingLoads = ConcurrentHashMap<String, CompletableDeferred<GutenbergListing>>()

    /** A cover already in memory, so a tile scrolled back into view draws it at once. */
    fun cachedCover(url: String): ImageBitmap? = covers.get(url)

    /** The list as last fetched, or the copy bundled with the app, or null: to show while [listing] fetches it again. */
    suspend fun savedListing(url: String): GutenbergListing? {
        listings.get(url)?.let { return it }
        return withContext(dispatchers.io) {
            val page = pages.get(url) ?: savedPage(url)?.also { pages.put(url, it) } ?: bundledPage(url)
                ?: return@withContext null
            runCatching { GutenbergFeeds.parseListing(page.body) }.getOrNull()?.also { listings.put(url, it) }
        }
    }

    /**
     * Whether [url] was fetched in the last few minutes (this run or, on disk, the last), so what [savedListing] gives
     * needs no refresh.
     */
    suspend fun isFresh(url: String): Boolean {
        val now = System.currentTimeMillis()
        pages.get(url)?.let { return now - it.fetchedAt < PageCacheMillis }
        return withContext(dispatchers.io) { pageFile(url).lastModified().let { it > 0 && now - it < PageCacheMillis } }
    }

    /** Loads the first screen into parsed memory, refreshing its saved copy only when stale. */
    suspend fun warmDefaultListing() = listing(GutenbergFeeds.listUrl(GutenbergQuery()))

    // Parsed off the main thread too: a list page is some 50 KB of XML. A fresh in-memory page keeps its parsed form,
    // avoiding another DOM build when the same list is revisited.
    suspend fun listing(url: String): GutenbergListing {
        freshListing(url)?.let { return it }
        val mine = CompletableDeferred<GutenbergListing>()
        listingLoads.putIfAbsent(url, mine)?.let { existing ->
            return try {
                existing.await()
            } catch (cancellation: CancellationException) {
                // If the request owner left its screen, an active waiter takes over instead of inheriting cancellation.
                currentCoroutineContext().ensureActive()
                listingLoads.remove(url, existing)
                listing(url)
            }
        }
        try {
            val result = withContext(dispatchers.io) {
                freshListing(url) ?: GutenbergFeeds.parseListing(page(url)).also { listings.put(url, it) }
            }
            mine.complete(result)
            return result
        } catch (error: Throwable) {
            mine.completeExceptionally(error)
            throw error
        } finally {
            listingLoads.remove(url, mine)
        }
    }

    private fun freshListing(url: String): GutenbergListing? {
        val page = pages.get(url) ?: return null
        return if (System.currentTimeMillis() - page.fetchedAt < PageCacheMillis) listings.get(url) else null
    }

    /**
     * A book's page. Its editions and sizes hardly ever change, so one saved in the last month is used as it is; an
     * older one only when gutenberg.org can't be reached.
     */
    suspend fun book(id: Long): GutenbergBook? = withContext(dispatchers.io) {
        val url = GutenbergFeeds.bookUrl(id)
        val xml = savedPage(url, maxAgeMillis = BookPageMaxAgeMillis)?.body ?: try {
            page(url)
        } catch (error: IOException) {
            savedPage(url)?.body ?: throw error
        }
        GutenbergFeeds.parseBook(xml, id)
    }

    /** A cover, or null when the book has none (Gutenberg answers 404) or it can't be fetched right now. */
    suspend fun cover(url: String): ImageBitmap? {
        covers.get(url)?.let { return it }
        if (missingCovers.get(url) == true) return null

        val mine = CompletableDeferred<ImageBitmap?>()
        coverLoads.putIfAbsent(url, mine)?.let { existing ->
            return try {
                existing.await()
            } catch (cancellation: CancellationException) {
                // The request that owned the shared load may have left the screen. An active waiter still needs the
                // cover, so it becomes the next owner; a waiter that was itself cancelled stops here.
                currentCoroutineContext().ensureActive()
                coverLoads.remove(url, existing)
                cover(url)
            }
        }
        try {
            val result = withContext(dispatchers.io) {
                val saved = coverFile(url)
                BitmapFactory.decodeFile(saved.path)?.asImageBitmap()?.also {
                    covers.put(url, it)
                    // Trimming drops the least recently used covers, not the ones saved first.
                    saved.setLastModified(System.currentTimeMillis())
                } ?: coverDownloads.withPermit {
                    covers.get(url) ?: run {
                        val bytes = try {
                            get(url, MaxCoverBytes)
                        } catch (_: NotFoundException) {
                            missingCovers.put(url, true)
                            null
                        } catch (_: IOException) {
                            null
                        }
                        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }?.also {
                            covers.put(url, it)
                            save(saved, bytes, MaxSavedCovers)
                        }
                    }
                }
            }
            mine.complete(result)
            return result
        } catch (error: Throwable) {
            mine.completeExceptionally(error)
            throw error
        } finally {
            coverLoads.remove(url, mine)
        }
    }

    /**
     * Downloads [edition] into the app's cache as `pg<id>.epub` (the name tells the importer the format), reporting
     * the fraction done; storage maintenance clears these once imported. A partial file survives cancellation and is
     * resumed with an HTTP Range request the next time the same edition is chosen.
     */
    suspend fun download(id: Long, edition: GutenbergEdition, onProgress: (Float) -> Unit): File = withContext(dispatchers.io) {
        val directory = File(context.cacheDir, CacheDirectory).apply { mkdirs() }
        val suffix = if (edition.kind == GutenbergEditionKind.WITH_IMAGES) "-images" else ""
        val target = File(directory, "pg$id$suffix.epub")
        val part = File(directory, "${target.name}.part")
        // Fetched a moment ago and not yet cleared (the import skipped it, or it's tried again): no need to fetch twice.
        if (edition.sizeBytes != null && target.length() == edition.sizeBytes) return@withContext target
        if (target.exists()) target.delete()
        if (part.length() > MaxBookBytes || edition.sizeBytes?.let { part.length() > it } == true) part.delete()
        if (edition.sizeBytes != null && part.length() == edition.sizeBytes) {
            if (!part.renameTo(target)) throw IOException("Could not save the book")
            return@withContext target
        }
        val requestedOffset = part.length().takeIf { it > 0L }
        withConnection(edition.url, rangeStart = requestedOffset) { connection ->
            val resumed = requestedOffset != null && connection.responseCode == HttpURLConnection.HTTP_PARTIAL &&
                connection.getHeaderField("Content-Range")?.startsWith("bytes $requestedOffset-") == true
            val start = if (resumed) requestedOffset else 0L
            val responseBytes = connection.contentLengthLong.takeIf { it > 0 }
            val total = edition.sizeBytes ?: responseBytes?.let { it + start } ?: -1L
            if (total > MaxBookBytes) {
                part.delete()
                throw IOException("Book is too large")
            }
            connection.inputStream.use { input ->
                FileOutputStream(part, resumed).use { output ->
                    val buffer = ByteArray(DownloadBufferBytes)
                    var done = start
                    var reported = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else -1
                    if (reported >= 0) onProgress(reported / 100f)
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
        if (edition.sizeBytes != null && part.length() != edition.sizeBytes) {
            // A short response is useful on retry and remains resumable; an oversized one is corrupt.
            if (part.length() > edition.sizeBytes) part.delete()
            throw IOException("Incomplete book download")
        }
        if (!part.renameTo(target)) throw IOException("Could not save the book")
        target
    }

    /** Clears saved feeds and covers, but keeps EPUBs and their resumable partial files. */
    suspend fun clearSavedCatalogue() = withContext(dispatchers.io) {
        pages.evictAll()
        listings.evictAll()
        covers.evictAll()
        missingCovers.evictAll()
        listOf(PagesDirectory, CoversDirectory).forEach { directory ->
            File(context.cacheDir, directory).deleteRecursively()
        }
    }

    private suspend fun page(url: String): String = withContext(dispatchers.io) {
        val now = System.currentTimeMillis()
        pages.get(url)?.takeIf { now - it.fetchedAt < PageCacheMillis }?.let { return@withContext it.body }
        savedPage(url, PageCacheMillis)?.also { pages.put(url, it) }?.let { return@withContext it.body }
        get(url, MaxFeedBytes).decodeToString().also {
            pages.put(url, CachedPage(now, it))
            listings.remove(url)
            save(pageFile(url), it.encodeToByteArray(), MaxSavedPages)
        }
    }

    private fun savedPage(url: String, maxAgeMillis: Long = Long.MAX_VALUE): CachedPage? {
        val file = pageFile(url)
        val fetchedAt = file.lastModified()
        if (fetchedAt <= 0 || System.currentTimeMillis() - fetchedAt > maxAgeMillis) return null
        return runCatching { CachedPage(fetchedAt, file.readText()) }.getOrNull()
    }

    private fun bundledPage(url: String): CachedPage? =
        if (url != GutenbergFeeds.listUrl(GutenbergQuery())) {
            null
        } else {
            runCatching { CachedPage(0, context.assets.open(BundledPopular).use { it.readBytes().decodeToString() }) }.getOrNull()
        }

    private fun pageFile(url: String) = File(File(context.cacheDir, PagesDirectory), "${key(url)}.xml")

    private fun coverFile(url: String) = File(File(context.cacheDir, CoversDirectory), "${key(url)}.jpg")

    /** Writes via a temporary file, so a half-written copy is never read; keeps the newest [maxFiles] in the folder. */
    private fun save(file: File, bytes: ByteArray, maxFiles: Int) {
        runCatching {
            val directory = file.parentFile ?: return
            directory.mkdirs()
            val temporary = File(directory, "${file.name}.tmp")
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(file)) {
                temporary.delete()
                return
            }
            // Names only (no stat per file) until the folder is well over its limit; then trim back to it.
            if ((directory.list()?.size ?: 0) <= maxFiles + TrimSlack) return
            val saved = directory.listFiles()?.filter { it.isFile } ?: return
            saved.sortedBy { it.lastModified() }.take(saved.size - maxFiles).forEach { it.delete() }
        }
    }

    private fun key(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(KeyLength)

    private suspend fun get(url: String, maxBytes: Int): ByteArray =
        withConnection(url) { connection -> connection.inputStream.use { it.readAtMost(maxBytes) } }

    /**
     * Connects to [url], following Gutenberg's own redirects (a download goes to its `/cache/` copy) but never off
     * gutenberg.org or away from HTTPS; a 404 or other failure throws.
     */
    private suspend fun <T> withConnection(
        url: String,
        rangeStart: Long? = null,
        block: suspend (HttpURLConnection) -> T,
    ): T {
        var current = url
        repeat(MaxRedirects + 1) {
            currentCoroutineContext().ensureActive()
            if (!GutenbergFeeds.isGutenbergUrl(current)) throw IOException("Not a Gutenberg address")
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = TimeoutMillis
                readTimeout = TimeoutMillis
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", UserAgent)
                rangeStart?.let { setRequestProperty("Range", "bytes=$it-") }
            }
            // responseCode and InputStream reads block. Disconnecting makes both return promptly when the owner job
            // is cancelled by a new search/filter, instead of holding an IO thread until the network timeout.
            val closeOnCancel = currentCoroutineContext()[Job]?.invokeOnCompletion { connection.disconnect() }
            try {
                when (val status = connection.responseCode) {
                    HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL -> return block(connection)
                    in RedirectCodes -> {
                        val location = connection.getHeaderField("Location")
                        current = URI(current).resolve(location ?: throw IOException("Redirect without a location")).toString()
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> throw NotFoundException()
                    else -> throw IOException("HTTP $status")
                }
            } catch (error: IOException) {
                // disconnect() is how cancellation wakes a blocking response/read; preserve cancellation semantics
                // rather than turning it into an ordinary network failure in the caller.
                currentCoroutineContext().ensureActive()
                throw error
            } finally {
                closeOnCancel?.dispose()
                connection.disconnect()
            }
        }
        throw IOException("Too many redirects")
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

    private class NotFoundException : IOException("Not found")

    private data class CachedPage(val fetchedAt: Long, val body: String)

    internal companion object {
        const val CacheDirectory = "gutenberg"
        private const val PagesDirectory = "gutenberg_pages"
        private const val CoversDirectory = "gutenberg_covers"
        private const val BundledPopular = "gutenberg/popular.xml"
        private const val MaxSavedPages = 80
        private const val MaxSavedCovers = 400
        private const val MissingCoverEntries = 512
        private const val KeyLength = 32
        private const val TrimSlack = 20
        private const val BookPageMaxAgeMillis = 30L * 24 * 60 * 60 * 1000
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
