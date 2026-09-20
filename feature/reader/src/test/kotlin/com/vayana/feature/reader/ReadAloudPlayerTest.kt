package com.vayana.feature.reader

import com.vayana.reader.api.BookEngine
import com.vayana.reader.api.BookSource
import com.vayana.reader.api.BookStyle
import com.vayana.reader.api.EngineEvent
import com.vayana.reader.api.Locator
import com.vayana.reader.api.MergedRange
import com.vayana.reader.api.NavTarget
import com.vayana.reader.api.OpenBook
import com.vayana.reader.api.ReadTheme
import com.vayana.reader.api.ReaderAnnotation
import com.vayana.reader.api.SpeechChunk
import com.vayana.reader.api.SpeechSentence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

class ReadAloudPlayerTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun readsChapterThenMovesToTheNextAndStopsAtTheEnd() {
        val output = FakeOutput()
        val engine = FakeEngine(listOf(listOf("One.", "Two."), listOf("Three.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        assertEquals(listOf("0:0", "0:1"), output.queued)
        assertTrue(player.state.value.playing)

        output.listener!!.onStart("0:1")
        assertEquals(listOf(MarkedSpeech("0:1", 0, 4)), engine.marked)

        output.listener!!.onDone("0:1")
        assertEquals(listOf("1:0"), output.queued)

        output.listener!!.onDone("1:0")
        assertFalse(player.state.value.active)
        assertTrue(engine.stopped)
    }

    @Test
    fun resumesFromTheSentenceThatWasBeingSpoken() {
        val output = FakeOutput()
        val engine = FakeEngine(listOf(listOf("One.", "Two.", "Three.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        output.listener!!.onStart("0:1")
        player.togglePlayback()
        assertFalse(player.state.value.playing)

        player.togglePlayback()
        assertTrue(player.state.value.playing)
        assertEquals(listOf("0:1", "0:2"), output.queued)
    }

    @Test
    fun missingVoiceAsksForOneInsteadOfReading() {
        val output = FakeOutput(available = false)
        val engine = FakeEngine(listOf(listOf("One.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "")

        assertTrue(player.state.value.voiceMissing)
        assertFalse(player.state.value.active)
        assertTrue(output.queued.isEmpty())
    }

    @Test
    fun skipsChaptersWithNothingToRead() {
        val output = FakeOutput()
        // One engine for the whole test, as in the reader: the player asks it for each next chapter in turn.
        val engine = FakeEngine(listOf(listOf("A."), emptyList(), listOf("B.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        output.listener!!.onDone("0:0")

        assertEquals(listOf("2:0"), output.queued)
    }

    @Test
    fun highlightsTheTimedWordRangeReportedByTts() {
        val output = FakeOutput()
        val engine = FakeEngine(listOf(listOf("Hello world.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        output.listener!!.onRangeStart("0:0", 6, 11)

        assertEquals(MarkedSpeech("0:0", 6, 11), engine.marked.last())
    }

    @Test
    fun highlightsTheWholeSentenceWhileTheEngineSendsNoWordRanges() {
        val output = FakeOutput()
        val engine = FakeEngine(listOf(listOf("Hello world.", "Second one.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        output.listener!!.onStart("0:0")
        output.listener!!.onStart("0:1")

        assertEquals(listOf(MarkedSpeech("0:0", 0, 12), MarkedSpeech("0:1", 0, 11)), engine.marked)
    }

    @Test
    fun narrowsToTheFirstWordOnceTheEngineHasSentWordRanges() {
        val output = FakeOutput()
        val engine = FakeEngine(listOf(listOf("Hello world.", "Second one.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        output.listener!!.onStart("0:0")
        output.listener!!.onRangeStart("0:0", 0, 5)
        output.listener!!.onStart("0:1")

        assertEquals(
            listOf(MarkedSpeech("0:0", 0, 12), MarkedSpeech("0:0", 0, 5), MarkedSpeech("0:1", 0, 6)),
            engine.marked,
        )
    }

    @Test
    fun timedRangeInSplitUtteranceMapsBackToTheSentence() {
        val output = FakeOutput()
        val engine = FakeEngine(listOf(listOf("a".repeat(3_001))))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        output.listener!!.onRangeStart("0:0#1", 0, 1)

        assertEquals(MarkedSpeech("0:0", 3_000, 3_001), engine.marked.last())
    }

    @Test
    fun appliesPitchAndRestartsTheCurrentSentenceWhenItChanges() {
        val output = FakeOutput()
        val engine = FakeEngine(listOf(listOf("One.", "Two.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 0.8f, voiceName = "")
        output.listener!!.onStart("0:1")
        player.setPitch(1.2f)

        assertEquals(listOf(0.8f, 1.2f), output.pitches)
        assertEquals(listOf("0:1"), output.queued)
        assertEquals(1.2f, player.state.value.pitch)
    }

    @Test
    fun appliesRateAndRestartsTheCurrentSentenceWhenItChanges() {
        val output = FakeOutput()
        val engine = FakeEngine(listOf(listOf("One.", "Two.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        output.listener!!.onStart("0:1")
        player.setRate(1.5f)

        assertEquals(listOf(1f, 1.5f), output.rates)
        assertEquals(listOf("0:1"), output.queued)
        assertEquals(1.5f, player.state.value.rate)
    }

    @Test
    fun appliesSelectedVoiceAndRestartsTheCurrentSentenceWhenItChanges() {
        val output = FakeOutput()
        val engine = FakeEngine(listOf(listOf("One.", "Two.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "voice-a")
        output.listener!!.onStart("0:1")
        player.setVoice("voice-b")

        assertEquals(listOf<String?>("voice-a", "voice-b"), output.voiceNames)
        assertEquals(listOf("0:1"), output.queued)
    }

    @Test
    fun startsAtTheGivenPositionWhenReadingFromASelection() {
        val output = FakeOutput()
        val engine = FakeEngine(listOf(listOf("One.", "Two.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "", fromCfi = "epubcfi(/6/4!/4/2)")

        assertEquals("epubcfi(/6/4!/4/2)", engine.startedFrom)
    }

    @Test
    fun skipsToTheNextAndPreviousSentence() {
        val output = FakeOutput()
        val engine = FakeEngine(listOf(listOf("One.", "Two.", "Three.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        output.listener!!.onStart("0:0")
        player.skip(1)
        assertEquals(listOf("0:1", "0:2"), output.queued)

        output.listener!!.onStart("0:1")
        player.skip(-1)
        assertEquals(listOf("0:0", "0:1", "0:2"), output.queued)
    }

    @Test
    fun skippingBackFromTheFirstSentenceRestartsIt() {
        val output = FakeOutput()
        val engine = FakeEngine(listOf(listOf("One.", "Two.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        output.listener!!.onStart("0:0")
        player.skip(-1)

        assertEquals(listOf("0:0", "0:1"), output.queued)
    }

    @Test
    fun skippingPastTheLastSentenceMovesToTheNextChapter() {
        val output = FakeOutput()
        val engine = FakeEngine(listOf(listOf("One.", "Two."), listOf("Three.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        output.listener!!.onStart("0:1")
        player.skip(1)

        assertEquals(listOf("1:0"), output.queued)
    }

    @Test
    fun pausesForAnotherAppAndResumesWhenItIsDone() {
        val output = FakeOutput()
        val focus = FakeFocus()
        val engine = FakeEngine(listOf(listOf("One.", "Two.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, focus)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        output.listener!!.onStart("0:1")
        focus.send(PlaybackFocusEvent.LOST_TEMPORARILY)
        assertFalse(player.state.value.playing)

        focus.send(PlaybackFocusEvent.REGAINED)
        assertTrue(player.state.value.playing)
        assertEquals(listOf("0:1"), output.queued)
    }

    @Test
    fun staysPausedAfterAnotherAppTakesFocusForGood() {
        val output = FakeOutput()
        val focus = FakeFocus()
        val engine = FakeEngine(listOf(listOf("One.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, focus)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        focus.send(PlaybackFocusEvent.LOST)
        focus.send(PlaybackFocusEvent.REGAINED)

        assertFalse(player.state.value.playing)
        assertEquals(1, focus.abandoned)
    }

    @Test
    fun pausesWhenTheHeadphonesAreUnplugged() {
        val output = FakeOutput()
        val focus = FakeFocus()
        val engine = FakeEngine(listOf(listOf("One.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, focus)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        focus.send(PlaybackFocusEvent.BECOMING_NOISY)

        assertFalse(player.state.value.playing)
    }

    @Test
    fun aPauseDuringATemporaryLossIsNotUndoneWhenFocusReturns() {
        val output = FakeOutput()
        val focus = FakeFocus()
        val engine = FakeEngine(listOf(listOf("One.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, focus)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        focus.send(PlaybackFocusEvent.LOST_TEMPORARILY)
        player.togglePlayback()
        player.togglePlayback()
        player.pause()
        focus.send(PlaybackFocusEvent.REGAINED)

        assertFalse(player.state.value.playing)
    }

    @Test
    fun doesNotStartWhileAnotherAppRefusesToShareAudio() {
        val output = FakeOutput()
        val focus = FakeFocus(granted = false)
        val engine = FakeEngine(listOf(listOf("One.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, focus)

        player.start(rate = 1f, pitch = 1f, voiceName = "")

        assertFalse(player.state.value.playing)
        assertTrue(output.queued.isEmpty())
    }

    @Test
    fun letsGoOfAudioFocusWhenPausedOrStopped() {
        val output = FakeOutput()
        val focus = FakeFocus()
        val engine = FakeEngine(listOf(listOf("One.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, focus)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        player.pause()
        assertEquals(1, focus.abandoned)

        player.stop()
        assertEquals(2, focus.abandoned)
    }

    private fun longChapter(sentences: Int) = FakeEngine(listOf((1..sentences).map { "Sentence $it." }))

    @Test
    fun handsTheEngineOnlyTheNextFewSentencesOfALongChapter() {
        val output = FakeOutput()
        val player = ReadAloudPlayer(output, scope, { longChapter(500) }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "")

        assertEquals((0 until 8).map { "0:$it" }, output.queued)
    }

    @Test
    fun keepsTheEngineAFewSentencesAheadAsReadingMovesOn() {
        val output = FakeOutput()
        val player = ReadAloudPlayer(output, scope, { longChapter(500) }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        output.listener!!.onStart("0:0")
        output.listener!!.onStart("0:1")
        output.listener!!.onStart("0:2")

        assertEquals((0 until 10).map { "0:$it" }, output.queued)
    }

    @Test
    fun skippingInALongChapterFlushesAndRefillsOnlyTheWindow() {
        val output = FakeOutput()
        val player = ReadAloudPlayer(output, scope, { longChapter(500) }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        output.listener!!.onStart("0:5")
        player.skip(1)

        assertEquals((6 until 14).map { "0:$it" }, output.queued)
    }

    @Test
    fun aRateChangeMidChapterRestartsTheCurrentSentenceWithoutResendingTheChapter() {
        val output = FakeOutput()
        val player = ReadAloudPlayer(output, scope, { longChapter(500) }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        output.listener!!.onStart("0:40")
        player.setRate(1.5f)

        assertEquals((40 until 48).map { "0:$it" }, output.queued)
    }

    @Test
    fun theChapterOnlyEndsAfterItsLastSentenceHasBeenSpoken() {
        val output = FakeOutput()
        val engine = FakeEngine(listOf((1..20).map { "Sentence $it." }, listOf("Next chapter.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "")
        output.listener!!.onDone("0:7")
        assertEquals((0 until 8).map { "0:$it" }, output.queued)

        (0..19).forEach { output.listener!!.onStart("0:$it") }
        output.listener!!.onDone("0:19")

        assertEquals(listOf("1:0"), output.queued)
    }

    @Test
    fun startsWithTheChosenSpeechEngine() {
        val output = FakeOutput()
        val player = ReadAloudPlayer(output, scope, { longChapter(3) }, {}, PlaybackFocus.Unmanaged)

        player.start(rate = 1f, pitch = 1f, voiceName = "", speechEngine = "org.example.tts")

        assertEquals("org.example.tts", output.engineName)
    }

    @Test
    fun changingTheEngineEndsTheReadingInProgressAndRelearnsWordRanges() {
        val output = FakeOutput()
        val engine = FakeEngine(listOf(listOf("Hello world.", "Second one.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, PlaybackFocus.Unmanaged)
        player.start(rate = 1f, pitch = 1f, voiceName = "")
        output.listener!!.onRangeStart("0:0", 0, 5)

        player.useEngine("org.example.tts")

        assertFalse(player.state.value.active)
        assertEquals("org.example.tts", output.engineName)

        // A new engine has to prove it reports word ranges again: the first sentence is marked whole.
        engine.marked.clear()
        player.start(rate = 1f, pitch = 1f, voiceName = "", speechEngine = "org.example.tts")
        output.listener!!.onStart("0:0")
        assertEquals(MarkedSpeech("0:0", 0, 12), engine.marked.last())
    }

    @Test
    fun selectingTheEngineAlreadyInUseChangesNothing() {
        val output = FakeOutput()
        val player = ReadAloudPlayer(output, scope, { longChapter(3) }, {}, PlaybackFocus.Unmanaged)
        player.start(rate = 1f, pitch = 1f, voiceName = "")

        player.useEngine("")

        assertTrue(player.state.value.active)
    }

    @Test
    fun withoutWordHighlightEachSentenceIsMarkedWholeAndWordRangesAreIgnored() {
        val output = FakeOutput()
        val engine = FakeEngine(listOf(listOf("Hello world.", "Second one.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {}, PlaybackFocus.Unmanaged)
        player.start(rate = 1f, pitch = 1f, voiceName = "", wordHighlight = false)

        output.listener!!.onStart("0:0")
        output.listener!!.onRangeStart("0:0", 6, 11)
        output.listener!!.onStart("0:1")
        output.listener!!.onRangeStart("0:1", 0, 6)

        assertEquals(listOf(MarkedSpeech("0:0", 0, 12), MarkedSpeech("0:1", 0, 11)), engine.marked)
    }

    private class FakeFocus(private val granted: Boolean = true) : PlaybackFocus {
        private var onEvent: ((PlaybackFocusEvent) -> Unit)? = null
        var abandoned = 0

        override fun request(onEvent: (PlaybackFocusEvent) -> Unit): Boolean {
            this.onEvent = onEvent
            return granted
        }

        override fun abandon() {
            abandoned++
        }

        fun send(event: PlaybackFocusEvent) = onEvent!!.invoke(event)
    }

    private class FakeOutput(private val available: Boolean = true) : SpeechOutput {
        override var listener: SpeechOutput.Listener? = null
        override val voices: StateFlow<List<SpeechVoiceOption>> = MutableStateFlow(emptyList())
        override val engines: StateFlow<List<SpeechEngineOption>> = MutableStateFlow(emptyList())
        var engineName: String? = null

        /** What the engine has queued since the last flush. */
        val queued = mutableListOf<String>()
        val rates = mutableListOf<Float>()
        val pitches = mutableListOf<Float>()
        val voiceNames = mutableListOf<String?>()

        override fun prepare(onReady: (Boolean) -> Unit) = onReady(available)

        override fun speak(utteranceId: String, text: String, flush: Boolean) {
            if (flush) queued.clear()
            queued += utteranceId
        }

        override fun setRate(rate: Float) {
            rates += rate
        }

        override fun setPitch(pitch: Float) {
            pitches += pitch
        }

        override fun setVoice(name: String?) {
            voiceNames += name
        }

        override fun setEngine(name: String?): Boolean {
            val next = name?.takeIf { it.isNotBlank() }
            if (next == engineName) return false
            engineName = next
            return true
        }

        override fun stop() = Unit

        override fun shutdown() = Unit
    }

    private class FakeEngine(private val chapters: List<List<String>>) : BookEngine {
        private var chapter = 0
        val marked = mutableListOf<MarkedSpeech>()
        var startedFrom: String? = null
        var stopped = false

        override suspend fun startSpeech(fromCfi: String?): SpeechChunk {
            startedFrom = fromCfi
            return chunk(0)
        }

        override suspend fun nextSpeechChunk(): SpeechChunk = chunk(++chapter)

        override suspend fun markSpeech(id: String, start: Int, end: Int) {
            marked += MarkedSpeech(id, start, end)
        }

        override suspend fun stopSpeech() {
            stopped = true
        }

        private fun chunk(index: Int): SpeechChunk =
            chapters.getOrNull(index)
                ?.let { sentences -> SpeechChunk(sentences.mapIndexed { n, text -> SpeechSentence("$index:$n", text) }, endOfBook = false) }
                ?: SpeechChunk(emptyList(), endOfBook = true)

        override suspend fun open(source: BookSource, resumeLocator: Locator?): Result<OpenBook> =
            Result.failure(UnsupportedOperationException())

        override val location: StateFlow<Locator?> = MutableStateFlow(null)

        override suspend fun goTo(target: NavTarget) = Unit

        override suspend fun applyStyle(style: BookStyle, theme: ReadTheme) = Unit

        override suspend fun renderAnnotations(annotations: List<ReaderAnnotation>) = Unit

        override suspend fun clearSelection() = Unit

        override suspend fun search(query: String) = Unit

        override suspend fun clearSearch() = Unit

        override suspend fun chapterWordCounts(minLength: Int): Map<String, Int> = emptyMap()

        override suspend fun mergeRanges(cfi: String, others: List<String>): MergedRange? = null

        override fun events(): Flow<EngineEvent> = emptyFlow()

        override fun close() = Unit
    }

    private data class MarkedSpeech(val id: String, val start: Int, val end: Int)
}
