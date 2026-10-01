package com.vayana.reader.web

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DarkThemeTest {
    @Test
    fun `dark page colours are recognised and light ones are not`() {
        assertTrue(0xFF000000.toInt().isDarkColor())
        assertTrue(0xFF121212.toInt().isDarkColor())
        assertTrue(0xFF1E2A38.toInt().isDarkColor())
        assertFalse(0xFFFFFFFF.toInt().isDarkColor())
        assertFalse(0xFFF4ECD8.toInt().isDarkColor())
        assertFalse(0xFFB0B0B0.toInt().isDarkColor())
    }
}
