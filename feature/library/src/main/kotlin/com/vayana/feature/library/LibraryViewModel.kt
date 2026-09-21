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
import com.vayana.core.backup.PortableSyncConflict
import com.vayana.core.backup.PortableWordLookupCounter
import com.vayana.core.backup.SnapshotExporter
import com.vayana.core.backup.PortableAnnotation
import com.vayana.core.backup.PortableBookAlias
import com.vayana.core.backup.PortableShelf
import com.vayana.core.backup.PortableShelfMembership
import com.vayana.core.backup.PortableTombstone
import com.vayana.core.backup.PortableVocabularyCard
import com.vayana.core.backup.parsePortableAnnotations
import com.vayana.core.backup.parsePortableBookAliases
import com.vayana.core.backup.parsePortableCloudBooks
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
import com.vayana.core.common.quoteMatchKey
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.database.model.normalizedBookTag
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
import com.vayana.core.database.model.isCommunityQuote
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
import com.vayana.core.datastore.settings.DefaultCoverSource
import com.vayana.core.datastore.settings.LaunchReadingProgressCheckMarker
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
import com.vayana.core.sync.snapshot.PortableSnapshotPublishProgress
import com.vayana.core.sync.snapshot.PortableSnapshotPublishStage
import com.vayana.core.sync.snapshot.RemotePortableSnapshotDocument
import com.vayana.core.sync.snapshot.RemotePortableSnapshotSlice
import com.vayana.core.sync.snapshot.getLatestPortableSnapshotDocumentUnlessSha
import com.vayana.core.sync.snapshot.getLatestPortableSnapshotDocument
import com.vayana.core.sync.snapshot.pushPortableReadingProgress
import com.vayana.core.sync.snapshot.putPortableSnapshotDocuments
import com.vayana.core.filesystem.BookFileImporter
import com.vayana.core.filesystem.ResolvedBooks
import com.vayana.core.filesystem.StorageRoots
import com.vayana.format.epub.EpubParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.vayana.core.sync.asset.CloudAssetDeletionProcessor
import kotlinx.coroutines.NonCancellable
import com.vayana.core.database.repository.isBookDeletion
import com.vayana.core.database.repository.isSyncedWithReadingProgress
import com.vayana.core.sync.RemoteBookDeletionNotices
import com.vayana.core.sync.SyncedBookDeletionApplier
import com.vayana.core.sync.asset.deletePendingAndLog
import com.vayana.core.sync.progress.ReadingProgressOnlySyncer

/** Result of one import batch, still kept for the final Snackbar summary. */
data class ImportSummary(val imported: Int, val duplicates: Int, val unsupported: Int, val failed: Int)

enum class ImportRowStatus { QUEUED, COPYING, PARSING, IMPORTED, DUPLICATE, UNSUPPORTED, FAILED }

private data class QuoteImportResult(val added: Int, val skipped: Int)

private const val QuoteLocatorPrefix = "quote"
private const val EpubMimeType = "application/epub+zip"
private const val GoodreadsQuoteLocatorPrefix = "goodreads-quote"

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
    data class QUOTES_IMPORTED(val added: Int, val skipped: Int) : BookDetailMessage
    data object MARKED_FINISHED : BookDetailMessage
    data object GOODREADS_APPLIED : BookDetailMessage
    data class GOODREADS_APPLIED_WITH_QUOTES(val quotesAdded: Int, val quotesSkipped: Int) : BookDetailMessage
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
    READING_PROGRESS_PULL_ONLY,
}

enum class LaunchProgressCheckOutcome {
    CHECKED,
    SKIPPED_FRESH_MARKER,
    SKIPPED_UNCHANGED_REMOTE,
    FAILED,
    NOT_APPLICABLE,
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
        val launchProgressCheckOutcome: LaunchProgressCheckOutcome = LaunchProgressCheckOutcome.NOT_APPLICABLE,
        val progressAppliedSyncIds: Set<String> = emptySet(),
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
    val stepProgress: Float? = null,
    val isRunning: Boolean = true,
) {
    val fraction: Float = (
        (completedSteps.toFloat() + (stepProgress ?: 0f).coerceIn(0f, 1f)) /
            totalSteps.toFloat()
        ).coerceIn(0f, 1f)
}

internal data class ReadingProgressMergeSummary(
    val applied: Int = 0,
    /** Sync ids of the books whose position this merge actually replaced with the remote one. */
    val appliedSyncIds: Set<String> = emptySet(),
    val conflicts: List<PortableSyncConflict> = emptyList(),
    val skipped: Int = 0,
    val failed: Boolean = false,
    val missingRemoteSnapshot: Boolean = false,
    val failureMessage: String? = null,
    val remoteSnapshot: RemotePortableSnapshotDocument? = null,
    val remoteSnapshotSha: String? = null,
    val skippedAlreadyChecked: Boolean = false,
) {
    val conflictCount: Int
        get() = conflicts.size
}

/**
 * Common shape shared by every per-entity cloud-merge summary, so the overall sync result can be folded
 * from a plain list of these instead of a hand-written N-term OR/sum/elvis chain naming every entity type
 * (previously duplicated, with drift, between the main sync path and the conflict-rebase retry path).
 */
internal interface SyncMergeOutcome {
    val failed: Boolean
    val skipped: Int
    val failureMessage: String?
}

private fun List<SyncMergeOutcome>.anyFailed(): Boolean = any { it.failed }
private fun List<SyncMergeOutcome>.totalSkipped(): Int = sumOf { it.skipped }
private fun List<SyncMergeOutcome>.firstFailureMessage(): String? = firstNotNullOfOrNull { it.failureMessage }

private fun PortableSnapshotPublishProgress.toSyncProgressDetail(): String =
    when (stage) {
        PortableSnapshotPublishStage.DEVICE_SNAPSHOT -> "Saving this device snapshot"
        PortableSnapshotPublishStage.SNAPSHOT_SLICE -> {
            val sliceNumber = completedDocuments.coerceAtLeast(1) - 1
            val sliceTotal = (totalDocuments - 2).coerceAtLeast(1)
            "Saving snapshot slice $sliceNumber of $sliceTotal: ${currentPath.toSnapshotSliceLabel()}"
        }
        PortableSnapshotPublishStage.LATEST_POINTER -> "Publishing latest snapshot"
        PortableSnapshotPublishStage.PRUNING -> "Cleaning old snapshot slices"
    }

private fun String?.toSnapshotSliceLabel(): String =
    when (this?.substringAfterLast('/')) {
        "annotations.json" -> "annotations"
        "shelves.json" -> "shelves"
        "shelf-memberships.json" -> "shelf memberships"
        "reading-sessions.json" -> "reading sessions"
        "vocabulary-cards.json" -> "vocabulary cards"
        "word-lookup-counters.json" -> "word lookup counters"
        "book-aliases.json" -> "book aliases"
        "tombstones.json" -> "deleted items"
        else -> "library metadata"
    }

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

