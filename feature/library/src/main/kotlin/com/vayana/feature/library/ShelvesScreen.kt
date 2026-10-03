package com.vayana.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.vayana.core.designsystem.theme.PagedLazyVerticalGrid
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.Shelf
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.theme.LocalFloatingNavigationInset
import com.vayana.core.resources.R

@Composable
fun ShelvesRoute(
    onBack: () -> Unit,
    onShelfClick: (Long) -> Unit,
    onBookClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()
    val shelfBookCounts by viewModel.shelfBookCounts.collectAsStateWithLifecycle()
    val readNextQueue by viewModel.readNextQueue.collectAsStateWithLifecycle()
    var showSmartShelves by remember { mutableStateOf(false) }

    ShelvesScreen(
        modifier = modifier,
        shelves = shelves,
        shelfBookCounts = shelfBookCounts,
        readNextQueue = readNextQueue,
        onBack = onBack,
        onShelfClick = onShelfClick,
        onBookClick = onBookClick,
        onCreateShelf = viewModel::createShelf,
        onDeleteShelf = viewModel::deleteShelf,
        onRemoveFromReadNext = { bookId -> viewModel.setReadNext(bookId, false) },
        onSmartShelves = { showSmartShelves = true },
    )
    if (showSmartShelves) {
        val smartShelves by viewModel.smartShelfItems.collectAsStateWithLifecycle()
        SmartShelvesDialog(smartShelves, viewModel::saveSmartShelf, viewModel::deleteSmartShelf,
            onBookClick = { book -> showSmartShelves = false; onBookClick(book.id) },
            onDismiss = { showSmartShelves = false })
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ShelvesScreen(
    onSmartShelves: () -> Unit,
    modifier: Modifier = Modifier,
    shelves: List<Shelf>,
    shelfBookCounts: Map<Long, Int>,
    readNextQueue: List<Book>,
    onBack: () -> Unit,
    onShelfClick: (Long) -> Unit,
    onBookClick: (Long) -> Unit,
    onCreateShelf: (String) -> Unit,
    onDeleteShelf: (Long) -> Unit,
    onRemoveFromReadNext: (Long) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var deletingShelf by remember { mutableStateOf<Shelf?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_shelves_title)) },
                // The app shell already applies the status-bar inset to this destination.
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.notes_back_content_description))
                    }
                },
                actions = { androidx.compose.material3.TextButton(onClick = onSmartShelves) { Text(stringResource(R.string.tools_smart_shelves)) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.padding(bottom = LocalFloatingNavigationInset.current),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.library_shelves_create))
            }
        },
    ) { innerPadding ->
        if (shelves.isEmpty() && readNextQueue.isEmpty()) {
            ShelvesEmptyState(contentPadding = innerPadding)
        } else {
            PagedLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Paddings.screenHorizontal,
                    end = Paddings.screenHorizontal,
                    top = innerPadding.calculateTopPadding() + Spacing.sm,
                    bottom = innerPadding.calculateBottomPadding() + Spacing.xl + LocalFloatingNavigationInset.current,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (readNextQueue.isNotEmpty()) {
                    item(key = "read-next-header") {
                        Text(
                            text = stringResource(R.string.library_read_next_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.animateItem(),
                        )
                    }
                    items(readNextQueue, key = { "next-${it.id}" }) { book ->
                        ReadNextRow(
                            book = book,
                            onClick = { onBookClick(book.id) },
                            onRemove = { onRemoveFromReadNext(book.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (shelves.isNotEmpty()) {
                    item(key = "shelves-header") {
                        Text(
                            text = stringResource(R.string.library_shelves_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .animateItem()
                                .padding(top = Spacing.sm),
                        )
                    }
                    items(shelves, key = { it.id }) { shelf ->
                        ShelfRow(
                            shelf = shelf,
                            bookCount = shelfBookCounts[shelf.id] ?: 0,
                            onClick = { onShelfClick(shelf.id) },
                            onDelete = { deletingShelf = shelf },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.library_shelves_create)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.library_shelves_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateShelf(name)
                        showCreateDialog = false
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.library_shelves_create)) }
            },
            dismissButton = {
                FilledTonalButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.settings_reset_all_cancel))
                }
            },
        )
    }

    deletingShelf?.let { shelf ->
        AlertDialog(
            onDismissRequest = { deletingShelf = null },
            title = { Text(stringResource(R.string.library_shelves_delete_title)) },
            text = { Text(stringResource(R.string.library_shelves_delete_body, shelf.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteShelf(shelf.id)
                        deletingShelf = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(R.string.library_delete_confirm)) }
            },
            dismissButton = {
                FilledTonalButton(onClick = { deletingShelf = null }) {
                    Text(stringResource(R.string.settings_reset_all_cancel))
                }
            },
        )
    }
}

@Composable
private fun ShelfRow(
    shelf: Shelf,
    bookCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(Paddings.card),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(Radii.medium),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null, modifier = Modifier.padding(Spacing.md))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shelf.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.library_shelves_book_count, bookCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.library_recently_deleted_purge_content_description),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ReadNextRow(
    book: Book,
    onClick: () -> Unit,
    onRemove: () -> Unit,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(Paddings.card),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(book = book, modifier = Modifier.width(Sizes.coverWidthMin))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                book.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(text = author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.library_read_next_remove), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun ShelfDetailRoute(
    shelfId: Long,
    onBack: () -> Unit,
    onBookClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val shelf by remember(shelfId) { viewModel.observeShelf(shelfId) }.collectAsStateWithLifecycle()
    val books by remember(shelfId) { viewModel.observeBooksForShelf(shelfId) }.collectAsStateWithLifecycle()

    ShelfDetailScreen(
        modifier = modifier,
        title = shelf?.name.orEmpty(),
        books = books,
        onBack = onBack,
        onBookClick = onBookClick,
        onRemoveFromShelf = { bookId -> viewModel.removeBookFromShelf(bookId, shelfId) },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ShelfDetailScreen(
    modifier: Modifier = Modifier,
    title: String,
    books: List<Book>,
    onBack: () -> Unit,
    onBookClick: (Long) -> Unit,
    onRemoveFromShelf: (Long) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.notes_back_content_description))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (books.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.library_shelves_empty_shelf),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            PagedLazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = Sizes.coverWidthMin),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Paddings.screenHorizontal,
                    end = Paddings.screenHorizontal,
                    top = innerPadding.calculateTopPadding() + Spacing.sm,
                    bottom = innerPadding.calculateBottomPadding() + Spacing.xl + LocalFloatingNavigationInset.current,
                ),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(books, key = { it.id }) { book ->
                    Column(
                        modifier = Modifier.clickable { onBookClick(book.id) },
                    ) {
                        BookCover(book = book, modifier = Modifier.fillMaxWidth())
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                        IconButton(onClick = { onRemoveFromShelf(book.id) }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.library_shelves_remove_book),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelvesEmptyState(contentPadding: PaddingValues) {
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
                imageVector = Icons.Outlined.CollectionsBookmark,
                contentDescription = null,
                modifier = Modifier.padding(Spacing.lg).size(Sizes.iconLarge),
            )
        }
        Text(
            text = stringResource(R.string.library_shelves_empty_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Text(
            text = stringResource(R.string.library_shelves_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}
