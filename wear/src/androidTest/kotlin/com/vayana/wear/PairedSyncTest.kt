package com.vayana.wear

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vayana.core.wear.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Requires a paired phone running Vayana. Uses an unknown book so no real reading history changes. */
@RunWith(AndroidJUnit4::class)
class PairedSyncTest {
    @Test fun queuedRecordReceivesPhoneAcknowledgementAndRemainsRecoverable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = WatchStore(context)
        val transport = WearTransport(context)
        val id = "wear-${UUID.randomUUID()}"
        val time = System.currentTimeMillis() - 2000
        val session = WearSession(id, WearBook("sync-test-${UUID.randomUUID()}", "Temporary sync test", 0, 10, 1),
            time, time + 1000, 1, 0, 1)
        val offline = InstrumentationRegistry.getArguments().getString("offline") == "true"
        val reconnectMarker = java.io.File(context.filesDir, "sync-test-reconnect")
        try {
            // Reachability metadata can remain cached after radios disconnect; verify actual delivery below.
            if (!offline) assertTrue("Phone must be reachable for this paired-device test", transport.phoneConnected())
            store.update { it.copy(entries = it.entries + WatchEntry(session)) }
            WatchSync.enqueue(context)
            if (offline) {
                // DataClient accepts and persists the item even when the phone is unreachable.
                transport.put(WearProtocol.SESSION + id, session.json().toString())
                Thread.sleep(5000)
                assertNull(WatchStore(context).read().entries.first { it.session.id == id }.receipt)
                assertTrue(transport.items().any { it.first.path == WearProtocol.SESSION + id })
                reconnectMarker.writeText("Offline queue persisted. Restore phone connectivity.")
            }
            val deadline = android.os.SystemClock.elapsedRealtime() + 60_000
            while (android.os.SystemClock.elapsedRealtime() < deadline &&
                store.read().entries.firstOrNull { it.session.id == id }?.receipt != "book_missing") {
                Thread.sleep(500)
            }
            val entry = store.read().entries.first { it.session.id == id }
            assertEquals("Phone receiver must acknowledge the test record", "book_missing", entry.receipt)
            assertEquals("Rejected sessions must remain locally recoverable", session, entry.session)
            assertTrue("Outbox item must remain until successful delivery", transport.items().any {
                it.first.path == WearProtocol.SESSION + id
            })
        } finally {
            reconnectMarker.delete()
            store.update { it.copy(entries = it.entries.filterNot { entry -> entry.session.id == id }) }
            transport.items().filter { it.first.path in setOf(WearProtocol.SESSION + id, WearProtocol.ACK + id) }
                .forEach { transport.delete(it.first) }
        }
    }
}
