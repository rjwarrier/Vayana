package com.vayana.feature.statistics

import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.repository.HighlightReview
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class HighlightReviewTest {
    private fun highlight(id: Long, text: String = "text $id", locator: String = "epubcfi(/6/$id)") = Annotation(
        id = id,
        bookId = 1,
        type = AnnotationType.HIGHLIGHT,
        colorKey = "yellow",
        locator = locator,
        chapterTitle = null,
        chapterHref = null,
        selectedText = text,
        readerNote = null,
        createdAt = id,
        updatedAt = id,
    )

    @Test
    fun skipsBlankAndCommunityQuotes() {
        val picks = dailyHighlights(
            listOf(
                highlight(1),
                highlight(2, text = ""),
                highlight(3, locator = "quote:0:x"),
                highlight(4, locator = "goodreads-quote:0:x"),
            ),
            LocalDate.of(2026, 1, 1),
        )
        assertEquals(listOf(1L), picks.map { it.id })
    }

    @Test
    fun rotatesThroughEveryHighlightAcrossDays() {
        val all = (1L..12L).map { highlight(it) }
        val start = LocalDate.of(2026, 1, 1)
        val seen = (0L until 12L).flatMap { day -> dailyHighlights(all, start.plusDays(day), count = 5).map { it.id } }.toSet()

        assertEquals(all.map { it.id }.toSet(), seen)
        assertEquals(dailyHighlights(all, start, 5), dailyHighlights(all.reversed(), start, 5))
        assertEquals(5, dailyHighlights(all, start, 5).distinct().size)
    }

    private fun review(id: Long, dueAt: Long) =
        HighlightReview(annotationId = id, dueAt = dueAt, intervalDays = 1, easeFactor = 2.5f, repetitions = 1, lastReviewedAt = 0)

    @Test
    fun dueHighlightsListOverdueFirstThenNeverReviewed() {
        val all = listOf(highlight(1), highlight(2), highlight(3), highlight(4))
        val reviews = mapOf(2L to review(2, dueAt = 900), 3L to review(3, dueAt = 500), 4L to review(4, dueAt = 2_000))

        val picks = dueHighlights(all, reviews, now = 1_000)

        // 3 is the longest overdue, then 2; 4 is scheduled for later; 1 was never reviewed.
        assertEquals(listOf(3L, 2L, 1L), picks.map { it.id })
    }

    @Test
    fun dueHighlightsStopAtTheSessionLimitAndSkipCommunityQuotes() {
        val all = (1L..30L).map { highlight(it) } + highlight(99, locator = "goodreads-quote:0:x")

        val picks = dueHighlights(all, emptyMap(), now = 0, limit = 10)

        assertEquals((1L..10L).toList(), picks.map { it.id })
    }

    @Test
    fun nothingIsDueWhenEveryReviewIsScheduledLater() {
        val all = listOf(highlight(1), highlight(2))
        val reviews = mapOf(1L to review(1, dueAt = 5_000), 2L to review(2, dueAt = 6_000))

        assertEquals(emptyList(), dueHighlights(all, reviews, now = 1_000))
    }
}
