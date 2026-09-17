package com.vayana.app.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState

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
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
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
                    Icon(
                        imageVector = if (settingsSelected) Icons.Filled.Settings else Icons.Outlined.Settings,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(com.vayana.core.resources.R.string.settings_title)) },
            )
        }
    }
}
