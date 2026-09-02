package com.vayana.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vayana.app.navigation.ReaderRoute
import com.vayana.app.navigation.VayanaBottomBar
import com.vayana.app.navigation.VayanaNavHost
import com.vayana.core.designsystem.theme.VayanaTheme

@Composable
fun VayanaAppRoot() {
    val settingsViewModel: AppSettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.settings.collectAsState()

    VayanaTheme(
        themeMode = settings.themeMode,
        displayProfile = settings.displayProfile,
        darkVariant = settings.darkVariant,
        motionSetting = settings.motionSetting,
    ) {
        val navController = rememberNavController()
        val currentDestination = navController.currentBackStackEntryAsState().value?.destination
        val showBottomBar = currentDestination?.hasRoute(ReaderRoute::class) != true

        Scaffold(
            bottomBar = { if (showBottomBar) VayanaBottomBar(navController) },
        ) { innerPadding ->
            VayanaNavHost(
                navController = navController,
                modifier = if (showBottomBar) Modifier.padding(innerPadding) else Modifier,
            )
        }
    }
}
