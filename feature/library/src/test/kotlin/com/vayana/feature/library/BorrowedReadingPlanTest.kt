package com.vayana.feature.library

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BorrowedReadingPlanTest {
    @Test
    fun sundayReturnMovesToSaturdayAndTargetsFriday() {
        val now = epochMillis(2026, 9, 21) // Monday
        val sundayReturn = epochMillis(2026, 9, 27)

        val plan = borrowedReadingPlan(
            pageCount = 300,
            currentPage = 50,
            returnAt = sundayReturn,
            now = now,
            zone = ZoneOffset.UTC,
        )

        assertEquals(5, plan.daysRemaining) // Monday through Friday
        assertEquals(50, plan.pagesPerDay)
        assertEquals(LocalDate.of(2026, 9, 25), plan.finishByAt.utcDate())
    }

    @Test
    fun pagesPerDayRoundsUpAndStopsWhenNoReadingDaysRemain() {
        val now = epochMillis(2026, 9, 21)
        val returnDate = epochMillis(2026, 9, 26)

        assertEquals(
            51,
            borrowedReadingPlan(300, 49, returnDate, now, ZoneOffset.UTC).pagesPerDay,
        )
        assertNull(
            borrowedReadingPlan(300, 49, now, now, ZoneOffset.UTC).pagesPerDay,
        )
    }

    private fun epochMillis(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun Long.utcDate(): LocalDate = java.time.Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
}
