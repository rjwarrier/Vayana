package com.vayana.feature.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Palette
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LibraryRoute(onBookClick: (Long) -> Unit, onSettingsClick: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val importSummary by viewModel.importSummary.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()

    LibraryScreen(
        modifier = modifier,
        uiState = uiState,
        importSummary = importSummary,
        importProgress = importProgress,
        onImportSummaryShown = viewModel::onImportSummaryShown,
        onImportProgressDismissed = viewModel::onImportProgressDismissed,
        onImportFiles = viewModel::importFiles,
        onImportFolder = viewModel::importFolder,
        onBookClick = onBookClick,
        onSettingsClick = onSettingsClick,
        onQueryChange = viewModel::updateQuery,
        onSortChange = viewModel::updateSort,
        onFilterChange = viewModel::updateFilter,
    )
}

@Composable
fun BookDetailRoute(
    bookId: Long,
    onBack: () -> Unit,
    onContinueReading: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val bookFlow = remember(bookId) { viewModel.observeBook(bookId) }
    val book by bookFlow.collectAsState()

    BookDetailScreen(
        modifier = modifier,
        book = book,
        onBack = onBack,
        onContinueReading = onContinueReading,
        onDeleteBook = {
            viewModel.deleteBook(bookId)
            onBack()
        },
    )
}

@Composable
private fun LibraryScreen(
    modifier: Modifier = Modifier,
    uiState: LibraryUiState,
    importSummary: ImportSummary?,
    importProgress: ImportProgressState?,
    onImportSummaryShown: () -> Unit,
    onImportProgressDismissed: () -> Unit,
    onImportFiles: (android.content.ContentResolver, List<Uri>) -> Unit,
    onImportFolder: (android.content.ContentResolver, Uri) -> Unit,
    onBookClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val importSummaryMessage = importSummary?.let { summary ->
        stringResource(
            R.string.library_import_summary,
            summary.imported,
            summary.duplicates,
            summary.unsupported,
            summary.failed,
        )
    }

    val filesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onImportFiles(context.contentResolver, uris)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onImportFolder(context.contentResolver, uri)
    }

    LaunchedEffect(importSummaryMessage) {
        val message = importSummaryMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onImportSummaryShown()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            LibraryTopBar(
                controls = uiState.controls,
                onSettingsClick = onSettingsClick,
                onQueryChange = onQueryChange,
                onSortChange = onSortChange,
                onFilterChange = onFilterChange,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            LibraryAddFab(
                onImportFiles = { filesPicker.launch(arrayOf("*/*")) },
                onImportFolder = { folderPicker.launch(null) },
            )
        },
    ) { innerPadding ->
        if (uiState.books.isEmpty()) {
            LibraryEmptyState(contentPadding = innerPadding, hasControls = uiState.controls != LibraryControls())
        } else {
            LibraryGrid(books = uiState.books, contentPadding = innerPadding, onBookClick = onBookClick)
        }
    }

    if (importProgress != null) {
        ImportProgressSheet(
            progress = importProgress,
            onDismissRequest = onImportProgressDismissed,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ImportProgressSheet(progress: ImportProgressState, onDismissRequest: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = { if (!progress.isRunning) onDismissRequest() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Paddings.screenHorizontal)
                .padding(bottom = Spacing.lg),
        ) {
            Text(text = stringResource(R.string.library_import_progress_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = progress.summary.label(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            if (progress.isRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = Spacing.md))
            }
            if (progress.rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.library_import_no_files),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            } else {
                LazyColumn(modifier = Modifier.padding(top = Spacing.md)) {
                    items(progress.rows, key = { it.id }) { row ->
                        ImportProgressRow(row = row)
                        HorizontalDivider()
                    }
                }
            }
            if (!progress.isRunning) {
                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md),
                ) {
                    Text(stringResource(R.string.library_import_done))
                }
            }
        }
    }
}

