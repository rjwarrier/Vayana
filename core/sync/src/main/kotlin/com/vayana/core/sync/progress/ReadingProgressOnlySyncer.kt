package com.vayana.core.sync.progress

import com.vayana.core.backup.PortableReadingProgress
import com.vayana.core.backup.PortableReadingProgressPatch
import com.vayana.core.backup.PortableReadNextState
import com.vayana.core.backup.PortableTombstone
import com.vayana.core.backup.parsePortableWordLookupCounters
import com.vayana.core.backup.parsePortableReadingSessions
import com.vayana.core.backup.parsePortableBookFileHashes
import com.vayana.core.backup.parsePortableTombstones
import com.vayana.core.backup.PortableWordLookupCounter
import com.vayana.core.backup.PortableReadingSession
import com.vayana.core.backup.parsePortableReadingProgressSnapshot
import com.vayana.core.database.dao.TombstoneDao
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.database.model.ReadingSession
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.TombstoneEntityType
import com.vayana.core.database.repository.WordLookupStatRepository
import com.vayana.core.database.repository.WordLookupCounterMergeResult
import com.vayana.core.database.repository.ReadingSessionRepository
import com.vayana.core.database.repository.ReadingSessionMergeResult
import com.vayana.core.database.repository.CloudWordLookupCounter
import com.vayana.core.database.repository.CloudReadingSessionRecord
import com.vayana.core.database.repository.ReadingProgressMergeResult
import com.vayana.core.database.repository.bookSyncIdOfReadingProgressReset
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.diagnostics.DiagnosticCategory
import com.vayana.core.diagnostics.DiagnosticsLogStore
import com.vayana.core.sync.asset.GitHubAssetStoreException
import com.vayana.core.sync.github.assetStore
import com.vayana.core.sync.github.gitHubSyncConfig
import com.vayana.core.sync.snapshot.RemotePortableSnapshotSlice
import com.vayana.core.sync.snapshot.getLatestPortableSnapshotDocument
import com.vayana.core.sync.snapshot.pushPortableReadingProgress
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.vayana.core.database.repository.isSyncedWithReadingProgress
import com.vayana.core.sync.RemoteBookDeletionNotices
import com.vayana.core.sync.SyncOperationCoordinator
import com.vayana.core.sync.SyncedBookDeletionApplier
import com.vayana.core.sync.asset.CloudAssetDeletionProcessor
import com.vayana.core.sync.asset.deletePendingAndLog

enum class ReadingProgressSyncStatus {
    /** Neither side had anything new; no network write was made. */
    NO_CHANGES,
    /** Remote had newer data for one or more books, applied locally. */
    PULLED,
    /** Local had newer data for one or more books, pushed to remote. */
    PUSHED,
    /** Both directions moved data. */
    SYNCED,
    THROTTLED,
    SYNC_DISABLED,
    CONFIG_INCOMPLETE,
    CLOUD_MISSING,
    FAILED,
}

data class ReadingProgressSyncResult(
    val status: ReadingProgressSyncStatus,
    val pushed: Int = 0,
    val pulled: Int = 0,
    val failureMessage: String? = null,
)

/**
 * Pushes local reading-position changes to the shared GitHub snapshot and pulls remote changes
 * back down, sharing a single document fetch between both directions. A cached remote SHA lets a
 * call skip re-parsing and re-applying the snapshot entirely when nothing has changed on either
 * side since the last successful run. Failures and other issues are journaled to
 * [DiagnosticsLogStore] so they can be inspected later from the app's diagnostics screen.
 */
