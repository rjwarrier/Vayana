package com.vayana.feature.statistics

import com.vayana.core.database.model.Annotation
import java.time.LocalDate

/**
 * Today's highlights to resurface: [count] of the reader's own highlights (not bookmarks or imported community
 * quotes), oldest first, starting further along each day. Every highlight comes back in turn, and the same set
 * shows all day without storing anything.
 */
internal fun dailyHighlights(annotations: List<Annotation>, today: LocalDate, count: Int = DailyHighlightCount): List<Annotation> {
    val eligible = annotations
        .filter { it.selectedText.isNotBlank() && !it.locator.startsWith(CommunityQuotePrefix) }
        .sortedWith(compareBy<Annotation> { it.createdAt }.thenBy { it.id })
    if (eligible.size <= count) return eligible
    val start = Math.floorMod(today.toEpochDay() * count, eligible.size.toLong()).toInt()
    return List(count) { offset -> eligible[(start + offset) % eligible.size] }
}

internal const val DailyHighlightCount = 5
private const val CommunityQuotePrefix = "quote:"
