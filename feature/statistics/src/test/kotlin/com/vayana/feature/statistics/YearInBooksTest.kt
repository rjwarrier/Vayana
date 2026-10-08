package com.vayana.feature.statistics

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YearInBooksTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 9, 22)

    @Test
    fun physicalFinishesCountTowardYearlyGoalAlongsideDigitalBooks() {
        val physical = book(1, finished = today, progress = 1f).copy(format = BookFormat.PHYSICAL)
        val digital = book(2, finished = today, progress = 1f)
        val unfinished = book(3, progress = .5f).copy(format = BookFormat.PHYSICAL)
        val year = listOf(physical, digital, unfinished).yearInBooks(today, zone, yearlyGoal = 12)
        assertEquals(2, year.finishedCount)
        assertEquals(2, year.monthlyFinishes[8])
        assertEquals(10, year.goalPace?.booksRemaining)
    }

    @Test
    fun countsRecordedFinishesByMonthAndShowsGoalPace() {
        val books = listOf(
            book(1, finished = LocalDate.of(2026, 1, 1)),
            book(2, finished = LocalDate.of(2026, 9, 22)),
            book(3, finished = LocalDate.of(2025, 12, 31)),
            book(4, finished = LocalDate.of(2026, 10, 1)),
        )

        val year = books.yearInBooks(today, zone, yearlyGoal = 12)

        assertEquals(2, year.finishedCount)
        assertEquals(1, year.monthlyFinishes[0])
        assertEquals(1, year.monthlyFinishes[8])
        assertEquals(0, year.monthlyFinishes[9])
        assertEquals(10, year.goalPace?.booksRemaining)
        assertEquals(101, year.goalPace?.daysRemaining)
        assertEquals(10, year.goalPace?.daysPerBook)
    }

    @Test
    fun disablesPaceWithoutGoalAndMarksReachedGoal() {
        val books = listOf(book(1, finished = today))
        assertNull(books.yearInBooks(today, zone, yearlyGoal = 0).goalPace)
        assertEquals(0, books.yearInBooks(today, zone, yearlyGoal = 1).goalPace?.booksRemaining)
    }

    @Test
    fun averageProgressUsesOnlyBooksBeingRead() {
        val books = listOf(book(1, progress = 0f), book(2, progress = 0.25f), book(3, progress = 0.75f), book(4, progress = 1f))
        assertEquals(50, books.averageActiveProgressPercent(finishedThreshold = 0.95f))
        assertEquals(0, listOf(books.first(), books.last()).averageActiveProgressPercent(0.95f))
    }

    private fun book(id: Long, finished: LocalDate? = null, progress: Float = 0f) = Book(
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
        readingPercent = progress,
        rating = 0f,
        createdAt = 0L,
        updatedAt = 0L,
        lastReadAt = null,
        lastLocator = null,
        finishedReadingAt = finished?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
    )
}
