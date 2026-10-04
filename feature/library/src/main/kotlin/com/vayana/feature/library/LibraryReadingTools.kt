package com.vayana.feature.library

import com.vayana.core.database.model.Book
import com.vayana.core.datastore.settings.SmartShelf
import com.vayana.core.datastore.settings.SmartShelfStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal data class SmartShelfItem(val shelf: SmartShelf, val books: List<Book>)

/** Subscribe to notes and time only when saved rules need them. The caller supplies title-sorted books. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeSmartShelfItems(
    books: Flow<List<Book>>,
    definitions: Flow<List<SmartShelf>>,
    notes: () -> Flow<Set<Long>>,
    threshold: Flow<Float>,
    clock: Flow<Long>,
): Flow<List<SmartShelfItem>> = definitions.flatMapLatest { rules ->
    if (rules.isEmpty()) return@flatMapLatest flowOf(emptyList())
    val notedBooks = if (rules.any { it.withNotes }) notes() else flowOf(emptySet())
    combine(books, notedBooks, threshold) { library, noted, finished ->
        Triple(library, noted, finished)
    }.flatMapLatest { (library, noted, finished) ->
        val staticItems = rules.filter { it.dormantDays <= 0 }.associate { shelf ->
            shelf.id to SmartShelfItem(shelf, library.filter { shelf.matches(it, noted, finished, 0L) })
        }
        fun select(time: Long) = rules.map { shelf ->
            staticItems[shelf.id] ?: SmartShelfItem(shelf, library.filter { shelf.matches(it, noted, finished, time) })
        }
        if (rules.any { it.dormantDays > 0 }) clock.map(::select) else flowOf(select(0L))
    }
}.distinctUntilChanged()

internal fun SmartShelf.matches(book: Book, booksWithNotes: Set<Long>, finishedThreshold: Float, now: Long): Boolean {
    val progress = book.readingPercent.coerceIn(0f, 1f)
    val finished = book.finishedReadingAt != null || progress >= finishedThreshold
    val started = book.hasStartedReading()
    val statusMatches = when (status) {
        SmartShelfStatus.ALL -> true
        SmartShelfStatus.UNREAD -> book.readingDisposition == "ACTIVE" && !started && !finished
        SmartShelfStatus.READING -> book.readingDisposition == "ACTIVE" && started && !finished
        SmartShelfStatus.FINISHED -> finished
        SmartShelfStatus.PAUSED -> book.readingDisposition == "PAUSED"
        SmartShelfStatus.DNF -> book.readingDisposition == "DNF"
    }
    if (!statusMatches || (withNotes && book.id !in booksWithNotes)) return false
    if (author.isNotBlank() && !book.author.orEmpty().contains(author.trim(), ignoreCase = true)) return false
    if (tag.isNotBlank() && book.tagsCsv.orEmpty().split(',').none { it.trim().equals(tag.trim(), ignoreCase = true) }) return false
    if (query.isNotBlank() && listOfNotNull(book.title, book.author, book.description, book.series, book.tagsCsv)
            .none { it.contains(query.trim(), ignoreCase = true) }) return false
    if (dormantDays > 0) {
        // "Untouched" shelves contain started books, not books that have never been opened.
        val lastRead = book.lastReadAt ?: return false
        if (!started || now - lastRead < dormantDays * 86_400_000L) return false
    }
    return true
}

internal data class FinishByPlan(val days: Int, val amount: Int, val unit: PlanUnit)
internal enum class PlanUnit { PAGES, MINUTES, PERCENT, FINISHED, OVERDUE }

internal fun finishByPlan(book: Book, target: LocalDate, today: LocalDate = LocalDate.now()): FinishByPlan {
    val progress = book.readingPercent.coerceIn(0f, 1f)
    if (progress >= 1f || book.finishedReadingAt != null) return FinishByPlan(0, 0, PlanUnit.FINISHED)
    if (target.isBefore(today)) return FinishByPlan(0, 0, PlanUnit.OVERDUE)
    val days = (ChronoUnit.DAYS.between(today, target) + 1).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
    val pages = book.pageCount?.takeIf { it > 0 }
    if (pages != null) return FinishByPlan(days, ceil(pages * (1.0 - progress) / days).toInt().coerceAtLeast(1), PlanUnit.PAGES)
    if (progress >= .01f && book.totalReadingSeconds >= 60L) {
        val remainingMinutes = book.totalReadingSeconds.toDouble() / 60 * (1 - progress) / progress
        return FinishByPlan(days, ceil(remainingMinutes / days).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt().coerceAtLeast(1), PlanUnit.MINUTES)
    }
    return FinishByPlan(days, ceil(100.0 * (1 - progress) / days).toInt().coerceAtLeast(1), PlanUnit.PERCENT)
}

internal sealed interface BulkLibraryAction {
    data class AddToShelf(val shelfId: Long) : BulkLibraryAction
    data class AddTags(val tags: String) : BulkLibraryAction
    data object Download : BulkLibraryAction
}
internal data class BulkLibraryResult(val completed: Int, val failed: Int, val skipped: Int)
