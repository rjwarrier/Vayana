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

    val fab = 56.dp
    val fabLarge = 64.dp
    val menuMinWidth = 232.dp
    val menuItemLargeHeight = 64.dp
    val bottomNavHeight = 80.dp
    val topBarHeight = 56.dp
    val statusBarHeight = 44.dp

    val contentMaxWidth = 600.dp

    /** Book cover aspect ratio, width:height. */
    const val coverAspectRatio = 2f / 3f

    val coverWidthMin = 72.dp
    val coverWidthMax = 160.dp

    val chartHeight = 112.dp
    val chartBarMaxHeight = 72.dp
    val chartBarMinWidth = 28.dp
    val chartBarMaxWidth = 56.dp
}
