package com.vayana.feature.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.core.text.HtmlCompat
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.vayana.core.common.QuoteParser
import com.vayana.core.common.shareText as shareTextWithChooser
import com.vayana.core.designsystem.sharecard.BookShareCard
import com.vayana.core.designsystem.sharecard.ShareCardDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import com.vayana.core.designsystem.theme.vayanaAnimateContentSize
import com.vayana.core.designsystem.tokens.Elevations
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
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LibraryRoute(
    onBookClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onRecentlyDeletedClick: () -> Unit,
    onShelvesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val importSummary by viewModel.importSummary.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()

    LibraryScreen(
        modifier = modifier,
        uiState = uiState,
        importSummary = importSummary,
        importProgress = importProgress,
        syncProgress = syncProgress,
        onImportSummaryShown = viewModel::onImportSummaryShown,
        onImportProgressDismissed = viewModel::onImportProgressDismissed,
        onSyncProgressDismissed = viewModel::onSyncProgressDismissed,
        onImportFiles = viewModel::importFiles,
        onImportFolder = viewModel::importFolder,
        onAddPhysicalBook = { title, author -> viewModel.addPhysicalBook(title, author, onCreated = onBookClick) },
        onBookClick = onBookClick,
        onDownloadCloudBook = viewModel::downloadCloudBook,
        onSyncNow = viewModel::syncNow,
        onSettingsClick = onSettingsClick,
        onRecentlyDeletedClick = onRecentlyDeletedClick,
        onShelvesClick = onShelvesClick,
        onQueryChange = viewModel::updateQuery,
        onSortChange = viewModel::updateSort,
        onFilterChange = viewModel::updateFilter,
        onGroupByChange = viewModel::updateGroupBy,
    )
}