@Singleton
class ReadingProgressOnlySyncer @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val bookRepository: BookRepository,
    private val readingSessionRepository: ReadingSessionRepository,
    private val wordLookupStatRepository: WordLookupStatRepository,
    private val tombstoneDao: TombstoneDao,
    private val dispatchers: DispatcherProvider,
    private val diagnosticsLogStore: DiagnosticsLogStore,
    private val syncedBookDeletionApplier: SyncedBookDeletionApplier,
    private val cloudAssetDeletionProcessor: CloudAssetDeletionProcessor,
    private val remoteBookDeletionNotices: RemoteBookDeletionNotices,
    private val syncOperationCoordinator: SyncOperationCoordinator,
) {
    private val lastSyncedAtMillis = AtomicLong(0L)
    private val lastAppliedRemoteSha = AtomicReference<String?>(null)

    /** Local state (positions, sessions, counters, tombstones) as of the last run that found nothing to push. */
    private val lastSettledLocalFingerprint = AtomicReference<Int?>(null)

    /** [force] skips the minimum interval between runs, e.g. to send a book deletion straight away. */
    suspend fun syncReadingProgress(force: Boolean = false): ReadingProgressSyncResult =
        syncOperationCoordinator.run {
            val result = runSync(force)
            if (result.status.isIssue) {
                diagnosticsLogStore.record(
                    category = DiagnosticCategory.SYNC,
                    source = "ReadingProgressOnlySyncer",
                    message = "${result.status}: ${result.failureMessage ?: "no further detail"}",
                )
            }
            result
        }

    private suspend fun runSync(force: Boolean): ReadingProgressSyncResult = withContext(dispatchers.io) {
        val now = System.currentTimeMillis()
        val previousSync = lastSyncedAtMillis.get()
        if ((!force && now - previousSync < MinSyncIntervalMillis) || !lastSyncedAtMillis.compareAndSet(previousSync, now)) {
            return@withContext ReadingProgressSyncResult(ReadingProgressSyncStatus.THROTTLED)
        }
        val settings = settingsRepository.snapshot.first()
        if (!settings.githubSyncEnabled) {
            return@withContext ReadingProgressSyncResult(ReadingProgressSyncStatus.SYNC_DISABLED)
        }
        val syncConfig = settings.gitHubSyncConfig()
            ?: return@withContext ReadingProgressSyncResult(ReadingProgressSyncStatus.CONFIG_INCOMPLETE)
        val store = runCatchingCancellable { syncConfig.assetStore() }
            .getOrElse { throwable ->
                return@withContext ReadingProgressSyncResult(
                    status = ReadingProgressSyncStatus.CONFIG_INCOMPLETE,
                    failureMessage = throwable.message,
                )
            }
        val localBooks = bookRepository.observeAll().first().filterNot { it.isHomeLibrary }
        val bookSyncIdsByLocalId = localBooks.associate { it.id to it.syncId }
        val bookFileHashesByLocalId = localBooks.associate { it.id to it.fileHash }
        val patches = localBooks.map { book ->
            PortableReadingProgressPatch(
                fileHash = book.fileHash,
                lastLocator = book.lastLocator,
                readingPercent = book.readingPercent,
                lastReadAt = book.lastReadAt,
                startedReadingAt = book.startedReadingAt,
                finishedReadingAt = book.finishedReadingAt,
                totalReadingSeconds = book.totalReadingSeconds,
                readNextAddedAt = book.readNextAddedAt,
                readNextUpdatedAt = book.readNextUpdatedAt,
                            readNextPinned = book.readNextPinned,
            )
        }
        val readingSessions = readingSessionRepository.observeAll().first()
            .mapNotNull { it.toPortable(bookSyncIdsByLocalId, bookFileHashesByLocalId) }
        val wordLookupCounters = wordLookupStatRepository.getAllForSync().map { it.toPortable() }
        val tombstones = tombstoneDao.getAll()
            .filter { tombstone -> isSyncedWithReadingProgress(tombstone.entityType) }
            .map { it.toPortable() }
        val localFingerprint = listOf(bookSyncIdsByLocalId, bookFileHashesByLocalId, patches, readingSessions, wordLookupCounters, tombstones).hashCode()

        var lastFailure: Throwable? = null
        repeat(MaxProgressOnlySyncAttempts) {
            val remoteSnapshot = runCatchingCancellable { store.getLatestPortableSnapshotDocument() }
                .getOrElse { throwable ->
                    return@withContext if (throwable.isMissingRemoteSnapshot()) {
                        ReadingProgressSyncResult(
                            status = ReadingProgressSyncStatus.CLOUD_MISSING,
                            failureMessage = throwable.syncFailureMessage(),
                        )
                    } else {
                        ReadingProgressSyncResult(
                            status = ReadingProgressSyncStatus.FAILED,
                            failureMessage = throwable.syncFailureMessage(),
                        )
                    }
                }
            val booksJson = remoteSnapshot.jsonFor(RemotePortableSnapshotSlice.Books)

            // Remote content is unchanged since the last time we fully processed it: whatever we
            // would apply locally, we already applied. Skip parsing and merging every book again.
            // Local imports can make previously unresolvable remote sessions match. Revisit an
            // unchanged remote snapshot whenever local state changed, or on an explicit retry.
            val remoteAlreadyApplied = !force && remoteSnapshot.sha == lastAppliedRemoteSha.get() &&
                localFingerprint == lastSettledLocalFingerprint.get()
            // Neither side moved since a run that had nothing to push: patching would only re-derive "no changes"
            // (and download slices to do it), so stop here.
            if (remoteAlreadyApplied && localFingerprint == lastSettledLocalFingerprint.get()) {
                return@withContext ReadingProgressSyncResult(status = ReadingProgressSyncStatus.NO_CHANGES)
            }
            var pulled = 0
            if (!remoteAlreadyApplied) {
                remoteSnapshot.prefetch(
                    listOf(
                        RemotePortableSnapshotSlice.Tombstones,
                        RemotePortableSnapshotSlice.ReadingSessions,
                        RemotePortableSnapshotSlice.WordLookupCounters,
                    ),
                )
                val parseAttempt = runCatchingCancellable { parsePortableReadingProgressSnapshot(booksJson) }
                parseAttempt.onFailure { throwable ->
                    return@withContext ReadingProgressSyncResult(
                        status = ReadingProgressSyncStatus.FAILED,
                        failureMessage = throwable.message,
                    )
                }
                pulled = pullRemoteTombstones(remoteSnapshot.jsonFor(RemotePortableSnapshotSlice.Tombstones))
                val parsedBooks = parseAttempt.getOrThrow()
                pulled += pullRemoteProgress(
                    progresses = parsedBooks.progresses,
                    syncedDeviceLabel = parsedBooks.deviceLabel,
                    syncedAt = parsedBooks.exportedAt,
                )
                pulled += pullRemoteReadNext(parsedBooks.readNextStates)
                val sessionPull = runCatchingCancellable {
                    pullRemoteReadingSessions(remoteSnapshot.jsonFor(RemotePortableSnapshotSlice.ReadingSessions),
                        parsePortableBookFileHashes(booksJson))
                }.getOrElse { throwable ->
                    // Never mark this snapshot applied when its reading history failed to import.
                    return@withContext ReadingProgressSyncResult(ReadingProgressSyncStatus.FAILED,
                        pulled = pulled, failureMessage = throwable.syncFailureMessage())
                }
                pulled += sessionPull
                pulled += pullRemoteWordLookupCounters(remoteSnapshot.jsonFor(RemotePortableSnapshotSlice.WordLookupCounters))
            }

            val attempt = runCatchingCancellable {
                val pushed = store.pushPortableReadingProgress(
                    remote = remoteSnapshot,
                    patches = patches,
                    exportedAt = System.currentTimeMillis(),
                    readingSessions = readingSessions,
                    wordLookupCounters = wordLookupCounters,
                    tombstones = tombstones,
                ).pushed
                if (pushed > 0) {
                    // The push changed the remote object, so our cached SHA is stale; force the
                    // next call to re-fetch and re-compare rather than assuming it's unchanged.
                    lastAppliedRemoteSha.set(null)
                    ReadingProgressSyncResult(
                        status = if (pulled > 0) ReadingProgressSyncStatus.SYNCED else ReadingProgressSyncStatus.PUSHED,
                        pushed = pushed,
                        pulled = pulled,
                    )
                } else {
                    lastAppliedRemoteSha.set(remoteSnapshot.sha)
                    // A pull may have just rewritten local rows, so only a pull-free run proves this state settled.
                    lastSettledLocalFingerprint.set(localFingerprint.takeIf { pulled == 0 })
                    ReadingProgressSyncResult(
                        status = if (pulled > 0) ReadingProgressSyncStatus.PULLED else ReadingProgressSyncStatus.NO_CHANGES,
                        pulled = pulled,
                    )
                }
            }
            attempt.onSuccess { result ->
                // Local tombstones, including permanent book deletes, are on the remote now, so their cloud files can go.
                runCatchingCancellable {
                    cloudAssetDeletionProcessor.deletePendingAndLog(store, diagnosticsLogStore, source = "ReadingProgressOnlySyncer")
                }
                return@withContext result
            }
            val throwable = attempt.exceptionOrNull()
            lastFailure = throwable
            if (throwable?.isGitHubConflict() != true) {
                return@withContext ReadingProgressSyncResult(
                    status = ReadingProgressSyncStatus.FAILED,
                    failureMessage = throwable?.syncFailureMessage(),
                )
            }
        }

        ReadingProgressSyncResult(
            status = ReadingProgressSyncStatus.FAILED,
            failureMessage = lastFailure?.syncFailureMessage(),
        )
    }

    /** Applies each book independently so one bad or unexpectedly-failing row doesn't block the rest. */
    private suspend fun pullRemoteProgress(
        progresses: List<PortableReadingProgress>,
        syncedDeviceLabel: String?,
        syncedAt: Long?,
    ): Int {
        var applied = 0
        for (progress in progresses) {
            val attempt = runCatchingCancellable {
                bookRepository.applySyncedReadingProgress(
                    syncId = progress.syncId,
                    fileHash = progress.fileHash,
                    locator = progress.lastLocator,
                    readingPercent = progress.readingPercent,
                    lastReadAt = progress.lastReadAt,
                    remoteUpdatedAt = progress.updatedAt,
                    startedReadingAt = progress.startedReadingAt,
                    finishedReadingAt = progress.finishedReadingAt,
                    totalReadingSeconds = progress.totalReadingSeconds,
                    syncedDeviceLabel = syncedDeviceLabel,
                    syncedAt = syncedAt,
                )
            }
            attempt.onSuccess { result -> if (result is ReadingProgressMergeResult.AppliedRemote) applied += 1 }
            attempt.onFailure { throwable ->
                diagnosticsLogStore.record(
                    category = DiagnosticCategory.SYNC,
                    source = "ReadingProgressOnlySyncer.pullRemoteProgress",
                    message = "Failed to apply remote progress for one book: ${throwable.message}",
                )
            }
        }
        return applied
    }

    private suspend fun pullRemoteReadNext(states: List<PortableReadNextState>): Int {
        var applied = 0
        for (state in states) {
            val attempt = runCatchingCancellable {
                bookRepository.applySyncedReadNext(
                    syncId = state.syncId,
                    fileHash = state.fileHash,
                    addedAt = state.addedAt,
                    remoteUpdatedAt = state.updatedAt,
                    pinned = state.pinned,
                )
            }
            attempt.onSuccess { changed -> if (changed) applied += 1 }
            attempt.onFailure { throwable ->
                diagnosticsLogStore.record(
                    category = DiagnosticCategory.SYNC,
                    source = "ReadingProgressOnlySyncer.pullRemoteReadNext",
                    message = "Failed to apply remote Read Next state: ${throwable.message}",
                )
            }
        }
        return applied
    }

    private suspend fun pullRemoteReadingSessions(jsonText: String, bookFileHashes: Map<String, String>): Int {
        var merged = 0
        for (session in parsePortableReadingSessions(jsonText)) {
            val record = session.toRecord().copy(bookFileHash = session.bookFileHash ?: bookFileHashes[session.bookSyncId])
            if (readingSessionRepository.mergeCloudSession(record) != ReadingSessionMergeResult.SKIPPED) merged += 1
        }
        return merged
    }

    private suspend fun pullRemoteTombstones(jsonText: String): Int {
        var applied = 0
        val tombstones = runCatchingCancellable { parsePortableTombstones(jsonText) }
            .getOrElse { throwable ->
                diagnosticsLogStore.record(
                    category = DiagnosticCategory.SYNC,
                    source = "ReadingProgressOnlySyncer.pullRemoteTombstones",
                    message = "Failed to parse remote tombstones: ${throwable.message}",
                )
                return 0
            }
        val deletedBookTitles = mutableListOf<String>()
        for (tombstone in tombstones) {
            val attempt = runCatchingCancellable {
                if (!upsertRemoteTombstoneIfNewer(tombstone)) return@runCatchingCancellable 0
                when (tombstone.entityType) {
                    TombstoneEntityType.READING_SESSION.value -> {
                        readingSessionRepository.deleteBySyncId(tombstone.syncId)
                    }
                    TombstoneEntityType.READING_PROGRESS_RESET.value -> {
                        val bookSyncId = bookSyncIdOfReadingProgressReset(tombstone.syncId) ?: return@runCatchingCancellable 0
                        bookRepository.applyReadingStatsReset(bookSyncId, resetAt = tombstone.deletedAt)
                    }
                    TombstoneEntityType.BOOK.value, TombstoneEntityType.BOOK_PURGE.value -> {
                        deletedBookTitles += syncedBookDeletionApplier.apply(tombstone) ?: return@runCatchingCancellable 0
                        1
                    }
                    else -> 0
                }
            }
            attempt.onSuccess { count -> if (count > 0) applied += count }
            attempt.onFailure { throwable ->
                diagnosticsLogStore.record(
                    category = DiagnosticCategory.SYNC,
                    source = "ReadingProgressOnlySyncer.pullRemoteTombstones",
                    message = "Failed to apply remote tombstone: ${throwable.message}",
                )
            }
        }
        remoteBookDeletionNotices.post(deletedBookTitles)
        return applied
    }

    private suspend fun upsertRemoteTombstoneIfNewer(tombstone: PortableTombstone): Boolean {
        val existing = tombstoneDao.findBySyncId(tombstone.syncId)
        if (existing != null && existing.deletedAt > tombstone.deletedAt) return false
        if (existing == null || existing.deletedAt < tombstone.deletedAt || existing.entityType != tombstone.entityType) {
            tombstoneDao.upsert(tombstone.toEntity())
        }
        return true
    }

    private suspend fun pullRemoteWordLookupCounters(jsonText: String): Int {
        var merged = 0
        val counters = runCatchingCancellable { parsePortableWordLookupCounters(jsonText) }
            .getOrElse { throwable ->
                diagnosticsLogStore.record(
                    category = DiagnosticCategory.SYNC,
                    source = "ReadingProgressOnlySyncer.pullRemoteWordLookupCounters",
                    message = "Failed to parse remote word lookup counters: ${throwable.message}",
                )
                return 0
            }
        for (counter in counters) {
            val attempt = runCatchingCancellable { wordLookupStatRepository.mergeCloudCounter(counter.toRecord()) }
            attempt.onSuccess { result -> if (result == WordLookupCounterMergeResult.MERGED) merged += 1 }
            attempt.onFailure { throwable ->
                diagnosticsLogStore.record(
                    category = DiagnosticCategory.SYNC,
                    source = "ReadingProgressOnlySyncer.pullRemoteWordLookupCounters",
                    message = "Failed to apply remote word lookup counter: ${throwable.message}",
                )
            }
        }
        return merged
    }
}

