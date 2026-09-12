package com.vayana.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import com.vayana.app.navigation.ReaderRoute
import com.vayana.app.navigation.VayanaBottomBar
import com.vayana.app.navigation.VayanaNavHost
import com.vayana.app.navigation.VayanaNavigationRail
import com.vayana.core.designsystem.theme.VayanaTheme
import com.vayana.feature.onboarding.OnboardingRoute

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
        if (!settings.onboardingCompleted) {
            OnboardingRoute(modifier = Modifier.fillMaxSize())
            return@VayanaTheme
        }

        val navController = rememberNavController()
        val currentDestination = navController.currentBackStackEntryAsState().value?.destination
        val showNavigation = currentDestination?.hasRoute(ReaderRoute::class) != true

        val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
        val useNavigationRail = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

        if (useNavigationRail) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (showNavigation) VayanaNavigationRail(navController)
                    VayanaNavHost(
                        navController = navController,
                        modifier = if (showNavigation) {
                            Modifier.fillMaxSize().safeDrawingPadding()
                        } else {
                            Modifier.fillMaxSize()
                        },
                    )
                }
            }
        } else {
            Scaffold(
                bottomBar = { if (showNavigation) VayanaBottomBar(navController) },
                containerColor = MaterialTheme.colorScheme.background,
            ) { innerPadding ->
                VayanaNavHost(
                    navController = navController,
                    modifier = if (showNavigation) Modifier.padding(innerPadding) else Modifier,
                )
            }
        }
    }
}
