package com.vayana.feature.gutenberg

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vayana.core.designsystem.component.VayanaDropdownMenu
import com.vayana.core.designsystem.component.VayanaLoadingIndicator
import com.vayana.core.designsystem.component.VayanaMenuGroup
import com.vayana.core.designsystem.component.VayanaMenuItem
import com.vayana.core.designsystem.component.morphingButtonShapes
import com.vayana.core.designsystem.theme.LocalFloatingNavigationInset
import com.vayana.core.designsystem.theme.VayanaLinearProgressIndicator
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.util.Locale
import kotlinx.coroutines.delay

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
        actions = remember(viewModel) {
            GutenbergActions(
                search = viewModel::search,
                showList = viewModel::showList,
                showTopic = viewModel::showTopic,
                showLanguage = viewModel::showLanguage,
                retry = viewModel::retry,
                loadMore = viewModel::loadMore,
                open = viewModel::open,
                close = viewModel::close,
                download = viewModel::download,
                cancelDownload = viewModel::cancelDownload,
                loadCover = viewModel::cover,
                cachedCover = viewModel::cachedCover,
            )
        },
        onBack = onBack,
    )
}

private class GutenbergActions(
    val search: (String) -> Unit,
    val showList: (GutenbergList) -> Unit,
    val showTopic: (GutenbergTopic?) -> Unit,
    val showLanguage: (String?) -> Unit,
    val retry: () -> Unit,
    val loadMore: () -> Unit,
    val open: (GutenbergBookSummary) -> Unit,
    val close: () -> Unit,
    val download: (GutenbergEdition) -> Unit,
    val cancelDownload: () -> Unit,
    val loadCover: suspend (String) -> ImageBitmap?,
    val cachedCover: (String) -> ImageBitmap?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GutenbergScreen(
    list: GutenbergListState,
    book: GutenbergBookState,
    actions: GutenbergActions,
    onBack: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    // Near the end of what's loaded, ask for the next page. Derived, so scrolling doesn't recompose the screen.
    val nearEnd by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LoadMoreThreshold
        }
    }
    val loadMore by rememberUpdatedState(actions.loadMore)
    LaunchedEffect(nearEnd, list.nextUrl) { if (nearEnd && list.nextUrl != null) loadMore() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gutenberg_title)) },
                // The app shell already applies the status-bar inset to this destination.
                windowInsets = WindowInsets(0, 0, 0, 0),
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Paddings.screenHorizontal,
                end = Paddings.screenHorizontal,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Spacing.xl + LocalFloatingNavigationInset.current,
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item(key = "search", span = { GridItemSpan(maxLineSpan) }, contentType = "search") {
                SearchField(text = list.query.text, onSearch = actions.search)
            }
            item(key = "filters", span = { GridItemSpan(maxLineSpan) }, contentType = "filters") {
                Filters(list.query, actions)
            }
            when {
                list.loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                    LoadingMessage(R.string.gutenberg_loading)
                }
                list.failed -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Message(stringResource(R.string.gutenberg_load_failed), actionLabel = stringResource(R.string.gutenberg_retry), onAction = actions.retry)
                }
                list.books.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Message(
                        if (list.query.text.isNotEmpty()) {
                            stringResource(R.string.gutenberg_no_results, list.query.text)
                        } else {
                            stringResource(R.string.gutenberg_no_books)
                        },
                    )
                }
                else -> {
                    items(list.books, key = { it.id }, contentType = { BookTileType }) { summary ->
                        BookTile(summary, actions, onClick = { actions.open(summary) })
                    }
                    if (list.loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) { LoadingMessage(R.string.gutenberg_loading_more) }
                    }
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
        BookSheet(book, actions)
    }
}

@Composable
private fun SearchField(text: String, onSearch: (String) -> Unit) {
    var value by rememberSaveable(text) { mutableStateOf(text) }
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.gutenberg_search_hint)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = {
                    value = ""
                    focusManager.clearFocus()
                    if (text.isNotEmpty()) onSearch("")
                }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.gutenberg_clear_search))
                }
            }
        } else {
            null
        },
        shape = RoundedCornerShape(Radii.full),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            focusManager.clearFocus()
            onSearch(value)
        }),
    )
}

/** The list's order and language on one row, the kinds of book on the next; both scroll sideways. */
@Composable
private fun Filters(query: GutenbergQuery, actions: GutenbergActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GutenbergList.entries.forEach { entry ->
                FilterChip(
                    selected = query.list == entry,
                    onClick = { if (query.list != entry || entry == GutenbergList.RANDOM) actions.showList(entry) },
                    label = { Text(entry.label()) },
                )
            }
            LanguageChip(query.language, actions.showLanguage)
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilterChip(
                selected = query.topic == null,
                onClick = { actions.showTopic(null) },
                label = { Text(stringResource(R.string.gutenberg_topic_all)) },
            )
            GutenbergTopic.entries.forEach { topic ->
                FilterChip(
                    selected = query.topic == topic,
                    // Tapping the chosen topic again goes back to every topic.
                    onClick = { actions.showTopic(if (query.topic == topic) null else topic) },
                    label = { Text(topic.label()) },
                )
            }
        }
    }
}

