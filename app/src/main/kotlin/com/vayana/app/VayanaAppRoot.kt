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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import com.vayana.app.navigation.ReaderRoute
import com.vayana.app.navigation.TopLevelRoute
import com.vayana.core.datastore.settings.StartScreen
import com.vayana.app.navigation.VayanaBottomBar
import com.vayana.app.navigation.VayanaNavHost
import com.vayana.app.navigation.VayanaNavigationRail
import com.vayana.core.designsystem.theme.VayanaTheme
import com.vayana.feature.onboarding.OnboardingRoute

@Composable
fun VayanaAppRoot() {
    val settingsViewModel: AppSettingsViewModel = hiltViewModel()
    // Null until DataStore's first read: drawing defaults first would flash onboarding and the wrong theme.
    val settings = settingsViewModel.settings.collectAsState().value ?: return

    VayanaTheme(
        themeMode = settings.themeMode,
        displayProfile = settings.displayProfile,
        darkVariant = settings.darkVariant,
        motionSetting = settings.motionSetting,
        dynamicColor = settings.dynamicColor,
        dateFormatStyle = settings.dateFormatStyle,
    ) {
        if (!settings.onboardingCompleted) {
            OnboardingRoute(modifier = Modifier.fillMaxSize())
            return@VayanaTheme
        }

        val navController = rememberNavController()
        // Read once per launch: changing "Open on" mid-session must not rebuild the nav graph.
        val startScreen = rememberSaveable { settings.startScreen }
        val startDestination = when (startScreen) {
            StartScreen.NOTES -> TopLevelRoute.Notes
            StartScreen.STATISTICS -> TopLevelRoute.Statistics
            StartScreen.LIBRARY, StartScreen.LAST_BOOK -> TopLevelRoute.Library
        }
        var lastBookOpened by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(startScreen) {
            if (startScreen != StartScreen.LAST_BOOK || lastBookOpened) return@LaunchedEffect
            lastBookOpened = true
            settingsViewModel.lastReadBookId()?.let { bookId -> navController.navigate(ReaderRoute(bookId)) }
        }
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
                        startDestination = startDestination,
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
                    startDestination = startDestination,
                    modifier = if (showNavigation) Modifier.padding(innerPadding) else Modifier,
                )
            }
        }
    }
}
