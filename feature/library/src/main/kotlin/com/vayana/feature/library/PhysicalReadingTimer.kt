package com.vayana.feature.library

import com.vayana.core.database.model.ReadingSession
import kotlin.math.ceil
import kotlin.math.abs

typealias PhysicalTimerPhase = com.vayana.core.wear.PhysicalTimerPhase
typealias PhysicalTimerSession = com.vayana.core.wear.PhysicalTimerSession

internal data class PhysicalSessionRecorded(val pagesRead: Int, val minutesRead: Int)

internal fun physicalSessionRecorded(timer: PhysicalTimerSession, endPage: Int): PhysicalSessionRecorded {
    val pages = abs(endPage.toLong() - timer.startPage).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val seconds = (timer.accumulatedMillis / 1000).coerceAtLeast(1)
    return PhysicalSessionRecorded(pagesRead = pages, minutesRead = (seconds / 60).coerceAtLeast(1).toInt())
}

internal fun validPhysicalTimerPages(startPage: Int?, endPage: Int?, totalText: String, knownTotal: Int? = null): Boolean {
    if (startPage == null || endPage == null || startPage < 0 || endPage < 0) return false
    val total = if (totalText.isBlank()) knownTotal ?: return true else totalText.toIntOrNull() ?: return false
    return total > 0 && startPage <= total && endPage <= total
}

internal data class PhysicalReadingPace(val pagesPerHour: Double?, val remainingSeconds: Long?,
    val sampleCount: Int = 0, val timedPages: Long = 0)

internal fun physicalReadingPace(sessions: List<ReadingSession>, pageCount: Int?, currentPage: Int?): PhysicalReadingPace {
    // Untimed progress and rereading cannot provide a reliable forward-reading pace.
    var seconds = 0L
    var pages = 0L
    // Keep a bounded newest-first sample instead of allocating and sorting the entire history.
    val recent = ArrayList<ReadingSession>(6)
    for (session in sessions) {
        val start = session.startPage ?: continue
        val end = session.endPage ?: continue
        if (session.durationSeconds <= 0 || start < 0 || end <= start) continue
        val index = recent.indexOfFirst { session.startedAt > it.startedAt }.let { if (it < 0) recent.size else it }
        if (index >= 5) continue
        recent.add(index, session)
        if (recent.size > 5) recent.removeAt(5)
    }
    for (session in recent) {
        seconds += session.durationSeconds
        pages += session.endPage!!.toLong() - session.startPage!!
    }
    if (seconds == 0L || pages == 0L) return PhysicalReadingPace(null, null)
    val pace = pages.toDouble() * 3600 / seconds
    val remaining = if (pageCount != null && currentPage != null && pageCount > 0)
        ceil((pageCount - currentPage).coerceAtLeast(0).toDouble() * seconds / pages).toLong() else null
    return PhysicalReadingPace(pace, remaining, recent.size, pages)
}
