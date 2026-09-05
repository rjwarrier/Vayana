package com.vayana.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.window.core.layout.WindowSizeClass
import com.vayana.feature.library.BookDetailRoute
import com.vayana.feature.library.LibraryRoute
import kotlinx.coroutines.launch

/**
 * On phones, behaves exactly like a plain [LibraryRoute] pushing [BookDetailRoute] onto the
 * back stack. On wide screens, hosts both panes side by side via [ListDetailPaneScaffold] so
 * selecting a book updates the detail pane in place instead of navigating away.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun LibraryListDetailRoute(
    navController: NavHostController,
    onSettingsClick: () -> Unit,
    onContinueReading: (Long) -> Unit,
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val useListDetailPane = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    if (!useListDetailPane) {
        LibraryRoute(
            onBookClick = { bookId -> navController.navigate(BookDetailRoute(bookId)) },
            onSettingsClick = onSettingsClick,
        )
        return
    }

    val navigator = rememberListDetailPaneScaffoldNavigator<Long>()
    val scope = rememberCoroutineScope()
    BackHandler(navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = Modifier.fillMaxSize(),
        listPane = {
            AnimatedPane {
                LibraryRoute(
                    onBookClick = { bookId ->
                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, bookId) }
                    },
                    onSettingsClick = onSettingsClick,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val bookId = navigator.currentDestination?.contentKey
                if (bookId != null) {
                    BookDetailRoute(
                        bookId = bookId,
                        onBack = { scope.launch { navigator.navigateBack() } },
                        onContinueReading = onContinueReading,
                    )
                }
            }
        },
    )
}
