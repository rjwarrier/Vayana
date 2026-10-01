package com.vayana.core.designsystem.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.vayana.core.designsystem.tokens.Opacities
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.tokens.Strokes
import com.vayana.core.resources.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Sends the hardware page keys to the list on screen. The activity offers every page key here first; a key is only
 * taken while a list has registered, so the reader (which has no such list) still gets its own page keys.
 * Main thread only.
 */
object EinkPageKeys {
    private val handlers = ArrayList<(PageKeyDirection) -> Boolean>()

    val hasHandler: Boolean get() = handlers.isNotEmpty()

    fun register(handler: (PageKeyDirection) -> Boolean): () -> Unit {
        handlers += handler
        return { handlers.remove(handler) }
    }

    /** The list that registered last, the one on top, pages by one screen. False when it has nowhere left to go. */
    fun dispatch(direction: PageKeyDirection): Boolean = handlers.lastOrNull()?.invoke(direction) ?: false
}

/**
 * A [LazyColumn] that, on an E-Ink display, also pages: two buttons in the corner and the hardware page keys move it by
 * one screen at a time. Scrolling by finger is a stream of partial refreshes there; a page is a single one. Any other
 * display gets the plain list.
 */
@Composable
fun PagedLazyColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    showPageButtons: Boolean = true,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val pager = rememberEinkPager(state) { state.viewportHeightPx() }
    Box(modifier) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            content = content,
        )
        if (showPageButtons) pager?.let { EinkPageControls(it, Modifier.align(Alignment.BottomEnd)) }
    }
}

/** The grid counterpart of [PagedLazyColumn]. */
@Composable
fun PagedLazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    showPageButtons: Boolean = true,
    content: LazyGridScope.() -> Unit,
) {
    val state = rememberLazyGridState()
    val pager = rememberEinkPager(state) { state.viewportHeightPx() }
    Box(modifier) {
        LazyVerticalGrid(
            columns = columns,
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = verticalArrangement,
            content = content,
        )
        if (showPageButtons) pager?.let { EinkPageControls(it, Modifier.align(Alignment.BottomEnd)) }
    }
}

private fun LazyListState.viewportHeightPx(): Int =
    layoutInfo.let { it.viewportSize.height - it.beforeContentPadding - it.afterContentPadding }

private fun LazyGridState.viewportHeightPx(): Int =
    layoutInfo.let { it.viewportSize.height - it.beforeContentPadding - it.afterContentPadding }

/** Null unless the display is E-Ink; while it lives it also receives the hardware page keys. */
@Composable
private fun rememberEinkPager(state: ScrollableState, viewportHeightPx: () -> Int): EinkPager? {
    val eink = LocalDisplayProfile.current == DisplayProfile.E_INK
    val scope = rememberCoroutineScope()
    val pager = remember(state, eink) { if (eink) EinkPager(state, viewportHeightPx, scope) else null }
    DisposableEffect(pager) {
        val unregister = pager?.let { EinkPageKeys.register(it::page) }
        onDispose { unregister?.invoke() }
    }
    return pager
}

private class EinkPager(
    private val state: ScrollableState,
    private val viewportHeightPx: () -> Int,
    private val scope: CoroutineScope,
) {
    val canPageBackward: Boolean get() = state.canScrollBackward
    val canPageForward: Boolean get() = state.canScrollForward

    /** Moves by one screen, keeping the last line of the old one in view; false when there is no further page. */
    fun page(direction: PageKeyDirection): Boolean {
        val forward = direction == PageKeyDirection.NEXT
        if (!(if (forward) canPageForward else canPageBackward)) return false
        val distance = viewportHeightPx() * PageFraction
        scope.launch { state.scrollBy(if (forward) distance else -distance) }
        return true
    }
}

@Composable
private fun EinkPageControls(pager: EinkPager, modifier: Modifier = Modifier) {
    val backward = pager.canPageBackward
    val forward = pager.canPageForward
    if (!backward && !forward) return
    Row(
        modifier = modifier.padding(
            end = Spacing.lg,
            bottom = Spacing.lg + LocalFloatingNavigationInset.current,
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PageButton(
            icon = Icons.Outlined.KeyboardArrowUp,
            description = stringResource(R.string.eink_page_previous),
            enabled = backward,
            onClick = { pager.page(PageKeyDirection.PREVIOUS) },
        )
        PageButton(
            icon = Icons.Outlined.KeyboardArrowDown,
            description = stringResource(R.string.eink_page_next),
            enabled = forward,
            onClick = { pager.page(PageKeyDirection.NEXT) },
        )
    }
}

@Composable
private fun PageButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(Sizes.touchTargetEink)
            .alpha(if (enabled) 1f else Opacities.disabled)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(Radii.largeIncreased),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Strokes.hairlineEink, MaterialTheme.colorScheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** A little less than the viewport, so the line a page ends on is the first of the next. */
private const val PageFraction = 0.9f
