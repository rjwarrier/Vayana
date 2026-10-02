package com.vayana.feature.statistics

import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.ReadingSession
import com.vayana.core.database.model.VocabularyCard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YearReviewTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 2)

    @Test
    fun nothingThisYearMeansNoReview() {
        val lastYear = session(1, LocalDate.of(2025, 12, 31), hour = 20, minutes = 30)
        assertNull(yearReview(listOf(book(1)), listOf(lastYear), emptyList(), emptyList(), today, zone))
    }

    @Test
    fun summarisesTheYearsReading() {
        val books = listOf(
            book(1, title = "Dune", author = "Frank Herbert", finished = LocalDate.of(2026, 3, 10), pages = 600, seconds = 40_000),
            book(2, title = "Children of Dune", author = "frank herbert", finished = LocalDate.of(2026, 5, 2), pages = 450, seconds = 30_000),
            book(3, title = "Emma", author = "Jane Austen", finished = LocalDate.of(2026, 6, 1), pages = 300, seconds = 10_000),
            book(4, title = "Old", author = "Someone", finished = LocalDate.of(2025, 6, 1), pages = 900),
        )
        val sessions = listOf(
            session(1, LocalDate.of(2026, 3, 9), hour = 21, minutes = 60),
            session(1, LocalDate.of(2026, 3, 10), hour = 22, minutes = 45),
            session(2, LocalDate.of(2026, 3, 11), hour = 21, minutes = 30),
            session(3, LocalDate.of(2026, 6, 1), hour = 7, minutes = 20),
            session(3, LocalDate.of(2026, 6, 1), hour = 7, minutes = 0, seconds = 20),
            session(4, LocalDate.of(2025, 6, 1), hour = 7, minutes = 500),
        )
        val annotations = listOf(
            annotation(1, AnnotationType.HIGHLIGHT, LocalDate.of(2026, 3, 9), note = "fear is the mind-killer"),
            annotation(2, AnnotationType.HIGHLIGHT, LocalDate.of(2026, 3, 9)),
            annotation(3, AnnotationType.NOTE, LocalDate.of(2026, 4, 1), note = "hm"),
            annotation(4, AnnotationType.HIGHLIGHT, LocalDate.of(2025, 4, 1)),
        )
        val words = listOf(card(1, LocalDate.of(2026, 2, 1)), card(2, LocalDate.of(2025, 2, 1)))

        val review = checkNotNull(yearReview(books, sessions, annotations, words, today, zone))

        assertEquals(2026, review.year)
        assertEquals(3, review.finishedBooks)
        assertEquals((60 + 45 + 30 + 20) * 60L, review.readingSeconds)
        assertEquals(4, review.activeDays)
        assertEquals(3, review.longestStreakDays)
        assertEquals(Month.MARCH, review.busiestMonth)
        assertEquals(DayOfWeek.MONDAY, review.busiestWeekday)
        assertEquals(ReaderKind.EVENING, review.readerKind)
        assertEquals("Dune", review.topBook?.title)
        assertEquals((60 + 45) * 60L, review.topBook?.seconds)
        assertEquals("Frank Herbert", review.topAuthor)
        assertEquals(2, review.topAuthorBooks)
        assertEquals("Dune", review.longestFinishedTitle)
        assertEquals(600, review.longestFinishedPages)
        assertEquals(2, review.highlights)
        assertEquals(2, review.notes)
        assertEquals(1, review.savedWords)
    }

    @Test
    fun streaksAndReaderKindFollowTheCalendarAndTheClock() {
        val days = setOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6))
        assertEquals(3, longestStreak(days))
        assertEquals(0, longestStreak(emptySet()))
        val night = listOf(session(1, today, hour = 1, minutes = 40), session(1, today, hour = 9, minutes = 10))
        assertEquals(ReaderKind.NIGHT_OWL, readerKind(night, zone))
        assertNull(readerKind(emptyList(), zone))
    }

    private fun millis(date: LocalDate, hour: Int = 12) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun session(bookId: Long, date: LocalDate, hour: Int, minutes: Int, seconds: Int = 0): ReadingSession {
        val start = millis(date, hour)
        val duration = minutes * 60L + seconds
        return ReadingSession(0, "s-$bookId-$start", bookId, start, start + duration * 1000, duration)
    }

    private fun annotation(id: Long, type: AnnotationType, date: LocalDate, note: String? = null) = Annotation(
        id = id, bookId = 1, type = type, colorKey = "yellow", locator = "epubcfi(/6/2)", chapterTitle = null, chapterHref = null,
        selectedText = "text", readerNote = note, createdAt = millis(date), updatedAt = millis(date), syncId = "a-$id",
    )

    private fun card(id: Long, date: LocalDate) = VocabularyCard(
        id = id, word = "w$id", definition = "d", sentence = null, bookId = null, bookTitle = null,
        createdAt = millis(date), lastReviewedAt = null, known = false,
    )

    private fun book(
        id: Long,
        title: String = "Book $id",
        author: String? = null,
        finished: LocalDate? = null,
        pages: Int? = null,
        seconds: Long = 0,
    ) = Book(
        id = id, syncId = "book-$id", title = title, author = author, series = null, seriesNumber = null, description = null,
        coverPath = null, filePath = "", fileAvailability = BookFileAvailability.LOCAL, format = BookFormat.EPUB,
        fileHash = "hash-$id", readingPercent = if (finished != null) 1f else 0f, rating = 0f, createdAt = 0L, updatedAt = 0L,
        lastReadAt = null, lastLocator = null, finishedReadingAt = finished?.let { millis(it) },
        totalReadingSeconds = seconds, pageCount = pages,
    )
}
