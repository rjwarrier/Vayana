package com.vayana.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vayana.feature.library.LibraryRoute
import com.vayana.feature.notes.NotesRoute
import com.vayana.feature.reader.ReaderRoute as ReaderScreenRoute
import com.vayana.feature.settings.SettingsRoute as SettingsScreenRoute
import com.vayana.feature.statistics.StatisticsRoute

@Composable
fun VayanaNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelRoute.Library,
        modifier = modifier,
    ) {
        composable<TopLevelRoute.Library> {
            LibraryRoute(
                onBookClick = { bookId -> navController.navigate(ReaderRoute(bookId)) },
                onSettingsClick = { navController.navigate(SettingsRoute) },
            )
        }
        composable<TopLevelRoute.Notes> { NotesRoute() }
        composable<TopLevelRoute.Statistics> { StatisticsRoute() }
        composable<SettingsRoute> {
            SettingsScreenRoute(onBack = { navController.popBackStack() })
        }
        composable<ReaderRoute> {
            ReaderScreenRoute(onBack = { navController.popBackStack() })
        }
    }
}
