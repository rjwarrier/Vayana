package com.vayana.feature.library

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.StartedReadingSeconds
import com.vayana.core.database.model.shownReadingPercent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookReadingStateTest {
    @Test
    fun explicitlyMarkedReadingCountsImmediatelyWithoutAFinishDate() {
        val reading = book().copy(readingDisposition = "READING", startedReadingAt = 5L)
        assertEquals(BookReadingState.READING, reading.readingState())
        assertEquals(0L, reading.totalReadingSeconds)
    }

    @Test
    fun untouchedBookIsNotStarted() {
        assertEquals(BookReadingState.NOT_STARTED, book().readingState())
    }

    @Test
    fun anEbookIsReadingOnlyAfterFiveMinutesOfReading() {
        // Opened, or leafed through its contents: a position and a date, but not a book being read.
        val opened = book(percent = 0.18f, lastReadAt = 5L).copy(startedReadingAt = 5L, totalReadingSeconds = 299L)
        assertEquals(BookReadingState.NOT_STARTED, opened.readingState())
        assertEquals(0f, opened.shownReadingPercent)
        val read = opened.copy(totalReadingSeconds = StartedReadingSeconds)
        assertEquals(BookReadingState.READING, read.readingState())
        assertEquals(0.18f, read.shownReadingPercent)
        // Jumping to the last chapter doesn't finish it either.
        assertEquals(BookReadingState.NOT_STARTED, book(percent = 1f).readingState())
    }

    @Test
    fun aBookReadOutsideTheAppCountsFromAnyRecordedReading() {
        val paper = book().copy(format = BookFormat.PHYSICAL, filePath = "")
        assertEquals(BookReadingState.NOT_STARTED, paper.readingState())
        assertEquals(BookReadingState.READING, paper.copy(readingPercent = 0.18f).readingState())
        assertEquals(BookReadingState.READING, paper.copy(startedReadingAt = 5L).readingState())
    }

    @Test
    fun finishedWinsOverProgress() {
        assertEquals(BookReadingState.FINISHED, book(percent = 0.4f).copy(finishedReadingAt = 9L).readingState())
        assertEquals(BookReadingState.FINISHED, book(percent = 1f).copy(totalReadingSeconds = StartedReadingSeconds).readingState())
    }

    @Test
    fun shareCardDropsEmptyStatsOnlyForUnreadBooks() {
        val defaults = BookShareImageOptions()
        val unread = BookShareImageOptions.forState(BookReadingState.NOT_STARTED, defaults)
        assertFalse(unread.showProgress)
        assertFalse(unread.showReadTime)
        val reading = BookShareImageOptions.forState(BookReadingState.READING, unread)
        assertTrue(reading.showProgress)
        assertTrue(reading.showReadTime)
    }

    @Test
    fun readAgainOnlyOnceAYearHasPassedSinceFinishing() {
        val finishedAt = 1_000L
        val finished = book(percent = 1f).copy(finishedReadingAt = finishedAt)
        assertFalse(finished.finishedLongAgo(finishedAt + ReadAgainAfterMillis - 1))
        assertTrue(finished.finishedLongAgo(finishedAt + ReadAgainAfterMillis))
        // Finished only by reaching the end: counts from when it was last read.
        assertTrue(book(percent = 1f, lastReadAt = finishedAt).copy(totalReadingSeconds = StartedReadingSeconds).finishedLongAgo(finishedAt + ReadAgainAfterMillis))
        // A book still being read is never "read again", however old.
        assertFalse(book(percent = 0.5f, lastReadAt = finishedAt).finishedLongAgo(finishedAt + 10 * ReadAgainAfterMillis))
    }

    private fun book(percent: Float = 0f, lastReadAt: Long? = null) = Book(
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
        lastReadAt = lastReadAt,
        lastLocator = null,
    )
}
