package com.vayana.app.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/** Iterates the [TopLevelDestination] registry — no `when` ladder over routes (§0.4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VayanaBottomBar(
    navController: NavHostController,
    onBooksLongPress: () -> Unit,
) {
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination

    NavigationBar {
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentDestination?.isSelectedFor(destination) == true
                val interactionSource = remember { MutableInteractionSource() }
                if (destination == TopLevelDestination.LIBRARY) {
                    NavigationLongPressEffect(interactionSource, onBooksLongPress)
                }
                NavigationBarItem(
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
                    interactionSource = interactionSource,
                )
            }
        }
    }
}

internal fun NavHostController.navigateToTopLevel(route: TopLevelRoute) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
