package com.vayana.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.Book
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.search.hasWordStartingWithAny
import com.vayana.core.database.search.searchTokens
import com.vayana.core.filesystem.ResolvedBooks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
import kotlinx.coroutines.launch
import com.vayana.core.common.DispatcherProvider

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
    val totalMatches: Int get() = books.size + annotations.size
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
    val tokens: List<String> = emptyList(),
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
    dispatchers: DispatcherProvider,
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
        .flowOn(dispatchers.default)

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
            tokens = results.tokens,
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
        // Stored one per line, so pasted line breaks collapse like any other whitespace.
        val text = query.value.trim().replace(WhitespaceRun, " ")
        if (searchTokens(text).isEmpty()) return
        viewModelScope.launch { settingsRepository.updateRecentSearches { it.withRecentSearch(text) } }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { settingsRepository.updateRecentSearches { emptyList() } }
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
        tokens = tokens,
        books = bookIds.mapNotNull { id ->
            booksById[id]?.let { book -> BookSearchResult(book, book.matchedFields(tokens)) }
        },
        annotations = annotations.mapNotNull { annotation ->
            booksById[annotation.bookId]?.let { book -> AnnotationSearchResult(annotation, book, annotation.matchedFields(tokens)) }
        },
    )
}

private fun Book.matchedFields(tokens: List<String>): List<SearchMatchedField> = buildList {
    if (title.hasWordStartingWithAny(tokens)) add(SearchMatchedField.TITLE)
    if (author.hasWordStartingWithAny(tokens)) add(SearchMatchedField.AUTHOR)
    if (series.hasWordStartingWithAny(tokens) || seriesNumber.hasWordStartingWithAny(tokens)) add(SearchMatchedField.SERIES)
    if (tagsCsv.hasWordStartingWithAny(tokens)) add(SearchMatchedField.TAGS)
    if (description.hasWordStartingWithAny(tokens)) add(SearchMatchedField.DESCRIPTION)
}

private fun Annotation.matchedFields(tokens: List<String>): List<SearchMatchedField> = buildList {
    if (selectedText.hasWordStartingWithAny(tokens)) add(SearchMatchedField.HIGHLIGHT)
    if (readerNote.hasWordStartingWithAny(tokens)) add(SearchMatchedField.NOTE)
    if (chapterTitle.hasWordStartingWithAny(tokens)) add(SearchMatchedField.CHAPTER)
}

/** [query] moved to the front, without case-insensitive repeats, capped. */
internal fun List<String>.withRecentSearch(query: String): List<String> =
    (listOf(query) + filterNot { it.equals(query, ignoreCase = true) }).take(MaxRecentSearches)

private val WhitespaceRun = Regex("""\s+""")

private const val SearchDebounceMillis = 150L
private const val MaxRecentSearches = 8
private const val MaxBookResults = 30
private const val MaxAnnotationResults = 80
