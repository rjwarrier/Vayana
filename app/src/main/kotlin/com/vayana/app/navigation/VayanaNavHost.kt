package com.vayana.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
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
                onBookClick = { bookId -> navController.navigate(BookDetailRoute(bookId)) },
                onSettingsClick = { navController.navigate(SettingsRoute) },
            )
        }
        composable<TopLevelRoute.Notes> { NotesRoute() }
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
