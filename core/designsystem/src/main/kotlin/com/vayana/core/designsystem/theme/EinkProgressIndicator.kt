package com.vayana.core.designsystem.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import com.vayana.core.designsystem.tokens.Durations

/**
 * Drop-in replacement for [CircularProgressIndicator]'s indeterminate spinner. An indeterminate
 * spinner animates continuously by design - the one motion an E-Ink panel can least afford
 * (PROMPT2appbuild.md §6: same reasoning as [vayanaSpring] etc collapsing to snap there). A
 * fixed-angle determinate ring still reads as "busy" without ever asking the panel to refresh
 * on its own.
 */
@Composable
fun VayanaCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.circularColor,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
) {
    if (LocalDisplayProfile.current == DisplayProfile.E_INK) {
        CircularProgressIndicator(
            progress = { EinkStaticProgressFraction },
            modifier = modifier,
            color = color,
            strokeWidth = strokeWidth,
        )
    } else {
        CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = strokeWidth)
    }
}

/** Same reasoning as [VayanaCircularProgressIndicator], for the sliding indeterminate bar. */
@Composable
fun VayanaLinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
) {
    if (LocalDisplayProfile.current == DisplayProfile.E_INK) {
        LinearProgressIndicator(
            progress = { EinkStaticProgressFraction },
            modifier = modifier,
            color = color,
            trackColor = trackColor,
        )
    } else {
        LinearProgressIndicator(modifier = modifier, color = color, trackColor = trackColor)
    }
}

/** Determinate progress that eases between state updates and snaps for E-Ink or Motion Off. */
@Composable
fun VayanaLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress().coerceIn(0f, 1f),
        animationSpec = vayanaTween(durationMillis = Durations.medium),
        label = "VayanaLinearProgress",
    )
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        strokeCap = strokeCap,
    )
}

private const val EinkStaticProgressFraction = 0.75f
