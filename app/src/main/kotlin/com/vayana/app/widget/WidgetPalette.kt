package com.vayana.app.widget

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.designsystem.theme.ColorSchemes
import com.vayana.core.designsystem.theme.DarkVariant
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.EinkPalette
import com.vayana.core.designsystem.theme.ThemeMode
import com.vayana.core.designsystem.theme.isMonochrome

/** The app's theme settings the widgets colour themselves from, so every widget matches the app and each other. */
internal data class WidgetTheme(
    val themeMode: ThemeMode,
    val displayProfile: DisplayProfile,
    val einkPalette: EinkPalette,
    val darkVariant: DarkVariant,
    val dynamicColor: Boolean,
)

internal fun SettingsSnapshot.widgetTheme() = WidgetTheme(themeMode, displayProfile, einkPalette, darkVariant, dynamicColor)

/** The Material 3 roles the widgets draw with, resolved from [WidgetTheme]: RemoteViews can't use Compose's theme. */
internal data class WidgetPalette(
    val surface: Int,
    val surfaceHigh: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondaryContainer: Int,
    val tertiary: Int,
    val outline: Int,
    val outlineVariant: Int,
    val errorContainer: Int,
    val onErrorContainer: Int,
    val eInk: Boolean,
)

internal fun WidgetTheme.palette(context: Context): WidgetPalette {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }
    val scheme = if (
        // Wallpaper colours on colour E-Ink too; only the monochrome palette replaces them.
        dynamicColor && !displayProfile.isMonochrome(einkPalette) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        ColorSchemes.dynamic(context, isDark, darkVariant)
    } else {
        ColorSchemes.forProfile(displayProfile, isDark, darkVariant, einkPalette)
    }
    return scheme.toWidgetPalette(displayProfile == DisplayProfile.E_INK)
}

private fun ColorScheme.toWidgetPalette(eInk: Boolean) = WidgetPalette(
    surface = surfaceContainer.toArgb(),
    surfaceHigh = surfaceContainerHigh.toArgb(),
    onSurface = onSurface.toArgb(),
    onSurfaceVariant = onSurfaceVariant.toArgb(),
    primary = primary.toArgb(),
    onPrimary = onPrimary.toArgb(),
    primaryContainer = primaryContainer.toArgb(),
    onPrimaryContainer = onPrimaryContainer.toArgb(),
    secondaryContainer = secondaryContainer.toArgb(),
    tertiary = tertiary.toArgb(),
    outline = outline.toArgb(),
    outlineVariant = outlineVariant.toArgb(),
    errorContainer = errorContainer.toArgb(),
    onErrorContainer = onErrorContainer.toArgb(),
    eInk = eInk,
)

/**
 * The widget's background and outline: the shared corner shapes for [appearance], tinted to [palette]. The outline is
 * full strength on E-Ink, where a faint one would vanish.
 */
internal fun RemoteViews.applySurface(backgroundId: Int, borderId: Int, appearance: WidgetAppearance, palette: WidgetPalette) {
    setImageViewResource(backgroundId, appearance.backgroundRes())
    setInt(backgroundId, "setColorFilter", palette.surface)
    setViewVisibility(borderId, if (appearance.border) View.VISIBLE else View.GONE)
    setImageViewResource(borderId, appearance.borderRes())
    setInt(borderId, "setColorFilter", palette.outline)
    setFloat(borderId, "setAlpha", if (palette.eInk) 1f else BorderAlpha)
}

/** Tints a view's background (Android 12+; earlier versions keep the layout's own colours). */
internal fun RemoteViews.tintBackground(viewId: Int, color: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setColorStateList(viewId, "setBackgroundTintList", ColorStateList.valueOf(color))
    }
}

private const val BorderAlpha = 0.25f
