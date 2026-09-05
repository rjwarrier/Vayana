package com.vayana.app.navigation

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.window.core.layout.WindowSizeClass
import com.vayana.feature.library.BookDetailRoute
import com.vayana.feature.library.LibraryRoute
import com.vayana.feature.library.LibraryViewModel
import kotlinx.coroutines.launch

/**
 * On phones, behaves exactly like a plain [LibraryRoute] pushing [BookDetailRoute] onto the
 * back stack. On wide screens in landscape, hosts both panes side by side via
 * [ListDetailPaneScaffold] so selecting a book updates the detail pane in place instead of
 * navigating away. Portrait always stays single-pane, by request.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun LibraryListDetailRoute(
    navController: NavHostController,
    onSettingsClick: () -> Unit,
    onRecentlyDeletedClick: () -> Unit,
    onShelvesClick: () -> Unit,
    onContinueReading: (Long) -> Unit,
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val landscapeTwoColumnLayout by viewModel.landscapeTwoColumnLayout.collectAsState()
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useListDetailPane = landscapeTwoColumnLayout && isLandscape &&
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    if (!useListDetailPane) {
        LibraryRoute(
            onBookClick = { bookId -> navController.navigate(BookDetailRoute(bookId)) },
            onSettingsClick = onSettingsClick,
            onRecentlyDeletedClick = onRecentlyDeletedClick,
            onShelvesClick = onShelvesClick,
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
                    onRecentlyDeletedClick = onRecentlyDeletedClick,
                    onShelvesClick = onShelvesClick,
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
