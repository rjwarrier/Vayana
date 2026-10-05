package com.vayana.core.database.model

/** A small projection for the physical timer; history rows are loaded only on demand. */
data class PhysicalReadingSessionSummary(val sessionCount: Long = 0, val totalSeconds: Long = 0) {
    val averageSeconds: Long get() = if (sessionCount > 0) totalSeconds / sessionCount else 0
}

data class ReadingSession(
    val id: Long,
    val syncId: String,
    val bookId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Long,
    val startPage: Int? = null,
    val endPage: Int? = null,
    val activeIntervals: String? = null,
)
