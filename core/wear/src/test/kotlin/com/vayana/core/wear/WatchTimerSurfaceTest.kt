package com.vayana.core.wear

import kotlin.test.*
import org.junit.Test

class WatchTimerSurfaceTest {
    private fun running() = WatchState().start(WearBook("book", "Reading", 10, 100, 1), 10,
        "wear-12345678-1234-1234-1234-123456789abc", 1000, 5000, 1)

    @Test fun checkpointsDoNotResetTheSystemChronometer() {
        val initial = running()
        val surface = WatchTimerSurface.from(initial.active)!!
        assertTrue(surface.running)
        assertEquals(5000L, surface.time)
        assertEquals(surface, WatchTimerSurface.from(initial.checkpoint(65000).active))
    }

    @Test fun pauseAndResumeExcludeThePauseFromTheDisplayedClock() {
        val paused = running().pause(65000)
        assertEquals(WatchTimerSurface("Reading", 10, false, 60000), WatchTimerSurface.from(paused.active))
        val resumed = WatchState.parse(paused.json()).resume(125000)
        val surface = WatchTimerSurface.from(resumed.active)!!
        assertTrue(surface.running)
        assertEquals(65000L, surface.time)
        assertEquals(resumed.active!!.timer.elapsedMillis(185000), 185000 - surface.time)
    }

    @Test fun finishedSessionsRemoveTheSurfaceAndRebootShowsPausedTime() {
        assertNull(WatchTimerSurface.from(running().finish(65000, 61000).active))
        val rebooted = WatchState.parse(running().checkpoint(65000).json()).recover(2, 500)
        assertEquals(WatchTimerSurface("Reading", 10, false, 60000), WatchTimerSurface.from(rebooted.active))
    }
}
