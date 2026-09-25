package com.vayana.feature.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderTapBehaviorTest {
    @Test
    fun `reader tap pauses read aloud while it is playing`() {
        assertTrue(shouldPauseReadAloudOnReaderTap(readAloudPlaying = true))
    }

    @Test
    fun `reader tap keeps its normal action while read aloud is not playing`() {
        assertFalse(shouldPauseReadAloudOnReaderTap(readAloudPlaying = false))
    }
}
