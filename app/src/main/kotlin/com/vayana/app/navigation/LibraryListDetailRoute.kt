package com.vayana.app.navigation

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.window.core.layout.WindowSizeClass
import com.vayana.feature.library.BookDetailRoute
import com.vayana.feature.library.ProvideBookSharedTransitionScopes
import com.vayana.feature.library.LibraryAddAction
import com.vayana.feature.library.LibraryRoute
import com.vayana.feature.library.LibraryViewModel
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.theme.vayanaContentTransform
import com.vayana.core.resources.R
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
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onRecentlyDeletedClick: () -> Unit,
    onShelvesClick: () -> Unit,
    onOfflineBooksClick: () -> Unit,
    onFreeBooksClick: () -> Unit,
    onReviewWords: () -> Unit,
    onReviewHighlights: () -> Unit,
    onContinueReading: (Long, String?) -> Unit,
    addBookAction: LibraryAddAction?,
    onAddBookActionHandled: () -> Unit,
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val landscapeTwoColumnLayout by viewModel.landscapeTwoColumnLayout.collectAsStateWithLifecycle()
    val libraryBooks by viewModel.libraryBooks.collectAsStateWithLifecycle()
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTabletLandscape = isLandscape &&
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val useListDetailPane = landscapeTwoColumnLayout && isTabletLandscape

    if (!useListDetailPane) {
        ProvideBookSharedTransitionScopes(sharedTransitionScope, animatedVisibilityScope) {
            LibraryRoute(
                onBookClick = { bookId, source ->
                    navController.navigate(BookDetailRoute(bookId, source.name))
                },
                onSeriesFolderClick = { seriesKey -> navController.navigate(SeriesFolderRoute(seriesKey)) },
                onSettingsClick = onSettingsClick,
                showSettingsAction = !isTabletLandscape,
                onSearchClick = onSearchClick,
                onRecentlyDeletedClick = onRecentlyDeletedClick,
                onShelvesClick = onShelvesClick,
                onOfflineBooksClick = onOfflineBooksClick,
                onFreeBooksClick = onFreeBooksClick,
                onContinueReading = onContinueReading,
                onReviewWords = onReviewWords,
                onReviewHighlights = onReviewHighlights,
                addBookAction = addBookAction,
                onAddBookActionHandled = onAddBookActionHandled,
            )
        }
        return
    }

    val navigator = rememberListDetailPaneScaffoldNavigator<Long>()
    val scope = rememberCoroutineScope()
    BackHandler(navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    // The wide detail pane would otherwise sit empty on arrival: open the book read last, once per visit.
    var openedInitialBook by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(libraryBooks.isNotEmpty()) {
        if (openedInitialBook || libraryBooks.isEmpty()) return@LaunchedEffect
        openedInitialBook = true
        val lastRead = libraryBooks.filter { it.lastReadAt != null }.maxByOrNull { it.lastReadAt ?: 0L } ?: return@LaunchedEffect
        if (navigator.currentDestination?.contentKey == null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, lastRead.id)
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = Modifier.fillMaxSize(),
        listPane = {
            // The default 360dp list pane crowds the toolbar and leaves a 3-column grid on a large tablet.
            AnimatedPane(modifier = Modifier.preferredWidth(LibraryListPaneWidth)) {
                LibraryRoute(
                    onBookClick = { bookId, _ ->
                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, bookId) }
                    },
                    onSeriesFolderClick = { seriesKey -> navController.navigate(SeriesFolderRoute(seriesKey)) },
                    onSettingsClick = onSettingsClick,
                    showSettingsAction = false,
                    selectedBookId = navigator.currentDestination?.contentKey,
                    onSearchClick = onSearchClick,
                    onRecentlyDeletedClick = onRecentlyDeletedClick,
                    onShelvesClick = onShelvesClick,
                    onOfflineBooksClick = onOfflineBooksClick,
                    onFreeBooksClick = onFreeBooksClick,
                onContinueReading = onContinueReading,
                onReviewWords = onReviewWords,
                onReviewHighlights = onReviewHighlights,
                    addBookAction = addBookAction,
                    onAddBookActionHandled = onAddBookActionHandled,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val bookId = navigator.currentDestination?.contentKey
                val detailContent = when {
                    bookId != null -> LibraryDetailContent.Book(bookId)
                    libraryBooks.isNotEmpty() -> LibraryDetailContent.Placeholder
                    else -> LibraryDetailContent.Empty
                }
                AnimatedContent(
                    targetState = detailContent,
                    transitionSpec = vayanaContentTransform(),
                    contentKey = { content -> content.key },
                    modifier = Modifier.fillMaxSize(),
                    label = "TabletLibraryDetail",
                ) { content ->
                    when (content) {
                        is LibraryDetailContent.Book -> BookDetailRoute(
                            bookId = content.id,
                            onBack = { scope.launch { navigator.navigateBack() } },
                            onContinueReading = onContinueReading,
                            onOpenNotes = { bookId -> navController.navigate(BookNotesRoute(bookId)) },
                            onReadFromStart = { bookId -> navController.navigate(ReaderRoute(bookId = bookId, fromStart = true)) },
                            useWideActions = true,
                        )
                        LibraryDetailContent.Placeholder -> LibraryDetailPlaceholder()
                        LibraryDetailContent.Empty -> Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        },
    )
}

private sealed interface LibraryDetailContent {
    val key: Any

    data class Book(val id: Long) : LibraryDetailContent {
        override val key: Any = id
    }

    data object Placeholder : LibraryDetailContent {
        override val key: Any = "placeholder"
    }

    data object Empty : LibraryDetailContent {
        override val key: Any = "empty"
    }
}

@Composable
private fun LibraryDetailPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(Sizes.badgeLarge),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.library_detail_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.library_detail_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val LibraryListPaneWidth = 520.dp
