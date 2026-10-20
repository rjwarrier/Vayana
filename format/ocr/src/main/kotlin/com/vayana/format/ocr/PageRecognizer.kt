package com.vayana.format.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.Closeable
import java.io.File

/** A recognised word: its text, its box in PDF points from the page's top-left corner, and Tesseract's confidence. */
data class RecognizedWord(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float,
)

/** A page's words, line by line, in reading order. */
data class RecognizedPage(val lines: List<List<RecognizedWord>>)

/**
 * Recognises the text of PDF pages with Tesseract. [dataPath] holds a `tessdata` folder with the `<language>`
 * model (`mal.traineddata`, ...). One instance is one Tesseract engine: not thread-safe, close it when done.
 */
class PageRecognizer(dataPath: File, language: String) : Closeable {
    private val tesseract = TessBaseAPI()

    init {
        check(tesseract.init(dataPath.absolutePath, language)) { "Tesseract could not load '$language' from $dataPath" }
        // One block of running text per page, as in the trial; the page's own layout is a single column of prose.
        tesseract.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
    }

    /** Renders [pageIndex] of [renderer] at [dpi] and recognises it. */
    fun recognize(renderer: PdfRenderer, pageIndex: Int, dpi: Int = DefaultDpi): RecognizedPage {
        val bitmap = renderer.openPage(pageIndex).use { page ->
            val scale = dpi / PointsPerInch
            Bitmap.createBitmap(
                (page.width * scale).toInt().coerceAtLeast(1),
                (page.height * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            ).also {
                // Pages render onto transparency; a scanned page is white paper.
                it.eraseColor(Color.WHITE)
                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
        try {
            return recognize(bitmap, PointsPerInch / dpi)
        } finally {
            bitmap.recycle()
        }
    }

    /** Recognises [bitmap]; [pointsPerPixel] converts its pixels to PDF points. */
    fun recognize(bitmap: Bitmap, pointsPerPixel: Float): RecognizedPage {
        tesseract.setImage(bitmap)
        tesseract.getUTF8Text() // runs recognition
        val lines = mutableListOf<List<RecognizedWord>>()
        var line = mutableListOf<RecognizedWord>()
        val words = tesseract.getResultIterator() ?: return RecognizedPage(emptyList())
        try {
            words.begin()
            do {
                val text = words.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    val box = words.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    line += RecognizedWord(
                        text = text,
                        left = box.left * pointsPerPixel,
                        top = box.top * pointsPerPixel,
                        right = box.right * pointsPerPixel,
                        bottom = box.bottom * pointsPerPixel,
                        confidence = words.confidence(TessBaseAPI.PageIteratorLevel.RIL_WORD),
                    )
                }
                if (words.isAtFinalElement(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE, TessBaseAPI.PageIteratorLevel.RIL_WORD) &&
                    line.isNotEmpty()
                ) {
                    lines += line
                    line = mutableListOf()
                }
            } while (words.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
        } finally {
            words.delete()
            tesseract.clear()
        }
        if (line.isNotEmpty()) lines += line
        return RecognizedPage(lines)
    }

    override fun close() = tesseract.recycle()

    companion object {
        const val DefaultDpi = 300
        private const val PointsPerInch = 72f
    }
}