private fun ReadingSession.toPortable(bookSyncIdsByLocalId: Map<Long, String>, bookFileHashesByLocalId: Map<Long, String>): PortableReadingSession? {
    val bookSyncId = bookSyncIdsByLocalId[bookId] ?: return null
    return PortableReadingSession(
        syncId = syncId,
        bookSyncId = bookSyncId,
        bookFileHash = bookFileHashesByLocalId[bookId],
        startedAt = startedAt,
        endedAt = endedAt,
        durationSeconds = durationSeconds,
        startPage = startPage,
        endPage = endPage,
        activeIntervals = activeIntervals,
    )
}

private fun PortableReadingSession.toRecord(): CloudReadingSessionRecord = CloudReadingSessionRecord(
    syncId = syncId,
    bookSyncId = bookSyncId,
    bookFileHash = bookFileHash,
    startedAt = startedAt,
    endedAt = endedAt,
    durationSeconds = durationSeconds,
    startPage = startPage,
    endPage = endPage,
    activeIntervals = activeIntervals,
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

private fun com.vayana.core.database.entity.TombstoneEntity.toPortable(): PortableTombstone = PortableTombstone(
    syncId = syncId,
    entityType = entityType,
    deletedAt = deletedAt,
)

private fun PortableTombstone.toEntity(): com.vayana.core.database.entity.TombstoneEntity =
    com.vayana.core.database.entity.TombstoneEntity(
        syncId = syncId,
        entityType = entityType,
        deletedAt = deletedAt,
    )

private val ReadingProgressSyncStatus.isIssue: Boolean
    get() = this == ReadingProgressSyncStatus.FAILED ||
        this == ReadingProgressSyncStatus.CLOUD_MISSING ||
        this == ReadingProgressSyncStatus.CONFIG_INCOMPLETE

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
        (statusCode == HttpURLConnection.HTTP_NOT_FOUND ||
            (statusCode == HttpURLConnection.HTTP_CONFLICT &&
                responseBody.contains("Git Repository is empty", ignoreCase = true)))

private fun Throwable.isGitHubConflict(): Boolean =
    this is GitHubAssetStoreException && statusCode == HttpURLConnection.HTTP_CONFLICT

private const val MaxProgressOnlySyncAttempts = 2
private const val MinSyncIntervalMillis = 20_000L
private const val MaxSyncFailureBodyChars = 320
private const val MaxSyncFailureMessageChars = 400
