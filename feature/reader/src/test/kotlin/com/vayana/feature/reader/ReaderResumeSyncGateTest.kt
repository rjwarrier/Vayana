package com.vayana.feature.reader

import com.vayana.core.sync.progress.ReadingProgressSyncResult
import com.vayana.core.sync.progress.ReadingProgressSyncStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderResumeSyncGateTest {
    @Test
    fun `an already-open reader checks sync before saving after resume`() {
        val gate = ReaderResumeSyncGate()

        assertNull(gate.onResume(bookOpen = true))
        gate.onPause(bookOpen = true)
        assertTrue(gate.blocksPositionWrites)

        val generation = assertNotNull(gate.onResume(bookOpen = true))
        assertTrue(gate.blocksPositionWrites)

        assertTrue(gate.onSyncFinished(generation))
        assertFalse(gate.blocksPositionWrites)
    }

    @Test
    fun `pausing again during a check keeps stale writes blocked`() {
        val gate = ReaderResumeSyncGate()
        gate.onPause(bookOpen = true)
        val firstGeneration = assertNotNull(gate.onResume(bookOpen = true))

        gate.onPause(bookOpen = true)
        assertFalse(gate.onSyncFinished(firstGeneration))

        assertTrue(gate.blocksPositionWrites)
        assertNotNull(gate.onResume(bookOpen = true))
    }

    @Test
    fun `a reader that was not open does not need a resume check`() {
        val gate = ReaderResumeSyncGate()

        gate.onPause(bookOpen = false)

        assertNull(gate.onResume(bookOpen = true))
        assertFalse(gate.blocksPositionWrites)
    }

    @Test
    fun `only a completed remote check may retain the automatic resume locator`() {
        val completed = listOf(
            ReadingProgressSyncStatus.NO_CHANGES,
            ReadingProgressSyncStatus.PULLED,
            ReadingProgressSyncStatus.PUSHED,
            ReadingProgressSyncStatus.SYNCED,
            ReadingProgressSyncStatus.SYNC_DISABLED,
            ReadingProgressSyncStatus.CLOUD_MISSING,
        )
        val incomplete = listOf(
            ReadingProgressSyncStatus.THROTTLED,
            ReadingProgressSyncStatus.CONFIG_INCOMPLETE,
            ReadingProgressSyncStatus.FAILED,
        )

        completed.forEach { status -> assertTrue(ReadingProgressSyncResult(status).remoteCheckCompleted, status.name) }
        incomplete.forEach { status -> assertFalse(ReadingProgressSyncResult(status).remoteCheckCompleted, status.name) }
    }
}