internal data class GenericSyncMergeSummary(
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
    val newLocator: String?,
    val newPercent: Float,
    val previousUpdatedAt: Long,
    val newUpdatedAt: Long,
    val previousLastReadAt: Long?,
    val newLastReadAt: Long?,
)

data class BookProgressSyncOutcome(
    val result: GitHubSyncNowResult,
    val progressChange: BookProgressChange?,
)

enum class LibrarySort { IMPORT_DATE, TITLE, AUTHOR, LAST_READ, PROGRESS }

enum class LibrarySortDirection { ASCENDING, DESCENDING }

enum class LibraryFilter { ALL, READING, FINISHED, NOT_STARTED }

enum class LibraryGroupBy { NONE, AUTHOR, SERIES }

enum class LibraryViewMode { THUMBNAILS, LIST }

data class LibraryControls(
    val query: String = "",
    val sort: LibrarySort = LibrarySort.IMPORT_DATE,
    val sortDirection: LibrarySortDirection = LibrarySortDirection.DESCENDING,
    val filter: LibraryFilter = LibraryFilter.ALL,
    val groupBy: LibraryGroupBy = LibraryGroupBy.NONE,
    val viewMode: LibraryViewMode = LibraryViewMode.THUMBNAILS,
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
    private val cloudAssetDeletionProcessor: CloudAssetDeletionProcessor,
    private val snapshotExporter: SnapshotExporter,
    private val storageRoots: StorageRoots,
    private val recentlyDeletedAutoPurge: RecentlyDeletedAutoPurge,
    private val syncedBookDeletionApplier: SyncedBookDeletionApplier,
    private val permanentDeletionNotices: PermanentDeletionNotices,
    private val readingProgressOnlySyncer: ReadingProgressOnlySyncer,
    private val remoteBookDeletionNotices: RemoteBookDeletionNotices,
    private val resolvedBooks: ResolvedBooks,
    private val goodreadsMetadataFetcher: GoodreadsMetadataFetcher,
    private val coverImageFetcher: CoverImageFetcher,
    private val dispatchers: DispatcherProvider,
    private val diagnosticsLogStore: DiagnosticsLogStore,
    private val launchReadingProgressCoordinator: LaunchReadingProgressCoordinator,
    private val bookAliasDao: BookAliasDao,
    private val tombstoneDao: TombstoneDao,
    private val bookDao: BookDao,
    private val shelfDao: ShelfDao,
    private val vocabularyCardDao: VocabularyCardDao,
    private val annotationDao: AnnotationDao,
    private val incomingBookFiles: IncomingBookFiles,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val controls = MutableStateFlow(LibraryControls())

    init {
        // Books other apps opened or shared into Vayana are imported like any picked file, as soon as the library exists.
        viewModelScope.launch {
            incomingBookFiles.pending.filter { it.isNotEmpty() }.collect {
                val uris = incomingBookFiles.drain()
                if (uris.isNotEmpty()) importFiles(appContext.contentResolver, uris)
            }
        }
    }

    /** Active books with absolute paths, shared app-wide through [ResolvedBooks]. */
    val libraryBooks: StateFlow<List<Book>> =
        resolvedBooks.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val githubSyncReady: Flow<Boolean> = settingsRepository.snapshot
        .map { it.isGitHubSyncReady() }
        .distinctUntilChanged()

    private val finishedThreshold: Flow<Float> = settingsRepository.snapshot
        .map { it.finishedFraction }
        .distinctUntilChanged()

    val uiState: StateFlow<LibraryUiState> = combine(
        libraryBooks,
        controls,
        githubSyncReady,
        finishedThreshold,
    ) { books, controls, syncReady, finishedThreshold ->
        LibraryUiState(
            books = books
                .filterBy(controls.filter, finishedThreshold)
                .filterByQuery(controls.query)
                .sortedBy(controls.sort, controls.sortDirection),
            controls = controls,
            githubSyncReady = syncReady,
        )
    }
        // Filtering (including description search) and sorting run per keystroke and per DB change: keep them
        // off the main thread.
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

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

    private val _coverImageDownloadInProgress = MutableStateFlow(false)
    val coverImageDownloadInProgress: StateFlow<Boolean> = _coverImageDownloadInProgress

    val pendingLaunchProgressChange: StateFlow<BookProgressChange?> =
        launchReadingProgressCoordinator.pendingProgressChange

    private val remoteReadingProgressMerger = RemoteReadingProgressMerger(
        bookRepository = bookRepository,
        localDeviceLabel = { settingsRepository.snapshot.first().deviceLabelForSync() },
        mergeTombstones = { tombstonesJson, scope -> mergeCloudTombstones(tombstonesJson, scope) },
    )

    private val launchReadingProgressPull = LaunchReadingProgressPull(
        markers = object : LaunchProgressMarkerStore {
            override suspend fun read() = settingsRepository.launchReadingProgressCheckMarker.first()
            override suspend fun write(marker: LaunchReadingProgressCheckMarker) =
                settingsRepository.updateLaunchReadingProgressCheckMarker(marker)
        },
        recordDiagnostic = { bookId, message, detail ->
            diagnosticsLogStore.recordLaunchProgressCheck(bookId = bookId, message = message, detail = detail)
        },
    )

    init {
        // One launch check for the most recently read book, then stop observing. Staying subscribed kept the full
        // book query live for the whole session, re-running it on every page turn's position write (and, when a
        // check failed offline, retrying the network call on each of them).
        viewModelScope.launch {
            val bookId = combine(bookRepository.observeAll(), githubSyncReady) { books, syncReady ->
                if (!syncReady) {
                    null
                } else {
                    books.maxByOrNull { it.lastReadAt ?: 0L }
                        ?.takeIf { (it.lastReadAt ?: 0L) > 0L }
                        ?.id
                }
            }
                .flowOn(dispatchers.default)
                .first { it != null }
                ?: return@launch
            launchReadingProgressCoordinator.checkOnce(bookId) {
                syncReadingProgressForBook(bookId = bookId, silent = true)
            }
        }
    }

    fun updateQuery(query: String) {
        controls.update { it.copy(query = query) }
    }

    fun updateSort(sort: LibrarySort) {
        controls.update { controls ->
            if (controls.sort == sort) {
                controls.copy(sortDirection = controls.sortDirection.toggled())
            } else {
                controls.copy(sort = sort, sortDirection = LibrarySortDirection.ASCENDING)
            }
        }
    }

    fun updateFilter(filter: LibraryFilter) {
        controls.update { it.copy(filter = filter) }
    }

    fun updateGroupBy(groupBy: LibraryGroupBy) {
        controls.update { it.copy(groupBy = groupBy) }
    }

    fun updateViewMode(viewMode: LibraryViewMode) {
        controls.update { it.copy(viewMode = viewMode) }
    }

    /** Moves a book to Recently deleted. Finishes even when the calling screen closes straight away. */
    fun deleteBook(bookId: Long) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                bookRepository.softDelete(bookId)
                publishDeletion()
            }
        }
    }

    val remoteBookDeletions: StateFlow<List<String>> = remoteBookDeletionNotices.titles

    fun consumeRemoteBookDeletions(titles: List<String>) {
        remoteBookDeletionNotices.consume(titles)
    }

    val permanentDeletionNotice: StateFlow<PermanentDeletionNotice?> = permanentDeletionNotices.notice

    fun consumePermanentDeletionNotice(notice: PermanentDeletionNotice) {
        permanentDeletionNotices.consume(notice)
    }

    /**
     * Deletes a book permanently, everywhere: the row and its highlights, notes and history now, its local files right
     * after, and its cloud files on the next sync. Finishes even when the calling screen closes straight away.
     */
    fun deletePermanently(bookId: Long) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                val purged = recentlyDeletedAutoPurge.purgeBook(bookId) ?: return@withContext
                permanentDeletionNotices.post(
                    PermanentDeletionNotice(title = purged.title, cloudCopyPending = purged.queuedCloudAssetIds.isNotEmpty()),
                )
                publishDeletion()
            }
        }
    }

    /** Sends a book deletion to other devices straight away through the lightweight sync, when sync is set up. */
    private suspend fun publishDeletion() {
        readingProgressOnlySyncer.syncReadingProgress(force = true)
    }

    fun observeAnnotationCount(bookId: Long): Flow<Int> = annotationRepository.observeCountForBook(bookId)

    fun observeCommunityQuoteCount(bookId: Long): Flow<Int> =
        annotationRepository.observeCommunityQuoteCountForBook(bookId)

    val recentlyDeletedBooks: StateFlow<List<Book>> = bookRepository.observeDeleted()
        .withAbsolutePaths()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restoreBook(bookId: Long) {
        viewModelScope.launch { bookRepository.restore(bookId) }
    }

    val shelves: StateFlow<List<Shelf>> = shelfRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val landscapeTwoColumnLayout: StateFlow<Boolean> = settingsRepository.snapshot
        .map { it.landscapeTwoColumnLayout }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Queued books in queue order (earliest added = next up), taken from the library already in memory. */
    val readNextQueue: StateFlow<List<Book>> = libraryBooks
        .map { books -> books.filter { it.readNextAddedAt != null }.sortedBy { it.readNextAddedAt } }
        .distinctUntilChanged()
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

    private val readNextBumpedEvents = MutableSharedFlow<List<Book>>(extraBufferCapacity = 1)

    /** Books that left "Read next" because queueing another went past its cap. */
    val readNextBumped: SharedFlow<List<Book>> = readNextBumpedEvents.asSharedFlow()

    fun setReadNext(bookId: Long, queued: Boolean) {
        viewModelScope.launch {
            val bumped = bookRepository.setReadNext(bookId, queued)
            if (bumped.isNotEmpty()) readNextBumpedEvents.emit(bumped)
        }
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
                tagsCsv = tagsCsv,
            )
            _bookDetailMessage.value = BookDetailMessage.METADATA_SAVED
        }
    }

    fun updateReadingDates(bookId: Long, startedAt: Long?, finishedAt: Long?) {
        viewModelScope.launch { bookRepository.updateReadingDates(bookId, startedAt, finishedAt) }
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

    /** Fetches [link] and pauses on a preview; applying still uses the same import path after confirmation. */
    fun importFromGoodreads(bookId: Long, link: String) {
        if (_goodreadsImport.value is GoodreadsImportState.Working) return
        _goodreadsImport.value = GoodreadsImportState.Working(GoodreadsImportStep.FETCHING_BOOK)
        viewModelScope.launch {
            when (val result = goodreadsMetadataFetcher.fetch(link)) {
                is GoodreadsFetchResult.Failure -> _goodreadsImport.value = GoodreadsImportState.Failed(result.error)
                is GoodreadsFetchResult.Success -> _goodreadsImport.value = GoodreadsImportState.Preview(result.metadata)
            }
        }
    }

    /** What the in-app Goodreads browser captured: previewed like a direct import, with the quotes it already read. */
    fun importFromGoodreadsCapture(bookId: Long, metadata: GoodreadsBookMetadata, quotes: List<ParsedQuote>?) {
        if (_goodreadsImport.value is GoodreadsImportState.Working) return
        _goodreadsImport.value = GoodreadsImportState.Preview(metadata, quotes)
    }

    fun applyPendingGoodreads(bookId: Long, options: GoodreadsImportOptions = GoodreadsImportOptions()) {
        val preview = _goodreadsImport.value as? GoodreadsImportState.Preview ?: return
        if (!options.hasAnySelection) return
        _goodreadsImport.value = GoodreadsImportState.Working(
            step = GoodreadsImportStep.FETCHING_COVER_AND_QUOTES,
            quoteProgress = preview.capturedQuotes
                ?.takeIf { options.quotes }
                ?.size
                ?.let { GoodreadsQuoteProgress(processed = it, total = it) },
        )
        viewModelScope.launch {
            _bookDetailMessage.value = withContext(dispatchers.io) {
                applyGoodreadsInLibrary(
                    bookId = bookId,
                    metadata = preview.metadata,
                    options = options,
                    capturedQuotes = preview.capturedQuotes,
                    loadQuotes = goodreadsMetadataFetcher::fetchQuotes,
                )
            }
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

    internal fun replaceCoverFromWeb(bookId: Long, request: CoverImageRequest) {
        if (_coverImageDownloadInProgress.value) return
        _coverImageDownloadInProgress.value = true
        viewModelScope.launch {
            try {
                _bookDetailMessage.value = withContext(dispatchers.io) { replaceCoverFromWebInLibrary(bookId, request) }
            } finally {
                _coverImageDownloadInProgress.value = false
            }
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

            val result = withContext(dispatchers.io) { addGoodreadsQuotes(bookId, quotes) }
            _bookDetailMessage.value = BookDetailMessage.QUOTES_IMPORTED(result.added, result.skipped)
        }
    }

    /** A quote as a "popular" underline: the reader finds [ParsedQuote.quoteText] in the book and draws it there. */
    private fun ParsedQuote.toPopularHighlight(
        bookId: Long,
        index: Int,
        now: Long,
        locatorPrefix: String = QuoteLocatorPrefix,
    ): Annotation = Annotation(
        id = 0,
        bookId = bookId,
        type = AnnotationType.UNDERLINE,
        colorKey = "popular",
        locator = "$locatorPrefix:$index:${UUID.randomUUID()}",
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
        showProgress: Boolean = true,
        launchReadingProgressBookId: Long? = null,
    ): GitHubSyncNowResult {
        val result = runSyncNow(allowInitialSync, mode, showProgress, launchReadingProgressBookId)
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
    suspend fun syncReadingProgressForBook(
        bookId: Long,
        silent: Boolean = false,
    ): BookProgressSyncOutcome {
        val before = bookRepository.getById(bookId)
        val syncMode = if (silent) GitHubSyncMode.READING_PROGRESS_PULL_ONLY else GitHubSyncMode.READING_PROGRESS_ONLY
        val result = syncNow(mode = syncMode, showProgress = !silent, launchReadingProgressBookId = bookId.takeIf { silent })
        val after = bookRepository.getById(bookId)
        // Only offer to revert when there was a real prior position to protect - a book going
        // from "never opened" to some synced progress isn't a conflict, just filling in data.
        // The pull must also have applied remote progress to this very book: otherwise a before/after
        // difference is local reading that happened while the sync ran, not a synced change.
        val appliedSyncIds = (result as? GitHubSyncNowResult.Complete)?.progressAppliedSyncIds.orEmpty()
        val changed = if (
            before != null && after != null &&
            before.syncId in appliedSyncIds &&
            before.hasMeaningfulSyncedProgressChange(after)
        ) {
            BookProgressChange(
                bookId = bookId,
                previousLocator = before.lastLocator,
                previousPercent = before.readingPercent,
                newLocator = after.lastLocator,
                newPercent = after.readingPercent,
                previousUpdatedAt = before.updatedAt,
                newUpdatedAt = after.updatedAt,
                previousLastReadAt = before.lastReadAt,
                newLastReadAt = after.lastReadAt,
            )
        } else {
            null
        }
        if ((result as? GitHubSyncNowResult.Complete)?.launchProgressCheckOutcome == LaunchProgressCheckOutcome.CHECKED) {
            withContext(dispatchers.io) {
                diagnosticsLogStore.recordLaunchProgressCheck(
                    bookId = bookId,
                    message = buildString {
                        append(if (changed == null) "Launch progress check found no newer progress" else "Launch progress check found newer progress")
                        (after ?: before)?.title?.let { append(" for \"").append(it).append('"') }
                    },
                    detail = "progressUpdated=${result.progressUpdated}, conflicts=${result.conflicts}",
                )
            }
        }
        return BookProgressSyncOutcome(result, changed)
    }

    fun acknowledgePendingLaunchProgressChange(bookId: Long) {
        launchReadingProgressCoordinator.acknowledge(bookId)
    }

    /** Reverts to a specific book's pre-sync position - the "stay" side of the prompt above. */
    fun revertReadingProgress(
        bookId: Long,
        locator: String?,
        percent: Float,
        onReverted: () -> Unit = {},
    ) {
        val cfi = locator?.takeIf { it.isNotBlank() }
        if (cfi == null) {
            onReverted()
            return
        }
        viewModelScope.launch {
            try {
                bookRepository.updateLocator(bookId, cfi, percent, settingsRepository.snapshot.first().finishedFraction)
            } finally {
                onReverted()
            }
        }
    }

    private suspend fun runSyncNow(
        allowInitialSync: Boolean,
        mode: GitHubSyncMode,
        showProgress: Boolean,
        launchReadingProgressBookId: Long?,
    ): GitHubSyncNowResult = withContext(dispatchers.io) {
        updateSyncProgress(showProgress = showProgress, GitHubSyncProgressStep.PREPARING, "Checking GitHub settings", completedSteps = 0)
        val settings = settingsRepository.snapshot.first()
        if (!settings.githubSyncEnabled) {
            finishSyncProgress(showProgress = showProgress, GitHubSyncProgressStep.FAILED, "GitHub sync is turned off")
            return@withContext GitHubSyncNowResult.SyncDisabled
        }
        val syncConfig = settings.gitHubSyncConfig()
        if (syncConfig == null) {
            finishSyncProgress(showProgress = showProgress, GitHubSyncProgressStep.FAILED, "GitHub settings are incomplete")
            return@withContext GitHubSyncNowResult.ConfigIncomplete
        }
        val store = runCatchingCancellable { syncConfig.assetStore() }
            .getOrElse {
                finishSyncProgress(showProgress = showProgress, GitHubSyncProgressStep.FAILED, "GitHub settings are invalid")
                return@withContext GitHubSyncNowResult.ConfigIncomplete
            }
        updateSyncProgress(showProgress = showProgress, GitHubSyncProgressStep.READING_CLOUD, "Reading cloud library", completedSteps = 1)
        if (mode == GitHubSyncMode.READING_PROGRESS_PULL_ONLY) {
            val result = launchReadingProgressPull.run(
                bookId = launchReadingProgressBookId,
                syncTarget = syncConfig.launchReadingProgressSyncTarget(),
            ) { skipRemoteSnapshotSha ->
                // Silent launch check moves reading positions and applies books deleted on other devices; other deletions
                // (notes, shelves, words) wait for a user-started sync.
                pullReadingProgress(store, skipRemoteSnapshotSha, tombstones = TombstoneMergeScope.BOOK_DELETIONS)
            }
            if (result.pullFailed) {
                finishSyncProgress(showProgress = showProgress, GitHubSyncProgressStep.FAILED, "Cloud progress could not be read")
            }
            return@withContext result
        }
        val progressMerge = pullReadingProgress(store)
        if (progressMerge.failed) {
            finishSyncProgress(showProgress = showProgress, GitHubSyncProgressStep.FAILED, "Cloud progress could not be read")
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
            finishSyncProgress(showProgress = showProgress, GitHubSyncProgressStep.FAILED, "Cloud progress could not be found")
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
            finishSyncProgress(showProgress = showProgress, GitHubSyncProgressStep.FAILED, "Cloud progress needs confirmation")
            return@withContext GitHubSyncNowResult.InitialSyncConfirmationRequired(progressMerge.failureMessage)
        }
        if (mode == GitHubSyncMode.READING_PROGRESS_ONLY) {
            updateSyncProgress(
                showProgress = showProgress,
                step = GitHubSyncProgressStep.SAVING_SNAPSHOT,
                detail = "Saving reading progress",
                completedSteps = 4,
                progressUpdated = progressMerge.applied,
            )
            val progressPush = pushReadingProgressOnly(remoteSnapshot = progressMerge.remoteSnapshot, store = store)
            if (!progressPush.failed) deletePendingCloudAssets(store)
            finishSyncProgress(
                showProgress = showProgress,
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
                progressAppliedSyncIds = progressMerge.appliedSyncIds,
            )
        }
        updateSyncProgress(
            showProgress = showProgress,
            step = GitHubSyncProgressStep.ADDING_CLOUD_BOOKS,
            detail = "Adding cloud books",
            completedSteps = 2,
            progressUpdated = progressMerge.applied,
        )
        val remoteSnapshot = progressMerge.remoteSnapshot
        // Every slice is merged below; download them together instead of one after another.
        remoteSnapshot?.prefetch()
        val tombstoneMerge = if (remoteSnapshot == null) {
            GenericSyncMergeSummary()
        } else {
            mergeCloudTombstones(remoteSnapshot.jsonFor(RemotePortableSnapshotSlice.Tombstones))
        }
        val cloudLibraryMerge = if (remoteSnapshot == null) {
            CloudLibraryMergeSummary()
        } else {
            mergeCloudLibrary(remoteSnapshot.jsonFor(RemotePortableSnapshotSlice.Books), store)
        }
        val bookAliasMerge = if (remoteSnapshot == null) {
            GenericSyncMergeSummary()
        } else {
            mergeCloudBookAliases(remoteSnapshot.jsonFor(RemotePortableSnapshotSlice.BookAliases))
        }
        val shelfMerge = if (remoteSnapshot == null) {
            GenericSyncMergeSummary()
        } else {
            mergeCloudShelves(remoteSnapshot.jsonFor(RemotePortableSnapshotSlice.Shelves))
        }
        // Must run after cloudLibraryMerge/shelfMerge: records whose book or shelf was just
        // created above can only resolve sync ids to local rows once those rows exist.
        val shelfMembershipMerge = if (remoteSnapshot == null) {
            GenericSyncMergeSummary()
        } else {
            mergeCloudShelfMemberships(remoteSnapshot.jsonFor(RemotePortableSnapshotSlice.ShelfMemberships))
        }
        val vocabularyCardMerge = if (remoteSnapshot == null) {
            GenericSyncMergeSummary()
        } else {
            mergeCloudVocabularyCards(remoteSnapshot.jsonFor(RemotePortableSnapshotSlice.VocabularyCards))
        }
        val readingSessionMerge = if (remoteSnapshot == null) {
            ReadingSessionMergeSummary()
        } else {
            mergeCloudReadingSessions(remoteSnapshot.jsonFor(RemotePortableSnapshotSlice.ReadingSessions))
        }
        val wordLookupCounterMerge = if (remoteSnapshot == null) {
            WordLookupCounterMergeSummary()
        } else {
            mergeCloudWordLookupCounters(remoteSnapshot.jsonFor(RemotePortableSnapshotSlice.WordLookupCounters))
        }
        val annotationMerge = if (remoteSnapshot == null) {
            AnnotationMergeSummary()
        } else {
            mergeCloudAnnotations(remoteSnapshot.jsonFor(RemotePortableSnapshotSlice.Annotations))
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
            showProgress = showProgress,
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
                showProgress = showProgress,
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
                showProgress = showProgress,
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
            showProgress = showProgress,
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
        val snapshotProgress: (PortableSnapshotPublishProgress) -> Unit = { progress ->
            updateSyncProgress(
                showProgress = showProgress,
                step = GitHubSyncProgressStep.SAVING_SNAPSHOT,
                detail = progress.toSyncProgressDetail(),
                completedSteps = 4,
                uploadedBooks = uploaded,
                failedBooks = failed,
                uploadedCovers = uploadedCovers,
                progressUpdated = progressMerge.applied,
                cloudBooksCreated = cloudLibraryMerge.created,
                cloudBooksUpdated = cloudLibraryMerge.updated,
                downloadedCovers = cloudLibraryMerge.coversDownloaded,
                stepProgress = progress.fraction,
            )
        }
        val metadataSave = saveMetadataSnapshotWithRebase(
            store = store,
            syncConfig = syncConfig,
            expectedSha = progressMerge.remoteSnapshotSha,
            previousSnapshot = progressMerge.remoteSnapshot,
            conflicts = progressMerge.conflicts,
            onSnapshotProgress = snapshotProgress,
        )
        if (metadataSave.synced) deletePendingCloudAssets(store)
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
            showProgress = showProgress,
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

    private suspend fun pullReadingProgress(
        store: GitHubContentsAssetStore,
        skipRemoteSnapshotSha: String? = null,
        tombstones: TombstoneMergeScope = TombstoneMergeScope.ALL,
    ): ReadingProgressMergeSummary =
        runCatchingCancellable {
            val remoteDocument = store.getLatestPortableSnapshotDocumentUnlessSha(skipSha = skipRemoteSnapshotSha)
                ?: return@runCatchingCancellable ReadingProgressMergeSummary(
                    remoteSnapshotSha = skipRemoteSnapshotSha,
                    skippedAlreadyChecked = true,
                )
            remoteReadingProgressMerger.merge(remoteDocument, tombstones)
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

    private suspend fun mergeCloudTombstones(
        snapshotJson: String,
        scope: TombstoneMergeScope = TombstoneMergeScope.ALL,
    ): GenericSyncMergeSummary =
        runCatchingCancellable {
            var created = 0
            var appliedDeletes = 0
            var skipped = 0
            val deletedBookTitles = mutableListOf<String>()
            for (remote in parsePortableTombstones(snapshotJson)) {
                if (scope == TombstoneMergeScope.BOOK_DELETIONS && !isBookDeletion(remote.entityType)) continue
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
                appliedDeletes += applyCloudTombstone(remote, deletedBookTitles)
            }
            remoteBookDeletionNotices.post(deletedBookTitles)
            GenericSyncMergeSummary(created = created, skipped = skipped, appliedDeletes = appliedDeletes)
        }.getOrElse { throwable ->
            GenericSyncMergeSummary(failed = true, failureMessage = throwable.syncFailureMessage())
        }

    private suspend fun applyCloudTombstone(tombstone: PortableTombstone, deletedBookTitles: MutableList<String>): Int {
        // Unrecognized entityType (e.g. a newer app version's tombstone kind synced down): leave it stored for
        // when this device updates, apply nothing now. TombstoneEntityType.fromValue's null return, plus the
        // exhaustive `when` below with no `else`, means a *known* type added later fails to compile here until
        // handled, instead of silently falling through to 0 the way an `else` branch would.
        val type = TombstoneEntityType.fromValue(tombstone.entityType) ?: return 0
        val entity = TombstoneEntity(syncId = tombstone.syncId, entityType = tombstone.entityType, deletedAt = tombstone.deletedAt)
        return when (type) {
            // Moves the book to Recently deleted unless it was restored here after the delete; reading doesn't count.
            // Deleted permanently on another device (BOOK_PURGE): applies over any local changes, including the book's files.
            TombstoneEntityType.BOOK, TombstoneEntityType.BOOK_PURGE -> {
                deletedBookTitles += syncedBookDeletionApplier.apply(tombstone) ?: return 0
                1
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

    /** Removes permanently deleted books' cloud files, now that the snapshot carrying their purge tombstones is published. */
    private suspend fun deletePendingCloudAssets(store: GitHubContentsAssetStore) = withContext(dispatchers.io) {
        cloudAssetDeletionProcessor.deletePendingAndLog(store, diagnosticsLogStore, source = "LibraryViewModel")
    }

    private suspend fun saveMetadataSnapshotWithRebase(
        store: GitHubContentsAssetStore,
        syncConfig: GitHubSyncConfig,
        expectedSha: String?,
        previousSnapshot: RemotePortableSnapshotDocument?,
        conflicts: List<PortableSyncConflict>,
        onSnapshotProgress: (PortableSnapshotPublishProgress) -> Unit = {},
    ): SnapshotMetadataSaveResult {
        var latestExpectedSha = expectedSha
        var latestPreviousSnapshot = previousSnapshot
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
                val snapshot = snapshotExporter.export()
                    .copy(syncConflicts = latestConflicts)
                store.putPortableSnapshotDocuments(
                    snapshot = snapshot,
                    deviceSnapshotPath = syncConfig.deviceSnapshotPath,
                    expectedLatestSha = latestExpectedSha,
                    diagnosticsLogStore = diagnosticsLogStore,
                    onProgress = onSnapshotProgress,
                    previous = latestPreviousSnapshot,
                )
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

            val currentDocument = runCatchingCancellable { store.getLatestPortableSnapshotDocument() }
                .getOrElse { refetchFailure ->
                    return result(synced = false, failureMessage = refetchFailure.syncFailureMessage())
                }
            currentDocument.prefetch()
            val rebase = mergeRemoteSnapshotForMetadataRebase(currentDocument, store)
            rebaseBooksCreated += rebase.booksCreated
            rebaseBooksUpdated += rebase.booksUpdated
            rebaseSkipped += rebase.summary.skipped
            if (rebase.summary.failed) {
                rebaseFailed = true
                return result(synced = false, failureMessage = rebase.summary.failureMessage)
            }
            latestExpectedSha = currentDocument.sha
            latestPreviousSnapshot = currentDocument
            latestConflicts = latestConflicts + rebase.summary.conflicts
        }
        return result(synced = false, failureMessage = "Snapshot save failed")
    }

    private suspend fun mergeRemoteSnapshotForMetadataRebase(
        snapshot: RemotePortableSnapshotDocument,
        store: GitHubContentsAssetStore,
    ): SnapshotRebaseMerge = runCatchingCancellable {
        val tombstoneMerge = mergeCloudTombstones(snapshot.jsonFor(RemotePortableSnapshotSlice.Tombstones))
        val progressMerge = remoteReadingProgressMerger.mergeProgress(snapshot.jsonFor(RemotePortableSnapshotSlice.Books))
        val cloudLibraryMerge = mergeCloudLibrary(snapshot.jsonFor(RemotePortableSnapshotSlice.Books), store)
        val bookAliasMerge = mergeCloudBookAliases(snapshot.jsonFor(RemotePortableSnapshotSlice.BookAliases))
        val shelfMerge = mergeCloudShelves(snapshot.jsonFor(RemotePortableSnapshotSlice.Shelves))
        val shelfMembershipMerge = mergeCloudShelfMemberships(snapshot.jsonFor(RemotePortableSnapshotSlice.ShelfMemberships))
        val vocabularyCardMerge = mergeCloudVocabularyCards(snapshot.jsonFor(RemotePortableSnapshotSlice.VocabularyCards))
        val readingSessionMerge = mergeCloudReadingSessions(snapshot.jsonFor(RemotePortableSnapshotSlice.ReadingSessions))
        val wordLookupCounterMerge = mergeCloudWordLookupCounters(snapshot.jsonFor(RemotePortableSnapshotSlice.WordLookupCounters))
        val annotationMerge = mergeCloudAnnotations(snapshot.jsonFor(RemotePortableSnapshotSlice.Annotations))
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
        remoteSnapshot: RemotePortableSnapshotDocument?,
        store: GitHubContentsAssetStore,
    ): ReadingProgressOnlyPushSummary {
        var remote = remoteSnapshot ?: return ReadingProgressOnlyPushSummary()
        var lastFailure: Throwable? = null
        repeat(MaxProgressOnlyPushAttempts) { attemptIndex ->
            val attempt = runCatchingCancellable {
                val localBooks = bookRepository.observeAll().first()
                val bookSyncIdsByLocalId = localBooks.associate { it.id to it.syncId }
                val progressTombstones = tombstoneDao.getAll()
                    .filter { isSyncedWithReadingProgress(it.entityType) }
                    .map { it.toPortable() }
                val result = store.pushPortableReadingProgress(
                    remote = remote,
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
                ReadingProgressOnlyPushSummary(pushed = result.pushed)
            }
            attempt.onSuccess { result -> return result }
            val throwable = attempt.exceptionOrNull()
            lastFailure = throwable
            // Another device wrote to the snapshot between our pull and this push - re-fetch the
            // now-current document and re-apply our patch on top of it, rather than failing outright.
            if (throwable?.isGitHubConflict() != true || attemptIndex == MaxProgressOnlyPushAttempts - 1) {
                return ReadingProgressOnlyPushSummary(failed = true, failureMessage = throwable?.syncFailureMessage())
            }
            remote = runCatchingCancellable { store.getLatestPortableSnapshotDocument() }
                .getOrElse { refetchFailure ->
                    return ReadingProgressOnlyPushSummary(failed = true, failureMessage = refetchFailure.syncFailureMessage())
                }
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
        showProgress: Boolean = true,
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
        stepProgress: Float? = null,
    ) {
        if (!showProgress) return
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
            stepProgress = stepProgress?.coerceIn(0f, 1f),
        )
    }

    private fun finishSyncProgress(
        showProgress: Boolean = true,
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
        if (!showProgress) return
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
        // Files opened from a browser or mail app often have no usable extension; their MIME type still says EPUB.
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            .ifEmpty { if (contentResolver.getType(uri) == EpubMimeType) "epub" else "" }
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
                tagsCsv = metadata.tags.joinToString(",").ifBlank { null },
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
                // A re-exported calibre file may carry new tags (or a series the book lacked). Merge them in
                // rather than replace, so tags the user added by hand survive.
                val fileSeriesMissing = existingBook.series.isNullOrBlank() && metadata.series != null
                if (metadata.tags.isNotEmpty() || fileSeriesMissing) {
                    bookRepository.getById(bookId)?.let { current ->
                        bookRepository.updateMetadata(
                            id = bookId,
                            title = current.title,
                            author = current.author,
                            series = if (fileSeriesMissing) metadata.series else current.series,
                            seriesNumber = if (fileSeriesMissing) metadata.seriesNumber else current.seriesNumber,
                            description = current.description,
                            tagsCsv = current.tagsCsv.withMergedTags(metadata.tags),
                        )
                    }
                }
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
            applyCustomCover(existingBook, pickedCover)
            coverFile = null
            BookDetailMessage.COVER_UPDATED
        }.getOrElse { BookDetailMessage.COVER_FAILED }
            .also { result ->
                if (result != BookDetailMessage.COVER_UPDATED) {
                    coverFile?.delete()
                }
            }
    }

    private suspend fun replaceCoverFromWebInLibrary(bookId: Long, request: CoverImageRequest): BookDetailMessage {
        val existingBook = bookRepository.getById(bookId) ?: return BookDetailMessage.COVER_FAILED
        var coverFile: File? = null
        return runCatchingCancellable {
            val downloaded = coverImageFetcher.fetch(request) ?: error("Cover image could not be downloaded")
            val pickedCover = saveCover(downloaded.bytes, downloaded.extension)
            coverFile = pickedCover
            applyCustomCover(existingBook, pickedCover)
            coverFile = null
            BookDetailMessage.COVER_UPDATED
        }.getOrElse { BookDetailMessage.COVER_FAILED }
            .also { result ->
                if (result != BookDetailMessage.COVER_UPDATED) coverFile?.delete()
            }
    }

    private suspend fun applyCustomCover(existingBook: Book, coverFile: File) {
        val pickedPath = storageRoots.relativize(coverFile)
        bookRepository.updateCover(existingBook.id, pickedPath)
        // A downloaded or picked image becomes "your cover"; Goodreads stays available to switch back to.
        bookRepository.updateCoverAlternates(
            existingBook.id,
            customCoverPath = pickedPath,
            goodreadsCoverPath = existingBook.goodreadsCoverPath,
        )
        listOfNotNull(existingBook.coverPath, existingBook.customCoverPath)
            .distinct()
            .filter { it != existingBook.goodreadsCoverPath }
            .forEach { storageRoots.resolve(it).delete() }
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
     * Goodreads rating, year and link travel as optional sync metadata. Fields the page didn't have leave the book's
     * own values alone. Quotes are added last, as popular highlights, skipping any the book already has.
     */
    private suspend fun applyGoodreadsInLibrary(
        bookId: Long,
        metadata: GoodreadsBookMetadata,
        options: GoodreadsImportOptions,
        capturedQuotes: List<ParsedQuote>? = null,
        loadQuotes: suspend (workId: String, onProgress: (GoodreadsQuoteProgress) -> Unit) -> List<ParsedQuote>?,
    ): BookDetailMessage {
        val book = bookRepository.getById(bookId) ?: return BookDetailMessage.GOODREADS_FAILED
        return runCatchingCancellable {
            val applySeries = options.series && metadata.series != null
            val applyDescription = options.description && !metadata.description.isNullOrBlank()
            val applyGenres = options.genres && metadata.genres.isNotEmpty()
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
            if (options.goodreadsInfo) {
                bookRepository.updateGoodreadsInfo(
                    id = bookId,
                    goodreadsUrl = metadata.canonicalUrl,
                    rating = metadata.averageRating ?: book.goodreadsRating,
                    ratingsCount = metadata.ratingsCount ?: book.goodreadsRatingsCount,
                    originalPublicationYear = metadata.originalPublicationYear ?: book.originalPublicationYear,
                )
            }
            // Cover and quotes only need the metadata already in hand, so both downloads run at once.
            val coverUrl = metadata.coverUrl?.takeIf { options.cover }
            val workId = metadata.workId?.takeIf { options.quotes }
            val capturedQuoteProgress = capturedQuotes
                ?.takeIf { options.quotes }
                ?.size
                ?.let { GoodreadsQuoteProgress(processed = it, total = it) }
            _goodreadsImport.value = GoodreadsImportState.Working(
                GoodreadsImportStep.FETCHING_COVER_AND_QUOTES,
                capturedQuoteProgress,
            )
            val (coverBytes, quotes) = coroutineScope {
                val cover = async { coverUrl?.let { goodreadsMetadataFetcher.downloadCover(it) } }
                val fetchedQuotes = async {
                    capturedQuotes?.takeIf { options.quotes } ?: workId?.let { id ->
                        loadQuotes(id) { progress ->
                            _goodreadsImport.value = GoodreadsImportState.Working(
                                GoodreadsImportStep.FETCHING_COVER_AND_QUOTES,
                                progress,
                            )
                        }
                    }
                }
                cover.await() to fetchedQuotes.await()
            }
            val coverApplied = coverUrl == null || coverBytes?.let { bytes ->
                applyGoodreadsCover(book, bytes, settingsRepository.snapshot.first().defaultCoverSource)
            } != null
            // Null means the quotes page couldn't be read at all, as opposed to it simply having none new.
            val quotesResult: QuoteImportResult? = when {
                quotes != null -> refreshGoodreadsQuotes(bookId, quotes)
                !options.quotes -> QuoteImportResult(added = 0, skipped = 0)
                workId == null -> QuoteImportResult(added = 0, skipped = 0)
                else -> null
            }
            when {
                !coverApplied -> BookDetailMessage.GOODREADS_COVER_FAILED
                quotesResult == null -> BookDetailMessage.GOODREADS_QUOTES_FAILED
                quotesResult.added > 0 || quotesResult.skipped > 0 -> BookDetailMessage.GOODREADS_APPLIED_WITH_QUOTES(
                    quotesResult.added,
                    quotesResult.skipped,
                )
                else -> BookDetailMessage.GOODREADS_APPLIED
            }
        }.getOrElse { BookDetailMessage.GOODREADS_FAILED }
    }

    /**
     * Adds [quotes] exactly as a pasted-quotes import does ([importQuotes]) - popular-highlight underlines the reader
     * places in the text - minus any whose text the book already has, so importing the same link again is harmless.
     */
    private suspend fun addGoodreadsQuotes(bookId: Long, quotes: List<ParsedQuote>): QuoteImportResult {
        // Compared on letters and digits only, so curly-vs-straight quotes or spacing can't sneak a duplicate in.
        val known = annotationRepository.observeForBook(bookId).first()
            .mapTo(HashSet()) { quoteMatchKey(it.selectedText.orEmpty()) }
        val fresh = quotes.filter { quote ->
            val key = quoteMatchKey(quote.quoteText)
            key.isNotEmpty() && known.add(key)
        }
        if (fresh.isEmpty()) return QuoteImportResult(added = 0, skipped = quotes.size)
        val now = System.currentTimeMillis()
        annotationRepository.createAll(fresh.mapIndexed { index, quote -> quote.toPopularHighlight(bookId, index, now) })
        return QuoteImportResult(added = fresh.size, skipped = quotes.size - fresh.size)
    }

    /**
     * Refresh adds quotes exposed by pages that earlier imports never reached, and cleans old auto-imported
     * popular quotes that no longer pass the current length/language rules. Ordinary reader annotations are
     * never candidates. The legacy `quote:` prefix is included because older Goodreads imports predate the
     * dedicated locator prefix.
     */
    private suspend fun refreshGoodreadsQuotes(bookId: Long, quotes: List<ParsedQuote>): QuoteImportResult {
        val annotations = annotationRepository.observeForBook(bookId).first()
        val communityQuotes = annotations.filter(Annotation::isCommunityQuote)
        val eligible = classifyGoodreadsQuotes(
            texts = communityQuotes.map(Annotation::selectedText),
            acceptsQuote = ::isEligibleGoodreadsQuote,
        )
        val removedIds = communityQuotes.zip(eligible).filterNot { (_, keep) -> keep }.mapTo(HashSet()) { (annotation, _) -> annotation.id }
        annotationRepository.softDeleteAll(removedIds)

        val known = annotations.asSequence()
            .filterNot { it.id in removedIds }
            .mapTo(HashSet()) { quoteMatchKey(it.selectedText) }
        val fresh = quotes.filter { quote ->
            val key = quoteMatchKey(quote.quoteText)
            key.isNotEmpty() && known.add(key)
        }
        if (fresh.isEmpty()) return QuoteImportResult(added = 0, skipped = quotes.size)
        val now = System.currentTimeMillis()
        annotationRepository.createAll(
            fresh.mapIndexed { index, quote ->
                quote.toPopularHighlight(bookId, index, now, GoodreadsQuoteLocatorPrefix)
            },
        )
        return QuoteImportResult(added = fresh.size, skipped = quotes.size - fresh.size)
    }

    /** [book] must carry root-relative paths; [bytes] is an already-validated image from Goodreads. */
    private suspend fun applyGoodreadsCover(book: Book, bytes: ByteArray, defaultCoverSource: DefaultCoverSource) {
        val goodreadsPath = storageRoots.relativize(saveCover(bytes))
        val previousGoodreads = book.goodreadsCoverPath
        // Whatever was showing before stays switchable as "your cover", unless it was itself a Goodreads one.
        val custom = book.customCoverPath ?: book.coverPath?.takeIf { it != previousGoodreads }
        bookRepository.updateCoverAlternates(book.id, customCoverPath = custom, goodreadsCoverPath = goodreadsPath)
        val activeCover = when (defaultCoverSource) {
            DefaultCoverSource.YOURS -> custom ?: goodreadsPath
            DefaultCoverSource.GOODREADS -> goodreadsPath
        }
        bookRepository.updateCover(book.id, activeCover)
        previousGoodreads?.takeIf { it != custom }?.let { storageRoots.resolve(it).delete() }
    }

    private fun String?.withGoodreadsGenres(genres: List<String>): String =
        withMergedTags(genres.take(GoodreadsMaxGenreTags))

    private fun String?.withMergedTags(tags: List<String>): String =
        (orEmpty().split(",").map { it.normalizedBookTag() } + tags)
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

    private fun Book.withAbsolutePaths(): Book = resolvedBooks.resolve(this)

    private fun Flow<List<Book>>.withAbsolutePaths(): Flow<List<Book>> = resolvedBooks.resolveAll(this)

    private fun saveCover(bytes: ByteArray, extension: String = "jpg"): File {
        val safeExtension = extension.takeIf { it in SupportedCoverExtensions } ?: "jpg"
        val coverFile = File(storageRoots.coversDir, "${UUID.randomUUID()}.$safeExtension")
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
        readNextUpdatedAt = readNextUpdatedAt,
        deletionUpdatedAt = deletionUpdatedAt,
        goodreadsUrl = goodreadsUrl,
        goodreadsRating = goodreadsRating,
        goodreadsRatingsCount = goodreadsRatingsCount,
        originalPublicationYear = originalPublicationYear,
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
    dueAt = dueAt,
    intervalDays = intervalDays,
    easeFactor = easeFactor,
    repetitions = repetitions,
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

private fun GitHubSyncConfig.launchReadingProgressSyncTarget(): String =
    listOf(owner, repository, branch, deviceSnapshotPath).joinToString("/")


private fun DiagnosticsLogStore.recordLaunchProgressCheck(
    bookId: Long?,
    message: String,
    detail: String? = null,
) {
    record(
        category = DiagnosticCategory.SYNC,
        source = "LibraryViewModel.launchReadingProgress",
        message = bookId?.let { "$message for book $it" } ?: message,
        detail = detail,
    )
}

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

private fun List<Book>.filterBy(filter: LibraryFilter, finishedThreshold: Float): List<Book> = when (filter) {
    LibraryFilter.ALL -> this
    LibraryFilter.READING -> filter { it.readingPercent > 0f && it.readingPercent < finishedThreshold }
    LibraryFilter.FINISHED -> filter { it.readingPercent >= finishedThreshold }
    LibraryFilter.NOT_STARTED -> filter { it.readingPercent <= 0f }
}

internal fun Book.hasMeaningfulSyncedProgressChange(after: Book): Boolean {
    if (lastLocator.isNullOrBlank()) return false
    return normalizedProgressLocator() != after.normalizedProgressLocator() ||
        abs(readingPercent.coerceIn(0f, 1f) - after.readingPercent.coerceIn(0f, 1f)) >= ProgressPromptPercentEpsilon ||
        readingStatus() != after.readingStatus()
}

private fun Book.normalizedProgressLocator(): String? =
    lastLocator?.trim()?.takeIf { it.isNotBlank() }

private fun Book.readingStatus(): ReadingStatus = when {
    finishedReadingAt != null || readingPercent >= FinishedThreshold -> ReadingStatus.FINISHED
    !lastLocator.isNullOrBlank() || readingPercent > ProgressPromptPercentEpsilon || startedReadingAt != null -> ReadingStatus.READING
    else -> ReadingStatus.NOT_STARTED
}

private enum class ReadingStatus { NOT_STARTED, READING, FINISHED }

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

private fun LibrarySortDirection.toggled(): LibrarySortDirection = when (this) {
    LibrarySortDirection.ASCENDING -> LibrarySortDirection.DESCENDING
    LibrarySortDirection.DESCENDING -> LibrarySortDirection.ASCENDING
}

/** A book with its text sort keys lower-cased once, instead of on every comparison of the sort. */
private class SortEntry(val book: Book) {
    val title: String = book.title.lowercase()
    val author: String = book.author.orEmpty().lowercase()
}

private fun List<Book>.sortedBy(sort: LibrarySort, direction: LibrarySortDirection): List<Book> {
    if (size < 2) return this
    val ascending = direction == LibrarySortDirection.ASCENDING
    fun <T : Comparable<T>> primary(selector: (SortEntry) -> T): Comparator<SortEntry> =
        if (ascending) compareBy(selector) else compareByDescending(selector)
    val comparator = when (sort) {
        LibrarySort.IMPORT_DATE -> primary { it.book.createdAt }.thenBy { it.title }
        LibrarySort.TITLE -> primary { it.title }.thenByDescending { it.book.createdAt }
        LibrarySort.AUTHOR -> primary { it.author }.thenBy { it.title }
        LibrarySort.LAST_READ -> primary { it.book.lastReadAt ?: 0L }.thenBy { it.title }
        LibrarySort.PROGRESS -> primary { it.book.readingPercent }.thenBy { it.title }
    }
    return map(::SortEntry).sortedWith(comparator).map { it.book }
}

/** Only for spotting a synced status change worth a prompt; library filters use the user's finished percent. */
private const val FinishedThreshold = 0.98f
private const val MaxSyncFailureBodyChars = 400
private const val MaxSyncFailureMessageChars = 600
private const val MaxProgressOnlyPushAttempts = 2
private const val MaxSnapshotMetadataSaveAttempts = 2
private const val MaxConcurrentCoverDownloads = 4
private const val GitHubSyncProgressTotalSteps = 5
private const val CloudBookDownloadProgressTotalSteps = 5
private const val ProgressPromptPercentEpsilon = 0.001f

private val SupportedCoverExtensions = setOf("jpg", "jpeg", "png", "webp")
