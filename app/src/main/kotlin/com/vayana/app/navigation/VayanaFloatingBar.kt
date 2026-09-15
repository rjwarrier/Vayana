package com.vayana.app.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import kotlin.math.roundToInt

private const val PressedIconScale = 0.82f
private const val LabelFadeThreshold = 0.35f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VayanaFloatingBar(navController: NavHostController, modifier: Modifier = Modifier) {
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val colors = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    val destinations = TopLevelDestination.entries
    val selectedFlags = destinations.map { destination -> currentDestination?.hasRoute(destination.routeClass) == true }

    // Animated values are kept as State and only read in layout/draw lambdas, so a tab switch
    // re-measures and redraws the bar without recomposing it every frame.
    val spatialProgress: List<State<Float>> = selectedFlags.map { selected ->
        animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = motionScheme.fastSpatialSpec(),
            label = "floatingNavSpatialProgress",
        )
    }
    val effectsProgress: List<State<Float>> = selectedFlags.map { selected ->
        animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = motionScheme.fastEffectsSpec(),
            label = "floatingNavEffectsProgress",
        )
    }
    val itemHeight = Sizes.floatingNavItem - (Spacing.xs * 2)
    val labelMaxWidth = Sizes.floatingNavSelectedItem - Spacing.lg - Sizes.icon - Spacing.sm - Spacing.lg
    val indicatorColor = colors.primary.copy(alpha = 0.16f)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
    ) {
        val alignBesideFab = currentDestination?.hasRoute(TopLevelRoute.Library::class) == true &&
            maxWidth < Sizes.floatingNavFabAlignmentBreakpoint
        // 0 = start (beside the Library FAB), 0.5 = centered. Springs instead of snapping alignment.
        val horizontalBias = animateFloatAsState(
            targetValue = if (alignBesideFab) 0f else 0.5f,
            animationSpec = motionScheme.defaultSpatialSpec(),
            label = "floatingNavHorizontalBias",
        )

        Surface(
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0))
                layout(constraints.maxWidth, placeable.height) {
                    val x = ((constraints.maxWidth - placeable.width) * horizontalBias.value).roundToInt()
                    placeable.placeRelative(x, 0)
                }
            },
            shape = CircleShape,
            color = colors.primaryContainer,
            shadowElevation = Elevations.shadowLarge,
        ) {
            Box(
                modifier = Modifier
                    .height(Sizes.floatingNavItem)
                    .padding(Spacing.xs)
                    .drawBehind {
                        // Indicator follows the springing item widths exactly: its position is the
                        // progress-weighted start of each item, so it never drifts off its tab mid-flight.
                        val unselectedPx = Sizes.floatingNavUnselectedItem.toPx()
                        val deltaPx = Sizes.floatingNavSelectedItem.toPx() - unselectedPx
                        var itemStart = 0f
                        var weight = 0f
                        var indicatorStart = 0f
                        var indicatorWidth = 0f
                        var alpha = 0f
                        spatialProgress.forEachIndexed { index, progressState ->
                            val progress = progressState.value
                            val itemWidth = unselectedPx + deltaPx * progress
                            weight += progress
                            indicatorStart += itemStart * progress
                            indicatorWidth += itemWidth * progress
                            alpha += effectsProgress[index].value
                            itemStart += itemWidth
                        }
                        if (weight > 0.001f && alpha > 0.001f) {
                            indicatorStart /= weight
                            indicatorWidth /= weight
                            val left = if (layoutDirection == LayoutDirection.Rtl) {
                                size.width - indicatorStart - indicatorWidth
                            } else {
                                indicatorStart
                            }
                            drawRoundRect(
                                color = indicatorColor,
                                topLeft = Offset(left, 0f),
                                size = Size(indicatorWidth, size.height),
                                cornerRadius = CornerRadius(size.height / 2f),
                                alpha = alpha.coerceIn(0f, 1f),
                            )
                        }
                    },
            ) {
                Row(
                    modifier = Modifier.selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    destinations.forEachIndexed { index, destination ->
                        val selected = selectedFlags[index]
                        val spatial = spatialProgress[index]
                        val effects = effectsProgress[index]
                        val label = stringResource(destination.labelRes)
                        val interactionSource = remember { MutableInteractionSource() }
                        val pressed by interactionSource.collectIsPressedAsState()
                        // Squish on press; release rides the expressive spring back past 1f for a pop.
                        val iconScale = animateFloatAsState(
                            targetValue = if (pressed) PressedIconScale else 1f,
                            animationSpec = motionScheme.fastSpatialSpec(),
                            label = "floatingNavIconScale",
                        )

                        Box(
                            modifier = Modifier
                                .height(itemHeight)
                                .layout { measurable, constraints ->
                                    val unselectedPx = Sizes.floatingNavUnselectedItem.toPx()
                                    val deltaPx = Sizes.floatingNavSelectedItem.toPx() - unselectedPx
                                    val width = (unselectedPx + deltaPx * spatial.value).roundToInt().coerceAtLeast(0)
                                    val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                                    layout(width, placeable.height) { placeable.placeRelative(0, 0) }
                                }
                                .clip(CircleShape)
                                .selectable(
                                    selected = selected,
                                    interactionSource = interactionSource,
                                    indication = ripple(),
                                    role = Role.Tab,
                                    onClick = { navController.navigateToTopLevel(destination.route) },
                                ),
                        ) {
                            val iconModifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = Spacing.lg)
                                .size(Sizes.icon)
                            Icon(
                                imageVector = destination.unselectedIcon,
                                contentDescription = if (selected) null else label,
                                tint = colors.onPrimaryContainer,
                                modifier = iconModifier.graphicsLayer {
                                    alpha = (1f - effects.value).coerceIn(0f, 1f)
                                    scaleX = iconScale.value
                                    scaleY = iconScale.value
                                },
                            )
                            Icon(
                                imageVector = destination.selectedIcon,
                                contentDescription = null,
                                tint = colors.onPrimaryContainer,
                                modifier = iconModifier.graphicsLayer {
                                    alpha = effects.value.coerceIn(0f, 1f)
                                    scaleX = iconScale.value
                                    scaleY = iconScale.value
                                },
                            )
                            Text(
                                text = label,
                                color = colors.onPrimaryContainer,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = Spacing.lg + Sizes.icon + Spacing.sm)
                                    // Constant measure constraints: text layout is cached while the item width springs.
                                    .wrapContentWidth(Alignment.Start, unbounded = true)
                                    .widthIn(max = labelMaxWidth)
                                    .graphicsLayer {
                                        val progress = ((effects.value - LabelFadeThreshold) / (1f - LabelFadeThreshold))
                                            .coerceIn(0f, 1f)
                                        alpha = progress
                                        val slide = Spacing.sm.toPx() * (1f - spatial.value.coerceIn(0f, 1f))
                                        translationX = if (isRtl) slide else -slide
                                    }
                                    .then(if (selected) Modifier else Modifier.clearAndSetSemantics { }),
                            )
                        }
                    }
                }
            }
        }
    }
}
