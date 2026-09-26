package com.vayana.feature.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PdfPageInputTest {
    private val labels = listOf("i", "ii", "1", "2", "A-1")

    @Test
    fun `printed labels resolve before physical page numbers`() {
        assertEquals(0, resolvePdfPageInput("I", labels))
        assertEquals(4, resolvePdfPageInput(" a-1 ", labels))
        assertEquals(2, resolvePdfPageInput("1", labels))
    }

    @Test
    fun `physical page number is a fallback when it is not a printed label`() {
        assertEquals(3, resolvePdfPageInput("4", labels))
        assertNull(resolvePdfPageInput("6", labels))
        assertNull(resolvePdfPageInput("", labels))
    }
}
