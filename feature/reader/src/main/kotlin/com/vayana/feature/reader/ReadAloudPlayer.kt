package com.vayana.feature.reader

import com.vayana.reader.api.BookEngine
import com.vayana.reader.api.SpeechChunk
import com.vayana.reader.api.SpeechSentence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
    val pitch: Float = 1f,
    val sleepTimerMinutes: Int = 0,
    val voiceMissing: Boolean = false,
)

/**
 * Reads the open book aloud a chapter at a time: the engine supplies sentences and highlights the one being
 * spoken word by word (turning pages as speech moves on), [output] speaks them. State changes run on [scope].
 */
internal class ReadAloudPlayer(
    private val output: SpeechOutput,
    private val scope: CoroutineScope,
    private val engine: () -> BookEngine?,
    private val onSpeaking: () -> Unit,
    private val focus: PlaybackFocus,
) : SpeechOutput.Listener {
    private val _state = MutableStateFlow(ReadAloudState())
    val state: StateFlow<ReadAloudState> = _state
    val voices: StateFlow<List<SpeechVoiceOption>> = output.voices
    val engines: StateFlow<List<SpeechEngineOption>> = output.engines

    private var queue: List<SpeechUtterance> = emptyList()
    private var sourceSentences: List<SpeechSentence> = emptyList()
    private var pronunciations: List<SpeechPronunciation> = emptyList()
    private var pronouncer = SpeechPronouncer(emptyList())
    private var indexById = mutableMapOf<String, Int>()
    private var position = 0
    private var playbackGeneration = 0L
    private var queueGeneration = 0L

    /** Index in [queue] of the last utterance handed to the speech engine, which is only ever fed a few ahead. */
    private var enqueued = -1
    private var endOfBook = false

    /** Set once the speech engine sends a word range; not every engine does. */
    private var reportsWordRanges = false

    /** Off for E-Ink: a highlight that moves every word is a partial panel refresh every few hundred ms. */
    private var wordHighlight = true
    private var sleepJob: Job? = null
    private var initialJob: Job? = null
    private var chunkJob: Job? = null
    private var chunkRequest: Any? = null

    /** Paused only because another app took audio focus for a moment, so reading picks up again when it is back. */
    private var resumeOnFocusGain = false

    init {
        output.listener = this
    }

    /** With [wordHighlight] off the page marks the sentence being read instead of following each word. */
    fun start(
        rate: Float,
        pitch: Float,
        voiceName: String,
        fromCfi: String? = null,
        speechEngine: String = "",
        wordHighlight: Boolean = true,
    ) {
        if (_state.value.active) return
        val bookEngine = engine() ?: return
        val generation = ++playbackGeneration
        this.wordHighlight = wordHighlight
        output.setEngine(speechEngine)
        _state.value = ReadAloudState(active = true, rate = rate, pitch = pitch)
        output.setRate(rate)
        output.setPitch(pitch)
        output.setVoice(voiceName)
        output.prepare { available ->
            val job = scope.launch(start = CoroutineStart.LAZY) {
                if (generation != playbackGeneration || !_state.value.active) return@launch
                if (!available) {
                    _state.value = ReadAloudState(rate = rate, pitch = pitch, voiceMissing = true)
                    return@launch
                }
                val chunk = bookEngine.startSpeech(fromCfi)
                if (generation == playbackGeneration && _state.value.active) load(chunk)
            }
            initialJob = job
            job.start()
        }
    }

    fun togglePlayback() {
        val current = _state.value
        if (!current.active) return
        resumeOnFocusGain = false
        if (current.playing) {
            _state.update { it.copy(playing = false) }
            indexById.clear()
            output.stop()
            focus.abandon()
        } else if (queue.isNotEmpty()) {
            resume()
        }
    }

    fun pause() {
        if (_state.value.playing) togglePlayback()
    }

    /** Plays if paused, from the sentence that was being read. */
    fun play() {
        if (_state.value.active && !_state.value.playing) togglePlayback()
    }

    /** Jumps [sentences] forward (positive) or back (negative) and reads from there; past the chapter's end, moves on. */
    fun skip(sentences: Int) {
        if (!_state.value.active || queue.isEmpty()) return
        val target = position + sentences
        if (target > queue.lastIndex) {
            if (!endOfBook) {
                resumeOnFocusGain = false
                output.stop()
                nextChapter()
            }
            return
        }
        position = target.coerceAtLeast(0)
        resumeOnFocusGain = false
        resume()
    }

    fun stop() {
        ++playbackGeneration
        sleepJob?.cancel()
        initialJob?.cancel()
        initialJob = null
        chunkJob?.cancel()
        chunkJob = null
        chunkRequest = null
        output.stop()
        focus.abandon()
        resumeOnFocusGain = false
        val wasActive = _state.value.active
        queue = emptyList()
        sourceSentences = emptyList()
        indexById.clear()
        position = 0
        enqueued = -1
        _state.value = ReadAloudState(rate = _state.value.rate, pitch = _state.value.pitch)
        if (wasActive) {
            val stoppedGeneration = playbackGeneration
            val bookEngine = engine()
            scope.launch { if (stoppedGeneration == playbackGeneration) bookEngine?.stopSpeech() }
        }
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

    fun setPitch(pitch: Float) {
        output.setPitch(pitch)
        _state.update { it.copy(pitch = pitch) }
        // Android only applies pitch to newly queued speech, so restart the current sentence.
        if (_state.value.playing) speakFromPosition()
    }

    /** Reads with the text-to-speech engine of package [name] (blank: default). A different engine ends any reading in progress. */
    fun useEngine(name: String) {
        if (!output.setEngine(name)) return
        if (_state.value.active) stop()
        reportsWordRanges = false
    }

    fun loadVoices() {
        output.prepare { }
    }

    fun setVoice(name: String) {
        output.setVoice(name)
        // A different voice may come from an engine that does (or does not) report word ranges; learn it again.
        reportsWordRanges = false
        if (_state.value.playing) speakFromPosition()
    }

    fun setPronunciations(rules: List<SpeechPronunciation>) {
        val next = normalizedPronunciations(rules)
        if (next == pronunciations) return
        pronunciations = next
        pronouncer = SpeechPronouncer(next)
        val current = queue.getOrNull(position)
        val sourceStart = current?.sourceRange(0, 1)?.first ?: 0
        queue = sourceSentences.flatMap { it.splitForSpeech(pronouncer) }
        position = queue.indexOfFirst {
            it.sourceId == current?.sourceId && it.sourceRange(0, it.text.length).second > sourceStart
        }.coerceAtLeast(0)
        indexById.clear()
        output.stop()
        if (_state.value.playing && queue.isNotEmpty()) speakFromPosition()
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
            if (!_state.value.playing) return@launch
            if (utteranceId.endsWith(":pause")) {
                // A pause belongs to the upcoming sentence. Pausing/focus loss during it must resume that
                // sentence, rather than replaying the preceding paragraph. No highlighting occurs here.
                indexById[utteranceId.removeSuffix(":pause")]?.let { position = it }
                return@launch
            }
            val index = indexById[utteranceId] ?: return@launch
            position = index
            // The engine was fed only a few sentences ahead; keep it that far ahead as reading moves on.
            if (_state.value.playing) enqueueAhead()
            onSpeaking()
            val utterance = queue[index]
            // Until the engine has proven it reports word timings, highlight the whole sentence: engines that never
            // send them would otherwise show only the first word. Word timings then narrow it as they arrive.
            val range = if (wordHighlight && reportsWordRanges) firstSpokenWordRange(utterance.text) else 0 until utterance.text.length
            if (range != null && !range.isEmpty()) {
                engine()?.markSpeech(
                    utterance.sourceId,
                    utterance.sourceRange(range.first, range.last + 1).first,
                    utterance.sourceRange(range.first, range.last + 1).second,
                )
            }
        }
    }

    override fun onRangeStart(utteranceId: String, start: Int, end: Int) {
        if (!wordHighlight) return
        scope.launch {
            if (!_state.value.playing) return@launch
            val utterance = indexById[utteranceId]?.let(queue::get) ?: return@launch
            reportsWordRanges = true
            val safeStart = start.coerceIn(0, utterance.text.length)
            val safeEnd = end.coerceIn(safeStart, utterance.text.length)
            if (safeStart == safeEnd) return@launch
            engine()?.markSpeech(
                utterance.sourceId,
                utterance.sourceRange(safeStart, safeEnd).first,
                utterance.sourceRange(safeStart, safeEnd).second,
            )
        }
    }

    override fun onDone(utteranceId: String) {
        scope.launch {
            if (_state.value.playing && indexById[utteranceId] == queue.lastIndex) nextChapter()
        }
    }

    /** Starts or resumes speaking at [position], unless another app (a call, say) will not give up the audio. */
    private fun resume() {
        if (!focus.request(::onFocusEvent)) {
            _state.update { it.copy(playing = false) }
            return
        }
        _state.update { it.copy(playing = true) }
        speakFromPosition()
    }

    private fun onFocusEvent(event: PlaybackFocusEvent) {
        val current = _state.value
        when (event) {
            PlaybackFocusEvent.LOST_TEMPORARILY -> if (current.playing) {
                resumeOnFocusGain = true
                _state.update { it.copy(playing = false) }
                indexById.clear()
                output.stop()
            }
            PlaybackFocusEvent.LOST, PlaybackFocusEvent.BECOMING_NOISY -> if (current.playing) togglePlayback()
            PlaybackFocusEvent.REGAINED -> if (resumeOnFocusGain && current.active && !current.playing) {
                resumeOnFocusGain = false
                resume()
            }
        }
    }

    private fun load(chunk: SpeechChunk) {
        sourceSentences = chunk.sentences
        queue = sourceSentences.flatMap { it.splitForSpeech(pronouncer) }
        indexById.clear()
        position = 0
        enqueued = -1
        endOfBook = chunk.endOfBook
        if (queue.isEmpty()) {
            // A chapter with nothing to read (e.g. only an image): move straight on.
            nextChapter()
            return
        }
        resume()
    }

    private fun nextChapter() {
        val bookEngine = engine()
        if (endOfBook || bookEngine == null) {
            stop()
            return
        }
        if (chunkRequest != null) return
        val request = Any()
        val generation = playbackGeneration
        chunkRequest = request
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val chunk = bookEngine.nextSpeechChunk()
                if (chunkRequest !== request || generation != playbackGeneration || !_state.value.active) return@launch
                chunkRequest = null
                load(chunk)
            } finally {
                if (chunkRequest === request) chunkRequest = null
            }
        }
        chunkJob = job
        job.start()
    }

    /** Restarts speech at [position]. Each hand-over to the engine is a binder call, so only the next few are sent. */
    private fun speakFromPosition() {
        ++queueGeneration
        indexById.clear()
        enqueued = position - 1
        enqueueAhead(flush = true)
    }

    /** Tops the engine's queue up to [LookaheadUtterances] utterances from [position]; [flush] drops what it held. */
    private fun enqueueAhead(flush: Boolean = false) {
        val last = minOf(position + LookaheadUtterances - 1, queue.lastIndex)
        var first = flush
        while (enqueued < last) {
            enqueued++
            val utterance = queue[enqueued]
            val id = "$playbackGeneration-$queueGeneration|${utterance.id}"
            indexById[id] = enqueued
            // Do not repeat the paragraph pause when resuming/skipping directly to a sentence. Silence IDs
            // are deliberately absent from indexById: their callbacks cannot mark words or advance chapters.
            if (utterance.pauseBeforeMs > 0 && !first) {
                output.silence("$id:pause", utterance.pauseBeforeMs, flush = false)
            }
            output.speak(id, utterance.text, flush = first)
            first = false
        }
    }
}

