package com.vayana.feature.library

import com.vayana.core.backup.PortableReadingPositionAlternative
import com.vayana.core.backup.PortableSyncConflict
import com.vayana.core.backup.parsePortableReadingProgressSnapshot
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.ReadingProgressMergeResult
import com.vayana.core.database.repository.ReadingProgressVersion
import com.vayana.core.sync.snapshot.RemotePortableSnapshotDocument
import com.vayana.core.sync.snapshot.RemotePortableSnapshotSlice

/** Which remote deletions a pull applies. */
internal enum class TombstoneMergeScope {
    /** Every kind: a user-started sync. */
    ALL,

    /** Only whole-book deletions: the silent launch check, which otherwise only moves reading positions. */
    BOOK_DELETIONS,
}

/**
 * Applies a pulled remote snapshot's reading positions and deletion tombstones (those in the given
 * [TombstoneMergeScope]) to the local library. The only library write it makes itself is
 * [BookRepository.applySyncedReadingProgress]; tombstones go through [mergeTombstones].
 */
internal class RemoteReadingProgressMerger(
    private val bookRepository: BookRepository,
    private val localDeviceLabel: suspend () -> String,
    private val mergeTombstones: suspend (tombstonesJson: String, scope: TombstoneMergeScope) -> GenericSyncMergeSummary,
) {
    suspend fun merge(document: RemotePortableSnapshotDocument, tombstones: TombstoneMergeScope): ReadingProgressMergeSummary {
        document.prefetch(listOf(RemotePortableSnapshotSlice.Tombstones, RemotePortableSnapshotSlice.Books))
        val tombstoneMerge = mergeTombstones(document.jsonFor(RemotePortableSnapshotSlice.Tombstones), tombstones)
        if (tombstoneMerge.failed) {
            return ReadingProgressMergeSummary(
                failed = true,
                skipped = tombstoneMerge.skipped,
                failureMessage = tombstoneMerge.failureMessage,
                remoteSnapshot = document,
                remoteSnapshotSha = document.sha,
            )
        }
        val progressMerge = mergeProgress(document.jsonFor(RemotePortableSnapshotSlice.Books))
        return progressMerge.copy(
            skipped = progressMerge.skipped + tombstoneMerge.skipped,
            remoteSnapshot = document,
            remoteSnapshotSha = document.sha,
        )
    }

    suspend fun mergeProgress(snapshotJson: String): ReadingProgressMergeSummary {
        val remoteSnapshot = parsePortableReadingProgressSnapshot(snapshotJson)
        val localDeviceLabel = localDeviceLabel()
        var applied = 0
        var skipped = 0
        val appliedSyncIds = linkedSetOf<String>()
        val conflicts = ArrayList<PortableSyncConflict>()
        remoteSnapshot.readNextStates.forEach { state ->
            if (bookRepository.applySyncedReadNext(
                    syncId = state.syncId,
                    fileHash = state.fileHash,
                    addedAt = state.addedAt,
                    remoteUpdatedAt = state.updatedAt,
                pinned = state.pinned,
                )
            ) {
                applied += 1
            }
        }
        remoteSnapshot.progresses.forEach { progress ->
            when (val mergeResult = bookRepository.applySyncedReadingProgress(
                syncId = progress.syncId,
                fileHash = progress.fileHash,
                locator = progress.lastLocator,
                readingPercent = progress.readingPercent,
                lastReadAt = progress.lastReadAt,
                remoteUpdatedAt = progress.updatedAt,
                startedReadingAt = progress.startedReadingAt,
                finishedReadingAt = progress.finishedReadingAt,
                totalReadingSeconds = progress.totalReadingSeconds,
                syncedDeviceLabel = remoteSnapshot.deviceLabel,
                syncedAt = remoteSnapshot.exportedAt,
            )) {
                ReadingProgressMergeResult.AppliedRemote -> {
                    applied += 1
                    appliedSyncIds += progress.syncId
                }
                is ReadingProgressMergeResult.ConflictLocalKept -> conflicts += mergeResult.toPortableConflict(
                    localDeviceLabel = localDeviceLabel,
                    remoteDeviceLabel = remoteSnapshot.deviceLabel,
                )
                ReadingProgressMergeResult.LocalNewer,
                ReadingProgressMergeResult.NoLocalMatch,
                ReadingProgressMergeResult.InvalidRemote,
                -> skipped += 1
            }
        }
        return ReadingProgressMergeSummary(
            applied = applied,
            appliedSyncIds = appliedSyncIds,
            conflicts = conflicts,
            skipped = skipped,
            remoteDeviceLabel = remoteSnapshot.deviceLabel,
            remoteSyncedAt = remoteSnapshot.exportedAt,
        )
    }
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
