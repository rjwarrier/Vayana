package com.vayana.feature.library

import com.vayana.core.backup.PortableReadingPositionAlternative
import com.vayana.core.backup.PortableSyncConflict
import com.vayana.core.backup.parsePortableReadingProgressSnapshot
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.ReadingProgressMergeResult
import com.vayana.core.database.repository.ReadingProgressVersion
import com.vayana.core.sync.snapshot.RemotePortableSnapshotDocument
import com.vayana.core.sync.snapshot.RemotePortableSnapshotSlice

/**
 * Applies a pulled remote snapshot's reading positions (and, unless told otherwise, its deletion
 * tombstones) to the local library. The only library write it makes itself is
 * [BookRepository.applySyncedReadingProgress]; tombstones go through [mergeTombstones].
 */
internal class RemoteReadingProgressMerger(
    private val bookRepository: BookRepository,
    private val localDeviceLabel: suspend () -> String,
    private val mergeTombstones: suspend (tombstonesJson: String) -> GenericSyncMergeSummary,
) {
    suspend fun merge(document: RemotePortableSnapshotDocument, applyTombstones: Boolean): ReadingProgressMergeSummary {
        val tombstoneMerge = if (applyTombstones) {
            mergeTombstones(document.jsonFor(RemotePortableSnapshotSlice.Tombstones))
        } else {
            GenericSyncMergeSummary()
        }
        if (tombstoneMerge.failed) {
            return ReadingProgressMergeSummary(
                failed = true,
                skipped = tombstoneMerge.skipped,
                failureMessage = tombstoneMerge.failureMessage,
                remoteSnapshot = document,
                remoteSnapshotSha = document.sha,
                remoteSnapshotSliced = document.sliced,
            )
        }
        val progressMerge = mergeProgress(document.jsonFor(RemotePortableSnapshotSlice.Books))
        return progressMerge.copy(
            skipped = progressMerge.skipped + tombstoneMerge.skipped,
            remoteSnapshot = document,
            remoteSnapshotSha = document.sha,
            remoteSnapshotSliced = document.sliced,
        )
    }

    suspend fun mergeProgress(snapshotJson: String): ReadingProgressMergeSummary {
        val remoteSnapshot = parsePortableReadingProgressSnapshot(snapshotJson)
        val localDeviceLabel = localDeviceLabel()
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
