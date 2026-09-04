package com.vayana.feature.notes

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.model.Book
import com.vayana.core.designsystem.theme.vayanaAnimateContentSize
import com.vayana.core.designsystem.theme.vayanaContentTransform
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun NotesRoute(
    onOpenReader: (Long, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: NotesViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    NotesScreen(
        modifier = modifier,
        booksWithNotes = uiState.booksWithNotes,
        allAnnotations = uiState.allAnnotations,
        onOpenReader = onOpenReader,
        onUpdateNote = viewModel::updateNote,
        onDeleteAnnotation = viewModel::deleteAnnotation,
    )
}

@Composable
private fun NotesScreen(
    modifier: Modifier = Modifier,
    booksWithNotes: List<BookNotesItem>,
    allAnnotations: List<Annotation>,
    onOpenReader: (Long, String?) -> Unit,
    onUpdateNote: (Annotation, String) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
) {
    val context = LocalContext.current
    var selectedBookId by remember { mutableStateOf<Long?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(NotesFilter.ALL) }
    var editingAnnotation by remember { mutableStateOf<Annotation?>(null) }
    var deletingAnnotation by remember { mutableStateOf<Annotation?>(null) }

    val activeBookItem = remember(selectedBookId, booksWithNotes) {
        booksWithNotes.firstOrNull { it.book.id == selectedBookId }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Paddings.screenHorizontal, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        if (activeBookItem != null) {
                            IconButton(onClick = { selectedBookId = null }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.notes_back_content_description),
                                )
                            }
                        }
                        Text(
                            text = if (activeBookItem != null) activeBookItem.book.title else stringResource(R.string.notes_title),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val exportNotes = if (activeBookItem != null) activeBookItem.annotations else allAnnotations
                    if (exportNotes.isNotEmpty()) {
                        IconButton(onClick = { context.shareAnnotations(exportNotes) }) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.notes_export_content_description),
                            )
                        }
                    }
                }

                if (booksWithNotes.isNotEmpty()) {
                    val focusManager = LocalFocusManager.current
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(Radii.full),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ),
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.input_clear_content_description),
                                    )
                                }
                            }
                        },
                        placeholder = { Text(stringResource(R.string.notes_search_placeholder)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    )

                    if (activeBookItem != null) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(top = Spacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            NotesFilter.entries.forEach { option ->
                                FilterChip(
                                    selected = filter == option,
                                    onClick = { filter = option },
                                    label = { Text(option.label()) },
                                    shape = RoundedCornerShape(Radii.full),
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = activeBookItem,
            transitionSpec = vayanaContentTransform(),
            label = "NotesBookNav",
        ) { bookItem ->
            if (bookItem == null) {
                val visibleBookItems = remember(booksWithNotes, query) {
                    booksWithNotes.filterBooksByQuery(query)
                }
                if (booksWithNotes.isEmpty()) {
                    NotesEmptyState(contentPadding = innerPadding)
                } else if (visibleBookItems.isEmpty()) {
                    NotesNoMatchesState(contentPadding = innerPadding)
                } else {
                    BooksWithNotesList(
                        contentPadding = innerPadding,
                        booksWithNotes = visibleBookItems,
                        onBookClick = { selectedBookId = it.book.id },
                    )
                }
            } else {
                val visibleAnnotations = remember(bookItem.annotations, query, filter) {
                    bookItem.annotations.filterByQuery(query, filter)
                }
                if (visibleAnnotations.isEmpty()) {
                    NotesNoMatchesState(contentPadding = innerPadding)
                } else {
                    BookNotesDetailList(
                        contentPadding = innerPadding,
                        bookItem = bookItem,
                        annotations = visibleAnnotations,
                        onAnnotationClick = { annotation ->
                            val locator = annotation.locator.ifBlank { "text:${annotation.id}" }
                            onOpenReader(bookItem.book.id, locator)
                        },
                        onOpenBook = {
                            onOpenReader(bookItem.book.id, null)
                        },
                        onEdit = { editingAnnotation = it },
                        onDelete = { deletingAnnotation = it },
                    )
                }
            }
        }
    }

    editingAnnotation?.let { annotation ->
        EditNoteDialog(
            annotation = annotation,
            onDismiss = { editingAnnotation = null },
            onConfirm = { updatedNote ->
                editingAnnotation = null
                onUpdateNote(annotation, updatedNote)
            },
        )
    }

    deletingAnnotation?.let { annotation ->
        AlertDialog(
            onDismissRequest = { deletingAnnotation = null },
            icon = {
                Surface(
                    shape = RoundedCornerShape(Radii.large),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            },
            title = { Text(stringResource(R.string.notes_delete_title)) },
            text = { Text(stringResource(R.string.notes_delete_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        deletingAnnotation = null
                        onDeleteAnnotation(annotation.id)
                    },
                    shape = RoundedCornerShape(Radii.full),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.notes_delete_confirm))
                }
            },
            dismissButton = {
                FilledTonalButton(
                    onClick = { deletingAnnotation = null },
                    shape = RoundedCornerShape(Radii.full),
                ) {
                    Text(stringResource(R.string.settings_reset_all_cancel))
                }
            },
            shape = RoundedCornerShape(Radii.extraLargeIncreased),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = Elevations.shadowLarge,
        )
    }
}

