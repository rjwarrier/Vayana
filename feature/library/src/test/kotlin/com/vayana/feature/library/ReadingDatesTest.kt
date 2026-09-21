package com.vayana.feature.library

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadingDatesTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun pickerDay(year: Int, month: Int, day: Int): Long =
        LocalDateTime.of(year, month, day, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val now = at(2026, 9, 21, 21, 0)

    @Test
    fun pickerShowsTheLocalCalendarDay() {
        // 00:30 in Kolkata is still the previous day in UTC; the picker must show the local day.
        assertEquals(pickerDay(2026, 9, 5), ReadingDates.toPickerMillis(at(2026, 9, 5, 0, 30), zone))
    }

    @Test
    fun noFutureDatesAndStartNeverAfterFinish() {
        val started = at(2026, 9, 5)
        val finished = at(2026, 9, 18)
        assertFalse(ReadingDates.isSelectable(ReadingDateField.STARTED, pickerDay(2026, 9, 22), null, null, now, zone))
        assertTrue(ReadingDates.isSelectable(ReadingDateField.STARTED, pickerDay(2026, 9, 18), started, finished, now, zone))
        assertFalse(ReadingDates.isSelectable(ReadingDateField.STARTED, pickerDay(2026, 9, 19), started, finished, now, zone))
        assertTrue(ReadingDates.isSelectable(ReadingDateField.FINISHED, pickerDay(2026, 9, 5), started, finished, now, zone))
        assertFalse(ReadingDates.isSelectable(ReadingDateField.FINISHED, pickerDay(2026, 9, 4), started, finished, now, zone))
        assertTrue(ReadingDates.isSelectable(ReadingDateField.FINISHED, pickerDay(2026, 9, 21), started, finished, now, zone))
    }

    @Test
    fun keepsTheTimeOfDayAndStaysInOrder() {
        val started = at(2026, 9, 5, 22, 15)
        val finished = at(2026, 9, 18, 8, 0)
        assertEquals(
            at(2026, 9, 1, 22, 15),
            ReadingDates.resolve(ReadingDateField.STARTED, pickerDay(2026, 9, 1), started, started, finished, now, zone),
        )
        // Moving the start onto the finish day at a later hour would put it after the finish: clamp to the finish.
        assertEquals(
            finished,
            ReadingDates.resolve(ReadingDateField.STARTED, pickerDay(2026, 9, 18), started, started, finished, now, zone),
        )
        // Today at an hour still to come is clamped to now.
        assertEquals(
            now,
            ReadingDates.resolve(ReadingDateField.FINISHED, pickerDay(2026, 9, 21), at(2026, 9, 18, 23, 0), started, finished, now, zone),
        )
        // No previous date: noon.
        assertEquals(
            at(2026, 9, 10),
            ReadingDates.resolve(ReadingDateField.STARTED, pickerDay(2026, 9, 10), null, null, null, now, zone),
        )
    }
}
