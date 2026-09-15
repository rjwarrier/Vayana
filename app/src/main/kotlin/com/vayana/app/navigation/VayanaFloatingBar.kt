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


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VayanaFloatingBar(navController: NavHostController, modifier: Modifier = Modifier) {
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val colors = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    val destinations = TopLevelDestination.entries
    val selectedFlags = destinations.map { destination -> currentDestination?.hasRoute(destination.routeClass) == true }
    val selectedIndex = destinations.indexOfFirst { destination ->
        currentDestination?.hasRoute(destination.routeClass) == true
    }.let { if (it == -1) 0 else it }

    // Animated values are kept as State and read in layout/draw lambdas to avoid recomposition.
    val spatialProgress: List<State<Float>> = selectedFlags.map { selected ->
        animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = motionScheme.defaultSpatialSpec(),
            label = "floatingNavSpatialProgress",
        )
    }
    val effectsProgress: List<State<Float>> = selectedFlags.map { selected ->
        animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = motionScheme.defaultEffectsSpec(),
            label = "floatingNavEffectsProgress",
        )
    }
    val itemHeight = Sizes.floatingNavItem - (Spacing.xs * 2)
    val labelMaxWidth = Sizes.floatingNavSelectedItem - Spacing.lg - Sizes.icon - Spacing.sm - Spacing.lg
    val indicatorColor = colors.primary.copy(alpha = 0.16f)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Invariant bar width: 1 selected tab + remaining unselected tabs + horizontal padding.
    val totalContentWidth = Sizes.floatingNavSelectedItem + (Sizes.floatingNavUnselectedItem * (destinations.size - 1))
    val totalBarWidth = totalContentWidth + (Spacing.xs * 2)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
    ) {
        val alignBesideFab = currentDestination?.hasRoute(TopLevelRoute.Library::class) == true &&
            maxWidth < Sizes.floatingNavFabAlignmentBreakpoint
        // 0 = start (beside the Library FAB), 0.5 = centered. Springs smoothly with bouncy settle.
        val horizontalBias = animateFloatAsState(
            targetValue = if (alignBesideFab) 0f else 0.5f,
            animationSpec = motionScheme.defaultSpatialSpec(),
            label = "floatingNavHorizontalBias",
        )

        val unselectedPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            Sizes.floatingNavUnselectedItem.toPx()
        }
        val selectedPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            Sizes.floatingNavSelectedItem.toPx()
        }

        // The indicator offset glides smoothly across tabs with spring physics.
        val indicatorOffset = animateFloatAsState(
            targetValue = selectedIndex * unselectedPx,
            animationSpec = motionScheme.defaultSpatialSpec(),
            label = "floatingNavIndicatorOffset",
        )

        Surface(
            modifier = Modifier
                .widthIn(max = totalBarWidth)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0))
                    layout(constraints.maxWidth, placeable.height) {
                        val availableSpace = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
                        val x = (availableSpace * horizontalBias.value).roundToInt()
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
                        val left = if (layoutDirection == LayoutDirection.Rtl) {
                            size.width - indicatorOffset.value - selectedPx
                        } else {
                            indicatorOffset.value
                        }
                        drawRoundRect(
                            color = indicatorColor,
                            topLeft = Offset(left, 0f),
                            size = Size(selectedPx, size.height),
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
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

                        // Tactile press squish with expressive spring pop on release.
                        val itemScale = animateFloatAsState(
                            targetValue = if (pressed) 0.93f else 1f,
                            animationSpec = motionScheme.fastSpatialSpec(),
                            label = "floatingNavItemScale",
                        )

                        Box(
                            modifier = Modifier
                                .height(itemHeight)
                                .layout { measurable, constraints ->
                                    val deltaPx = selectedPx - unselectedPx
                                    val width = (unselectedPx + deltaPx * spatial.value.coerceIn(0f, 1.15f))
                                        .roundToInt()
                                        .coerceAtLeast(0)
                                    val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                                    layout(width, placeable.height) { placeable.placeRelative(0, 0) }
                                }
                                .clip(CircleShape)
                                .graphicsLayer {
                                    scaleX = itemScale.value
                                    scaleY = itemScale.value
                                }
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
                                },
                            )
                            Icon(
                                imageVector = destination.selectedIcon,
                                contentDescription = null,
                                tint = colors.onPrimaryContainer,
                                modifier = iconModifier.graphicsLayer {
                                    alpha = effects.value.coerceIn(0f, 1f)
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
                                    .wrapContentWidth(Alignment.Start, unbounded = true)
                                    .widthIn(max = labelMaxWidth)
                                    .graphicsLayer {
                                        alpha = effects.value.coerceIn(0f, 1f)
                                        val slide = Spacing.sm.toPx() * (1f - spatial.value).coerceIn(-0.2f, 1f)
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
