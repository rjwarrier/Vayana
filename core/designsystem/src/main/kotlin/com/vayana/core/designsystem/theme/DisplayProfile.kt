package com.vayana.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf

/** docs/PRODUCT_SPEC.md §6. No component reads `isSystemInDarkTheme()` or checks hardware directly — everything reads this. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class DisplayProfile { STANDARD, E_INK }

/** Color capability is independent of the panel's motion and refresh constraints. */
enum class EinkPalette { MONOCHROME, COLOR }

fun DisplayProfile.isMonochrome(einkPalette: EinkPalette): Boolean =
    this == DisplayProfile.E_INK && einkPalette == EinkPalette.MONOCHROME

/** Design/DESIGN_SPEC.md §1: softer dark (raised black point) and true-black (AMOLED) are both dark-mode variants. */
enum class DarkVariant { STANDARD, SOFTER, TRUE_BLACK }

enum class MotionSetting { FULL, REDUCED, OFF }

val LocalDisplayProfile = staticCompositionLocalOf { DisplayProfile.STANDARD }
val LocalEinkPalette = staticCompositionLocalOf { EinkPalette.MONOCHROME }
val LocalDarkVariant = staticCompositionLocalOf { DarkVariant.STANDARD }
val LocalMotionSetting = staticCompositionLocalOf { MotionSetting.FULL }
