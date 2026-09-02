package com.vayana.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.vayana.core.designsystem.tokens.Palette

/**
 * [ColorScheme] builders from [Palette]. Only these functions (plus [Palette] itself) may
 * reference a literal hex/[Color] value — everything else in the app reads roles off the
 * scheme via `MaterialTheme.colorScheme`. The handoff names three surfaces per mode
 * (page background / card surface / elevated surface); M3 wants five container tiers, so the
 * two extra steps are interpolated rather than specified pixel-for-pixel in the handoff — safe
 * to tighten later once more surfaces (sheets, dialogs) are actually built.
 *
 * The handoff does not specify an error color (no error states were in scope for the design
 * pass); standard M3 baseline error tones are used until product/design supplies one.
 */
object ColorSchemes {

    val light: ColorScheme = lightColorScheme(
        primary = Palette.Teal700,
        onPrimary = Palette.White,
        primaryContainer = Palette.Teal100,
        onPrimaryContainer = Palette.Teal700,
        secondary = Palette.Forest500,
        onSecondary = Palette.White,
        secondaryContainer = Palette.Forest100,
        onSecondaryContainer = Palette.Forest900,
        tertiary = Palette.Gold700,
        onTertiary = Palette.White,
        tertiaryContainer = Palette.Gold300,
        onTertiaryContainer = Palette.Gold700,
        background = Palette.Cream100,
        onBackground = Palette.FgPrimaryLight,
        surface = Palette.Cream100,
        onSurface = Palette.FgPrimaryLight,
        surfaceVariant = Palette.Cream200,
        onSurfaceVariant = Palette.TextMutedLight,
        surfaceContainerLowest = Palette.Cream200,
        surfaceContainerLow = Palette.Cream100,
        surfaceContainer = Palette.White,
        surfaceContainerHigh = Palette.Cream50,
        surfaceContainerHighest = Palette.Cream50,
        outline = Palette.BorderLight,
        outlineVariant = Palette.BorderLight,
        error = Color(0xFFB3261E),
        onError = Palette.White,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
    )

    val dark: ColorScheme = darkColorScheme(
        primary = Palette.Teal300,
        onPrimary = Palette.Navy900,
        primaryContainer = Palette.Teal700,
        onPrimaryContainer = Palette.Teal100,
        secondary = Palette.Forest300,
        onSecondary = Palette.Navy900,
        secondaryContainer = Palette.Forest700,
        onSecondaryContainer = Palette.Forest100,
        tertiary = Palette.Gold300,
        onTertiary = Palette.Navy900,
        tertiaryContainer = Palette.Gold700,
        onTertiaryContainer = Palette.Gold300,
        background = Palette.Navy800,
        onBackground = Palette.WarmWhite,
        surface = Palette.Navy800,
        onSurface = Palette.WarmWhite,
        surfaceVariant = Palette.Navy700,
        onSurfaceVariant = Palette.TextMutedDark,
        surfaceContainerLowest = Palette.Navy900,
        surfaceContainerLow = Palette.Navy800,
        surfaceContainer = Palette.Navy700,
        surfaceContainerHigh = Palette.Navy600,
        surfaceContainerHighest = Palette.Navy500,
        outline = Palette.BorderDark,
        outlineVariant = Palette.BorderDark,
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
    )

    /** Raised black point (PROMPT1uidesign.md §1: "softer dark ... raised black point, less pure-black"). */
    val softerDark: ColorScheme = dark.copy(
        background = Palette.Navy900,
        surface = Palette.Navy900,
        surfaceContainerLowest = Palette.Navy950,
        surfaceContainerLow = Palette.Navy900,
        surfaceContainer = Palette.Navy800,
        surfaceContainerHigh = Palette.Navy700,
        surfaceContainerHighest = Palette.Navy600,
    )

    /** OLED true-black variant. */
    val trueBlack: ColorScheme = dark.copy(
        background = Palette.Amoled,
        surface = Palette.Amoled,
        surfaceContainerLowest = Palette.Amoled,
        surfaceContainerLow = Palette.Amoled,
        surfaceContainer = Palette.NearBlack,
        surfaceContainerHigh = Palette.Navy800,
        surfaceContainerHighest = Palette.Navy700,
    )

    /**
     * PROMPT1uidesign.md §2: pure #FFFFFF background, #000000 text, no greys below ~30% for
     * text — greys survive only as hairline dividers ([hairline30]). No accent color: every
     * role collapses to black-on-white so nothing depends on a color an E-Ink panel can't show.
     */
    private val hairline30 = Color(0x4D000000)

    val eInk: ColorScheme = lightColorScheme(
        primary = Palette.EinkForeground,
        onPrimary = Palette.EinkBackground,
        primaryContainer = Palette.EinkForeground,
        onPrimaryContainer = Palette.EinkBackground,
        secondary = Palette.EinkForeground,
        onSecondary = Palette.EinkBackground,
        secondaryContainer = Palette.EinkBackground,
        onSecondaryContainer = Palette.EinkForeground,
        tertiary = Palette.EinkForeground,
        onTertiary = Palette.EinkBackground,
        tertiaryContainer = Palette.EinkForeground,
        onTertiaryContainer = Palette.EinkBackground,
        background = Palette.EinkBackground,
        onBackground = Palette.EinkForeground,
        surface = Palette.EinkBackground,
        onSurface = Palette.EinkForeground,
        surfaceVariant = Palette.EinkBackground,
        onSurfaceVariant = Palette.EinkForeground,
        surfaceContainerLowest = Palette.EinkBackground,
        surfaceContainerLow = Palette.EinkBackground,
        surfaceContainer = Palette.EinkBackground,
        surfaceContainerHigh = Palette.EinkBackground,
        surfaceContainerHighest = Palette.EinkBackground,
        outline = hairline30,
        outlineVariant = hairline30,
        error = Palette.EinkForeground,
        onError = Palette.EinkBackground,
        errorContainer = Palette.EinkBackground,
        onErrorContainer = Palette.EinkForeground,
    )

    fun forProfile(profile: DisplayProfile, isDark: Boolean, darkVariant: DarkVariant): ColorScheme = when {
        profile == DisplayProfile.E_INK -> eInk
        !isDark -> light
        darkVariant == DarkVariant.TRUE_BLACK -> trueBlack
        darkVariant == DarkVariant.SOFTER -> softerDark
        else -> dark
    }
}
