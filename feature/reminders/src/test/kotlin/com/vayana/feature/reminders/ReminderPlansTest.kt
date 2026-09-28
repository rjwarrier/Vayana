package com.vayana.feature.reminders

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.PhysicalBookOwnership
import com.vayana.core.database.model.ReadingSession
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReminderPlansTest {
    private val zone = ZoneOffset.UTC
    // A Wednesday evening.
    private val now = at(LocalDate.of(2026, 9, 30), 20)

    private fun at(date: LocalDate, hour: Int) = date.atTime(LocalTime.of(hour, 0)).toInstant(zone).toEpochMilli()
    private fun session(daysAgo: Long, minutes: Long) =
        ReadingSession(0, "s", 1, at(LocalDate.of(2026, 9, 30).minusDays(daysAgo), 9), 0, minutes * 60)

    @Test
    fun nudgesWithTodaysMinutesAndTheStreakUpToYesterday() {
        val sessions = listOf(session(0, 5), session(1, 30), session(2, 10), session(4, 20))

        assertEquals(ReadingNudge(minutesToday = 5, goalMinutes = 20, streakDays = 2), readingNudge(sessions, 20, now, zone))
    }

    @Test
    fun staysQuietOnceTheGoalIsMetOrWithNoGoal() {
        assertNull(readingNudge(listOf(session(0, 25)), 20, now, zone))
        assertNull(readingNudge(emptyList(), 0, now, zone))
    }

    @Test
    fun noStreakWhenYesterdayHadNoReading() {
        assertEquals(0, readingNudge(listOf(session(2, 30)), 20, now, zone)?.streakDays)
    }

    private fun borrowed(id: Long, returnDaysAhead: Long, percent: Float = 0.5f, pages: Int? = 300) = Book(
        id = id, syncId = "b$id", title = "Book $id", author = null, series = null, seriesNumber = null,
        description = null, coverPath = null, filePath = "", fileAvailability = BookFileAvailability.LOCAL,
        format = BookFormat.PHYSICAL, fileHash = "physical:$id", readingPercent = percent, rating = 0f,
        createdAt = 0, updatedAt = 0, lastReadAt = null, lastLocator = null, pageCount = pages,
        physicalOwnership = PhysicalBookOwnership.BORROWED,
        // Mid-week dates: a Sunday return date moves to Saturday.
        borrowReturnAt = at(LocalDate.of(2026, 9, 30).plusDays(returnDaysAhead), 12),
    )

    @Test
    fun remindsThreeDaysAndOneDayAheadAndOnTheDay() {
        val books = listOf(borrowed(1, 3), borrowed(2, 2), borrowed(3, 1), borrowed(4, 0))

        val reminders = borrowReminders(books, now, zone)

        assertEquals(listOf(1L to 3, 3L to 1, 4L to 0), reminders.map { it.bookId to it.daysRemaining })
        // 150 pages left over the three days before the return date.
        assertEquals(50, reminders.first().pagesPerDay)
    }

    @Test
    fun skipsOwnedFinishedAndPagelessPlans() {
        val owned = borrowed(1, 3).copy(physicalOwnership = PhysicalBookOwnership.OWNED)
        val finished = borrowed(2, 3, percent = 1f)
        val noPages = borrowed(3, 3, pages = null)

        val reminders = borrowReminders(listOf(owned, finished, noPages), now, zone)

        assertEquals(listOf(3L), reminders.map { it.bookId })
        assertNull(reminders.single().pagesPerDay)
    }
}
