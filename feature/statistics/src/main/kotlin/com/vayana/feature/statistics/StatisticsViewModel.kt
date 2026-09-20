package com.vayana.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.ReadingSession
import com.vayana.core.database.model.VocabularyCard
import com.vayana.core.database.model.WordLookupStat
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.HighlightReviewRepository
import com.vayana.core.database.repository.ReadingSessionRepository
import com.vayana.core.database.repository.VocabularyCardRepository
import com.vayana.core.database.repository.WordLookupStatRepository
import com.vayana.core.datastore.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

data class DailyReadingMinutes(
    val date: LocalDate,
    /** Minutes read that day, or [FutureDayMinutes] for a date after today (a trailing cell in
     *  the current, still-incomplete week of the grid) - distinct from a past day that had zero. */
    val minutes: Int,
) {
    val isFuture: Boolean get() = minutes == FutureDayMinutes
}

const val FutureDayMinutes = -1

data class ReadingPaceEstimate(
    val bookTitle: String,
    val estimatedDaysRemaining: Int,
)

/** One tag treated as a genre: how many books carry it and how much time has gone into them. */
data class GenreStat(
    val name: String,
    val bookCount: Int,
    val totalSeconds: Long,
)

data class ReadingHabits(
    val busiestDayOfWeek: DayOfWeek?,
    val averageSessionMinutes: Int,
    /** Average days from starting a book to finishing it, across books with both timestamps. */
    val averageDaysToFinish: Int?,
    val booksFinishedLastYear: Int,
)

data class AuthorStat(
    val author: String,
    val bookCount: Int,
    val totalSeconds: Long,
)

/** How much of an author's imported catalog has actually been finished - "of what I own", not a
 *  claim about the author's or series' true total, which this app has no way to know. */
data class SeriesProgress(
    val seriesName: String,
    val finishedCount: Int,
    val totalCount: Int,
)

data class VocabularyGrowth(
    val masteredFraction: Float,
    /** Last [VocabularyGrowthWeeks] weeks of new-card counts, oldest first. */
    val weeklyNewCards: List<Int>,
)

data class StatisticsSummary(
    val totalBooks: Int = 0,
    val readingBooks: Int = 0,
    val finishedBooks: Int = 0,
    val averageProgressPercent: Int = 0,
    val totalAnnotations: Int = 0,
    val notesWithText: Int = 0,
    /** Today's rotating set of highlights to resurface; the card shows the first and opens the rest. */
    val highlightsToRevisit: List<Annotation> = emptyList(),
    val topLookedUpWords: List<WordLookupStat> = emptyList(),
    val sessionCount: Int = 0,
    val longestSessionSeconds: Long = 0L,
    val currentStreakDays: Int = 0,
    val todayReadingMinutes: Int = 0,
    val recentWeekReadingMinutes: Int = 0,
    val dailyGoalMinutes: Int = 0,
    val booksFinishedThisYear: Int = 0,
    val yearlyGoalBooks: Int = 0,
    /** Weeks (starting on the user's first day of the week) ending this week, oldest first, sized to a whole number of 7-day
     *  columns so the UI can chunk it directly with no partial-week special-casing - a
     *  GitHub-style contribution grid. Between [MinActivityGridWeeks] and [ActivityGridWeeks]
     *  weeks wide depending on how long there's been any activity to show. */
    val dailyReadingMinutes: List<DailyReadingMinutes> = emptyList(),
    val readingPace: ReadingPaceEstimate? = null,
    /** Tags treated as genres, richest (most time invested) first. */
    val genreStats: List<GenreStat> = emptyList(),
    val readingHabits: ReadingHabits? = null,
    val uniqueAuthorCount: Int = 0,
    val topAuthor: AuthorStat? = null,
    val topSeries: SeriesProgress? = null,
    val vocabularyGrowth: VocabularyGrowth? = null,
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    bookRepository: BookRepository,
    annotationRepository: AnnotationRepository,
    wordLookupStatRepository: WordLookupStatRepository,
    readingSessionRepository: ReadingSessionRepository,
    settingsRepository: SettingsRepository,
    vocabularyCardRepository: VocabularyCardRepository,
    highlightReviewRepository: HighlightReviewRepository,
) : ViewModel() {
    /** One live query shared by the summary and the due-highlights list, instead of two. */
    private val annotations = annotationRepository.observeAll()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val coreInputs = combine(
        bookRepository.observeAll(),
        annotations,
        wordLookupStatRepository.observeTop(TopLookedUpWordsLimit),
        readingSessionRepository.observeAll(),
        settingsRepository.snapshot,
    ) { books, annotations, topWords, sessions, settings -> CoreInputs(books, annotations, topWords, sessions, settings) }

    /** One live query shared by the summary and the card count, instead of two. */
    private val vocabularyCards = vocabularyCardRepository.observeAll()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val summary: StateFlow<StatisticsSummary> = combine(
        coreInputs,
        vocabularyCards,
    ) { inputs, vocabularyCards ->
        inputs.books.toSummary(
            annotations = inputs.annotations,
            topWords = inputs.topWords,
            sessions = inputs.sessions,
            vocabularyCards = vocabularyCards,
            dailyGoalMinutes = inputs.settings.dailyReadingGoalMinutes,
            yearlyGoalBooks = inputs.settings.yearlyBooksGoal,
            finishedThreshold = inputs.settings.finishedFraction,
            firstDayOfWeek = inputs.settings.weekStart.day,
        )
    }
        // Summarising every book and session re-runs on each change to any of them; keep it off the main thread.
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsSummary())

    /** Highlights whose review has come round, or that were never reviewed, ready for the review screen. */
    val highlightsDue: StateFlow<List<Annotation>> = combine(
        annotations,
        highlightReviewRepository.observeAll(),
    ) { annotations, reviews -> dueHighlights(annotations, reviews, System.currentTimeMillis()) }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vocabularyCardCount: StateFlow<Int> = vocabularyCards
        .map { it.size }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Saved words due for review, counted from when the screen starts watching. */
    val vocabularyDueCount: StateFlow<Int> = vocabularyCardRepository.observeDueCount()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

