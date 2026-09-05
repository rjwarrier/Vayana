package com.vayana.app.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/** Tablet/wide-screen counterpart to [VayanaBottomBar] — same destinations, rail layout. */
@Composable
fun VayanaNavigationRail(navController: NavHostController) {
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination

    NavigationRail {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination?.hasRoute(destination.routeClass) == true
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
    }
}
