package com.vayana.feature.reader

import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.designsystem.theme.DisplayProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderLifecycleTest {
    @Test
    fun `eink profile can disable reader audio features`() {
        val settings = SettingsSnapshot(
            displayProfile = DisplayProfile.E_INK,
            einkAudioFeaturesEnabled = false,
        )

        assertFalse(settings.readerAudioFeaturesEnabled)
    }

    @Test
    fun `standard profile keeps reader audio available regardless of eink preference`() {
        val settings = SettingsSnapshot(
            displayProfile = DisplayProfile.STANDARD,
            einkAudioFeaturesEnabled = false,
        )

        assertTrue(settings.readerAudioFeaturesEnabled)
    }

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

    @Test
    fun `notification permission is requested on Android 13 when missing`() {
        assertTrue(needsNotificationPermission(sdkInt = 33, permissionGranted = false))
    }

    @Test
    fun `notification permission is not requested when already granted`() {
        assertFalse(needsNotificationPermission(sdkInt = 37, permissionGranted = true))
    }

    @Test
    fun `notification permission is not required before Android 13`() {
        assertFalse(needsNotificationPermission(sdkInt = 32, permissionGranted = false))
    }

    @Test
    fun `read aloud progress is rounded to a whole percentage`() {
        kotlin.test.assertEquals(43, readAloudProgressPercent(0.426f))
    }

    @Test
    fun `read aloud progress stays within notification bounds`() {
        kotlin.test.assertEquals(100, readAloudProgressPercent(1.2f))
        kotlin.test.assertEquals(0, readAloudProgressPercent(-0.1f))
    }

    @Test
    fun `voice languages use stable locale tags rather than display labels`() {
        val voices = listOf(
            voice(name = "voice-a", localeTag = "en-US", localeLabel = "English"),
            voice(name = "voice-b", localeTag = "en-GB", localeLabel = "English"),
        )

        assertEquals(listOf("en-US", "en-GB"), speechLanguageOptions(voices).map { it.tag })
    }

    @Test
    fun `selected voice determines the displayed language`() {
        val voices = listOf(
            voice(name = "voice-a", localeTag = "en-US", localeLabel = "English (United States)", default = true),
            voice(name = "voice-b", localeTag = "fr-FR", localeLabel = "French (France)"),
        )

        assertEquals("fr-FR", resolveSpeechLanguageTag(voices, selectedVoiceName = "voice-b", previousTag = "en-US"))
    }

    @Test
    fun `system default locale is used when no voice is selected`() {
        val voices = listOf(
            voice(name = "voice-a", localeTag = "en-US", localeLabel = "English (United States)", default = true),
            voice(name = "voice-b", localeTag = "fr-FR", localeLabel = "French (France)"),
        )

        assertEquals("en-US", resolveSpeechLanguageTag(voices, selectedVoiceName = "", previousTag = ""))
    }

    private fun voice(
        name: String,
        localeTag: String,
        localeLabel: String,
        default: Boolean = false,
    ) = SpeechVoiceOption(
        name = name,
        localeTag = localeTag,
        localeLabel = localeLabel,
        requiresNetwork = false,
        isSystemDefault = default,
    )
}