private data class CoreInputs(
    val books: List<Book>,
    val annotations: List<Annotation>,
    val topWords: List<WordLookupStat>,
    val sessions: List<ReadingSession>,
    val settings: com.vayana.core.datastore.settings.SettingsSnapshot,
)

private fun List<Book>.toSummary(
    annotations: List<Annotation>,
    topWords: List<WordLookupStat>,
    sessions: List<ReadingSession>,
    vocabularyCards: List<VocabularyCard>,
    dailyGoalMinutes: Int,
    yearlyGoalBooks: Int,
    finishedThreshold: Float,
    firstDayOfWeek: DayOfWeek,
): StatisticsSummary {
    val average = if (isEmpty()) 0 else (sumOf { (it.readingPercent * 100).toDouble() } / size).toInt()
    val countedSessions = sessions.filter { it.durationSeconds >= MinCountedSessionSeconds }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    // One pass over sessions, keyed by day, backs the streak check, the activity chart, and the
    // pace estimate below - the alternative (re-filtering the session list per day) is quadratic
    // in the chart window for no benefit.
    val secondsByDate: Map<LocalDate, Long> = countedSessions
        .groupingBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        .fold(0L) { total, session -> total + session.durationSeconds }
    var streak = 0
    var probe = today
    // A streak "survives" a still-open today with zero sessions - it only breaks once yesterday
    // is also missing, so reading last night still shows a live streak this morning.
    if (today !in secondsByDate) probe = today.minusDays(1)
    while (probe in secondsByDate) {
        streak++
        probe = probe.minusDays(1)
    }
    // Week columns starting on [firstDayOfWeek], GitHub-style: the grid always ends on this week's last day
    // regardless of what day "today" is, so the column count and shape never change day to day -
    // only how many trailing cells in the last column are still in the future.
    val currentWeekStart = today.startOfWeek(firstDayOfWeek)
    val maxLookbackStart = currentWeekStart.minusWeeks((ActivityGridWeeks - 1).toLong())
    val minLookbackStart = currentWeekStart.minusWeeks((MinActivityGridWeeks - 1).toLong())
    // A brand-new library has no reason to drag in 53 weeks of empty squares before the first
    // book was even added - start the grid at the first real activity instead, still bounded to
    // at least MinActivityGridWeeks (so day one doesn't look like a single bare column) and at
    // most ActivityGridWeeks (so a long-lived library still gets the familiar year view).
    val earliestActivityDate = (sessions.minOfOrNull { it.startedAt } ?: minOfOrNull { it.createdAt })
        ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    val earliestWeekStart = earliestActivityDate?.startOfWeek(firstDayOfWeek)
    val gridStart = (earliestWeekStart ?: minLookbackStart).coerceIn(maxLookbackStart, minLookbackStart)
    val totalWeeks = ((currentWeekStart.toEpochDay() - gridStart.toEpochDay()) / 7 + 1).toInt()
    val dailyReadingMinutes = (0 until totalWeeks * 7).map { offset ->
        val date = gridStart.plusDays(offset.toLong())
        val minutes = if (date.isAfter(today)) FutureDayMinutes else ((secondsByDate[date] ?: 0L) / 60L).toInt()
        DailyReadingMinutes(date = date, minutes = minutes)
    }
    val recentWeekMinutes = (0 until 7).sumOf { daysAgo -> ((secondsByDate[today.minusDays(daysAgo.toLong())] ?: 0L) / 60L).toInt() }
    val finishedThisYear = count { book ->
        book.finishedReadingAt?.let { Instant.ofEpochMilli(it).atZone(zone).year == today.year } == true
    }
    return StatisticsSummary(
        totalBooks = size,
        readingBooks = count { it.readingPercent > 0f && it.readingPercent < finishedThreshold },
        finishedBooks = count { it.readingPercent >= finishedThreshold },
        averageProgressPercent = average.coerceIn(0, 100),
        totalAnnotations = annotations.size,
        notesWithText = annotations.count { it.readerNote?.isNotBlank() == true },
        highlightsToRevisit = dailyHighlights(annotations, today),
        topLookedUpWords = topWords,
        sessionCount = countedSessions.size,
        longestSessionSeconds = countedSessions.maxOfOrNull { it.durationSeconds } ?: 0L,
        currentStreakDays = streak,
        todayReadingMinutes = ((secondsByDate[today] ?: 0L) / 60L).toInt(),
        recentWeekReadingMinutes = recentWeekMinutes,
        dailyGoalMinutes = dailyGoalMinutes,
        booksFinishedThisYear = finishedThisYear,
        yearlyGoalBooks = yearlyGoalBooks,
        dailyReadingMinutes = dailyReadingMinutes,
        readingPace = readingPaceEstimate(secondsByDate, today, finishedThreshold),
        genreStats = genreStats(),
        readingHabits = readingHabits(countedSessions, zone, today),
        uniqueAuthorCount = mapNotNull { it.author?.trim()?.lowercase()?.ifBlank { null } }.distinct().size,
        topAuthor = topAuthor(),
        topSeries = topSeries(finishedThreshold),
        vocabularyGrowth = vocabularyGrowth(vocabularyCards, zone, today, firstDayOfWeek),
    )
}

