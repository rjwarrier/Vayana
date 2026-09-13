package com.vayana.feature.search

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.runtime.collectAsState
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
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.designsystem.theme.vayanaContentTransform
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Palette
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

@Composable
fun SearchRoute(
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenReader: (Long, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SearchViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    SearchScreen(
        modifier = modifier,
        uiState = uiState,
        onQueryChange = viewModel::updateQuery,
        onBack = onBack,
        onOpenBook = onOpenBook,
        onOpenReader = onOpenReader,
    )
}

@Composable
private fun SearchScreen(
    uiState: GlobalSearchUiState,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenReader: (Long, String?) -> Unit,
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
                        value = uiState.query,
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
                        trailingIcon = if (uiState.query.isNotEmpty()) {
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
                        keyboardActions = KeyboardActions.Default,
                    )
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
                SearchContentState.EmptyQuery -> SearchEmptyState(innerPadding)
                SearchContentState.NoMatches -> SearchNoMatchesState(innerPadding)
                SearchContentState.Results -> SearchResultsList(
                    contentPadding = innerPadding,
                    books = uiState.books,
                    annotations = uiState.annotations,
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
    onOpenBook: (Long) -> Unit,
    onOpenReader: (Long, String?) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Paddings.screenHorizontal,
            top = contentPadding.calculateTopPadding() + Spacing.md,
            end = Paddings.screenHorizontal,
            bottom = contentPadding.calculateBottomPadding() + Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (books.isNotEmpty()) {
            item { SearchSectionHeader(stringResource(R.string.search_books_header, books.size)) }
            items(books, key = { "book-${it.book.id}" }) { result ->
                BookResultRow(result = result, onClick = { onOpenBook(result.book.id) })
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
                    onClick = {
                        if (result.book.format != BookFormat.PHYSICAL) {
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
private fun BookResultRow(result: BookSearchResult, onClick: () -> Unit) {
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
                    text = result.book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                result.book.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        text = author,
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
private fun AnnotationResultRow(result: AnnotationSearchResult, onClick: () -> Unit) {
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
                    text = result.annotation.selectedText,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            result.annotation.readerNote?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.large))
            .clickable(onClick = onClick),
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
private fun SearchEmptyState(contentPadding: PaddingValues) {
    SearchState(
        contentPadding = contentPadding,
        icon = Icons.Outlined.Search,
        title = stringResource(R.string.search_empty_title),
        body = stringResource(R.string.search_empty_body),
    )
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
    hasMatches -> SearchContentState.Results
    isSearching -> SearchContentState.Results
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
