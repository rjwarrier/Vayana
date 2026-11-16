package com.vayana.app.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vayana.core.database.model.Book
import com.vayana.core.filesystem.StorageRoots
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

internal data class ReadingWidgetCover(
    val key: String,
    val version: String,
    val bookId: Long,
    val path: String,
    val sourceVersion: String,
    val bytes: ByteArray,
) {
    val uri: String get() = "content://com.vayana.app.readingwidget/v1/cover/$key"
}

/** Retains only the current thumbnail. Source files and private paths never cross the provider boundary. */
@Singleton
class ReadingWidgetCovers @Inject constructor(private val roots: StorageRoots) {
    private var cached: ReadingWidgetCover? = null

    @Synchronized
    internal fun prepare(book: Book?): ReadingWidgetCover? {
        val path = book?.coverPath
        if (book == null || path == null) {
            cached = null
            return null
        }
        return try {
            val source = sourceBytes(path) ?: return clear()
            val sourceVersion = digest(source)
            cached?.takeIf { it.bookId == book.id && it.path == path && it.sourceVersion == sourceVersion }?.let { return it }
            val bytes = thumbnail(source) ?: return clear()
            ReadingWidgetCover(
                key = digest("${book.syncId}\n$path\n$sourceVersion".toByteArray()),
                version = digest(bytes),
                bookId = book.id,
                path = path,
                sourceVersion = sourceVersion,
                bytes = bytes,
            ).also { cached = it }
        } catch (_: Exception) {
            clear()
        }
    }

    @Synchronized
    internal fun clear(): ReadingWidgetCover? {
        cached = null
        return null
    }

    private fun sourceBytes(path: String): ByteArray? {
        val file = roots.resolve(path).canonicalFile
        val root = roots.rootDir.canonicalFile
        if (!file.toPath().startsWith(root.toPath()) || file == root || !file.isFile ||
            file.length() !in 1..MaxSourceBytes.toLong()
        ) return null
        return file.inputStream().use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size() + count > MaxSourceBytes) return null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun thumbnail(source: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (minOf(bounds.outWidth, bounds.outHeight) <= 0) return null
        var sample = 1
        while (longest / sample > MaxEdge * 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(source, 0, source.size,
            BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        var scaled = decoded
        try {
            val factor = minOf(1f, MaxEdge.toFloat() / maxOf(decoded.width, decoded.height))
            if (factor < 1f) {
                scaled = Bitmap.createScaledBitmap(decoded,
                    (decoded.width * factor).roundToInt().coerceAtLeast(1),
                    (decoded.height * factor).roundToInt().coerceAtLeast(1), true)
            }
            val output = ByteArrayOutputStream()
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)) return null
            return output.toByteArray().takeIf { it.size <= MaxThumbnailBytes }
        } finally {
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MaxEdge = 320
        const val MaxThumbnailBytes = 512 * 1024
        const val MaxSourceBytes = 16 * 1024 * 1024
    }
}
