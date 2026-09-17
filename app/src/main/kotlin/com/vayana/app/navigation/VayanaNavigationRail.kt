package com.vayana.app.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vayana.core.designsystem.theme.vayanaSpring

/** Tablet/wide-screen counterpart to [VayanaBottomBar] — same destinations, rail layout. */
@Composable
fun VayanaNavigationRail(
    navController: NavHostController,
    showSettings: Boolean = false,
) {
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val settingsSelected = currentDestination?.hasRoute(SettingsRoute::class) == true ||
        currentDestination?.hasRoute(HelpRoute::class) == true ||
        currentDestination?.hasRoute(DiagnosticsRoute::class) == true

    NavigationRail {
        TopLevelDestination.entries.forEach { destination ->
            val selected = !settingsSelected && currentDestination?.isSelectedFor(destination) == true
            NavigationRailItem(
                selected = selected,
                onClick = {
                    navController.navigateToTopLevel(destination.route)
                },
                icon = {
                    TabletRailIcon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        selected = selected,
                        animationLabel = "${destination.name}RailIcon",
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
        if (showSettings) {
            Spacer(modifier = Modifier.weight(1f))
            NavigationRailItem(
                selected = settingsSelected,
                onClick = { navController.navigate(SettingsRoute) },
                icon = {
                    TabletRailIcon(
                        imageVector = if (settingsSelected) Icons.Filled.Settings else Icons.Outlined.Settings,
                        selected = settingsSelected,
                        animationLabel = "SettingsRailIcon",
                    )
                },
                label = { Text(stringResource(com.vayana.core.resources.R.string.settings_title)) },
            )
        }
    }
}

@Composable
private fun TabletRailIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    animationLabel: String,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = vayanaSpring(),
        label = animationLabel,
    )
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    )
}
