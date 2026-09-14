package com.vayana.core.sync.asset

import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.database.dao.PendingCloudDeletionDao
import com.vayana.core.diagnostics.DiagnosticCategory
import com.vayana.core.diagnostics.DiagnosticsLogStore
import javax.inject.Inject

data class CloudAssetDeletionSummary(
    val deleted: Int = 0,
    val failed: Int = 0,
    /** Still queued after this run, including anything beyond the batch. */
    val remaining: Int = 0,
    val failureMessage: String? = null,
)

/**
 * Removes the cloud files of permanently deleted books from the sync repository. Call it only after a snapshot
 * carrying their purge tombstones has been published, so no device is still pointed at a file that is gone.
 */
class CloudAssetDeletionProcessor @Inject constructor(
    private val pendingCloudDeletionDao: PendingCloudDeletionDao,
) {
    suspend fun deletePending(store: CloudAssetStore, batchSize: Int = MaxDeletionsPerSync): CloudAssetDeletionSummary {
        var deleted = 0
        var failed = 0
        var failureMessage: String? = null
        for (pending in pendingCloudDeletionDao.getBatch(batchSize)) {
            if (!CloudAssetLayout.isValidAssetId(pending.assetId)) {
                // Nothing could ever have been uploaded under this id.
                pendingCloudDeletionDao.delete(pending.assetId)
                continue
            }
            val error = runCatchingCancellable { store.delete(CloudAssetLayout.pathFor(pending.assetId)) }.exceptionOrNull()
            if (error == null) {
                pendingCloudDeletionDao.delete(pending.assetId)
                deleted++
                continue
            }
            val message = error.message ?: error::class.java.simpleName
            pendingCloudDeletionDao.recordFailure(pending.assetId, message.take(MaxStoredErrorChars))
            failed++
            failureMessage = message
            // Auth and rate-limit failures would repeat for every remaining asset; leave them for the next sync.
            if (error is GitHubAssetStoreException && error.statusCode in StopBatchStatusCodes) break
        }
        return CloudAssetDeletionSummary(
            deleted = deleted,
            failed = failed,
            remaining = pendingCloudDeletionDao.count(),
            failureMessage = failureMessage,
        )
    }
}

/** Runs [CloudAssetDeletionProcessor.deletePending] and records any failed deletions to diagnostics under [source]. */
suspend fun CloudAssetDeletionProcessor.deletePendingAndLog(
    store: CloudAssetStore,
    diagnosticsLogStore: DiagnosticsLogStore,
    source: String,
) {
    val summary = deletePending(store)
    if (summary.failed == 0) return
    diagnosticsLogStore.record(
        category = DiagnosticCategory.SYNC,
        source = "$source.deletePendingCloudAssets",
        message = buildString {
            append("Could not delete ").append(summary.failed).append(" cloud file(s); ")
            append(summary.remaining).append(" still queued")
            summary.failureMessage?.let { append(": ").append(it) }
        },
    )
}

private const val MaxDeletionsPerSync = 20
private const val MaxStoredErrorChars = 500
private val StopBatchStatusCodes = setOf(401, 403, 429)
