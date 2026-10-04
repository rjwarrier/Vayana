package com.vayana.feature.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.vayana.core.designsystem.theme.PagedLazyColumn
import com.vayana.core.designsystem.theme.rememberCoverColorFilter
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.model.Book
import com.vayana.core.designsystem.theme.vayanaContentTransform
import com.vayana.core.designsystem.theme.vayanaPressScale
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Palette
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.vayana.core.database.search.wordPrefixMatchRanges

@Composable
fun SearchRoute(
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenReader: (Long, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SearchViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val indexProgress by viewModel.contentIndex.progress.collectAsStateWithLifecycle()

    SearchScreen(
        modifier = modifier,
        uiState = uiState,
        indexProgress = indexProgress,
        onRetryIndex = viewModel.contentIndex::retry,
        searchText = viewModel.searchText,
        onQueryChange = viewModel::updateQuery,
        onFilterChange = viewModel::updateFilter,
        onSubmitSearch = viewModel::recordSearch,
        onUseRecentSearch = viewModel::updateQuery,
        onClearRecentSearches = viewModel::clearRecentSearches,
        onBack = onBack,
        onOpenBook = { bookId ->
            viewModel.recordSearch()
            onOpenBook(bookId)
        },
        onOpenReader = { bookId, locator ->
            viewModel.recordSearch()
            onOpenReader(bookId, locator)
        },
    )
}

@Composable
private fun SearchScreen(
    uiState: GlobalSearchUiState,
    indexProgress: IndexProgress,
    onRetryIndex: () -> Unit,
    /** What the search box shows; [uiState]'s query lags a frame behind typing. */
    searchText: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenReader: (Long, String?) -> Unit,
    onFilterChange: (SearchFilter) -> Unit,
    onSubmitSearch: () -> Unit,
    onUseRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = androidx.compose.runtime.remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Paddings.screenHorizontal, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.search_back_content_description),
                            )
                        }
                        Text(
                            text = stringResource(R.string.search_title),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        singleLine = true,
                        shape = RoundedCornerShape(Radii.full),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = SearchFieldUnfocusedBorderAlpha),
                        ),
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = if (searchText.isNotEmpty()) {
                            {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.input_clear_content_description),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                    )
                    if (indexProgress.running) Text(stringResource(R.string.search_indexing, indexProgress.done, indexProgress.total), style = MaterialTheme.typography.bodySmall)
                    if (indexProgress.failures > 0 && !indexProgress.running) Row {
                        Text(stringResource(R.string.search_index_errors, indexProgress.failures))
                        TextButton(onClick = onRetryIndex) { Text(stringResource(R.string.search_index_retry)) }
                    }
                    if (uiState.hasQuery && uiState.totalMatches > 0) {
                        SearchFilterChips(uiState = uiState, onFilterChange = onFilterChange)
                    }
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = uiState.contentState(),
            transitionSpec = vayanaContentTransform(),
            label = "GlobalSearchContent",
        ) { state ->
            when (state) {
                SearchContentState.EmptyQuery -> SearchEmptyState(
                    contentPadding = innerPadding,
                    recentSearches = uiState.recentSearches,
                    onUseRecentSearch = onUseRecentSearch,
                    onClearRecentSearches = onClearRecentSearches,
                )
                SearchContentState.NoMatches -> SearchNoMatchesState(innerPadding)
                SearchContentState.Results -> SearchResultsList(
                    contentPadding = innerPadding,
                    books = uiState.shownBooks,
                    annotations = uiState.shownAnnotations,
                    passages = uiState.shownPassages,
                    tokens = uiState.tokens,
                    onOpenBook = onOpenBook,
                    onOpenReader = onOpenReader,
                )
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    books: List<BookSearchResult>,
    annotations: List<AnnotationSearchResult>,
    passages: List<BookContentResult>,
    tokens: List<String>,
    onOpenBook: (Long) -> Unit,
    onOpenReader: (Long, String?) -> Unit,
    contentPadding: PaddingValues,
) {
    PagedLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Paddings.screenHorizontal,
            top = contentPadding.calculateTopPadding() + Spacing.md,
            end = Paddings.screenHorizontal,
            bottom = contentPadding.calculateBottomPadding() + Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (passages.isNotEmpty()) {
            item { SearchSectionHeader(stringResource(R.string.search_contents)) }
            items(passages, key = { "passage-${it.id}" }) { result ->
                SearchResultSurface(onClick = { onOpenReader(result.book.id, result.locator) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(result.book.title, style = MaterialTheme.typography.titleMedium)
                        Text(result.chapter, style = MaterialTheme.typography.labelMedium)
                        Text(highlightMatches(result.excerpt, tokens), maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (books.isNotEmpty()) {
            item { SearchSectionHeader(stringResource(R.string.search_books_header, books.size)) }
            items(books, key = { "book-${it.book.id}" }) { result ->
                BookResultRow(result = result, tokens = tokens, onClick = { onOpenBook(result.book.id) })
            }
        }
        if (books.isNotEmpty() && annotations.isNotEmpty()) {
            item { HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm)) }
        }
        if (annotations.isNotEmpty()) {
            item { SearchSectionHeader(stringResource(R.string.search_notes_header, annotations.size)) }
            items(annotations, key = { "annotation-${it.annotation.id}" }) { result ->
                AnnotationResultRow(
                    result = result,
                    tokens = tokens,
                    onClick = {
                        if (!result.book.format.isOffline) {
                            onOpenReader(result.book.id, result.annotation.locator.ifBlank { "text:${result.annotation.id}" })
                        } else {
                            onOpenBook(result.book.id)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.xs),
    )
}

@Composable
private fun BookResultRow(result: BookSearchResult, tokens: List<String>, onClick: () -> Unit) {
    SearchResultSurface(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(book = result.book)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = highlightMatches(result.book.title, tokens),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                result.book.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        text = highlightMatches(author, tokens),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MatchChips(result.matchedFields)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun AnnotationResultRow(result: AnnotationSearchResult, tokens: List<String>, onClick: () -> Unit) {
    SearchResultSurface(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = result.book.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = result.annotation.type.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            if (result.annotation.selectedText.isNotBlank()) {
                Text(
                    text = highlightMatches(result.annotation.selectedText, tokens),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            result.annotation.readerNote?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = highlightMatches(note, tokens),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MatchChips(result.matchedFields)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun SearchResultSurface(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .vayanaPressScale(interactionSource)
            .clip(RoundedCornerShape(Radii.large))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.none,
    ) {
        Box(modifier = Modifier.padding(Paddings.card)) {
            content()
        }
    }
}

@Composable
private fun MatchChips(fields: List<SearchMatchedField>) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        fields.take(MaxVisibleMatchChips).forEach { field ->
            AssistChip(
                onClick = {},
                label = { Text(field.label(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun BookCover(book: Book) {
    val coverModifier = Modifier
        .width(Sizes.coverWidthMin)
        .aspectRatio(Sizes.coverAspectRatio)
        .clip(RoundedCornerShape(Radii.medium))

    if (book.coverPath != null) {
        AsyncImage(
            model = book.coverPath,
            contentDescription = stringResource(R.string.library_book_cover_content_description, book.title),
            contentScale = ContentScale.Crop,
            colorFilter = rememberCoverColorFilter(),
            modifier = coverModifier,
        )
    } else {
        Surface(
            modifier = coverModifier,
            color = Palette.ReaderPaperBackground,
            contentColor = Palette.ReaderPaperText,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(Spacing.sm)) {
                Icon(Icons.Outlined.AutoStories, contentDescription = null, modifier = Modifier.size(Sizes.icon))
            }
        }
    }
}

@Composable
private fun SearchEmptyState(
    contentPadding: PaddingValues,
    recentSearches: List<String>,
    onUseRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
) {
    if (recentSearches.isEmpty()) {
        SearchState(
            contentPadding = contentPadding,
            icon = Icons.Outlined.Search,
            title = stringResource(R.string.search_empty_title),
            body = stringResource(R.string.search_empty_body),
        )
        return
    }
    PagedLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Paddings.screenHorizontal,
            top = contentPadding.calculateTopPadding() + Spacing.md,
            end = Paddings.screenHorizontal,
            bottom = contentPadding.calculateBottomPadding() + Spacing.xl,
        ),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchSectionHeader(stringResource(R.string.search_recent_title))
                TextButton(onClick = onClearRecentSearches) {
                    Text(stringResource(R.string.search_recent_clear))
                }
            }
        }
        items(recentSearches, key = { it }) { recent ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.medium))
                    .clickable { onUseRecentSearch(recent) }
                    .padding(horizontal = Spacing.sm, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = recent,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SearchFilterChips(uiState: GlobalSearchUiState, onFilterChange: (SearchFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SearchFilter.entries.forEach { filter ->
            FilterChip(
                selected = uiState.filter == filter,
                onClick = { onFilterChange(filter) },
                label = { Text(filter.label(uiState)) },
            )
        }
    }
}

@Composable
private fun SearchFilter.label(state: GlobalSearchUiState): String = when (this) {
    SearchFilter.ALL -> stringResource(R.string.search_filter_all, state.totalMatches)
    SearchFilter.BOOKS -> stringResource(R.string.search_filter_books, state.books.size)
    SearchFilter.NOTES -> stringResource(R.string.search_filter_notes, state.annotations.size)
    SearchFilter.CONTENTS -> stringResource(R.string.search_contents) + " (${state.passages.size})"
}

/** [text] with the word prefixes that matched the search emphasised. */
@Composable
private fun highlightMatches(text: String, tokens: List<String>): AnnotatedString {
    val highlight = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    return remember(text, tokens, highlight) {
        buildAnnotatedString {
            append(text)
            wordPrefixMatchRanges(text, tokens).forEach { range -> addStyle(highlight, range.first, range.last + 1) }
        }
    }
}

@Composable
private fun SearchNoMatchesState(contentPadding: PaddingValues) {
    SearchState(
        contentPadding = contentPadding,
        icon = Icons.Outlined.EditNote,
        title = stringResource(R.string.search_no_matches_title),
        body = stringResource(R.string.search_no_matches_body),
    )
}

@Composable
private fun SearchState(
    contentPadding: PaddingValues,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(Paddings.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(Radii.extraLarge),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(Spacing.lg)
                    .size(Sizes.iconLarge),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

private enum class SearchContentState { EmptyQuery, NoMatches, Results }

private fun GlobalSearchUiState.contentState(): SearchContentState = when {
    !hasQuery -> SearchContentState.EmptyQuery
    hasMatches || isSearching -> SearchContentState.Results
    else -> SearchContentState.NoMatches
}

@Composable
private fun SearchMatchedField.label(): String = when (this) {
    SearchMatchedField.TITLE -> stringResource(R.string.search_match_title)
    SearchMatchedField.AUTHOR -> stringResource(R.string.search_match_author)
    SearchMatchedField.SERIES -> stringResource(R.string.search_match_series)
    SearchMatchedField.TAGS -> stringResource(R.string.search_match_tags)
    SearchMatchedField.DESCRIPTION -> stringResource(R.string.search_match_description)
    SearchMatchedField.HIGHLIGHT -> stringResource(R.string.search_match_highlight)
    SearchMatchedField.NOTE -> stringResource(R.string.search_match_note)
    SearchMatchedField.CHAPTER -> stringResource(R.string.search_match_chapter)
}

@Composable
private fun AnnotationType.label(): String = when (this) {
    AnnotationType.HIGHLIGHT -> stringResource(R.string.notes_filter_highlights)
    AnnotationType.NOTE -> stringResource(R.string.notes_filter_notes)
    AnnotationType.BOOKMARK -> stringResource(R.string.notes_filter_bookmarks)
    AnnotationType.UNDERLINE -> stringResource(R.string.notes_filter_underlines)
}

private const val SearchFieldUnfocusedBorderAlpha = 0.35f
private const val MaxVisibleMatchChips = 3
