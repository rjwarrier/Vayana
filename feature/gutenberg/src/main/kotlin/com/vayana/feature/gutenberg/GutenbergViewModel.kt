package com.vayana.feature.gutenberg

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.common.IncomingBookFiles
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.database.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GutenbergListState(
    val query: GutenbergQuery = GutenbergQuery(),
    val books: List<GutenbergBookSummary> = emptyList(),
    val nextUrl: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val failed: Boolean = false,
    /** A saved copy is showing while the list is fetched again. */
    val refreshing: Boolean = false,
    /** The saved copy is all there is: gutenberg.org couldn't be reached. */
    val offline: Boolean = false,
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
    bookRepository: BookRepository,
) : ViewModel() {
    /** The library, to mark the Gutenberg books already in it. */
    val library: StateFlow<LibraryIndex> = bookRepository.observeAll()
        .map { books -> LibraryIndex(books.map { LibraryBook(it.id, it.title, it.author) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), LibraryIndex.Empty)

    private val _list = MutableStateFlow(GutenbergListState())
    val list: StateFlow<GutenbergListState> = _list

    private val _book = MutableStateFlow<GutenbergBookState>(GutenbergBookState.Hidden)
    val book: StateFlow<GutenbergBookState> = _book

    /** A downloaded book, handed to the library's importer: the screen goes back to the library to show it. */
    private val _imported = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val imported: SharedFlow<Unit> = _imported

    private var listJob: Job? = null
    private var moreJob: Job? = null
    private var bookJob: Job? = null

    init {
        show(GutenbergQuery())
    }

    fun search(text: String) = show(_list.value.query.copy(text = text.trim()))

    fun showList(list: GutenbergList) = show(_list.value.query.copy(list = list))

    /** A topic, or null for every topic. */
    fun showTopic(topic: GutenbergTopic?) = show(_list.value.query.copy(topic = topic))

    /** A language code, or null for every language. */
    fun showLanguage(language: String?) = show(_list.value.query.copy(language = language))

    /** The author's books, most read first, in the language already chosen. */
    fun showAuthor(author: String) {
        close()
        show(GutenbergQuery(text = author, language = _list.value.query.language))
    }

    /**
     * Shows the saved copy of the list at once, if there is one, then the fresh one. Random is always fetched new:
     * the same "random" books again would defeat it.
     */
    private fun show(query: GutenbergQuery) {
        listJob?.cancel()
        moreJob?.cancel()
        _list.value = GutenbergListState(query = query, loading = true)
        val url = GutenbergFeeds.listUrl(query)
        listJob = viewModelScope.launch {
            val saved = if (query.list == GutenbergList.RANDOM) null else client.savedListing(url)
            if (saved != null) {
                val fresh = client.isFresh(url)
                _list.update { it.copy(books = saved.books, nextUrl = saved.nextUrl, loading = false, refreshing = !fresh) }
                if (fresh) return@launch
            }
            runCatchingCancellable { client.listing(url) }
                .onSuccess { listing ->
                    _list.update { current ->
                        // Pages the reader already scrolled into stay; only an untouched first page is swapped.
                        if (saved == null || current.books.size <= saved.books.size) {
                            current.copy(books = listing.books, nextUrl = listing.nextUrl, loading = false, refreshing = false)
                        } else {
                            current.copy(refreshing = false)
                        }
                    }
                }
                .onFailure { error ->
                    Log.w(Tag, "Couldn't load $url", error)
                    _list.update {
                        if (saved != null) it.copy(refreshing = false, offline = true) else it.copy(loading = false, failed = true)
                    }
                }
        }
    }

    fun retry() = show(_list.value.query)

    /** The next page, when the list is scrolled near its end. */
    fun loadMore() {
        val state = _list.value
        val next = state.nextUrl ?: return
        if (state.loading || state.loadingMore) return
        _list.update { it.copy(loadingMore = true) }
        // Its own job: a refresh of the first page still running must not be cancelled by scrolling.
        moreJob = viewModelScope.launch {
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
        const val StopTimeoutMillis = 5_000L
    }
}
