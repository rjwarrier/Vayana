package com.vayana.feature.reader

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