@Composable
fun BookDetailRoute(
    bookId: Long,
    onBack: () -> Unit,
    onContinueReading: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = hiltViewModel()
    val bookFlow = remember(bookId) { viewModel.observeBook(bookId) }
    val book by bookFlow.collectAsState()
    val libraryBooks by viewModel.libraryBooks.collectAsState()
    val detailMessage by viewModel.bookDetailMessage.collectAsState()
    val allShelves by viewModel.shelves.collectAsState()
    val shelvesForBook by remember(bookId) { viewModel.observeShelvesForBook(bookId) }.collectAsState()

    BookDetailScreen(
        modifier = modifier,
        book = book,
        libraryBooks = libraryBooks,
        detailMessage = detailMessage,
        allShelves = allShelves,
        shelvesForBook = shelvesForBook,
        onCreateShelf = viewModel::createShelf,
        onAddToShelf = { shelfId -> viewModel.addBookToShelf(bookId, shelfId) },
        onRemoveFromShelf = { shelfId -> viewModel.removeBookFromShelf(bookId, shelfId) },
        onSetReadNext = { queued -> viewModel.setReadNext(bookId, queued) },
        onBack = onBack,
        onContinueReading = onContinueReading,
        onUpdateMetadata = { title, author, series, seriesNumber, description ->
            viewModel.updateMetadata(bookId, title, author, series, seriesNumber, description)
        },
        onReplaceSource = { contentResolver, uri ->
            viewModel.replaceSource(bookId, contentResolver, uri)
        },
        onReplaceCover = { contentResolver, uri ->
            viewModel.replaceCover(bookId, contentResolver, uri)
        },
        onRemoveCover = {
            viewModel.removeCover(bookId)
        },
        onRemoveFromDevice = {
            viewModel.removeBookFromDevice(bookId)
        },
        onImportQuotes = { text ->
            viewModel.importQuotes(bookId, text)
        },
        onImportQuotesFile = { uri ->
            viewModel.importQuotesFromFile(bookId, context.contentResolver, uri)
        },
        onDetailMessageShown = viewModel::onBookDetailMessageShown,
        onMarkFinished = {
            viewModel.markFinished(bookId)
        },
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
    syncProgress: GitHubSyncProgressState?,
    onImportSummaryShown: () -> Unit,
    onImportProgressDismissed: () -> Unit,
    onSyncProgressDismissed: () -> Unit,
    onImportFiles: (android.content.ContentResolver, List<Uri>) -> Unit,
    onImportFolder: (android.content.ContentResolver, Uri) -> Unit,
    onAddPhysicalBook: (String, String?) -> Unit,
    onBookClick: (Long) -> Unit,
    onDownloadCloudBook: suspend (Book) -> CloudBookDownloadResult,
    onSyncNow: suspend (Boolean) -> GitHubSyncNowResult,
    onSettingsClick: () -> Unit,
    onRecentlyDeletedClick: () -> Unit,
    onShelvesClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onGroupByChange: (LibraryGroupBy) -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
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
    var showAddPhysicalBookDialog by remember { mutableStateOf(false) }
    var syncRunning by remember { mutableStateOf(false) }
    var initialSyncConfirmationMessage by remember { mutableStateOf<String?>(null) }
    val cloudDownloadStartedMessage = stringResource(R.string.library_book_cloud_download_started)
    val cloudDownloadCompleteMessage = stringResource(R.string.library_book_cloud_download_complete)
    val cloudSyncDisabledMessage = stringResource(R.string.library_book_cloud_sync_disabled)
    val cloudSyncConfigMissingMessage = stringResource(R.string.library_book_cloud_sync_config_missing)
    val cloudAssetMissingMessage = stringResource(R.string.library_book_cloud_asset_missing)
    val cloudDownloadFailedMessage = stringResource(R.string.library_book_cloud_download_failed)
    val missingFileMessage = stringResource(R.string.library_book_file_missing)
    val uploadPendingMessage = stringResource(R.string.library_book_upload_pending)
    val syncStartedMessage = stringResource(R.string.library_sync_started)
    val syncDisabledMessage = stringResource(R.string.library_sync_disabled)
    val syncConfigMissingMessage = stringResource(R.string.library_sync_config_missing)
    val syncCompleteMessage = stringResource(R.string.library_sync_complete)
    val syncCompleteWithCloudMessage = stringResource(R.string.library_sync_complete_with_cloud)
    val syncPartialMessage = stringResource(R.string.library_sync_partial)
    val syncConflictMessage = stringResource(R.string.library_sync_conflicts)
    val syncPullFailedMessage = stringResource(R.string.library_sync_pull_failed)
    val syncMetadataFailedMessage = stringResource(R.string.library_sync_metadata_failed)
    val initialSyncConfirmationTitle = stringResource(R.string.library_sync_initial_confirm_title)
    val initialSyncConfirmationBody = stringResource(R.string.library_sync_initial_confirm_body)
    val initialSyncConfirmationDetail = stringResource(R.string.library_sync_initial_confirm_detail)
    val initialSyncConfirm = stringResource(R.string.library_sync_initial_confirm_yes)
    val initialSyncCancel = stringResource(R.string.library_sync_initial_confirm_no)

    fun handleBookClick(book: Book) {
        when (book.fileAvailability) {
            BookFileAvailability.LOCAL -> onBookClick(book.id)
            BookFileAvailability.CLOUD_ONLY -> coroutineScope.launch {
                snackbarHostState.showSnackbar(cloudDownloadStartedMessage)
                when (onDownloadCloudBook(book)) {
                    CloudBookDownloadResult.DOWNLOADED -> {
                        snackbarHostState.showSnackbar(cloudDownloadCompleteMessage)
                        onBookClick(book.id)
                    }
                    CloudBookDownloadResult.SYNC_DISABLED -> snackbarHostState.showSnackbar(cloudSyncDisabledMessage)
                    CloudBookDownloadResult.CONFIG_INCOMPLETE -> snackbarHostState.showSnackbar(cloudSyncConfigMissingMessage)
                    CloudBookDownloadResult.ASSET_MISSING -> snackbarHostState.showSnackbar(cloudAssetMissingMessage)
                    CloudBookDownloadResult.FAILED -> snackbarHostState.showSnackbar(cloudDownloadFailedMessage)
                }
            }
            BookFileAvailability.MISSING -> coroutineScope.launch { snackbarHostState.showSnackbar(missingFileMessage) }
            BookFileAvailability.UPLOAD_PENDING -> coroutineScope.launch { snackbarHostState.showSnackbar(uploadPendingMessage) }
        }
    }

    suspend fun runSyncNow(allowInitialSync: Boolean) {
        snackbarHostState.currentSnackbarData?.dismiss()
        val startedSnackbar = coroutineScope.launch {
            snackbarHostState.showSnackbar(syncStartedMessage)
        }
        when (val result = onSyncNow(allowInitialSync)) {
            is GitHubSyncNowResult.Complete -> {
                startedSnackbar.cancel()
                snackbarHostState.currentSnackbarData?.dismiss()
                val message = when {
                    result.pullFailed -> syncPullFailedMessage.format(result.failureMessage.orEmpty())
                    !result.metadataSynced -> syncMetadataFailedMessage.format(
                        result.uploaded,
                        result.failed,
                        result.failureMessage.orEmpty(),
                    )
                    result.conflicts > 0 -> syncConflictMessage.format(result.uploaded, result.progressUpdated, result.conflicts)
                    result.failed == 0 && (result.cloudBooksCreated > 0 || result.cloudBooksUpdated > 0) -> syncCompleteWithCloudMessage.format(
                        result.cloudBooksCreated,
                        result.cloudBooksUpdated,
                        result.uploaded,
                        result.progressUpdated,
                    )
                    result.failed == 0 -> syncCompleteMessage.format(result.uploaded, result.progressUpdated)
                    else -> syncPartialMessage.format(result.uploaded, result.failed, result.progressUpdated)
                }
                snackbarHostState.showSnackbar(message)
            }
            is GitHubSyncNowResult.InitialSyncConfirmationRequired -> {
                startedSnackbar.cancel()
                snackbarHostState.currentSnackbarData?.dismiss()
                initialSyncConfirmationMessage = result.message
            }
            GitHubSyncNowResult.SyncDisabled -> {
                startedSnackbar.cancel()
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(syncDisabledMessage)
            }
            GitHubSyncNowResult.ConfigIncomplete -> {
                startedSnackbar.cancel()
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(syncConfigMissingMessage)
            }
        }
    }

    fun handleSyncNow(allowInitialSync: Boolean = false) {
        if (syncRunning) return
        coroutineScope.launch {
            syncRunning = true
            try {
                runSyncNow(allowInitialSync)
            } finally {
                syncRunning = false
            }
        }
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
                showSyncNow = uiState.githubSyncReady,
                syncRunning = syncRunning,
                onSyncNow = ::handleSyncNow,
                onSettingsClick = onSettingsClick,
                onRecentlyDeletedClick = onRecentlyDeletedClick,
                onShelvesClick = onShelvesClick,
                onQueryChange = onQueryChange,
                onSortChange = onSortChange,
                onFilterChange = onFilterChange,
                onGroupByChange = onGroupByChange,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            LibraryAddFab(
                onImportFiles = { filesPicker.launch(arrayOf("*/*")) },
                onImportFolder = { folderPicker.launch(null) },
                onAddPhysicalBook = { showAddPhysicalBookDialog = true },
            )
        },
    ) { innerPadding ->
        if (uiState.books.isEmpty()) {
            LibraryEmptyState(contentPadding = innerPadding, hasControls = uiState.controls != LibraryControls())
        } else {
            LibraryGrid(
                books = uiState.books,
                groupBy = uiState.controls.groupBy,
                contentPadding = innerPadding,
                onBookClick = ::handleBookClick,
            )
        }
    }

    if (importProgress != null) {
        ImportProgressSheet(
            progress = importProgress,
            onDismissRequest = onImportProgressDismissed,
        )
    }

    if (syncProgress != null) {
        GitHubSyncProgressSheet(
            progress = syncProgress,
            onDismissRequest = onSyncProgressDismissed,
        )
    }

    if (showAddPhysicalBookDialog) {
        AddPhysicalBookDialog(
            onDismiss = { showAddPhysicalBookDialog = false },
            onConfirm = { title, author ->
                onAddPhysicalBook(title, author)
                showAddPhysicalBookDialog = false
            },
        )
    }

    initialSyncConfirmationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { initialSyncConfirmationMessage = null },
            title = { Text(initialSyncConfirmationTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(initialSyncConfirmationBody)
                    if (message.isNotBlank()) {
                        Text(
                            text = initialSyncConfirmationDetail.format(message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        initialSyncConfirmationMessage = null
                        handleSyncNow(allowInitialSync = true)
                    },
                ) {
                    Text(initialSyncConfirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { initialSyncConfirmationMessage = null }) {
                    Text(initialSyncCancel)
                }
            },
            shape = RoundedCornerShape(Radii.extraLargeIncreased),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun AddPhysicalBookDialog(onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_add_physical_book_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = stringResource(R.string.library_add_physical_book_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.library_add_physical_book_book_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(stringResource(R.string.library_add_physical_book_author)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title.trim(), author.trim().takeIf { it.isNotBlank() }) },
                enabled = title.isNotBlank(),
            ) {
                Text(stringResource(R.string.library_add_physical_book_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_reset_all_cancel))
            }
        },
        shape = RoundedCornerShape(Radii.extraLargeIncreased),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GitHubSyncProgressSheet(progress: GitHubSyncProgressState, onDismissRequest: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rows = listOf(
        GitHubSyncProgressStep.PREPARING,
        GitHubSyncProgressStep.READING_CLOUD,
        GitHubSyncProgressStep.ADDING_CLOUD_BOOKS,
        GitHubSyncProgressStep.UPLOADING_BOOKS,
        GitHubSyncProgressStep.SAVING_SNAPSHOT,
    )

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
            Text(text = stringResource(R.string.library_sync_progress_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = progress.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md),
                strokeCap = StrokeCap.Round,
            )
            Text(
                text = stringResource(
                    R.string.library_sync_progress_summary,
                    progress.cloudBooksCreated,
                    progress.cloudBooksUpdated,
                    progress.uploadedBooks,
                    progress.failedBooks,
                    progress.progressUpdated,
                    progress.uploadedCovers,
                    progress.downloadedCovers,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            LazyColumn(modifier = Modifier.padding(top = Spacing.md)) {
                items(rows, key = { it.name }) { step ->
                    GitHubSyncProgressRow(
                        label = step.label(),
                        status = progress.statusFor(step),
                    )
                    HorizontalDivider()
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
private fun GitHubSyncProgressRow(label: String, status: GitHubSyncStepStatus) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(status.label()) },
        leadingContent = { GitHubSyncStepIcon(status) },
    )
}

@Composable
private fun GitHubSyncStepIcon(status: GitHubSyncStepStatus) {
    when (status) {
        GitHubSyncStepStatus.RUNNING -> CircularProgressIndicator(modifier = Modifier.size(Sizes.icon))
        GitHubSyncStepStatus.DONE -> Icon(Icons.Outlined.TaskAlt, contentDescription = null, modifier = Modifier.size(Sizes.icon))
        GitHubSyncStepStatus.FAILED -> Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(Sizes.icon))
        GitHubSyncStepStatus.WAITING -> Icon(Icons.Outlined.HourglassEmpty, contentDescription = null, modifier = Modifier.size(Sizes.icon))
    }
}

@Composable
private fun LibraryTopBar(
    controls: LibraryControls,
    showSyncNow: Boolean,
    syncRunning: Boolean,
    onSyncNow: () -> Unit,
    onSettingsClick: () -> Unit,
    onRecentlyDeletedClick: () -> Unit,
    onShelvesClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onGroupByChange: (LibraryGroupBy) -> Unit,
) {
    var filterExpanded by remember { mutableStateOf(false) }
    var groupExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Paddings.screenHorizontal, vertical = Spacing.md)
            .vayanaAnimateContentSize(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showSyncNow) {
                    IconButton(
                        onClick = onSyncNow,
                        enabled = !syncRunning,
                    ) {
                        if (syncRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(Sizes.icon))
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = stringResource(R.string.library_sync_now_content_description),
                                modifier = Modifier.size(Sizes.icon),
                            )
                        }
                    }
                }
                Text(text = stringResource(R.string.library_title), style = MaterialTheme.typography.headlineMedium)
            }
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
                IconButton(onClick = { groupExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Category,
                        contentDescription = stringResource(R.string.library_group_content_description),
                        modifier = Modifier.size(Sizes.icon),
                    )
                }
                DropdownMenu(expanded = groupExpanded, onDismissRequest = { groupExpanded = false }) {
                    LibraryGroupBy.entries.forEach { groupBy ->
                        DropdownMenuItem(
                            text = { Text(groupBy.label()) },
                            onClick = {
                                groupExpanded = false
                                onGroupByChange(groupBy)
                            },
                        )
                    }
                }
                IconButton(onClick = onShelvesClick) {
                    Icon(
                        imageVector = Icons.Outlined.CollectionsBookmark,
                        contentDescription = stringResource(R.string.library_shelves_content_description),
                        modifier = Modifier.size(Sizes.icon),
                    )
                }
                IconButton(onClick = onRecentlyDeletedClick) {
                    Icon(
                        imageVector = Icons.Outlined.RestoreFromTrash,
                        contentDescription = stringResource(R.string.library_recently_deleted_content_description),
                        modifier = Modifier.size(Sizes.icon),
                    )
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
        val focusManager = LocalFocusManager.current
        OutlinedTextField(
            value = controls.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.touchTargetEink)
                .padding(top = Spacing.md),
            singleLine = true,
            shape = RoundedCornerShape(Radii.full),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = SearchFieldUnfocusedBorderAlpha),
                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (controls.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.input_clear_content_description),
                        )
                    }
                }
            },
            placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
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
private fun LibraryAddFab(onImportFiles: () -> Unit, onImportFolder: () -> Unit, onAddPhysicalBook: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column {
        FloatingActionButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.size(Sizes.fabLarge),
            shape = RoundedCornerShape(Radii.largeIncreased),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                defaultElevation = Elevations.shadowLarge,
                pressedElevation = Elevations.shadowLarge,
                focusedElevation = Elevations.shadowMedium,
                hoveredElevation = Elevations.shadowMedium,
            ),
        ) {
            Icon(
                imageVector = Icons.Outlined.LibraryAdd,
                contentDescription = stringResource(R.string.library_add_content_description),
                modifier = Modifier.size(Sizes.iconLarge),
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.widthIn(min = Sizes.menuMinWidth),
            shape = RoundedCornerShape(Radii.largeIncreased),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = Elevations.shadowLarge,
            shadowElevation = Elevations.shadowMedium,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_import_files)) },
                leadingIcon = { LibraryAddMenuIcon(Icons.Outlined.AutoStories) },
                modifier = Modifier.heightIn(min = Sizes.menuItemLargeHeight),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                onClick = {
                    menuExpanded = false
                    onImportFiles()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_import_folder)) },
                leadingIcon = { LibraryAddMenuIcon(Icons.Outlined.CreateNewFolder) },
                modifier = Modifier.heightIn(min = Sizes.menuItemLargeHeight),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                onClick = {
                    menuExpanded = false
                    onImportFolder()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_add_physical_book)) },
                leadingIcon = { LibraryAddMenuIcon(Icons.AutoMirrored.Outlined.MenuBook) },
                modifier = Modifier.heightIn(min = Sizes.menuItemLargeHeight),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                onClick = {
                    menuExpanded = false
                    onAddPhysicalBook()
                },
            )
        }
    }
}