@Composable
private fun ImportProgressRow(row: ImportProgressRow) {
    ListItem(
        headlineContent = {
            Text(
                text = row.fileName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = { Text(row.status.label()) },
        leadingContent = { ImportStatusIcon(row.status) },
    )
}

@Composable
private fun ImportStatusIcon(status: ImportRowStatus) {
    when (status) {
        ImportRowStatus.COPYING,
        ImportRowStatus.PARSING,
        -> CircularProgressIndicator(modifier = Modifier.size(Sizes.icon))
        ImportRowStatus.IMPORTED,
        ImportRowStatus.DUPLICATE,
        -> Icon(Icons.Outlined.TaskAlt, contentDescription = null, modifier = Modifier.size(Sizes.icon))
        ImportRowStatus.UNSUPPORTED,
        ImportRowStatus.FAILED,
        -> Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(Sizes.icon))
        ImportRowStatus.QUEUED -> Icon(Icons.Outlined.HourglassEmpty, contentDescription = null, modifier = Modifier.size(Sizes.icon))
    }
}

@Composable
private fun LibraryTopBar(
    controls: LibraryControls,
    onSettingsClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
) {
    var filterExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Paddings.screenHorizontal, vertical = Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.library_title), style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { filterExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = stringResource(R.string.library_filter_content_description),
                        modifier = Modifier.size(Sizes.icon),
                    )
                }
                DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                    LibrarySort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.label()) },
                            onClick = {
                                filterExpanded = false
                                onSortChange(sort)
                            },
                        )
                    }
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.library_settings_content_description),
                        modifier = Modifier.size(Sizes.icon),
                    )
                }
            }
        }
        OutlinedTextField(
            value = controls.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            LibraryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = controls.filter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter.label()) },
                )
            }
        }
    }
}

@Composable
private fun LibraryAddFab(onImportFiles: () -> Unit, onImportFolder: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column {
        FloatingActionButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Outlined.LibraryAdd,
                contentDescription = stringResource(R.string.library_add_content_description),
            )
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_import_files)) },
                leadingIcon = { Icon(Icons.Outlined.AutoStories, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onImportFiles()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_import_folder)) },
                leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onImportFolder()
                },
            )
        }
    }
}

@Composable
private fun LibraryGrid(books: List<Book>, contentPadding: PaddingValues, onBookClick: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = Sizes.coverWidthMin),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Paddings.screenHorizontal,
            end = Paddings.screenHorizontal,
            top = contentPadding.calculateTopPadding() + Spacing.md,
            bottom = contentPadding.calculateBottomPadding() + Spacing.md,
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        gridItems(books, key = { it.id }) { book -> BookCoverCell(book, onClick = { onBookClick(book.id) }) }
    }
}

@Composable
private fun BookCoverCell(book: Book, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        BookCover(book = book, modifier = Modifier.fillMaxWidth())
        Text(
            text = book.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        if (book.readingPercent > 0f) {
            LinearProgressIndicator(
                progress = { book.readingPercent.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun BookDetailScreen(
    modifier: Modifier = Modifier,
    book: Book?,
    onBack: () -> Unit,
    onContinueReading: (Long) -> Unit,
    onDeleteBook: () -> Unit,
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Paddings.screenHorizontal, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.settings_back_content_description))
                }
                Text(
                    text = stringResource(R.string.library_book_detail_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        },
    ) { innerPadding ->
        if (book == null) {
            Text(
                text = stringResource(R.string.library_book_not_found),
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(Paddings.screenHorizontal),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(Paddings.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        BookCover(
                            book = book,
                            modifier = Modifier
                                .size(width = Sizes.coverWidthMax, height = Sizes.coverWidthMax / Sizes.coverAspectRatio),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.headlineSmall)
                            book.author?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Spacing.xs),
                                )
                            }
                            AssistChip(
                                onClick = {},
                                label = { Text(book.format.name) },
                                modifier = Modifier.padding(top = Spacing.md),
                            )
                        }
                    }
                }
                item {
                    LinearProgressIndicator(progress = { book.readingPercent.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.library_progress_value, (book.readingPercent * 100).roundToInt()),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                val description = book.description
                if (!description.isNullOrBlank()) {
                    item {
                        Text(text = stringResource(R.string.library_description), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
                item {
                    Text(text = stringResource(R.string.library_imported_on, book.createdAt.formatDate()), style = MaterialTheme.typography.bodyMedium)
                    book.lastReadAt?.let {
                        Text(text = stringResource(R.string.library_last_read_on, it.formatDate()), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(
                            onClick = { onContinueReading(book.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                            Text(text = stringResource(R.string.library_continue_reading), modifier = Modifier.padding(start = Spacing.sm))
                        }
                        ElevatedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Edit, contentDescription = null)
                            Text(text = stringResource(R.string.library_edit_metadata), modifier = Modifier.padding(start = Spacing.sm))
                        }
                        ElevatedButton(onClick = { context.shareBookFile(book) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Share, contentDescription = null)
                            Text(text = stringResource(R.string.library_share_file), modifier = Modifier.padding(start = Spacing.sm))
                        }
                        ElevatedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Sync, contentDescription = null)
                            Text(text = stringResource(R.string.library_replace_source_file), modifier = Modifier.padding(start = Spacing.sm))
                        }
                        TextButton(onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Delete, contentDescription = null)
                            Text(text = stringResource(R.string.library_delete_book), modifier = Modifier.padding(start = Spacing.sm))
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(Spacing.xl)) }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteBook()
                    },
                ) { Text(stringResource(R.string.library_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.settings_reset_all_cancel))
                }
            },
            title = { Text(stringResource(R.string.library_delete_title)) },
            text = { Text(stringResource(R.string.library_delete_body)) },
        )
    }
}

