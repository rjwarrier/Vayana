package com.vayana.core.filesystem

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Covers as books and Goodreads supply them run to several megabytes and thousands of pixels; the library never
 * shows one larger than a detail-screen hero. These shrink a cover to [MaxEdgePx] on its long edge and re-encode it
 * in its own format, keeping the result only when it is clearly smaller. A cover already small is left alone, so
 * running this again (or on a cover another device already shrank) changes nothing.
 */
object CoverImages {
    /** Long edge of a stored cover: sharp on a tablet's book-details hero, a fraction of a camera-sized original. */
    const val MaxEdgePx = 1200

    /** Below this a cover isn't worth decoding, whatever its size in pixels. */
    const val CompactThresholdBytes = 300 * 1024

    private const val JpegQuality = 85
    private const val WebpQuality = 85
    private const val MinSavingsPercent = 10

    /** [bytes] shrunk for storage, or [bytes] themselves when small already, undecodable or not worth changing. */
    fun compact(bytes: ByteArray, extension: String): ByteArray {
        if (bytes.size < CompactThresholdBytes) return bytes
        val format = compressFormatFor(extension) ?: return bytes
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return bytes
        val bitmap = decodeScaled(bytes, bounds.outWidth, bounds.outHeight) ?: return bytes
        val output = try {
            ByteArrayOutputStream(bytes.size / 4).also { stream ->
                bitmap.compress(format, if (format == Bitmap.CompressFormat.PNG) 100 else qualityFor(format), stream)
            }.toByteArray()
        } finally {
            bitmap.recycle()
        }
        return if (output.isNotEmpty() && output.size <= bytes.size * (100 - MinSavingsPercent) / 100) output else bytes
    }

    /** Shrinks [file] in place (via a temporary file, so a crash never leaves half a cover). True when it changed. */
    fun compactFile(file: File): Boolean {
        if (!file.isFile || file.length() < CompactThresholdBytes) return false
        val original = file.readBytes()
        val compacted = compact(original, file.extension)
        if (compacted === original) return false
        val temporary = File(file.parentFile, "${file.name}.compacting")
        temporary.writeBytes(compacted)
        if (!temporary.renameTo(file)) {
            temporary.delete()
            return false
        }
        return true
    }

    /** Decodes at the smallest power-of-two sample still at least [MaxEdgePx], then scales exactly to fit. */
    private fun decodeScaled(bytes: ByteArray, width: Int, height: Int): Bitmap? {
        val longEdge = maxOf(width, height)
        var sampleSize = 1
        while (longEdge / (sampleSize * 2) >= MaxEdgePx) sampleSize *= 2
        val sampled = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: return null
        val sampledLongEdge = maxOf(sampled.width, sampled.height)
        if (sampledLongEdge <= MaxEdgePx) return sampled
        val scale = MaxEdgePx.toFloat() / sampledLongEdge
        val scaled = Bitmap.createScaledBitmap(
            sampled,
            (sampled.width * scale).toInt().coerceAtLeast(1),
            (sampled.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== sampled) sampled.recycle()
        return scaled
    }

    @Suppress("DEPRECATION") // WEBP is the only lossy WebP encoder before Android 11.
    private fun compressFormatFor(extension: String): Bitmap.CompressFormat? = when (extension.lowercase()) {
        "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
        "png" -> Bitmap.CompressFormat.PNG
        "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        else -> null
    }

    private fun qualityFor(format: Bitmap.CompressFormat): Int =
        if (format == Bitmap.CompressFormat.JPEG) JpegQuality else WebpQuality
}