@Composable
private fun LibraryAddMenuIcon(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(Sizes.touchTarget),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(Sizes.icon))
        }
    }
}

private data class LibraryGroupSection(val label: String, val books: List<Book>)

@Composable
private fun LibraryGrid(
    books: List<Book>,
    groupBy: LibraryGroupBy,
    contentPadding: PaddingValues,
    onBookClick: (Book) -> Unit,
) {
    val lastOpenedBook = remember(books) {
        books.filter { (it.lastReadAt ?: 0L) > 0L }
            .maxByOrNull { it.lastReadAt ?: 0L }
    }
    val sections = books.toGroupSections(groupBy)

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
        if (lastOpenedBook != null) {
            item(key = "hero:${lastOpenedBook.id}", span = { GridItemSpan(maxLineSpan) }) {
                LibraryHeroCard(
                    book = lastOpenedBook,
                    onClick = { onBookClick(lastOpenedBook) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        if (sections == null) {
            val gridBooks = if (lastOpenedBook != null) books.filter { it.id != lastOpenedBook.id } else books
            gridItems(gridBooks, key = { it.id }) { book ->
                BookCoverCell(book, modifier = Modifier.animateItem(), onClick = { onBookClick(book) })
            }
        } else {
            sections.forEach { section ->
                val sectionBooks = if (lastOpenedBook != null) section.books.filter { it.id != lastOpenedBook.id } else section.books
                if (sectionBooks.isNotEmpty()) {
                    item(key = "header:${section.label}", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = section.label,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .padding(top = Spacing.sm, bottom = Spacing.xs)
                                .animateItem(),
                        )
                    }
                    gridItems(sectionBooks, key = { it.id }) { book ->
                        BookCoverCell(book, modifier = Modifier.animateItem(), onClick = { onBookClick(book) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeroCard(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radii.extraLargeIncreased),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.shadowSmall,
    ) {
        Row(
            modifier = Modifier.padding(Paddings.card),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                book = book,
                modifier = Modifier
                    .width(Sizes.coverWidthMin)
                    .clip(RoundedCornerShape(Radii.small)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Surface(
                    shape = RoundedCornerShape(Radii.full),
                    color = if (book.fileAvailability == BookFileAvailability.CLOUD_ONLY) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (book.fileAvailability == BookFileAvailability.CLOUD_ONLY) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Icon(
                            imageVector = if (book.fileAvailability == BookFileAvailability.CLOUD_ONLY) Icons.Outlined.CloudDownload else Icons.Outlined.AutoStories,
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.iconSmall),
                        )
                        Text(
                            text = if (book.fileAvailability == BookFileAvailability.CLOUD_ONLY) {
                                stringResource(R.string.library_cloud_book_badge)
                            } else {
                                stringResource(R.string.library_continue_reading)
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                book.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (book.readingPercent > 0f) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        LinearProgressIndicator(
                            progress = { book.readingPercent.coerceIn(0f, 1f) },
                            modifier = Modifier.weight(1f),
                            strokeCap = StrokeCap.Round,
                        )
                        Text(
                            text = "${(book.readingPercent * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                book.lastReadAt?.let { timestamp ->
                    Text(
                        text = stringResource(R.string.library_last_read_on, timestamp.formatDate()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }
    }
}

/** Null when [mode] is [LibraryGroupBy.NONE] — callers fall back to the flat, ungrouped grid. */
@Composable
private fun List<Book>.toGroupSections(mode: LibraryGroupBy): List<LibraryGroupSection>? {
    if (mode == LibraryGroupBy.NONE) return null
    val unknownLabel = when (mode) {
        LibraryGroupBy.AUTHOR -> stringResource(R.string.library_group_unknown_author)
        LibraryGroupBy.SERIES -> stringResource(R.string.library_group_unknown_series)
        LibraryGroupBy.NONE -> ""
    }
    return remember(this, mode, unknownLabel) {
        val keyOf: (Book) -> String? = when (mode) {
            LibraryGroupBy.AUTHOR -> { book -> book.author }
            LibraryGroupBy.SERIES -> { book -> book.series }
            LibraryGroupBy.NONE -> { _ -> null }
        }
        groupBy { book -> keyOf(book)?.trim()?.takeIf(String::isNotBlank) }
            .entries
            .sortedWith(compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.key })
            .map { (key, groupBooks) ->
                LibraryGroupSection(
                    label = key ?: unknownLabel,
                    books = if (mode == LibraryGroupBy.SERIES) groupBooks.sortedBySeriesNumber() else groupBooks,
                )
            }
    }
}

private fun List<Book>.sortedBySeriesNumber(): List<Book> = sortedWith(
    compareBy<Book, Double?>(nullsLast()) { it.seriesNumber?.toDoubleOrNull() }.thenBy { it.title.lowercase() },
)

@Composable
private fun BookCoverCell(
    book: Book,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
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
        if (book.fileAvailability == BookFileAvailability.CLOUD_ONLY) {
            Row(
                modifier = Modifier.padding(top = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.iconSmall),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.library_cloud_book_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BookDetailScreen(
    modifier: Modifier = Modifier,
    book: Book?,
    libraryBooks: List<Book>,
    detailMessage: BookDetailMessage?,
    allShelves: List<com.vayana.core.database.model.Shelf>,
    shelvesForBook: List<com.vayana.core.database.model.Shelf>,
    onCreateShelf: (String) -> Unit,
    onAddToShelf: (Long) -> Unit,
    onRemoveFromShelf: (Long) -> Unit,
    onSetReadNext: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinueReading: (Long) -> Unit,
    onUpdateMetadata: (String, String, String, String, String) -> Unit,
    onReplaceSource: (android.content.ContentResolver, Uri) -> Unit,
    onReplaceCover: (android.content.ContentResolver, Uri) -> Unit,
    onRemoveCover: () -> Unit,
    onRemoveFromDevice: () -> Unit,
    onImportQuotes: (String) -> Unit,
    onImportQuotesFile: (Uri) -> Unit,
    onDetailMessageShown: () -> Unit,
    onMarkFinished: () -> Unit,
    onDeleteBook: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showEditDescriptionDialog by remember { mutableStateOf(false) }
    var showCoverPreview by remember { mutableStateOf(false) }
    var showImportQuotesDialog by remember { mutableStateOf(false) }
    var showShareBookDialog by remember { mutableStateOf(false) }
    var showRemoveFromDeviceDialog by remember { mutableStateOf(false) }
    val detailMessageText = detailMessage?.label()
    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onReplaceSource(context.contentResolver, uri)
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onReplaceCover(context.contentResolver, uri)
    }
    val shareBookTitle = stringResource(R.string.library_share_book)

    LaunchedEffect(detailMessageText) {
        val message = detailMessageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onDetailMessageShown()
    }

    val cleanedDescription = remember(book?.description) { book?.description?.cleanHtml() }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
        floatingActionButton = {
            if (book != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FloatingActionButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.size(Sizes.fab),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = Elevations.shadowSmall),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.library_edit_metadata),
                            modifier = Modifier.size(Sizes.iconLarge),
                        )
                    }
                    if (book.hasLocalReadableSource()) {
                        FloatingActionButton(
                            onClick = { onContinueReading(book.id) },
                            modifier = Modifier.size(Sizes.fab),
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = Elevations.shadowSmall),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoStories,
                                contentDescription = stringResource(R.string.library_continue_reading),
                                modifier = Modifier.size(Sizes.iconLarge),
                            )
                        }
                    }
                }
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Column(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BookCover(
                                book = book,
                                modifier = Modifier
                                    .size(width = Sizes.coverWidthMax, height = Sizes.coverWidthMax / Sizes.coverAspectRatio)
                                    .clickable { showCoverPreview = true },
                            )
                            CoverActionButtons(
                                hasCover = book.coverPath != null,
                                onChangeCover = { coverPicker.launch(arrayOf("image/*")) },
                                onRemoveCover = onRemoveCover,
                                modifier = Modifier
                                    .width(Sizes.coverWidthMax)
                                    .padding(top = Spacing.sm),
                            )
                        }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(book.title, style = MaterialTheme.typography.headlineSmall)
                            book.author?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Spacing.xs),
                                )
                            }
                            if (!book.series.isNullOrBlank() || !book.seriesNumber.isNullOrBlank()) {
                                Text(
                                    text = stringResource(R.string.library_series_value, book.seriesDisplay()),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Spacing.xs),
                                )
                            }
                            Surface(
                                modifier = Modifier.padding(top = Spacing.md),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ) {
                                Text(
                                    text = book.format.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                                )
                            }
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
                if (book.hasStartedReading()) {
                    item {
                        ReadingStatsCard(book = book)
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.library_description),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { showEditDescriptionDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.library_edit_description),
                            )
                        }
                    }
                    if (!cleanedDescription.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.xs)
                                .heightIn(max = Sizes.coverWidthMax)
                                .clip(RoundedCornerShape(Radii.medium)),
                            shape = RoundedCornerShape(Radii.medium),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = Elevations.none,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = Sizes.coverWidthMax)
                                    .verticalScroll(rememberScrollState())
                                    .padding(Spacing.md),
                            ) {
                                Text(
                                    text = cleanedDescription,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.library_description_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
                item {
                    Text(text = stringResource(R.string.library_imported_on, book.createdAt.formatDate()), style = MaterialTheme.typography.bodyMedium)
                    if (!book.hasStartedReading()) {
                        book.lastReadAt?.let {
                            Text(text = stringResource(R.string.library_last_read_on, it.formatDate()), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item {
                    BookShelvesSection(
                        shelvesForBook = shelvesForBook,
                        allShelves = allShelves,
                        onCreateShelf = onCreateShelf,
                        onAddToShelf = onAddToShelf,
                        onRemoveFromShelf = onRemoveFromShelf,
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        if (book.format != BookFormat.PHYSICAL) {
                            val isQueued = book.readNextAddedAt != null
                            ElevatedButton(onClick = { onSetReadNext(!isQueued) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(if (isQueued) Icons.Outlined.Check else Icons.Outlined.PlaylistAdd, contentDescription = null)
                                Text(
                                    text = stringResource(if (isQueued) R.string.library_read_next_remove else R.string.library_read_next_add),
                                    modifier = Modifier.padding(start = Spacing.sm),
                                )
                            }
                        }
                        ElevatedButton(
                            onClick = { showImportQuotesDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.EditNote, contentDescription = null)
                            Text(text = stringResource(R.string.library_import_quotes), modifier = Modifier.padding(start = Spacing.sm))
                        }
                        ElevatedButton(onClick = { showShareBookDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Share, contentDescription = null)
                            Text(text = stringResource(R.string.library_share_book), modifier = Modifier.padding(start = Spacing.sm))
                        }
                        if (book.hasLocalReadableSource()) {
                            ElevatedButton(onClick = { context.shareBookFile(book) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.Share, contentDescription = null)
                                Text(text = stringResource(R.string.library_share_file), modifier = Modifier.padding(start = Spacing.sm))
                            }
                        }
                        if (book.canRemoveLocalFileFromDevice()) {
                            ElevatedButton(onClick = { showRemoveFromDeviceDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.Delete, contentDescription = null)
                                Text(text = stringResource(R.string.library_remove_from_device), modifier = Modifier.padding(start = Spacing.sm))
                            }
                        }
                        if (book.format != BookFormat.PHYSICAL) {
                            ElevatedButton(onClick = { sourcePicker.launch(arrayOf("application/epub+zip", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.AutoStories, contentDescription = null)
                                Text(text = stringResource(R.string.library_replace_source_file), modifier = Modifier.padding(start = Spacing.sm))
                            }
                        }
                        if (book.finishedReadingAt == null && book.readingPercent < 1f) {
                            ElevatedButton(onClick = onMarkFinished, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.Check, contentDescription = null)
                                Text(text = stringResource(R.string.library_mark_finished), modifier = Modifier.padding(start = Spacing.sm))
                            }
                        }
                        TextButton(onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Delete, contentDescription = null)
                            Text(text = stringResource(R.string.library_delete_book), modifier = Modifier.padding(start = Spacing.sm))
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(Sizes.bottomNavHeight)) }
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

    if (showRemoveFromDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveFromDeviceDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveFromDeviceDialog = false
                        onRemoveFromDevice()
                    },
                ) { Text(stringResource(R.string.library_remove_from_device_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveFromDeviceDialog = false }) {
                    Text(stringResource(R.string.settings_reset_all_cancel))
                }
            },
            title = { Text(stringResource(R.string.library_remove_from_device_title)) },
            text = { Text(stringResource(R.string.library_remove_from_device_body)) },
        )
    }

    if (showEditDialog && book != null) {
        EditMetadataDialog(
            book = book,
            libraryBooks = libraryBooks,
            onDismiss = { showEditDialog = false },
            onSave = { title, author, series, seriesNumber ->
                showEditDialog = false
                onUpdateMetadata(title, author, series, seriesNumber, book.description.orEmpty())
            },
        )
    }

    if (showEditDescriptionDialog && book != null) {
        EditDescriptionDialog(
            description = book.description.orEmpty(),
            onDismiss = { showEditDescriptionDialog = false },
            onSave = { description ->
                showEditDescriptionDialog = false
                onUpdateMetadata(book.title, book.author.orEmpty(), book.series.orEmpty(), book.seriesNumber.orEmpty(), description)
            },
        )
    }

    if (showCoverPreview && book != null) {
        CoverPreviewDialog(
            book = book,
            onDismiss = { showCoverPreview = false },
        )
    }

    if (showImportQuotesDialog && book != null) {
        ImportQuotesDialog(
            onDismiss = { showImportQuotesDialog = false },
            onImportText = { text ->
                showImportQuotesDialog = false
                onImportQuotes(text)
            },
            onImportFile = { uri ->
                showImportQuotesDialog = false
                onImportQuotesFile(uri)
            },
        )
    }

    if (showShareBookDialog && book != null) {
        ShareCardDialog(
            onDismiss = { showShareBookDialog = false },
            onShareText = {
                context.shareTextWithChooser(
                    book.toShareText(context),
                    shareBookTitle,
                )
                showShareBookDialog = false
            },
            chooserTitle = stringResource(R.string.share_card_image_chooser_title),
            shareTextLabel = stringResource(R.string.share_card_share_text),
            shareImageLabel = stringResource(R.string.share_card_share_image),
        ) {
            val readSeconds = book.totalReadingSeconds
            BookShareCard(
                title = book.title,
                author = book.author,
                statusLabel = if (book.finishedReadingAt != null) {
                    stringResource(R.string.share_card_status_finished)
                } else {
                    stringResource(R.string.share_card_status_progress, (book.readingPercent * 100).toInt())
                },
                stat1Value = "${(book.readingPercent * 100).toInt()}%",
                stat1Label = stringResource(R.string.share_card_stat_progress_label),
                stat2Value = stringResource(R.string.share_card_stat_hours, (readSeconds / 3600).toInt(), ((readSeconds % 3600) / 60).toInt()),
                stat2Label = stringResource(R.string.share_card_stat_read_time_label),
                footerLeft = stringResource(R.string.library_imported_on, book.createdAt.formatDate()),
                footerRight = stringResource(R.string.share_card_tagline),
                watermark = stringResource(R.string.share_card_watermark),
            ) {
                BookCover(book = book)
            }
        }
    }
}

@Composable
private fun BookShelvesSection(
    shelvesForBook: List<com.vayana.core.database.model.Shelf>,
    allShelves: List<com.vayana.core.database.model.Shelf>,
    onCreateShelf: (String) -> Unit,
    onAddToShelf: (Long) -> Unit,
    onRemoveFromShelf: (Long) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.library_shelves_section_title), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.library_shelves_add_to_shelf))
            }
        }
        if (shelvesForBook.isEmpty()) {
            Text(
                text = stringResource(R.string.library_shelves_none_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                shelvesForBook.forEach { shelf ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveFromShelf(shelf.id) },
                        label = { Text(shelf.name) },
                        trailingIcon = {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.library_shelves_remove_book), modifier = Modifier.size(Sizes.iconSmall))
                        },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        var newShelfName by remember { mutableStateOf("") }
        val memberIds = remember(shelvesForBook) { shelvesForBook.map { it.id }.toSet() }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.library_shelves_add_to_shelf)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    allShelves.forEach { shelf ->
                        val onShelf = shelf.id in memberIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (onShelf) onRemoveFromShelf(shelf.id) else onAddToShelf(shelf.id)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Checkbox(checked = onShelf, onCheckedChange = { checked -> if (checked) onAddToShelf(shelf.id) else onRemoveFromShelf(shelf.id) })
                            Text(shelf.name)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                    OutlinedTextField(
                        value = newShelfName,
                        onValueChange = { newShelfName = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.library_shelves_name_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newShelfName.isNotBlank()) onCreateShelf(newShelfName)
                        showAddDialog = false
                    },
                ) { Text(if (newShelfName.isNotBlank()) stringResource(R.string.library_shelves_create) else stringResource(R.string.notes_edit_save)) }
            },
            dismissButton = {
                FilledTonalButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.settings_reset_all_cancel))
                }
            },
        )
    }
}

