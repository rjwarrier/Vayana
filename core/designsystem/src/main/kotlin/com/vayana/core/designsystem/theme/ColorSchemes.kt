package com.vayana.core.designsystem.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.vayana.core.designsystem.tokens.Palette

/**
 * [ColorScheme] builders from [Palette]. Only these functions (plus [Palette] itself) may
 * reference a literal hex/[Color] value — everything else in the app reads roles off the
 * scheme via `MaterialTheme.colorScheme`.
 *
 * App chroming uses a Material 3 tonal role set; reader-specific page themes and generated
 * covers keep their own display colors because they model content rather than UI chrome.
 */
object ColorSchemes {

    val light: ColorScheme = lightColorScheme(
        primary = Palette.M3PrimaryLight,
        onPrimary = Palette.M3OnPrimaryLight,
        primaryContainer = Palette.M3PrimaryContainerLight,
        onPrimaryContainer = Palette.M3OnPrimaryContainerLight,
        secondary = Palette.M3SecondaryLight,
        onSecondary = Palette.M3OnSecondaryLight,
        secondaryContainer = Palette.M3SecondaryContainerLight,
        onSecondaryContainer = Palette.M3OnSecondaryContainerLight,
        tertiary = Palette.M3TertiaryLight,
        onTertiary = Palette.M3OnTertiaryLight,
        tertiaryContainer = Palette.M3TertiaryContainerLight,
        onTertiaryContainer = Palette.M3OnTertiaryContainerLight,
        background = Palette.M3BackgroundLight,
        onBackground = Palette.M3OnBackgroundLight,
        surface = Palette.M3SurfaceLight,
        onSurface = Palette.M3OnSurfaceLight,
        surfaceVariant = Palette.M3SurfaceVariantLight,
        onSurfaceVariant = Palette.M3OnSurfaceVariantLight,
        surfaceContainerLowest = Palette.M3SurfaceContainerLowestLight,
        surfaceContainerLow = Palette.M3SurfaceContainerLowLight,
        surfaceContainer = Palette.M3SurfaceContainerLight,
        surfaceContainerHigh = Palette.M3SurfaceContainerHighLight,
        surfaceContainerHighest = Palette.M3SurfaceContainerHighestLight,
        outline = Palette.M3OutlineLight,
        outlineVariant = Palette.M3OutlineVariantLight,
        error = Color(0xFFB3261E),
        onError = Palette.White,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
    )

    val dark: ColorScheme = darkColorScheme(
        primary = Palette.M3PrimaryDark,
        onPrimary = Palette.M3OnPrimaryDark,
        primaryContainer = Palette.M3PrimaryContainerDark,
        onPrimaryContainer = Palette.M3OnPrimaryContainerDark,
        secondary = Palette.M3SecondaryDark,
        onSecondary = Palette.M3OnSecondaryDark,
        secondaryContainer = Palette.M3SecondaryContainerDark,
        onSecondaryContainer = Palette.M3OnSecondaryContainerDark,
        tertiary = Palette.M3TertiaryDark,
        onTertiary = Palette.M3OnTertiaryDark,
        tertiaryContainer = Palette.M3TertiaryContainerDark,
        onTertiaryContainer = Palette.M3OnTertiaryContainerDark,
        background = Palette.M3BackgroundDark,
        onBackground = Palette.M3OnBackgroundDark,
        surface = Palette.M3SurfaceDark,
        onSurface = Palette.M3OnSurfaceDark,
        surfaceVariant = Palette.M3SurfaceVariantDark,
        onSurfaceVariant = Palette.M3OnSurfaceVariantDark,
        surfaceContainerLowest = Palette.M3SurfaceContainerLowestDark,
        surfaceContainerLow = Palette.M3SurfaceContainerLowDark,
        surfaceContainer = Palette.M3SurfaceContainerDark,
        surfaceContainerHigh = Palette.M3SurfaceContainerHighDark,
        surfaceContainerHighest = Palette.M3SurfaceContainerHighestDark,
        outline = Palette.M3OutlineDark,
        outlineVariant = Palette.M3OutlineVariantDark,
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
    )

