package com.vayana.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.vayana.core.designsystem.theme.PagedLazyColumn
import com.vayana.core.designsystem.theme.VayanaSnackbarHost
import com.vayana.core.database.model.Book
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

@Composable
fun RecentlyDeletedRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val deletedBooks by viewModel.recentlyDeletedBooks.collectAsStateWithLifecycle()
    val deletionNotice by viewModel.permanentDeletionNotice.collectAsStateWithLifecycle()

    RecentlyDeletedScreen(
        modifier = modifier,
        deletedBooks = deletedBooks,
        onBack = onBack,
        onRestore = viewModel::restoreBook,
        onPurge = viewModel::deletePermanently,
        deletionNotice = deletionNotice,
        onDeletionNoticeShown = viewModel::consumePermanentDeletionNotice,
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RecentlyDeletedScreen(
    modifier: Modifier = Modifier,
    deletedBooks: List<Book>,
    onBack: () -> Unit,
    onRestore: (Long) -> Unit,
    onPurge: (Long) -> Unit,
    deletionNotice: PermanentDeletionNotice?,
    onDeletionNoticeShown: (PermanentDeletionNotice) -> Unit,
) {
    var purgingBook by remember { mutableStateOf<Book?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    PermanentDeletionNoticeEffect(deletionNotice, snackbarHostState, onDeletionNoticeShown)

    Scaffold(
        modifier = modifier,
        snackbarHost = { VayanaSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_recently_deleted_title)) },
                // The app shell already applies the status-bar inset to this destination.
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.notes_back_content_description),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (deletedBooks.isEmpty()) {
            RecentlyDeletedEmptyState(contentPadding = innerPadding)
        } else {
            PagedLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Paddings.screenHorizontal,
                    end = Paddings.screenHorizontal,
                    top = innerPadding.calculateTopPadding() + Spacing.sm,
                    bottom = innerPadding.calculateBottomPadding() + Spacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(deletedBooks, key = { it.id }) { book ->
                    DeletedBookCard(
                        book = book,
                        onRestore = { onRestore(book.id) },
                        onPurge = { purgingBook = book },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    purgingBook?.let { book ->
        PermanentDeleteConfirmDialog(
            book = book,
            highlightCount = null,
            onDismissRequest = { purgingBook = null },
            onConfirm = {
                purgingBook = null
                onPurge(book.id)
            },
        )
    }
}

@Composable
private fun DeletedBookCard(
    book: Book,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.largeIncreased)),
        shape = RoundedCornerShape(Radii.largeIncreased),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.shadowSmall,
    ) {
        Row(
            modifier = Modifier.padding(Paddings.card),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(book = book, modifier = Modifier.width(Sizes.coverWidthMin))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                book.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(onClick = onRestore) {
                Icon(
                    imageVector = Icons.Outlined.RestoreFromTrash,
                    contentDescription = stringResource(R.string.library_recently_deleted_restore),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onPurge) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.library_recently_deleted_purge_content_description),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RecentlyDeletedEmptyState(contentPadding: PaddingValues) {
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
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(
                imageVector = Icons.Outlined.RestoreFromTrash,
                contentDescription = null,
                modifier = Modifier
                    .padding(Spacing.lg)
                    .size(Sizes.iconLarge),
            )
        }
        Text(
            text = stringResource(R.string.library_recently_deleted_empty_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Text(
            text = stringResource(R.string.library_recently_deleted_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}
