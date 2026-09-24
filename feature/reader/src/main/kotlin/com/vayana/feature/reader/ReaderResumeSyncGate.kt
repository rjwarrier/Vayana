package com.vayana.feature.reader

import com.vayana.core.sync.progress.ReadingProgressSyncResult
import com.vayana.core.sync.progress.ReadingProgressSyncStatus

/**
 * Keeps a reader that survived in the background from writing its stale locator until a forced
 * progress sync has checked for a newer position from another device.
 */
internal class ReaderResumeSyncGate {
    private var generation = 0L
    private var checkRequired = false
    private var checkingGeneration: Long? = null

    val blocksPositionWrites: Boolean
        get() = checkRequired || checkingGeneration != null

    fun onPause(bookOpen: Boolean) {
        if (!bookOpen) return
        generation += 1
        checkRequired = true
    }

    fun onResume(bookOpen: Boolean): Long? {
        if (!bookOpen || !checkRequired) return null
        checkRequired = false
        return generation.also { checkingGeneration = it }
    }

    /** Returns true only when this completion released the final outstanding write block. */
    fun onSyncFinished(finishedGeneration: Long): Boolean {
        if (checkingGeneration != finishedGeneration) return false
        checkingGeneration = null
        return !checkRequired
    }
}

/** Whether this result established that retaining the WebView's automatic resume relocation is safe. */
internal val ReadingProgressSyncResult.remoteCheckCompleted: Boolean
    get() = when (status) {
        ReadingProgressSyncStatus.NO_CHANGES,
        ReadingProgressSyncStatus.PULLED,
        ReadingProgressSyncStatus.PUSHED,
        ReadingProgressSyncStatus.SYNCED,
        ReadingProgressSyncStatus.SYNC_DISABLED,
        ReadingProgressSyncStatus.CLOUD_MISSING,
        -> true
        ReadingProgressSyncStatus.THROTTLED,
        ReadingProgressSyncStatus.CONFIG_INCOMPLETE,
        ReadingProgressSyncStatus.FAILED,
        -> false
    }