    /** Raised black point (Design/DESIGN_SPEC.md §1: "softer dark ... raised black point, less pure-black"). */
    val softerDark: ColorScheme = dark.copy(
        background = Palette.M3SurfaceContainerLowestDark,
        surface = Palette.M3SurfaceContainerLowestDark,
        surfaceContainerLowest = Palette.M3SurfaceContainerLowestDark,
        surfaceContainerLow = Palette.M3BackgroundDark,
        surfaceContainer = Palette.M3SurfaceContainerLowDark,
        surfaceContainerHigh = Palette.M3SurfaceContainerDark,
        surfaceContainerHighest = Palette.M3SurfaceContainerHighDark,
    )

    /** OLED true-black variant. */
    val trueBlack: ColorScheme = dark.withTrueBlack()

    /** Monochrome, high-contrast chrome optimized for E-Ink refresh and legibility. */
    val eInk: ColorScheme = lightColorScheme(
        primary = Palette.EinkForeground,
        onPrimary = Palette.EinkBackground,
        primaryContainer = Palette.EinkContainerHigh,
        onPrimaryContainer = Palette.EinkForeground,
        secondary = Palette.EinkForeground,
        onSecondary = Palette.EinkBackground,
        secondaryContainer = Palette.EinkContainerHigh,
        onSecondaryContainer = Palette.EinkForeground,
        tertiary = Palette.EinkForeground,
        onTertiary = Palette.EinkBackground,
        tertiaryContainer = Palette.EinkContainer,
        onTertiaryContainer = Palette.EinkForeground,
        background = Palette.EinkBackground,
        onBackground = Palette.EinkForeground,
        surface = Palette.EinkBackground,
        onSurface = Palette.EinkForeground,
        surfaceVariant = Palette.EinkContainerHigh,
        onSurfaceVariant = Palette.EinkForeground,
        surfaceContainerLowest = Palette.EinkBackground,
        surfaceContainerLow = Palette.EinkContainerLow,
        surfaceContainer = Palette.EinkContainer,
        surfaceContainerHigh = Palette.EinkContainerHigh,
        surfaceContainerHighest = Palette.EinkContainerHighest,
        outline = Palette.EinkOutline,
        outlineVariant = Palette.EinkOutlineVariant,
        error = Palette.EinkForeground,
        onError = Palette.EinkBackground,
        errorContainer = Palette.EinkContainerHigh,
        onErrorContainer = Palette.EinkForeground,
    )

    /** Solid, neutral surface steps and high-contrast text; accents keep color without tinting every surface. */
    val eInkColorLight: ColorScheme = light.copy(
        primary = Palette.EinkColorPrimary, onPrimary = Palette.White,
        secondary = Palette.EinkColorSecondary, onSecondary = Palette.White,
        tertiary = Palette.EinkColorTertiary, onTertiary = Palette.White,
        primaryContainer = Palette.EinkColorPrimaryContainer, onPrimaryContainer = Palette.Amoled,
        secondaryContainer = Palette.EinkColorSecondaryContainer, onSecondaryContainer = Palette.Amoled,
        tertiaryContainer = Palette.EinkColorTertiaryContainer, onTertiaryContainer = Palette.Amoled,
        background = Palette.White, onBackground = Palette.Amoled,
        surface = Palette.White, onSurface = Palette.Amoled,
        surfaceBright = Palette.White, surfaceDim = Palette.EinkColorSurfaceHighest,
        surfaceVariant = Palette.EinkColorSurfaceHigh, onSurfaceVariant = Palette.Amoled,
        surfaceContainerLowest = Palette.White,
        surfaceContainerLow = Palette.EinkColorSurfaceLow,
        surfaceContainer = Palette.EinkColorSurface,
        surfaceContainerHigh = Palette.EinkColorSurfaceHigh,
        surfaceContainerHighest = Palette.EinkColorSurfaceHighest,
        outline = Palette.EinkColorOutline, outlineVariant = Palette.EinkColorOutlineVariant,
        surfaceTint = Color.Transparent,
        inverseSurface = Palette.Amoled, inverseOnSurface = Palette.White,
        inversePrimary = Palette.EinkColorPrimaryDark,
        error = Palette.EinkColorError, onError = Palette.White,
        errorContainer = Palette.EinkColorErrorContainer, onErrorContainer = Palette.Amoled,
        primaryFixed = Palette.EinkColorPrimaryContainer, primaryFixedDim = Palette.EinkColorPrimaryDark,
        onPrimaryFixed = Palette.Amoled, onPrimaryFixedVariant = Palette.Amoled,
        secondaryFixed = Palette.EinkColorSecondaryContainer, secondaryFixedDim = Palette.EinkColorSecondaryDark,
        onSecondaryFixed = Palette.Amoled, onSecondaryFixedVariant = Palette.Amoled,
        tertiaryFixed = Palette.EinkColorTertiaryContainer, tertiaryFixedDim = Palette.EinkColorTertiaryDark,
        onTertiaryFixed = Palette.Amoled, onTertiaryFixedVariant = Palette.Amoled,
    )

