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

    @Test
    fun literalNullTagIsDropped() {
        assertEquals("", " NULL ".normalizedBookTag())
        assertEquals("Mystery, Science Fiction", "null, Mystery,Science Fiction".normalizedBookTagsCsv())
        assertNull("null".normalizedBookTagsCsv())
        assertEquals("Nullification", "Nullification".normalizedBookTagsCsv())
    }

    @Test
    fun detectsOnlyAWholeNullTag() {
        assertEquals(true, hasNullBookTag("Mystery, null"))
        assertEquals(false, hasNullBookTag("Nullification, Mystery"))
        assertEquals(false, hasNullBookTag(null))
    }
}
