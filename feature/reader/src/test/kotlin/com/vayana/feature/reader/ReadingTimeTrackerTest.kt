package com.vayana.feature.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReadingTimeTrackerTest {
    private val tracker = ReadingTimeTracker(300_000L)

    @Test
    fun idleTimeStopsAtDeadlineEvenWhenTheTickerIsLate() {
        tracker.resume(0L)
        assertEquals(10L, tracker.flush(10_000L).addedSeconds)
        val idle = tracker.flush(3_600_000L)
        assertEquals(290L, idle.addedSeconds)
        assertEquals(CompletedReadingSession(0L, 300_000L), idle.session)
        assertEquals(ReadingTimeUpdate(), tracker.flush(7_200_000L))
        assertEquals(ReadingTimeUpdate(), tracker.pause(7_200_000L))
    }

    @Test
    fun pageTurnRestartsAfterIdleWithoutCountingTheGap() {
        tracker.resume(0L)
        val previous = tracker.interact(600_000L)
        assertEquals(300L, previous.addedSeconds)
        assertEquals(CompletedReadingSession(0L, 300_000L), previous.session)
        val next = tracker.pause(610_000L)
        assertEquals(10L, next.addedSeconds)
        assertEquals(CompletedReadingSession(600_000L, 610_000L), next.session)
    }

    @Test
    fun pageTurnExtendsTheIdleDeadline() {
        tracker.resume(0L)
        assertEquals(240L, tracker.interact(240_000L).addedSeconds)
        assertNull(tracker.flush(300_000L).session)
        assertEquals(CompletedReadingSession(0L, 540_000L), tracker.flush(540_000L).session)
    }

    @Test
    fun repeatedPauseDoesNotCountBackgroundTime() {
        tracker.resume(0L)
        assertEquals(10L, tracker.pause(10_000L).addedSeconds)
        assertEquals(ReadingTimeUpdate(), tracker.pause(600_000L))
        tracker.resume(700_000L)
        assertEquals(10L, tracker.pause(710_000L).addedSeconds)
    }

    @Test
    fun duplicateResumeDoesNotLoseElapsedTime() {
        tracker.resume(0L)
        tracker.resume(5_000L)
        assertEquals(10L, tracker.pause(10_000L).addedSeconds)
    }

    @Test
    fun frequentFlushesPreserveFractionalSeconds() {
        tracker.resume(0L)
        assertEquals(0L, tracker.flush(900L).addedSeconds)
        assertEquals(1L, tracker.flush(1_500L).addedSeconds)
        assertEquals(1L, tracker.pause(2_000L).addedSeconds)
    }
}