/** Splits a sentence longer than one utterance may be into parts whose ids keep the sentence id before [PartSeparator]. */
internal data class SpeechUtterance(
    val id: String,
    val text: String,
    val sourceId: String,
    val sourceOffset: Int,
    val pauseBeforeMs: Long = 0,
    val pronounced: PronouncedSpeechText = PronouncedSpeechText(text),
) {
    fun sourceRange(start: Int, end: Int): Pair<Int, Int> = pronounced.sourceRange(sourceOffset + start, sourceOffset + end)
}

internal fun SpeechSentence.splitForSpeech(pronunciations: List<SpeechPronunciation> = emptyList()): List<SpeechUtterance> =
    splitForSpeech(SpeechPronouncer(pronunciations))

internal fun SpeechSentence.splitForSpeech(pronouncer: SpeechPronouncer): List<SpeechUtterance> {
    val pronounced = pronouncer.prepare(this.text)
    val text = pronounced.text
    val parts = mutableListOf<SpeechUtterance>()
    var start = 0
    while (start < text.length) {
        var end = minOf(start + MaxUtteranceChars, text.length)
        if (end < text.length) {
            // Prefer a clause boundary in the latter half, otherwise split between words. A pathological
            // token longer than the engine limit still needs a hard split, but never between a surrogate pair.
            val minimum = start + MaxUtteranceChars / 2
            val clause = (end - 1 downTo minimum).firstOrNull {
                text[it].isWhitespace() && text[it - 1] in ",;:!?—"
            }
            val space = clause ?: (end - 1 downTo start + 1).firstOrNull { text[it].isWhitespace() }
            if (space != null) end = space + 1
            if (Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
        }
        parts += SpeechUtterance(
            id = if (text.length <= MaxUtteranceChars) id else "$id$PartSeparator${parts.size}",
            text = text.substring(start, end), sourceId = id, sourceOffset = start,
            pauseBeforeMs = if (start == 0) pauseBeforeMs else 0,
            pronounced = pronounced,
        )
        start = end
    }
    return parts
}

private fun firstSpokenWordRange(text: String): IntRange? = SpokenWordRegex.find(text)?.range

private const val PartSeparator = '#'
private const val MaxUtteranceChars = 3_000
private const val LookaheadUtterances = 8
private val SleepTimerSteps = listOf(0, 15, 30, 60)
private val SpokenWordRegex = Regex("[\\p{L}\\p{N}]+(?:['’\\-][\\p{L}\\p{N}]+)*")
