package com.vayana.feature.notes

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.common.HighlightTags
import com.vayana.core.common.KindleBookClippings
import com.vayana.core.common.KindleClippingsParser
import com.vayana.core.common.quoteMatchKey
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.model.Book
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.filesystem.ResolvedBooks
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BookNotesItem(
    val book: Book,
    val annotations: List<Annotation>,
)

data class NotesUiState(
    val booksWithNotes: List<BookNotesItem> = emptyList(),
    val allAnnotations: List<Annotation> = emptyList(),
    /** Every #tag used in a note, most used first. */
    val tags: List<String> = emptyList(),
)

internal data class NotesAnnotationIndex(
    val annotations: List<Annotation>,
    val byBook: Map<Long, List<Annotation>>,
    val latestByBook: Map<Long, Long>,
    val tags: List<String>,
)

internal fun indexNotesAnnotations(annotations: List<Annotation>): NotesAnnotationIndex {
    val byBook = HashMap<Long, MutableList<Annotation>>()
    val latestByBook = HashMap<Long, Long>()
    val tagCounts = HashMap<String, Int>()
    annotations.forEach { annotation ->
        byBook.getOrPut(annotation.bookId) { mutableListOf() }.add(annotation)
        latestByBook[annotation.bookId] = maxOf(latestByBook[annotation.bookId] ?: Long.MIN_VALUE, annotation.updatedAt)
        HighlightTags.parse(annotation.readerNote).forEach { tag ->
            tagCounts[tag] = (tagCounts[tag] ?: 0) + 1
        }
    }
    return NotesAnnotationIndex(
        annotations = annotations,
        byBook = byBook,
        latestByBook = latestByBook,
        tags = tagCounts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key },
    )
}

/** Outcome of a Kindle clippings import; all zero means the file had no clippings. */
data class KindleImportResult(val added: Int, val duplicates: Int, val unmatchedBooks: Int)

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val annotationRepository: AnnotationRepository,
    private val bookRepository: BookRepository,
    resolvedBooks: ResolvedBooks,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val annotationIndex = annotationRepository.observeAll()
        .map(::indexNotesAnnotations)
        .flowOn(dispatchers.default)

    val uiState: StateFlow<NotesUiState> = combine(
        resolvedBooks.all,
        annotationIndex,
    ) { books, index ->
        val booksWithNotes = books.mapNotNull { book ->
            val bookAnnotations = index.byBook[book.id]
            if (!bookAnnotations.isNullOrEmpty()) {
                BookNotesItem(book = book, annotations = bookAnnotations)
            } else {
                null
            }
        }.sortedByDescending { item ->
            index.latestByBook[item.book.id] ?: 0L
        }

        NotesUiState(
            booksWithNotes = booksWithNotes,
            allAnnotations = index.annotations,
            tags = index.tags,
        )
    }
        // Book progress updates only rebuild the book join; annotation grouping and tag parsing run on annotation changes.
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    private val _kindleImportResult = MutableStateFlow<KindleImportResult?>(null)
    val kindleImportResult: StateFlow<KindleImportResult?> = _kindleImportResult

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

    /**
     * Adds the highlights and notes of a Kindle "My Clippings.txt" to the library books they came from, matched by
     * title. The reader places each highlight by finding its text; ones already in a book are skipped.
     */
    fun importKindleClippings(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _kindleImportResult.value = withContext(dispatchers.io) {
                val text = runCatchingCancellable {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull().orEmpty()
                val clippings = KindleClippingsParser.parse(text)
                if (clippings.isEmpty()) return@withContext KindleImportResult(0, 0, 0)
                val booksByTitle = bookRepository.observeAll().first().groupBy { titleKey(it.title) }
                val existingByBook = annotationRepository.observeAll().first().groupBy { it.bookId }
                var duplicates = 0
                var unmatched = 0
                val now = System.currentTimeMillis()
                val fresh = clippings.flatMap { bookClippings ->
                    val book = booksByTitle[titleKey(bookClippings.title)]?.singleOrNull()
                    if (book == null) {
                        unmatched++
                        return@flatMap emptyList()
                    }
                    val known = existingByBook[book.id].orEmpty()
                        .mapTo(HashSet()) { quoteMatchKey(it.selectedText.ifBlank { it.readerNote.orEmpty() }) }
                    bookClippings.toAnnotations(book.id, now).filter { annotation ->
                        val key = quoteMatchKey(annotation.selectedText.ifBlank { annotation.readerNote.orEmpty() })
                        (key.isNotEmpty() && known.add(key)).also { added -> if (!added) duplicates++ }
                    }
                }
                annotationRepository.createAll(fresh)
                KindleImportResult(added = fresh.size, duplicates = duplicates, unmatchedBooks = unmatched)
            }
        }
    }

    fun consumeKindleImportResult() {
        _kindleImportResult.value = null
    }
}

private fun KindleBookClippings.toAnnotations(bookId: Long, now: Long): List<Annotation> =
    highlights.map { highlight ->
        kindleAnnotation(bookId, AnnotationType.HIGHLIGHT, highlight.text, highlight.note, now)
    } + looseNotes.map { note -> kindleAnnotation(bookId, AnnotationType.NOTE, "", note, now) }

private fun kindleAnnotation(bookId: Long, type: AnnotationType, text: String, note: String?, now: Long) = Annotation(
    id = 0,
    bookId = bookId,
    type = type,
    colorKey = KindleHighlightColor,
    // "text:" locators are placed by the reader by searching for the highlight's text.
    locator = "text:kindle:${UUID.randomUUID()}",
    chapterTitle = null,
    chapterHref = null,
    selectedText = text,
    readerNote = note,
    createdAt = now,
    updatedAt = now,
)

/** Kindle titles often carry a subtitle or edition in brackets that the library copy doesn't. */
private fun titleKey(title: String): String =
    quoteMatchKey(title.substringBefore(':').substringBefore(" (").substringBefore(" ["))

private const val KindleHighlightColor = "yellow"
