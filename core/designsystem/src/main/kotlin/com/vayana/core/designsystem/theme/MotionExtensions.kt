@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.vayana.core.designsystem.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vayana.core.designsystem.tokens.Durations

@Composable
fun isMotionEnabled(): Boolean {
    val profile = LocalDisplayProfile.current
    val motion = LocalMotionSetting.current
    return profile != DisplayProfile.E_INK && motion != MotionSetting.OFF
}

@Composable
fun isExpressiveMotion(): Boolean {
    val profile = LocalDisplayProfile.current
    val motion = LocalMotionSetting.current
    return profile != DisplayProfile.E_INK && motion == MotionSetting.FULL
}

@Composable
fun <T> vayanaSpring(
    dampingRatio: Float = Spring.DampingRatioLowBouncy,
    stiffness: Float = Spring.StiffnessMediumLow,
): FiniteAnimationSpec<T> {
    val profile = LocalDisplayProfile.current
    val motion = LocalMotionSetting.current
    return when {
        profile == DisplayProfile.E_INK || motion == MotionSetting.OFF -> snap()
        motion == MotionSetting.REDUCED -> spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        else -> spring(dampingRatio = dampingRatio, stiffness = stiffness)
    }
}

@Composable
fun <T> vayanaTween(
    durationMillis: Int = Durations.medium,
    delayMillis: Int = 0,
): FiniteAnimationSpec<T> {
    val profile = LocalDisplayProfile.current
    val motion = LocalMotionSetting.current
    return when {
        profile == DisplayProfile.E_INK || motion == MotionSetting.OFF -> snap()
        motion == MotionSetting.REDUCED -> tween(durationMillis = (durationMillis * 0.6f).toInt(), delayMillis = 0, easing = LinearOutSlowInEasing)
        else -> tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = FastOutSlowInEasing)
    }
}

@Composable
fun vayanaFadeIn(durationMillis: Int = Durations.medium): EnterTransition {
    val profile = LocalDisplayProfile.current
    val motion = LocalMotionSetting.current
    return when {
        profile == DisplayProfile.E_INK || motion == MotionSetting.OFF -> EnterTransition.None
        motion == MotionSetting.REDUCED -> fadeIn(animationSpec = tween(durationMillis = Durations.short, easing = LinearOutSlowInEasing))
        else -> fadeIn(animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing))
    }
}

@Composable
fun vayanaFadeOut(durationMillis: Int = Durations.short): ExitTransition {
    val profile = LocalDisplayProfile.current
    val motion = LocalMotionSetting.current
    return when {
        profile == DisplayProfile.E_INK || motion == MotionSetting.OFF -> ExitTransition.None
        motion == MotionSetting.REDUCED -> fadeOut(animationSpec = tween(durationMillis = Durations.short, easing = LinearOutSlowInEasing))
        else -> fadeOut(animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing))
    }
}

@Composable
fun vayanaSlideInVertically(
    initialOffsetY: (fullHeight: Int) -> Int = { it / 2 },
    durationMillis: Int = Durations.medium,
): EnterTransition {
    val profile = LocalDisplayProfile.current
    val motion = LocalMotionSetting.current
    return when {
        profile == DisplayProfile.E_INK || motion == MotionSetting.OFF -> EnterTransition.None
        motion == MotionSetting.REDUCED -> fadeIn(animationSpec = tween(durationMillis = Durations.short))
        else -> slideInVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            initialOffsetY = initialOffsetY,
        ) + fadeIn(animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing))
    }
}

@Composable
fun vayanaSlideOutVertically(
    targetOffsetY: (fullHeight: Int) -> Int = { it / 2 },
    durationMillis: Int = Durations.short,
): ExitTransition {
    val profile = LocalDisplayProfile.current
    val motion = LocalMotionSetting.current
    return when {
        profile == DisplayProfile.E_INK || motion == MotionSetting.OFF -> ExitTransition.None
        motion == MotionSetting.REDUCED -> fadeOut(animationSpec = tween(durationMillis = Durations.short))
        else -> slideOutVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            targetOffsetY = targetOffsetY,
        ) + fadeOut(animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing))
    }
}

@Composable
fun vayanaScaleIn(
    initialScale: Float = 0.88f,
    durationMillis: Int = Durations.medium,
): EnterTransition {
    val profile = LocalDisplayProfile.current
    val motion = LocalMotionSetting.current
    return when {
        profile == DisplayProfile.E_INK || motion == MotionSetting.OFF -> EnterTransition.None
        motion == MotionSetting.REDUCED -> fadeIn(animationSpec = tween(durationMillis = Durations.short))
        else -> scaleIn(
            initialScale = initialScale,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        ) + fadeIn(animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing))
    }
}

