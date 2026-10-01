package com.vayana.feature.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class SpeechPronunciationTest {
    @Test
    fun romanLabelsBecomeNumbersAndHighlightTheOriginalNumeral() {
        val source = "CHAPTER I. Part IV, Volume XII, Section ix, Scene XL and Book MCMXCIX."
        val prepared = prepareSpeechPronunciation(source, emptyList())
        assertEquals("CHAPTER 1. Part 4, Volume 12, Section 9, Scene 40 and Book 1999.", prepared.text)
        val start = prepared.text.indexOf("1999")
        val original = source.indexOf("MCMXCIX")
        assertEquals(original to original + 7, prepared.sourceRange(start, start + 4))
    }

    @Test
    fun standaloneAndUnicodeNumeralsWorkWithoutChangingProseOrInvalidNumbers() {
        assertEquals("14.", prepareSpeechPronunciation("XIV.", emptyList()).text)
        assertEquals("12", prepareSpeechPronunciation("Ⅻ", emptyList()).text)
        assertEquals("Chapter 3000", prepareSpeechPronunciation("Chapter mMm", emptyList()).text)
        val prose = "I mix CIVIC words with IV treatment, M. Smith, Chapter IIII and Part IC."
        assertEquals(prose, prepareSpeechPronunciation(prose, emptyList()).text)
    }

    @Test
    fun bookCorrectionsOverrideRomanNumbersEvenWhenUnchanged() {
        assertEquals("Chapter eye", prepareSpeechPronunciation("Chapter I", listOf(SpeechPronunciation("I", "eye"))).text)
        assertEquals("Chapter IV", prepareSpeechPronunciation("Chapter IV", listOf(SpeechPronunciation("IV", "IV"))).text)
    }

    @Test
    fun mixedExpansionAndContractionKeepEverySourceRangeIncludingEmoji() {
        val source = "😀 Hermione hmmm Hermione today."
        val prepared = prepareSpeechPronunciation(source, listOf(SpeechPronunciation("Hermione", "her MY oh nee")))
        assertEquals("😀 her MY oh nee hmm her MY oh nee today.", prepared.text)
        assertEquals(0 to 2, prepared.sourceRange(0, 2))
        val firstName = prepared.text.indexOf("her MY oh nee")
        val lastName = prepared.text.lastIndexOf("her MY oh nee")
        for (offset in 0 until 13) {
            assertEquals(3 to 11, prepared.sourceRange(firstName + offset, firstName + offset + 1))
            assertEquals(17 to 25, prepared.sourceRange(lastName + offset, lastName + offset + 1))
        }
        val start = prepared.text.indexOf("today")
        assertEquals(26 to 31, prepared.sourceRange(start, start + 5))
    }

    @Test
    fun elongatedInterjectionsUsePhoneticSoundsAndPreserveWordOffsets() {
        val source = "Hmmm, mmmm... uhhh, ahhh! Ohhh, shhhh."
        val prepared = prepareSpeechPronunciation(source, emptyList())
        assertEquals("hmm, mmm... uh, ah! oh, shh.", prepared.text)
        assertEquals(0 to 4, prepared.sourceRange(0, 3))
        val start = prepared.text.indexOf("shh")
        assertEquals(source.indexOf("shhhh") to source.indexOf("shhhh") + 5, prepared.sourceRange(start, start + 3))
    }

    @Test
    fun unitsAcronymsAndFragmentsInsideOtherWordsAreUnchanged() {
        val source = "5 mm, HMMM, MMM, Muhammad and summmer."
        assertEquals(source, prepareSpeechPronunciation(source, emptyList()).text)
    }

    @Test
    fun bookCorrectionTakesPriorityOverInterjectionFallback() {
        val prepared = prepareSpeechPronunciation("hmmm and mmm", listOf(SpeechPronunciation("mmm", "my sound")))
        assertEquals("hmm and my sound", prepared.text)
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
