package com.vayana.feature.opds

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.common.IncomingBookFiles
import com.vayana.core.common.LibraryBook
import com.vayana.core.common.LibraryIndex
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.common.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What checking an address found: a working catalogue, or why it is not one. */
sealed interface OpdsCheck {
    data class Ok(val url: String, val title: String) : OpdsCheck
    data class Failed(val failure: OpdsFailure) : OpdsCheck
}

@HiltViewModel
class OpdsCatalogsViewModel @Inject constructor(
    private val store: OpdsCatalogStore,
    private val client: OpdsClient,
    private val discovery: OpdsDiscovery,
) : ViewModel() {
    val catalogs: StateFlow<List<OpdsCatalog>> = store.catalogs

    /** Servers answering on the home network, as they are found. */
    fun discover() = discovery.scan()

    /** Asks the server before saving, so a typo or a wrong login is caught while the editor is still open. */
    suspend fun check(id: String?, url: String, username: String, password: String): OpdsCheck {
        val address = normalizedCatalogUrl(url) ?: return OpdsCheck.Failed(OpdsFailure.UNREACHABLE)
        val login = username.trim()
        val saved = id?.let(store::find)
        val candidate = OpdsCatalog(
            id = id ?: "check",
            name = "",
            url = address,
            username = login,
            password = if (login.isEmpty()) "" else password.ifEmpty { saved?.password.orEmpty() },
        )
        return try {
            val (working, feed) = client.check(candidate)
            OpdsCheck.Ok(working, feed.title)
        } catch (error: OpdsException) {
            OpdsCheck.Failed(error.failure)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            // A malformed address or an odd server answer is a failed check, never a crash.
            OpdsCheck.Failed(OpdsFailure.UNREACHABLE)
        }
    }

    /** Saves a catalogue from the editor. Returns false when the address isn't a web address. */
    fun save(id: String?, name: String, url: String, username: String, password: String): Boolean {
        val address = normalizedCatalogUrl(url) ?: return false
        client.forget()
        val existing = id?.let(store::find)
        val login = username.trim()
        store.save(
            OpdsCatalog(
                id = existing?.id ?: store.newId(),
                name = name.trim().ifEmpty { runCatching { java.net.URI(address).host }.getOrNull() ?: address },
                url = address,
                username = login,
                // Left blank while editing a catalogue that has a login: keep the saved password.
                password = if (login.isEmpty()) "" else password.ifEmpty { existing?.password.orEmpty() },
            ),
        )
        return true
    }

    fun delete(id: String) {
        client.forget()
        store.delete(id)
    }
}

/** One level of the catalogue the reader has walked into, kept so going back is instant. */
private data class OpdsLevel(
    val url: String,
    val title: String,
    val entries: List<OpdsEntry> = emptyList(),
    val nextUrl: String? = null,
    val search: OpdsSearch? = null,
    val loaded: Boolean = false,
)

data class OpdsBrowseState(
    val catalog: OpdsCatalog? = null,
    /** Identifies the folder on screen, so the list can keep a scroll position for each. */
    val levelKey: String = "",
    val title: String = "",
    val canGoUp: Boolean = false,
    val entries: List<OpdsEntry> = emptyList(),
    val hasNext: Boolean = false,
    val canSearch: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val failure: OpdsFailure? = null,
    val searchFailed: Boolean = false,
)

/** A book being fetched ([progress] null until its size is known), or one that couldn't be. */
data class OpdsDownload(val format: OpdsFormat, val progress: Float?, val failed: Boolean = false)

data class OpdsSelection(val entry: OpdsEntry, val download: OpdsDownload? = null)