    /** Respect an explicit dark theme, with the same neutral surfaces and distinct accent families. */
    val eInkColorDark: ColorScheme = eInkColorLight.copy(
        primary = Palette.EinkColorPrimaryDark, onPrimary = Palette.Amoled,
        secondary = Palette.EinkColorSecondaryDark, onSecondary = Palette.Amoled,
        tertiary = Palette.EinkColorTertiaryDark, onTertiary = Palette.Amoled,
        primaryContainer = Palette.EinkColorPrimaryContainerDark, onPrimaryContainer = Palette.White,
        secondaryContainer = Palette.EinkColorSecondaryContainerDark, onSecondaryContainer = Palette.White,
        tertiaryContainer = Palette.EinkColorTertiaryContainerDark, onTertiaryContainer = Palette.White,
        background = Palette.Amoled, onBackground = Palette.White,
        surface = Palette.Amoled, onSurface = Palette.White,
        surfaceDim = Palette.Amoled, surfaceBright = Palette.EinkColorSurfaceHighestDark,
        surfaceVariant = Palette.EinkColorSurfaceHighDark, onSurfaceVariant = Palette.White,
        surfaceContainerLowest = Palette.Amoled,
        surfaceContainerLow = Palette.EinkColorSurfaceLowDark,
        surfaceContainer = Palette.EinkColorSurfaceDark,
        surfaceContainerHigh = Palette.EinkColorSurfaceHighDark,
        surfaceContainerHighest = Palette.EinkColorSurfaceHighestDark,
        outline = Palette.White, outlineVariant = Palette.EinkColorSurfaceHighest,
        inverseSurface = Palette.White, inverseOnSurface = Palette.Amoled,
        inversePrimary = Palette.EinkColorPrimary,
        error = Palette.EinkColorErrorDark, onError = Palette.Amoled,
        errorContainer = Palette.EinkColorErrorContainerDark, onErrorContainer = Palette.White,
    )

    /** Material You colors from the wallpaper (Android 12+), keeping true black when that dark variant is chosen. */
    @RequiresApi(Build.VERSION_CODES.S)
    fun dynamic(context: Context, isDark: Boolean, darkVariant: DarkVariant): ColorScheme {
        if (!isDark) return dynamicLightColorScheme(context)
        val scheme = dynamicDarkColorScheme(context)
        return if (darkVariant == DarkVariant.TRUE_BLACK) scheme.withTrueBlack() else scheme
    }

    /** Pure-black surfaces for OLED screens, laid over any dark scheme. */
    private fun ColorScheme.withTrueBlack(): ColorScheme = copy(
        background = Palette.Amoled,
        surface = Palette.Amoled,
        surfaceContainerLowest = Palette.Amoled,
        surfaceContainerLow = Palette.Amoled,
        surfaceContainer = Palette.NearBlack,
        surfaceContainerHigh = Palette.M3SurfaceContainerLowDark,
        surfaceContainerHighest = Palette.M3SurfaceContainerDark,
    )

    fun forProfile(
        profile: DisplayProfile,
        isDark: Boolean,
        darkVariant: DarkVariant,
        einkPalette: EinkPalette = EinkPalette.MONOCHROME,
    ): ColorScheme = when {
        profile.isMonochrome(einkPalette) -> eInk
        profile == DisplayProfile.E_INK -> if (isDark) eInkColorDark else eInkColorLight
        !isDark -> light
        darkVariant == DarkVariant.TRUE_BLACK -> trueBlack
        darkVariant == DarkVariant.SOFTER -> softerDark
        else -> dark
    }
}
