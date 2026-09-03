package com.vayana.core.designsystem.tokens

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vayana.core.designsystem.R

/**
 * Type scale ported from the handoff (section 01, "Type scale — M3 Expressive, emphasized").
 * The app interface uses JetBrains Mono by product choice; in-book reader text remains controlled
 * by reader settings and can still use the serif/sans/mono options.
 *
 * All four families ship as variable fonts (single .ttf per family/style, weight selected via
 * [FontVariation.Settings]) rather than one static file per weight.
 */
object FontFamilies {
    val Display = FontFamily(
        Font(R.font.playfair_display, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.playfair_display, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.playfair_display, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.playfair_display, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
        Font(R.font.playfair_display, weight = FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
        Font(R.font.playfair_display_italic, weight = FontWeight.Normal, style = FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    )

    val Serif = FontFamily(
        Font(R.font.lora, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.lora, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.lora, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.lora_italic, weight = FontWeight.Normal, style = FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    )

    /** Original substitute for the handoff's `--font-ui` ("Google Sans") — see docs/DECISIONS.md. */
    val Ui = FontFamily(
        Font(R.font.inter, weight = FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
        Font(R.font.inter, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.inter, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.inter, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )

    val Mono = FontFamily(
        Font(R.font.jetbrains_mono, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.jetbrains_mono, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    )
}

/** M3 roles mapped onto the handoff's 5-step scale (Display 57 / Headline 32 / Title 22 / Body 16 / Label 12). */
val VayanaTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp),

    headlineLarge = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),

    titleLarge = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),

    bodyLarge = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),

    labelLarge = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp),
)
