package com.vayana.core.wear

import kotlin.test.*

class WearSyncRulesTest {
    private val book = WearBook("missing-test-book", "Sync test", 0, 100, 1)
    private val id = "wear-12345678-1234-1234-1234-123456789abc"
    private fun finished() = WatchState(books = listOf(book))
        .start(book, 0, id, 1000, 1000, 1).page(1).finish(61000, 61000)

    @Test fun offlineReceiptRoundTripStaysPendingThenReconnectAcknowledges() {
        val offline = WatchState.parse(finished().json())
        assertNull(offline.entries.single().receipt)
        assertTrue(WearSyncRules.cleanupIds(offline).isEmpty())
        val connected = offline.acknowledge(id, "saved")
        assertEquals(setOf(id), WearSyncRules.cleanupIds(WatchState.parse(connected.json())))
    }
    @Test fun staleRejectionCannotUndoSuccessfulDelivery() {
        val delivered = finished().acknowledge(id, "page_kept")
        assertEquals(delivered, delivered.acknowledge(id, "phone_timer_active"))
        assertEquals(delivered, delivered.acknowledge(id, "duplicate"))
        assertEquals(delivered, delivered.acknowledge(id, "garbage"))
    }
    @Test fun cleanupSurvivesMissingRemoteAcknowledgementAndRestart() {
        val local = WatchState.parse(finished().acknowledge(id, "saved").json())
        assertEquals(setOf(id), WearSyncRules.cleanupIds(local))
        assertTrue(WearSyncRules.cleanupIds(finished().acknowledge(id, "overlap")).isEmpty())
    }
    @Test fun eventsDoNotCreateFeedbackLoopsOrWakeOnOwnWrites() {
        assertTrue(WearSyncRules.phoneEvent(WearProtocol.SESSION + id, true))
        assertTrue(WearSyncRules.phoneEvent(WearProtocol.SESSION + id, false))
        assertTrue(WearSyncRules.phoneEvent(WearProtocol.REQUEST, true))
        assertFalse(WearSyncRules.phoneEvent(WearProtocol.CATALOG, true))
        assertFalse(WearSyncRules.phoneEvent(WearProtocol.ACK + id, true))
        assertTrue(WearSyncRules.watchEvent(WearProtocol.ACK + id, true))
        assertTrue(WearSyncRules.watchEvent(WearProtocol.CATALOG, true))
        assertFalse(WearSyncRules.watchEvent(WearProtocol.SESSION + id, true))
        assertFalse(WearSyncRules.watchEvent(WearProtocol.REQUEST, true))
        assertFalse(WearSyncRules.watchEvent(WearProtocol.ACK + id, false))
    }

    @Test fun acknowledgementForPreviousTimestampsCannotAcceptCorrectedSession() {
        val original = finished().entries.single().session
        val revised = original.copy(startedAt = original.startedAt + 10000, endedAt = original.endedAt + 10000, activeIntervals = "11000:71000")
        val ack = org.json.JSONObject().put("status", "saved").put("resolution", "auto").put("fingerprint", original.fingerprint()).toString()
        assertEquals("saved", WearSyncRules.readReceipt(ack, "auto", original.fingerprint()))
        assertNull(WearSyncRules.readReceipt(ack, "auto", revised.fingerprint()))
        assertNull(WearSyncRules.readReceipt(ack, "watch_page", original.fingerprint()))
    }
}
