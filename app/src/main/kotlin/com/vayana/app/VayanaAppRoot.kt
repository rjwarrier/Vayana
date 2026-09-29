package com.vayana.app

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.window.core.layout.WindowSizeClass
import com.vayana.app.navigation.GutenbergRoute
import com.vayana.app.navigation.NavigationPresentation
import com.vayana.app.navigation.ReaderRoute
import com.vayana.app.navigation.SearchRoute
import com.vayana.app.navigation.TopLevelRoute
import com.vayana.app.navigation.VayanaBottomBar
import com.vayana.app.navigation.VayanaFloatingBar
import com.vayana.app.navigation.VayanaNavHost
import com.vayana.app.navigation.VayanaNavigationRail
import com.vayana.app.navigation.navigateToTopLevel
import com.vayana.app.navigation.resolveNavigationPresentation
import com.vayana.app.widget.ShortcutDestination
import com.vayana.core.datastore.settings.StartScreen
import com.vayana.core.designsystem.theme.VayanaTheme
import com.vayana.core.designsystem.theme.LocalFloatingNavigationInset
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.feature.onboarding.OnboardingRoute

@Composable
fun VayanaAppRoot() {
    val settingsViewModel: AppSettingsViewModel = hiltViewModel()
    // Null until DataStore's first read: drawing defaults first would flash onboarding and the wrong theme.
    val settings = settingsViewModel.settings.collectAsStateWithLifecycle().value ?: return

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
        // A book opened or shared from another app is imported by the library, so show it.
        val hasIncomingBooks by settingsViewModel.hasIncomingBooks.collectAsStateWithLifecycle()
        LaunchedEffect(hasIncomingBooks) {
            if (hasIncomingBooks) navController.navigateToTopLevel(TopLevelRoute.Library)
        }
        // The home-screen widget's book, straight into the reader - and read aloud, from its play button.
        val openBookRequest by settingsViewModel.openBookRequest.collectAsStateWithLifecycle()
        LaunchedEffect(openBookRequest) {
            val request = openBookRequest ?: return@LaunchedEffect
            val top = navController.currentBackStackEntry
            val alreadyOpen = top?.destination?.hasRoute(ReaderRoute::class) == true && top.toRoute<ReaderRoute>().bookId == request.bookId
            if (alreadyOpen) {
                if (request.readAloud) settingsViewModel.requestReadAloud(request.bookId)
            } else {
                navController.navigate(ReaderRoute(request.bookId, readAloud = request.readAloud)) { launchSingleTop = true }
            }
            settingsViewModel.consumeOpenBookRequest(request)
        }
        // A launcher shortcut's screen, over the library it belongs to.
        val shortcutRequest by settingsViewModel.shortcutRequest.collectAsStateWithLifecycle()
        LaunchedEffect(shortcutRequest) {
            val destination = shortcutRequest ?: return@LaunchedEffect
            navController.navigateToTopLevel(TopLevelRoute.Library)
            when (destination) {
                ShortcutDestination.FREE_BOOKS -> navController.navigate(GutenbergRoute) { launchSingleTop = true }
                ShortcutDestination.SEARCH -> navController.navigate(SearchRoute) { launchSingleTop = true }
            }
            settingsViewModel.consumeShortcutRequest(destination)
        }
        val currentDestination = navController.currentBackStackEntryAsState().value?.destination
        val showNavigation = currentDestination?.hasRoute(ReaderRoute::class) != true

        val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
        val useNavigationRail = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
        val showSettingsInRail = useNavigationRail &&
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val navigationPresentation = resolveNavigationPresentation(
            showNavigation = showNavigation,
            useNavigationRail = useNavigationRail,
            displayProfile = settings.displayProfile,
            navigationMode = settings.navigationMode,
        )

        if (useNavigationRail) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (navigationPresentation == NavigationPresentation.Rail) {
                        VayanaNavigationRail(
                            navController = navController,
                            showSettings = showSettingsInRail,
                        )
                    }
                    VayanaNavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = if (navigationPresentation == NavigationPresentation.Rail) {
                            Modifier.fillMaxSize().safeDrawingPadding()
                        } else {
                            Modifier.fillMaxSize()
                        },
                    )
                }
            }
        } else {
            Scaffold(
                bottomBar = {
                    if (navigationPresentation == NavigationPresentation.BottomBar) {
                        VayanaBottomBar(
                            navController = navController,
                            onBooksLongPress = settingsViewModel::toggleNavigationMode,
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.background,
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(
                        LocalFloatingNavigationInset provides if (navigationPresentation == NavigationPresentation.FloatingBar) {
                            Sizes.floatingNavItem + Spacing.xxl
                        } else {
                            0.dp
                        },
                    ) {
                        VayanaNavHost(
                            navController = navController,
                            startDestination = startDestination,
                            modifier = when (navigationPresentation) {
                                NavigationPresentation.FloatingBar ->
                                    Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())
                                NavigationPresentation.BottomBar ->
                                    Modifier.fillMaxSize().padding(innerPadding)
                                NavigationPresentation.Hidden,
                                NavigationPresentation.Rail -> Modifier.fillMaxSize()
                            },
                        )
                    }
                    if (navigationPresentation == NavigationPresentation.FloatingBar) {
                        VayanaFloatingBar(
                            navController = navController,
                            onBooksLongPress = settingsViewModel::toggleNavigationMode,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
    }
}
