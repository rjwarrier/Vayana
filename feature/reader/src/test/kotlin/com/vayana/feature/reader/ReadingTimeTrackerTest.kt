package com.vayana.feature.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals

class ReadingTimeTrackerTest {
    private val tracker = ReadingTimeTracker(
        idleTimeoutMillis = 300_000L,
        continuationGraceMillis = 60_000L,
    )

    @Test
    fun idleTimeStopsAtDeadlineEvenWhenTheTickerIsLate() {
        tracker.interact(0L)
        assertEquals(10L, tracker.flush(10_000L).addedSeconds)
        val idle = tracker.flush(3_600_000L)
        assertEquals(290L, idle.addedSeconds)
        assertEquals(CompletedReadingSession(0L, 300_000L, 300L), idle.session)
        assertEquals(ReadingTimeUpdate(), tracker.flush(7_200_000L))
        assertEquals(ReadingTimeUpdate(), tracker.finish(7_200_000L))
    }

    @Test
    fun interactionRestartsAfterIdleWithoutCountingTheGap() {
        tracker.interact(0L)
        val previous = tracker.interact(600_000L)
        assertEquals(300L, previous.addedSeconds)
        assertEquals(CompletedReadingSession(0L, 300_000L, 300L), previous.session)
        val next = tracker.finish(610_000L)
        assertEquals(10L, next.addedSeconds)
        assertEquals(CompletedReadingSession(600_000L, 610_000L, 10L), next.session)
    }

    @Test
    fun interactionExtendsTheIdleDeadline() {
        tracker.interact(0L)
        assertEquals(240L, tracker.interact(240_000L).addedSeconds)
        assertNull(tracker.flush(300_000L).session)
        assertEquals(CompletedReadingSession(0L, 540_000L, 540L), tracker.flush(540_000L).session)
    }

    @Test
    fun pauseDoesNotCountBackgroundTime() {
        tracker.interact(0L)
        assertEquals(10L, tracker.pause(10_000L).addedSeconds)
        val paused = tracker.pause(30_000L)
        assertEquals(0L, paused.addedSeconds)
        assertEquals(10L, paused.activeSessionSeconds)
        assertEquals(10L, paused.checkpoint?.durationSeconds)
        assertEquals(CompletedReadingSession(0L, 10_000L, 10L), tracker.flush(80_001L).session)
        tracker.interact(700_000L)
        assertEquals(10L, tracker.finish(710_000L).addedSeconds)
    }

    @Test
    fun quickReturnContinuesTheSameSessionWithoutCountingAwayTime() {
        tracker.interact(0L)
        assertEquals(10L, tracker.pause(10_000L).addedSeconds)
        assertEquals(0L, tracker.interact(50_000L).addedSeconds)
        val finished = tracker.finish(60_000L)
        assertEquals(10L, finished.addedSeconds)
        assertEquals(CompletedReadingSession(0L, 60_000L, 20L), finished.session)
    }

    @Test
    fun frequentFlushesPreserveFractionalSeconds() {
        tracker.interact(0L)
        assertEquals(0L, tracker.flush(900L).addedSeconds)
        assertEquals(1L, tracker.flush(1_500L).addedSeconds)
        assertEquals(1L, tracker.finish(2_000L).addedSeconds)
    }

    @Test
    fun openingReaderDoesNotStartClockUntilInteraction() {
        assertEquals(ReadingTimeUpdate(), tracker.flush(10_000L))
        assertEquals(ReadingTimeUpdate(), tracker.pause(20_000L))
        assertEquals(ReadingTimeUpdate(), tracker.finish(30_000L))
    }

    @Test
    fun activeAndPausedReadingHaveDurableCumulativeCheckpointsBeforeCompletion() {
        tracker.interact(1_000L)
        val first = assertNotNull(tracker.flush(61_000L).checkpoint)
        assertEquals(60L, first.durationSeconds)
        val paused = tracker.pause(121_000L)
        assertNull(paused.session)
        assertEquals(first.syncId, paused.checkpoint?.syncId)
        assertEquals(120L, paused.checkpoint?.durationSeconds)
        // No subsequent callback is necessary to save the time before leaving the app.
        tracker.interact(151_000L)
        val completed = tracker.finish(181_000L)
        assertEquals(first.syncId, completed.checkpoint?.syncId)
        assertEquals(150L, completed.checkpoint?.durationSeconds)
        assertEquals(150L, completed.session?.durationSeconds)
    }

    @Test
    fun aNewSessionHasANewIdentityAndOldCheckpointsRemainImmutable() {
        tracker.interact(1_000L)
        val first = assertNotNull(tracker.finish(61_000L).checkpoint)
        tracker.interact(62_000L)
        val second = assertNotNull(tracker.flush(122_000L).checkpoint)
        assertNotEquals(first.syncId, second.syncId)
        assertEquals(60L, first.durationSeconds)
        assertEquals(60L, second.durationSeconds)
    }
}
