package com.vayana.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.Book
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookNotesItem(
    val book: Book,
    val annotations: List<Annotation>,
)

data class NotesUiState(
    val booksWithNotes: List<BookNotesItem> = emptyList(),
    val allAnnotations: List<Annotation> = emptyList(),
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val annotationRepository: AnnotationRepository,
    private val bookRepository: BookRepository,
) : ViewModel() {
    val uiState: StateFlow<NotesUiState> = combine(
        bookRepository.observeAll(),
        annotationRepository.observeAll(),
    ) { books, annotations ->
        val annotationsByBook = annotations.groupBy { it.bookId }
        val booksWithNotes = books.mapNotNull { book ->
            val bookAnnotations = annotationsByBook[book.id]
            if (!bookAnnotations.isNullOrEmpty()) {
                BookNotesItem(book = book, annotations = bookAnnotations)
            } else {
                null
            }
        }.sortedByDescending { item ->
            item.annotations.maxOfOrNull { it.updatedAt } ?: 0L
        }

        NotesUiState(
            booksWithNotes = booksWithNotes,
            allAnnotations = annotations,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun updateNote(annotation: Annotation, readerNote: String) {
        viewModelScope.launch {
            annotationRepository.update(annotation.copy(readerNote = readerNote.takeIf { it.isNotBlank() }))
        }
    }

    fun deleteAnnotation(annotationId: Long) {
        viewModelScope.launch {
            annotationRepository.delete(annotationId)
        }
    }
}
