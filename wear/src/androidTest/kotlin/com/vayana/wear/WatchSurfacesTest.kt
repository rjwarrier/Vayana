package com.vayana.wear

import android.app.Notification
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.tiles.RequestBuilders
import com.vayana.core.wear.WatchTimerSurface
import org.junit.Assert.*
import org.junit.Test

/** Reads local books but never starts, saves, or edits a real session. */
class WatchSurfacesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun runningAndPausedNotificationsHaveCorrectClockAndReturnIntent() {
        val running = WatchTimerSurfaces.notification(context, WatchTimerSurface("Test", 12, true, 5000))
        assertTrue(running.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(running.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertNotNull(running.contentIntent)
        assertNotNull(running.extras.getBundle("android.wearable.ongoingactivities.EXTENSIONS"))
        val paused = WatchTimerSurfaces.notification(context, WatchTimerSurface("Test", 12, false, 60000))
        assertFalse(paused.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals("Paused · page 12", paused.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test fun tileAndCachedImageResourcesBuildWithoutPhoneOrSessionChanges() {
        val before = context.getSharedPreferences("reading", 0).getString("state", null)
        val service = ReadingTileService()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", android.content.Context::class.java)
            .apply { isAccessible = true }.invoke(service, context)
        val tile = service.onTileRequest(RequestBuilders.TileRequest.Builder().build()).get()
        assertFalse(tile.resourcesVersion.isEmpty())
        assertFalse(tile.tileTimeline!!.timelineEntries.isEmpty())
        val resources = service.onTileResourcesRequest(RequestBuilders.ResourcesRequest.Builder()
            .setVersion(tile.resourcesVersion).build()).get()
        assertEquals(tile.resourcesVersion, resources.version)
        assertEquals(before, context.getSharedPreferences("reading", 0).getString("state", null))
    }

    @Test fun complicationPreviewsMatchEveryAdvertisedType() {
        val service = ReadingComplicationService()
        listOf(androidx.wear.watchface.complications.data.ComplicationType.SHORT_TEXT,
            androidx.wear.watchface.complications.data.ComplicationType.LONG_TEXT,
            androidx.wear.watchface.complications.data.ComplicationType.RANGED_VALUE).forEach {
            assertEquals(it, service.getPreviewData(it).type)
        }
    }

    @Test fun livePhoneRejectsAnUnknownTimerAndAcknowledgesTheRequest() {
        val transport = com.vayana.core.wear.WearTransport(context)
        org.junit.Assume.assumeTrue("Phone must be connected for the live transport check", transport.phoneConnected())
        val command = com.vayana.core.wear.SharedTimerCommand("cmd-${java.util.UUID.randomUUID()}", "phone",
            "session-${java.util.UUID.randomUUID()}", 0, "pause")
        val path = com.vayana.core.wear.SharedTimerSnapshot.commandPath(command)
        try {
            transport.put(path, command.json())
            val deadline = android.os.SystemClock.elapsedRealtime() + 25000
            var receipt: String? = null
            while (receipt == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                receipt = transport.items().firstOrNull { it.first.path == com.vayana.core.wear.SharedTimerSnapshot.receiptPath(command) }?.second
                if (receipt == null) Thread.sleep(250)
            }
            // The random session ID cannot match an actual user's timer, so no reading state can change.
            assertEquals("ended", command.receiptStatus(receipt))
        } finally {
            transport.items().firstOrNull { it.first.path == path }?.let { transport.delete(it.first) }
            transport.put(com.vayana.core.wear.WearProtocol.REQUEST, java.util.UUID.randomUUID().toString())
        }
    }

    @Test fun ambientLayoutIsMinimalAndDoesNotChangeAnySavedSession() {
        val before = context.getSharedPreferences("reading", 0).getString("state", null)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(android.content.Intent(context, WatchActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as WatchActivity
        val now = android.os.SystemClock.elapsedRealtime()
        val preview = com.vayana.core.wear.WatchState().start(com.vayana.core.wear.WearBook("preview", "Ambient preview", 10, 100, 1),
            10, "wear-12345678-1234-1234-1234-123456789abc", System.currentTimeMillis() - 600000, now - 600000, 1)
        instrumentation.runOnMainSync {
            try {
                activity.javaClass.getDeclaredMethod("renderAmbient", com.vayana.core.wear.WatchState::class.java)
                    .apply { isAccessible = true }.invoke(activity, preview)
                fun texts(view: android.view.View): List<String> = when (view) {
                    is android.widget.TextView -> listOf(view.text.toString())
                    is android.view.ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
                    else -> emptyList()
                }
                assertTrue(texts(activity.window.decorView).contains("0:10"))
                assertTrue(texts(activity.window.decorView).contains("Ambient preview"))
            } finally {
                activity.javaClass.getDeclaredMethod("render").apply { isAccessible = true }.invoke(activity)
                activity.finish()
            }
        }
        // Normal sync may update companion metadata, so compare the actual session log entries.
        val after = context.getSharedPreferences("reading", 0).getString("state", null)
        val priorEntries = before?.let { org.json.JSONObject(it).getJSONArray("entries").toString() }
        val afterEntries = after?.let { org.json.JSONObject(it).getJSONArray("entries").toString() }
        assertEquals(priorEntries, afterEntries)
    }
}