@Composable
private fun BookCover(book: Book, modifier: Modifier = Modifier) {
    val coverPath = book.coverPath
    if (coverPath != null) {
        AsyncImage(
            model = File(coverPath),
            contentDescription = stringResource(R.string.library_book_cover_content_description, book.title),
            modifier = modifier
                .aspectRatio(Sizes.coverAspectRatio)
                .clip(RoundedCornerShape(Radii.small)),
            contentScale = ContentScale.Crop,
        )
    } else {
        GeneratedCover(title = book.title, author = book.author, modifier = modifier)
    }
}

@Composable
private fun GeneratedCover(title: String, author: String?, modifier: Modifier = Modifier) {
    val colors = listOf(Palette.Forest700, Palette.Teal700, Palette.Navy500, Palette.Gold700)
    val background = colors[title.hashCode().mod(colors.size)]

    Column(
        modifier = modifier
            .aspectRatio(Sizes.coverAspectRatio)
            .background(background, RoundedCornerShape(Radii.small))
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = Palette.White,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (author != null) {
            Text(
                text = author,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.White.copy(alpha = GeneratedCoverAuthorAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LibraryEmptyState(contentPadding: PaddingValues, hasControls: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(Paddings.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (hasControls) Icons.Outlined.Search else Icons.Outlined.AutoStories,
            contentDescription = null,
            modifier = Modifier.size(Sizes.iconLarge),
        )
        Text(
            text = stringResource(if (hasControls) R.string.library_no_matches_title else R.string.library_empty_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Text(
            text = stringResource(if (hasControls) R.string.library_no_matches_body else R.string.library_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

@Composable
private fun LibrarySort.label(): String = when (this) {
    LibrarySort.IMPORT_DATE -> stringResource(R.string.library_sort_import_date)
    LibrarySort.TITLE -> stringResource(R.string.library_sort_title)
    LibrarySort.AUTHOR -> stringResource(R.string.library_sort_author)
    LibrarySort.LAST_READ -> stringResource(R.string.library_sort_last_read)
    LibrarySort.PROGRESS -> stringResource(R.string.library_sort_progress)
}

@Composable
private fun LibraryFilter.label(): String = when (this) {
    LibraryFilter.ALL -> stringResource(R.string.library_filter_all)
    LibraryFilter.READING -> stringResource(R.string.library_filter_reading)
    LibraryFilter.FINISHED -> stringResource(R.string.library_filter_finished)
    LibraryFilter.NOT_STARTED -> stringResource(R.string.library_filter_not_started)
}

@Composable
private fun ImportSummary.label(): String = stringResource(
    R.string.library_import_summary,
    imported,
    duplicates,
    unsupported,
    failed,
)

@Composable
private fun ImportRowStatus.label(): String = when (this) {
    ImportRowStatus.QUEUED -> stringResource(R.string.library_import_status_queued)
    ImportRowStatus.COPYING -> stringResource(R.string.library_import_status_copying)
    ImportRowStatus.PARSING -> stringResource(R.string.library_import_status_parsing)
    ImportRowStatus.IMPORTED -> stringResource(R.string.library_import_status_imported)
    ImportRowStatus.DUPLICATE -> stringResource(R.string.library_import_status_duplicate)
    ImportRowStatus.UNSUPPORTED -> stringResource(R.string.library_import_status_unsupported)
    ImportRowStatus.FAILED -> stringResource(R.string.library_import_status_failed)
}

private fun android.content.Context.shareBookFile(book: Book) {
    val file = File(book.filePath)
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = book.format.shareMimeType()
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.library_share_file)))
}

private fun BookFormat.shareMimeType(): String = when (this) {
    BookFormat.EPUB -> "application/epub+zip"
    BookFormat.PDF -> "application/pdf"
    BookFormat.TXT -> "text/plain"
    BookFormat.MOBI,
    BookFormat.AZW3,
    BookFormat.FB2,
    -> "application/octet-stream"
}

private fun Long.formatDate(): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))

private const val GeneratedCoverAuthorAlpha = 0.8f
