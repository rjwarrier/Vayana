package com.vayana.reader.web

import com.vayana.reader.api.BookTextAlign
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TextAlignCssTest {
    @Test
    fun `all four alignments override prose and preserve special paragraph layouts`() {
        val values = mapOf(BookTextAlign.LEFT to "left", BookTextAlign.RIGHT to "right",
            BookTextAlign.CENTER to "center", BookTextAlign.JUSTIFIED to "justify")
        for ((align, value) in values) {
            val css = textAlignCss(align)!!
            assertTrue(css.contains("text-align:$value !important;"))
            assertTrue(css.contains("not(.poem)"))
            assertTrue(css.contains("not([align])"))
        }
        assertNull(textAlignCss(BookTextAlign.BOOK))
    }
}
