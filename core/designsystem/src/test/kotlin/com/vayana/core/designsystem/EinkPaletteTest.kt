package com.vayana.core.designsystem

import com.vayana.core.designsystem.theme.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EinkPaletteTest {
    @Test
    fun `monochrome remains the existing eink default`() {
        assertSame(ColorSchemes.eInk, ColorSchemes.forProfile(DisplayProfile.E_INK, true, DarkVariant.TRUE_BLACK))
        assertTrue(DisplayProfile.E_INK.isMonochrome(EinkPalette.MONOCHROME))
    }

    @Test
    fun `color eink uses Material 3 light and dark palettes instead of OLED surface variants`() {
        for (variant in DarkVariant.entries) {
            assertSame(ColorSchemes.eInkColorLight, ColorSchemes.forProfile(DisplayProfile.E_INK, false, variant, EinkPalette.COLOR))
            assertSame(ColorSchemes.eInkColorDark, ColorSchemes.forProfile(DisplayProfile.E_INK, true, variant, EinkPalette.COLOR))
        }
        assertFalse(DisplayProfile.E_INK.isMonochrome(EinkPalette.COLOR))
    }

    @Test
    fun `color eink Material 3 text retains contrast across tonal surface tiers`() {
        for (scheme in listOf(ColorSchemes.eInkColorLight, ColorSchemes.eInkColorDark)) {
            val surfaces = listOf(scheme.background, scheme.surface, scheme.surfaceDim, scheme.surfaceBright,
                scheme.surfaceContainerLowest, scheme.surfaceContainerLow, scheme.surfaceContainer,
                scheme.surfaceContainerHigh, scheme.surfaceContainerHighest)
            for (surface in surfaces) {
                assertEquals(1f, surface.alpha)
                for (text in listOf(scheme.onSurface, scheme.onSurfaceVariant)) {
                    assertTrue(contrast(text, surface) >= 4.5f, "$text on $surface: ${contrast(text, surface)}")
                }
            }
            val pairs = listOf(scheme.onPrimary to scheme.primary, scheme.onSecondary to scheme.secondary,
                scheme.onTertiary to scheme.tertiary, scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onSecondaryContainer to scheme.secondaryContainer, scheme.onTertiaryContainer to scheme.tertiaryContainer,
                scheme.onError to scheme.error, scheme.onErrorContainer to scheme.errorContainer)
            pairs.forEach { (text, fill) -> assertTrue(contrast(text, fill) >= 4.5f) }
            assertEquals(0f, scheme.surfaceTint.alpha)
        }
    }

    @Test
    fun `color eink inherits Material 3 accents containers and tonal surfaces`() {
        for ((actual, material) in listOf(ColorSchemes.eInkColorLight to ColorSchemes.light,
            ColorSchemes.eInkColorDark to ColorSchemes.dark)) {
            assertEquals(material.primary, actual.primary)
            assertEquals(material.secondary, actual.secondary)
            assertEquals(material.tertiary, actual.tertiary)
            assertEquals(material.primaryContainer, actual.primaryContainer)
            assertEquals(material.secondaryContainer, actual.secondaryContainer)
            assertEquals(material.tertiaryContainer, actual.tertiaryContainer)
            assertEquals(material.background, actual.background)
            assertEquals(material.onSurface, actual.onSurface)
            assertEquals(material.surfaceContainerLow, actual.surfaceContainerLow)
            assertEquals(material.surfaceContainerHigh, actual.surfaceContainerHigh)
            assertEquals(material.outline, actual.outline)
            assertEquals(material.error, actual.error)
        }
    }

    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)

    @Test
    fun `standard display ignores the saved eink palette`() {
        for (palette in EinkPalette.entries) {
            assertSame(ColorSchemes.light, ColorSchemes.forProfile(DisplayProfile.STANDARD, false, DarkVariant.STANDARD, palette))
            assertFalse(DisplayProfile.STANDARD.isMonochrome(palette))
        }
    }
}
