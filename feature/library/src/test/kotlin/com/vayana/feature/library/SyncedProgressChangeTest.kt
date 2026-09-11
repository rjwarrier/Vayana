package com.vayana.feature.library

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncedProgressChangeTest {
    private val before = book(locator = "epubcfi(/6/4!/4/2)", percent = 0.40f)

    @Test
    fun timestampOnlyChangeDoesNotPrompt() {
        val after = before.copy(updatedAt = 99L, lastReadAt = 99L)

        assertFalse(before.hasMeaningfulSyncedProgressChange(after))
    }

    @Test
    fun percentRoundingNoiseDoesNotPrompt() {
        val after = before.copy(readingPercent = 0.4005f)

        assertFalse(before.hasMeaningfulSyncedProgressChange(after))
    }

    @Test
    fun locatorWhitespaceDoesNotPrompt() {
        val after = before.copy(lastLocator = "  epubcfi(/6/4!/4/2)\n")

        assertFalse(before.hasMeaningfulSyncedProgressChange(after))
    }

    @Test
    fun locatorChangePrompts() {
        val after = before.copy(lastLocator = "epubcfi(/6/8!/4/2)")

        assertTrue(before.hasMeaningfulSyncedProgressChange(after))
    }

    @Test
    fun percentChangeAboveThresholdPrompts() {
        val after = before.copy(readingPercent = 0.402f)

        assertTrue(before.hasMeaningfulSyncedProgressChange(after))
    }

    @Test
    fun readStatusChangePrompts() {
        val after = before.copy(finishedReadingAt = 50L)

        assertTrue(before.hasMeaningfulSyncedProgressChange(after))
    }

    @Test
    fun noPriorLocatorNeverPrompts() {
        val unopened = book(locator = null, percent = 0f)
        val after = unopened.copy(lastLocator = "epubcfi(/6/8!/4/2)", readingPercent = 0.5f)

        assertFalse(unopened.hasMeaningfulSyncedProgressChange(after))
    }

    private fun book(locator: String?, percent: Float) = Book(
        id = 1L,
        syncId = "sync-1",
        title = "Book",
        author = null,
        series = null,
        seriesNumber = null,
        description = null,
        coverPath = null,
        filePath = "books/book.epub",
        fileAvailability = BookFileAvailability.LOCAL,
        format = BookFormat.EPUB,
        fileHash = "hash",
        readingPercent = percent,
        rating = 0f,
        createdAt = 1L,
        updatedAt = 1L,
        lastReadAt = 1L,
        lastLocator = locator,
    )
}
