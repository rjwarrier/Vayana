package com.vayana.feature.reader

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.reader.api.ReaderSelection

/** Where the selection is on screen: its top and bottom edges in pixels, in root coordinates. */
internal data class SelectionAnchor(val top: Float, val bottom: Float)

/** The selection's screen position, from its fractions of the reader view and that view's [bounds]; null if unknown. */
internal fun ReaderSelection.anchorIn(bounds: Rect?): SelectionAnchor? {
    val top = top ?: return null
    val bottom = bottom ?: return null
    bounds ?: return null
    return SelectionAnchor(top = bounds.top + top * bounds.height, bottom = bounds.top + bottom * bounds.height)
}

/**
 * The y of a card of [cardHeight] for a selection spanning [anchorTop]..[anchorBottom], within [minY]..[maxY]: just
 * above the selection when it fits there, else just below, and when neither fits on the roomier side's edge. The
 * gap below is larger, to clear the selection handles.
 */
internal fun selectionCardTop(
    anchorTop: Float,
    anchorBottom: Float,
    cardHeight: Int,
    minY: Int,
    maxY: Int,
    gapAbove: Int,
    gapBelow: Int,
): Int {
    val above = anchorTop - gapAbove - cardHeight
    val below = anchorBottom + gapBelow
    val top = when {
        above >= minY -> above.toInt()
        below + cardHeight <= maxY -> below.toInt()
        anchorTop - minY >= maxY - anchorBottom -> minY
        else -> maxY - cardHeight
    }
    return top.coerceIn(minY, maxOf(minY, maxY - cardHeight))
}

/**
 * Places [content] (the selection card) next to the selection: above it, or below it when there is no room above,
 * kept clear of the system bars and [reservedBottomPx] (a panel along the bottom). Without an [anchor] it sits at the
 * bottom edge.
 */
@Composable
internal fun AnchoredToSelection(
    anchor: SelectionAnchor?,
    reservedBottomPx: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var originY by remember { mutableFloatStateOf(0f) }
    val insets = WindowInsets.safeDrawing
    Layout(
        content = content,
        modifier = modifier.onGloballyPositioned { originY = it.positionInRoot().y },
    ) { measurables, constraints ->
        val minY = insets.getTop(this)
        val maxY = (constraints.maxHeight - insets.getBottom(this) - reservedBottomPx).coerceAtLeast(minY)
        val card = measurables.firstOrNull()?.measure(
            Constraints(maxWidth = constraints.maxWidth, maxHeight = maxY - minY),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            card ?: return@layout
            val top = if (anchor == null) {
                maxY - card.height
            } else {
                selectionCardTop(
                    anchorTop = anchor.top - originY,
                    anchorBottom = anchor.bottom - originY,
                    cardHeight = card.height,
                    minY = minY,
                    maxY = maxY,
                    gapAbove = Spacing.md.roundToPx(),
                    gapBelow = Spacing.xxl.roundToPx(),
                )
            }
            card.place(0, top)
        }
    }
}