private fun Book.toShareText(context: android.content.Context): String {
    val progress = (readingPercent * 100).toInt()
    return buildString {
        appendLine(title)
        author?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
        appendLine(context.getString(R.string.library_share_book_progress, progress))
    }.trim()
}

@Composable
private fun CoverPreviewDialog(book: Book, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radii.medium),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = Elevations.shadowMedium,
        ) {
            BookCover(
                book = book,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
            )
        }
    }
}

@Composable
private fun EditMetadataDialog(
    book: Book,
    libraryBooks: List<Book>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author.orEmpty()) }
    var series by remember(book.id) { mutableStateOf(book.series.orEmpty()) }
    var seriesNumber by remember(book.id) { mutableStateOf(book.seriesNumber.orEmpty()) }
    val authorSuggestions = remember(libraryBooks) { libraryBooks.metadataSuggestions { it.author } }
    val seriesSuggestions = remember(libraryBooks) { libraryBooks.metadataSuggestions { it.series } }
    val duplicateSeriesNumber = remember(book.id, libraryBooks, series, seriesNumber) {
        libraryBooks.hasSeriesNumberCollision(
            currentBookId = book.id,
            series = series,
            seriesNumber = seriesNumber,
        )
    }
    val canSave = title.isNotBlank() && !duplicateSeriesNumber
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = Sizes.contentMaxWidth),
            shape = RoundedCornerShape(Radii.extraLargeIncreased),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = Elevations.shadowLarge,
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.lg)
                    .verticalScroll(rememberScrollState())
                    .vayanaAnimateContentSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Surface(
                        shape = RoundedCornerShape(Radii.large),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        tonalElevation = Elevations.level1,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.library_edit_metadata_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.library_edit_metadata_title_label)) },
                    singleLine = true,
                    isError = !canSave,
                    shape = RoundedCornerShape(Radii.medium),
                    colors = textFieldColors,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.AutoStories,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )

                MetadataSuggestionField(
                    value = author,
                    onValueChange = { author = it },
                    suggestions = authorSuggestions,
                    label = stringResource(R.string.library_edit_metadata_author_label),
                    leadingIcon = Icons.Outlined.EditNote,
                    textFieldColors = textFieldColors,
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radii.large),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = Elevations.none,
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        MetadataSuggestionField(
                            value = series,
                            onValueChange = { series = it },
                            suggestions = seriesSuggestions,
                            label = stringResource(R.string.library_edit_metadata_series_label),
                            leadingIcon = Icons.Outlined.Category,
                            textFieldColors = textFieldColors,
                        )

                        OutlinedTextField(
                            value = seriesNumber,
                            onValueChange = { seriesNumber = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.library_edit_metadata_series_number_label)) },
                            singleLine = true,
                            isError = duplicateSeriesNumber,
                            shape = RoundedCornerShape(Radii.medium),
                            colors = textFieldColors,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            supportingText = {
                                if (duplicateSeriesNumber) {
                                    Text(
                                        text = stringResource(R.string.library_edit_metadata_series_number_taken),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(Radii.full),
                    ) {
                        Text(stringResource(R.string.library_edit_metadata_cancel))
                    }
                    Button(
                        onClick = { onSave(title, author, series, seriesNumber) },
                        enabled = canSave,
                        shape = RoundedCornerShape(Radii.full),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.padding(end = Spacing.xs),
                        )
                        Text(stringResource(R.string.library_edit_metadata_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditDescriptionDialog(
    description: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(description) }
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = Sizes.contentMaxWidth),
            shape = RoundedCornerShape(Radii.extraLargeIncreased),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = Elevations.shadowLarge,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.library_edit_description),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.library_edit_metadata_description_label)) },
                    minLines = 5,
                    shape = RoundedCornerShape(Radii.medium),
                    colors = textFieldColors,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(Radii.full),
                    ) {
                        Text(stringResource(R.string.library_edit_metadata_cancel))
                    }
                    Button(
                        onClick = { onSave(text) },
                        shape = RoundedCornerShape(Radii.full),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.padding(end = Spacing.xs),
                        )
                        Text(stringResource(R.string.library_edit_metadata_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataSuggestionField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    textFieldColors: androidx.compose.material3.TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(value, suggestions) { suggestions.matchingMetadataSuggestions(value) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    expanded = it.isNotBlank()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(label) },
                singleLine = true,
                shape = RoundedCornerShape(Radii.medium),
                colors = textFieldColors,
                leadingIcon = leadingIcon?.let { icon ->
                    {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                trailingIcon = {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange(""); expanded = false }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.input_clear_content_description),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            DropdownMenu(
                expanded = expanded && matches.isNotEmpty(),
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(),
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
            ) {
                matches.forEach { suggestion ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = suggestion,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = {
                            onValueChange(suggestion)
                            expanded = false
                        },
                    )
                }
            }
        }

        if (matches.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                matches.take(4).forEach { suggestion ->
                    SuggestionChip(
                        onClick = {
                            onValueChange(suggestion)
                            expanded = false
                        },
                        label = {
                            Text(
                                text = suggestion,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        shape = RoundedCornerShape(Radii.full),
                    )
                }
            }
        }
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
private fun CoverActionButtons(
    hasCover: Boolean,
    onChangeCover: () -> Unit,
    onRemoveCover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        ElevatedButton(
            onClick = onChangeCover,
            modifier = Modifier
                .height(Sizes.touchTarget)
                .weight(1f),
            shape = RoundedCornerShape(percent = 50),
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = stringResource(R.string.library_change_cover),
            )
        }
        ElevatedButton(
            onClick = onRemoveCover,
            enabled = hasCover,
            modifier = Modifier
                .height(Sizes.touchTarget)
                .weight(1f),
            shape = RoundedCornerShape(percent = 50),
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.library_remove_cover),
            )
        }
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
private fun LibraryGroupBy.label(): String = when (this) {
    LibraryGroupBy.NONE -> stringResource(R.string.library_group_none)
    LibraryGroupBy.AUTHOR -> stringResource(R.string.library_group_author)
    LibraryGroupBy.SERIES -> stringResource(R.string.library_group_series)
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

private enum class GitHubSyncStepStatus {
    WAITING,
    RUNNING,
    DONE,
    FAILED,
}

@Composable
private fun GitHubSyncProgressStep.label(): String = when (this) {
    GitHubSyncProgressStep.PREPARING -> stringResource(R.string.library_sync_progress_prepare)
    GitHubSyncProgressStep.READING_CLOUD -> stringResource(R.string.library_sync_progress_read_cloud)
    GitHubSyncProgressStep.ADDING_CLOUD_BOOKS -> stringResource(R.string.library_sync_progress_add_cloud)
    GitHubSyncProgressStep.UPLOADING_BOOKS -> stringResource(R.string.library_sync_progress_upload)
    GitHubSyncProgressStep.SAVING_SNAPSHOT -> stringResource(R.string.library_sync_progress_save)
    GitHubSyncProgressStep.COMPLETE -> stringResource(R.string.library_sync_progress_complete)
    GitHubSyncProgressStep.FAILED -> stringResource(R.string.library_sync_progress_failed)
}

@Composable
private fun GitHubSyncStepStatus.label(): String = when (this) {
    GitHubSyncStepStatus.WAITING -> stringResource(R.string.library_sync_progress_waiting)
    GitHubSyncStepStatus.RUNNING -> stringResource(R.string.library_sync_progress_running)
    GitHubSyncStepStatus.DONE -> stringResource(R.string.library_sync_progress_done)
    GitHubSyncStepStatus.FAILED -> stringResource(R.string.library_sync_progress_failed)
}

private fun GitHubSyncProgressState.statusFor(step: GitHubSyncProgressStep): GitHubSyncStepStatus {
    if (this.step == GitHubSyncProgressStep.FAILED && step.ordinal == completedSteps.coerceAtMost(GitHubSyncProgressStep.SAVING_SNAPSHOT.ordinal)) {
        return GitHubSyncStepStatus.FAILED
    }
    return when {
        step.ordinal < completedSteps -> GitHubSyncStepStatus.DONE
        this.step == step && isRunning -> GitHubSyncStepStatus.RUNNING
        this.step == GitHubSyncProgressStep.COMPLETE -> GitHubSyncStepStatus.DONE
        else -> GitHubSyncStepStatus.WAITING
    }
}

@Composable
private fun BookDetailMessage.label(): String = when (this) {
    BookDetailMessage.METADATA_SAVED -> stringResource(R.string.library_metadata_saved)
    BookDetailMessage.COVER_UPDATED -> stringResource(R.string.library_cover_updated)
    BookDetailMessage.COVER_REMOVED -> stringResource(R.string.library_cover_removed)
    BookDetailMessage.COVER_FAILED -> stringResource(R.string.library_cover_failed)
    BookDetailMessage.SOURCE_REPLACED -> stringResource(R.string.library_source_replaced)
    BookDetailMessage.SOURCE_DUPLICATE -> stringResource(R.string.library_source_duplicate)
    BookDetailMessage.SOURCE_UNSUPPORTED -> stringResource(R.string.library_source_unsupported)
    BookDetailMessage.SOURCE_FAILED -> stringResource(R.string.library_source_failed)
    BookDetailMessage.LOCAL_FILE_REMOVED -> stringResource(R.string.library_remove_from_device_done)
    BookDetailMessage.LOCAL_FILE_REMOVE_UNAVAILABLE -> stringResource(R.string.library_remove_from_device_unavailable)
    BookDetailMessage.LOCAL_FILE_REMOVE_FAILED -> stringResource(R.string.library_remove_from_device_failed)
    is BookDetailMessage.QUOTES_IMPORTED -> stringResource(R.string.library_quotes_imported_message, count)
    BookDetailMessage.MARKED_FINISHED -> stringResource(R.string.library_marked_finished)
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

private fun Book.hasLocalReadableSource(): Boolean =
    format != BookFormat.PHYSICAL &&
        fileAvailability == BookFileAvailability.LOCAL &&
        filePath.isNotBlank()

private fun Book.canRemoveLocalFileFromDevice(): Boolean =
    hasLocalReadableSource() &&
        !fileAssetId.isNullOrBlank() &&
        !fileAssetSha256.isNullOrBlank() &&
        fileAssetSizeBytes != null &&
        fileAssetUploadedAt != null

private fun BookFormat.shareMimeType(): String = when (this) {
    BookFormat.EPUB -> "application/epub+zip"
    BookFormat.PDF -> "application/pdf"
    BookFormat.TXT -> "text/plain"
    BookFormat.MOBI,
    BookFormat.AZW3,
    BookFormat.FB2,
    BookFormat.PHYSICAL,
    -> "application/octet-stream"
}

private fun List<Book>.metadataSuggestions(selector: (Book) -> String?): List<String> =
    mapNotNull { book -> selector(book)?.trim()?.takeIf { it.isNotEmpty() } }
        .distinctBy { it.metadataKey() }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

private fun List<String>.matchingMetadataSuggestions(value: String): List<String> {
    val query = value.trim()
    if (query.isEmpty()) return emptyList()
    return filter { suggestion ->
        suggestion.contains(query, ignoreCase = true)
    }.filterNot { suggestion ->
        suggestion.equals(query, ignoreCase = true)
    }.take(MetadataSuggestionLimit)
}

private fun List<Book>.hasSeriesNumberCollision(currentBookId: Long, series: String, seriesNumber: String): Boolean {
    val normalizedSeries = series.metadataKey()
    val normalizedNumber = seriesNumber.metadataKey()
    if (normalizedNumber.isEmpty()) return false

    return any { book ->
        book.id != currentBookId &&
            book.series.orEmpty().metadataKey() == normalizedSeries &&
            book.seriesNumber.orEmpty().metadataKey() == normalizedNumber
    }
}

private fun String.metadataKey(): String = trim().lowercase()

@Composable
private fun Book.seriesDisplay(): String = listOfNotNull(
    series?.takeIf { it.isNotBlank() },
    seriesNumber?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.library_series_number_value, it) },
).joinToString(" · ")

