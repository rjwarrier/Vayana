package com.vayana.feature.reader

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeechNumberHintsTest {
    @Test
    fun hintsMurmursAsTextWithoutChangingAcronymsUnitsOrOtherLanguages() {
        val text = "hmm, mmm… shh! HMMM, MMM, 2 mm, summer."
        val hints = speechNumberHints(text, Locale.US)
        assertEquals(listOf("hmm", "mmm", "shh", "2 mm"), hints.map { text.substring(it.start, it.end) })
        assertEquals(listOf(SpeechNumberKind.TEXT, SpeechNumberKind.TEXT, SpeechNumberKind.TEXT, SpeechNumberKind.MEASURE), hints.map { it.kind })
        assertEquals("mmm", hints[1].arguments["text"])
        assertTrue(speechNumberHints(text, Locale.FRANCE).isEmpty())
    }

    @Test
    fun recognisesMoneyMeasurementsPercentagesDatesAndClockTimes() {
        val text = "Pay ₹1,250.50 for 2.5 kg at 9:30 on 2026-10-01 with 15% off."
        val hints = speechNumberHints(text, Locale.US)
        assertEquals(listOf(SpeechNumberKind.MONEY, SpeechNumberKind.MEASURE, SpeechNumberKind.TIME, SpeechNumberKind.DATE, SpeechNumberKind.MEASURE), hints.map { it.kind })
        assertEquals(listOf("₹1,250.50", "2.5 kg", "9:30", "2026-10-01", "15%"), hints.map { text.substring(it.start, it.end) })
        assertEquals("1250", hints[0].arguments["integer"])
        assertEquals("50", hints[0].arguments["fraction"])
        assertEquals("INR", hints[0].arguments["currency"])
    }

    @Test
    fun leavesAmbiguousDatesRatiosInvalidDatesAndOtherLanguagesAlone() {
        assertTrue(speechNumberHints("01/02/2026, ratio 1:20, 2026-02-30", Locale.US).isEmpty())
        assertTrue(speechNumberHints("2,5 kg, €12,50", Locale.FRANCE).isEmpty())
        assertTrue(speechNumberHints("version 1.2.3 or 10mgx", Locale.US).isEmpty())
    }

    @Test
    fun dollarCurrencyUsesTheVoiceRegion() {
        assertEquals("AUD", speechNumberHints("\$12", Locale("en", "AU")).single().arguments["currency"])
    }
}
