package com.vayana.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.HighlightReviewRepository
import com.vayana.core.database.repository.ReviewGrade
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HighlightReviewItem(
    val annotation: Annotation,
    val bookTitle: String,
    val bookAuthor: String?,
    val bookCoverPath: String?,
    val bookSeries: String?,
    val bookSeriesNumber: String?,
    /** Physical books have no text to open. */
    val canOpen: Boolean,
)

/**
 * One sitting of review. [scheduled] sessions record how each highlight went; a practice session (today's set, offered
 * when nothing is due) only shows them. [reviewableCount] tells "no highlights yet" apart from "all caught up".
 */
data class HighlightReviewSession(
    val items: List<HighlightReviewItem>,
    val scheduled: Boolean,
    val reviewableCount: Int,
)

@HiltViewModel
class HighlightReviewViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val highlightReviewRepository: HighlightReviewRepository,
) : ViewModel() {

    /** Null until loaded. Taken once, so the set doesn't shift while it's being reviewed. */
    private val _session = MutableStateFlow<HighlightReviewSession?>(null)
    val session: StateFlow<HighlightReviewSession?> = _session

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index

    init {
        // Hold one bounded, consistent set for the sitting; grading never reshuffles it mid-session.
        viewModelScope.launch(Dispatchers.Default) {
            val due = highlightReviewRepository.session(System.currentTimeMillis(), ReviewSessionSize)
            _session.value = HighlightReviewSession(
                items = due.annotations.toItems(),
                scheduled = true,
                reviewableCount = due.reviewableCount,
            )
            runCatchingCancellable { highlightReviewRepository.deleteOrphans() }
        }
    }

    /** Nothing was due: go through today's fixed set anyway, without touching any schedule. */
    fun practiceAnyway() {
        _index.value = 0
        viewModelScope.launch(Dispatchers.Default) {
            val items = highlightReviewRepository.practice(LocalDate.now().toEpochDay(), DailyHighlightCount).toItems()
            _session.update { current -> HighlightReviewSession(
                items = items,
                scheduled = false,
                reviewableCount = current?.reviewableCount ?: 0,
            ) }
        }
    }

    /** Records how the shown highlight went (a scheduled session only) and moves on. */
    fun grade(grade: ReviewGrade) {
        val current = _session.value
        val item = current?.items?.getOrNull(_index.value)
        if (current?.scheduled == true && item != null) {
            viewModelScope.launch {
                runCatchingCancellable { highlightReviewRepository.grade(item.annotation.syncId, grade) }
            }
        }
        next()
    }

    fun next() {
        _index.update { it + 1 }
    }

    private suspend fun List<Annotation>.toItems(): List<HighlightReviewItem> {
        val books = map(Annotation::bookId).distinct().associateWith { bookRepository.getById(it) }
        return mapNotNull { annotation ->
        val book = books[annotation.bookId] ?: return@mapNotNull null
        HighlightReviewItem(
            annotation = annotation,
            bookTitle = book.title,
            bookAuthor = book.author,
            bookCoverPath = book.coverPath,
            bookSeries = book.series,
            bookSeriesNumber = book.seriesNumber,
            canOpen = book.format != BookFormat.PHYSICAL,
        )
        }
    }
}
