package com.vayana.core.designsystem.tokens

import androidx.compose.ui.graphics.Color

/**
 * Raw color ramp, ported 1:1 from the design handoff's CSS custom properties
 * (`Design/design_handoff_vayana/vayana_ui_design.html`, section 01 Foundations).
 * Nothing outside this file (and [ColorSchemes]) may reference a literal hex value — see
 * the Konsist test in this module's `src/test`, which enforces §0.2 of PROMPT2appbuild.md.
 */
object Palette {
    val Forest900 = Color(0xFF2A3F23)
    val Forest700 = Color(0xFF3D5C33)
    val Forest500 = Color(0xFF4A7041)
    val Forest300 = Color(0xFF6F9A64)
    val Forest100 = Color(0xFFC8DCC3)

    val Teal700 = Color(0xFF00A89E)
    val Teal500 = Color(0xFF00D4C8)
    val Teal300 = Color(0xFF5DE8E0)
    val Teal100 = Color(0xFFB8F5F2)

    val Navy950 = Color(0xFF080D13)
    val Navy900 = Color(0xFF0D1520)
    val Navy800 = Color(0xFF131C27)
    val Navy700 = Color(0xFF1E2A38)
    val Navy600 = Color(0xFF253447)
    val Navy500 = Color(0xFF2E4058)
    val Navy400 = Color(0xFF3F5470)

    val Cream50 = Color(0xFFFDFCFA)
    val Cream100 = Color(0xFFF5F0E8)
    val Cream200 = Color(0xFFEDE9E0)
    val Cream300 = Color(0xFFE0D8CB)
    val Cream400 = Color(0xFFC8BCAA)

    val White = Color(0xFFFFFFFF)
    val WarmWhite = Color(0xFFE8E0D0)
    val SepiaSurface = Color(0xFFFFE8BF)
    val DarkReaderSurface = Color(0xFF111827)
    val ReaderLightBackground = White
    val ReaderLightText = Color(0xFF172033)
    val ReaderPaperBackground = Color(0xFFF7F1E6)
    val ReaderPaperText = Color(0xFF231F1A)
    val ReaderSepiaBackground = Color(0xFFEED3A3)
    val ReaderSepiaText = Color(0xFF2B2115)
    val ReaderMintBackground = Color(0xFFE5F3EA)
    val ReaderMintText = Color(0xFF14251D)
    val ReaderSkyBackground = Color(0xFFE7F0FA)
    val ReaderSkyText = Color(0xFF142133)
    val ReaderRoseBackground = Color(0xFFF7E8EA)
    val ReaderRoseText = Color(0xFF2B181D)
    val ReaderDarkBackground = DarkReaderSurface
    val ReaderDarkText = Color(0xFFF8F4EC)

    val TextMutedDark = Color(0xFF8A9BB0)
    val TextMutedLight = Color(0xFF6B7280)

    val Amoled = Color(0xFF000000)
    val NearBlack = Color(0xFF0A0A0A)
    val ReaderOledBackground = Amoled
    val ReaderOledText = Color(0xFFEDEDED)

    val Gold500 = Color(0xFFC9A84C)
    val Gold300 = Color(0xFFE2C97E)
    val Gold700 = Color(0xFFA0832A)

    val FgPrimaryLight = Color(0xFF1A1A1A)

    val BorderLight = Color(0x1A000000) // rgba(0,0,0,.10)
    val BorderDark = Color(0x14FFFFFF)  // rgba(255,255,255,.08)

    val M3PrimaryLight = Color(0xFF006A60)
    val M3OnPrimaryLight = White
    val M3PrimaryContainerLight = Color(0xFF9EF2E5)
    val M3OnPrimaryContainerLight = Color(0xFF00201C)
    val M3SecondaryLight = Color(0xFF4A635F)
    val M3OnSecondaryLight = White
    val M3SecondaryContainerLight = Color(0xFFCCE8E1)
    val M3OnSecondaryContainerLight = Color(0xFF05201C)
    val M3TertiaryLight = Color(0xFF456179)
    val M3OnTertiaryLight = White
    val M3TertiaryContainerLight = Color(0xFFCCE5FF)
    val M3OnTertiaryContainerLight = Color(0xFF001E31)
    val M3BackgroundLight = Color(0xFFF4FBF8)
    val M3OnBackgroundLight = Color(0xFF161D1B)
    val M3SurfaceLight = M3BackgroundLight
    val M3OnSurfaceLight = M3OnBackgroundLight
    val M3SurfaceVariantLight = Color(0xFFDAE5E1)
    val M3OnSurfaceVariantLight = Color(0xFF3F4946)
    val M3SurfaceContainerLowestLight = White
    val M3SurfaceContainerLowLight = Color(0xFFEFF5F2)
    val M3SurfaceContainerLight = Color(0xFFE9EFEC)
    val M3SurfaceContainerHighLight = Color(0xFFE3EAE7)
    val M3SurfaceContainerHighestLight = Color(0xFFDDE4E1)
    val M3OutlineLight = Color(0xFF6F7976)
    val M3OutlineVariantLight = Color(0xFFBEC9C5)

    val M3PrimaryDark = Color(0xFF82D5C9)
    val M3OnPrimaryDark = Color(0xFF003730)
    val M3PrimaryContainerDark = Color(0xFF005047)
    val M3OnPrimaryContainerDark = Color(0xFF9EF2E5)
    val M3SecondaryDark = Color(0xFFB1CCC5)
    val M3OnSecondaryDark = Color(0xFF1C3531)
    val M3SecondaryContainerDark = Color(0xFF334B47)
    val M3OnSecondaryContainerDark = Color(0xFFCCE8E1)
    val M3TertiaryDark = Color(0xFFADCAE6)
    val M3OnTertiaryDark = Color(0xFF143349)
    val M3TertiaryContainerDark = Color(0xFF2C4A61)
    val M3OnTertiaryContainerDark = Color(0xFFCCE5FF)
    val M3BackgroundDark = Color(0xFF0E1513)
    val M3OnBackgroundDark = Color(0xFFDDE4E1)
    val M3SurfaceDark = M3BackgroundDark
    val M3OnSurfaceDark = M3OnBackgroundDark
    val M3SurfaceVariantDark = Color(0xFF3F4946)
    val M3OnSurfaceVariantDark = Color(0xFFBEC9C5)
    val M3SurfaceContainerLowestDark = Color(0xFF090F0E)
    val M3SurfaceContainerLowDark = Color(0xFF161D1B)
    val M3SurfaceContainerDark = Color(0xFF1A211F)
    val M3SurfaceContainerHighDark = Color(0xFF252B2A)
    val M3SurfaceContainerHighestDark = Color(0xFF303635)
    val M3OutlineDark = Color(0xFF899390)
    val M3OutlineVariantDark = Color(0xFF3F4946)

    /** E-Ink build (PROMPT2appbuild.md §6, PROMPT1uidesign.md §2): pure #FFFFFF / #000000 only. */
    val EinkBackground = White
    val EinkForeground = Amoled
    val EinkContainerLow = Color(0xFFF7F7F7)
    val EinkContainer = Color(0xFFF0F0F0)
    val EinkContainerHigh = Color(0xFFE8E8E8)
    val EinkContainerHighest = Color(0xFFE0E0E0)
    val EinkOutline = Color(0x99000000)
    val EinkOutlineVariant = Color(0x4D000000)
}