@Composable
private fun LanguageChip(language: String?, onLanguage: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val appLocale = LocalConfiguration.current.locales[0]
    fun name(code: String) = Locale.forLanguageTag(code).getDisplayLanguage(appLocale).replaceFirstChar { it.titlecase(appLocale) }
    Box {
        FilterChip(
            selected = language != null,
            onClick = { expanded = true },
            label = { Text(language?.let(::name) ?: stringResource(R.string.gutenberg_language_all)) },
            leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall)) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall)) },
        )
        VayanaDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            groups = listOf(
                VayanaMenuGroup(
                    label = stringResource(R.string.gutenberg_language),
                    items = listOf(
                        VayanaMenuItem(
                            label = stringResource(R.string.gutenberg_language_all),
                            selected = language == null,
                            onClick = { if (language != null) onLanguage(null) },
                        ),
                    ) + GutenbergLanguages.map { code ->
                        VayanaMenuItem(label = name(code), selected = language == code, onClick = { if (language != code) onLanguage(code) })
                    },
                ),
            ),
        )
    }
}

@Composable
private fun BookTile(summary: GutenbergBookSummary, actions: GutenbergActions, onClick: () -> Unit) {
    Column(modifier = Modifier.clip(RoundedCornerShape(Radii.small)).clickable(onClick = onClick)) {
        Cover(summary.coverUrl, summary.title, actions, Modifier.fillMaxWidth())
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
private fun Cover(url: String, title: String, actions: GutenbergActions, modifier: Modifier = Modifier) {
    // A cover already in memory draws on the first frame, with no placeholder flash.
    val bitmap by produceState(actions.cachedCover(url), url) { if (value == null) value = actions.loadCover(url) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookSheet(state: GutenbergBookState, actions: GutenbergActions) {
    val context = LocalContext.current
    val appLocale = LocalConfiguration.current.locales[0]
    ModalBottomSheet(onDismissRequest = actions.close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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
                // What the list already knows shows at once; the details and editions follow.
                is GutenbergBookState.Loading -> {
                    BookHeader(state.summary.coverUrl, state.summary.title, state.summary.author, details = null, actions)
                    LoadingMessage(R.string.gutenberg_loading_book)
                }
                is GutenbergBookState.Failed -> {
                    BookHeader(state.summary.coverUrl, state.summary.title, state.summary.author, details = null, actions)
                    Message(stringResource(R.string.gutenberg_book_failed), stringResource(R.string.gutenberg_retry)) { actions.open(state.summary) }
                }
                is GutenbergBookState.Loaded -> {
                    val book = state.book
                    val details = listOfNotNull(
                        book.language?.let { Locale.forLanguageTag(it).getDisplayLanguage(appLocale) },
                        book.published?.let { stringResource(R.string.gutenberg_published, it) },
                    ).joinToString(" · ").ifEmpty { null }
                    BookHeader(book.coverUrl, book.title, book.author, details, actions)
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
                        DownloadProgress(download, onCancel = actions.cancelDownload)
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
                            EditionButton(edition, formatSize = { Formatter.formatShortFileSize(context, it) }, onClick = { actions.download(edition) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookHeader(coverUrl: String, title: String, author: String?, details: String?, actions: GutenbergActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Cover(coverUrl, title, actions, Modifier.width(Sizes.coverWidthDetail))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            author?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            details?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun DownloadProgress(download: GutenbergDownload, onCancel: () -> Unit) {
    val progress = download.progress
    Text(
        text = if (progress == null) {
            stringResource(R.string.gutenberg_downloading)
        } else {
            stringResource(R.string.gutenberg_downloading_percent, (progress * 100).toInt())
        },
        style = MaterialTheme.typography.titleSmall,
    )
    // Until the first bytes (and the size) arrive, an indeterminate bar: something is happening.
    if (progress == null) {
        VayanaLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else {
        VayanaLinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
    TextButton(onClick = onCancel) { Text(stringResource(R.string.gutenberg_cancel)) }
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

/**
 * Gutenberg can take a few seconds to answer: an expressive loading indicator and what's being fetched, then, if it's
 * still going, a note that it's slow rather than stuck.
 */
@Composable
private fun LoadingMessage(@StringRes textRes: Int) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SlowAfterMillis)
        slow = true
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        VayanaLoadingIndicator(modifier = Modifier.size(Sizes.badge))
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (slow) {
            Text(
                text = stringResource(R.string.gutenberg_loading_slow),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Message(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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

@Composable
private fun GutenbergTopic.label(): String = stringResource(
    when (this) {
        GutenbergTopic.ADVENTURE -> R.string.gutenberg_topic_adventure
        GutenbergTopic.MYSTERY -> R.string.gutenberg_topic_mystery
        GutenbergTopic.SCIENCE_FICTION -> R.string.gutenberg_topic_science_fiction
        GutenbergTopic.FANTASY -> R.string.gutenberg_topic_fantasy
        GutenbergTopic.HORROR -> R.string.gutenberg_topic_horror
        GutenbergTopic.ROMANCE -> R.string.gutenberg_topic_romance
        GutenbergTopic.HUMOUR -> R.string.gutenberg_topic_humour
        GutenbergTopic.SHORT_STORIES -> R.string.gutenberg_topic_short_stories
        GutenbergTopic.POETRY -> R.string.gutenberg_topic_poetry
        GutenbergTopic.DRAMA -> R.string.gutenberg_topic_drama
        GutenbergTopic.CHILDREN -> R.string.gutenberg_topic_children
        GutenbergTopic.PHILOSOPHY -> R.string.gutenberg_topic_philosophy
    },
)

private const val LoadMoreThreshold = 6
private const val MaxSubjects = 4
private const val SlowAfterMillis = 4_000L
private const val BookTileType = "book"
