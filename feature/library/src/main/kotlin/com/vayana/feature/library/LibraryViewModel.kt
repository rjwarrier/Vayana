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
import com.vayana.core.backup.PortableReadingPositionAlternative
import com.vayana.core.backup.PortableSyncConflict
import com.vayana.core.backup.SnapshotExporter
import com.vayana.core.backup.parsePortableCloudBooks
import com.vayana.core.backup.parsePortableReadingProgressSnapshot
import com.vayana.core.backup.toJsonString
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.common.Hashing
import com.vayana.core.common.QuoteParser
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.Shelf
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.CloudBookMergeResult
import com.vayana.core.database.repository.CloudBookRecord
import com.vayana.core.database.repository.ReadingProgressMergeResult
import com.vayana.core.database.repository.ReadingProgressVersion
import com.vayana.core.database.repository.ShelfRepository
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.sync.asset.CloudAssetReference
import com.vayana.core.sync.asset.CloudBookAssetTransfer
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Result of one import batch, still kept for the final Snackbar summary. */
data class ImportSummary(val imported: Int, val duplicates: Int, val unsupported: Int, val failed: Int)

enum class ImportRowStatus { QUEUED, COPYING, PARSING, IMPORTED, DUPLICATE, UNSUPPORTED, FAILED }

sealed interface BookDetailMessage {
    data object METADATA_SAVED : BookDetailMessage
    data object COVER_UPDATED : BookDetailMessage
    data object COVER_REMOVED : BookDetailMessage
    data object COVER_FAILED : BookDetailMessage
    data object SOURCE_REPLACED : BookDetailMessage
    data object SOURCE_DUPLICATE : BookDetailMessage
    data object SOURCE_UNSUPPORTED : BookDetailMessage
    data object SOURCE_FAILED : BookDetailMessage
    data class QUOTES_IMPORTED(val count: Int) : BookDetailMessage
    data object MARKED_FINISHED : BookDetailMessage
}

enum class CloudBookDownloadResult {
    DOWNLOADED,
    SYNC_DISABLED,
    CONFIG_INCOMPLETE,
    ASSET_MISSING,
    FAILED,
}

