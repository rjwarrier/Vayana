package com.vayana.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.WordLookupStat
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.WordLookupStatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatisticsSummary(
    val totalBooks: Int = 0,
    val readingBooks: Int = 0,
    val finishedBooks: Int = 0,
    val averageProgressPercent: Int = 0,
    val totalAnnotations: Int = 0,
    val notesWithText: Int = 0,
    val highlightToRevisit: Annotation? = null,
    val topLookedUpWords: List<WordLookupStat> = emptyList(),
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    bookRepository: BookRepository,
    annotationRepository: AnnotationRepository,
    wordLookupStatRepository: WordLookupStatRepository,
) : ViewModel() {
    val summary: StateFlow<StatisticsSummary> = combine(
        bookRepository.observeAll(),
        annotationRepository.observeAll(),
        wordLookupStatRepository.observeTop(TopLookedUpWordsLimit),
    ) { books, annotations, topWords ->
        books.toSummary(annotations, topWords)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsSummary())
}

private fun List<Book>.toSummary(annotations: List<Annotation>, topWords: List<WordLookupStat>): StatisticsSummary {
    val average = if (isEmpty()) 0 else (sumOf { (it.readingPercent * 100).toDouble() } / size).toInt()
    return StatisticsSummary(
        totalBooks = size,
        readingBooks = count { it.readingPercent > 0f && it.readingPercent < FinishedThreshold },
        finishedBooks = count { it.readingPercent >= FinishedThreshold },
        averageProgressPercent = average.coerceIn(0, 100),
        totalAnnotations = annotations.size,
        notesWithText = annotations.count { it.readerNote?.isNotBlank() == true },
        highlightToRevisit = annotations
            .filter { it.selectedText.isNotBlank() }
            .sortedBy { it.createdAt }
            .firstOrNull(),
        topLookedUpWords = topWords,
    )
}

private const val TopLookedUpWordsLimit = 8

private const val FinishedThreshold = 0.98f
