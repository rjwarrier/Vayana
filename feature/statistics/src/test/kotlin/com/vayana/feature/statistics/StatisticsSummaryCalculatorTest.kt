package com.vayana.feature.statistics

import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.model.ReadingSession
import com.vayana.core.database.model.VocabularyCard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class StatisticsSummaryCalculatorTest {
    @Test
    fun vocabularyAndAnnotationUpdatesPreserveReadingWorkAndYearReviewSemantics() {
        val calculator = StatisticsSummaryCalculator()
        val today = LocalDate.of(2026, 10, 3)
        val zone = ZoneId.of("Asia/Kolkata")
        val time = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = listOf(ReadingSession(1, "session", 1, time, time + 120_000, 120))
        val word = VocabularyCard(1, "word", "definition", null, null, null, time, null, false)
        val mark = Annotation(1, 1, AnnotationType.HIGHLIGHT, "yellow", "epubcfi(/6/2)", null, null, "text", "note", time, time)
        fun summary(words: List<VocabularyCard>, marks: List<Annotation>, day: LocalDate = today) = calculator.calculate(
            emptyList(), marks, emptyList(), sessions, words, 20, 12, .95f, DayOfWeek.MONDAY, zone, day,
        )
        val first = summary(listOf(word), emptyList())
        val reviewed = summary(listOf(word.copy(known = true)), emptyList())
        assertSame(first.dailyReadingMinutes, reviewed.dailyReadingMinutes)
        assertEquals(0f, first.vocabularyGrowth?.masteredFraction)
        assertEquals(1f, reviewed.vocabularyGrowth?.masteredFraction)
        val annotations = listOf(mark, mark.copy(id = 2, isDeleted = true), mark.copy(id = 3, type = AnnotationType.UNDERLINE, colorKey = "popular", locator = "quote:1"))
        val withNotes = summary(listOf(word.copy(known = true)), annotations)
        assertSame(reviewed.dailyReadingMinutes, withNotes.dailyReadingMinutes)
        assertSame(reviewed.vocabularyGrowth, withNotes.vocabularyGrowth)
        assertEquals(yearReview(emptyList(), sessions, annotations, listOf(word), today, zone), withNotes.yearReview)
        assertEquals(2, withNotes.todayReadingMinutes)
        assertEquals(0, summary(listOf(word), annotations, today.plusDays(1)).todayReadingMinutes)
    }
}
