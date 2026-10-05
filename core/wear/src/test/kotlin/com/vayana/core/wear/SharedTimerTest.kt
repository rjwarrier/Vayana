package com.vayana.core.wear

import kotlin.test.*
import org.junit.Test

class SharedTimerTest {
    private val id = "wear-12345678-1234-1234-1234-123456789abc"
    private fun running() = WatchState().start(WearBook("book", "A book", 10, 200, 1), 10, id, 1000, 5000, 1)
    private fun command(action: String, page: Int? = null, revision: Long = 0, session: String = id) =
        SharedTimerCommand("cmd-12345678-1234-1234-1234-123456789abc", "watch", session, revision, action, page)

    @Test fun snapshotsUseOwnerElapsedTimeAndPersistTheRemoteAnchor() {
        val owner = running().checkpoint(65000)
        val snapshot = SharedTimerSnapshot.create("phone", 7, owner.active, 65000, 61000)
        val mirrored = WatchState().receiveRemote(SharedTimerSnapshot.parse(snapshot.json()), 25000, 63000, 3)
        val restored = WatchState.parse(mirrored.json())
        assertEquals(72000L, restored.displayActive(35000, 3)!!.timer.elapsedMillis(35000))
        assertEquals(snapshot, restored.remoteTimer)
        assertNull(restored.active)
        assertTrue(restored.entries.isEmpty())
    }

    @Test fun remoteSnapshotsCannotOverwriteAnOfflineLocalTimerOrRegressRevision() {
        val local = running()
        val remote = SharedTimerSnapshot.create("phone", 5, local.active, 5000, 1000)
        val state = local.receiveRemote(remote, 10000, 1000, 1)
        assertEquals(local.active, state.displayActive(15000, 1))
        assertEquals(state, state.receiveRemote(remote.copy(revision = 4), 10000, 1000, 1))
        assertFailsWith<IllegalStateException> { WatchState().receiveRemote(remote, 10000, 1000, 1)
            .start(local.active!!.book, 10, id, 1000, 10000, 1) }
    }

    @Test fun repeatedCommandsNeverPauseOrSaveTwiceAfterRestart() {
        val pause = command("pause")
        val applied = running().applySharedCommand(pause, 65000, 61000).first
        assertEquals(PhysicalTimerPhase.PAUSED, applied.active!!.timer.phase)
        assertEquals(applied, WatchState.parse(applied.json()).applySharedCommand(pause, 125000, 121000).first)
        val finish = command("finish", 20).copy(id = "cmd-22345678-1234-1234-1234-123456789abc")
        val saved = applied.applySharedCommand(finish, 125000, 121000).first
        assertEquals(1, saved.entries.size)
        assertEquals(60L, saved.entries.single().session.seconds)
        assertEquals(saved, WatchState.parse(saved.json()).applySharedCommand(finish, 150000, 146000).first)
    }

    @Test fun staleCommandsOldSessionsAndChangedPayloadsAreRejected() {
        val local = running().copy(timerRevision = 5)
        assertEquals("stale", local.applySharedCommand(command("pause"), 65000, 61000).second)
        assertEquals("ended", local.applySharedCommand(command("pause", revision = 5, session = "other"), 65000, 61000).second)
        assertEquals(PhysicalTimerPhase.RUNNING, local.active!!.timer.phase)
        val pause = command("pause", revision = 5)
        val paused = local.applySharedCommand(pause, 65000, 61000).first
        assertEquals("payload_conflict", paused.applySharedCommand(pause.copy(action = "discard"), 65000, 61000).second)
        assertNotNull(paused.active)
    }

    @Test fun wrongOwnerInvalidPagesAndReceiptsCannotAffectTheTimer() {
        assertEquals("invalid", running().applySharedCommand(command("pause").copy(target = "phone"), 65000, 61000).second)
        assertEquals("invalid", running().applySharedCommand(command("finish", 201), 65000, 61000).second)
        val cmd = command("pause")
        assertEquals(cmd, SharedTimerCommand.parse(cmd.json()))
        assertEquals("applied", cmd.receiptStatus(cmd.receipt("applied")))
        assertNull(cmd.copy(action = "stop").receiptStatus(cmd.receipt("applied")))
    }

    @Test fun sharedEventsDoNotWakeThePublisherInALoop() {
        assertTrue(WearSyncRules.phoneEvent(SharedTimerSnapshot.statePath("watch"), true))
        assertFalse(WearSyncRules.watchEvent(SharedTimerSnapshot.statePath("watch"), true))
        assertTrue(WearSyncRules.watchEvent(SharedTimerSnapshot.statePath("phone"), true))
        assertFalse(WearSyncRules.phoneEvent(SharedTimerSnapshot.statePath("phone"), true))
    }
}
