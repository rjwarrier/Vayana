package com.vayana.feature.gutenberg

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vayana.core.designsystem.component.VayanaLoadingIndicator
import com.vayana.core.designsystem.component.morphingButtonShapes
import com.vayana.core.designsystem.theme.VayanaLinearProgressIndicator
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.util.Locale

/**
 * Browse and search Project Gutenberg, pick a book, choose the edition (with or without images, each with its size),
 * and it lands in the library through the normal import. [onImported] returns to the library, which shows it.
 */
@Composable
fun GutenbergRoute(onBack: () -> Unit, onImported: () -> Unit, viewModel: GutenbergViewModel = hiltViewModel()) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val book by viewModel.book.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.imported.collect { onImported() } }
    GutenbergScreen(
        list = list,
        book = book,
        onBack = onBack,
        onShow = viewModel::show,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onOpen = viewModel::open,
        onClose = viewModel::close,
        onDownload = viewModel::download,
        onCancelDownload = viewModel::cancelDownload,
        loadCover = viewModel::cover,
        cachedCover = viewModel::cachedCover,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GutenbergScreen(
    list: GutenbergListState,
    book: GutenbergBookState,
    onBack: () -> Unit,
    onShow: (GutenbergSource) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (GutenbergBookSummary) -> Unit,
    onClose: () -> Unit,
    onDownload: (GutenbergEdition) -> Unit,
    onCancelDownload: () -> Unit,
    loadCover: suspend (String) -> ImageBitmap?,
    cachedCover: (String) -> ImageBitmap?,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val gridState = rememberLazyGridState()
    // Near the end of what's loaded, ask for the next page. Derived, so scrolling doesn't recompose the screen.
    val nearEnd by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LoadMoreThreshold
        }
    }
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(nearEnd, list.nextUrl) { if (nearEnd && list.nextUrl != null) currentOnLoadMore() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.gutenberg_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.gutenberg_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.gutenberg_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = Sizes.libraryGridCoverWidthMin),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Paddings.screenHorizontal, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.gutenberg_search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    shape = RoundedCornerShape(Radii.full),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onShow(GutenbergSource.Search(query)) }),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    GutenbergList.entries.forEach { entry ->
                        FilterChip(
                            selected = list.source == GutenbergSource.Browse(entry),
                            onClick = {
                                query = ""
                                onShow(GutenbergSource.Browse(entry))
                            },
                            label = { Text(entry.label()) },
                        )
                    }
                }
            }
            when {
                list.loading -> item(span = { GridItemSpan(maxLineSpan) }) { CenteredLoading() }
                list.failed -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Message(stringResource(R.string.gutenberg_load_failed), actionLabel = stringResource(R.string.gutenberg_retry), onAction = onRetry)
                }
                list.books.isEmpty() && list.source is GutenbergSource.Search -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Message(stringResource(R.string.gutenberg_no_results, list.source.query))
                }
                else -> {
                    items(list.books, key = { it.id }, contentType = { BookTileType }) { summary ->
                        BookTile(summary, loadCover, cachedCover, onClick = { onOpen(summary) })
                    }
                    if (list.loadingMore) item(span = { GridItemSpan(maxLineSpan) }) { CenteredLoading() }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(R.string.gutenberg_attribution),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.md),
                        )
                    }
                }
            }
        }
    }
    if (book != GutenbergBookState.Hidden) {
        BookSheet(book, loadCover, cachedCover, onClose, onDownload, onCancelDownload)
    }
}

