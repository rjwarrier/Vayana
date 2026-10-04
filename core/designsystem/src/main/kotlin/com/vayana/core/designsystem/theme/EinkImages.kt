package com.vayana.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * Book covers on a monochrome E-Ink panel: greyscale with the contrast pushed up. A colour cover otherwise arrives as a flat
 * mid-grey where red, green and blue happen to share a luminance, and its title text loses its edge.
 * Null on every other display, where the cover is drawn as it is.
 */
@Composable
fun rememberCoverColorFilter(): ColorFilter? {
    val eink = LocalDisplayProfile.current.isMonochrome(LocalEinkPalette.current)
    return remember(eink) { if (eink) EinkCoverColorFilter else null }
}

private const val CoverContrast = 1.35f
private const val LumaRed = 0.2126f
private const val LumaGreen = 0.7152f
private const val LumaBlue = 0.0722f

// Every channel becomes contrast * luma, pivoting on mid-grey so light stays light and dark stays dark.
private const val CoverContrastOffset = 127.5f * (1f - CoverContrast)

private val EinkCoverColorFilter: ColorFilter = run {
    val row = floatArrayOf(
        CoverContrast * LumaRed, CoverContrast * LumaGreen, CoverContrast * LumaBlue, 0f, CoverContrastOffset,
    )
    ColorFilter.colorMatrix(
        ColorMatrix(row + row + row + floatArrayOf(0f, 0f, 0f, 1f, 0f)),
    )
}
