package com.vayana.feature.statistics

import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
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
}
