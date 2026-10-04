package com.vayana.feature.library

import com.vayana.core.database.model.ReadingSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhysicalReadingTimerTest {
    private fun timer() = PhysicalTimerSession(1, "Book", "session", 1_000, 40,
        runningSince = 10_000, bootCount = 1)

    @Test
    fun timerPageValidationIncludesStartingCheckpointWithoutRejectingRereading() {
        assertFalse(validPhysicalTimerPages(80, 20, "50"))
        assertTrue(validPhysicalTimerPages(80, 20, "100"))
        assertTrue(validPhysicalTimerPages(80, 20, ""))
        assertFalse(validPhysicalTimerPages(80, 20, "", knownTotal = 50))
        assertTrue(validPhysicalTimerPages(80, 20, "", knownTotal = 100))
        assertFalse(validPhysicalTimerPages(0, 101, "100"))
        assertFalse(validPhysicalTimerPages(null, 0, "100"))
        assertFalse(validPhysicalTimerPages(0, -1, "100"))
        assertFalse(validPhysicalTimerPages(0, 0, "0"))
        assertFalse(validPhysicalTimerPages(0, 0, "invalid"))
    }

    @Test
    fun estimatesUseFiveMostRecentValidSessionsEvenWhenHistoryIsUnsorted() {
        val older = log(0, 100, 3600).copy(startedAt = 100)
        val recent = (1..5).map { log(0, 10, 600).copy(startedAt = 1000L + it) }
        val pace = physicalReadingPace(listOf(recent[2], older) + recent.filterIndexed { index, _ -> index != 2 }, 100, 50)
        assertEquals(60.0, pace.pagesPerHour)
        assertEquals(3000L, pace.remainingSeconds)
        assertEquals(5, pace.sampleCount)
        assertEquals(50L, pace.timedPages)
    }

    @Test
    fun pausesExcludeBreaksAndRepeatedActionsDoNotAddTime() {
        val paused = timer().pause(70_000).pause(130_000)
        assertEquals(60_000, paused.elapsedMillis(200_000))
        val resumed = paused.resume(200_000).resume(220_000)
        val stopped = resumed.stop(260_000, 300_000)
        assertEquals(120_000, stopped.elapsedMillis(400_000))
        assertEquals(stopped, stopped.stop(500_000, 600_000))
    }

    @Test
    fun wallClockChangesDoNotAffectActiveDuration() {
        val stopped = timer().stop(70_000, 500)
        assertEquals(60_000, stopped.accumulatedMillis)
        assertEquals(61_000, stopped.endedAt)
    }

    @Test
    fun recordedSummaryUsesPageDistanceAndWholeElapsedMinutes() {
        val stopped = timer().copy(accumulatedMillis = 614_000, phase = PhysicalTimerPhase.STOPPED)
        assertEquals(PhysicalSessionRecorded(11, 10), physicalSessionRecorded(stopped, 51))
        assertEquals(PhysicalSessionRecorded(10, 10), physicalSessionRecorded(stopped, 30))
        assertEquals(
            PhysicalSessionRecorded(0, 1),
            physicalSessionRecorded(stopped.copy(accumulatedMillis = 900), 40),
        )
    }

    @Test
    fun paceUsesOnlyTimedForwardPageMovement() {
        val logs = listOf(log(40, 50, 600), log(50, 70, 1200), log(null, null, 5000), log(70, 60, 900))
        val pace = physicalReadingPace(logs, 200, 100)
        assertEquals(60.0, pace.pagesPerHour)
        assertEquals(6000L, pace.remainingSeconds)
        assertEquals(0L, physicalReadingPace(logs, 200, 200).remainingSeconds)
    }

    @Test
    fun noMovementOrMissingTotalDoesNotInventAnEstimate() {
        assertNull(physicalReadingPace(listOf(log(30, 30, 120)), 100, 30).pagesPerHour)
        assertNull(physicalReadingPace(listOf(log(30, 40, 120)), null, 40).remainingSeconds)
    }

    @Test
    fun invalidLogsDoNotSkewPace() {
        val logs = listOf(log(-10, 10, 60), log(10, 20, 0), log(10, 20, -60),
            log(null, 20, 60), log(10, null, 60), log(10, 20, 600))
        assertEquals(60.0, physicalReadingPace(logs, 100, 20).pagesPerHour)
        assertEquals(4800L, physicalReadingPace(logs, 100, 20).remainingSeconds)
    }

    private fun log(start: Int?, end: Int?, seconds: Long) = ReadingSession(1, "session", 1, 1000, 5000, seconds, start, end)
}
