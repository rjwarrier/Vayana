package com.vayana.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.vayana.core.database.model.Annotation
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.text.DateFormat
import java.util.Date

@Composable
fun NotesRoute(modifier: Modifier = Modifier) {
    val viewModel: NotesViewModel = hiltViewModel()
    val annotations by viewModel.annotations.collectAsState()

    NotesScreen(modifier = modifier, annotations = annotations)
}

@Composable
private fun NotesScreen(modifier: Modifier = Modifier, annotations: List<Annotation>) {
    Scaffold(
        modifier = modifier,
        topBar = {
            Text(
                text = stringResource(R.string.notes_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(Paddings.screenHorizontal),
            )
        },
    ) { innerPadding ->
        if (annotations.isEmpty()) {
            NotesEmptyState(contentPadding = innerPadding)
        } else {
            NotesList(contentPadding = innerPadding, annotations = annotations)
        }
    }
}

@Composable
private fun NotesList(contentPadding: PaddingValues, annotations: List<Annotation>) {
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
            AnnotationRow(annotation = annotation)
        }
    }
}

@Composable
private fun AnnotationRow(annotation: Annotation) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        headlineContent = {
            Text(
                text = annotation.selectedText.ifBlank { stringResource(R.string.notes_bookmark_without_text) },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                annotation.readerNote?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = note,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = annotation.chapterTitle ?: annotation.updatedAt.formatDate(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.EditNote,
                contentDescription = null,
                modifier = Modifier.size(Sizes.icon),
            )
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

private fun Long.formatDate(): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))
