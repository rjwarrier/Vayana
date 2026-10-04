package com.vayana.feature.reader

import com.vayana.core.datastore.settings.ReaderTheme
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.EinkPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EinkPaletteReaderTest {
    @Test
    fun `color eink preserves reader palette while keeping page turns static`() {
        val standard = SettingsSnapshot(readerTheme = ReaderTheme.MINT, readerPageTurnAnimation = true)
        val color = standard.copy(displayProfile = DisplayProfile.E_INK, einkPalette = EinkPalette.COLOR)
        assertEquals(standard.readTheme.backgroundColorArgb, color.readTheme.backgroundColorArgb)
        assertEquals(standard.readTheme.textColorArgb, color.readTheme.textColorArgb)
        assertTrue(color.readTheme.eink)
        assertFalse(color.readTheme.monochrome)
        assertFalse(color.toBookStyle().pageTurnAnimation)
        assertTrue(standard.toBookStyle().pageTurnAnimation)
    }

    @Test
    fun `monochrome overrides reader colors and retains static rendering`() {
        val settings = SettingsSnapshot(displayProfile = DisplayProfile.E_INK, readerTheme = ReaderTheme.DARK, readerPageTurnAnimation = true)
        assertEquals(-1, settings.readTheme.backgroundColorArgb)
        assertEquals(0xFF000000.toInt(), settings.readTheme.textColorArgb)
        assertTrue(settings.readTheme.eink)
        assertTrue(settings.readTheme.monochrome)
        assertFalse(settings.toBookStyle().pageTurnAnimation)
    }
}
