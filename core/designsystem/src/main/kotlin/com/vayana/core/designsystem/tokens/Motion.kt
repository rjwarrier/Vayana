package com.vayana.core.designsystem.tokens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp

/**
 * Spring-based, emphasized motion (PROMPT1uidesign.md §1). In [com.vayana.core.designsystem.theme.DisplayProfile.E_INK]
 * every duration in [Durations] collapses to zero and every spring in [Springs] becomes a snap —
 * not merely "fast": a fast animation still ghosts on E-Ink hardware (PROMPT2appbuild.md §6).
 */
object Durations {
    const val instant = 0
    const val short = 120
    const val medium = 220
    const val long = 350
}

object Springs {
    val fastSpatial: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    val slowSpatial: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    val effects: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    val snap: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
}

object Opacities {
    const val pressed = 0.7f // "Press state: 0.7 opacity on tap for interactive items" (handoff README)
    const val disabled = 0.38f
    const val scrimStrong = 0.55f // book-cover genre/count label scrim
    const val hairline = 0.10f
}

object Strokes {
    val hairline = 1.dp
    val hairlineEink = 1.5.dp
    val outline = 1.dp
    val emphasis = 1.5.dp
    val none = 0.dp
}
