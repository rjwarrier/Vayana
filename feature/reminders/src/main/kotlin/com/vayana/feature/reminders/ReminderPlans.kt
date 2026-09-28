package com.vayana.feature.reminders

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.PhysicalBookOwnership
import com.vayana.core.database.model.ReadingSession
import com.vayana.core.database.model.borrowedReadingPlan
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Today's reading against the daily goal, and the run of days read before today that a session today would extend. */
data class ReadingNudge(val minutesToday: Int, val goalMinutes: Int, val streakDays: Int)

/**
 * What the reading reminder says, or null when there's nothing to nudge about: no daily goal, or it's met already.
 * [streakDays] counts consecutive days with reading up to yesterday - today's hasn't happened yet.
 */
fun readingNudge(sessions: List<ReadingSession>, goalMinutes: Int, now: Long, zone: ZoneId): ReadingNudge? {
    if (goalMinutes <= 0) return null
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val secondsByDay = HashMap<LocalDate, Long>()
    sessions.forEach { session ->
        val day = Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate()
        secondsByDay[day] = (secondsByDay[day] ?: 0L) + session.durationSeconds
    }
    val minutesToday = ((secondsByDay[today] ?: 0L) / SecondsPerMinute).toInt()
    if (minutesToday >= goalMinutes) return null
    var streak = 0
    var day = today.minusDays(1)
    while ((secondsByDay[day] ?: 0L) > 0L) {
        streak += 1
        day = day.minusDays(1)
    }
    return ReadingNudge(minutesToday = minutesToday, goalMinutes = goalMinutes, streakDays = streak)
}

/** A borrowed physical book coming due, and the daily pages that still finish it in time (null without a page count). */
data class BorrowReminder(val bookId: Long, val title: String, val daysRemaining: Int, val pagesPerDay: Int?)

/**
 * Borrowed physical books due back in [ReminderDays] days (three, one, or today), not yet finished. The reading plan
 * is the one the library shows: finish by the day before the return date.
 */
fun borrowReminders(books: List<Book>, now: Long, zone: ZoneId): List<BorrowReminder> = books.mapNotNull { book ->
    val returnAt = book.borrowReturnAt ?: return@mapNotNull null
    if (book.format != BookFormat.PHYSICAL || book.physicalOwnership != PhysicalBookOwnership.BORROWED) return@mapNotNull null
    if (book.finishedReadingAt != null || book.readingPercent >= 1f) return@mapNotNull null
    val pageCount = book.pageCount?.takeIf { it > 0 }
    val currentPage = pageCount?.let { (book.readingPercent * it).roundToInt() } ?: 0
    val plan = borrowedReadingPlan(pageCount ?: 0, currentPage, returnAt, now, zone)
    if (plan.daysRemaining !in ReminderDays) return@mapNotNull null
    BorrowReminder(
        bookId = book.id,
        title = book.title,
        daysRemaining = plan.daysRemaining,
        pagesPerDay = plan.pagesPerDay?.takeIf { pageCount != null && it > 0 },
    )
}

private val ReminderDays = setOf(3, 1, 0)
private const val SecondsPerMinute = 60L
