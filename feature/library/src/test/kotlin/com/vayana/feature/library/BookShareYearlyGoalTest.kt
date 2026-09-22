package com.vayana.feature.library

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookShareYearlyGoalTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun countsThisYearsFinishedBooksAndAddsUnfinishedSharedBook() {
        val shared = book(1)
        val thisYear = book(2, finished = LocalDate.of(2026, 1, 1))
        val lastYear = book(3, finished = LocalDate.of(2025, 12, 31))

        assertEquals(
            YearlyBookShareProgress(readCount = 2, target = 12),
            yearlyBookShareProgress(listOf(shared, thisYear, lastYear), shared, 12, 2026, zone),
        )
    }

    @Test
    fun doesNotCountSharedBookTwiceWhenAlreadyFinishedThisYear() {
        val shared = book(1, finished = LocalDate.of(2026, 9, 22))
        assertEquals(1, yearlyBookShareProgress(listOf(shared), shared, 12, 2026, zone)?.readCount)
    }

    @Test
    fun disabledGoalHasNoBadge() {
        val shared = book(1)
        assertNull(yearlyBookShareProgress(listOf(shared), shared, 0, 2026, zone))
    }

    private fun book(id: Long, finished: LocalDate? = null) = Book(
        id = id,
        syncId = "book-$id",
        title = "Book $id",
        author = null,
        series = null,
        seriesNumber = null,
        description = null,
        coverPath = null,
        filePath = "",
        fileAvailability = BookFileAvailability.LOCAL,
        format = BookFormat.EPUB,
        fileHash = "hash-$id",
        readingPercent = if (finished != null) 1f else 0f,
        rating = 0f,
        createdAt = 0L,
        updatedAt = 0L,
        lastReadAt = null,
        lastLocator = null,
        finishedReadingAt = finished?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
    )
}
