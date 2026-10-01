package com.vayana.feature.reader

import com.vayana.reader.api.SpeechSentence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeechSplittingTest {
    @Test
    fun groupsOnlyShortSentencesWithinTheSameParagraphAndKeepsPunctuation() {
        val sentences = listOf(
            SpeechSentence("a", "\"Really?\""), SpeechSentence("b", "Wait…"),
            SpeechSentence("c", "Yes!"), SpeechSentence("d", "New paragraph.", 250),
        ).flatMap { it.splitForSpeech() }
        assertEquals("\"Really?\" Wait… Yes!", speechBatch(sentences, 0, 3, true).text)
        assertEquals(listOf(0, 1, 2), speechBatch(sentences, 0, 3, true).parts.map { it.index })
        assertEquals("Yes!", speechBatch(sentences, 2, 3, true).text)
        assertEquals("\"Really?\"", speechBatch(sentences, 0, 3, false).text)
    }

    @Test
    fun groupsAreBoundedBySentenceCountAndLength() {
        val short = (0..5).flatMap { SpeechSentence("$it", "Short.").splitForSpeech() }
        assertEquals(3, speechBatch(short, 0, 5, true).parts.size)
        val long = (0..2).flatMap { SpeechSentence("$it", "word ".repeat(80)).splitForSpeech() }
        assertEquals(1, speechBatch(long, 0, 2, true).parts.size)
    }

    @Test
    fun addedPausesAccountForPunctuationAndSpeedWithoutAlteringText() {
        assertEquals(100L, additionalSpeechPause(250, "Done.", 1f))
        assertEquals(75L, additionalSpeechPause(250, "\"Really?\"", 1f))
        assertEquals(0L, additionalSpeechPause(250, "Wait…", 1f))
        assertEquals(250L, additionalSpeechPause(250, "Heading", 1f))
        assertEquals(325L, additionalSpeechPause(900, "Wait...", 2f))
        assertEquals(500L, additionalSpeechPause(250, "Heading", 0.5f))
        assertEquals(0L, additionalSpeechPause(-1, "Heading", 1f))
    }

    @Test
    fun prefersClauseBoundaryAndPreservesEverySourceOffset() {
        val text = "word ".repeat(350) + "; " + "word ".repeat(400)
        val parts = SpeechSentence("s", text, pauseBeforeMs = 250).splitForSpeech()
        assertTrue(parts.first().text.endsWith("; "))
        assertEquals(text, parts.joinToString("") { it.text })
        parts.forEach { assertEquals(it.text, text.substring(it.sourceOffset, it.sourceOffset + it.text.length)) }
        assertEquals(listOf(250L, 0L), parts.map { it.pauseBeforeMs })
    }

    @Test
    fun splitsBetweenWordsAndNeverInsideAnEmoji() {
        val words = SpeechSentence("s", "abc ".repeat(1000)).splitForSpeech()
        assertTrue(words.dropLast(1).all { it.text.endsWith(' ') })
        val text = "a".repeat(2999) + "😀" + "b".repeat(50)
        val parts = SpeechSentence("s", text).splitForSpeech()
        assertEquals(2999, parts.first().text.length)
        assertTrue(parts[1].text.startsWith("😀"))
        assertEquals(text, parts.joinToString("") { it.text })
    }
}
