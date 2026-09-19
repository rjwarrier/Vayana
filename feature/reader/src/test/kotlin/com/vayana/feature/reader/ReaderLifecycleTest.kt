package com.vayana.feature.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderLifecycleTest {
    @Test
    fun `book engine stays active when screen turns off during read aloud`() {
        assertFalse(shouldPauseReaderWebView(readAloudPlaying = true))
    }

    @Test
    fun `book engine pauses normally when read aloud is not playing`() {
        assertTrue(shouldPauseReaderWebView(readAloudPlaying = false))
    }

    @Test
    fun `notification offers pause while speech is playing`() {
        kotlin.test.assertEquals(
            ReadAloudNotificationPlaybackAction.PAUSE,
            readAloudNotificationPlaybackAction(playing = true),
        )
    }

    @Test
    fun `notification offers play while speech is paused`() {
        kotlin.test.assertEquals(
            ReadAloudNotificationPlaybackAction.PLAY,
            readAloudNotificationPlaybackAction(playing = false),
        )
    }
}
