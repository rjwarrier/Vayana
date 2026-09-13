package com.vayana.core.designsystem.tokens

import androidx.compose.ui.unit.dp

/** Base unit 4dp; scale per design handoff README §"Spacing & Radii". */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

/**
 * Named padding constants for specific surfaces — never an inline `PaddingValues` (§0.2).
 * Chip and sheet values are hand-tuned pixel values straight from the handoff HTML
 * (filter chips: "6px/14px padding"; reader chrome sheet: "16px/8px/20px padding"),
 * not multiples of [Spacing] — kept as their own constants rather than rounded to the scale.
 */
object Paddings {
    val screenHorizontal = Spacing.lg
    val screenVertical = Spacing.lg
    val listRow = Spacing.lg
    val card = Spacing.lg
    val chipVertical = 6.dp
    val chipHorizontal = 14.dp
    val sheetHandleTop = Spacing.lg
    val sheetContentHorizontal = Spacing.sm
    val sheetContentBottom = Spacing.xl
    val page = 20.dp
    val heatmapCellGap = 3.dp
}
