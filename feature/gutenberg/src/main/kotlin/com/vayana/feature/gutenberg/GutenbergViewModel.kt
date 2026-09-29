package com.vayana.feature.gutenberg

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.common.IncomingBookFiles
import com.vayana.core.common.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the book list shows: one of Gutenberg's lists, or search results. */
sealed interface GutenbergSource {
    data class Browse(val list: GutenbergList) : GutenbergSource
    data class Search(val query: String) : GutenbergSource
}

data class GutenbergListState(
    val source: GutenbergSource = GutenbergSource.Browse(GutenbergList.POPULAR),
    val books: List<GutenbergBookSummary> = emptyList(),
    val nextUrl: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val failed: Boolean = false,
)

sealed interface GutenbergBookState {
    data object Hidden : GutenbergBookState
    data class Loading(val summary: GutenbergBookSummary) : GutenbergBookState
    data class Loaded(val book: GutenbergBook, val download: GutenbergDownload? = null) : GutenbergBookState
    data class Failed(val summary: GutenbergBookSummary) : GutenbergBookState
}

/** An edition being fetched ([progress] null until its size is known), or one that couldn't be. */
data class GutenbergDownload(val kind: GutenbergEditionKind, val progress: Float?, val failed: Boolean = false)

@HiltViewModel
class GutenbergViewModel @Inject constructor(
    private val client: GutenbergClient,
    private val incomingBookFiles: IncomingBookFiles,
) : ViewModel() {
    private val _list = MutableStateFlow(GutenbergListState())
    val list: StateFlow<GutenbergListState> = _list

    private val _book = MutableStateFlow<GutenbergBookState>(GutenbergBookState.Hidden)
    val book: StateFlow<GutenbergBookState> = _book

    /** A downloaded book, handed to the library's importer: the screen goes back to the library to show it. */
    private val _imported = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val imported: SharedFlow<Unit> = _imported

    private var listJob: Job? = null
    private var bookJob: Job? = null

    init {
        show(GutenbergSource.Browse(GutenbergList.POPULAR))
    }

    fun show(source: GutenbergSource) {
        if (source is GutenbergSource.Search && source.query.isBlank()) return
        listJob?.cancel()
        _list.value = GutenbergListState(source = source, loading = true)
        listJob = viewModelScope.launch {
            val url = when (source) {
                is GutenbergSource.Browse -> GutenbergFeeds.listUrl(source.list)
                is GutenbergSource.Search -> GutenbergFeeds.searchUrl(source.query)
            }
            runCatchingCancellable { client.listing(url) }
                .onSuccess { listing -> _list.update { it.copy(books = listing.books, nextUrl = listing.nextUrl, loading = false) } }
                .onFailure { error ->
                    Log.w(Tag, "Couldn't load $url", error)
                    _list.update { it.copy(loading = false, failed = true) }
                }
        }
    }

    fun retry() = show(_list.value.source)

    /** The next page, when the list is scrolled near its end. */
    fun loadMore() {
        val state = _list.value
        val next = state.nextUrl ?: return
        if (state.loading || state.loadingMore) return
        _list.update { it.copy(loadingMore = true) }
        listJob = viewModelScope.launch {
            runCatchingCancellable { client.listing(next) }
                .onSuccess { page ->
                    _list.update { current ->
                        current.copy(
                            books = (current.books + page.books).distinctBy { it.id },
                            nextUrl = page.nextUrl,
                            loadingMore = false,
                        )
                    }
                }
                // Scrolling back to the end tries again.
                .onFailure { _list.update { it.copy(loadingMore = false) } }
        }
    }

    fun open(summary: GutenbergBookSummary) {
        bookJob?.cancel()
        _book.value = GutenbergBookState.Loading(summary)
        bookJob = viewModelScope.launch {
            val book = runCatchingCancellable { client.book(summary.id) }
                .onFailure { Log.w(Tag, "Couldn't load book ${summary.id}", it) }
                .getOrNull()
            _book.value = if (book == null) GutenbergBookState.Failed(summary) else GutenbergBookState.Loaded(book)
        }
    }

    fun close() {
        bookJob?.cancel()
        _book.value = GutenbergBookState.Hidden
    }

    fun download(edition: GutenbergEdition) {
        val loaded = _book.value as? GutenbergBookState.Loaded ?: return
        if (loaded.download?.failed == false) return
        bookJob?.cancel()
        _book.value = loaded.copy(download = GutenbergDownload(edition.kind, progress = null))
        bookJob = viewModelScope.launch {
            runCatchingCancellable {
                client.download(loaded.book.id, edition) { progress ->
                    _book.update { state ->
                        if (state is GutenbergBookState.Loaded) state.copy(download = GutenbergDownload(edition.kind, progress)) else state
                    }
                }
            }.onSuccess { file ->
                // The same path as "Open with": the library imports it, skips a duplicate, reads its cover.
                incomingBookFiles.offer(listOf(Uri.fromFile(file)))
                _book.value = GutenbergBookState.Hidden
                _imported.tryEmit(Unit)
            }.onFailure { error ->
                Log.w(Tag, "Couldn't download ${edition.url}", error)
                _book.value = loaded.copy(download = GutenbergDownload(edition.kind, progress = null, failed = true))
            }
        }
    }

    fun cancelDownload() {
        val loaded = _book.value as? GutenbergBookState.Loaded ?: return
        bookJob?.cancel()
        _book.value = loaded.copy(download = null)
    }

    suspend fun cover(url: String) = client.cover(url)

    fun cachedCover(url: String) = client.cachedCover(url)

    private companion object {
        const val Tag = "Gutenberg"
    }
}
