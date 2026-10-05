package com.vayana.core.wear

import kotlin.test.*
import org.json.JSONObject

class WatchStateTest {
    private val book = WearBook("book-123", "A book", 10, 200, 1000)
    private val id = "wear-12345678-1234-1234-1234-123456789abc"
    private fun running() = WatchState(books = listOf(book)).start(book, 10, id, 1000, 5000, 1)

    @Test fun offlineSessionSurvivesSerializationAndExcludesPausedTime() {
        val paused = running().page(15).pause(65_000)
        val restored = WatchState.parse(paused.json())
        assertEquals(paused, restored)
        val finished = restored.resume(125_000).finish(185_000, 181_000)
        assertNull(finished.active)
        val log = finished.entries.single().session
        assertEquals(120L, log.seconds)
        assertEquals(10, log.startPage)
        assertEquals(15, log.endPage)
        assertNull(finished.entries.single().receipt)
        assertEquals(finished, WatchState.parse(finished.json()))
    }

    @Test fun sameBootProcessDeathContinuesButRebootPausesAtCheckpoint() {
        val checkpoint = running().checkpoint(35_000)
        assertEquals(60_000L, WatchState.parse(checkpoint.json()).recover(1, 65_000).active!!.timer.elapsedMillis(65_000))
        val reboot = WatchState.parse(checkpoint.json()).recover(2, 500)
        assertEquals(PhysicalTimerPhase.PAUSED, reboot.active!!.timer.phase)
        assertEquals(30_000L, reboot.active.timer.elapsedMillis(5000))
        assertTrue(reboot.recoveredAfterReboot)
    }

    @Test fun acknowledgementIsIdempotentAndDoesNotRemoveUndeliveredSessions() {
        val finished = running().finish(65_000, 61_000)
        val acknowledged = finished.acknowledge(id, "saved")
        assertEquals(acknowledged, acknowledged.acknowledge(id, "saved"))
        assertEquals(finished, finished.acknowledge("unknown", "saved"))
        assertEquals("overlap", finished.acknowledge(id, "overlap").entries.single().receipt)
        assertEquals(acknowledged, WatchState.parse(acknowledged.json()))
    }

    @Test fun invalidPagesAndWirePayloadsCannotBeSaved() {
        assertFailsWith<IllegalArgumentException> { running().page(201) }
        assertFailsWith<IllegalArgumentException> { running().page(-1) }
        assertFailsWith<IllegalStateException> { running().start(book, 10, id, 1000, 5000, 1) }
        val json = running().finish(65_000, 61_000).entries.single().session.json()
        assertFailsWith<IllegalArgumentException> { WearSession.parse(JSONObject(json.toString()).put("version", 2)) }
        assertFailsWith<IllegalArgumentException> { WearSession.parse(JSONObject(json.toString()).put("seconds", 999999)) }
        assertFailsWith<IllegalArgumentException> { WearSession.parse(JSONObject(json.toString()).put("id", "../other")) }
    }

    @Test fun catalogPreservesStableIdentityAndUnknownPageCount() {
        val books = listOf(book, book.copy(id = "unknown", page = 0, total = null))
        assertEquals(books, WearProtocol.books(WearProtocol.catalog(books)))
    }

    @Test fun intervalsRetainPauseGapsAfterCheckpointsAndProcessDeath() {
        val state = running().checkpoint(15000).pause(65000)
        val log = WatchState.parse(state.json()).resume(125000).checkpoint(135000).finish(185000,181000).entries.single().session
        assertEquals("1000:61000,121000:181000", log.activeIntervals)
        assertEquals(120L, log.seconds)
    }
    @Test fun clockChangesKeepElapsedTimeAndRequireReview() {
        val log = running().finish(65000, 900000).entries.single().session
        assertEquals(60000L, log.seconds * 1000)
        assertEquals(61000L, log.endedAt)
        assertTrue(log.clockChanged)
    }
    @Test fun rebootWithBackwardsClockCannotOverlapPreviousIntervals() {
        val saved = running().checkpoint(65000).active!!.timer
        val log = saved.recoverBoot(2, 500, 100).resume(1000).stop(11000,71500)
        assertTrue(log.clockChanged)
        assertEquals(70000L, log.accumulatedMillis)
        assertEquals(70000L, com.vayana.core.common.ReadingIntervals.millis(com.vayana.core.common.ReadingIntervals.decode(log.activeIntervals!!)))
    }
}
