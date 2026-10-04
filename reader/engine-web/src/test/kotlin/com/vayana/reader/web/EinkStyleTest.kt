package com.vayana.reader.web

import com.vayana.reader.api.ReadTheme
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EinkStyleTest {
    private val whitePage = ReadTheme(-1, 0xFF000000.toInt())

    @Test
    fun `black text on white alone does not enable eink behavior`() {
        assertEquals("", einkStyleCss(whitePage))
    }

    @Test
    fun `both eink palettes suppress motion but only monochrome desaturates images`() {
        val color = einkStyleCss(whitePage.copy(eink = true))
        val monochrome = einkStyleCss(whitePage.copy(eink = true, monochrome = true))
        for (css in listOf(color, monochrome)) {
            assertTrue(css.contains("animation:none"))
            assertTrue(css.contains("transition:none"))
            assertTrue(css.contains("text-decoration:underline"))
        }
        assertFalse(color.contains("grayscale"))
        assertFalse(color.contains("color:#000000"))
        assertTrue(monochrome.contains("grayscale(1)"))
        assertTrue(monochrome.contains("color:#000000"))
    }
}
