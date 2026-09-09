package com.vayana.feature.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        assertEquals(ReadingTimeUpdate(activeSessionSeconds = 10L), tracker.pause(30_000L))
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
}
