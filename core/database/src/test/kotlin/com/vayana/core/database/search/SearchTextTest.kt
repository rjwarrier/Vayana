package com.vayana.core.database.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchTextTest {
    @Test
    fun tokensDropPunctuationAndFtsSyntax() {
        assertEquals(listOf("lord", "of", "the", "rings"), searchTokens("  \"Lord\" -of* the:Rings "))
    }

    @Test
    fun tokensKeepNonLatinLettersAndDigits() {
        assertEquals(listOf("രാമായണം", "2"), searchTokens("രാമായണം #2"))
    }

    @Test
    fun matchIsLowerCasePrefixPerToken() {
        assertEquals("tolk* or*", ftsPrefixMatch(searchTokens("Tolk OR")))
    }

    @Test
    fun blankQueryHasNoMatch() {
        assertNull(ftsPrefixMatch(searchTokens(" ,.- ")))
    }

    @Test
    fun fieldMatchesOnWordPrefixOnly() {
        assertTrue("J.R.R. Tolkien".hasWordStartingWith("tolk"))
        assertFalse("J.R.R. Tolkien".hasWordStartingWith("olkien"))
        assertFalse(null.hasWordStartingWith("tolk"))
    }
}