sealed interface GitHubSyncNowResult {
    data class Complete(
        val uploaded: Int,
        val failed: Int,
        val progressUpdated: Int,
        val cloudBooksCreated: Int,
        val cloudBooksUpdated: Int,
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
) {
    val conflictCount: Int
        get() = conflicts.size
}

private data class CloudLibraryMergeSummary(
    val created: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val coversDownloaded: Int = 0,
    val failed: Boolean = false,
    val failureMessage: String? = null,
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
    private val bookFileImporter: BookFileImporter,
    private val shelfRepository: ShelfRepository,
    private val settingsRepository: SettingsRepository,
    private val cloudBookAssetTransfer: CloudBookAssetTransfer,
    private val snapshotExporter: SnapshotExporter,
    private val storageRoots: StorageRoots,
    private val dispatchers: DispatcherProvider,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val controls = MutableStateFlow(LibraryControls())

    /** [Book.coverPath] and [Book.filePath] come back root-relative; resolve both before UI use. */
    private val allBooks: Flow<List<Book>> = bookRepository.observeAll()
        .map { books -> books.map { it.withAbsolutePaths() } }

    val libraryBooks: StateFlow<List<Book>> =
        allBooks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<LibraryUiState> = combine(libraryBooks, controls, settingsRepository.snapshot) { books, controls, settings ->
        LibraryUiState(
            books = books
                .filterBy(controls.filter)
                .filterByQuery(controls.query)
                .sortedBy(controls.sort),
            controls = controls,
            githubSyncReady = settings.isGitHubSyncReady(),
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
        .map { books -> books.map { it.withAbsolutePaths() } }
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
        .map { books -> books.map { it.withAbsolutePaths() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeBooksForShelf(shelfId: Long): StateFlow<List<Book>> = shelfRepository.observeBooksForShelf(shelfId)
        .map { books -> books.map { it.withAbsolutePaths() } }
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

    fun updateMetadata(bookId: Long, title: String, author: String, series: String, seriesNumber: String, description: String) {
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
            )
            _bookDetailMessage.value = BookDetailMessage.METADATA_SAVED
        }
    }

    fun markFinished(bookId: Long) {
        viewModelScope.launch {
            bookRepository.markFinished(bookId)
            _bookDetailMessage.value = BookDetailMessage.MARKED_FINISHED
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

    fun importQuotes(bookId: Long, quotesText: String) {
        viewModelScope.launch {
            val quotes = withContext(dispatchers.default) {
                QuoteParser.parse(quotesText)
            }
            if (quotes.isEmpty()) return@launch

            val now = System.currentTimeMillis()
            val annotations = quotes.mapIndexed { index, quote ->
                Annotation(
                    id = 0,
                    bookId = bookId,
                    type = AnnotationType.UNDERLINE,
                    colorKey = "popular",
                    locator = "quote:$index:${UUID.randomUUID()}",
                    chapterTitle = quote.sourceTitle ?: quote.author,
                    chapterHref = null,
                    selectedText = quote.quoteText,
                    readerNote = "${quote.highlightsCount} highlights",
                    createdAt = now,
                    updatedAt = now,
                )
            }
            withContext(dispatchers.io) {
                annotationRepository.createAll(annotations)
            }
            _bookDetailMessage.value = BookDetailMessage.QUOTES_IMPORTED(quotes.size)
        }
    }

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
        val reference = book.fileAssetReference() ?: return@withContext CloudBookDownloadResult.ASSET_MISSING
        val settings = settingsRepository.snapshot.first()
        if (!settings.githubSyncEnabled) return@withContext CloudBookDownloadResult.SYNC_DISABLED
        val syncConfig = settings.gitHubSyncConfig() ?: return@withContext CloudBookDownloadResult.CONFIG_INCOMPLETE

        runCatchingCancellable {
            val store = syncConfig.assetStore()
            val passphrase = syncConfig.passphrase.toCharArray()
            try {
                cloudBookAssetTransfer.downloadBookFile(
                    bookId = book.id,
                    reference = reference,
                    extension = book.format.name.lowercase(),
                    passphrase = passphrase,
                    store = store,
                )
            } finally {
                passphrase.fill('\u0000')
            }
            book.coverAssetReference()?.let { coverReference ->
                downloadCoverIfNeeded(book, coverReference, store)
            }
            CloudBookDownloadResult.DOWNLOADED
        }.getOrElse { CloudBookDownloadResult.FAILED }
    }

    suspend fun syncNow(allowInitialSync: Boolean = false): GitHubSyncNowResult = withContext(dispatchers.io) {
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
        if ((progressMerge.missingRemoteSnapshot || progressMerge.failed) && !allowInitialSync) {
            finishSyncProgress(GitHubSyncProgressStep.FAILED, "Cloud progress needs confirmation")
            return@withContext GitHubSyncNowResult.InitialSyncConfirmationRequired(progressMerge.failureMessage)
        }
        updateSyncProgress(
            step = GitHubSyncProgressStep.ADDING_CLOUD_BOOKS,
            detail = "Adding cloud books",
            completedSteps = 2,
            progressUpdated = progressMerge.applied,
        )
        val cloudLibraryMerge = if (progressMerge.missingRemoteSnapshot) {
            CloudLibraryMergeSummary()
        } else {
            mergeCloudLibrary(store)
        }
        val localBooks = bookRepository.observeAll().first()
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
        val metadataError = runCatchingCancellable {
            val snapshot = snapshotExporter.export()
            val snapshotBytes = snapshot
                .copy(syncConflicts = progressMerge.conflicts)
                .toJsonString()
                .toByteArray(Charsets.UTF_8)
            store.putSyncDocument(syncConfig.deviceSnapshotPath, snapshotBytes)
            store.putSyncDocument("vayana/snapshot-latest.json", snapshotBytes)
        }.exceptionOrNull()
        finishSyncProgress(
            step = if (metadataError == null && failed == 0 && !cloudLibraryMerge.failed) {
                GitHubSyncProgressStep.COMPLETE
            } else {
                GitHubSyncProgressStep.FAILED
            },
            detail = if (metadataError == null) "Sync finished" else "Snapshot save failed",
            uploadedBooks = uploaded,
            failedBooks = failed,
            uploadedCovers = uploadedCovers,
            progressUpdated = progressMerge.applied,
            cloudBooksCreated = cloudLibraryMerge.created,
            cloudBooksUpdated = cloudLibraryMerge.updated,
            downloadedCovers = cloudLibraryMerge.coversDownloaded,
        )
        GitHubSyncNowResult.Complete(
            uploaded = uploaded,
            failed = failed,
            progressUpdated = progressMerge.applied,
            cloudBooksCreated = cloudLibraryMerge.created,
            cloudBooksUpdated = cloudLibraryMerge.updated,
            conflicts = progressMerge.conflictCount,
            skipped = progressMerge.skipped + cloudLibraryMerge.skipped,
            pullFailed = (progressMerge.failed || cloudLibraryMerge.failed) && !allowInitialSync,
            metadataSynced = metadataError == null,
            failureMessage = metadataError?.syncFailureMessage() ?: cloudLibraryMerge.failureMessage,
        )
    }

    private suspend fun pullReadingProgress(store: GitHubContentsAssetStore): ReadingProgressMergeSummary =
        runCatchingCancellable {
            val snapshotJson = store.getSyncDocument("vayana/snapshot-latest.json").toString(Charsets.UTF_8)
            val remoteSnapshot = parsePortableReadingProgressSnapshot(snapshotJson)
            val localDeviceLabel = settingsRepository.snapshot.first().deviceLabelForSync()
            remoteSnapshot.progresses.fold(ReadingProgressMergeSummary()) { summary, progress ->
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

    private suspend fun mergeCloudLibrary(store: GitHubContentsAssetStore): CloudLibraryMergeSummary =
        runCatchingCancellable {
            val snapshotJson = store.getSyncDocument("vayana/snapshot-latest.json").toString(Charsets.UTF_8)
            parsePortableCloudBooks(snapshotJson).fold(CloudLibraryMergeSummary()) { summary, cloudBook ->
                val record = cloudBook.toRecord() ?: return@fold summary.copy(skipped = summary.skipped + 1)
                val mergeResult = bookRepository.mergeCloudBook(record)
                val coverDownloaded = downloadCloudCoverIfNeeded(cloudBook, record, store)
                when (mergeResult) {
                    CloudBookMergeResult.CREATED -> summary.copy(
                        created = summary.created + 1,
                        coversDownloaded = summary.coversDownloaded + coverDownloaded,
                    )
                    CloudBookMergeResult.UPDATED -> summary.copy(
                        updated = summary.updated + 1,
                        coversDownloaded = summary.coversDownloaded + coverDownloaded,
                    )
                    CloudBookMergeResult.SKIPPED -> summary.copy(
                        skipped = summary.skipped + 1,
                        coversDownloaded = summary.coversDownloaded + coverDownloaded,
                    )
                }
            }
        }.getOrElse { throwable ->
            CloudLibraryMergeSummary(failed = true, failureMessage = throwable.syncFailureMessage())
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
        val existingBook = bookRepository.getById(bookId)?.withAbsolutePaths() ?: return BookDetailMessage.COVER_FAILED
        var coverFile: File? = null
        return runCatchingCancellable {
            val pickedCover = savePickedCover(contentResolver, uri)
            coverFile = pickedCover
            bookRepository.updateCover(bookId, storageRoots.relativize(pickedCover))
            existingBook.coverPath?.let { File(it).delete() }
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
        val existingBook = bookRepository.getById(bookId)?.withAbsolutePaths() ?: return BookDetailMessage.COVER_FAILED
        return runCatchingCancellable {
            bookRepository.updateCover(bookId, null)
            existingBook.coverPath?.let { File(it).delete() }
            BookDetailMessage.COVER_REMOVED
        }.getOrElse { BookDetailMessage.COVER_FAILED }
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
    )

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
            book.description.orEmpty().contains(normalizedQuery, ignoreCase = true)
    }
}

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
private const val GitHubSyncProgressTotalSteps = 5

private val SupportedCoverExtensions = setOf("jpg", "jpeg", "png", "webp")
