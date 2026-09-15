package com.vayana.app.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.theme.LocalMotionSetting
import com.vayana.core.designsystem.theme.MotionSetting

@Composable
fun VayanaFloatingBar(navController: NavHostController, modifier: Modifier = Modifier) {
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val colors = MaterialTheme.colorScheme
    val motionSetting = LocalMotionSetting.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
    ) {
        val alignBesideFab = currentDestination?.hasRoute(TopLevelRoute.Library::class) == true &&
            maxWidth < Sizes.floatingNavFabAlignmentBreakpoint
        Surface(
            modifier = Modifier.align(if (alignBesideFab) Alignment.CenterStart else Alignment.Center),
            shape = CircleShape,
            color = colors.primaryContainer,
            shadowElevation = Elevations.shadowLarge,
        ) {
            Row(
                modifier = Modifier
                    .height(Sizes.floatingNavItem)
                    .padding(Spacing.xs),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hasRoute(destination.routeClass) == true
                    val progress by animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = when (motionSetting) {
                            MotionSetting.OFF -> snap()
                            MotionSetting.REDUCED -> spring(
                                stiffness = Spring.StiffnessMedium,
                                dampingRatio = Spring.DampingRatioNoBouncy,
                            )
                            MotionSetting.FULL -> spring(
                                stiffness = Spring.StiffnessLow,
                                dampingRatio = Spring.DampingRatioNoBouncy,
                            )
                        },
                        label = "floatingNavSelectionProgress",
                    )
                    val itemWidth = Sizes.floatingNavUnselectedItem +
                        (Sizes.floatingNavSelectedItem - Sizes.floatingNavUnselectedItem) * progress
                    val label = stringResource(destination.labelRes)

                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .height(Sizes.floatingNavItem - (Spacing.xs * 2))
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.16f * progress))
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { navController.navigateToTopLevel(destination.route) },
                            ),
                    ) {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = if (selected) null else label,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = Spacing.lg)
                                .size(Sizes.icon),
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
                                .graphicsLayer {
                                    alpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                                }
                                .then(if (selected) Modifier else Modifier.clearAndSetSemantics { }),
                        )
                    }
                }
            }
        }
    }
}
