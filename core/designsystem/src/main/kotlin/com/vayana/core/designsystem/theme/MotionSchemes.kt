package com.vayana.core.designsystem.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Every [MotionScheme] spec collapses to [snap] — zero duration, no spring overshoot.
 * PROMPT2appbuild.md §6: on E-Ink this is *not* "fast", it's zero, because a fast animation
 * still ghosts on E-Ink hardware. Used for [DisplayProfile.E_INK] and [MotionSetting.OFF].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object SnapMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

/**
 * Custom expressive scheme tuned for lively, organic feedback.
 * Uses a low-bouncy damping ratio (0.74) and responsive stiffness so movements
 * have an organic tactile overshoot and settle without feeling stiff or sluggish.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class VayanaExpressiveMotionScheme(
    private val base: MotionScheme = MotionScheme.expressive(),
) : MotionScheme by base {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow)

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMedium)

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val ExpressiveSchemeInstance: MotionScheme = VayanaExpressiveMotionScheme()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun motionSchemeFor(profile: DisplayProfile, motionSetting: MotionSetting): MotionScheme = when {
    profile == DisplayProfile.E_INK -> SnapMotionScheme
    motionSetting == MotionSetting.OFF -> SnapMotionScheme
    motionSetting == MotionSetting.REDUCED -> MotionScheme.standard()
    else -> ExpressiveSchemeInstance
}
