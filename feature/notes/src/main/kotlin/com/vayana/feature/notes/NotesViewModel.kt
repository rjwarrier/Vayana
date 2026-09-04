package com.vayana.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.Book
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.filesystem.StorageRoots
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
    private val storageRoots: StorageRoots,
) : ViewModel() {
    val uiState: StateFlow<NotesUiState> = combine(
        bookRepository.observeAll(),
        annotationRepository.observeAll(),
    ) { rawBooks, annotations ->
        val books = rawBooks.map { book ->
            book.copy(
                coverPath = book.coverPath?.let { storageRoots.resolve(it).absolutePath },
                filePath = storageRoots.resolve(book.filePath).absolutePath,
            )
        }
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

    /** Immediate and durable - the row is filtered out of every query right away, so this is
     * safe even if the caller (a snackbar's coroutine, say) never gets to follow up. */
    fun softDeleteAnnotation(annotationId: Long) {
        viewModelScope.launch {
            annotationRepository.softDelete(annotationId)
        }
    }

    fun undoDeleteAnnotation(annotationId: Long) {
        viewModelScope.launch {
            annotationRepository.restore(annotationId)
        }
    }

    /** Best-effort cleanup after the undo window passes. If this never runs (app killed, etc.)
     * the annotation stays soft-deleted - invisible, which is all "deleted" needs to mean. */
    fun purgeAnnotation(annotationId: Long) {
        viewModelScope.launch {
            annotationRepository.purge(annotationId)
        }
    }
}