@Composable
fun vayanaScaleOut(
    targetScale: Float = 0.88f,
    durationMillis: Int = Durations.short,
): ExitTransition {
    val profile = LocalDisplayProfile.current
    val motion = LocalMotionSetting.current
    return when {
        profile == DisplayProfile.E_INK || motion == MotionSetting.OFF -> ExitTransition.None
        motion == MotionSetting.REDUCED -> fadeOut(animationSpec = tween(durationMillis = Durations.short))
        else -> scaleOut(
            targetScale = targetScale,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        ) + fadeOut(animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing))
    }
}

@Composable
fun <S> vayanaContentTransform(): AnimatedContentTransitionScope<S>.() -> ContentTransform {
    val profile = LocalDisplayProfile.current
    val motion = LocalMotionSetting.current
    return {
        when {
            profile == DisplayProfile.E_INK || motion == MotionSetting.OFF -> {
                EnterTransition.None togetherWith ExitTransition.None
            }
            motion == MotionSetting.REDUCED -> {
                fadeIn(animationSpec = tween(Durations.short, easing = LinearOutSlowInEasing)) togetherWith
                    fadeOut(animationSpec = tween(Durations.short, easing = LinearOutSlowInEasing))
            }
            else -> {
                (fadeIn(animationSpec = tween(Durations.medium, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))) togetherWith
                    (fadeOut(animationSpec = tween(Durations.short, easing = FastOutSlowInEasing)) +
                        scaleOut(targetScale = 0.96f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)))
            }
        }
    }
}

fun vayanaNavTabEnter(profile: DisplayProfile, motion: MotionSetting, scheme: MotionScheme): EnterTransition = when {
    profile == DisplayProfile.E_INK || motion == MotionSetting.OFF -> EnterTransition.None
    motion == MotionSetting.REDUCED -> fadeIn(animationSpec = scheme.fastEffectsSpec())
    else -> fadeIn(animationSpec = scheme.slowEffectsSpec())
}

fun vayanaNavTabExit(profile: DisplayProfile, motion: MotionSetting, scheme: MotionScheme): ExitTransition = when {
    profile == DisplayProfile.E_INK || motion == MotionSetting.OFF -> ExitTransition.None
    motion == MotionSetting.REDUCED -> fadeOut(animationSpec = scheme.fastEffectsSpec())
    else -> fadeOut(animationSpec = scheme.slowEffectsSpec())
}

fun vayanaNavEnter(profile: DisplayProfile, motion: MotionSetting, scheme: MotionScheme): EnterTransition = when {
    profile == DisplayProfile.E_INK || motion == MotionSetting.OFF -> EnterTransition.None
    motion == MotionSetting.REDUCED -> fadeIn(animationSpec = scheme.fastEffectsSpec())
    else -> fadeIn(animationSpec = scheme.slowEffectsSpec()) +
        slideInHorizontally(
            animationSpec = scheme.slowSpatialSpec(),
            initialOffsetX = { fullWidth -> fullWidth / 24 },
        )
}

fun vayanaNavExit(profile: DisplayProfile, motion: MotionSetting, scheme: MotionScheme): ExitTransition = when {
    profile == DisplayProfile.E_INK || motion == MotionSetting.OFF -> ExitTransition.None
    motion == MotionSetting.REDUCED -> fadeOut(animationSpec = scheme.fastEffectsSpec())
    else -> fadeOut(animationSpec = scheme.slowEffectsSpec()) +
        slideOutHorizontally(
            animationSpec = scheme.slowSpatialSpec(),
            targetOffsetX = { fullWidth -> -fullWidth / 24 },
        )
}

fun vayanaNavPopEnter(profile: DisplayProfile, motion: MotionSetting, scheme: MotionScheme): EnterTransition = when {
    profile == DisplayProfile.E_INK || motion == MotionSetting.OFF -> EnterTransition.None
    motion == MotionSetting.REDUCED -> fadeIn(animationSpec = scheme.fastEffectsSpec())
    else -> fadeIn(animationSpec = scheme.slowEffectsSpec()) +
        slideInHorizontally(
            animationSpec = scheme.slowSpatialSpec(),
            initialOffsetX = { fullWidth -> -fullWidth / 24 },
        )
}

fun vayanaNavPopExit(profile: DisplayProfile, motion: MotionSetting, scheme: MotionScheme): ExitTransition = when {
    profile == DisplayProfile.E_INK || motion == MotionSetting.OFF -> ExitTransition.None
    motion == MotionSetting.REDUCED -> fadeOut(animationSpec = scheme.fastEffectsSpec())
    else -> fadeOut(animationSpec = scheme.slowEffectsSpec()) +
        slideOutHorizontally(
            animationSpec = scheme.slowSpatialSpec(),
            targetOffsetX = { fullWidth -> fullWidth / 24 },
        )
}

@Composable
fun Modifier.vayanaAnimateContentSize(): Modifier {
    val profile = LocalDisplayProfile.current
    val motion = LocalMotionSetting.current
    return if (profile == DisplayProfile.E_INK || motion == MotionSetting.OFF) {
        this
    } else {
        animateContentSize(
            animationSpec = if (motion == MotionSetting.REDUCED) {
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            } else {
                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
            },
        )
    }
}
