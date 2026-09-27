package com.vayana.feature.library

import com.vayana.core.database.model.normalizeBorrowReturnAt
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Daily target for finishing a borrowed physical book no later than the day before it is returned. */
internal data class BorrowedReadingPlan(
    val daysRemaining: Int,
    val pagesPerDay: Int?,
    val finishByAt: Long,
)

internal fun borrowedReadingPlan(
    pageCount: Int,
    currentPage: Int,
    returnAt: Long,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): BorrowedReadingPlan {
    val normalizedReturnAt = normalizeBorrowReturnAt(returnAt, zone) ?: return BorrowedReadingPlan(0, null, returnAt)
    val returnDateTime = Instant.ofEpochMilli(normalizedReturnAt).atZone(zone)
    val returnDate = returnDateTime.toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    // Today through the day before return is the reading window. Its size equals the calendar-day distance to return.
    val daysRemaining = ChronoUnit.DAYS.between(today, returnDate)
        .coerceIn(0L, Int.MAX_VALUE.toLong())
        .toInt()
    val pagesRemaining = (pageCount - currentPage).coerceAtLeast(0)
    val pagesPerDay = when {
        pagesRemaining == 0 -> 0
        daysRemaining == 0 -> null
        else -> ((pagesRemaining.toLong() + daysRemaining - 1L) / daysRemaining).toInt()
    }
    return BorrowedReadingPlan(
        daysRemaining = daysRemaining,
        pagesPerDay = pagesPerDay,
        finishByAt = returnDateTime.minusDays(1).toInstant().toEpochMilli(),
    )
}
