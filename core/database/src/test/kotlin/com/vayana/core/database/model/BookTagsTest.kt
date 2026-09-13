package com.vayana.core.database.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BookTagsTest {
    @Test
    fun tagCollapsesControlCharactersAndWhitespaceAndIsCapped() {
        assertEquals("space opera", "  space\t\u0007 opera ".normalizedBookTag())
        assertEquals(MaxBookTagChars, "x".repeat(100).normalizedBookTag().length)
    }

    @Test
    fun csvDropsBlankAndRepeatedTags() {
        assertEquals("Sci-Fi, classic", " Sci-Fi ,, sci-fi, classic, ".normalizedBookTagsCsv())
        assertNull(" , ,".normalizedBookTagsCsv())
        assertNull(null.normalizedBookTagsCsv())
    }
}
