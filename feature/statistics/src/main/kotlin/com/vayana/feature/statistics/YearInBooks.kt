package com.vayana.feature.statistics

import com.vayana.core.database.model.Book
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.floor

data class YearInBooks(
    val year: Int,
    val currentMonth: Int,
    /** January through December. Future months stay zero until their dates arrive. */
    val monthlyFinishes: List<Int>,
    val finishedCount: Int,
    val goalTarget: Int,
    val goalPace: YearlyGoalPace?,
)

data class YearlyGoalPace(
    val booksRemaining: Int,
    val daysRemaining: Int,
    /** Conservative whole-day interval; null when more than one book per day is needed. */
    val daysPerBook: Int?,
)

internal fun List<Book>.yearInBooks(
    today: LocalDate,
    zone: ZoneId,
    yearlyGoal: Int,
): YearInBooks {
    val months = MutableList(12) { 0 }
    for (book in this) {
        val finishedAt = book.finishedReadingAt ?: continue
        val date = Instant.ofEpochMilli(finishedAt).atZone(zone).toLocalDate()
        if (date.year == today.year && !date.isAfter(today)) months[date.monthValue - 1]++
    }
    val finished = months.sum()
    val remaining = (yearlyGoal - finished).coerceAtLeast(0)
    val daysRemaining = (ChronoUnit.DAYS.between(today, LocalDate.of(today.year, 12, 31)) + 1L).toInt()
    return YearInBooks(
        year = today.year,
        currentMonth = today.monthValue,
        monthlyFinishes = months,
        finishedCount = finished,
        goalTarget = yearlyGoal,
        goalPace = if (yearlyGoal > 0) YearlyGoalPace(
            booksRemaining = remaining,
            daysRemaining = daysRemaining,
            daysPerBook = if (remaining in 1..daysRemaining) floor(daysRemaining.toDouble() / remaining).toInt() else null,
        ) else null,
    )
}

internal fun List<Book>.averageActiveProgressPercent(finishedThreshold: Float): Int {
    val active = filter { it.readingPercent > 0f && it.readingPercent < finishedThreshold }
    return if (active.isEmpty()) 0 else (active.sumOf { it.readingPercent.toDouble() } / active.size * 100).toInt()
}
