package com.vayana.feature.statistics

import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.ReadingSession
import com.vayana.core.database.model.VocabularyCard
import com.vayana.core.database.model.isCommunityQuote
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId

/** When in the day the reader reads most. */
enum class ReaderKind { EARLY_BIRD, AFTERNOON, EVENING, NIGHT_OWL }

data class YearReviewBook(val title: String, val author: String?, val seconds: Long)

/** The reader's year at a glance, for the shareable review: only things the reader actually did this year. */
data class YearReview(
    val year: Int,
    val finishedBooks: Int,
    val readingSeconds: Long,
    val activeDays: Int,
    val longestStreakDays: Int,
    val busiestMonth: Month?,
    val busiestWeekday: DayOfWeek?,
    val readerKind: ReaderKind?,
    val topBook: YearReviewBook?,
    val topAuthor: String?,
    val topAuthorBooks: Int,
    val longestFinishedTitle: String?,
    val longestFinishedPages: Int?,
    val highlights: Int,
    val notes: Int,
    val savedWords: Int,
)

/** Sessions under this are taps, not reading; the statistics screen ignores them too. */
private const val MinReviewSessionSeconds = 60L

internal fun yearReview(
    books: List<Book>,
    sessions: List<ReadingSession>,
    annotations: List<Annotation>,
    vocabularyCards: List<VocabularyCard>,
    today: LocalDate,
    zone: ZoneId,
): YearReview? {
    val year = today.year
    fun Long.date(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
    val yearSessions = sessions.filter { it.durationSeconds >= MinReviewSessionSeconds && it.startedAt.date().year == year }
    val finished = books.filter { book -> book.finishedReadingAt?.date()?.let { it.year == year && !it.isAfter(today) } == true }
    if (yearSessions.isEmpty() && finished.isEmpty()) return null

    val secondsByDate = yearSessions.groupingBy { it.startedAt.date() }.fold(0L) { total, session -> total + session.durationSeconds }
    val secondsByMonth = yearSessions.groupingBy { it.startedAt.date().month }.fold(0L) { total, session -> total + session.durationSeconds }
    val secondsByWeekday = secondsByDate.entries.groupingBy { it.key.dayOfWeek }.fold(0L) { total, entry -> total + entry.value }

    val byId = books.associateBy { it.id }
    val secondsByBook = yearSessions.groupingBy { it.bookId }.fold(0L) { total, session -> total + session.durationSeconds }
    val topBook = secondsByBook.maxByOrNull { it.value }?.let { (bookId, seconds) ->
        byId[bookId]?.let { YearReviewBook(it.title, it.author, seconds) }
    }

    val authors = finished.mapNotNull { book -> book.author?.trim()?.takeIf { it.isNotEmpty() }?.let { it to book } }
        .groupBy({ it.first.lowercase() }, { it.first to it.second })
    val topAuthorEntry = authors.values.maxByOrNull { group -> group.size * 1_000_000_000L + group.sumOf { it.second.totalReadingSeconds } }
    val longest = finished.filter { it.pageCount != null }.maxByOrNull { it.pageCount ?: 0 }

    val inYear = { millis: Long -> millis.date().year == year }
    val marks = annotations.filter { !it.isDeleted && !it.isCommunityQuote() && inYear(it.createdAt) }

    return YearReview(
        year = year,
        finishedBooks = finished.size,
        readingSeconds = yearSessions.sumOf { it.durationSeconds },
        activeDays = secondsByDate.size,
        longestStreakDays = longestStreak(secondsByDate.keys),
        busiestMonth = secondsByMonth.maxByOrNull { it.value }?.key,
        busiestWeekday = secondsByWeekday.maxByOrNull { it.value }?.key,
        readerKind = readerKind(yearSessions, zone),
        topBook = topBook,
        topAuthor = topAuthorEntry?.first()?.first,
        topAuthorBooks = topAuthorEntry?.size ?: 0,
        longestFinishedTitle = longest?.title,
        longestFinishedPages = longest?.pageCount,
        highlights = marks.count { it.type == AnnotationType.HIGHLIGHT },
        notes = marks.count { !it.readerNote.isNullOrBlank() },
        savedWords = vocabularyCards.count { inYear(it.createdAt) },
    )
}

/** The longest run of consecutive days with any reading. */
internal fun longestStreak(days: Set<LocalDate>): Int {
    var best = 0
    for (day in days) {
        if (day.minusDays(1) in days) continue
        var length = 1
        while (day.plusDays(length.toLong()) in days) length++
        best = maxOf(best, length)
    }
    return best
}

/** Where most of the reading time falls: 5-11 early bird, 12-17 afternoon, 18-22 evening, otherwise night owl. */
internal fun readerKind(sessions: List<ReadingSession>, zone: ZoneId): ReaderKind? {
    if (sessions.isEmpty()) return null
    val seconds = HashMap<ReaderKind, Long>()
    for (session in sessions) {
        val hour = Instant.ofEpochMilli(session.startedAt).atZone(zone).hour
        val kind = when (hour) {
            in 5..11 -> ReaderKind.EARLY_BIRD
            in 12..17 -> ReaderKind.AFTERNOON
            in 18..22 -> ReaderKind.EVENING
            else -> ReaderKind.NIGHT_OWL
        }
        seconds[kind] = (seconds[kind] ?: 0L) + session.durationSeconds
    }
    return seconds.maxByOrNull { it.value }?.key
}
