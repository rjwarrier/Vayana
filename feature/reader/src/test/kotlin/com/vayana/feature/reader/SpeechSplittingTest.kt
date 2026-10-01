package com.vayana.feature.reader

import com.vayana.reader.api.SpeechSentence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeechSplittingTest {
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