@Composable
private fun BookTile(
    summary: GutenbergBookSummary,
    loadCover: suspend (String) -> ImageBitmap?,
    cachedCover: (String) -> ImageBitmap?,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.clip(RoundedCornerShape(Radii.small)).clickable(onClick = onClick)) {
        Cover(summary.coverUrl, summary.title, loadCover, cachedCover, Modifier.fillMaxWidth())
        Text(
            text = summary.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        summary.author?.let { author ->
            Text(
                text = author,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A cover from gutenberg.org, with the title on a plain card until it arrives, or when the book has none. */
@Composable
private fun Cover(
    url: String,
    title: String,
    loadCover: suspend (String) -> ImageBitmap?,
    cachedCover: (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    // A cover already in memory draws on the first frame, with no placeholder flash.
    val bitmap by produceState(cachedCover(url), url) { if (value == null) value = loadCover(url) }
    Surface(
        modifier = modifier.aspectRatio(Sizes.coverAspectRatio),
        shape = RoundedCornerShape(Radii.small),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        val image = bitmap
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(Spacing.sm)) {
                Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BookSheet(
    state: GutenbergBookState,
    loadCover: suspend (String) -> ImageBitmap?,
    cachedCover: (String) -> ImageBitmap?,
    onClose: () -> Unit,
    onDownload: (GutenbergEdition) -> Unit,
    onCancelDownload: () -> Unit,
) {
    val context = LocalContext.current
    val appLocale = LocalConfiguration.current.locales[0]
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Paddings.screenHorizontal)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when (state) {
                GutenbergBookState.Hidden -> Unit
                is GutenbergBookState.Loading -> CenteredLoading()
                is GutenbergBookState.Failed -> Message(stringResource(R.string.gutenberg_book_failed))
                is GutenbergBookState.Loaded -> {
                    val book = state.book
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        Cover(book.coverUrl, book.title, loadCover, cachedCover, Modifier.width(Sizes.coverWidthDetail))
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(book.title, style = MaterialTheme.typography.titleLarge)
                            book.author?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                            val details = listOfNotNull(
                                book.language?.let { Locale.forLanguageTag(it).getDisplayLanguage(appLocale) },
                                book.published?.let { stringResource(R.string.gutenberg_published, it) },
                            ).joinToString(" · ")
                            if (details.isNotEmpty()) {
                                Text(details, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    book.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (book.subjects.isNotEmpty()) {
                        Text(
                            book.subjects.take(MaxSubjects).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val download = state.download
                    if (download != null && !download.failed) {
                        Text(stringResource(R.string.gutenberg_downloading), style = MaterialTheme.typography.titleSmall)
                        VayanaLinearProgressIndicator(progress = { download.progress ?: 0f }, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = onCancelDownload) { Text(stringResource(R.string.gutenberg_cancel)) }
                    } else {
                        if (download?.failed == true) {
                            Text(
                                stringResource(R.string.gutenberg_download_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        // Both editions, each with its size: the reader chooses.
                        book.editions.forEach { edition ->
                            EditionButton(edition, formatSize = { Formatter.formatShortFileSize(context, it) }, onClick = { onDownload(edition) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditionButton(edition: GutenbergEdition, formatSize: (Long) -> String, onClick: () -> Unit) {
    val withImages = edition.kind == GutenbergEditionKind.WITH_IMAGES
    val name = stringResource(if (withImages) R.string.gutenberg_edition_with_images else R.string.gutenberg_edition_without_images)
    val label = edition.sizeBytes?.let { stringResource(R.string.gutenberg_edition_action, name, formatSize(it)) } ?: name
    val detail = stringResource(
        if (withImages) R.string.gutenberg_edition_with_images_detail else R.string.gutenberg_edition_without_images_detail,
    )
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                if (withImages) Icons.Outlined.Image else Icons.AutoMirrored.Outlined.Notes,
                contentDescription = null,
                modifier = Modifier.size(Sizes.iconSmall),
            )
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(detail, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    // The lighter edition is the filled one: it's what most readers want, and the smaller download.
    if (withImages) {
        OutlinedButton(onClick = onClick, shapes = morphingButtonShapes(), modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        FilledTonalButton(onClick = onClick, shapes = morphingButtonShapes(), modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun CenteredLoading() {
    Box(modifier = Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        VayanaLoadingIndicator(modifier = Modifier.size(Sizes.iconLarge))
    }
}

@Composable
private fun Message(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        actionLabel?.let { TextButton(onClick = onAction) { Text(it) } }
    }
}

@Composable
private fun GutenbergList.label(): String = stringResource(
    when (this) {
        GutenbergList.POPULAR -> R.string.gutenberg_list_popular
        GutenbergList.LATEST -> R.string.gutenberg_list_latest
        GutenbergList.RANDOM -> R.string.gutenberg_list_random
    },
)

private const val LoadMoreThreshold = 6
private const val BookTileType = "book"
private const val MaxSubjects = 4
