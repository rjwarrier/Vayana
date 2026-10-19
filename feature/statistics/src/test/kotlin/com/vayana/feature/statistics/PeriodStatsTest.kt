package com.vayana.feature.statistics

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.ReadingSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class PeriodStatsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 21) // a Wednesday

    private fun session(day: LocalDate, hour: Int, minutes: Long, bookId: Long = 1, device: String? = null): ReadingSession {
        val start = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        return ReadingSession(0, "s-$day-$hour-$bookId", bookId, start, start + minutes * 60_000, minutes * 60, deviceName = device,
            listenedSeconds = if (hour >= 21) minutes * 30 else 0L)
    }

    private fun book(id: Long, finishedOn: LocalDate? = null) = Book(id = id, syncId = "b$id", title = "Book $id", author = null,
        series = null, seriesNumber = null, description = null, coverPath = null, filePath = "", fileAvailability = BookFileAvailability.LOCAL,
        format = BookFormat.EPUB, fileHash = "h$id", readingPercent = 0f, rating = 0f, createdAt = 0, updatedAt = 0, lastReadAt = null,
        lastLocator = null, finishedReadingAt = finishedOn?.atStartOfDay(zone)?.toInstant()?.toEpochMilli())

    @Test
    fun weekComparesWithTheSameDaysLastWeek() {
        val sessions = listOf(
            session(today, 7, 30, device = "Pixel"), // morning, this week
            session(today.minusDays(1), 22, 20, bookId = 2, device = "Reader"), // night, Tuesday
            session(today.minusDays(7), 9, 15), // last Wednesday: inside the compared span
            session(today.minusDays(5), 9, 40), // last Friday: after the compared span
            session(today, 10, 0), // under a minute: not counted
        )
        val stats = periodStats(StatsPeriod.WEEK, sessions, listOf(book(1, finishedOn = today), book(2)), DayOfWeek.MONDAY, today, zone)
        assertEquals(50L * 60, stats.totalSeconds)
        assertEquals(10L * 60, stats.listenedSeconds) // half of the night session was read aloud
        assertEquals(15L * 60, stats.previousSeconds)
        assertEquals(2, stats.sessionCount)
        assertEquals(2, stats.activeDays)
        assertEquals(1, stats.booksFinished)
        assertEquals(7, stats.buckets.size)
        assertEquals(LocalDate.of(2026, 10, 19), stats.buckets.first().start)
        assertEquals(listOf(false, false, false, true, true, true, true), stats.buckets.map { it.isFuture })
        assertEquals(listOf(30L * 60, 0L, 0L, 20L * 60), stats.timeOfDaySeconds)
        assertEquals(DayOfWeek.MONDAY, stats.weekdaySeconds.first().first)
        assertEquals(listOf(1L, 2L), stats.topBooks.map { it.book.id })
        assertEquals(listOf("Pixel", "Reader"), stats.devices.map { it.name })
    }

    @Test
    fun yearHasTwelveMonthsAndAllTimeShowsAtLeastTheLastTwelve() {
        val sessions = listOf(session(LocalDate.of(2026, 9, 3), 18, 60), session(today, 18, 30))
        val year = periodStats(StatsPeriod.YEAR, sessions, emptyList(), DayOfWeek.MONDAY, today, zone)
        assertEquals(12, year.buckets.size)
        assertEquals(listOf(60L * 60, 30L * 60), year.buckets.filter { it.seconds > 0 }.map { it.seconds })
        val all = periodStats(StatsPeriod.ALL, sessions, emptyList(), DayOfWeek.MONDAY, today, zone)
        assertEquals((0L..11L).map { LocalDate.of(2025, 11, 1).plusMonths(it) }, all.buckets.map { it.start })
        assertEquals(listOf(60L * 60, 30L * 60), all.buckets.takeLast(2).map { it.seconds })
        assertEquals(null, all.previousSeconds)
        assertEquals(90L * 60, all.totalSeconds)
    }

    @Test
    fun monthComparisonIsClippedToTheShorterMonth() {
        val march31 = LocalDate.of(2027, 3, 31)
        val sessions = listOf(session(LocalDate.of(2027, 2, 28), 12, 10), session(LocalDate.of(2027, 3, 1), 12, 5))
        val stats = periodStats(StatsPeriod.MONTH, sessions, emptyList(), DayOfWeek.MONDAY, march31, zone)
        assertEquals(31, stats.buckets.size)
        assertEquals(10L * 60, stats.previousSeconds)
    }
}
