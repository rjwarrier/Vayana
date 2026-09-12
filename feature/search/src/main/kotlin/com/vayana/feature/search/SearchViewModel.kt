package com.vayana.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.Book
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.filesystem.StorageRoots
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class GlobalSearchUiState(
    val query: String = "",
    val books: List<BookSearchResult> = emptyList(),
    val annotations: List<AnnotationSearchResult> = emptyList(),
    val totalCandidates: Int = 0,
) {
    val hasQuery: Boolean get() = query.isNotBlank()
    val hasMatches: Boolean get() = books.isNotEmpty() || annotations.isNotEmpty()
}

data class BookSearchResult(
    val book: Book,
    val matchedFields: List<SearchMatchedField>,
)

data class AnnotationSearchResult(
    val annotation: Annotation,
    val book: Book,
    val matchedFields: List<SearchMatchedField>,
)

enum class SearchMatchedField {
    TITLE,
    AUTHOR,
    SERIES,
    TAGS,
    DESCRIPTION,
    HIGHLIGHT,
    NOTE,
    CHAPTER,
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    bookRepository: BookRepository,
    annotationRepository: AnnotationRepository,
    private val storageRoots: StorageRoots,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val books = bookRepository.observeAll()
        .map { rawBooks -> rawBooks.map { it.withAbsolutePaths() } }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<GlobalSearchUiState> = combine(
        query,
        books,
        annotationRepository.observeAll(),
    ) { query, books, annotations ->
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            GlobalSearchUiState(query = query, totalCandidates = books.size + annotations.size)
        } else {
            val booksById = books.associateBy { it.id }
            GlobalSearchUiState(
                query = query,
                books = books.mapNotNull { book -> book.searchMatch(trimmed)?.let { BookSearchResult(book, it) } }
                    .sortedWith(compareByDescending<BookSearchResult> { it.book.lastReadAt ?: it.book.updatedAt }.thenBy { it.book.title.lowercase() })
                    .take(MaxBookResults),
                annotations = annotations.mapNotNull { annotation ->
                    val book = booksById[annotation.bookId] ?: return@mapNotNull null
                    annotation.searchMatch(trimmed)?.let { AnnotationSearchResult(annotation, book, it) }
                }
                    .sortedByDescending { it.annotation.updatedAt }
                    .take(MaxAnnotationResults),
                totalCandidates = books.size + annotations.size,
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalSearchUiState())

    fun updateQuery(value: String) {
        query.update { value }
    }

    private fun Book.withAbsolutePaths(): Book = copy(
        coverPath = coverPath?.let { storageRoots.resolve(it).absolutePath },
        filePath = filePath.takeIf { it.isNotBlank() }?.let { storageRoots.resolve(it).absolutePath }.orEmpty(),
    )
}

private fun Book.searchMatch(query: String): List<SearchMatchedField>? = buildList {
    if (title.matchesSearch(query)) add(SearchMatchedField.TITLE)
    if (author.matchesSearch(query)) add(SearchMatchedField.AUTHOR)
    if (series.matchesSearch(query) || seriesNumber.matchesSearch(query)) add(SearchMatchedField.SERIES)
    if (tagsCsv.matchesSearch(query)) add(SearchMatchedField.TAGS)
    if (description.matchesSearch(query)) add(SearchMatchedField.DESCRIPTION)
}.takeIf { it.isNotEmpty() }

private fun Annotation.searchMatch(query: String): List<SearchMatchedField>? = buildList {
    if (selectedText.matchesSearch(query)) add(SearchMatchedField.HIGHLIGHT)
    if (readerNote.matchesSearch(query)) add(SearchMatchedField.NOTE)
    if (chapterTitle.matchesSearch(query)) add(SearchMatchedField.CHAPTER)
}.takeIf { it.isNotEmpty() }

private fun String?.matchesSearch(query: String): Boolean =
    !isNullOrBlank() && contains(query, ignoreCase = true)

private const val MaxBookResults = 30
private const val MaxAnnotationResults = 80