/**
 * "At this pace, you'll finish X in about N days": extrapolates the book's own historical
 * seconds-read-per-percent-progress rate to the remaining percentage, then divides by how many
 * seconds/day the last week actually averaged. Deliberately book-specific (not a generic reading
 * speed) since it's already exactly what the book's own totalReadingSeconds/readingPercent encode -
 * no word count or reading-speed assumption needed.
 */
private fun List<Book>.readingPaceEstimate(
    secondsByDate: Map<LocalDate, Long>,
    today: LocalDate,
    finishedThreshold: Float,
): ReadingPaceEstimate? {
    val currentBook = filter { it.readingPercent > 0.01f && it.readingPercent < finishedThreshold && it.totalReadingSeconds > 0L }
        .maxByOrNull { it.lastReadAt ?: 0L }
        ?: return null
    val recentSecondsPerDay = (0 until PaceWindowDays)
        .sumOf { daysAgo -> secondsByDate[today.minusDays(daysAgo.toLong())] ?: 0L }
        .toDouble() / PaceWindowDays
    if (recentSecondsPerDay <= 0.0) return null
    val secondsPerPercent = currentBook.totalReadingSeconds / currentBook.readingPercent
    val remainingSeconds = secondsPerPercent * (1f - currentBook.readingPercent)
    val estimatedDays = ceil(remainingSeconds / recentSecondsPerDay).toInt().coerceAtLeast(1)
    if (estimatedDays > MaxEstimatedDays) return null
    return ReadingPaceEstimate(bookTitle = currentBook.title, estimatedDaysRemaining = estimatedDays)
}

/** Tags are the only per-book categorical field this app has (no parsed EPUB genre metadata) -
 *  treat them as genres. A book can carry several, so it contributes to each of its tags' totals. */
private fun List<Book>.genreStats(): List<GenreStat> {
    val totals = LinkedHashMap<String, GenreStat>()
    for (book in this) {
        for (tag in book.tagsCsv.splitTags()) {
            val key = tag.lowercase()
            val existing = totals[key]
            totals[key] = GenreStat(
                name = existing?.name ?: tag,
                bookCount = (existing?.bookCount ?: 0) + 1,
                totalSeconds = (existing?.totalSeconds ?: 0L) + book.totalReadingSeconds,
            )
        }
    }
    return totals.values.sortedByDescending { it.totalSeconds }.take(MaxGenreStats)
}

private fun String?.splitTags(): List<String> =
    this?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

