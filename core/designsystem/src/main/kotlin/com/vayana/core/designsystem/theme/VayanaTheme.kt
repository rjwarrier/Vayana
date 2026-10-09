package com.vayana.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.VayanaTypography

/** No press ripple - a fading ripple is exactly the kind of animated repaint that ghosts on E-Ink. */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun hashCode(): Int = -1
    override fun equals(other: Any?): Boolean = other === this
}

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
 * the color scheme / motion scheme / shapes stay centrally controlled (docs/PRODUCT_SPEC.md §0.6).
 *
 * `isSystemInDarkTheme()` is called exactly once, here — nowhere else in the app may call it
 * (§0.6: "No component reads isSystemInDarkTheme() directly").
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VayanaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    displayProfile: DisplayProfile = LocalDisplayProfile.current,
    einkPalette: EinkPalette = LocalEinkPalette.current,
    darkVariant: DarkVariant = LocalDarkVariant.current,
    motionSetting: MotionSetting = LocalMotionSetting.current,
    dynamicColor: Boolean = LocalDynamicColor.current,
    dateFormatStyle: DateFormatStyle = LocalDateFormatStyle.current,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // Monochrome E-Ink stays black and white; color E-Ink follows Material You.
    val useDynamicColor = dynamicColor && !displayProfile.isMonochrome(einkPalette)
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val colorScheme = remember(context, configuration, isDark, useDynamicColor, displayProfile, einkPalette, darkVariant) {
        if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scheme = ColorSchemes.dynamic(context, isDark,
                if (displayProfile == DisplayProfile.E_INK) DarkVariant.STANDARD else darkVariant)
            if (displayProfile == DisplayProfile.E_INK) scheme.copy(surfaceTint = Color.Transparent)
            else scheme
        } else {
            ColorSchemes.forProfile(displayProfile, isDark, darkVariant, einkPalette)
        }
    }
    val motionScheme = motionSchemeFor(displayProfile, motionSetting)

    CompositionLocalProvider(
        LocalDisplayProfile provides displayProfile,
        LocalEinkPalette provides einkPalette,
        LocalDarkVariant provides darkVariant,
        LocalMotionSetting provides motionSetting,
        LocalDynamicColor provides useDynamicColor,
        LocalDateFormatStyle provides dateFormatStyle,
        // Ripple fade and stretch/glow overscroll both animate a repaint - deadly for E-Ink ghosting.
        LocalIndication provides if (displayProfile == DisplayProfile.E_INK) NoIndication else LocalIndication.current,
        LocalOverscrollFactory provides if (displayProfile == DisplayProfile.E_INK) null else LocalOverscrollFactory.current,
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
