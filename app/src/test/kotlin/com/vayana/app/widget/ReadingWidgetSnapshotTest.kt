package com.vayana.app.widget

import com.vayana.core.database.model.ReadingSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingWidgetSnapshotTest {
    private val zone = ZoneId.of("America/New_York")
    private val sunday = LocalDate.of(2026, 3, 8)
    private val midnight = sunday.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `active intervals split at local midnight and the configured week boundary`() {
        val start = midnight - 120_000
        val end = midnight + 180_000
        val session = session(start, end, 120, "$start:${start + 30_000},${midnight + 90_000}:$end")
        val sundayWeek = readingWidgetTime(listOf(session), sunday, zone, DayOfWeek.SUNDAY, sunday.minusDays(10))
        val mondayWeek = readingWidgetTime(listOf(session), sunday, zone, DayOfWeek.MONDAY, sunday.minusDays(10))

        assertEquals(listOf(0, 0, 0, 0, 0, 0, 30, 90), sundayWeek.dailySeconds)
        assertEquals(90L, sundayWeek.weekSeconds)
        assertEquals(120L, mondayWeek.weekSeconds)
        assertEquals(2, sundayWeek.streakDays)
    }

    @Test
    fun `legacy seconds prorate across a daylight saving day and zero today keeps the streak live`() {
        val monday = sunday.plusDays(1)
        val mondayStart = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val session = session(midnight - 3_600_000, mondayStart, 24 * 60 * 60L)
        val time = readingWidgetTime(listOf(session), monday, zone, DayOfWeek.MONDAY, sunday.minusDays(10))

        assertEquals(listOf(3_600, 82_800, 0), time.dailySeconds.takeLast(3))
        assertEquals(0L, time.weekSeconds)
        assertEquals(2, time.streakDays)
    }

    @Test
    fun `uncovered days are null and sub-minute sessions do not count`() {
        val short = session(midnight, midnight + 30_000, 30)
        val time = readingWidgetTime(listOf(short), sunday, zone, DayOfWeek.MONDAY, sunday)

        assertEquals(List(7) { null } + 0, time.dailySeconds)
        assertEquals(null, time.weekSeconds)
        assertEquals(null, time.streakDays)
    }

    private fun session(start: Long, end: Long, seconds: Long, intervals: String? = null) = ReadingSession(
        id = 1, syncId = "s", bookId = 1, startedAt = start, endedAt = end, durationSeconds = seconds,
        activeIntervals = intervals,
    )
}
