package com.vayana.app.navigation

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
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
import com.vayana.core.designsystem.theme.LocalDynamicColor
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VayanaFloatingBar(navController: NavHostController, modifier: Modifier = Modifier) {
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val colors = MaterialTheme.colorScheme
    val isWallpaperColor = LocalDynamicColor.current && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val motionScheme = MaterialTheme.motionScheme
    val destinations = TopLevelDestination.entries
    val selectedFlags = destinations.map { destination -> currentDestination?.isSelectedFor(destination) == true }
    val selectedIndex = selectedFlags.indexOfFirst { it }.let { if (it == -1) 0 else it }

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
    val indicatorColor = colors.primary.copy(alpha = 0.16f)
    val barColor = if (isWallpaperColor) colors.surfaceContainerHigh else colors.primaryContainer
    val showBookDetailBackAttachment = currentDestination?.hasRoute(BookDetailRoute::class) == true

    // Invariant bar width: 1 selected tab + remaining unselected tabs + horizontal padding.
    val totalContentWidth = Sizes.floatingNavSelectedItem + (Sizes.floatingNavUnselectedItem * (destinations.size - 1))
    val totalBarWidth = totalContentWidth + (Spacing.xs * 2)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        val unselectedPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            Sizes.floatingNavUnselectedItem.toPx()
        }
        val selectedPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            Sizes.floatingNavSelectedItem.toPx()
        }

        // The front of the pill follows the tab while its trailing edge catches up.
        val indicatorLeadingOffset = animateFloatAsState(
            targetValue = selectedIndex * unselectedPx,
            animationSpec = motionScheme.defaultSpatialSpec(),
            label = "floatingNavIndicatorLeadingOffset",
        )
        val indicatorTrailingOffset = animateFloatAsState(
            targetValue = selectedIndex * unselectedPx,
            animationSpec = motionScheme.slowSpatialSpec(),
            label = "floatingNavIndicatorTrailingOffset",
        )
        val maxStretchPx = with(androidx.compose.ui.platform.LocalDensity.current) { Spacing.xl.toPx() }

        val attachmentSize = Sizes.floatingNavUnselectedItem
        val attachmentOverlap = Spacing.sm
        val attachmentOffset = -(totalBarWidth / 2 + attachmentSize / 2 - attachmentOverlap)
        val attachmentContentColor = if (isWallpaperColor) colors.primary else colors.onPrimaryContainer
        AnimatedVisibility(
            visible = showBookDetailBackAttachment,
            modifier = Modifier
                .offset(x = attachmentOffset)
                .size(attachmentSize),
            enter = slideInHorizontally(
                animationSpec = motionScheme.defaultSpatialSpec(),
                initialOffsetX = { fullWidth -> fullWidth },
            ) + scaleIn(
                animationSpec = motionScheme.defaultSpatialSpec(),
                initialScale = 0.72f,
                transformOrigin = TransformOrigin(1f, 0.5f),
            ) + fadeIn(
                animationSpec = motionScheme.defaultEffectsSpec(),
                initialAlpha = 0.35f,
            ),
            exit = slideOutHorizontally(
                animationSpec = motionScheme.defaultSpatialSpec(),
                targetOffsetX = { fullWidth -> fullWidth },
            ) + scaleOut(
                animationSpec = motionScheme.defaultSpatialSpec(),
                targetScale = 0.72f,
                transformOrigin = TransformOrigin(1f, 0.5f),
            ) + fadeOut(animationSpec = motionScheme.defaultEffectsSpec()),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // The shape rotates independently so the click target and arrow remain upright.
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = -90f },
                    shape = MaterialShapes.Ghostish.toShape(),
                    color = barColor,
                    shadowElevation = Elevations.shadowLarge,
                ) {}
                IconButton(
                    onClick = {
                        val returnedToLibrary = navController.popBackStack(
                            route = TopLevelRoute.Library,
                            inclusive = false,
                        )
                        if (!returnedToLibrary) navController.navigateToTopLevel(TopLevelRoute.Library)
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(com.vayana.core.resources.R.string.settings_back_content_description),
                        tint = attachmentContentColor,
                        modifier = Modifier.size(Sizes.icon),
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.width(totalBarWidth),
            shape = CircleShape,
            color = barColor,
            shadowElevation = Elevations.shadowLarge,
        ) {
            Box(
                modifier = Modifier
                    .height(Sizes.floatingNavItem)
                    .padding(Spacing.xs)
                    .drawBehind {
                        val maxOffset = (size.width - selectedPx).coerceAtLeast(0f)
                        val leading = indicatorLeadingOffset.value.coerceIn(0f, maxOffset)
                        val trailing = indicatorTrailingOffset.value.coerceIn(0f, maxOffset)
                        val stretch = (leading - trailing).coerceIn(-maxStretchPx, maxStretchPx)
                        val logicalLeft = leading - stretch.coerceAtLeast(0f)
                        val logicalRight = leading + selectedPx - stretch.coerceAtMost(0f)
                        val pillWidth = logicalRight - logicalLeft
                        val left = if (layoutDirection == LayoutDirection.Rtl) {
                            size.width - logicalRight
                        } else {
                            logicalLeft
                        }
                        val verticalInset = Spacing.xs.toPx() * abs(stretch) / maxStretchPx / 2f
                        val pillHeight = size.height - verticalInset * 2f
                        drawRoundRect(
                            color = indicatorColor,
                            topLeft = Offset(left, verticalInset),
                            size = Size(pillWidth, pillHeight),
                            cornerRadius = CornerRadius(pillHeight / 2f),
                        )
                    },
            ) {
                Row(
                    modifier = Modifier.selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    destinations.forEachIndexed { index, destination ->
                        val selected = selectedFlags[index]
                        val contentColor = if (isWallpaperColor) {
                            if (selected) colors.primary else colors.onSurfaceVariant
                        } else {
                            colors.onPrimaryContainer
                        }
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
                                    val width = (unselectedPx + deltaPx * spatial.value.coerceIn(0f, 1f))
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
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.wrapContentSize(Alignment.Center),
                            ) {
                                Box(
                                    modifier = Modifier.size(Sizes.icon),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = destination.unselectedIcon,
                                        contentDescription = if (selected) null else label,
                                        tint = contentColor,
                                        modifier = Modifier.fillMaxSize().graphicsLayer {
                                            alpha = (1f - effects.value).coerceIn(0f, 1f)
                                        },
                                    )
                                    Icon(
                                        imageVector = destination.selectedIcon,
                                        contentDescription = null,
                                        tint = contentColor,
                                        modifier = Modifier.fillMaxSize().graphicsLayer {
                                            alpha = effects.value.coerceIn(0f, 1f)
                                        },
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .layout { measurable, constraints ->
                                            val placeable = measurable.measure(constraints.copy(minWidth = 0))
                                            val animatedWidth = (placeable.width * spatial.value.coerceIn(0f, 1f)).roundToInt()
                                            layout(animatedWidth, placeable.height) {
                                                placeable.placeRelative(0, 0)
                                            }
                                        }
                                        .clipToBounds()
                                        .graphicsLayer {
                                            alpha = effects.value.coerceIn(0f, 1f)
                                        },
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = Spacing.sm),
                                    ) {
                                        Text(
                                            text = label,
                                            color = contentColor,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Clip,
                                            softWrap = false,
                                            modifier = Modifier.then(if (selected) Modifier else Modifier.clearAndSetSemantics { }),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