private fun List<Book>.readingHabits(countedSessions: List<ReadingSession>, zone: ZoneId, today: LocalDate): ReadingHabits? {
    val finishDurationsDays = mapNotNull { book ->
        val started = book.startedReadingAt
        val finished = book.finishedReadingAt
        if (started == null || finished == null || finished <= started) return@mapNotNull null
        TimeUnit.MILLISECONDS.toDays(finished - started).toInt()
    }
    val averageDaysToFinish = if (finishDurationsDays.isEmpty()) null else finishDurationsDays.average().roundToInt().coerceAtLeast(1)
    val lastYear = today.year - 1
    val finishedLastYear = count { book ->
        book.finishedReadingAt?.let { Instant.ofEpochMilli(it).atZone(zone).year == lastYear } == true
    }
    if (countedSessions.isEmpty() && averageDaysToFinish == null && finishedLastYear == 0) return null
    val secondsByDayOfWeek = countedSessions
        .groupingBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).dayOfWeek }
        .fold(0L) { total, session -> total + session.durationSeconds }
    val busiestDay = secondsByDayOfWeek.maxByOrNull { it.value }?.key
    val averageSessionMinutes = if (countedSessions.isEmpty()) {
        0
    } else {
        (countedSessions.sumOf { it.durationSeconds } / countedSessions.size / 60L).toInt()
    }
    return ReadingHabits(
        busiestDayOfWeek = busiestDay,
        averageSessionMinutes = averageSessionMinutes,
        averageDaysToFinish = averageDaysToFinish,
        booksFinishedLastYear = finishedLastYear,
    )
}

private fun List<Book>.topAuthor(): AuthorStat? {
    val totals = LinkedHashMap<String, AuthorStat>()
    for (book in this) {
        val author = book.author?.trim()?.ifBlank { null } ?: continue
        val key = author.lowercase()
        val existing = totals[key]
        totals[key] = AuthorStat(
            author = existing?.author ?: author,
            bookCount = (existing?.bookCount ?: 0) + 1,
            totalSeconds = (existing?.totalSeconds ?: 0L) + book.totalReadingSeconds,
        )
    }
    return totals.values.maxByOrNull { it.totalSeconds }?.takeIf { it.totalSeconds > 0L }
}

private fun List<Book>.topSeries(finishedThreshold: Float): SeriesProgress? {
    val bySeriesName = filter { !it.series.isNullOrBlank() }.groupBy { it.series!!.trim() }
    return bySeriesName.entries
        .map { (name, books) -> SeriesProgress(name, books.count { it.readingPercent >= finishedThreshold }, books.size) }
        .filter { it.totalCount > 1 }
        .maxByOrNull { it.totalCount }
}

private fun vocabularyGrowth(
    cards: List<VocabularyCard>,
    zone: ZoneId,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
): VocabularyGrowth? {
    if (cards.isEmpty()) return null
    val masteredFraction = cards.count { it.known }.toFloat() / cards.size
    val currentWeekStart = today.startOfWeek(firstDayOfWeek)
    val weeklyNewCards = (VocabularyGrowthWeeks - 1 downTo 0).map { weeksAgo ->
        val weekStart = currentWeekStart.minusWeeks(weeksAgo.toLong())
        val weekEndExclusive = weekStart.plusWeeks(1)
        cards.count { card ->
            val date = Instant.ofEpochMilli(card.createdAt).atZone(zone).toLocalDate()
            !date.isBefore(weekStart) && date.isBefore(weekEndExclusive)
        }
    }
    return VocabularyGrowth(masteredFraction = masteredFraction, weeklyNewCards = weeklyNewCards)
}

/** The first day of the week containing this date, for weeks that begin on [firstDayOfWeek]. */
private fun LocalDate.startOfWeek(firstDayOfWeek: DayOfWeek): LocalDate =
    minusDays(((dayOfWeek.value - firstDayOfWeek.value + 7) % 7).toLong())

private const val TopLookedUpWordsLimit = 8

/** Sessions shorter than this are noise (an accidental open) and are dropped from the count/highest stat. */
private const val MinCountedSessionSeconds = 60L

/** Widest the reading-activity contribution grid ever grows, in weeks (~1 year). */
const val ActivityGridWeeks = 53

/** Narrowest the grid ever shrinks to, even for a library with only a day or two of history. */
private const val MinActivityGridWeeks = 4

/** Trailing window used to estimate current reading pace. */
private const val PaceWindowDays = 7

/** Caps an absurd extrapolation (e.g. one lucky session skewing a near-zero recent pace) from
 *  showing something like "in 4000 days" instead of just not showing a pace at all. */
private const val MaxEstimatedDays = 365

private const val MaxGenreStats = 6

const val VocabularyGrowthWeeks = 8
