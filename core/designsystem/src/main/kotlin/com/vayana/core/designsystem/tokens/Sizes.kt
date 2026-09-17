package com.vayana.core.designsystem.tokens

import androidx.compose.ui.unit.dp

object Sizes {
    val swatchSmall = 12.dp
    val iconSmall = 18.dp
    val icon = 24.dp
    val iconLarge = 32.dp

    /** Standard Android touch target; E-Ink profile bumps this to [touchTargetEink] (§6). */
    val touchTarget = 48.dp
    val touchTargetEink = 56.dp

    val fab = 64.dp
    val floatingNavFab = 72.dp
    val fabLarge = 76.dp
    val menuMinWidth = 232.dp
    val menuItemLargeHeight = 64.dp
    val bottomNavHeight = 80.dp
    val floatingNavItem = 72.dp
    val floatingNavUnselectedItem = 64.dp
    val floatingNavSelectedItem = 132.dp
    val floatingNavFabAlignmentBreakpoint = 400.dp
    val topBarHeight = 56.dp
    val statusBarHeight = 44.dp

    val contentMaxWidth = 600.dp

    /**
     * A tablet list pane can be compact even when the full window is wide. Below this width,
     * secondary library toolbar actions move into the overflow menu so the title row remains
     * usable in a list-detail layout.
     */
    val libraryToolbarExpandedActionsBreakpoint = 560.dp

    /** Book cover aspect ratio, width:height. */
    const val coverAspectRatio = 2f / 3f

    val coverWidthMin = 72.dp
    val libraryGridCoverWidthMin = 96.dp
    val coverWidthMax = 160.dp

    /** Cover beside the title block at the top of book details - leaves the text column room to breathe. */
    val coverWidthDetail = 120.dp

    val chartHeight = 112.dp
    val chartBarMaxHeight = 72.dp
    val chartBarMinWidth = 28.dp
    val chartBarMaxWidth = 56.dp

    val shareCardWidth = 320.dp
    val shareCardCoverWidth = 96.dp
    val shareCardSpotlightCoverWidth = 60.dp
    val shareCardProgressBarHeight = 6.dp
    val shareCardQuoteMarkHeight = 40.dp
    val shareCardAccentBarWidth = 2.dp

    val iconMedium = 22.dp
    val badge = 42.dp
    val badgeLarge = 64.dp
    val chipMinWidth = 132.dp
    val syncDot = 8.dp

    /** Larger on E-Ink: the sync dot's ring and centre need the extra pixels to read apart. */
    val syncDotEink = 11.dp
    val heatmapCell = 11.dp
    val heatmapMonthLabelHeight = 16.dp
    val settingsContentMaxWidth = 840.dp

    /** Width at which Settings lays its categories out in two columns. */
    val twoColumnBreakpoint = 680.dp

    /** Below this width, side-by-side choice buttons stack vertically. */
    val compactChoiceBreakpoint = 360.dp

    /** Exported share-image side length in pixels — square, high enough res for social posts. */
    const val shareCardExportPx = 1600
}
