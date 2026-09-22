package com.vayana.feature.library

import android.graphics.Bitmap
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LibraryStatsShareRenderTest {
    @Test
    fun rendersStoryAndSquareAtRequiredSizes() {
        val context = RuntimeEnvironment.getApplication()
        val copy = LibraryStatsShareCopy("My library", "Every spine is a book in my library.",
            "A snapshot of my library shelves.", "Books", "Authors", "Read",
            "33% of my library read", "Books read", "Made with Vayana")
        val fonts = LibraryShareFonts(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.DEFAULT)
        val books = (1L..80L).map { LibraryStatsBook(it, it, "Author ${it % 12}", it % 3L == 0L) }
        val snapshot = LibraryStatsSnapshot(books, 12, books.count(LibraryStatsBook::read))
        val fallback = LibrarySharePalette(0, 0, 0, 0, 0, listOf(0))
        for (format in LibraryShareFormat.entries) {
            for (style in LibrarySpineStyle.entries) {
                val colors = paletteFor(LibraryShareTheme.NIGHT, fallback)
                val bitmap = renderLibraryStatsCard(
                    context, snapshot, format, colors, LocalDate.of(2026, 9, 22), copy, style, fonts,
                )
                assertEquals(format.exportWidth, bitmap.width)
                assertEquals(format.exportHeight, bitmap.height)
                assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
                bitmap.recycle()
            }
        }
    }
}
