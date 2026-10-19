package com.vayana.feature.statistics

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.ReadingSession
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The stretch of time the Statistics hero and the cards under it describe: the calendar week, month or year so far. */
enum class StatsPeriod { WEEK, MONTH, YEAR, ALL }

/** Morning 5–12, afternoon 12–17, evening 17–21, night 21–5, by when a session started. */
enum class TimeOfDay { MORNING, AFTERNOON, EVENING, NIGHT }

/** One bar of the hero chart: a day (week, month) or a month (year, all time) starting on [start]. */
data class PeriodBucket(val start: LocalDate, val seconds: Long, val isFuture: Boolean)

data class BookTime(val book: Book, val seconds: Long)

data class PeriodStats(
    val period: StatsPeriod,
    val totalSeconds: Long = 0L,
    /** The part of [totalSeconds] spent listening to read aloud. */
    val listenedSeconds: Long = 0L,
    /** Reading in the previous period up to the same point (Monday–Wednesday last week, say); null for all time. */
    val previousSeconds: Long? = null,
    val sessionCount: Int = 0,
    val activeDays: Int = 0,
    val booksFinished: Int = 0,
    val buckets: List<PeriodBucket> = emptyList(),
    /** Seconds per [TimeOfDay], in its order. */
    val timeOfDaySeconds: List<Long> = List(TimeOfDay.entries.size) { 0L },
    /** Seconds per weekday, starting on the user's first day of the week. */
    val weekdaySeconds: List<Pair<DayOfWeek, Long>> = emptyList(),
    val averageSessionSeconds: Long = 0L,
    val longestSessionSeconds: Long = 0L,
    /** Most read in the period, most time first. */
    val topBooks: List<BookTime> = emptyList(),
    val devices: List<DeviceStat> = emptyList(),
    val unattributedDeviceSeconds: Long = 0L,
) {
    val bucketsAreMonths: Boolean get() = period == StatsPeriod.YEAR || period == StatsPeriod.ALL
}

internal fun periodStats(
    period: StatsPeriod,
    sessions: List<ReadingSession>,
    books: List<Book>,
    firstDayOfWeek: DayOfWeek,
    today: LocalDate,
    zone: ZoneId,
): PeriodStats {
    val counted = sessions.filter { it.durationSeconds >= MinCountedSessionSeconds }
    fun ReadingSession.date(): LocalDate = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()
    val firstActivity = counted.minOfOrNull { it.date() } ?: today
    val start = when (period) {
        StatsPeriod.WEEK -> today.startOfWeek(firstDayOfWeek)
        StatsPeriod.MONTH -> today.withDayOfMonth(1)
        StatsPeriod.YEAR -> today.withDayOfYear(1)
        // At least the last twelve months, empty ones included, so a young library still gets a year-shaped chart.
        StatsPeriod.ALL -> firstActivity.withDayOfMonth(1).coerceAtMost(today.withDayOfMonth(1).minusMonths(11))
    }
    val inPeriod = counted.filter { it.date() in start..today }
    val previousSeconds = when (period) {
        StatsPeriod.WEEK -> start.minusWeeks(1)
        StatsPeriod.MONTH -> start.minusMonths(1)
        StatsPeriod.YEAR -> start.minusYears(1)
        StatsPeriod.ALL -> null
    }?.let { previousStart ->
        // The same elapsed span, clipped to the previous period (31 March has no 31 February).
        val elapsed = ChronoUnit.DAYS.between(start, today)
        val previousEnd = previousStart.plusDays(elapsed).coerceAtMost(start.minusDays(1))
        counted.filter { it.date() in previousStart..previousEnd }.sumOf { it.durationSeconds }
    }
    val secondsByDate = inPeriod.groupingBy { it.date() }.fold(0L) { total, session -> total + session.durationSeconds }
    val buckets = when (period) {
        StatsPeriod.WEEK -> (0L until 7L).map { start.plusDays(it) }.map { day ->
            PeriodBucket(day, secondsByDate[day] ?: 0L, day.isAfter(today))
        }
        StatsPeriod.MONTH -> (0L until start.lengthOfMonth()).map { start.plusDays(it) }.map { day ->
            PeriodBucket(day, secondsByDate[day] ?: 0L, day.isAfter(today))
        }
        StatsPeriod.YEAR, StatsPeriod.ALL -> {
            val end = if (period == StatsPeriod.YEAR) start.plusMonths(11) else today.withDayOfMonth(1)
            val months = ChronoUnit.MONTHS.between(start, end).toInt().coerceAtLeast(0)
            (0..months).map { start.plusMonths(it.toLong()) }.map { month ->
                val seconds = secondsByDate.entries.filter { it.key.year == month.year && it.key.month == month.month }.sumOf { it.value }
                PeriodBucket(month, seconds, month.isAfter(today))
            }
        }
    }
    val timeOfDay = LongArray(TimeOfDay.entries.size)
    inPeriod.forEach { session ->
        val hour = Instant.ofEpochMilli(session.startedAt).atZone(zone).hour
        timeOfDay[timeOfDay(hour).ordinal] += session.durationSeconds
    }
    val weekdays = (0L until 7L).map { firstDayOfWeek.plus(it) }
    val byWeekday = inPeriod.groupingBy { it.date().dayOfWeek }.fold(0L) { total, session -> total + session.durationSeconds }
    val booksById = books.associateBy { it.id }
    val topBooks = inPeriod.groupingBy { it.bookId }.fold(0L) { total, session -> total + session.durationSeconds }
        .entries.sortedByDescending { it.value }
        .mapNotNull { (id, seconds) -> booksById[id]?.let { BookTime(it, seconds) } }
        .take(TopBooksLimit)
    val devices = deviceStats(inPeriod)
    return PeriodStats(
        period = period,
        totalSeconds = inPeriod.sumOf { it.durationSeconds },
        listenedSeconds = inPeriod.sumOf { it.listenedSeconds.coerceIn(0L, it.durationSeconds) },
        previousSeconds = previousSeconds,
        sessionCount = inPeriod.size,
        activeDays = secondsByDate.size,
        booksFinished = books.count { book ->
            book.finishedReadingAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() in start..today } == true
        },
        buckets = buckets,
        timeOfDaySeconds = timeOfDay.toList(),
        weekdaySeconds = weekdays.map { it to (byWeekday[it] ?: 0L) },
        averageSessionSeconds = if (inPeriod.isEmpty()) 0L else inPeriod.sumOf { it.durationSeconds } / inPeriod.size,
        longestSessionSeconds = inPeriod.maxOfOrNull { it.durationSeconds } ?: 0L,
        topBooks = topBooks,
        devices = devices,
        unattributedDeviceSeconds = if (devices.isEmpty()) 0L else inPeriod.filter { it.deviceName == null }.sumOf { it.durationSeconds },
    )
}

internal fun timeOfDay(hour: Int): TimeOfDay = when (hour) {
    in 5..11 -> TimeOfDay.MORNING
    in 12..16 -> TimeOfDay.AFTERNOON
    in 17..20 -> TimeOfDay.EVENING
    else -> TimeOfDay.NIGHT
}

private const val TopBooksLimit = 3
