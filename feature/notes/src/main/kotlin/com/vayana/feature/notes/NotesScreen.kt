package com.vayana.feature.notes

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.text.DateFormat
import java.util.Date

@Composable
fun NotesRoute(modifier: Modifier = Modifier) {
    val viewModel: NotesViewModel = hiltViewModel()
    val annotations by viewModel.annotations.collectAsState()

    NotesScreen(
        modifier = modifier,
        annotations = annotations,
        onUpdateNote = viewModel::updateNote,
        onDeleteAnnotation = viewModel::deleteAnnotation,
    )
}

@Composable
private fun NotesScreen(
    modifier: Modifier = Modifier,
    annotations: List<Annotation>,
    onUpdateNote: (Annotation, String) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(NotesFilter.ALL) }
    var editingAnnotation by remember { mutableStateOf<Annotation?>(null) }
    var deletingAnnotation by remember { mutableStateOf<Annotation?>(null) }
    val visibleAnnotations = remember(annotations, query, filter) { annotations.filterByQuery(query, filter) }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column(modifier = Modifier.padding(Paddings.screenHorizontal)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.notes_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    if (annotations.isNotEmpty()) {
                        IconButton(onClick = { context.shareAnnotations(annotations) }) {
                            Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.notes_export_content_description))
                        }
                    }
                }
                if (annotations.isNotEmpty()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(R.string.notes_search_placeholder)) },
                    )
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(top = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        NotesFilter.entries.forEach { option ->
                            FilterChip(
                                selected = filter == option,
                                onClick = { filter = option },
                                label = { Text(option.label()) },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        if (annotations.isEmpty()) {
            NotesEmptyState(contentPadding = innerPadding)
        } else if (visibleAnnotations.isEmpty()) {
            NotesNoMatchesState(contentPadding = innerPadding)
        } else {
            NotesList(
                contentPadding = innerPadding,
                annotations = visibleAnnotations,
                onEdit = { editingAnnotation = it },
                onDelete = { deletingAnnotation = it },
            )
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
            title = { Text(stringResource(R.string.notes_delete_title)) },
            text = { Text(stringResource(R.string.notes_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingAnnotation = null
                        onDeleteAnnotation(annotation.id)
                    },
                ) {
                    Text(stringResource(R.string.notes_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAnnotation = null }) {
                    Text(stringResource(R.string.settings_reset_all_cancel))
                }
            },
        )
    }
}

@Composable
private fun NotesList(
    contentPadding: PaddingValues,
    annotations: List<Annotation>,
    onEdit: (Annotation) -> Unit,
    onDelete: (Annotation) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Paddings.screenHorizontal,
            end = Paddings.screenHorizontal,
            top = contentPadding.calculateTopPadding() + Spacing.md,
            bottom = contentPadding.calculateBottomPadding() + Spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(annotations, key = { it.id }) { annotation ->
            AnnotationCard(
                annotation = annotation,
                onEdit = { onEdit(annotation) },
                onDelete = { onDelete(annotation) },
            )
        }
    }
}

@Composable
private fun AnnotationCard(annotation: Annotation, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(Paddings.card)) {
            Text(
                text = annotation.selectedText.ifBlank { stringResource(R.string.notes_bookmark_without_text) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            annotation.readerNote?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.heightIn(min = Sizes.icon)) {
                    Text(
                        text = annotation.type.label(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = annotation.chapterTitle ?: annotation.updatedAt.formatDate(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.notes_edit_content_description),
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.notes_delete_content_description),
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
        title = { Text(stringResource(R.string.notes_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = annotation.selectedText.ifBlank { stringResource(R.string.notes_bookmark_without_text) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.notes_edit_label)) },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note) }) {
                Text(stringResource(R.string.notes_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_reset_all_cancel))
            }
        },
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
        Icon(
            imageVector = Icons.Outlined.EditNote,
            contentDescription = null,
            modifier = Modifier.size(Sizes.iconLarge),
        )
        Text(
            text = stringResource(R.string.notes_empty_title),
            style = MaterialTheme.typography.titleLarge,
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
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(Sizes.iconLarge),
        )
        Text(
            text = stringResource(R.string.notes_no_matches_title),
            style = MaterialTheme.typography.titleLarge,
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
