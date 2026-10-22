package com.vayana.feature.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecognitionRefreshTest {
    @Test
    fun lineJoinsFinishingRefreshesEvenWhenEveryPageWasAlreadyRecognized() {
        val refresh = RecognitionRefresh()
        assertFalse(refresh.update(99, false))
        assertTrue(refresh.update(100, false))
        assertFalse(refresh.update(100, false))
        assertTrue(refresh.update(100, true))
        assertFalse(refresh.update(100, true))
    }

    @Test
    fun aCompletedBookRefreshesOnceWhenFirstObserved() {
        val refresh = RecognitionRefresh()
        assertTrue(refresh.update(100, true))
        assertFalse(refresh.update(100, true))
    }
}
