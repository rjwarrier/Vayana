package com.vayana.feature.statistics

import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.model.ReadingSession
import com.vayana.core.database.model.VocabularyCard
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.BookFileAvailability
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class StatisticsSummaryCalculatorTest {
    @Test fun physicalReadingRecordsKeepDetailsDatesAndTimeWithoutRequiringCompletion() {
        val paper = Book(id = 1, syncId = "paper", title = "Paper book", author = "Author",
            series = null, seriesNumber = null, description = "Details", coverPath = "/covers/paper.jpg",
            filePath = "", fileAvailability = BookFileAvailability.LOCAL, format = BookFormat.PHYSICAL,
            fileHash = "paper", readingPercent = .5f, rating = 0f, createdAt = 1, updatedAt = 2,
            lastReadAt = 2000, lastLocator = null, startedReadingAt = 1000, totalReadingSeconds = 600)
        val finished = paper.copy(id = 2, syncId = "finished", readingPercent = 1f, finishedReadingAt = 3000)
        val unread = paper.copy(id = 3, syncId = "unread", readingPercent = 0f,
            startedReadingAt = null, lastReadAt = null, totalReadingSeconds = 0)
        val calculator = StatisticsSummaryCalculator()
        fun calculate(books: List<Book>) = calculator.calculate(books, emptyList(), emptyList(), emptyList(),
            emptyList(), 20, 12, .95f, DayOfWeek.MONDAY)
        val summary = calculate(listOf(paper, unread, finished))
        assertEquals(listOf(finished, paper), summary.readingRecords)
        assertEquals(1, summary.finishedBooks)
        val corrected = paper.copy(coverPath = "/covers/new.jpg", totalReadingSeconds = 1200)
        assertEquals(corrected, calculate(listOf(corrected)).readingRecords.single())
    }
    @Test
    fun syncedCheckpointsUpdateHeatmapTotalsAndYearReviewTogether() {
        val calculator = StatisticsSummaryCalculator()
        val today = LocalDate.of(2026, 10, 6)
        val zone = ZoneId.of("Asia/Kolkata")
        val start = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val digital = ReadingSession(1, "device-a", 1, start, start + 600_000, 600)
        val physical = ReadingSession(2, "device-b", 2, start + 900_000, start + 2_100_000, 1200, 10, 20)
        fun summary(sessions: List<ReadingSession>) = calculator.calculate(emptyList(), emptyList(), emptyList(),
            sessions, emptyList(), 20, 12, .95f, DayOfWeek.MONDAY, zone, today)
        val initial = summary(listOf(digital, physical))
        assertEquals(1800L, initial.totalReadingSeconds)
        assertEquals(30, initial.todayReadingMinutes)
        assertEquals(30, initial.dailyReadingMinutes.single { it.date == today }.minutes)
        assertEquals(1800L, initial.yearReview?.readingSeconds)
        val later = summary(listOf(digital.copy(endedAt = start + 900_000, durationSeconds = 900), physical))
        assertEquals(2100L, later.totalReadingSeconds)
        assertEquals(35, later.dailyReadingMinutes.single { it.date == today }.minutes)
        assertEquals(2100L, later.yearReview?.readingSeconds)
        assertEquals(2, later.sessionCount)
    }

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
