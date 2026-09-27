package com.vayana.feature.library

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** The two reading dates a reader can correct on the book details screen. */
internal enum class ReadingDateField { STARTED, FINISHED }

/**
 * Converts between stored reading dates (instants) and the Material date picker, which works in UTC-midnight
 * millis of the chosen calendar day. A corrected date keeps the original time of day, so reading sessions on the
 * same day stay in order.
 */
internal object ReadingDates {
    fun toPickerMillis(instant: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        localDate(instant, zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun pickerDate(pickerMillis: Long): LocalDate = Instant.ofEpochMilli(pickerMillis).atZone(ZoneOffset.UTC).toLocalDate()

    /** Resolves an unrestricted calendar date (such as a loan return date), retaining its prior time of day. */
    fun resolvePickerDate(
        pickerMillis: Long,
        previous: Long?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val time = previous?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: LocalTime.NOON
        return pickerDate(pickerMillis).atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    /**
     * Whether [pickerMillis] may be chosen for [field]: never after today, a start never after the finish, and a
     * finish never before the start. The other date is compared by calendar day only.
     */
    fun isSelectable(
        field: ReadingDateField,
        pickerMillis: Long,
        startedAt: Long?,
        finishedAt: Long?,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val day = pickerDate(pickerMillis)
        if (day.isAfter(localDate(now, zone))) return false
        return when (field) {
            ReadingDateField.STARTED -> finishedAt == null || !day.isAfter(localDate(finishedAt, zone))
            ReadingDateField.FINISHED -> startedAt == null || !day.isBefore(localDate(startedAt, zone))
        }
    }

    /**
     * The instant to store for [field] once [pickerMillis] is chosen: that day at the previous date's time of day
     * (noon when there was none), kept out of the future and on the right side of the other date.
     */
    fun resolve(
        field: ReadingDateField,
        pickerMillis: Long,
        previous: Long?,
        startedAt: Long?,
        finishedAt: Long?,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val time = previous?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: LocalTime.NOON
        var instant = pickerDate(pickerMillis).atTime(time).atZone(zone).toInstant().toEpochMilli()
        instant = minOf(instant, now)
        return when (field) {
            ReadingDateField.STARTED -> finishedAt?.let { minOf(instant, it) } ?: instant
            ReadingDateField.FINISHED -> startedAt?.let { maxOf(instant, it) } ?: instant
        }
    }

    private fun localDate(instant: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(instant).atZone(zone).toLocalDate()
}
