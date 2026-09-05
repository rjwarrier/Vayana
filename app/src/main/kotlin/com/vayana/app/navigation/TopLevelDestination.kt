package com.vayana.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.ui.graphics.vector.ImageVector
import com.vayana.core.resources.R
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

/**
 * Bottom nav is 3 tabs (Library / Notes / Statistics), matching every bottom-nav mock in
 * `Design/design_handoff_vayana/` — see docs/DECISIONS.md ("Bottom nav is 3 tabs, not 4").
 * Search and Settings are reached from within a screen, not from the bar.
 */
@Serializable
sealed interface TopLevelRoute {
    @Serializable data object Library : TopLevelRoute
    @Serializable data object Notes : TopLevelRoute
    @Serializable data object Statistics : TopLevelRoute
}

@Serializable
data object SettingsRoute

@Serializable
data object RecentlyDeletedRoute

@Serializable
data object VocabularyReviewRoute

@Serializable
data object ShelvesRoute

@Serializable
data class ShelfDetailRoute(val shelfId: Long)

@Serializable
data class BookDetailRoute(val bookId: Long)

enum class TopLevelDestination(
    val route: TopLevelRoute,
    val routeClass: KClass<out TopLevelRoute>,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    LIBRARY(
        route = TopLevelRoute.Library,
        routeClass = TopLevelRoute.Library::class,
        selectedIcon = Icons.Filled.AutoStories,
        unselectedIcon = Icons.Outlined.AutoStories,
        labelRes = R.string.nav_library,
    ),
    NOTES(
        route = TopLevelRoute.Notes,
        routeClass = TopLevelRoute.Notes::class,
        selectedIcon = Icons.Filled.EditNote,
        unselectedIcon = Icons.Outlined.EditNote,
        labelRes = R.string.nav_notes,
    ),
    STATISTICS(
        route = TopLevelRoute.Statistics,
        routeClass = TopLevelRoute.Statistics::class,
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
        labelRes = R.string.nav_statistics,
    ),
}
