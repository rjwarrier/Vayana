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
 * Type scale ported from the UI reference (section 01, "Type scale — M3 Expressive, emphasized").
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
        Font(R.font.libron_regular, weight = FontWeight.Normal),
        Font(R.font.libron_regular, weight = FontWeight.Medium),
        Font(R.font.libron_bold, weight = FontWeight.SemiBold),
        Font(R.font.libron_bold, weight = FontWeight.Bold),
        Font(R.font.libron_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
        Font(R.font.libron_bold_italic, weight = FontWeight.Bold, style = FontStyle.Italic),
    )

    /** Original substitute for the UI reference's `--font-ui` ("Google Sans") — see docs/DECISIONS.md. */
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

/** M3 roles mapped onto the UI reference's 5-step scale (Display 57 / Headline 32 / Title 22 / Body 16 / Label 12). */
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

/** Fixed-brand type styles for the share-card templates (§ share-card reference), independent of app theme. */
object ShareCardTypography {
    val wordmark = TextStyle(fontFamily = FontFamilies.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    val quoteMark = TextStyle(fontFamily = FontFamilies.Display, fontSize = 22.sp)
    val quoteBody = TextStyle(fontFamily = FontFamilies.Display, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 30.sp)
    val quoteBodyLarge = TextStyle(fontFamily = FontFamilies.Display, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 34.sp)
    val cardTitleDark = TextStyle(fontFamily = FontFamilies.Display, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    val cardSubtitleMono = TextStyle(fontFamily = FontFamilies.Mono, fontSize = 12.sp)
    val cardSeriesMono = TextStyle(fontFamily = FontFamilies.Mono, fontSize = 10.sp)
    val caption = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.5.sp)
    val bookTitle = TextStyle(fontFamily = FontFamilies.Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp)
    val bookTitleLarge = TextStyle(fontFamily = FontFamilies.Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp)
    val bookAuthor = TextStyle(fontFamily = FontFamilies.Display, fontSize = 14.sp)
    val statValue = TextStyle(fontFamily = FontFamilies.Display, fontWeight = FontWeight.Bold, fontSize = 18.sp)
}
