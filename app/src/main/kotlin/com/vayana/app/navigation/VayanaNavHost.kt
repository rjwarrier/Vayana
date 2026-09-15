package com.vayana.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
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
import com.vayana.core.designsystem.theme.vayanaNavTabEnter
import com.vayana.core.designsystem.theme.vayanaNavTabExit
import com.vayana.feature.library.RecentlyDeletedRoute as RecentlyDeletedScreenRoute
import com.vayana.feature.library.ShelfDetailRoute as ShelfDetailScreenRoute
import com.vayana.feature.library.ShelvesRoute as ShelvesScreenRoute
import com.vayana.feature.notes.NotesRoute
import com.vayana.feature.help.HelpRoute as HelpScreenRoute
import com.vayana.feature.reader.ReaderRoute as ReaderScreenRoute
import com.vayana.feature.search.SearchRoute as SearchScreenRoute
import com.vayana.feature.settings.DiagnosticsRoute as DiagnosticsScreenRoute
import com.vayana.feature.settings.SettingsRoute as SettingsScreenRoute
import com.vayana.feature.statistics.HighlightReviewRoute as HighlightReviewScreenRoute
import com.vayana.feature.statistics.LearnWordsRoute as LearnWordsScreenRoute
import com.vayana.feature.statistics.StatisticsRoute
import com.vayana.feature.statistics.VocabularyReviewRoute as VocabularyReviewScreenRoute

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VayanaNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: TopLevelRoute = TopLevelRoute.Library,
) {
    val displayProfile = LocalDisplayProfile.current
    val motionSetting = LocalMotionSetting.current
    val motionScheme = MaterialTheme.motionScheme

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            if (initialState.destination.isTopLevelTab() && targetState.destination.isTopLevelTab()) {
                vayanaNavTabEnter(displayProfile, motionSetting, motionScheme, initialState.destination.tabDirectionTo(targetState.destination))
            } else {
                vayanaNavEnter(displayProfile, motionSetting, motionScheme)
            }
        },
        exitTransition = {
            if (initialState.destination.isTopLevelTab() && targetState.destination.isTopLevelTab()) {
                vayanaNavTabExit(displayProfile, motionSetting, motionScheme, initialState.destination.tabDirectionTo(targetState.destination))
            } else {
                vayanaNavExit(displayProfile, motionSetting, motionScheme)
            }
        },
        popEnterTransition = {
            if (initialState.destination.isTopLevelTab() && targetState.destination.isTopLevelTab()) {
                vayanaNavTabEnter(displayProfile, motionSetting, motionScheme, initialState.destination.tabDirectionTo(targetState.destination))
            } else {
                vayanaNavPopEnter(displayProfile, motionSetting, motionScheme)
            }
        },
        popExitTransition = {
            if (initialState.destination.isTopLevelTab() && targetState.destination.isTopLevelTab()) {
                vayanaNavTabExit(displayProfile, motionSetting, motionScheme, initialState.destination.tabDirectionTo(targetState.destination))
            } else {
                vayanaNavPopExit(displayProfile, motionSetting, motionScheme)
            }
        },
    ) {
        composable<TopLevelRoute.Library> {
            LibraryListDetailRoute(
                navController = navController,
                onSettingsClick = { navController.navigate(SettingsRoute) },
                onSearchClick = { navController.navigate(SearchRoute) },
                onRecentlyDeletedClick = { navController.navigate(RecentlyDeletedRoute) },
                onShelvesClick = { navController.navigate(ShelvesRoute) },
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
        composable<TopLevelRoute.Statistics> {
            StatisticsRoute(
                onReviewVocabulary = { navController.navigate(VocabularyReviewRoute) },
                onOpenLearnWords = { navController.navigate(LearnWordsRoute) },
                onReviewHighlights = { navController.navigate(HighlightReviewRoute) },
            )
        }
        composable<VocabularyReviewRoute> {
            VocabularyReviewScreenRoute(onBack = { navController.popBackStack() })
        }
        composable<HighlightReviewRoute> {
            HighlightReviewScreenRoute(
                onBack = { navController.popBackStack() },
                onOpenReader = { bookId, locator -> navController.navigate(ReaderRoute(bookId = bookId, targetLocator = locator)) },
            )
        }
        composable<LearnWordsRoute> {
            LearnWordsScreenRoute(onBack = { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreenRoute(
                onBack = { navController.popBackStack() },
                onHelpClick = { navController.navigate(HelpRoute) },
                onDiagnosticsClick = { navController.navigate(DiagnosticsRoute) },
            )
        }
        composable<HelpRoute> {
            HelpScreenRoute(onBack = { navController.popBackStack() })
        }
        composable<DiagnosticsRoute> {
            DiagnosticsScreenRoute(onBack = { navController.popBackStack() })
        }
        composable<SearchRoute> {
            SearchScreenRoute(
                onBack = { navController.popBackStack() },
                onOpenBook = { bookId -> navController.navigate(BookDetailRoute(bookId)) },
                onOpenReader = { bookId, locator -> navController.navigate(ReaderRoute(bookId = bookId, targetLocator = locator)) },
            )
        }
        composable<RecentlyDeletedRoute> {
            RecentlyDeletedScreenRoute(onBack = { navController.popBackStack() })
        }
        composable<ShelvesRoute> {
            ShelvesScreenRoute(
                onBack = { navController.popBackStack() },
                onShelfClick = { shelfId -> navController.navigate(ShelfDetailRoute(shelfId)) },
                onBookClick = { bookId -> navController.navigate(BookDetailRoute(bookId)) },
            )
        }
        composable<ShelfDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ShelfDetailRoute>()
            ShelfDetailScreenRoute(
                shelfId = route.shelfId,
                onBack = { navController.popBackStack() },
                onBookClick = { bookId -> navController.navigate(BookDetailRoute(bookId)) },
            )
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
            ReaderScreenRoute(
                onBack = { navController.popBackStack() },
                onReviewVocabulary = { navController.navigate(VocabularyReviewRoute) },
            )
        }
    }
}

private fun NavDestination.isTopLevelTab(): Boolean =
    TopLevelDestination.entries.any { destination -> hasRoute(destination.routeClass) }

private fun NavDestination.tabDirectionTo(target: NavDestination): Int {
    val initialIndex = TopLevelDestination.entries.indexOfFirst { hasRoute(it.routeClass) }
    val targetIndex = TopLevelDestination.entries.indexOfFirst { target.hasRoute(it.routeClass) }
    return if (targetIndex >= initialIndex) 1 else -1
}