private fun Long.formatDate(): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))

@Composable
private fun ReadingStatsCard(book: Book, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val startedAt = book.startedReadingAt ?: book.lastReadAt ?: book.createdAt
    val isFinished = book.finishedReadingAt != null || book.readingPercent >= 1.0f
    val daysTaken = remember(startedAt, book.finishedReadingAt) {
        calculateDaysTaken(startedAt = startedAt, finishedAt = book.finishedReadingAt)
    }
    val timeTakenText = remember(book.totalReadingSeconds, context) {
        formatReadingDuration(book.totalReadingSeconds, context)
    }
    val daysTakenText = remember(daysTaken, context) {
        formatDaysTaken(daysTaken, context)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.level1,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.library_reading_stats_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.library_stat_started),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = startedAt.formatDate(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (isFinished) {
                val finishedAt = book.finishedReadingAt ?: book.updatedAt
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.library_stat_finished),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = finishedAt.formatDate(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.library_stat_time_taken),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = timeTakenText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.library_stat_days_taken),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = daysTakenText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun formatReadingDuration(totalSeconds: Long, context: android.content.Context): String {
    if (totalSeconds < 60L) {
        return context.getString(R.string.reading_time_less_than_minute)
    }
    val totalMinutes = totalSeconds / 60L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> context.getString(R.string.reading_time_hours_and_minutes, hours, minutes)
        hours > 0L -> context.getString(R.string.reading_time_hours_only, hours)
        else -> context.getString(R.string.reading_time_minutes_only, minutes)
    }
}

