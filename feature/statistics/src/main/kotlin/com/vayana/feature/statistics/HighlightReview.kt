package com.vayana.feature.statistics

import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.isCommunityQuoteLocator
import com.vayana.core.database.repository.HighlightReview
import java.time.LocalDate

/** The reader's own highlights and notes with text: not bookmarks, and not popular quotes imported from Goodreads. */
internal fun reviewableHighlights(annotations: List<Annotation>): List<Annotation> = annotations.filter { annotation ->
    annotation.selectedText.isNotBlank() && !isCommunityQuoteLocator(annotation.locator)
}

/**
 * The highlights to review now: those whose scheduled review has come round, longest overdue first, then highlights
 * never reviewed, oldest first, up to [limit]. A highlight rated "again" comes back after minutes and one rated well
 * after a longer wait each time (see `VocabularySchedule`), so a large collection resurfaces in manageable sessions.
 */
internal fun dueHighlights(
    annotations: List<Annotation>,
    reviews: Map<Long, HighlightReview>,
    now: Long,
    limit: Int = ReviewSessionSize,
): List<Annotation> {
    val eligible = reviewableHighlights(annotations)
    val overdue = eligible
        .filter { annotation -> reviews[annotation.id]?.let { it.dueAt <= now } == true }
        .sortedWith(compareBy<Annotation> { reviews.getValue(it.id).dueAt }.thenBy { it.id })
    val fresh = eligible
        .filter { annotation -> annotation.id !in reviews }
        .sortedWith(compareBy<Annotation> { it.createdAt }.thenBy { it.id })
    return (overdue + fresh).take(limit)
}

/**
 * A fixed set for practising without scheduling: [count] highlights, oldest first, starting further along each day.
 * Every highlight comes back in turn, and the same set shows all day without storing anything.
 */
internal fun dailyHighlights(annotations: List<Annotation>, today: LocalDate, count: Int = DailyHighlightCount): List<Annotation> {
    val eligible = reviewableHighlights(annotations)
        .sortedWith(compareBy<Annotation> { it.createdAt }.thenBy { it.id })
    if (eligible.size <= count) return eligible
    val start = Math.floorMod(today.toEpochDay() * count, eligible.size.toLong()).toInt()
    return List(count) { offset -> eligible[(start + offset) % eligible.size] }
}

internal const val DailyHighlightCount = 5
internal const val ReviewSessionSize = 10