@HiltViewModel
class OpdsBrowseViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val store: OpdsCatalogStore,
    private val client: OpdsClient,
    private val incomingBookFiles: IncomingBookFiles,
    bookRepository: BookRepository,
) : ViewModel() {
    /** The library, to mark the catalogue books already in it. */
    val library: StateFlow<LibraryIndex> = bookRepository.observeAll()
        .map { books -> books.map { LibraryBook(it.id, it.title, it.author) } }
        // Reading progress rewrites Book rows constantly; only identity matters here, so skip those emissions.
        .distinctUntilChanged()
        .map(::LibraryIndex)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), LibraryIndex.Empty)

    private val catalog: OpdsCatalog? = savedState.get<String>(CatalogIdKey)?.let(store::find)

    private val levels = ArrayDeque<OpdsLevel>()
    private val _state = MutableStateFlow(OpdsBrowseState(catalog = catalog, title = catalog?.name.orEmpty()))
    val state: StateFlow<OpdsBrowseState> = _state.asStateFlow()

    val grid: StateFlow<Boolean> = store.gridView

    fun setGrid(grid: Boolean) = store.setGridView(grid)

    private val _selection = MutableStateFlow<OpdsSelection?>(null)
    val selection: StateFlow<OpdsSelection?> = _selection.asStateFlow()

    private val _imported = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** A downloaded book, handed to the library's importer: the screen goes back to the library to show it. */
    val imported: SharedFlow<Unit> = _imported

    private var loadJob: Job? = null
    private var downloadJob: Job? = null

    init {
        if (catalog != null) open(catalog.url, catalog.name)
    }

    /** Walks into a folder, or runs a search results URL, as a new level. */
    fun open(url: String, title: String) {
        levels.addLast(OpdsLevel(url, title))
        load()
    }

    fun openEntry(entry: OpdsEntry) {
        val url = entry.navigationUrl ?: return
        open(url, entry.title)
    }

    /** Goes up one level. Returns false at the top, where the screen should leave instead. */
    fun up(): Boolean {
        if (levels.size <= 1) return false
        loadJob?.cancel()
        levels.removeLast()
        publish(loading = !levels.last().loaded)
        if (!levels.last().loaded) load()
        return true
    }

    fun retry() = load(refresh = true)

    fun loadMore() {
        val catalog = catalog ?: return
        val level = levels.lastOrNull() ?: return
        val next = level.nextUrl ?: return
        if (_state.value.loadingMore || _state.value.loading) return
        _state.update { it.copy(loadingMore = true) }
        loadJob = viewModelScope.launch {
            runCatchingCancellable { client.feed(catalog, next) }
                .onSuccess { feed ->
                    replaceLevel(level.url) { current ->
                        val known = current.entries.mapTo(HashSet()) { it.id }
                        current.copy(
                            entries = current.entries + feed.entries.filter { known.add(it.id) },
                            nextUrl = feed.nextUrl?.takeIf { it != next },
                        )
                    }
                }
                .onFailure { error ->
                    Log.w(Tag, "Couldn't load $next", error)
                    // Keep what is shown; stop asking for more until the reader scrolls again or retries.
                    replaceLevel(level.url) { it.copy(nextUrl = null) }
                }
        }.also { job ->
            // Also on cancellation (the reader went up a level): the next level must not inherit a stuck spinner.
            job.invokeOnCompletion { _state.update { it.copy(loadingMore = false) } }
        }
    }

    fun search(terms: String) {
        val catalog = catalog ?: return
        val words = terms.trim()
        if (words.isEmpty()) return
        val search = levels.lastOrNull { it.search != null }?.search ?: return
        _state.update { it.copy(searchFailed = false) }
        viewModelScope.launch {
            val url = runCatchingCancellable { client.searchUrl(catalog, search, words) }.getOrNull()
            if (url == null) {
                _state.update { it.copy(searchFailed = true) }
            } else {
                open(url, words)
            }
        }
    }

    fun select(entry: OpdsEntry) {
        downloadJob?.cancel()
        _selection.value = OpdsSelection(entry)
    }

    fun close() {
        downloadJob?.cancel()
        _selection.value = null
    }

    fun download(acquisition: OpdsAcquisition) {
        val catalog = catalog ?: return
        val selected = _selection.value ?: return
        if (selected.download?.failed == false) return
        _selection.value = selected.copy(download = OpdsDownload(acquisition.format, progress = null))
        downloadJob = viewModelScope.launch {
            runCatchingCancellable {
                client.download(catalog, selected.entry, acquisition) { progress ->
                    _selection.update { current ->
                        current?.copy(download = OpdsDownload(acquisition.format, progress))
                    }
                }
            }.onSuccess { file ->
                // The same path as "Open with": the library imports it, skips a duplicate, reads its cover.
                incomingBookFiles.offer(listOf(Uri.fromFile(file)))
                _selection.value = null
                _imported.tryEmit(Unit)
            }.onFailure { error ->
                Log.w(Tag, "Couldn't download ${acquisition.url}", error)
                _selection.update { it?.copy(download = OpdsDownload(acquisition.format, progress = null, failed = true)) }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _selection.update { it?.copy(download = null) }
    }

    suspend fun cover(url: String) = catalog?.let { client.cover(it, url) }

    fun cachedCover(url: String) = catalog?.let { client.cachedCover(it, url) }

    private fun load(refresh: Boolean = false) {
        val catalog = catalog ?: return
        val level = levels.lastOrNull() ?: return
        loadJob?.cancel()
        publish(loading = true)
        loadJob = viewModelScope.launch {
            runCatchingCancellable { client.feed(catalog, level.url, refresh) }
                .onSuccess { feed ->
                    replaceLevel(level.url) {
                        it.copy(
                            title = feed.title.takeIf { title -> title.isNotBlank() && it.title.isBlank() } ?: it.title,
                            entries = feed.entries,
                            nextUrl = feed.nextUrl?.takeIf { next -> next != level.url },
                            search = feed.search,
                            loaded = true,
                        )
                    }
                    _state.update { it.copy(loading = false, failure = null) }
                }
                .onFailure { error ->
                    Log.w(Tag, "Couldn't load ${level.url}", error)
                    val failure = (error as? OpdsException)?.failure ?: OpdsFailure.UNREACHABLE
                    _state.update { it.copy(loading = false, failure = failure) }
                }
        }
    }

    private fun replaceLevel(url: String, change: (OpdsLevel) -> OpdsLevel) {
        val index = levels.indexOfLast { it.url == url }
        if (index < 0) return
        levels[index] = change(levels[index])
        if (index == levels.lastIndex) publish(loading = _state.value.loading)
    }

    private fun publish(loading: Boolean) {
        val level = levels.lastOrNull() ?: return
        _state.update {
            it.copy(
                levelKey = level.url,
                title = level.title,
                canGoUp = levels.size > 1,
                entries = level.entries,
                hasNext = level.nextUrl != null,
                canSearch = levels.any { l -> l.search != null },
                loading = loading,
                failure = null,
            )
        }
    }

    companion object {
        const val CatalogIdKey = "catalogId"
        private const val Tag = "Opds"
        private const val StopTimeoutMillis = 5_000L
    }
}