private fun calculateDaysTaken(startedAt: Long, finishedAt: Long?): Int {
    val startCal = java.util.Calendar.getInstance().apply {
        timeInMillis = startedAt
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val endCal = java.util.Calendar.getInstance().apply {
        timeInMillis = finishedAt ?: System.currentTimeMillis()
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val diffMillis = endCal.timeInMillis - startCal.timeInMillis
    val days = (diffMillis / (24 * 60 * 60 * 1000L)).toInt() + 1
    return days.coerceAtLeast(1)
}

private fun formatDaysTaken(days: Int, context: android.content.Context): String =
    if (days == 1) {
        context.getString(R.string.reading_days_single)
    } else {
        context.getString(R.string.reading_days_plural, days)
    }

private fun Book.hasStartedReading(): Boolean =
    startedReadingAt != null || readingPercent > 0f || lastReadAt != null || totalReadingSeconds > 0L

private const val MetadataSuggestionLimit = 5
private const val SearchFieldUnfocusedBorderAlpha = 0.35f
private const val GeneratedCoverAuthorAlpha = 0.8f

private fun String.cleanHtml(): String {
    val unescaped = try {
        HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
    } catch (_: Throwable) {
        this.replace(Regex("<[^>]*>"), "")
    }
    return unescaped.replace(Regex("\n{3,}"), "\n\n").trim()
}

@Composable
private fun ImportQuotesDialog(
    onDismiss: () -> Unit,
    onImportText: (String) -> Unit,
    onImportFile: (Uri) -> Unit,
) {
    var rawText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    val parsedQuotes = remember(rawText) { QuoteParser.parse(rawText) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onImportFile(uri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.library_import_quotes_dialog_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.library_import_quotes_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text(stringResource(R.string.library_import_quotes_tab_paste)) },
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text(stringResource(R.string.library_import_quotes_tab_file)) },
                    )
                }

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Sizes.coverWidthMin, max = Sizes.coverWidthMax),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.library_import_quotes_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        textStyle = MaterialTheme.typography.bodySmall,
                    )

                    if (parsedQuotes.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radii.medium),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Column(modifier = Modifier.padding(Spacing.md)) {
                                Text(
                                    text = stringResource(R.string.library_import_quotes_detected, parsedQuotes.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.height(Spacing.xs))
                                parsedQuotes.take(3).forEach { quote ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = Spacing.xs),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "“${quote.quoteText.take(45)}…”",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(Radii.small),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        ) {
                                            Text(
                                                text = "${quote.highlightsCount}★",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                                            )
                                        }
                                    }
                                }
                                if (parsedQuotes.size > 3) {
                                    Text(
                                        text = "+ ${parsedQuotes.size - 3} more",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = Spacing.xs),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    ElevatedButton(
                        onClick = { filePicker.launch(arrayOf("text/plain", "*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = null)
                        Text(
                            text = stringResource(R.string.library_import_quotes_file_button),
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                Button(
                    onClick = {
                        onImportText(rawText)
                    },
                    enabled = parsedQuotes.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.library_import_quotes_confirm, parsedQuotes.size))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_reset_all_cancel))
            }
        },
    )
}
