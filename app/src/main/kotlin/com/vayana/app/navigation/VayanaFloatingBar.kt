package com.vayana.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VayanaFloatingBar(navController: NavHostController, modifier: Modifier = Modifier) {
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.floatingNavHostHeight)
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
        ) {
            VayanaFloatingBarItems(
                selected = { destination -> currentDestination?.hasRoute(destination.routeClass) == true },
                onDestinationClick = { destination -> navController.navigateToTopLevel(destination.route) },
            )
        }
    }
}

@Composable
private fun RowScope.VayanaFloatingBarItems(
    selected: (TopLevelDestination) -> Boolean,
    onDestinationClick: (TopLevelDestination) -> Unit,
) {
    TopLevelDestination.entries.forEach { destination ->
        val isSelected = selected(destination)
        NavigationBarItem(
            selected = isSelected,
            onClick = { onDestinationClick(destination) },
            icon = {
                Icon(
                    imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                    contentDescription = null,
                )
            },
            label = { Text(stringResource(destination.labelRes)) },
        )
    }
}
