package com.vayana.feature.reader

import com.vayana.reader.api.BookEngine
import com.vayana.reader.api.SpeechChunk
import com.vayana.reader.api.SpeechSentence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the read-aloud bar shows. */
data class ReadAloudState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val rate: Float = 1f,
    val sleepTimerMinutes: Int = 0,
    val voiceMissing: Boolean = false,
)

/**
 * Reads the open book aloud a chapter at a time: the engine supplies sentences and highlights the one being
 * spoken (turning pages as speech moves on), [output] speaks them. State changes run on [scope].
 */
internal class ReadAloudPlayer(
    private val output: SpeechOutput,
    private val scope: CoroutineScope,
    private val engine: () -> BookEngine?,
    private val onSpeaking: () -> Unit,
) : SpeechOutput.Listener {
    private val _state = MutableStateFlow(ReadAloudState())
    val state: StateFlow<ReadAloudState> = _state

    private var queue: List<SpeechSentence> = emptyList()
    private var position = 0
    private var endOfBook = false
    private var sleepJob: Job? = null
    private var chunkJob: Job? = null

    init {
        output.listener = this
    }

    fun start(rate: Float) {
        if (_state.value.active) return
        val bookEngine = engine() ?: return
        _state.value = ReadAloudState(active = true, rate = rate)
        output.setRate(rate)
        output.prepare { available ->
            scope.launch {
                if (!_state.value.active) return@launch
                if (!available) {
                    _state.value = ReadAloudState(rate = rate, voiceMissing = true)
                    return@launch
                }
                load(bookEngine.startSpeech())
            }
        }
    }

    fun togglePlayback() {
        val current = _state.value
        if (!current.active) return
        if (current.playing) {
            _state.update { it.copy(playing = false) }
            output.stop()
        } else if (queue.isNotEmpty()) {
            _state.update { it.copy(playing = true) }
            speakFromPosition()
        }
    }

    fun pause() {
        if (_state.value.playing) togglePlayback()
    }

    fun stop() {
        sleepJob?.cancel()
        chunkJob?.cancel()
        output.stop()
        val wasActive = _state.value.active
        queue = emptyList()
        position = 0
        _state.value = ReadAloudState(rate = _state.value.rate)
        if (wasActive) scope.launch { engine()?.stopSpeech() }
    }

    fun dismissVoiceMissing() {
        _state.update { it.copy(voiceMissing = false) }
    }

    fun setRate(rate: Float) {
        output.setRate(rate)
        _state.update { it.copy(rate = rate) }
        // A new rate only applies to what the engine is asked to speak next, so restart the current sentence.
        if (_state.value.playing) speakFromPosition()
    }

    fun cycleSleepTimer() {
        val minutes = SleepTimerSteps[(SleepTimerSteps.indexOf(_state.value.sleepTimerMinutes) + 1) % SleepTimerSteps.size]
        sleepJob?.cancel()
        _state.update { it.copy(sleepTimerMinutes = minutes) }
        if (minutes > 0) {
            sleepJob = scope.launch {
                delay(minutes * 60_000L)
                pause()
                _state.update { it.copy(sleepTimerMinutes = 0) }
            }
        }
    }

    fun release() {
        stop()
        output.shutdown()
    }

    override fun onStart(utteranceId: String) {
        scope.launch {
            val index = queue.indexOfFirst { it.id == utteranceId }
            if (index < 0) return@launch
            position = index
            onSpeaking()
            engine()?.markSpeech(utteranceId.substringBefore(PartSeparator))
        }
    }

    override fun onDone(utteranceId: String) {
        scope.launch {
            if (_state.value.playing && queue.lastOrNull()?.id == utteranceId) nextChapter()
        }
    }

    private fun load(chunk: SpeechChunk) {
        queue = chunk.sentences.flatMap { it.splitForSpeech() }
        position = 0
        endOfBook = chunk.endOfBook
        if (queue.isEmpty()) {
            // A chapter with nothing to read (e.g. only an image): move straight on.
            nextChapter()
            return
        }
        _state.update { it.copy(playing = true) }
        speakFromPosition()
    }

    private fun nextChapter() {
        val bookEngine = engine()
        if (endOfBook || bookEngine == null) {
            stop()
            return
        }
        chunkJob = scope.launch { load(bookEngine.nextSpeechChunk()) }
    }

    private fun speakFromPosition() {
        queue.drop(position).forEachIndexed { offset, sentence ->
            output.speak(sentence.id, sentence.text, flush = offset == 0)
        }
    }
}

/** Splits a sentence longer than one utterance may be into parts whose ids keep the sentence id before [PartSeparator]. */
private fun SpeechSentence.splitForSpeech(): List<SpeechSentence> =
    if (text.length <= MaxUtteranceChars) {
        listOf(this)
    } else {
        text.chunked(MaxUtteranceChars).mapIndexed { part, partText -> SpeechSentence("$id$PartSeparator$part", partText) }
    }

private const val PartSeparator = '#'
private const val MaxUtteranceChars = 3_000
private val SleepTimerSteps = listOf(0, 15, 30, 60)
