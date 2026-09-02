package com.vayana.core.designsystem.tokens

import androidx.compose.ui.unit.dp

/**
 * Elevation is expressed by surface *tiers*, not shadow depth (PROMPT2appbuild.md §6) — see
 * `ColorSchemes.kt` for the `surfaceContainerLowest → Highest` role ladder each [SurfaceTier]
 * maps to. The handoff's literal light-mode drop shadows (`shadow-sm/md/lg`, used on the FAB
 * and the search field) are kept as an additional light-only accent since the high-fidelity
 * design uses them explicitly; dark mode never draws a shadow (handoff: "elevation conveyed
 * via layered surface colors"), and E-Ink never draws either — a 1px outline replaces both.
 */
enum class SurfaceTier { Lowest, Low, Container, High, Highest }

object Elevations {
    val shadowSmall = 1.dp
    val shadowMedium = 2.dp
    val shadowLarge = 4.dp
}
