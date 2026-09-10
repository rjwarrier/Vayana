package com.vayana.feature.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.backup.PortableCloudBook
import com.vayana.core.backup.PortableAsset
import com.vayana.core.backup.PortableReadingProgressPatch
import com.vayana.core.backup.PortableReadingSession
import com.vayana.core.backup.PortableReadingPositionAlternative
import com.vayana.core.backup.PortableSyncConflict
import com.vayana.core.backup.PortableWordLookupCounter
import com.vayana.core.backup.SnapshotExporter
import com.vayana.core.backup.patchPortableReadingProgressOnly
import com.vayana.core.backup.PortableAnnotation
import com.vayana.core.backup.PortableBookAlias
import com.vayana.core.backup.PortableShelf
import com.vayana.core.backup.PortableShelfMembership
import com.vayana.core.backup.PortableTombstone
import com.vayana.core.backup.PortableVocabularyCard
import com.vayana.core.backup.parsePortableAnnotations
import com.vayana.core.backup.parsePortableBookAliases
import com.vayana.core.backup.parsePortableCloudBooks
import com.vayana.core.backup.parsePortableReadingProgressSnapshot
import com.vayana.core.backup.parsePortableReadingSessions
import com.vayana.core.backup.parsePortableShelfMemberships
import com.vayana.core.backup.parsePortableShelves
import com.vayana.core.backup.parsePortableTombstones
import com.vayana.core.backup.parsePortableVocabularyCards
import com.vayana.core.backup.parsePortableWordLookupCounters
import com.vayana.core.backup.toJsonString
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.common.Hashing
import com.vayana.core.common.ParsedQuote
import com.vayana.core.common.QuoteParser
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.database.dao.AnnotationDao
import com.vayana.core.database.dao.BookAliasDao
import com.vayana.core.database.dao.BookDao
import com.vayana.core.database.dao.ShelfDao
import com.vayana.core.database.dao.TombstoneDao
import com.vayana.core.database.dao.VocabularyCardDao
import com.vayana.core.database.entity.BookAliasEntity
import com.vayana.core.database.entity.TombstoneEntity
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.ReadingSession
import com.vayana.core.database.model.Shelf
import com.vayana.core.database.repository.AnnotationMergeResult
import com.vayana.core.database.repository.AnnotationRecord
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.bookSyncIdOfReadingProgressReset
import com.vayana.core.database.repository.CloudBookMergeResult
import com.vayana.core.database.repository.CloudBookRecord
import com.vayana.core.database.repository.CloudReadingSessionRecord
import com.vayana.core.database.repository.CloudShelfMembershipRecord
import com.vayana.core.database.repository.CloudShelfRecord
import com.vayana.core.database.repository.CloudVocabularyCardRecord
import com.vayana.core.database.repository.CloudWordLookupCounter
import com.vayana.core.database.repository.ReadingProgressMergeResult
import com.vayana.core.database.repository.ReadingProgressVersion
import com.vayana.core.database.repository.ReadingSessionMergeResult
import com.vayana.core.database.repository.ReadingSessionRepository
import com.vayana.core.database.repository.ShelfMembershipMergeResult
import com.vayana.core.database.repository.ShelfMergeResult
import com.vayana.core.database.repository.ShelfRepository
import com.vayana.core.database.repository.TombstoneEntityType
import com.vayana.core.database.repository.VocabularyCardMergeResult
import com.vayana.core.database.repository.VocabularyCardRepository
import com.vayana.core.database.repository.WordLookupCounterMergeResult
import com.vayana.core.database.repository.WordLookupStatRepository
import com.vayana.core.database.repository.appliesOver
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.diagnostics.DiagnosticCategory
import com.vayana.core.diagnostics.DiagnosticsLogStore
import com.vayana.core.sync.asset.CloudAssetReference
import com.vayana.core.sync.asset.CloudBookAssetTransfer
import com.vayana.core.sync.asset.CloudBookFileDownloadPhase
import com.vayana.core.sync.asset.GitHubAssetStoreException
import com.vayana.core.sync.asset.GitHubContentsAssetStore
import com.vayana.core.sync.asset.GitHubRepository
import com.vayana.core.filesystem.BookFileImporter
import com.vayana.core.filesystem.StorageRoots
import com.vayana.format.epub.EpubParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Result of one import batch, still kept for the final Snackbar summary. */
data class ImportSummary(val imported: Int, val duplicates: Int, val unsupported: Int, val failed: Int)

enum class ImportRowStatus { QUEUED, COPYING, PARSING, IMPORTED, DUPLICATE, UNSUPPORTED, FAILED }

sealed interface BookDetailMessage {
    data object METADATA_SAVED : BookDetailMessage
    data object RATING_SAVED : BookDetailMessage
    data object COVER_UPDATED : BookDetailMessage
    data object COVER_REMOVED : BookDetailMessage
    data object COVER_FAILED : BookDetailMessage
    data object SOURCE_REPLACED : BookDetailMessage
    data object SOURCE_DUPLICATE : BookDetailMessage
    data object SOURCE_UNSUPPORTED : BookDetailMessage
    data object SOURCE_FAILED : BookDetailMessage
    data object LOCAL_FILE_REMOVED : BookDetailMessage
    data object LOCAL_FILE_REMOVE_UNAVAILABLE : BookDetailMessage
    data object LOCAL_FILE_REMOVE_FAILED : BookDetailMessage
    data class QUOTES_IMPORTED(val count: Int) : BookDetailMessage
    data object MARKED_FINISHED : BookDetailMessage
    data object GOODREADS_APPLIED : BookDetailMessage
    data class GOODREADS_APPLIED_WITH_QUOTES(val quotesAdded: Int) : BookDetailMessage
    data object GOODREADS_COVER_FAILED : BookDetailMessage
    data object GOODREADS_QUOTES_FAILED : BookDetailMessage
    data object GOODREADS_FAILED : BookDetailMessage
    data object READING_STATS_RESET : BookDetailMessage
    data object READING_STATS_RESET_FAILED : BookDetailMessage
}

enum class CloudBookDownloadResult {
    DOWNLOADED,
    SYNC_DISABLED,
    CONFIG_INCOMPLETE,
    ASSET_MISSING,
    FAILED,
}

enum class GitHubSyncMode {
    FULL,
    READING_PROGRESS_ONLY,
}

enum class CloudBookDownloadProgressStep {
    CHECKING_SETTINGS,
    DOWNLOADING_FILE,
    DECRYPTING_FILE,
    SAVING_FILE,
    DOWNLOADING_COVER,
    COMPLETE,
    FAILED,
}

