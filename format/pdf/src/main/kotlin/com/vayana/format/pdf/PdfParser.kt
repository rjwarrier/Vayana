package com.vayana.format.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File

class PdfMetadata(
    val title: String,
    val author: String?,
    val pageCount: Int,
    val coverBytes: ByteArray?,
)

/** A PDF that needs a password to open; the reader can't show it. */
class PdfPasswordProtectedException(cause: Throwable) : Exception("PDF is password protected", cause)

object PdfParser {
    private const val CoverWidthPx = 600
    private const val CoverJpegQuality = 85

    /**
     * Title and author from the PDF's info dictionary, falling back to [fallbackTitle] (the imported file's name),
     * and its first page rendered as a JPEG cover.
     *
     * @throws PdfPasswordProtectedException when the file can't be opened without a password.
     */
    fun parse(file: File, fallbackTitle: String): PdfMetadata {
        val info = runCatching { PdfInfoReader.read(file) }.getOrDefault(PdfInfo(null, null))
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (error: SecurityException) {
            descriptor.close()
            throw PdfPasswordProtectedException(error)
        } catch (error: Throwable) {
            descriptor.close()
            throw error
        }
        return renderer.use {
            PdfMetadata(
                title = info.title ?: fallbackTitle,
                author = info.author,
                pageCount = renderer.pageCount,
                coverBytes = runCatching { renderCover(renderer) }.getOrNull(),
            )
        }
    }

    private fun renderCover(renderer: PdfRenderer): ByteArray? {
        if (renderer.pageCount <= 0) return null
        renderer.openPage(0).use { page ->
            if (page.width <= 0 || page.height <= 0) return null
            val width = CoverWidthPx
            val height = (CoverWidthPx.toLong() * page.height / page.width).toInt().coerceIn(1, CoverWidthPx * 4)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                // Pages render onto transparency; a PDF page is white paper.
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, CoverJpegQuality, out)
                    out.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        }
    }
}
