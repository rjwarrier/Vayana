package com.vayana.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HighlightReviewItem(
    val annotation: Annotation,
    val bookTitle: String,
    val bookAuthor: String?,
    /** Physical books have no text to open. */
    val canOpen: Boolean,
)

@HiltViewModel
class HighlightReviewViewModel @Inject constructor(
    private val annotationRepository: AnnotationRepository,
    private val bookRepository: BookRepository,
) : ViewModel() {

    /** Null until loaded. Taken once, so the set doesn't shift while it's being reviewed. */
    private val _items = MutableStateFlow<List<HighlightReviewItem>?>(null)
    val items: StateFlow<List<HighlightReviewItem>?> = _items

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index

    init {
        viewModelScope.launch {
            val books = bookRepository.observeAll().first().associateBy { it.id }
            val annotations = annotationRepository.observeAll().first().filter { it.bookId in books }
            _items.value = dailyHighlights(annotations, LocalDate.now()).mapNotNull { annotation ->
                val book = books[annotation.bookId] ?: return@mapNotNull null
                HighlightReviewItem(
                    annotation = annotation,
                    bookTitle = book.title,
                    bookAuthor = book.author,
                    canOpen = book.format != BookFormat.PHYSICAL,
                )
            }
        }
    }

    fun next() {
        _index.value += 1
    }
}
