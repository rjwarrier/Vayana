package com.vayana.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.VayanaTypography

private val VayanaShapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.extraSmall),
    small = RoundedCornerShape(Radii.small),
    medium = RoundedCornerShape(Radii.medium),
    large = RoundedCornerShape(Radii.large),
    extraLarge = RoundedCornerShape(Radii.extraLarge),
    largeIncreased = RoundedCornerShape(Radii.largeIncreased),
    extraLargeIncreased = RoundedCornerShape(Radii.extraLargeIncreased),
    extraExtraLarge = RoundedCornerShape(Radii.huge),
)

/**
 * Root theme. Every screen composes under this — never `MaterialTheme` directly — so
 * [LocalDisplayProfile] / [LocalDarkVariant] / [LocalMotionSetting] are always available and
 * the color scheme / motion scheme / shapes stay centrally controlled (PROMPT2appbuild.md §0.6).
 *
 * `isSystemInDarkTheme()` is called exactly once, here — nowhere else in the app may call it
 * (§0.6: "No component reads isSystemInDarkTheme() directly").
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VayanaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    displayProfile: DisplayProfile = LocalDisplayProfile.current,
    darkVariant: DarkVariant = LocalDarkVariant.current,
    motionSetting: MotionSetting = LocalMotionSetting.current,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = ColorSchemes.forProfile(displayProfile, isDark, darkVariant)
    val motionScheme = motionSchemeFor(displayProfile, motionSetting)

    CompositionLocalProvider(
        LocalDisplayProfile provides displayProfile,
        LocalDarkVariant provides darkVariant,
        LocalMotionSetting provides motionSetting,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = motionScheme,
            shapes = VayanaShapes,
            typography = VayanaTypography,
            content = content,
        )
    }
}
