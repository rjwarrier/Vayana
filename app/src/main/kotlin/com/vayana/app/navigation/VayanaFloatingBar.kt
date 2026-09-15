package com.vayana.app.navigation

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VayanaFloatingBar(navController: NavHostController, modifier: Modifier = Modifier) {
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val colors = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme

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
                    val spatialProgress by animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = motionScheme.fastSpatialSpec(),
                        label = "floatingNavSpatialProgress",
                    )
                    val effectsProgress by animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = motionScheme.fastEffectsSpec(),
                        label = "floatingNavEffectsProgress",
                    )
                    val itemWidth = Sizes.floatingNavUnselectedItem +
                        (Sizes.floatingNavSelectedItem - Sizes.floatingNavUnselectedItem) * spatialProgress
                    val label = stringResource(destination.labelRes)

                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .height(Sizes.floatingNavItem - (Spacing.xs * 2))
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.16f * effectsProgress.coerceIn(0f, 1f)))
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
                                    alpha = ((effectsProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                                }
                                .then(if (selected) Modifier else Modifier.clearAndSetSemantics { }),
                        )
                    }
                }
            }
        }
    }
}