data class CloudBookDownloadProgressState(
    val bookId: Long,
    val title: String,
    val step: CloudBookDownloadProgressStep,
    val detail: String,
    val completedSteps: Int,
    val totalSteps: Int = CloudBookDownloadProgressTotalSteps,
    val isRunning: Boolean = true,
) {
    val fraction: Float = (completedSteps.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
}

sealed interface GitHubSyncNowResult {
    data class Complete(
        val uploaded: Int,
        val failed: Int,
        val progressUpdated: Int,
        val cloudBooksCreated: Int,
        val cloudBooksUpdated: Int,
        val progressUploaded: Int = 0,
        val conflicts: Int,
        val skipped: Int,
        val pullFailed: Boolean,
        val metadataSynced: Boolean,
        val failureMessage: String? = null,
    ) : GitHubSyncNowResult
    data class InitialSyncConfirmationRequired(val message: String?) : GitHubSyncNowResult
    data object SyncDisabled : GitHubSyncNowResult
    data object ConfigIncomplete : GitHubSyncNowResult
}

data class ImportProgressRow(
    val id: String,
    val fileName: String,
    val status: ImportRowStatus,
)

data class ImportProgressState(
    val rows: List<ImportProgressRow> = emptyList(),
    val isRunning: Boolean = false,
) {
    val summary: ImportSummary
        get() = rows.summarize()
}

enum class GitHubSyncProgressStep {
    PREPARING,
    READING_CLOUD,
    ADDING_CLOUD_BOOKS,
    UPLOADING_BOOKS,
    SAVING_SNAPSHOT,
    COMPLETE,
    FAILED,
}

data class GitHubSyncProgressState(
    val step: GitHubSyncProgressStep,
    val detail: String,
    val completedSteps: Int,
    val totalSteps: Int = GitHubSyncProgressTotalSteps,
    val uploadedBooks: Int = 0,
    val failedBooks: Int = 0,
    val uploadedCovers: Int = 0,
    val downloadedCovers: Int = 0,
    val cloudBooksCreated: Int = 0,
    val cloudBooksUpdated: Int = 0,
    val progressUpdated: Int = 0,
    val isRunning: Boolean = true,
) {
    val fraction: Float = (completedSteps.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
}

private data class ReadingProgressMergeSummary(
    val applied: Int = 0,
    val conflicts: List<PortableSyncConflict> = emptyList(),
    val skipped: Int = 0,
    val failed: Boolean = false,
    val missingRemoteSnapshot: Boolean = false,
    val failureMessage: String? = null,
    val remoteSnapshotJson: String? = null,
    val remoteSnapshotSha: String? = null,
) {
    val conflictCount: Int
        get() = conflicts.size
}

/**
 * Common shape shared by every per-entity cloud-merge summary, so the overall sync result can be folded
 * from a plain list of these instead of a hand-written N-term OR/sum/elvis chain naming every entity type
 * (previously duplicated, with drift, between the main sync path and the conflict-rebase retry path).
 */
private interface SyncMergeOutcome {
    val failed: Boolean
    val skipped: Int
    val failureMessage: String?
}

private fun List<SyncMergeOutcome>.anyFailed(): Boolean = any { it.failed }
private fun List<SyncMergeOutcome>.totalSkipped(): Int = sumOf { it.skipped }
private fun List<SyncMergeOutcome>.firstFailureMessage(): String? = firstNotNullOfOrNull { it.failureMessage }

private data class CloudLibraryMergeSummary(
    val created: Int = 0,
    val updated: Int = 0,
    override val skipped: Int = 0,
    val coversDownloaded: Int = 0,
    override val failed: Boolean = false,
    override val failureMessage: String? = null,
) : SyncMergeOutcome

private data class ReadingSessionMergeSummary(
    val created: Int = 0,
    override val skipped: Int = 0,
    override val failed: Boolean = false,
    override val failureMessage: String? = null,
) : SyncMergeOutcome

private data class WordLookupCounterMergeSummary(
    val merged: Int = 0,
    override val skipped: Int = 0,
    override val failed: Boolean = false,
    override val failureMessage: String? = null,
) : SyncMergeOutcome

private data class AnnotationMergeSummary(
    val created: Int = 0,
    val updated: Int = 0,
    override val skipped: Int = 0,
    val conflicts: Int = 0,
    override val failed: Boolean = false,
    override val failureMessage: String? = null,
) : SyncMergeOutcome

private data class GenericSyncMergeSummary(
    val created: Int = 0,
    val updated: Int = 0,
    override val skipped: Int = 0,
    val appliedDeletes: Int = 0,
    override val failed: Boolean = false,
    override val failureMessage: String? = null,
) : SyncMergeOutcome

private data class ReadingProgressOnlyPushSummary(
    val pushed: Int = 0,
    val failed: Boolean = false,
    val failureMessage: String? = null,
)

private data class SnapshotMetadataSaveResult(
    val synced: Boolean,
    val failureMessage: String? = null,
    val extraBooksCreated: Int = 0,
    val extraBooksUpdated: Int = 0,
    val extraSkipped: Int = 0,
    val extraConflicts: Int = 0,
    val extraFailed: Boolean = false,
)

private data class SnapshotRebaseMerge(
    val summary: ReadingProgressMergeSummary,
    val booksCreated: Int = 0,
    val booksUpdated: Int = 0,
)

data class BookProgressChange(
    val bookId: Long,
    val previousLocator: String?,
    val previousPercent: Float,
    val newPercent: Float,
)

data class BookProgressSyncOutcome(
    val result: GitHubSyncNowResult,
    val progressChange: BookProgressChange?,
)

enum class LibrarySort { IMPORT_DATE, TITLE, AUTHOR, LAST_READ, PROGRESS }

enum class LibraryFilter { ALL, READING, FINISHED, NOT_STARTED }

enum class LibraryGroupBy { NONE, AUTHOR, SERIES }

data class LibraryControls(
    val query: String = "",
    val sort: LibrarySort = LibrarySort.IMPORT_DATE,
    val filter: LibraryFilter = LibraryFilter.ALL,
    val groupBy: LibraryGroupBy = LibraryGroupBy.NONE,
)

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val controls: LibraryControls = LibraryControls(),
    val githubSyncReady: Boolean = false,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val annotationRepository: AnnotationRepository,
    private val readingSessionRepository: ReadingSessionRepository,
    private val wordLookupStatRepository: WordLookupStatRepository,
    private val bookFileImporter: BookFileImporter,
    private val shelfRepository: ShelfRepository,
    private val vocabularyCardRepository: VocabularyCardRepository,
    private val settingsRepository: SettingsRepository,
    private val cloudBookAssetTransfer: CloudBookAssetTransfer,
    private val snapshotExporter: SnapshotExporter,
    private val storageRoots: StorageRoots,
    private val goodreadsMetadataFetcher: GoodreadsMetadataFetcher,
    private val dispatchers: DispatcherProvider,
    private val diagnosticsLogStore: DiagnosticsLogStore,
    private val bookAliasDao: BookAliasDao,
    private val tombstoneDao: TombstoneDao,
    private val bookDao: BookDao,
    private val shelfDao: ShelfDao,
    private val vocabularyCardDao: VocabularyCardDao,
    private val annotationDao: AnnotationDao,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val controls = MutableStateFlow(LibraryControls())

    /** [Book.coverPath] and [Book.filePath] come back root-relative; resolve both before UI use. */
    private val allBooks: Flow<List<Book>> = bookRepository.observeAll().withAbsolutePaths()

    val libraryBooks: StateFlow<List<Book>> =
        allBooks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val githubSyncReady: Flow<Boolean> = settingsRepository.snapshot
        .map { it.isGitHubSyncReady() }
        .distinctUntilChanged()

    val uiState: StateFlow<LibraryUiState> = combine(libraryBooks, controls, githubSyncReady) { books, controls, syncReady ->
        LibraryUiState(
            books = books
                .filterBy(controls.filter)
                .filterByQuery(controls.query)
                .sortedBy(controls.sort),
            controls = controls,
            githubSyncReady = syncReady,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun observeBook(bookId: Long): StateFlow<Book?> = libraryBooks
        .map { books -> books.firstOrNull { it.id == bookId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _importSummary = MutableStateFlow<ImportSummary?>(null)
    val importSummary: StateFlow<ImportSummary?> = _importSummary

    private val _importProgress = MutableStateFlow<ImportProgressState?>(null)
    val importProgress: StateFlow<ImportProgressState?> = _importProgress

    private val _syncProgress = MutableStateFlow<GitHubSyncProgressState?>(null)
    val syncProgress: StateFlow<GitHubSyncProgressState?> = _syncProgress

    private val _cloudBookDownloadProgress = MutableStateFlow<CloudBookDownloadProgressState?>(null)
    val cloudBookDownloadProgress: StateFlow<CloudBookDownloadProgressState?> = _cloudBookDownloadProgress

    private val _bookDetailMessage = MutableStateFlow<BookDetailMessage?>(null)
    val bookDetailMessage: StateFlow<BookDetailMessage?> = _bookDetailMessage

    fun updateQuery(query: String) {
        controls.update { it.copy(query = query) }
    }

    fun updateSort(sort: LibrarySort) {
        controls.update { it.copy(sort = sort) }
    }

    fun updateFilter(filter: LibraryFilter) {
        controls.update { it.copy(filter = filter) }
    }

    fun updateGroupBy(groupBy: LibraryGroupBy) {
        controls.update { it.copy(groupBy = groupBy) }
    }

    fun deleteBook(bookId: Long) {
        viewModelScope.launch { bookRepository.softDelete(bookId) }
    }

    val recentlyDeletedBooks: StateFlow<List<Book>> = bookRepository.observeDeleted()
        .withAbsolutePaths()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restoreBook(bookId: Long) {
        viewModelScope.launch { bookRepository.restore(bookId) }
    }

    fun purgeBook(bookId: Long) {
        viewModelScope.launch { bookRepository.purge(bookId) }
    }

    val shelves: StateFlow<List<Shelf>> = shelfRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val landscapeTwoColumnLayout: StateFlow<Boolean> = settingsRepository.snapshot
        .map { it.landscapeTwoColumnLayout }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val readNextQueue: StateFlow<List<Book>> = bookRepository.observeReadNextQueue()
        .withAbsolutePaths()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeBooksForShelf(shelfId: Long): StateFlow<List<Book>> = shelfRepository.observeBooksForShelf(shelfId)
        .withAbsolutePaths()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeShelvesForBook(bookId: Long): StateFlow<List<Shelf>> = shelfRepository.observeShelvesForBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeShelfBookCount(shelfId: Long): StateFlow<Int> = shelfRepository.observeShelfBookCount(shelfId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun observeShelf(shelfId: Long): StateFlow<Shelf?> = shelves
        .map { list -> list.firstOrNull { it.id == shelfId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun createShelf(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { shelfRepository.create(trimmed) }
    }

    fun renameShelf(shelfId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { shelfRepository.rename(shelfId, trimmed) }
    }

    fun deleteShelf(shelfId: Long) {
        viewModelScope.launch { shelfRepository.delete(shelfId) }
    }

    fun addBookToShelf(bookId: Long, shelfId: Long) {
        viewModelScope.launch { shelfRepository.addBookToShelf(bookId, shelfId) }
    }

    fun removeBookFromShelf(bookId: Long, shelfId: Long) {
        viewModelScope.launch { shelfRepository.removeBookFromShelf(bookId, shelfId) }
    }

    fun setReadNext(bookId: Long, queued: Boolean) {
        viewModelScope.launch { bookRepository.setReadNext(bookId, queued) }
    }

    fun updateMetadata(bookId: Long, title: String, author: String, series: String, seriesNumber: String, description: String, tagsCsv: String) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) return
        viewModelScope.launch {
            bookRepository.updateMetadata(
                id = bookId,
                title = normalizedTitle,
                author = author.trim().ifBlank { null },
                series = series.trim().ifBlank { null },
                seriesNumber = seriesNumber.trim().ifBlank { null },
                description = description.trim().ifBlank { null },
                tagsCsv = tagsCsv.normalizedTagsCsv(),
            )
            _bookDetailMessage.value = BookDetailMessage.METADATA_SAVED
        }
    }

    fun updateRating(bookId: Long, rating: Float) {
        val normalizedRating = ((rating * 2f).roundToInt() / 2f).coerceIn(0f, 5f)
        viewModelScope.launch {
            bookRepository.updateRating(bookId, normalizedRating)
            _bookDetailMessage.value = BookDetailMessage.RATING_SAVED
        }
    }

    private val _goodreadsImport = MutableStateFlow<GoodreadsImportState>(GoodreadsImportState.Idle)
    val goodreadsImport: StateFlow<GoodreadsImportState> = _goodreadsImport

    /** Fetches [link] and applies everything it yields - details, cover and quotes - with no review step. */
    fun importFromGoodreads(bookId: Long, link: String) {
        if (_goodreadsImport.value is GoodreadsImportState.Working) return
        viewModelScope.launch {
            _goodreadsImport.value = GoodreadsImportState.Working(GoodreadsImportStep.FETCHING_BOOK)
            when (val result = goodreadsMetadataFetcher.fetch(link)) {
                is GoodreadsFetchResult.Failure -> _goodreadsImport.value = GoodreadsImportState.Failed(result.error)
                is GoodreadsFetchResult.Success -> {
                    _bookDetailMessage.value = withContext(dispatchers.io) {
                        applyGoodreadsInLibrary(bookId, result.metadata, goodreadsMetadataFetcher::fetchQuotes)
                    }
                    _goodreadsImport.value = GoodreadsImportState.Done
                }
            }
        }
    }

    /** What the in-app Goodreads browser captured: applied like a direct import, with the quotes it already read. */
    fun importFromGoodreadsCapture(bookId: Long, metadata: GoodreadsBookMetadata, quotes: List<ParsedQuote>?) {
        if (_goodreadsImport.value is GoodreadsImportState.Working) return
        _goodreadsImport.value = GoodreadsImportState.Working(GoodreadsImportStep.FETCHING_COVER_AND_QUOTES)
        viewModelScope.launch {
            _bookDetailMessage.value = withContext(dispatchers.io) { applyGoodreadsInLibrary(bookId, metadata) { quotes } }
            _goodreadsImport.value = GoodreadsImportState.Done
        }
    }

    fun dismissGoodreadsImport() {
        _goodreadsImport.value = GoodreadsImportState.Idle
    }

    fun useCover(bookId: Long, source: CoverSource) {
        viewModelScope.launch {
            _bookDetailMessage.value = withContext(dispatchers.io) { useCoverInLibrary(bookId, source) }
        }
    }

    fun resetReadingStats(bookId: Long) {
        viewModelScope.launch {
            _bookDetailMessage.value = withContext(dispatchers.io) {
                runCatchingCancellable {
                    bookRepository.resetReadingStats(bookId)
                    BookDetailMessage.READING_STATS_RESET
                }.getOrElse { BookDetailMessage.READING_STATS_RESET_FAILED }
            }
        }
    }

    /** [announce] posts the detail-screen snackbar; the library grid shows its own instead. */
    fun markFinished(bookId: Long, announce: Boolean = true) {
        viewModelScope.launch {
            bookRepository.markFinished(bookId)
            if (announce) _bookDetailMessage.value = BookDetailMessage.MARKED_FINISHED
        }
    }

    fun replaceSource(bookId: Long, contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _bookDetailMessage.value = withContext(dispatchers.io) { replaceSourceInLibrary(bookId, contentResolver, uri) }
        }
    }

    fun replaceCover(bookId: Long, contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _bookDetailMessage.value = withContext(dispatchers.io) { replaceCoverInLibrary(bookId, contentResolver, uri) }
        }
    }

    fun removeCover(bookId: Long) {
        viewModelScope.launch {
            _bookDetailMessage.value = withContext(dispatchers.io) { removeCoverFromLibrary(bookId) }
        }
    }

    fun removeBookFromDevice(bookId: Long) {
        viewModelScope.launch {
            _bookDetailMessage.value = withContext(dispatchers.io) { removeLocalFileFromLibrary(bookId) }
        }
    }

    fun importQuotes(bookId: Long, quotesText: String) {
        viewModelScope.launch {
            val quotes = withContext(dispatchers.default) {
                QuoteParser.parse(quotesText)
            }
            if (quotes.isEmpty()) return@launch

            val now = System.currentTimeMillis()
            val annotations = quotes.mapIndexed { index, quote -> quote.toPopularHighlight(bookId, index, now) }
            withContext(dispatchers.io) {
                annotationRepository.createAll(annotations)
            }
            _bookDetailMessage.value = BookDetailMessage.QUOTES_IMPORTED(quotes.size)
        }
    }

    /** A quote as a "popular" underline: the reader finds [ParsedQuote.quoteText] in the book and draws it there. */
    private fun ParsedQuote.toPopularHighlight(bookId: Long, index: Int, now: Long): Annotation = Annotation(
        id = 0,
        bookId = bookId,
        type = AnnotationType.UNDERLINE,
        colorKey = "popular",
        locator = "quote:$index:${UUID.randomUUID()}",
        chapterTitle = sourceTitle ?: author,
        chapterHref = null,
        selectedText = quoteText,
        readerNote = "$highlightsCount highlights",
        createdAt = now,
        updatedAt = now,
    )

    fun importQuotesFromFile(bookId: Long, contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val text = withContext(dispatchers.io) {
                try {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    null
                }
            } ?: return@launch
            importQuotes(bookId, text)
        }
    }

    suspend fun downloadCloudBook(book: Book): CloudBookDownloadResult = withContext(dispatchers.io) {
        updateCloudBookDownloadProgress(book, CloudBookDownloadProgressStep.CHECKING_SETTINGS, "Checking GitHub settings", completedSteps = 0)
        val reference = book.fileAssetReference() ?: return@withContext finishCloudBookDownloadProgress(
            book = book,
            step = CloudBookDownloadProgressStep.FAILED,
            detail = "Cloud book file is missing",
            result = CloudBookDownloadResult.ASSET_MISSING,
        )
        val settings = settingsRepository.snapshot.first()
        if (!settings.githubSyncEnabled) {
            return@withContext finishCloudBookDownloadProgress(
                book = book,
                step = CloudBookDownloadProgressStep.FAILED,
                detail = "GitHub sync is turned off",
                result = CloudBookDownloadResult.SYNC_DISABLED,
            )
        }
        val syncConfig = settings.gitHubSyncConfig() ?: return@withContext finishCloudBookDownloadProgress(
            book = book,
            step = CloudBookDownloadProgressStep.FAILED,
            detail = "GitHub settings are incomplete",
            result = CloudBookDownloadResult.CONFIG_INCOMPLETE,
        )

        runCatchingCancellable {
            val store = syncConfig.assetStore()
            val passphrase = syncConfig.passphrase.toCharArray()
            val staged = try {
                updateCloudBookDownloadProgress(book, CloudBookDownloadProgressStep.DOWNLOADING_FILE, "Downloading book file", completedSteps = 1)
                cloudBookAssetTransfer.downloadBookFile(
                    bookId = book.id,
                    reference = reference,
                    extension = book.format.name.lowercase(),
                    passphrase = passphrase,
                    store = store,
                    onProgress = { phase ->
                        when (phase) {
                            CloudBookFileDownloadPhase.ENCRYPTED_BYTES_DOWNLOADED -> updateCloudBookDownloadProgress(
                                book = book,
                                step = CloudBookDownloadProgressStep.DECRYPTING_FILE,
                                detail = "Decrypting book file",
                                completedSteps = 2,
                            )
                            CloudBookFileDownloadPhase.PLAINTEXT_DECRYPTED -> updateCloudBookDownloadProgress(
                                book = book,
                                step = CloudBookDownloadProgressStep.SAVING_FILE,
                                detail = "Saving book to this device",
                                completedSteps = 3,
                            )
                        }
                    },
                )
            } finally {
                passphrase.fill('\u0000')
            }
            updateCloudBookDownloadProgress(book, CloudBookDownloadProgressStep.DOWNLOADING_COVER, "Checking cloud cover", completedSteps = 4)
            book.coverAssetReference()?.let { coverReference -> downloadCoverIfNeeded(book, coverReference, store) }
            // Older uploads (or books whose cover-upload never ran) carry no cover asset at
            // all in the cloud snapshot. Only fall back to extracting the cover straight from
            // the epub when there's genuinely no cover yet - the bulk sync pass may already
            // have downloaded a correct (possibly custom) cover for this book independently
            // of its file being local, and re-extracting would silently overwrite it.
            if (bookRepository.getById(book.id)?.coverPath.isNullOrBlank()) {
                extractLocalCoverFallback(book.id, staged.relativePath)
            }
            return@runCatchingCancellable finishCloudBookDownloadProgress(
                book = book,
                step = CloudBookDownloadProgressStep.COMPLETE,
                detail = "Book downloaded",
                result = CloudBookDownloadResult.DOWNLOADED,
            )
        }.getOrElse {
            finishCloudBookDownloadProgress(
                book = book,
                step = CloudBookDownloadProgressStep.FAILED,
                detail = "Book download failed",
                result = CloudBookDownloadResult.FAILED,
            )
        }
    }

    // Books that were locally imported (or downloaded) before cover extraction/upload existed,
    // or whose cover-upload never ran, can be stuck with a local epub but no cover forever -
    // nothing else re-checks them once they're already LOCAL. Repair opportunistically on
    // every sync so they pick up a cover (and get queued for cover upload right after).
    private suspend fun repairMissingCoversFromLocalFiles(books: List<Book>): Int =
        books
            .filter { book ->
                book.fileAvailability == BookFileAvailability.LOCAL &&
                    book.format == BookFormat.EPUB &&
                    book.coverPath.isNullOrBlank() &&
                    book.filePath.isNotBlank()
            }
            .count { book -> extractLocalCoverFallback(book.id, book.filePath) }

    private suspend fun extractLocalCoverFallback(bookId: Long, relativeFilePath: String): Boolean =
        runCatchingCancellable {
            val file = storageRoots.resolve(relativeFilePath)
            val coverBytes = EpubParser.parse(file).coverBytes ?: return@runCatchingCancellable false
            val coverFile = saveCover(coverBytes)
            bookRepository.updateCover(bookId, storageRoots.relativize(coverFile))
            true
        }.getOrDefault(false)

    suspend fun syncNow(
        allowInitialSync: Boolean = false,
        mode: GitHubSyncMode = GitHubSyncMode.FULL,
    ): GitHubSyncNowResult {
        val result = runSyncNow(allowInitialSync, mode)
        if (result is GitHubSyncNowResult.Complete && (result.pullFailed || !result.metadataSynced)) {
            withContext(dispatchers.io) {
                diagnosticsLogStore.record(
                    category = DiagnosticCategory.SYNC,
                    source = "LibraryViewModel.syncNow",
                    message = buildString {
                        append(if (result.pullFailed) "Cloud progress pull failed" else "Metadata sync failed")
                        result.failureMessage?.let { append(": ").append(it) }
                    },
                )
            }
        }
        return result
    }

    /**
     * Reading-progress-only sync, scoped to a caller-visible "did this exact book move?" check.
     * The underlying merge (like any other sync entry point) applies immediately - there's no
     * preview step - so this snapshots the book before and diffs against a fresh read after,
     * rather than trusting the reactive [observeBook] flow to have recomposed by the time this
     * suspend call returns. The caller uses the result to offer a keep/revert choice: whichever
     * screen triggered this is exactly the one already showing this book's progress, so it's the
     * right place to ask, no matter that the write already happened underneath.
     */
    suspend fun syncReadingProgressForBook(bookId: Long): BookProgressSyncOutcome {
        val before = bookRepository.getById(bookId)
        val result = syncNow(mode = GitHubSyncMode.READING_PROGRESS_ONLY)
        val after = bookRepository.getById(bookId)
        // Only offer to revert when there was a real prior position to protect - a book going
        // from "never opened" to some synced progress isn't a conflict, just filling in data.
        val changed = if (before != null && after != null && !before.lastLocator.isNullOrBlank() &&
            (before.lastLocator != after.lastLocator || before.readingPercent != after.readingPercent)
        ) {
            BookProgressChange(
                bookId = bookId,
                previousLocator = before.lastLocator,
                previousPercent = before.readingPercent,
                newPercent = after.readingPercent,
            )
        } else {
            null
        }
        return BookProgressSyncOutcome(result, changed)
    }

    /** Reverts to a specific book's pre-sync position - the "stay" side of the prompt above. */
    fun revertReadingProgress(bookId: Long, locator: String?, percent: Float) {
        val cfi = locator?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch { bookRepository.updateLocator(bookId, cfi, percent) }
    }

    private suspend fun runSyncNow(
        allowInitialSync: Boolean,
        mode: GitHubSyncMode,
    ): GitHubSyncNowResult = withContext(dispatchers.io) {
        updateSyncProgress(GitHubSyncProgressStep.PREPARING, "Checking GitHub settings", completedSteps = 0)
        val settings = settingsRepository.snapshot.first()
        if (!settings.githubSyncEnabled) {
            finishSyncProgress(GitHubSyncProgressStep.FAILED, "GitHub sync is turned off")
            return@withContext GitHubSyncNowResult.SyncDisabled
        }
        val syncConfig = settings.gitHubSyncConfig()
        if (syncConfig == null) {
            finishSyncProgress(GitHubSyncProgressStep.FAILED, "GitHub settings are incomplete")
            return@withContext GitHubSyncNowResult.ConfigIncomplete
        }
        val store = runCatchingCancellable { syncConfig.assetStore() }
            .getOrElse {
                finishSyncProgress(GitHubSyncProgressStep.FAILED, "GitHub settings are invalid")
                return@withContext GitHubSyncNowResult.ConfigIncomplete
            }
        updateSyncProgress(GitHubSyncProgressStep.READING_CLOUD, "Reading cloud library", completedSteps = 1)
        val progressMerge = pullReadingProgress(store)
        if (progressMerge.failed) {
            finishSyncProgress(GitHubSyncProgressStep.FAILED, "Cloud progress could not be read")
            return@withContext GitHubSyncNowResult.Complete(
                uploaded = 0,
                failed = 0,
                progressUpdated = 0,
                cloudBooksCreated = 0,
                cloudBooksUpdated = 0,
                conflicts = 0,
                skipped = 0,
                pullFailed = true,
                metadataSynced = false,
                failureMessage = progressMerge.failureMessage,
            )
        }
        if (progressMerge.missingRemoteSnapshot && mode == GitHubSyncMode.READING_PROGRESS_ONLY) {
            finishSyncProgress(GitHubSyncProgressStep.FAILED, "Cloud progress could not be found")
            return@withContext GitHubSyncNowResult.Complete(
                uploaded = 0,
                failed = 0,
                progressUpdated = 0,
                cloudBooksCreated = 0,
                cloudBooksUpdated = 0,
                progressUploaded = 0,
                conflicts = 0,
                skipped = 0,
                pullFailed = true,
                metadataSynced = false,
                failureMessage = progressMerge.failureMessage,
            )
        }
        if (progressMerge.missingRemoteSnapshot && !allowInitialSync) {
            finishSyncProgress(GitHubSyncProgressStep.FAILED, "Cloud progress needs confirmation")
            return@withContext GitHubSyncNowResult.InitialSyncConfirmationRequired(progressMerge.failureMessage)
        }
        if (mode == GitHubSyncMode.READING_PROGRESS_ONLY) {
            updateSyncProgress(
                step = GitHubSyncProgressStep.SAVING_SNAPSHOT,
                detail = "Saving reading progress",
                completedSteps = 4,
                progressUpdated = progressMerge.applied,
            )
            val progressPush = pushReadingProgressOnly(
                remoteSnapshotJson = progressMerge.remoteSnapshotJson.orEmpty(),
                remoteSnapshotSha = progressMerge.remoteSnapshotSha,
                store = store,
            )
            finishSyncProgress(
                step = if (progressPush.failed) GitHubSyncProgressStep.FAILED else GitHubSyncProgressStep.COMPLETE,
                detail = if (progressPush.failed) "Reading progress save failed" else "Reading progress synced",
                progressUpdated = progressMerge.applied,
            )
            return@withContext GitHubSyncNowResult.Complete(
                uploaded = 0,
                failed = 0,
                progressUpdated = progressMerge.applied,
                cloudBooksCreated = 0,
                cloudBooksUpdated = 0,
                progressUploaded = progressPush.pushed,
                conflicts = progressMerge.conflictCount,
                skipped = progressMerge.skipped,
                pullFailed = progressMerge.failed,
                metadataSynced = !progressPush.failed,
                failureMessage = progressPush.failureMessage,
            )
        }
        updateSyncProgress(
            step = GitHubSyncProgressStep.ADDING_CLOUD_BOOKS,
            detail = "Adding cloud books",
            completedSteps = 2,
            progressUpdated = progressMerge.applied,
        )
        val tombstoneMerge = if (progressMerge.missingRemoteSnapshot) {
            GenericSyncMergeSummary()
        } else {
            mergeCloudTombstones(progressMerge.remoteSnapshotJson.orEmpty())
        }
        val cloudLibraryMerge = if (progressMerge.missingRemoteSnapshot) {
            CloudLibraryMergeSummary()
        } else {
            mergeCloudLibrary(progressMerge.remoteSnapshotJson.orEmpty(), store)
        }
        val bookAliasMerge = if (progressMerge.missingRemoteSnapshot) {
            GenericSyncMergeSummary()
        } else {
            mergeCloudBookAliases(progressMerge.remoteSnapshotJson.orEmpty())
        }
        val shelfMerge = if (progressMerge.missingRemoteSnapshot) {
            GenericSyncMergeSummary()
        } else {
            mergeCloudShelves(progressMerge.remoteSnapshotJson.orEmpty())
        }
        // Must run after cloudLibraryMerge/shelfMerge: records whose book or shelf was just
        // created above can only resolve sync ids to local rows once those rows exist.
        val shelfMembershipMerge = if (progressMerge.missingRemoteSnapshot) {
            GenericSyncMergeSummary()
        } else {
            mergeCloudShelfMemberships(progressMerge.remoteSnapshotJson.orEmpty())
        }
        val vocabularyCardMerge = if (progressMerge.missingRemoteSnapshot) {
            GenericSyncMergeSummary()
        } else {
            mergeCloudVocabularyCards(progressMerge.remoteSnapshotJson.orEmpty())
        }
        val readingSessionMerge = if (progressMerge.missingRemoteSnapshot) {
            ReadingSessionMergeSummary()
        } else {
            mergeCloudReadingSessions(progressMerge.remoteSnapshotJson.orEmpty())
        }
        val wordLookupCounterMerge = if (progressMerge.missingRemoteSnapshot) {
            WordLookupCounterMergeSummary()
        } else {
            mergeCloudWordLookupCounters(progressMerge.remoteSnapshotJson.orEmpty())
        }
        val annotationMerge = if (progressMerge.missingRemoteSnapshot) {
            AnnotationMergeSummary()
        } else {
            mergeCloudAnnotations(progressMerge.remoteSnapshotJson.orEmpty())
        }
        val booksBeforeRepair = bookRepository.observeAll().first()
        val repairedCovers = repairMissingCoversFromLocalFiles(booksBeforeRepair)
        val localBooks = if (repairedCovers > 0) bookRepository.observeAll().first() else booksBeforeRepair
        val uploadCandidates = localBooks
            .filter { book ->
                book.fileAvailability == BookFileAvailability.LOCAL &&
                    book.format != BookFormat.PHYSICAL &&
                    book.fileAssetId.isNullOrBlank()
            }
        val coverUploadCandidates = localBooks
            .filter { book ->
                book.fileAvailability == BookFileAvailability.LOCAL &&
                    book.coverNeedsUpload(storageRoots)
            }

        updateSyncProgress(
            step = GitHubSyncProgressStep.UPLOADING_BOOKS,
            detail = "Uploading ${uploadCandidates.size} books and ${coverUploadCandidates.size} covers",
            completedSteps = 3,
            progressUpdated = progressMerge.applied,
            cloudBooksCreated = cloudLibraryMerge.created,
            cloudBooksUpdated = cloudLibraryMerge.updated,
            downloadedCovers = cloudLibraryMerge.coversDownloaded,
        )
        var uploaded = 0
        var failed = 0
        var uploadedCovers = 0
        uploadCandidates.forEach { book ->
            val passphrase = syncConfig.passphrase.toCharArray()
            val result = runCatchingCancellable {
                try {
                    cloudBookAssetTransfer.uploadBookFile(book.id, passphrase, store)
                } finally {
                    passphrase.fill('\u0000')
                }
            }
            result.onSuccess {
                uploaded += 1
            }.onFailure {
                failed += 1
            }
            updateSyncProgress(
                step = GitHubSyncProgressStep.UPLOADING_BOOKS,
                detail = "Uploaded $uploaded of ${uploadCandidates.size} local books",
                completedSteps = 3,
                uploadedBooks = uploaded,
                failedBooks = failed,
                uploadedCovers = uploadedCovers,
                progressUpdated = progressMerge.applied,
                cloudBooksCreated = cloudLibraryMerge.created,
                cloudBooksUpdated = cloudLibraryMerge.updated,
                downloadedCovers = cloudLibraryMerge.coversDownloaded,
            )
        }
        coverUploadCandidates.forEach { book ->
            val passphrase = syncConfig.passphrase.toCharArray()
            val result = runCatchingCancellable {
                try {
                    cloudBookAssetTransfer.uploadCoverImage(book.id, passphrase, store)
                } finally {
                    passphrase.fill('\u0000')
                }
            }
            result.onSuccess {
                uploadedCovers += 1
            }
            updateSyncProgress(
                step = GitHubSyncProgressStep.UPLOADING_BOOKS,
                detail = "Uploaded $uploadedCovers of ${coverUploadCandidates.size} covers",
                completedSteps = 3,
                uploadedBooks = uploaded,
                failedBooks = failed,
                uploadedCovers = uploadedCovers,
                progressUpdated = progressMerge.applied,
                cloudBooksCreated = cloudLibraryMerge.created,
                cloudBooksUpdated = cloudLibraryMerge.updated,
                downloadedCovers = cloudLibraryMerge.coversDownloaded,
            )
        }
        updateSyncProgress(
            step = GitHubSyncProgressStep.SAVING_SNAPSHOT,
            detail = "Saving latest snapshot",
            completedSteps = 4,
            uploadedBooks = uploaded,
            failedBooks = failed,
            uploadedCovers = uploadedCovers,
            progressUpdated = progressMerge.applied,
            cloudBooksCreated = cloudLibraryMerge.created,
            cloudBooksUpdated = cloudLibraryMerge.updated,
            downloadedCovers = cloudLibraryMerge.coversDownloaded,
        )
        val metadataSave = saveMetadataSnapshotWithRebase(
            store = store,
            syncConfig = syncConfig,
            expectedSha = progressMerge.remoteSnapshotSha,
            conflicts = progressMerge.conflicts,
        )
        // A rebase (409-conflict retry) may have pulled and merged additional remote data into the local DB after
        // the counts above were computed; fold its deltas in so the reported summary reflects what actually synced.
        val entityMerges = listOf(
            cloudLibraryMerge, tombstoneMerge, bookAliasMerge, shelfMerge, shelfMembershipMerge,
            vocabularyCardMerge, readingSessionMerge, wordLookupCounterMerge, annotationMerge,
        )
        val totalBooksCreated = cloudLibraryMerge.created + metadataSave.extraBooksCreated
        val totalBooksUpdated = cloudLibraryMerge.updated + metadataSave.extraBooksUpdated
        val totalSkipped = progressMerge.skipped + entityMerges.totalSkipped() + metadataSave.extraSkipped
        val anyPullFailed = progressMerge.failed || entityMerges.anyFailed() || metadataSave.extraFailed
        finishSyncProgress(
            step = if (metadataSave.synced && failed == 0 && !anyPullFailed) {
                GitHubSyncProgressStep.COMPLETE
            } else {
                GitHubSyncProgressStep.FAILED
            },
            detail = if (metadataSave.synced) "Sync finished" else "Snapshot save failed",
            uploadedBooks = uploaded,
            failedBooks = failed,
            uploadedCovers = uploadedCovers,
            progressUpdated = progressMerge.applied,
            cloudBooksCreated = totalBooksCreated,
            cloudBooksUpdated = totalBooksUpdated,
            downloadedCovers = cloudLibraryMerge.coversDownloaded,
        )
        GitHubSyncNowResult.Complete(
            uploaded = uploaded,
            failed = failed,
            progressUpdated = progressMerge.applied,
            cloudBooksCreated = totalBooksCreated,
            cloudBooksUpdated = totalBooksUpdated,
            conflicts = progressMerge.conflictCount + metadataSave.extraConflicts,
            skipped = totalSkipped,
            pullFailed = anyPullFailed && !allowInitialSync,
            metadataSynced = metadataSave.synced,
            failureMessage = metadataSave.failureMessage ?: entityMerges.firstFailureMessage(),
        )
    }

    private suspend fun pullReadingProgress(store: GitHubContentsAssetStore): ReadingProgressMergeSummary =
        runCatchingCancellable {
            val remoteDocument = store.getSyncDocumentWithSha("vayana/snapshot-latest.json")
            val snapshotJson = remoteDocument.bytes.toString(Charsets.UTF_8)
            val tombstoneMerge = mergeCloudTombstones(snapshotJson)
            if (tombstoneMerge.failed) {
                return@runCatchingCancellable ReadingProgressMergeSummary(
                    failed = true,
                    skipped = tombstoneMerge.skipped,
                    failureMessage = tombstoneMerge.failureMessage,
                    remoteSnapshotJson = snapshotJson,
                    remoteSnapshotSha = remoteDocument.sha,
                )
            }
            val progressMerge = mergeReadingProgressSnapshot(snapshotJson)
            progressMerge.copy(
                skipped = progressMerge.skipped + tombstoneMerge.skipped,
                remoteSnapshotJson = snapshotJson,
                remoteSnapshotSha = remoteDocument.sha,
            )
        }.getOrElse { throwable ->
            if (throwable.isMissingRemoteSnapshot()) {
                ReadingProgressMergeSummary(
                    missingRemoteSnapshot = true,
                    failureMessage = throwable.syncFailureMessage(),
                )
            } else {
                ReadingProgressMergeSummary(failed = true, failureMessage = throwable.syncFailureMessage())
            }
        }

    private suspend fun mergeReadingProgressSnapshot(snapshotJson: String): ReadingProgressMergeSummary {
        val remoteSnapshot = parsePortableReadingProgressSnapshot(snapshotJson)
        val localDeviceLabel = settingsRepository.snapshot.first().deviceLabelForSync()
        return remoteSnapshot.progresses.fold(ReadingProgressMergeSummary()) { summary, progress ->
            val mergeResult = bookRepository.applySyncedReadingProgress(
                syncId = progress.syncId,
                fileHash = progress.fileHash,
                locator = progress.lastLocator,
                readingPercent = progress.readingPercent,
                lastReadAt = progress.lastReadAt,
                remoteUpdatedAt = progress.updatedAt,
                startedReadingAt = progress.startedReadingAt,
                finishedReadingAt = progress.finishedReadingAt,
                totalReadingSeconds = progress.totalReadingSeconds,
            )
            when (mergeResult) {
                ReadingProgressMergeResult.AppliedRemote -> summary.copy(applied = summary.applied + 1)
                is ReadingProgressMergeResult.ConflictLocalKept -> summary.copy(
                    conflicts = summary.conflicts + mergeResult.toPortableConflict(
                        localDeviceLabel = localDeviceLabel,
                        remoteDeviceLabel = remoteSnapshot.deviceLabel,
                    ),
                )
                ReadingProgressMergeResult.LocalNewer,
                ReadingProgressMergeResult.NoLocalMatch,
                ReadingProgressMergeResult.InvalidRemote,
                -> summary.copy(skipped = summary.skipped + 1)
            }
        }
    }

    private suspend fun mergeCloudLibrary(snapshotJson: String, store: GitHubContentsAssetStore): CloudLibraryMergeSummary =
        runCatchingCancellable {
            // Merging is DB-only and fast; do it sequentially first so cover downloads below only
            // ever run against already-merged rows. The downloads themselves are the network-bound
            // part - for a library with many new/changed covers, running them one at a time was the
            // dominant cost of a fresh-device sync. Fan them out with bounded concurrency instead.
            var created = 0
            var updated = 0
            var skipped = 0
            val coverTargets = ArrayList<Pair<PortableCloudBook, CloudBookRecord>>()
            for (cloudBook in parsePortableCloudBooks(snapshotJson)) {
                val record = cloudBook.toRecord()
                if (record == null) {
                    skipped += 1
                    continue
                }
                when (bookRepository.mergeCloudBook(record)) {
                    CloudBookMergeResult.CREATED -> created += 1
                    CloudBookMergeResult.UPDATED -> updated += 1
                    CloudBookMergeResult.SKIPPED -> skipped += 1
                }
                coverTargets += cloudBook to record
            }
            val coversDownloaded = downloadCloudCoversConcurrently(coverTargets, store)
            CloudLibraryMergeSummary(created = created, updated = updated, skipped = skipped, coversDownloaded = coversDownloaded)
        }.getOrElse { throwable ->
            CloudLibraryMergeSummary(failed = true, failureMessage = throwable.syncFailureMessage())
        }

    private suspend fun downloadCloudCoversConcurrently(
        targets: List<Pair<PortableCloudBook, CloudBookRecord>>,
        store: GitHubContentsAssetStore,
    ): Int = coroutineScope {
        val permits = Semaphore(MaxConcurrentCoverDownloads)
        targets.map { (cloudBook, record) ->
            async {
                permits.withPermit {
                    runCatchingCancellable { downloadCloudCoverIfNeeded(cloudBook, record, store) }.getOrDefault(0)
                }
            }
        }.sumOf { it.await() }
    }


    private suspend fun mergeCloudShelves(snapshotJson: String): GenericSyncMergeSummary =
        runCatchingCancellable {
            var created = 0
            var updated = 0
            var skipped = 0
            for (remote in parsePortableShelves(snapshotJson)) {
                when (shelfRepository.mergeCloudShelf(remote.toRecord())) {
                    ShelfMergeResult.CREATED -> created += 1
                    ShelfMergeResult.UPDATED -> updated += 1
                    ShelfMergeResult.SKIPPED -> skipped += 1
                }
            }
            GenericSyncMergeSummary(created = created, updated = updated, skipped = skipped)
        }.getOrElse { throwable ->
            GenericSyncMergeSummary(failed = true, failureMessage = throwable.syncFailureMessage())
        }

    private suspend fun mergeCloudShelfMemberships(snapshotJson: String): GenericSyncMergeSummary =
        runCatchingCancellable {
            var created = 0
            var skipped = 0
            for (remote in parsePortableShelfMemberships(snapshotJson)) {
                when (shelfRepository.mergeCloudMembership(remote.toRecord())) {
                    ShelfMembershipMergeResult.CREATED -> created += 1
                    ShelfMembershipMergeResult.SKIPPED -> skipped += 1
                }
            }
            GenericSyncMergeSummary(created = created, skipped = skipped)
        }.getOrElse { throwable ->
            GenericSyncMergeSummary(failed = true, failureMessage = throwable.syncFailureMessage())
        }

    private suspend fun mergeCloudVocabularyCards(snapshotJson: String): GenericSyncMergeSummary =
        runCatchingCancellable {
            var created = 0
            var updated = 0
            var skipped = 0
            for (remote in parsePortableVocabularyCards(snapshotJson)) {
                when (vocabularyCardRepository.mergeCloudCard(remote.toRecord())) {
                    VocabularyCardMergeResult.CREATED -> created += 1
                    VocabularyCardMergeResult.UPDATED -> updated += 1
                    VocabularyCardMergeResult.SKIPPED -> skipped += 1
                }
            }
            GenericSyncMergeSummary(created = created, updated = updated, skipped = skipped)
        }.getOrElse { throwable ->
            GenericSyncMergeSummary(failed = true, failureMessage = throwable.syncFailureMessage())
        }

    private suspend fun mergeCloudBookAliases(snapshotJson: String): GenericSyncMergeSummary =
        runCatchingCancellable {
            var created = 0
            var skipped = 0
            for (remote in parsePortableBookAliases(snapshotJson)) {
                if (tombstoneDao.findBySyncId(remote.syncId) != null || remote.syncId.isBlank() || remote.fileHash.isBlank()) {
                    skipped += 1
                } else {
                    bookAliasDao.upsert(BookAliasEntity(syncId = remote.syncId, fileHash = remote.fileHash, createdAt = remote.createdAt))
                    created += 1
                }
            }
            GenericSyncMergeSummary(created = created, skipped = skipped)
        }.getOrElse { throwable ->
            GenericSyncMergeSummary(failed = true, failureMessage = throwable.syncFailureMessage())
        }

    private suspend fun mergeCloudTombstones(snapshotJson: String): GenericSyncMergeSummary =
        runCatchingCancellable {
            var created = 0
            var appliedDeletes = 0
            var skipped = 0
            for (remote in parsePortableTombstones(snapshotJson)) {
                if (remote.syncId.isBlank() || remote.entityType.isBlank() || remote.deletedAt <= 0L) {
                    skipped += 1
                    continue
                }
                val existing = tombstoneDao.findBySyncId(remote.syncId)
                if (existing != null && existing.deletedAt > remote.deletedAt) {
                    skipped += 1
                    continue
                }
                if (existing == null || existing.deletedAt < remote.deletedAt || existing.entityType != remote.entityType) {
                    tombstoneDao.upsert(TombstoneEntity(syncId = remote.syncId, entityType = remote.entityType, deletedAt = remote.deletedAt))
                    created += 1
                }
                appliedDeletes += applyCloudTombstone(remote)
            }
            GenericSyncMergeSummary(created = created, skipped = skipped, appliedDeletes = appliedDeletes)
        }.getOrElse { throwable ->
            GenericSyncMergeSummary(failed = true, failureMessage = throwable.syncFailureMessage())
        }

    private suspend fun applyCloudTombstone(tombstone: PortableTombstone): Int {
        // Unrecognized entityType (e.g. a newer app version's tombstone kind synced down): leave it stored for
        // when this device updates, apply nothing now. TombstoneEntityType.fromValue's null return, plus the
        // exhaustive `when` below with no `else`, means a *known* type added later fails to compile here until
        // handled, instead of silently falling through to 0 the way an `else` branch would.
        val type = TombstoneEntityType.fromValue(tombstone.entityType) ?: return 0
        val entity = TombstoneEntity(syncId = tombstone.syncId, entityType = tombstone.entityType, deletedAt = tombstone.deletedAt)
        return when (type) {
            TombstoneEntityType.BOOK -> {
                val book = bookDao.findAnyBySyncId(tombstone.syncId)
                if (book != null && !tombstoneDao.appliesOver(entity, book.updatedAt)) {
                    0
                } else {
                    bookDao.softDeleteBySyncId(tombstone.syncId, tombstone.deletedAt)
                }
            }
            TombstoneEntityType.ANNOTATION -> {
                val annotation = annotationDao.findBySyncId(tombstone.syncId)
                if (annotation != null && !tombstoneDao.appliesOver(entity, annotation.updatedAt)) {
                    0
                } else {
                    annotationDao.softDeleteBySyncId(tombstone.syncId, tombstone.deletedAt)
                }
            }
            TombstoneEntityType.SHELF -> {
                val shelf = shelfDao.findBySyncId(tombstone.syncId) ?: return 0
                if (!tombstoneDao.appliesOver(entity, shelf.updatedAt)) return 0
                shelfDao.deleteBySyncId(tombstone.syncId)
            }
            TombstoneEntityType.VOCABULARY_CARD -> {
                val card = vocabularyCardDao.findBySyncId(tombstone.syncId) ?: return 0
                val cardVersion = card.lastReviewedAt ?: card.createdAt
                if (!tombstoneDao.appliesOver(entity, cardVersion)) return 0
                vocabularyCardDao.deleteBySyncId(tombstone.syncId)
            }
            TombstoneEntityType.SHELF_MEMBERSHIP -> {
                val parts = tombstone.syncId.removePrefix("shelf_membership:").split(":", limit = 2)
                if (parts.size != 2) return 0
                shelfRepository.applyMembershipTombstone(bookSyncId = parts[0], shelfSyncId = parts[1], deletedAt = tombstone.deletedAt)
            }
            TombstoneEntityType.READING_SESSION -> readingSessionRepository.deleteBySyncId(tombstone.syncId)
            TombstoneEntityType.READING_PROGRESS_RESET -> {
                val bookSyncId = bookSyncIdOfReadingProgressReset(tombstone.syncId) ?: return 0
                bookRepository.applyReadingStatsReset(bookSyncId, resetAt = tombstone.deletedAt)
            }
        }
    }

    private suspend fun mergeCloudReadingSessions(snapshotJson: String): ReadingSessionMergeSummary =
        runCatchingCancellable {
            var created = 0
            var skipped = 0
            for (remote in parsePortableReadingSessions(snapshotJson)) {
                when (readingSessionRepository.mergeCloudSession(remote.toRecord())) {
                    ReadingSessionMergeResult.CREATED -> created += 1
                    ReadingSessionMergeResult.SKIPPED -> skipped += 1
                }
            }
            ReadingSessionMergeSummary(created = created, skipped = skipped)
        }.getOrElse { throwable ->
            ReadingSessionMergeSummary(failed = true, failureMessage = throwable.syncFailureMessage())
        }

    private suspend fun mergeCloudWordLookupCounters(snapshotJson: String): WordLookupCounterMergeSummary =
        runCatchingCancellable {
            var merged = 0
            var skipped = 0
            for (remote in parsePortableWordLookupCounters(snapshotJson)) {
                when (wordLookupStatRepository.mergeCloudCounter(remote.toRecord())) {
                    WordLookupCounterMergeResult.MERGED -> merged += 1
                    WordLookupCounterMergeResult.SKIPPED -> skipped += 1
                }
            }
            WordLookupCounterMergeSummary(merged = merged, skipped = skipped)
        }.getOrElse { throwable ->
            WordLookupCounterMergeSummary(failed = true, failureMessage = throwable.syncFailureMessage())
        }

    private suspend fun mergeCloudAnnotations(snapshotJson: String): AnnotationMergeSummary =
        runCatchingCancellable {
            var created = 0
            var updated = 0
            var skipped = 0
            var conflicts = 0
            for (remote in parsePortableAnnotations(snapshotJson)) {
                val record = remote.toRecord()
                if (record == null) {
                    skipped += 1
                    continue
                }
                when (annotationRepository.mergeCloudAnnotation(record)) {
                    AnnotationMergeResult.CREATED -> created += 1
                    AnnotationMergeResult.UPDATED -> updated += 1
                    AnnotationMergeResult.KEPT_LOCAL_OVER_CONFLICT -> conflicts += 1
                    AnnotationMergeResult.NO_CHANGE, AnnotationMergeResult.NO_LOCAL_BOOK -> skipped += 1
                }
            }
            AnnotationMergeSummary(created = created, updated = updated, skipped = skipped, conflicts = conflicts)
        }.getOrElse { throwable ->
            AnnotationMergeSummary(failed = true, failureMessage = throwable.syncFailureMessage())
        }

    private suspend fun saveMetadataSnapshotWithRebase(
        store: GitHubContentsAssetStore,
        syncConfig: GitHubSyncConfig,
        expectedSha: String?,
        conflicts: List<PortableSyncConflict>,
    ): SnapshotMetadataSaveResult {
        var latestExpectedSha = expectedSha
        var latestConflicts = conflicts
        // Rebasing on a 409 pulls and merges real remote data (new books, shelves, etc.) into the local DB. These
        // accumulators keep that work visible in the result the caller reports, instead of the caller silently
        // continuing to show only the pre-conflict counts computed before any rebase happened.
        var rebaseBooksCreated = 0
        var rebaseBooksUpdated = 0
        var rebaseSkipped = 0
        var rebaseFailed = false
        fun result(synced: Boolean, failureMessage: String? = null) = SnapshotMetadataSaveResult(
            synced = synced,
            failureMessage = failureMessage,
            extraBooksCreated = rebaseBooksCreated,
            extraBooksUpdated = rebaseBooksUpdated,
            extraSkipped = rebaseSkipped,
            extraConflicts = latestConflicts.size - conflicts.size,
            extraFailed = rebaseFailed,
        )
        repeat(MaxSnapshotMetadataSaveAttempts) { attemptIndex ->
            val saveAttempt = runCatchingCancellable {
                val snapshotBytes = snapshotExporter.export()
                    .copy(syncConflicts = latestConflicts)
                    .toJsonString()
                    .toByteArray(Charsets.UTF_8)
                store.putSyncDocument(syncConfig.deviceSnapshotPath, snapshotBytes)
                store.putSyncDocumentIfUnchanged("vayana/snapshot-latest.json", snapshotBytes, latestExpectedSha)
            }
            saveAttempt.onSuccess { return result(synced = true) }
            val throwable = saveAttempt.exceptionOrNull()
            if (throwable?.isGitHubConflict() != true) {
                return result(synced = false, failureMessage = throwable?.syncFailureMessage() ?: "Snapshot save failed")
            }
            if (attemptIndex == MaxSnapshotMetadataSaveAttempts - 1) {
                // The book/cover uploads earlier in this sync already succeeded; only this small metadata
                // pointer write lost the race to another device's concurrent save. Say so explicitly rather
                // than surfacing the raw GitHub 409, which reads as if the whole sync failed.
                return result(
                    synced = false,
                    failureMessage = "Another device saved changes while this sync was uploading. Your uploads are safe - sync again to finish.",
                )
            }

            val currentDocument = runCatchingCancellable { store.getSyncDocumentWithSha("vayana/snapshot-latest.json") }
                .getOrElse { refetchFailure ->
                    return result(synced = false, failureMessage = refetchFailure.syncFailureMessage())
                }
            val currentJson = currentDocument.bytes.toString(Charsets.UTF_8)
            val rebase = mergeRemoteSnapshotForMetadataRebase(currentJson, store)
            rebaseBooksCreated += rebase.booksCreated
            rebaseBooksUpdated += rebase.booksUpdated
            rebaseSkipped += rebase.summary.skipped
            if (rebase.summary.failed) {
                rebaseFailed = true
                return result(synced = false, failureMessage = rebase.summary.failureMessage)
            }
            latestExpectedSha = currentDocument.sha
            latestConflicts = latestConflicts + rebase.summary.conflicts
        }
        return result(synced = false, failureMessage = "Snapshot save failed")
    }

    private suspend fun mergeRemoteSnapshotForMetadataRebase(
        snapshotJson: String,
        store: GitHubContentsAssetStore,
    ): SnapshotRebaseMerge = runCatchingCancellable {
        val tombstoneMerge = mergeCloudTombstones(snapshotJson)
        val progressMerge = mergeReadingProgressSnapshot(snapshotJson)
        val cloudLibraryMerge = mergeCloudLibrary(snapshotJson, store)
        val bookAliasMerge = mergeCloudBookAliases(snapshotJson)
        val shelfMerge = mergeCloudShelves(snapshotJson)
        val shelfMembershipMerge = mergeCloudShelfMemberships(snapshotJson)
        val vocabularyCardMerge = mergeCloudVocabularyCards(snapshotJson)
        val readingSessionMerge = mergeCloudReadingSessions(snapshotJson)
        val wordLookupCounterMerge = mergeCloudWordLookupCounters(snapshotJson)
        val annotationMerge = mergeCloudAnnotations(snapshotJson)
        val entityMerges = listOf(
            cloudLibraryMerge, tombstoneMerge, bookAliasMerge, shelfMerge, shelfMembershipMerge,
            vocabularyCardMerge, readingSessionMerge, wordLookupCounterMerge, annotationMerge,
        )
        val summary = progressMerge.copy(
            failed = progressMerge.failed || entityMerges.anyFailed(),
            failureMessage = progressMerge.failureMessage ?: entityMerges.firstFailureMessage(),
            skipped = progressMerge.skipped + entityMerges.totalSkipped(),
        )
        SnapshotRebaseMerge(summary = summary, booksCreated = cloudLibraryMerge.created, booksUpdated = cloudLibraryMerge.updated)
    }.getOrElse { throwable ->
        SnapshotRebaseMerge(summary = ReadingProgressMergeSummary(failed = true, failureMessage = throwable.syncFailureMessage()))
    }

    private suspend fun pushReadingProgressOnly(
        remoteSnapshotJson: String,
        remoteSnapshotSha: String?,
        store: GitHubContentsAssetStore,
    ): ReadingProgressOnlyPushSummary {
        var jsonText = remoteSnapshotJson
        var sha = remoteSnapshotSha
        var lastFailure: Throwable? = null
        repeat(MaxProgressOnlyPushAttempts) { attemptIndex ->
            val attempt = runCatchingCancellable {
                val localBooks = bookRepository.observeAll().first()
                val bookSyncIdsByLocalId = localBooks.associate { it.id to it.syncId }
                val progressTombstones = tombstoneDao.getAll()
                    .filter { it.isReadingProgressOnlyTombstone() }
                    .map { it.toPortable() }
                val result = patchPortableReadingProgressOnly(
                    jsonText = jsonText,
                    patches = localBooks.map { book ->
                        PortableReadingProgressPatch(
                            fileHash = book.fileHash,
                            lastLocator = book.lastLocator,
                            readingPercent = book.readingPercent,
                            lastReadAt = book.lastReadAt,
                            startedReadingAt = book.startedReadingAt,
                            finishedReadingAt = book.finishedReadingAt,
                            totalReadingSeconds = book.totalReadingSeconds,
                        )
                    },
                    exportedAt = System.currentTimeMillis(),
                    readingSessions = readingSessionRepository.observeAll().first()
                        .mapNotNull { it.toPortable(bookSyncIdsByLocalId) },
                    wordLookupCounters = wordLookupStatRepository.getAllForSync().map { it.toPortable() },
                    tombstones = progressTombstones,
                )
                val pushed = result.patched + result.sessionsAdded + result.wordLookupCountersMerged + result.tombstonesMerged
                if (pushed > 0) {
                    val snapshotBytes = result.jsonText.toByteArray(Charsets.UTF_8)
                    store.putSyncDocumentIfUnchanged("vayana/snapshot-latest.json", snapshotBytes, sha)
                }
                ReadingProgressOnlyPushSummary(pushed = pushed)
            }
            attempt.onSuccess { result -> return result }
            val throwable = attempt.exceptionOrNull()
            lastFailure = throwable
            // Another device wrote to the snapshot between our pull and this push - re-fetch the
            // now-current document and re-apply our patch on top of it, rather than failing outright.
            if (throwable?.isGitHubConflict() != true || attemptIndex == MaxProgressOnlyPushAttempts - 1) {
                return ReadingProgressOnlyPushSummary(failed = true, failureMessage = throwable?.syncFailureMessage())
            }
            val refetch = runCatchingCancellable { store.getSyncDocumentWithSha("vayana/snapshot-latest.json") }
            val document = refetch.getOrElse { refetchFailure ->
                return ReadingProgressOnlyPushSummary(failed = true, failureMessage = refetchFailure.syncFailureMessage())
            }
            jsonText = document.bytes.toString(Charsets.UTF_8)
            sha = document.sha
        }
        return ReadingProgressOnlyPushSummary(failed = true, failureMessage = lastFailure?.syncFailureMessage())
    }

    private suspend fun downloadCloudCoverIfNeeded(
        cloudBook: PortableCloudBook,
        record: CloudBookRecord,
        store: GitHubContentsAssetStore,
    ): Int {
        val reference = cloudBook.coverAsset?.toCloudAssetReference() ?: return 0
        val book = bookRepository.findActiveBySyncIdOrHash(record.syncId, record.fileHash) ?: return 0
        return downloadCoverIfNeeded(book, reference, store)
    }

    private suspend fun downloadCoverIfNeeded(
        book: Book,
        reference: CloudAssetReference,
        store: GitHubContentsAssetStore,
    ): Int {
        val localCoverIsCurrent = book.coverAssetId == reference.id &&
            book.coverAssetSha256 == reference.sha256 &&
            book.coverAssetSizeBytes == reference.sizeBytes &&
            book.coverAssetUploadedAt == reference.uploadedAt &&
            book.coverPath?.let { storageRoots.resolve(it).isFile } == true
        if (localCoverIsCurrent) return 0

        val passphrase = settingsRepository.snapshot.first().githubSyncPassphrase.toCharArray()
        return runCatchingCancellable {
            try {
                cloudBookAssetTransfer.downloadCoverImage(
                    bookId = book.id,
                    reference = reference,
                    passphrase = passphrase,
                    store = store,
                )
            } finally {
                passphrase.fill('\u0000')
            }
            1
        }.getOrDefault(0)
    }

    fun onBookDetailMessageShown() {
        _bookDetailMessage.value = null
    }

    fun onImportSummaryShown() {
        _importSummary.value = null
    }

    fun onImportProgressDismissed() {
        if (_importProgress.value?.isRunning == false) {
            _importProgress.value = null
        }
    }

    fun onSyncProgressDismissed() {
        if (_syncProgress.value?.isRunning != true) {
            _syncProgress.value = null
        }
    }

    fun onCloudBookDownloadProgressDismissed() {
        if (_cloudBookDownloadProgress.value?.isRunning != true) {
            _cloudBookDownloadProgress.value = null
        }
    }

    private fun updateCloudBookDownloadProgress(
        book: Book,
        step: CloudBookDownloadProgressStep,
        detail: String,
        completedSteps: Int,
    ) {
        _cloudBookDownloadProgress.value = CloudBookDownloadProgressState(
            bookId = book.id,
            title = book.title,
            step = step,
            detail = detail,
            completedSteps = completedSteps,
        )
    }

    private fun finishCloudBookDownloadProgress(
        book: Book,
        step: CloudBookDownloadProgressStep,
        detail: String,
        result: CloudBookDownloadResult,
    ): CloudBookDownloadResult {
        _cloudBookDownloadProgress.value = CloudBookDownloadProgressState(
            bookId = book.id,
            title = book.title,
            step = step,
            detail = detail,
            completedSteps = if (step == CloudBookDownloadProgressStep.COMPLETE) CloudBookDownloadProgressTotalSteps else _cloudBookDownloadProgress.value?.completedSteps ?: 0,
            isRunning = false,
        )
        return result
    }

    private fun updateSyncProgress(
        step: GitHubSyncProgressStep,
        detail: String,
        completedSteps: Int,
        uploadedBooks: Int = _syncProgress.value?.uploadedBooks ?: 0,
        failedBooks: Int = _syncProgress.value?.failedBooks ?: 0,
        uploadedCovers: Int = _syncProgress.value?.uploadedCovers ?: 0,
        downloadedCovers: Int = _syncProgress.value?.downloadedCovers ?: 0,
        cloudBooksCreated: Int = _syncProgress.value?.cloudBooksCreated ?: 0,
        cloudBooksUpdated: Int = _syncProgress.value?.cloudBooksUpdated ?: 0,
        progressUpdated: Int = _syncProgress.value?.progressUpdated ?: 0,
    ) {
        _syncProgress.value = GitHubSyncProgressState(
            step = step,
            detail = detail,
            completedSteps = completedSteps,
            uploadedBooks = uploadedBooks,
            failedBooks = failedBooks,
            uploadedCovers = uploadedCovers,
            downloadedCovers = downloadedCovers,
            cloudBooksCreated = cloudBooksCreated,
            cloudBooksUpdated = cloudBooksUpdated,
            progressUpdated = progressUpdated,
        )
    }

    private fun finishSyncProgress(
        step: GitHubSyncProgressStep,
        detail: String,
        uploadedBooks: Int = _syncProgress.value?.uploadedBooks ?: 0,
        failedBooks: Int = _syncProgress.value?.failedBooks ?: 0,
        uploadedCovers: Int = _syncProgress.value?.uploadedCovers ?: 0,
        downloadedCovers: Int = _syncProgress.value?.downloadedCovers ?: 0,
        cloudBooksCreated: Int = _syncProgress.value?.cloudBooksCreated ?: 0,
        cloudBooksUpdated: Int = _syncProgress.value?.cloudBooksUpdated ?: 0,
        progressUpdated: Int = _syncProgress.value?.progressUpdated ?: 0,
    ) {
        _syncProgress.value = GitHubSyncProgressState(
            step = step,
            detail = detail,
            completedSteps = if (step == GitHubSyncProgressStep.COMPLETE) GitHubSyncProgressTotalSteps else _syncProgress.value?.completedSteps ?: 0,
            uploadedBooks = uploadedBooks,
            failedBooks = failedBooks,
            uploadedCovers = uploadedCovers,
            downloadedCovers = downloadedCovers,
            cloudBooksCreated = cloudBooksCreated,
            cloudBooksUpdated = cloudBooksUpdated,
            progressUpdated = progressUpdated,
            isRunning = false,
        )
    }

    fun importFiles(contentResolver: ContentResolver, uris: List<Uri>) {
        viewModelScope.launch {
            val candidates = uris.map { uri -> ImportCandidate(uri, displayNameOf(contentResolver, uri)) }
            startImportProgress(candidates)
            val results = withContext(dispatchers.io) {
                candidates.map { candidate -> importOne(contentResolver, candidate) }
            }
            _importSummary.value = results.summarize()
            markImportComplete()
        }
    }

    fun importFolder(contentResolver: ContentResolver, treeUri: Uri) {
        viewModelScope.launch {
            val candidates = withContext(dispatchers.io) {
                DocumentFile.fromTreeUri(appContext, treeUri)
                    ?.listFiles()
                    ?.filter { it.isFile }
                    ?.map { doc -> ImportCandidate(doc.uri, doc.name.orEmpty()) }
                    .orEmpty()
            }
            startImportProgress(candidates)
            val results = withContext(dispatchers.io) {
                candidates.map { candidate -> importOne(contentResolver, candidate) }
            }
            _importSummary.value = results.summarize()
            markImportComplete()
        }
    }

    /**
     * Adds a file-less entry for a paper book, so it can hold manually-typed quotes/notes.
     * [onCreated] fires with the new book's id once it lands, so the caller can jump straight
     * to its detail screen; it won't fire if [title] is blank or the insert somehow collides.
     */
    fun addPhysicalBook(title: String, author: String?, onCreated: (Long) -> Unit = {}) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        viewModelScope.launch {
            val book = withContext(dispatchers.io) {
                bookRepository.insertIfNew(
                    title = cleanTitle,
                    author = author?.trim()?.takeIf { it.isNotBlank() },
                    series = null,
                    seriesNumber = null,
                    description = null,
                    coverPath = null,
                    filePath = "",
                    format = BookFormat.PHYSICAL,
                    fileHash = "physical:${UUID.randomUUID()}",
                )
            }
            if (book != null) onCreated(book.id)
        }
    }

    private data class ImportCandidate(val uri: Uri, val displayName: String) {
        val id: String = "${uri}#${displayName}"
        val rowName: String = displayName.ifBlank { uri.lastPathSegment.orEmpty() }
    }

    private sealed interface ImportResult {
        val status: ImportRowStatus

        data object Imported : ImportResult {
            override val status = ImportRowStatus.IMPORTED
        }

        data object Duplicate : ImportResult {
            override val status = ImportRowStatus.DUPLICATE
        }

        data object Unsupported : ImportResult {
            override val status = ImportRowStatus.UNSUPPORTED
        }

        data object Failed : ImportResult {
            override val status = ImportRowStatus.FAILED
        }
    }

    private suspend fun importOne(contentResolver: ContentResolver, candidate: ImportCandidate): ImportResult {
        val uri = candidate.uri
        val displayName = candidate.displayName
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        val format = BookFormat.entries.firstOrNull { it.name.equals(extension, ignoreCase = true) }
            ?: return finishImportRow(candidate.id, ImportResult.Unsupported)
        if (format != BookFormat.EPUB) return finishImportRow(candidate.id, ImportResult.Unsupported)

        var importedFile: File? = null
        var coverFile: File? = null
        return runCatchingCancellable {
            updateImportRow(candidate.id, ImportRowStatus.COPYING)
            val imported = bookFileImporter.import(uri, extension)
            importedFile = imported.file
            updateImportRow(candidate.id, ImportRowStatus.PARSING)
            val metadata = EpubParser.parse(imported.file)
            coverFile = metadata.coverBytes?.let { bytes -> saveCover(bytes) }

            val book = bookRepository.insertIfNew(
                title = metadata.title,
                author = metadata.author,
                series = metadata.series,
                seriesNumber = metadata.seriesNumber,
                description = metadata.description,
                coverPath = coverFile?.let { storageRoots.relativize(it) },
                filePath = storageRoots.relativize(imported.file),
                format = format,
                fileHash = imported.sha256,
            )
            if (book != null) {
                importedFile = null
                coverFile = null
                ImportResult.Imported
            } else {
                ImportResult.Duplicate
            }
        }.getOrElse { ImportResult.Failed }
            .also { result ->
                if (result != ImportResult.Imported) {
                    importedFile?.delete()
                    coverFile?.delete()
                }
            }
            .also { result -> updateImportRow(candidate.id, result.status) }
    }

    private suspend fun replaceSourceInLibrary(bookId: Long, contentResolver: ContentResolver, uri: Uri): BookDetailMessage {
        val existingBook = bookRepository.getById(bookId)?.withAbsolutePaths() ?: return BookDetailMessage.SOURCE_FAILED
        val displayName = displayNameOf(contentResolver, uri)
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        val format = BookFormat.entries.firstOrNull { it.name.equals(extension, ignoreCase = true) }
            ?: return BookDetailMessage.SOURCE_UNSUPPORTED
        if (format != BookFormat.EPUB) return BookDetailMessage.SOURCE_UNSUPPORTED

        var importedFile: File? = null
        var coverFile: File? = null
        return runCatchingCancellable {
            val imported = bookFileImporter.import(uri, extension)
            importedFile = imported.file
            val metadata = EpubParser.parse(imported.file)
            coverFile = metadata.coverBytes?.let { bytes -> saveCover(bytes) }
            val replaced = bookRepository.replaceSource(
                id = bookId,
                title = metadata.title,
                author = metadata.author,
                series = existingBook.series,
                seriesNumber = existingBook.seriesNumber,
                description = metadata.description,
                coverPath = coverFile?.let { storageRoots.relativize(it) },
                filePath = storageRoots.relativize(imported.file),
                format = format,
                fileHash = imported.sha256,
            )
            if (replaced) {
                importedFile = null
                coverFile = null
                File(existingBook.filePath).delete()
                existingBook.coverPath?.let { File(it).delete() }
                BookDetailMessage.SOURCE_REPLACED
            } else {
                BookDetailMessage.SOURCE_DUPLICATE
            }
        }.getOrElse { BookDetailMessage.SOURCE_FAILED }
            .also { result ->
                if (result != BookDetailMessage.SOURCE_REPLACED) {
                    importedFile?.delete()
                    coverFile?.delete()
                }
            }
    }

    private suspend fun replaceCoverInLibrary(bookId: Long, contentResolver: ContentResolver, uri: Uri): BookDetailMessage {
        // Root-relative paths throughout: they're compared against the stored alternates, not just deleted.
        val existingBook = bookRepository.getById(bookId) ?: return BookDetailMessage.COVER_FAILED
        var coverFile: File? = null
        return runCatchingCancellable {
            val pickedCover = savePickedCover(contentResolver, uri)
            coverFile = pickedCover
            val pickedPath = storageRoots.relativize(pickedCover)
            bookRepository.updateCover(bookId, pickedPath)
            // The picked image becomes "your cover"; a Goodreads cover, if any, stays available to switch back to.
            bookRepository.updateCoverAlternates(bookId, customCoverPath = pickedPath, goodreadsCoverPath = existingBook.goodreadsCoverPath)
            listOfNotNull(existingBook.coverPath, existingBook.customCoverPath)
                .distinct()
                .filter { it != existingBook.goodreadsCoverPath }
                .forEach { storageRoots.resolve(it).delete() }
            coverFile = null
            BookDetailMessage.COVER_UPDATED
        }.getOrElse { BookDetailMessage.COVER_FAILED }
            .also { result ->
                if (result != BookDetailMessage.COVER_UPDATED) {
                    coverFile?.delete()
                }
            }
    }

    private suspend fun removeCoverFromLibrary(bookId: Long): BookDetailMessage {
        val existingBook = bookRepository.getById(bookId) ?: return BookDetailMessage.COVER_FAILED
        return runCatchingCancellable {
            val current = existingBook.coverPath
            val custom = existingBook.customCoverPath?.takeIf { it != current }
            val goodreads = existingBook.goodreadsCoverPath?.takeIf { it != current }
            // Fall back to the other stored cover, if there is one, rather than leaving the book bare.
            bookRepository.updateCover(bookId, custom ?: goodreads)
            bookRepository.updateCoverAlternates(bookId, customCoverPath = custom, goodreadsCoverPath = goodreads)
            current?.let { storageRoots.resolve(it).delete() }
            BookDetailMessage.COVER_REMOVED
        }.getOrElse { BookDetailMessage.COVER_FAILED }
    }

    private suspend fun useCoverInLibrary(bookId: Long, source: CoverSource): BookDetailMessage {
        val book = bookRepository.getById(bookId) ?: return BookDetailMessage.COVER_FAILED
        val target = when (source) {
            CoverSource.CUSTOM -> book.customCoverPath
            CoverSource.GOODREADS -> book.goodreadsCoverPath
        }
        if (target == null || !storageRoots.resolve(target).isFile) return BookDetailMessage.COVER_FAILED
        if (target != book.coverPath) bookRepository.updateCover(bookId, target)
        return BookDetailMessage.COVER_UPDATED
    }

    /**
     * Series, genres and description go through [BookRepository.updateMetadata] like a manual edit, so they sync;
     * the Goodreads rating, year and link stay local to this device. Fields the page didn't have leave the book's
     * own values alone. Quotes are added last, as popular highlights, skipping any the book already has.
     */
    private suspend fun applyGoodreadsInLibrary(
        bookId: Long,
        metadata: GoodreadsBookMetadata,
        loadQuotes: suspend (workId: String) -> List<ParsedQuote>?,
    ): BookDetailMessage {
        val book = bookRepository.getById(bookId) ?: return BookDetailMessage.GOODREADS_FAILED
        return runCatchingCancellable {
            val applySeries = metadata.series != null
            val applyDescription = !metadata.description.isNullOrBlank()
            val applyGenres = metadata.genres.isNotEmpty()
            if (applySeries || applyDescription || applyGenres) {
                bookRepository.updateMetadata(
                    id = bookId,
                    title = book.title,
                    author = book.author,
                    series = if (applySeries) metadata.series else book.series,
                    seriesNumber = if (applySeries) metadata.seriesNumber else book.seriesNumber,
                    description = if (applyDescription) metadata.description else book.description,
                    tagsCsv = if (applyGenres) book.tagsCsv.withGoodreadsGenres(metadata.genres) else book.tagsCsv,
                )
            }
            bookRepository.updateGoodreadsInfo(
                id = bookId,
                goodreadsUrl = metadata.canonicalUrl,
                rating = metadata.averageRating ?: book.goodreadsRating,
                ratingsCount = metadata.ratingsCount ?: book.goodreadsRatingsCount,
                originalPublicationYear = metadata.originalPublicationYear ?: book.originalPublicationYear,
            )
            // Cover and quotes only need the metadata already in hand, so both downloads run at once.
            val coverUrl = metadata.coverUrl
            val workId = metadata.workId
            _goodreadsImport.value = GoodreadsImportState.Working(GoodreadsImportStep.FETCHING_COVER_AND_QUOTES)
            val (coverBytes, quotes) = coroutineScope {
                val cover = async { coverUrl?.let { goodreadsMetadataFetcher.downloadCover(it) } }
                val fetchedQuotes = async { workId?.let { loadQuotes(it) } }
                cover.await() to fetchedQuotes.await()
            }
            val coverApplied = coverUrl == null || coverBytes?.let { applyGoodreadsCover(book, it) } != null
            // Null means the quotes page couldn't be read at all, as opposed to it simply having none new.
            val quotesAdded: Int? = when {
                workId == null -> 0
                quotes == null -> null
                else -> addGoodreadsQuotes(bookId, quotes)
            }
            when {
                !coverApplied -> BookDetailMessage.GOODREADS_COVER_FAILED
                quotesAdded == null -> BookDetailMessage.GOODREADS_QUOTES_FAILED
                quotesAdded > 0 -> BookDetailMessage.GOODREADS_APPLIED_WITH_QUOTES(quotesAdded)
                else -> BookDetailMessage.GOODREADS_APPLIED
            }
        }.getOrElse { BookDetailMessage.GOODREADS_FAILED }
    }

    /**
     * Adds [quotes] exactly as a pasted-quotes import does ([importQuotes]) - popular-highlight underlines the reader
     * places in the text - minus any whose text the book already has, so importing the same link again is harmless.
     */
    private suspend fun addGoodreadsQuotes(bookId: Long, quotes: List<ParsedQuote>): Int {
        // Compared on letters and digits only, so curly-vs-straight quotes or spacing can't sneak a duplicate in.
        val known = annotationRepository.observeForBook(bookId).first()
            .mapTo(HashSet()) { quoteMatchKey(it.selectedText.orEmpty()) }
        val fresh = quotes.filter { quote ->
            val key = quoteMatchKey(quote.quoteText)
            key.isNotEmpty() && known.add(key)
        }
        if (fresh.isEmpty()) return 0
        val now = System.currentTimeMillis()
        annotationRepository.createAll(fresh.mapIndexed { index, quote -> quote.toPopularHighlight(bookId, index, now) })
        return fresh.size
    }

    /** [book] must carry root-relative paths; [bytes] is an already-validated image from Goodreads. */
    private suspend fun applyGoodreadsCover(book: Book, bytes: ByteArray) {
        val goodreadsPath = storageRoots.relativize(saveCover(bytes))
        val previousGoodreads = book.goodreadsCoverPath
        // Whatever was showing before stays switchable as "your cover", unless it was itself a Goodreads one.
        val custom = book.customCoverPath ?: book.coverPath?.takeIf { it != previousGoodreads }
        bookRepository.updateCoverAlternates(book.id, customCoverPath = custom, goodreadsCoverPath = goodreadsPath)
        bookRepository.updateCover(book.id, goodreadsPath)
        previousGoodreads?.takeIf { it != custom }?.let { storageRoots.resolve(it).delete() }
    }

    private fun String?.withGoodreadsGenres(genres: List<String>): String =
        (orEmpty().split(",").map { it.trim() } + genres.take(GoodreadsMaxGenreTags))
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .joinToString(",")

    private suspend fun removeLocalFileFromLibrary(bookId: Long): BookDetailMessage {
        val book = bookRepository.getById(bookId) ?: return BookDetailMessage.LOCAL_FILE_REMOVE_FAILED
        if (!book.canRemoveLocalFileFromDevice()) return BookDetailMessage.LOCAL_FILE_REMOVE_UNAVAILABLE
        return runCatchingCancellable {
            val localFile = storageRoots.resolve(book.filePath)
            val rootPath = storageRoots.rootDir.canonicalFile.toPath()
            val localPath = localFile.canonicalFile.toPath()
            if (!localPath.startsWith(rootPath)) return@runCatchingCancellable BookDetailMessage.LOCAL_FILE_REMOVE_FAILED
            if (localFile.exists() && !localFile.delete()) return@runCatchingCancellable BookDetailMessage.LOCAL_FILE_REMOVE_FAILED
            if (bookRepository.removeLocalFile(book.id)) {
                BookDetailMessage.LOCAL_FILE_REMOVED
            } else {
                BookDetailMessage.LOCAL_FILE_REMOVE_UNAVAILABLE
            }
        }.getOrElse { BookDetailMessage.LOCAL_FILE_REMOVE_FAILED }
    }

    private fun finishImportRow(rowId: String, result: ImportResult): ImportResult {
        updateImportRow(rowId, result.status)
        return result
    }

    private fun startImportProgress(candidates: List<ImportCandidate>) {
        _importProgress.value = ImportProgressState(
            rows = candidates.map { candidate ->
                ImportProgressRow(
                    id = candidate.id,
                    fileName = candidate.rowName,
                    status = ImportRowStatus.QUEUED,
                )
            },
            isRunning = candidates.isNotEmpty(),
        )
    }

    private fun markImportComplete() {
        _importProgress.update { state -> state?.copy(isRunning = false) }
    }

    private fun updateImportRow(rowId: String, status: ImportRowStatus) {
        _importProgress.update { state ->
            state?.copy(
                rows = state.rows.map { row ->
                    if (row.id == rowId) row.copy(status = status) else row
                },
            )
        }
    }

    private fun Book.withAbsolutePaths(): Book = copy(
        coverPath = coverPath?.let { storageRoots.resolve(it).absolutePath },
        filePath = filePath.takeIf { it.isNotBlank() }?.let { storageRoots.resolve(it).absolutePath }.orEmpty(),
        // An alternate whose file has gone (e.g. replaced along with the source file) drops out, so the UI
        // never offers a cover it can't show.
        customCoverPath = customCoverPath?.let { storageRoots.resolve(it) }?.takeIf { it.isFile }?.absolutePath,
        goodreadsCoverPath = goodreadsCoverPath?.let { storageRoots.resolve(it) }?.takeIf { it.isFile }?.absolutePath,
    )

    private fun Flow<List<Book>>.withAbsolutePaths(): Flow<List<Book>> =
        map { books -> books.map { it.withAbsolutePaths() } }

    private fun saveCover(bytes: ByteArray): File {
        val coverFile = File(storageRoots.coversDir, "${UUID.randomUUID()}.jpg")
        coverFile.writeBytes(bytes)
        return coverFile
    }

    private fun savePickedCover(contentResolver: ContentResolver, uri: Uri): File {
        val extension = displayNameOf(contentResolver, uri)
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .takeIf { it in SupportedCoverExtensions }
            ?: "jpg"
        val coverFile = File(storageRoots.coversDir, "${UUID.randomUUID()}.$extension")
        contentResolver.openInputStream(uri)?.use { input ->
            coverFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cover image could not be opened")
        return coverFile
    }

    private fun displayNameOf(contentResolver: ContentResolver, uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment.orEmpty()
    }

    private fun List<ImportResult>.summarize(): ImportSummary = ImportSummary(
        imported = count { it is ImportResult.Imported },
        duplicates = count { it is ImportResult.Duplicate },
        unsupported = count { it is ImportResult.Unsupported },
        failed = count { it is ImportResult.Failed },
    )
}

private fun Book.fileAssetReference(): CloudAssetReference? {
    val assetId = fileAssetId?.takeIf { it.isNotBlank() } ?: return null
    val assetSha256 = fileAssetSha256?.takeIf { it.isNotBlank() } ?: return null
    val assetSizeBytes = fileAssetSizeBytes ?: return null
    val assetUploadedAt = fileAssetUploadedAt ?: return null
    return runCatchingCancellable {
        CloudAssetReference(
            id = assetId,
            sha256 = assetSha256,
            sizeBytes = assetSizeBytes,
            uploadedAt = assetUploadedAt,
        )
    }.getOrNull()
}

private fun Book.coverAssetReference(): CloudAssetReference? {
    val assetId = coverAssetId?.takeIf { it.isNotBlank() } ?: return null
    val assetSha256 = coverAssetSha256?.takeIf { it.isNotBlank() } ?: return null
    val assetSizeBytes = coverAssetSizeBytes ?: return null
    val assetUploadedAt = coverAssetUploadedAt ?: return null
    return runCatchingCancellable {
        CloudAssetReference(
            id = assetId,
            sha256 = assetSha256,
            sizeBytes = assetSizeBytes,
            uploadedAt = assetUploadedAt,
        )
    }.getOrNull()
}

private fun Book.coverNeedsUpload(storageRoots: StorageRoots): Boolean {
    val coverPath = coverPath?.takeIf { it.isNotBlank() } ?: return false
    val coverFile = storageRoots.resolve(coverPath)
    if (!coverFile.isFile) return false
    return coverAssetSha256.isNullOrBlank() || runCatchingCancellable {
        Hashing.sha256(coverFile.readBytes()) != coverAssetSha256
    }.getOrDefault(true)
}

private fun Book.canRemoveLocalFileFromDevice(): Boolean =
    format != BookFormat.PHYSICAL &&
        fileAvailability == BookFileAvailability.LOCAL &&
        filePath.isNotBlank() &&
        !fileAssetId.isNullOrBlank() &&
        !fileAssetSha256.isNullOrBlank() &&
        fileAssetSizeBytes != null &&
        fileAssetUploadedAt != null

private fun PortableAsset.toCloudAssetReference(): CloudAssetReference? =
    runCatchingCancellable {
        CloudAssetReference(
            id = id,
            sha256 = sha256,
            sizeBytes = sizeBytes,
            uploadedAt = uploadedAt,
        )
    }.getOrNull()

private fun PortableCloudBook.toRecord(): CloudBookRecord? {
    val format = runCatchingCancellable { BookFormat.valueOf(format.uppercase()) }.getOrNull()
        ?: return null
    return CloudBookRecord(
        syncId = syncId,
        title = title,
        author = author,
        series = series,
        seriesNumber = seriesNumber,
        description = description,
        tagsCsv = tagsCsv,
        format = format,
        fileHash = fileHash,
        assetId = fileAsset.id,
        assetSha256 = fileAsset.sha256,
        assetSizeBytes = fileAsset.sizeBytes,
        assetUploadedAt = fileAsset.uploadedAt,
        coverAssetId = coverAsset?.id,
        coverAssetSha256 = coverAsset?.sha256,
        coverAssetSizeBytes = coverAsset?.sizeBytes,
        coverAssetUploadedAt = coverAsset?.uploadedAt,
        lastLocator = lastLocator,
        readingPercent = readingPercent,
        rating = rating,
        wordCount = wordCount,
        pageEstimate = pageEstimate,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastReadAt = lastReadAt,
        startedReadingAt = startedReadingAt,
        finishedReadingAt = finishedReadingAt,
        totalReadingSeconds = totalReadingSeconds,
        customFontSizePercent = customFontSizePercent,
        customLineHeight = customLineHeight,
        customFontFamily = customFontFamily,
        customSideMarginPercent = customSideMarginPercent,
        readNextAddedAt = readNextAddedAt,
    )
}



private fun ReadingSession.toPortable(bookSyncIdsByLocalId: Map<Long, String>): PortableReadingSession? {
    val bookSyncId = bookSyncIdsByLocalId[bookId] ?: return null
    return PortableReadingSession(
        syncId = syncId,
        bookSyncId = bookSyncId,
        startedAt = startedAt,
        endedAt = endedAt,
        durationSeconds = durationSeconds,
    )
}

private fun PortableReadingSession.toRecord(): CloudReadingSessionRecord =
    CloudReadingSessionRecord(
        syncId = syncId,
        bookSyncId = bookSyncId,
        startedAt = startedAt,
        endedAt = endedAt,
        durationSeconds = durationSeconds,
    )

private fun PortableWordLookupCounter.toRecord(): CloudWordLookupCounter = CloudWordLookupCounter(
    word = word,
    writerOrigin = writerOrigin,
    count = count,
    lastLookedUpAt = lastLookedUpAt,
)

private fun CloudWordLookupCounter.toPortable(): PortableWordLookupCounter = PortableWordLookupCounter(
    word = word,
    writerOrigin = writerOrigin,
    count = count,
    lastLookedUpAt = lastLookedUpAt,
)

private fun PortableAnnotation.toRecord(): AnnotationRecord? {
    val type = runCatchingCancellable { AnnotationType.valueOf(type.uppercase()) }.getOrNull() ?: return null
    return AnnotationRecord(
        syncId = syncId,
        bookSyncId = bookSyncId,
        type = type,
        colorKey = colorKey,
        locator = locator,
        chapterTitle = chapterTitle,
        chapterHref = chapterHref,
        selectedText = selectedText,
        readerNote = readerNote,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
    )
}


private fun PortableShelf.toRecord(): CloudShelfRecord = CloudShelfRecord(
    syncId = syncId,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun PortableShelfMembership.toRecord(): CloudShelfMembershipRecord = CloudShelfMembershipRecord(
    bookSyncId = bookSyncId,
    shelfSyncId = shelfSyncId,
    createdAt = createdAt,
)

private fun TombstoneEntity.isReadingProgressOnlyTombstone(): Boolean =
    entityType == TombstoneEntityType.READING_SESSION.value ||
        entityType == TombstoneEntityType.READING_PROGRESS_RESET.value

private fun TombstoneEntity.toPortable(): PortableTombstone = PortableTombstone(
    syncId = syncId,
    entityType = entityType,
    deletedAt = deletedAt,
)

private fun PortableVocabularyCard.toRecord(): CloudVocabularyCardRecord = CloudVocabularyCardRecord(
    syncId = syncId,
    word = word,
    definition = definition,
    sentence = sentence,
    bookSyncId = bookSyncId,
    bookTitle = bookTitle,
    createdAt = createdAt,
    lastReviewedAt = lastReviewedAt,
    known = known,
)

private data class GitHubSyncConfig(
    val owner: String,
    val repository: String,
    val branch: String,
    val token: String,
    val passphrase: String,
    val committerName: String,
) {
    val deviceSnapshotPath: String = "vayana/snapshots/${committerName.syncPathSegment()}.json"
}

private fun SettingsSnapshot.isGitHubSyncReady(): Boolean =
    githubSyncEnabled && gitHubSyncConfig()?.canBuildRepository() == true

private fun SettingsSnapshot.deviceLabelForSync(): String =
    kindleDeviceName.trim().ifBlank { "Vayana Sync" }

private fun SettingsSnapshot.gitHubSyncConfig(): GitHubSyncConfig? {
    val owner = githubOwner.trim()
    val repository = githubRepository.trim()
    val branch = githubBranch.trim()
    val token = githubToken.trim()
    val passphrase = githubSyncPassphrase
    if (owner.isBlank() || repository.isBlank() || branch.isBlank() || token.isBlank() || passphrase.isBlank()) {
        return null
    }
    return GitHubSyncConfig(
        owner = owner,
        repository = repository,
        branch = branch,
        token = token,
        passphrase = passphrase,
        committerName = deviceLabelForSync(),
    )
}

private fun ReadingProgressMergeResult.ConflictLocalKept.toPortableConflict(
    localDeviceLabel: String,
    remoteDeviceLabel: String?,
): PortableSyncConflict =
    PortableSyncConflict(
        type = "readingPosition",
        syncId = local.syncId,
        reason = reason.name,
        detectedAt = System.currentTimeMillis(),
        localDeviceLabel = localDeviceLabel,
        remoteDeviceLabel = remoteDeviceLabel?.takeIf { it.isNotBlank() },
        local = local.toPortableAlternative(),
        remote = remote.toPortableAlternative(),
    )

private fun ReadingProgressVersion.toPortableAlternative(): PortableReadingPositionAlternative =
    PortableReadingPositionAlternative(
        fileHash = fileHash,
        locator = locator,
        readingPercent = readingPercent,
        lastReadAt = lastReadAt,
        updatedAt = updatedAt,
    )

private fun GitHubSyncConfig.assetStore(): GitHubContentsAssetStore =
    GitHubContentsAssetStore(
        repository = GitHubRepository(
            owner = owner,
            name = repository,
            branch = branch,
        ),
        token = token,
        committerName = committerName,
        committerEmail = "$owner@users.noreply.github.com",
    )

private fun GitHubSyncConfig.canBuildRepository(): Boolean =
    runCatchingCancellable {
        GitHubRepository(
            owner = owner,
            name = repository,
            branch = branch,
        )
    }.isSuccess

private fun Throwable.syncFailureMessage(): String =
    when (this) {
        is GitHubAssetStoreException -> buildString {
            append(message ?: "GitHub request failed")
            responseBody.takeIf { it.isNotBlank() }?.let { body ->
                append(": ")
                append(body.take(MaxSyncFailureBodyChars))
            }
        }
        else -> message ?: "GitHub sync failed"
    }.take(MaxSyncFailureMessageChars)

private fun Throwable.isMissingRemoteSnapshot(): Boolean =
    this is GitHubAssetStoreException &&
        (statusCode == 404 || (statusCode == 409 && responseBody.contains("Git Repository is empty", ignoreCase = true)))

private fun Throwable.isGitHubConflict(): Boolean =
    this is GitHubAssetStoreException && statusCode == 409

private fun String.syncPathSegment(): String =
    trim()
        .lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-')
        .take(80)
        .ifBlank { "vayana-sync" }

private fun List<ImportProgressRow>.summarize(): ImportSummary = ImportSummary(
    imported = count { it.status == ImportRowStatus.IMPORTED },
    duplicates = count { it.status == ImportRowStatus.DUPLICATE },
    unsupported = count { it.status == ImportRowStatus.UNSUPPORTED },
    failed = count { it.status == ImportRowStatus.FAILED },
)

private fun List<Book>.filterBy(filter: LibraryFilter): List<Book> = when (filter) {
    LibraryFilter.ALL -> this
    LibraryFilter.READING -> filter { it.readingPercent > 0f && it.readingPercent < FinishedThreshold }
    LibraryFilter.FINISHED -> filter { it.readingPercent >= FinishedThreshold }
    LibraryFilter.NOT_STARTED -> filter { it.readingPercent <= 0f }
}

private fun List<Book>.filterByQuery(query: String): List<Book> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return this
    return filter { book ->
        book.title.contains(normalizedQuery, ignoreCase = true) ||
            book.author.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
            book.series.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
            book.seriesNumber.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
            book.tagsCsv.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
            book.description.orEmpty().contains(normalizedQuery, ignoreCase = true)
    }
}

private fun String.normalizedTagsCsv(): String? =
    split(",")
        .map { it.normalizedBookTag() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .take(MaxBookTags)
        .joinToString(", ")
        .take(MaxBookTagsCsvChars)
        .trimEnd(',', ' ')
        .ifBlank { null }

private fun String.normalizedBookTag(): String =
    map { if (Character.isISOControl(it)) ' ' else it }
        .joinToString("")
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(MaxBookTagChars)
        .trim()

private fun List<Book>.sortedBy(sort: LibrarySort): List<Book> = when (sort) {
    LibrarySort.IMPORT_DATE -> sortedWith(compareByDescending<Book> { it.createdAt }.thenBy { it.title.lowercase() })
    LibrarySort.TITLE -> sortedWith(compareBy<Book> { it.title.lowercase() }.thenByDescending { it.createdAt })
    LibrarySort.AUTHOR -> sortedWith(compareBy<Book> { it.author.orEmpty().lowercase() }.thenBy { it.title.lowercase() })
    LibrarySort.LAST_READ -> sortedWith(compareByDescending<Book> { it.lastReadAt ?: 0L }.thenBy { it.title.lowercase() })
    LibrarySort.PROGRESS -> sortedWith(compareByDescending<Book> { it.readingPercent }.thenBy { it.title.lowercase() })
}

private const val FinishedThreshold = 0.98f
private const val MaxSyncFailureBodyChars = 400
private const val MaxSyncFailureMessageChars = 600
private const val MaxProgressOnlyPushAttempts = 2
private const val MaxSnapshotMetadataSaveAttempts = 2
private const val MaxConcurrentCoverDownloads = 4
private const val GitHubSyncProgressTotalSteps = 5
private const val CloudBookDownloadProgressTotalSteps = 5
private const val MaxBookTags = 32
private const val MaxBookTagChars = 40
private const val MaxBookTagsCsvChars = 1_024

private val SupportedCoverExtensions = setOf("jpg", "jpeg", "png", "webp")
