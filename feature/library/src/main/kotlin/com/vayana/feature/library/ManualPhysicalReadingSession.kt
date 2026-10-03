package com.vayana.feature.library

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

internal data class ManualPhysicalReadingSession(
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Long,
    val startPage: Int,
    val endPage: Int,
    val pageCount: Int?,
) {
    fun completedBy(now: Long): ManualPhysicalReadingSession? = takeIf { endedAt <= now }
}

internal data class ManualPhysicalSessionDraft(
    val dateMillis: Long,
    val startHour: Int,
    val startMinute: Int,
    val hours: String,
    val minutes: String,
    val startPage: String,
    val endPage: String,
    val pageCount: String,
) {
    /** DatePicker uses UTC dates; the actual session starts in the reader's local timezone. */
    fun resolve(now: Long, zone: ZoneId = ZoneId.systemDefault()): ManualPhysicalReadingSession? {
        return prepare(zone)?.completedBy(now)
    }

    /** Parse only when inputs change; completion is checked separately against a fresh clock. */
    fun prepare(zone: ZoneId): ManualPhysicalReadingSession? {
        val hours = hours.ifBlank { "0" }.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minutes = minutes.ifBlank { "0" }.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        val duration = hours * 3600L + minutes * 60L
        if (duration <= 0 || startHour !in 0..23 || startMinute !in 0..59) return null
        val start = startPage.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val end = endPage.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val total = if (pageCount.isBlank()) null else pageCount.toIntOrNull()?.takeIf { it > 0 } ?: return null
        if (total != null && (start > total || end > total)) return null
        val date = Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val startedAt = date.atTime(startHour, startMinute).atZone(zone).toInstant().toEpochMilli()
        val endedAt = startedAt + duration * 1000
        if (startedAt <= 0) return null
        return ManualPhysicalReadingSession(startedAt, endedAt, duration, start, end, total)
    }
}
