package com.vayana.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.Book
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.search.hasWordStartingWith
import com.vayana.core.database.search.searchTokens
import com.vayana.core.filesystem.ResolvedBooks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.vayana.core.datastore.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class GlobalSearchUiState(
    val query: String = "",
    val books: List<BookSearchResult> = emptyList(),
    val annotations: List<AnnotationSearchResult> = emptyList(),
    /** The results shown are for an older query; the debounced search for [query] hasn't landed yet. */
    val isSearching: Boolean = false,
    val filter: SearchFilter = SearchFilter.ALL,
    /** Words of the query the results were found for, to highlight in them. */
    val tokens: List<String> = emptyList(),
    val recentSearches: List<String> = emptyList(),
) {
    val hasQuery: Boolean get() = query.isNotBlank()
    val shownBooks: List<BookSearchResult> get() = if (filter == SearchFilter.NOTES) emptyList() else books
    val shownAnnotations: List<AnnotationSearchResult> get() = if (filter == SearchFilter.BOOKS) emptyList() else annotations
    val hasMatches: Boolean get() = shownBooks.isNotEmpty() || shownAnnotations.isNotEmpty()
}

enum class SearchFilter { ALL, BOOKS, NOTES }

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

internal data class SearchResults(
    val query: String = "",
    val books: List<BookSearchResult> = emptyList(),
    val annotations: List<AnnotationSearchResult> = emptyList(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    bookRepository: BookRepository,
    annotationRepository: AnnotationRepository,
    resolvedBooks: ResolvedBooks,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(SearchFilter.ALL)

    // Matching runs in SQLite FTS; only the capped result rows are joined with books and checked here.
    private val results = query
        .map { it.trim() }
        .debounce { text -> if (text.isEmpty()) 0L else SearchDebounceMillis }
        .distinctUntilChanged()
        .flatMapLatest { text ->
            if (searchTokens(text).isEmpty()) {
                flowOf(SearchResults(query = text))
            } else {
                combine(
                    bookRepository.observeSearchIds(text, MaxBookResults),
                    annotationRepository.observeSearch(text, MaxAnnotationResults),
                    resolvedBooks.all,
                ) { bookIds, annotations, books -> searchResults(text, bookIds, annotations, books) }
            }
        }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<GlobalSearchUiState> = combine(
        query,
        results,
        filter,
        settingsRepository.recentSearches,
    ) { query, results, filter, recentSearches ->
        GlobalSearchUiState(
            query = query,
            books = results.books,
            annotations = results.annotations,
            isSearching = query.trim() != results.query,
            filter = filter,
            tokens = searchTokens(results.query),
            recentSearches = recentSearches,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalSearchUiState())

    fun updateQuery(value: String) {
        query.update { value }
    }

    fun updateFilter(value: SearchFilter) {
        filter.update { value }
    }

    /** Remembers the current query; called when the user submits it or opens one of its results. */
    fun recordSearch() {
        val text = query.value.trim()
        if (searchTokens(text).isEmpty()) return
        viewModelScope.launch {
            settingsRepository.updateRecentSearches(settingsRepository.recentSearches.first().withRecentSearch(text))
        }
    }

    fun useRecentSearch(value: String) {
        query.update { value }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { settingsRepository.updateRecentSearches(emptyList()) }
    }
}

internal fun searchResults(
    text: String,
    bookIds: List<Long>,
    annotations: List<Annotation>,
    books: List<Book>,
): SearchResults {
    val tokens = searchTokens(text)
    val booksById = books.associateBy { it.id }
    return SearchResults(
        query = text,
        books = bookIds.mapNotNull { id ->
            booksById[id]?.let { book -> BookSearchResult(book, book.matchedFields(tokens)) }
        },
        annotations = annotations.mapNotNull { annotation ->
            booksById[annotation.bookId]?.let { book -> AnnotationSearchResult(annotation, book, annotation.matchedFields(tokens)) }
        },
    )
}

private fun Book.matchedFields(tokens: List<String>): List<SearchMatchedField> = buildList {
    if (title.matchesAny(tokens)) add(SearchMatchedField.TITLE)
    if (author.matchesAny(tokens)) add(SearchMatchedField.AUTHOR)
    if (series.matchesAny(tokens) || seriesNumber.matchesAny(tokens)) add(SearchMatchedField.SERIES)
    if (tagsCsv.matchesAny(tokens)) add(SearchMatchedField.TAGS)
    if (description.matchesAny(tokens)) add(SearchMatchedField.DESCRIPTION)
}

private fun Annotation.matchedFields(tokens: List<String>): List<SearchMatchedField> = buildList {
    if (selectedText.matchesAny(tokens)) add(SearchMatchedField.HIGHLIGHT)
    if (readerNote.matchesAny(tokens)) add(SearchMatchedField.NOTE)
    if (chapterTitle.matchesAny(tokens)) add(SearchMatchedField.CHAPTER)
}

private fun String?.matchesAny(tokens: List<String>): Boolean = tokens.any { hasWordStartingWith(it) }

/** [query] moved to the front, without case-insensitive repeats, capped. */
internal fun List<String>.withRecentSearch(query: String): List<String> =
    (listOf(query) + filterNot { it.equals(query, ignoreCase = true) }).take(MaxRecentSearches)

private const val SearchDebounceMillis = 150L
private const val MaxRecentSearches = 8
private const val MaxBookResults = 30
private const val MaxAnnotationResults = 80
