package com.vayana.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.vayana.core.designsystem.theme.LocalDisplayProfile
import com.vayana.core.designsystem.theme.LocalMotionSetting
import com.vayana.core.designsystem.theme.vayanaNavEnter
import com.vayana.core.designsystem.theme.vayanaNavExit
import com.vayana.core.designsystem.theme.vayanaNavPopEnter
import com.vayana.core.designsystem.theme.vayanaNavPopExit
import com.vayana.feature.notes.NotesRoute
import com.vayana.feature.reader.ReaderRoute as ReaderScreenRoute
import com.vayana.feature.settings.SettingsRoute as SettingsScreenRoute
import com.vayana.feature.statistics.StatisticsRoute

@Composable
fun VayanaNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val displayProfile = LocalDisplayProfile.current
    val motionSetting = LocalMotionSetting.current

    NavHost(
        navController = navController,
        startDestination = TopLevelRoute.Library,
        modifier = modifier,
        enterTransition = { vayanaNavEnter(displayProfile, motionSetting) },
        exitTransition = { vayanaNavExit(displayProfile, motionSetting) },
        popEnterTransition = { vayanaNavPopEnter(displayProfile, motionSetting) },
        popExitTransition = { vayanaNavPopExit(displayProfile, motionSetting) },
    ) {
        composable<TopLevelRoute.Library> {
            LibraryListDetailRoute(
                navController = navController,
                onSettingsClick = { navController.navigate(SettingsRoute) },
                onContinueReading = { bookId -> navController.navigate(ReaderRoute(bookId)) },
            )
        }
        composable<TopLevelRoute.Notes> {
            NotesRoute(
                onOpenReader = { bookId, locator ->
                    navController.navigate(ReaderRoute(bookId = bookId, targetLocator = locator))
                },
            )
        }
        composable<TopLevelRoute.Statistics> { StatisticsRoute() }
        composable<SettingsRoute> {
            SettingsScreenRoute(onBack = { navController.popBackStack() })
        }
        composable<BookDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<BookDetailRoute>()
            com.vayana.feature.library.BookDetailRoute(
                bookId = route.bookId,
                onBack = { navController.popBackStack() },
                onContinueReading = { bookId -> navController.navigate(ReaderRoute(bookId)) },
            )
        }
        composable<ReaderRoute> {
            ReaderScreenRoute(onBack = { navController.popBackStack() })
        }
    }
}
