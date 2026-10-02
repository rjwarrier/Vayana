package com.vayana.feature.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadingPaceTest {
    private var clock = 0L
    private val tracker = ReadingPaceTracker { clock }

    @Test
    fun theFixedEstimateAppliesUntilThereIsEnoughReading() {
        assertEquals(1f, ReadingPace.factor(ReadingPaceTotals()))
        assertEquals(1f, ReadingPace.factor(ReadingPaceTotals(actualSeconds = 100f, estimatedSeconds = 200f)))
        assertEquals(1.5f, ReadingPace.factor(ReadingPaceTotals(actualSeconds = 1_500f, estimatedSeconds = 1_000f)))
        assertEquals(0.5f, ReadingPace.factor(ReadingPaceTotals(actualSeconds = 600f, estimatedSeconds = 1_200f)))
    }

    @Test
    fun theFactorStaysInAHumanRange() {
        assertEquals(2.5f, ReadingPace.factor(ReadingPaceTotals(actualSeconds = 50_000f, estimatedSeconds = 1_000f)))
        assertEquals(0.4f, ReadingPace.factor(ReadingPaceTotals(actualSeconds = 100f, estimatedSeconds = 1_000f)))
    }

    @Test
    fun scalingRoundsUpAndNeverEmptiesARealEstimate() {
        assertEquals(15, ReadingPace.scaled(10, 1.5f))
        assertEquals(6, ReadingPace.scaled(10, 0.55f))
        assertEquals(1, ReadingPace.scaled(1, 0.4f))
        assertEquals(0, ReadingPace.scaled(0, 2f))
    }

    @Test
    fun aPageTurnAtAReadingPaceIsASample() {
        assertNull(tracker.onRelocate(100.0))
        clock += 30_000
        // 0.4 minutes (24 s) of estimated reading took 30 s.
        val sample = assertNotNull(tracker.onRelocate(99.6))
        assertEquals(30f, sample.actualSeconds, 0.01f)
        assertEquals(24f, sample.estimatedSeconds, 0.01f)
    }

    @Test
    fun jumpsGapsAndFlippingGiveNoSample() {
        tracker.onRelocate(100.0)
        clock += 20_000
        assertNull(tracker.onRelocate(60.0), "a chapter skip")
        clock += 20_000
        assertNull(tracker.onRelocate(61.0), "going back")
        clock += 20 * 60_000
        assertNull(tracker.onRelocate(60.7), "the phone was put down")
        clock += 1_000
        assertNull(tracker.onRelocate(60.3), "a flip far faster than reading")
        clock += 25_000
        assertNotNull(tracker.onRelocate(59.9))
    }

    @Test
    fun resettingForgetsTheLastPosition() {
        tracker.onRelocate(100.0)
        tracker.reset()
        clock += 20_000
        assertNull(tracker.onRelocate(99.7))
    }

    @Test
    fun aSlowReaderEndsUpWithAFactorAboveOne() {
        var totals = ReadingPaceTotals()
        repeat(60) { totals = ReadingPace.add(totals, PaceSample(actualSeconds = 30f, estimatedSeconds = 20f)) }
        val factor = ReadingPace.factor(totals)
        assertTrue(factor in 1.45f..1.55f, "factor $factor")
    }
}
