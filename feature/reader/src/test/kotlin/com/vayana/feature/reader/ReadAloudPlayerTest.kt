package com.vayana.feature.reader

import com.vayana.reader.api.BookEngine
import com.vayana.reader.api.BookSource
import com.vayana.reader.api.BookStyle
import com.vayana.reader.api.EngineEvent
import com.vayana.reader.api.Locator
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
        val player = ReadAloudPlayer(output, scope, { engine }, {})

        player.start(rate = 1f)
        assertEquals(listOf("0:0", "0:1"), output.queued)
        assertTrue(player.state.value.playing)

        output.listener!!.onStart("0:1")
        assertEquals(listOf("0:1"), engine.marked)

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
        val player = ReadAloudPlayer(output, scope, { engine }, {})

        player.start(rate = 1f)
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
        val player = ReadAloudPlayer(output, scope, { engine }, {})

        player.start(rate = 1f)

        assertTrue(player.state.value.voiceMissing)
        assertFalse(player.state.value.active)
        assertTrue(output.queued.isEmpty())
    }

    @Test
    fun skipsChaptersWithNothingToRead() {
        val output = FakeOutput()
        // One engine for the whole test, as in the reader: the player asks it for each next chapter in turn.
        val engine = FakeEngine(listOf(listOf("A."), emptyList(), listOf("B.")))
        val player = ReadAloudPlayer(output, scope, { engine }, {})

        player.start(rate = 1f)
        output.listener!!.onDone("0:0")

        assertEquals(listOf("2:0"), output.queued)
    }

    private class FakeOutput(private val available: Boolean = true) : SpeechOutput {
        override var listener: SpeechOutput.Listener? = null

        /** What the engine has queued since the last flush. */
        val queued = mutableListOf<String>()

        override fun prepare(onReady: (Boolean) -> Unit) = onReady(available)

        override fun speak(utteranceId: String, text: String, flush: Boolean) {
            if (flush) queued.clear()
            queued += utteranceId
        }

        override fun setRate(rate: Float) = Unit

        override fun stop() = Unit

        override fun shutdown() = Unit
    }

    private class FakeEngine(private val chapters: List<List<String>>) : BookEngine {
        private var chapter = 0
        val marked = mutableListOf<String>()
        var stopped = false

        override suspend fun startSpeech(): SpeechChunk = chunk(0)

        override suspend fun nextSpeechChunk(): SpeechChunk = chunk(++chapter)

        override suspend fun markSpeech(id: String) {
            marked += id
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

        override suspend fun chapterWordCounts(): Map<String, Int> = emptyMap()

        override fun events(): Flow<EngineEvent> = emptyFlow()

        override fun close() = Unit
    }
}
