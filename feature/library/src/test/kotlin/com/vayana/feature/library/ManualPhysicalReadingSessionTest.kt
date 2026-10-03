package com.vayana.feature.library

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class ManualPhysicalReadingSessionTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val date = LocalDate.of(2026, 10, 3)
    private val now = date.atTime(10, 30).atZone(zone).toInstant().toEpochMilli()
    private fun draft() = ManualPhysicalSessionDraft(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        10, 0, "0", "30", "40", "50", "200")

    @Test
    fun datePickerDateIsResolvedInLocalTimezoneWithExactDuration() {
        val log = assertNotNull(draft().resolve(now, zone))
        assertEquals(date.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(), log.startedAt)
        assertEquals(now, log.endedAt)
        assertEquals(1800L, log.durationSeconds)
        assertEquals(40, log.startPage)
        assertEquals(50, log.endPage)
    }

    @Test
    fun preparedSessionCanBeRevalidatedWithoutReparsingIncludingClockRollback() {
        val prepared = assertNotNull(draft().prepare(zone))
        assertNull(prepared.completedBy(now - 1))
        assertEquals(prepared, prepared.completedBy(now))
        assertEquals(prepared, prepared.completedBy(now + 60_000))
        assertNull(prepared.completedBy(now - 1))
    }

    @Test
    fun futureZeroOrInvalidDurationsAreRejected() {
        assertNull(draft().resolve(now - 1, zone))
        assertNull(draft().copy(minutes = "0").resolve(now, zone))
        assertNull(draft().copy(minutes = "60").resolve(now, zone))
        assertNull(draft().copy(hours = "-1").resolve(now, zone))
        assertNull(draft().copy(startHour = 24).resolve(now, zone))
    }

    @Test
    fun invalidPagesAreRejectedAndTotalIsOptional() {
        assertNull(draft().copy(startPage = "201").resolve(now, zone))
        assertNull(draft().copy(endPage = "201").resolve(now, zone))
        assertNull(draft().copy(endPage = "").resolve(now, zone))
        assertNull(draft().copy(pageCount = "0").resolve(now, zone))
        assertNull(draft().copy(startPage = "-1").resolve(now, zone))
        assertNull(assertNotNull(draft().copy(pageCount = "").resolve(now, zone)).pageCount)
    }

    @Test
    fun sessionCanCrossMidnightAndRereadingCanMoveBackwards() {
        val yesterday = date.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val log = assertNotNull(draft().copy(dateMillis = yesterday, startHour = 23, startMinute = 50,
            minutes = "20", endPage = "30").resolve(now, zone))
        assertEquals(date.atTime(0, 10).atZone(zone).toInstant().toEpochMilli(), log.endedAt)
        assertEquals(30, log.endPage)
    }
}
