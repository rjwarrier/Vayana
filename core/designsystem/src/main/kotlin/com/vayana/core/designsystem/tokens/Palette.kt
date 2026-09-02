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

    val TextMutedDark = Color(0xFF8A9BB0)
    val TextMutedLight = Color(0xFF6B7280)

    val Amoled = Color(0xFF000000)
    val NearBlack = Color(0xFF0A0A0A)

    val Gold500 = Color(0xFFC9A84C)
    val Gold300 = Color(0xFFE2C97E)
    val Gold700 = Color(0xFFA0832A)

    val FgPrimaryLight = Color(0xFF1A1A1A)

    val BorderLight = Color(0x1A000000) // rgba(0,0,0,.10)
    val BorderDark = Color(0x14FFFFFF)  // rgba(255,255,255,.08)

    /** E-Ink build (PROMPT2appbuild.md §6, PROMPT1uidesign.md §2): pure #FFFFFF / #000000 only. */
    val EinkBackground = White
    val EinkForeground = Amoled
}