@Composable
private fun BooksWithNotesList(
    contentPadding: PaddingValues,
    booksWithNotes: List<BookNotesItem>,
    onBookClick: (BookNotesItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Paddings.screenHorizontal,
            end = Paddings.screenHorizontal,
            top = contentPadding.calculateTopPadding() + Spacing.sm,
            bottom = contentPadding.calculateBottomPadding() + Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(booksWithNotes, key = { it.book.id }) { item ->
            BookNotesCard(
                item = item,
                onClick = { onBookClick(item) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun BookNotesCard(
    item: BookNotesItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.largeIncreased))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radii.largeIncreased),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.shadowSmall,
    ) {
        Row(
            modifier = Modifier.padding(Paddings.card),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCoverThumbnail(
                coverPath = item.book.coverPath,
                title = item.book.title,
                modifier = Modifier
                    .width(Sizes.coverWidthMin)
                    .aspectRatio(Sizes.coverAspectRatio),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = item.book.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.book.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                BookNotesStatsSummary(
                    annotations = item.annotations,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BookNotesStatsSummary(
    annotations: List<Annotation>,
    modifier: Modifier = Modifier,
) {
    val highlightCount = remember(annotations) { annotations.count { it.type == AnnotationType.HIGHLIGHT } }
    val noteCount = remember(annotations) { annotations.count { it.type == AnnotationType.NOTE || !it.readerNote.isNullOrBlank() } }
    val bookmarkCount = remember(annotations) { annotations.count { it.type == AnnotationType.BOOKMARK } }
    val underlineCount = remember(annotations) { annotations.count { it.type == AnnotationType.UNDERLINE } }
    val chaptersCount = remember(annotations) { annotations.mapNotNull { it.chapterTitle }.distinct().size }
    val latestDate = remember(annotations) { annotations.maxOfOrNull { it.updatedAt } }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Highlights count pill
            if (highlightCount > 0) {
                Surface(
                    shape = RoundedCornerShape(Radii.full),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        text = if (highlightCount == 1) {
                            stringResource(R.string.notes_stat_single_highlight)
                        } else {
                            stringResource(R.string.notes_stat_highlights, highlightCount)
                        },
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            // User notes count pill
            if (noteCount > 0) {
                Surface(
                    shape = RoundedCornerShape(Radii.full),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        text = if (noteCount == 1) {
                            stringResource(R.string.notes_stat_single_user_note)
                        } else {
                            stringResource(R.string.notes_stat_user_notes, noteCount)
                        },
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            // Bookmarks count pill
            if (bookmarkCount > 0) {
                Surface(
                    shape = RoundedCornerShape(Radii.full),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Text(
                        text = if (bookmarkCount == 1) {
                            stringResource(R.string.notes_stat_single_bookmark)
                        } else {
                            stringResource(R.string.notes_stat_bookmarks, bookmarkCount)
                        },
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            // Underlines count pill
            if (underlineCount > 0) {
                Surface(
                    shape = RoundedCornerShape(Radii.full),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = stringResource(R.string.notes_stat_underlines, underlineCount),
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        // Secondary metadata line: chapters count & last modified date
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (chaptersCount > 0) {
                Text(
                    text = if (chaptersCount == 1) {
                        stringResource(R.string.notes_stat_single_chapter)
                    } else {
                        stringResource(R.string.notes_stat_chapters_count, chaptersCount)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (chaptersCount > 0 && latestDate != null) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            latestDate?.let { date ->
                Text(
                    text = stringResource(R.string.notes_stat_last_activity, date.formatDate()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BookCoverThumbnail(
    coverPath: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(Radii.small)),
        shape = RoundedCornerShape(Radii.small),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = Elevations.none,
    ) {
        if (coverPath != null && File(coverPath).exists()) {
            AsyncImage(
                model = File(coverPath),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoStories,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.icon),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun BookNotesDetailList(
    contentPadding: PaddingValues,
    bookItem: BookNotesItem,
    annotations: List<Annotation>,
    onAnnotationClick: (Annotation) -> Unit,
    onOpenBook: () -> Unit,
    onEdit: (Annotation) -> Unit,
    onDelete: (Annotation) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Paddings.screenHorizontal,
            end = Paddings.screenHorizontal,
            top = contentPadding.calculateTopPadding() + Spacing.sm,
            bottom = contentPadding.calculateBottomPadding() + Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            BookNotesHero(bookItem = bookItem, onOpenBook = onOpenBook)
        }
        val userAnnotations = annotations.filterNot { it.isCommunityQuote() }
        val communityAnnotations = annotations.filter { it.isCommunityQuote() }
        if (userAnnotations.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.notes_section_user, userAnnotations.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            items(userAnnotations, key = { it.id }) { annotation ->
                AnnotationCard(
                    annotation = annotation,
                    onClick = { onAnnotationClick(annotation) },
                    onEdit = { onEdit(annotation) },
                    onDelete = { onDelete(annotation) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (communityAnnotations.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.notes_section_community, communityAnnotations.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            items(communityAnnotations, key = { it.id }) { annotation ->
                AnnotationCard(
                    annotation = annotation,
                    onClick = { onAnnotationClick(annotation) },
                    onEdit = { onEdit(annotation) },
                    onDelete = { onDelete(annotation) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

private fun Annotation.isCommunityQuote(): Boolean = locator.startsWith("quote:")

@Composable
private fun BookNotesHero(
    bookItem: BookNotesItem,
    onOpenBook: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenBook),
        shape = RoundedCornerShape(Radii.extraLargeIncreased),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.shadowSmall,
    ) {
        Row(
            modifier = Modifier.padding(Paddings.card),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCoverThumbnail(
                coverPath = bookItem.book.coverPath,
                title = bookItem.book.title,
                modifier = Modifier
                    .width(Sizes.coverWidthMin)
                    .aspectRatio(Sizes.coverAspectRatio),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = bookItem.book.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                bookItem.book.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BookNotesStatsSummary(
                    annotations = bookItem.annotations,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun AnnotationCard(
    annotation: Annotation,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radii.largeIncreased),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.none,
    ) {
        Column(
            modifier = Modifier
                .padding(Paddings.card)
                .vayanaAnimateContentSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(Radii.full),
                    color = when (annotation.type) {
                        AnnotationType.HIGHLIGHT -> MaterialTheme.colorScheme.primaryContainer
                        AnnotationType.NOTE -> MaterialTheme.colorScheme.secondaryContainer
                        AnnotationType.BOOKMARK -> MaterialTheme.colorScheme.tertiaryContainer
                        AnnotationType.UNDERLINE -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = when (annotation.type) {
                        AnnotationType.HIGHLIGHT -> MaterialTheme.colorScheme.onPrimaryContainer
                        AnnotationType.NOTE -> MaterialTheme.colorScheme.onSecondaryContainer
                        AnnotationType.BOOKMARK -> MaterialTheme.colorScheme.onTertiaryContainer
                        AnnotationType.UNDERLINE -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) {
                    Text(
                        text = annotation.type.label(),
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                annotation.chapterTitle?.let { chapter ->
                    Text(
                        text = chapter,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }

            if (annotation.selectedText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(Radii.medium),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "“${annotation.selectedText}”",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }

            annotation.readerNote?.takeIf { it.isNotBlank() }?.let { note ->
                Surface(
                    shape = RoundedCornerShape(Radii.medium),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Sizes.iconSmall),
                        )
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = annotation.updatedAt.formatDate(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.notes_edit_content_description),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.notes_delete_content_description),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

private enum class NotesFilter { ALL, HIGHLIGHTS, NOTES, BOOKMARKS, UNDERLINES }

@Composable
private fun NotesFilter.label(): String = when (this) {
    NotesFilter.ALL -> stringResource(R.string.notes_filter_all)
    NotesFilter.HIGHLIGHTS -> stringResource(R.string.notes_filter_highlights)
    NotesFilter.NOTES -> stringResource(R.string.notes_filter_notes)
    NotesFilter.BOOKMARKS -> stringResource(R.string.notes_filter_bookmarks)
    NotesFilter.UNDERLINES -> stringResource(R.string.notes_filter_underlines)
}

@Composable
private fun AnnotationType.label(): String = when (this) {
    AnnotationType.HIGHLIGHT -> stringResource(R.string.notes_filter_highlights)
    AnnotationType.NOTE -> stringResource(R.string.notes_filter_notes)
    AnnotationType.BOOKMARK -> stringResource(R.string.notes_filter_bookmarks)
    AnnotationType.UNDERLINE -> stringResource(R.string.notes_filter_underlines)
}

@Composable
private fun EditNoteDialog(annotation: Annotation, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var note by remember(annotation.id) { mutableStateOf(annotation.readerNote.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = RoundedCornerShape(Radii.large),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Outlined.EditNote,
                    contentDescription = null,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.notes_edit_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.vayanaAnimateContentSize(),
            ) {
                if (annotation.selectedText.isNotBlank()) {
                    Text(
                        text = annotation.selectedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.notes_edit_label)) },
                    minLines = 3,
                    shape = RoundedCornerShape(Radii.medium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(note) },
                shape = RoundedCornerShape(Radii.full),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Spacing.xs),
                )
                Text(stringResource(R.string.notes_edit_save))
            }
        },
        dismissButton = {
            FilledTonalButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(Radii.full),
            ) {
                Text(stringResource(R.string.settings_reset_all_cancel))
            }
        },
        shape = RoundedCornerShape(Radii.extraLargeIncreased),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.shadowLarge,
    )
}

@Composable
private fun NotesEmptyState(contentPadding: PaddingValues) {
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
                imageVector = Icons.Outlined.EditNote,
                contentDescription = null,
                modifier = Modifier
                    .padding(Spacing.lg)
                    .size(Sizes.iconLarge),
            )
        }
        Text(
            text = stringResource(R.string.notes_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Text(
            text = stringResource(R.string.notes_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

@Composable
private fun NotesNoMatchesState(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(Paddings.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(Radii.large),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier
                    .padding(Spacing.md)
                    .size(Sizes.iconLarge),
            )
        }
        Text(
            text = stringResource(R.string.notes_no_matches_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Text(
            text = stringResource(R.string.notes_no_matches_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

private fun Long.formatDate(): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))

private fun List<BookNotesItem>.filterBooksByQuery(query: String): List<BookNotesItem> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return this
    return filter { item ->
        item.book.title.contains(normalizedQuery, ignoreCase = true) ||
            item.book.author.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
            item.annotations.any { annotation ->
                annotation.selectedText.contains(normalizedQuery, ignoreCase = true) ||
                    annotation.readerNote.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
                    annotation.chapterTitle.orEmpty().contains(normalizedQuery, ignoreCase = true)
            }
    }
}

private fun List<Annotation>.filterByQuery(query: String, filter: NotesFilter): List<Annotation> {
    val normalizedQuery = query.trim()
    return filter { annotation ->
        val matchesFilter = when (filter) {
            NotesFilter.ALL -> true
            NotesFilter.HIGHLIGHTS -> annotation.type == AnnotationType.HIGHLIGHT
            NotesFilter.NOTES -> annotation.type == AnnotationType.NOTE
            NotesFilter.BOOKMARKS -> annotation.type == AnnotationType.BOOKMARK
            NotesFilter.UNDERLINES -> annotation.type == AnnotationType.UNDERLINE
        }
        val matchesQuery = normalizedQuery.isEmpty() ||
            annotation.selectedText.contains(normalizedQuery, ignoreCase = true) ||
            annotation.readerNote.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
            annotation.chapterTitle.orEmpty().contains(normalizedQuery, ignoreCase = true)
        matchesFilter && matchesQuery
    }
}

private fun Context.shareAnnotations(annotations: List<Annotation>) {
    val text = annotations.joinToString(separator = "\n\n") { annotation ->
        buildString {
            append("> ")
            appendLine(annotation.selectedText.ifBlank { getString(R.string.notes_bookmark_without_text) })
            annotation.readerNote?.takeIf { it.isNotBlank() }?.let { note ->
                appendLine()
                appendLine(note)
            }
            annotation.chapterTitle?.let { chapter -> appendLine(chapter) }
        }.trim()
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.notes_export_content_description)))
}

