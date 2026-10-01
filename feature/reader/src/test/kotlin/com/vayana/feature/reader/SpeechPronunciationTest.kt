package com.vayana.feature.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class SpeechPronunciationTest {
    @Test
    fun elongatedInterjectionsUsePhoneticSoundsAndPreserveWordOffsets() {
        val source = "Hmmm, mmmm... uhhh, ahhh! Ohhh, shhhh."
        val prepared = prepareSpeechPronunciation(source, emptyList())
        assertEquals("hum, mum... uh, ah! oh, shush.", prepared.text)
        assertEquals(0 to 4, prepared.sourceRange(0, 3))
        val start = prepared.text.indexOf("shush")
        assertEquals(source.indexOf("shhhh") to source.indexOf("shhhh") + 5, prepared.sourceRange(start, start + 5))
    }

    @Test
    fun unitsAcronymsAndFragmentsInsideOtherWordsAreUnchanged() {
        val source = "5 mm, HMMM, MMM, Muhammad and summmer."
        assertEquals(source, prepareSpeechPronunciation(source, emptyList()).text)
    }

    @Test
    fun bookCorrectionTakesPriorityOverInterjectionFallback() {
        val prepared = prepareSpeechPronunciation("hmmm and mmm", listOf(SpeechPronunciation("mmm", "my sound")))
        assertEquals("hum and my sound", prepared.text)
    }
    @Test
    fun replacementsMatchWholeNamesWithoutChangingLaterWordOffsets() {
        val source = "Hermione met Hermiones and HERMIONE today."
        val prepared = prepareSpeechPronunciation(source, listOf(SpeechPronunciation("Hermione", "her MY oh nee")))
        assertEquals("her MY oh nee met Hermiones and her MY oh nee today.", prepared.text)
        assertEquals(0 to 8, prepared.sourceRange(0, 12))
        val start = prepared.text.indexOf("today")
        assertEquals(source.indexOf("today") to source.indexOf("today") + 5, prepared.sourceRange(start, start + 5))
    }

    @Test
    fun longestPhraseWinsAndReplacementIsNotAppliedRecursively() {
        val prepared = prepareSpeechPronunciation("New York and York", listOf(
            SpeechPronunciation("York", "yorkshire"), SpeechPronunciation("New York", "York"),
        ))
        assertEquals("York and yorkshire", prepared.text)
    }

    @Test
    fun splitReplacementsKeepTheirOriginalRange() {
        val text = "abc ".repeat(730) + "Hermione today."
        val parts = com.vayana.reader.api.SpeechSentence("s", text).splitForSpeech(
            listOf(SpeechPronunciation("Hermione", "her ".repeat(40).trim())),
        )
        assertEquals(2, parts.size)
        val last = parts.last()
        val start = last.text.indexOf("today")
        assertEquals(text.indexOf("today") to text.indexOf("today") + 5, last.sourceRange(start, start + 5))
    }
}
