package com.vayana.feature.reader

import androidx.compose.runtime.Immutable

/** What the Read Aloud panel needs to choose an engine and a voice: the options and the ways to change them. */
@Immutable
internal class ReadAloudVoiceControls(
    val voices: List<SpeechVoiceOption>,
    val engines: List<SpeechEngineOption>,
    val onLoad: () -> Unit,
    val onVoiceChange: (String) -> Unit,
    val onEngineChange: (String) -> Unit,
)
