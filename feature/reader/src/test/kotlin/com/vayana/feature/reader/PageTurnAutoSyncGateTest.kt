package com.vayana.feature.reader

import com.vayana.reader.api.Locator
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageTurnAutoSyncGateTest {
    @Test
    fun triggersAfterThreePageTransitions() {
        val gate = PageTurnAutoSyncGate(thresholdPages = { 3 })

        assertFalse(gate.onLocator(locator(page = 10)))
        assertFalse(gate.onLocator(locator(page = 11)))
        assertFalse(gate.onLocator(locator(page = 12)))
        assertTrue(gate.onLocator(locator(page = 13)))
    }

    @Test
    fun ignoresDuplicatePageReports() {
        val gate = PageTurnAutoSyncGate(thresholdPages = { 3 })

        assertFalse(gate.onLocator(locator(page = 1)))
        assertFalse(gate.onLocator(locator(page = 1)))
        assertFalse(gate.onLocator(locator(page = 2)))
        assertFalse(gate.onLocator(locator(page = 2)))
        assertFalse(gate.onLocator(locator(page = 3)))
        assertTrue(gate.onLocator(locator(page = 4)))
    }

    @Test
    fun jumpsCanSatisfyThreshold() {
        val gate = PageTurnAutoSyncGate(thresholdPages = { 3 })

        assertFalse(gate.onLocator(locator(page = 20)))
        assertTrue(gate.onLocator(locator(page = 23)))
    }

    @Test
    fun keepsRemainderFromLongJumps() {
        val gate = PageTurnAutoSyncGate(thresholdPages = { 3 })

        assertFalse(gate.onLocator(locator(page = 1)))
        assertTrue(gate.onLocator(locator(page = 5)))
        assertFalse(gate.onLocator(locator(page = 6)))
        assertTrue(gate.onLocator(locator(page = 7)))
    }

    @Test
    fun resetsAfterTrigger() {
        val gate = PageTurnAutoSyncGate(thresholdPages = { 3 })

        assertFalse(gate.onLocator(locator(page = 1)))
        assertTrue(gate.onLocator(locator(page = 4)))
        assertFalse(gate.onLocator(locator(page = 5)))
        assertFalse(gate.onLocator(locator(page = 6)))
        assertTrue(gate.onLocator(locator(page = 7)))
    }

    @Test
    fun skipsLocationsWithoutPageNumbers() {
        val gate = PageTurnAutoSyncGate(thresholdPages = { 3 })

        assertFalse(gate.onLocator(locator(page = 1)))
        assertFalse(gate.onLocator(locator(page = null)))
        assertFalse(gate.onLocator(locator(page = 2)))
        assertFalse(gate.onLocator(locator(page = 3)))
        assertTrue(gate.onLocator(locator(page = 4)))
    }

    @Test
    fun skipsInvalidPageNumbers() {
        val gate = PageTurnAutoSyncGate(thresholdPages = { 3 })

        assertFalse(gate.onLocator(locator(page = 1)))
        assertFalse(gate.onLocator(locator(page = 0)))
        assertFalse(gate.onLocator(locator(page = -1)))
        assertFalse(gate.onLocator(locator(page = 2)))
        assertFalse(gate.onLocator(locator(page = 3)))
        assertTrue(gate.onLocator(locator(page = 4)))
    }

    @Test
    fun zeroThresholdTurnsSyncOff() {
        var threshold = 0
        val gate = PageTurnAutoSyncGate(thresholdPages = { threshold })

        assertFalse(gate.onLocator(locator(page = 1)))
        assertFalse(gate.onLocator(locator(page = 10)))
        threshold = 3
        assertFalse(gate.onLocator(locator(page = 11)))
        assertFalse(gate.onLocator(locator(page = 12)))
        assertTrue(gate.onLocator(locator(page = 13)))
    }

    private fun locator(page: Int?): Locator =
        Locator(
            cfi = null,
            href = null,
            progression = 0f,
            chapterTitle = null,
            currentPage = page,
        )
}
