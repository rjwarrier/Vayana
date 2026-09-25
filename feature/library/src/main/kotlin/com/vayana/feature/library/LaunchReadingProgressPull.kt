package com.vayana.feature.library

import com.vayana.core.datastore.settings.LaunchReadingProgressCheckMarker

/**
 * The silent launch-time reading-progress check ([GitHubSyncMode.READING_PROGRESS_PULL_ONLY]).
 *
 * Pull-only by construction: it never sees the asset store, only a [pull] of the remote snapshot,
 * so it has no way to upload books, covers or a snapshot. A fresh local marker for the same book
 * and sync target skips the network entirely; otherwise the marker's remote SHA lets [pull] skip
 * re-reading an unchanged snapshot.
 */
internal class LaunchReadingProgressPull(
    private val markers: LaunchProgressMarkerStore,
    private val recordDiagnostic: (bookId: Long?, message: String, detail: String?) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(
        bookId: Long?,
        syncTarget: String,
        pull: suspend (skipRemoteSnapshotSha: String?) -> ReadingProgressMergeSummary,
    ): GitHubSyncNowResult.Complete {
        val existingMarker = bookId?.let { markers.read() }
            ?.takeIf { it.bookId == bookId && it.syncTarget == syncTarget }
        if (existingMarker != null && now() - existingMarker.checkedAt in 0..LaunchReadingProgressFreshCheckTtlMs) {
            recordDiagnostic(
                bookId,
                "Silent launch progress check skipped by fresh local marker",
                "syncTarget=$syncTarget, checkedAt=${existingMarker.checkedAt}, outcome=${existingMarker.outcome}",
            )
            return complete(outcome = LaunchProgressCheckOutcome.SKIPPED_FRESH_MARKER, skipped = 1)
        }

        val merge = pull(existingMarker?.remoteSnapshotSha)
        if (merge.failed || merge.missingRemoteSnapshot) {
            recordDiagnostic(
                bookId,
                if (merge.failed) "Silent launch progress check failed" else "Silent launch progress check found no cloud snapshot",
                merge.failureMessage,
            )
            return complete(
                outcome = LaunchProgressCheckOutcome.FAILED,
                pullFailed = true,
                failureMessage = merge.failureMessage,
            )
        }

        val outcome = if (merge.skippedAlreadyChecked) {
            recordDiagnostic(
                bookId,
                "Silent launch progress check skipped by unchanged remote snapshot",
                "syncTarget=$syncTarget, remoteSnapshotSha=${merge.remoteSnapshotSha.orEmpty()}",
            )
            LaunchProgressCheckOutcome.SKIPPED_UNCHANGED_REMOTE
        } else {
            LaunchProgressCheckOutcome.CHECKED
        }
        val remoteSnapshotSha = merge.remoteSnapshotSha
        if (bookId != null && remoteSnapshotSha != null) {
            markers.write(
                LaunchReadingProgressCheckMarker(
                    bookId = bookId,
                    syncTarget = syncTarget,
                    remoteSnapshotSha = remoteSnapshotSha,
                    checkedAt = now(),
                    outcome = outcome.name,
                ),
            )
        }
        return complete(
            outcome = outcome,
            progressUpdated = merge.applied,
            progressAppliedSyncIds = merge.appliedSyncIds,
            syncedDeviceLabel = merge.remoteDeviceLabel,
            syncedAt = merge.remoteSyncedAt,
            conflicts = merge.conflictCount,
            skipped = merge.skipped + if (merge.skippedAlreadyChecked) 1 else 0,
        )
    }

    private fun complete(
        outcome: LaunchProgressCheckOutcome,
        progressUpdated: Int = 0,
        progressAppliedSyncIds: Set<String> = emptySet(),
        syncedDeviceLabel: String? = null,
        syncedAt: Long? = null,
        conflicts: Int = 0,
        skipped: Int = 0,
        pullFailed: Boolean = false,
        failureMessage: String? = null,
    ) = GitHubSyncNowResult.Complete(
        uploaded = 0,
        failed = 0,
        progressUpdated = progressUpdated,
        cloudBooksCreated = 0,
        cloudBooksUpdated = 0,
        progressUploaded = 0,
        conflicts = conflicts,
        skipped = skipped,
        pullFailed = pullFailed,
        metadataSynced = !pullFailed,
        failureMessage = failureMessage,
        launchProgressCheckOutcome = outcome,
        progressAppliedSyncIds = progressAppliedSyncIds,
        syncedDeviceLabel = syncedDeviceLabel,
        syncedAt = syncedAt,
    )
}

internal interface LaunchProgressMarkerStore {
    suspend fun read(): LaunchReadingProgressCheckMarker?
    suspend fun write(marker: LaunchReadingProgressCheckMarker)
}

private const val LaunchReadingProgressFreshCheckTtlMs = 5 * 60 * 1000L
