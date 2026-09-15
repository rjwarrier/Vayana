package com.vayana.app.navigation

import com.vayana.core.datastore.settings.NavigationMode
import com.vayana.core.designsystem.theme.DisplayProfile

enum class NavigationPresentation {
    Hidden,
    Rail,
    BottomBar,
    FloatingBar,
}

fun resolveNavigationPresentation(
    showNavigation: Boolean,
    useNavigationRail: Boolean,
    displayProfile: DisplayProfile,
    navigationMode: NavigationMode,
): NavigationPresentation = when {
    !showNavigation -> NavigationPresentation.Hidden
    useNavigationRail -> NavigationPresentation.Rail
    displayProfile == DisplayProfile.E_INK -> NavigationPresentation.BottomBar
    navigationMode == NavigationMode.FLOATING_BAR -> NavigationPresentation.FloatingBar
    else -> NavigationPresentation.BottomBar
}
