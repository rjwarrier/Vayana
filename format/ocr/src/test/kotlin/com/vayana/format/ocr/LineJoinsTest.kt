package com.vayana.format.ocr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LineJoinsTest {
    private val book = mapOf("ഒരു" to 600, "അവൻ" to 280, "സമയത്ത്" to 90, "ആനന്ദി" to 260, "റാമുംകൂടി" to 6)

    @Test
    fun `a next line starting with a mark, a doubled consonant or a ya ending continues the word`() {
        assertTrue(continuesOnNextLine("കൊടു", "ക്കുന്ന", book))
        assertTrue(continuesOnNextLine("ചിണു", "ങ്ങുന്ന", book))
        assertTrue(continuesOnNextLine("ഉണർന്നിട്ടു", "ണ്ടായിരുന്നില്ല.", book))
        assertTrue(continuesOnNextLine("ആനന്ദി", "യുടെ", book))
    }

    @Test
    fun `halves that make a word found whole elsewhere in the book are joined`() {
        assertTrue(continuesOnNextLine("സമ", "യത്ത്", book))
    }

    @Test
    fun `halves never seen as words are joined unless the first ends as words do`() {
        assertTrue(continuesOnNextLine("മാറ്റിപിടിക്കുവാനാ", "യിട്ട്", book))
        assertFalse(continuesOnNextLine("ആഴത്തിൽ", "സംസാരിച്ചാൽ", book))
        assertFalse(continuesOnNextLine("ലെമൺ", "റൈസ്,", book))
    }

    @Test
    fun `whole words, vowel letters, punctuation and digits stay apart`() {
        assertFalse(continuesOnNextLine("ഒരു", "സിനിമ", book))
        assertFalse(continuesOnNextLine("കുറവുകളും", "ഉണ്ടാകുവാൻ", book))
        assertFalse(continuesOnNextLine("മാറിയിരുന്നു.", "ഹോൺ", book))
        assertFalse(continuesOnNextLine("2018", "ജനുവരി", book))
        assertFalse(continuesOnNextLine("അവൻ", "“ശരി", book))
    }

    @Test
    fun `joins are found per page from the whole book's words`() {
        fun line(vararg words: String) = words.map { RecognizedWord(it, 0f, 0f, 1f, 1f, 90f) }
        val pages = mapOf(
            0 to RecognizedPage(listOf(line("ഒരു", "സമയത്ത്", "അവൻ", "സമ"), line("യത്ത്", "ഒരു", "പോയി"), line("ഒരു"))),
        )
        assertEquals(mapOf(0 to setOf(0)), lineJoins(pages))
    }
}
