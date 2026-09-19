package com.vayana.feature.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderVolumeKeyTest {
    @Test
    fun `volume keys control system volume while read aloud is playing`() {
        assertFalse(
            shouldInterceptReaderVolumeKey(
                readAloudPlaying = true,
                volumeKeysTurnPages = true,
                chromeVisible = false,
            ),
        )
    }

    @Test
    fun `volume keys keep turning pages during manual reading`() {
        assertTrue(
            shouldInterceptReaderVolumeKey(
                readAloudPlaying = false,
                volumeKeysTurnPages = true,
                chromeVisible = false,
            ),
        )
    }

    @Test
    fun `playing read aloud does not consume volume keys when reader chrome is visible`() {
        assertFalse(
            shouldInterceptReaderVolumeKey(
                readAloudPlaying = true,
                volumeKeysTurnPages = true,
                chromeVisible = true,
            ),
        )
    }
}
