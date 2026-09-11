package com.vayana.feature.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import com.vayana.core.designsystem.sharecard.BookShareCardLayout
import com.vayana.core.designsystem.sharecard.ShareCardDialog
import com.vayana.core.designsystem.sharecard.ShareCardTheme
import com.vayana.core.designsystem.dialog.ConfirmActionDialog
import com.vayana.core.designsystem.dialog.ExpressiveDialogHeader
import com.vayana.core.designsystem.dialog.ExpressiveDialogSurface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
import com.vayana.core.designsystem.theme.VayanaLinearProgressIndicator
import com.vayana.core.designsystem.theme.vayanaAnimateContentSize
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Palette
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.common.ParsedQuote
import com.vayana.core.resources.R
import java.io.File
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
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
    val cloudBookDownloadProgress by viewModel.cloudBookDownloadProgress.collectAsState()

    LibraryScreen(
        modifier = modifier,
        uiState = uiState,
        importSummary = importSummary,
        importProgress = importProgress,
        syncProgress = syncProgress,
        cloudBookDownloadProgress = cloudBookDownloadProgress,
        onImportSummaryShown = viewModel::onImportSummaryShown,
        onImportProgressDismissed = viewModel::onImportProgressDismissed,
        onSyncProgressDismissed = viewModel::onSyncProgressDismissed,
        onCloudBookDownloadProgressDismissed = viewModel::onCloudBookDownloadProgressDismissed,
        onImportFiles = viewModel::importFiles,
        onImportFolder = viewModel::importFolder,
        onBookClick = onBookClick,
        onMarkFinished = { bookId -> viewModel.markFinished(bookId, announce = false) },
        onDownloadCloudBook = viewModel::downloadCloudBook,
        onSyncNow = viewModel::syncNow,
        onSettingsClick = onSettingsClick,
        onRecentlyDeletedClick = onRecentlyDeletedClick,
        onShelvesClick = onShelvesClick,
        onQueryChange = viewModel::updateQuery,
        onSortChange = viewModel::updateSort,
        onFilterChange = viewModel::updateFilter,
        onGroupByChange = viewModel::updateGroupBy,
        onViewModeChange = viewModel::updateViewMode,
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
    val uiState by viewModel.uiState.collectAsState()
    val libraryBooks by viewModel.libraryBooks.collectAsState()
    val detailMessage by viewModel.bookDetailMessage.collectAsState()
    val allShelves by viewModel.shelves.collectAsState()
    val shelvesForBook by remember(bookId) { viewModel.observeShelvesForBook(bookId) }.collectAsState()
    val goodreadsImport by viewModel.goodreadsImport.collectAsState()
    var syncReadingProgressRunning by remember { mutableStateOf(false) }
    var progressChangePrompt by remember { mutableStateOf<BookProgressChange?>(null) }

    BookDetailScreen(
        modifier = modifier,
        book = book,
        showSyncReadingProgress = uiState.githubSyncReady,
        syncReadingProgressRunning = syncReadingProgressRunning,
        progressChangePrompt = progressChangePrompt,
        onKeepSyncedProgress = { progressChangePrompt = null },
        onRevertSyncedProgress = { prompt ->
            viewModel.revertReadingProgress(prompt.bookId, prompt.previousLocator, prompt.previousPercent)
            progressChangePrompt = null
        },
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
        onUpdateMetadata = { title, author, series, seriesNumber, description, tagsCsv ->
            viewModel.updateMetadata(bookId, title, author, series, seriesNumber, description, tagsCsv)
        },
        onUpdateRating = { rating ->
            viewModel.updateRating(bookId, rating)
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
        onSyncReadingProgress = sync@{
            if (syncReadingProgressRunning) return@sync GitHubSyncNowResult.SyncDisabled
            syncReadingProgressRunning = true
            try {
                val outcome = viewModel.syncReadingProgressForBook(bookId)
                outcome.progressChange?.let { progressChangePrompt = it }
                outcome.result
            } finally {
                syncReadingProgressRunning = false
            }
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
        goodreadsImport = goodreadsImport,
        onImportGoodreads = { link -> viewModel.importFromGoodreads(bookId, link) },
        onImportGoodreadsCapture = { metadata, quotes -> viewModel.importFromGoodreadsCapture(bookId, metadata, quotes) },
        onApplyGoodreads = { viewModel.applyPendingGoodreads(bookId) },
        onResetReadingStats = { viewModel.resetReadingStats(bookId) },
        onDismissGoodreads = viewModel::dismissGoodreadsImport,
        onUseCover = { source -> viewModel.useCover(bookId, source) },
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
    cloudBookDownloadProgress: CloudBookDownloadProgressState?,
    onImportSummaryShown: () -> Unit,
    onImportProgressDismissed: () -> Unit,
    onSyncProgressDismissed: () -> Unit,
    onCloudBookDownloadProgressDismissed: () -> Unit,
    onImportFiles: (android.content.ContentResolver, List<Uri>) -> Unit,
    onImportFolder: (android.content.ContentResolver, Uri) -> Unit,
    onBookClick: (Long) -> Unit,
    onMarkFinished: (Long) -> Unit,
    onDownloadCloudBook: suspend (Book) -> CloudBookDownloadResult,
    onSyncNow: suspend (Boolean, GitHubSyncMode) -> GitHubSyncNowResult,
    onSettingsClick: () -> Unit,
    onRecentlyDeletedClick: () -> Unit,
    onShelvesClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onGroupByChange: (LibraryGroupBy) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
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
    var pendingFinishBook by remember { mutableStateOf<Book?>(null) }
    val markedFinishedMessage = stringResource(R.string.library_marked_finished)
    var syncRunning by remember { mutableStateOf(false) }
    var syncBadge by remember { mutableStateOf<LibrarySyncBadge?>(null) }
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
    val syncProgressOnlyCompleteMessage = stringResource(R.string.library_sync_progress_only_complete)
    val syncProgressOnlyConflictsMessage = stringResource(R.string.library_sync_progress_only_conflicts)
    val syncProgressOnlyFailedMessage = stringResource(R.string.library_sync_progress_only_failed)
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
    val activeDownloadBookId = cloudBookDownloadProgress?.takeIf { it.isRunning }?.bookId

    fun handleBookClick(book: Book) {
        if (activeDownloadBookId != null) return
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

    suspend fun runSyncNow(allowInitialSync: Boolean, mode: GitHubSyncMode) {
        snackbarHostState.currentSnackbarData?.dismiss()
        syncBadge = null
        val startedSnackbar = coroutineScope.launch {
            snackbarHostState.showSnackbar(syncStartedMessage)
        }
        when (val result = onSyncNow(allowInitialSync, mode)) {
            is GitHubSyncNowResult.Complete -> {
                syncBadge = if (result.pullFailed || !result.metadataSynced || result.failed > 0) {
                    LibrarySyncBadge.FAILED
                } else {
                    LibrarySyncBadge.SUCCESS
                }
                startedSnackbar.cancel()
                snackbarHostState.currentSnackbarData?.dismiss()
                val message = if (mode == GitHubSyncMode.READING_PROGRESS_ONLY) {
                    when {
                        result.pullFailed || !result.metadataSynced -> syncProgressOnlyFailedMessage.format(result.failureMessage.orEmpty())
                        result.conflicts > 0 -> syncProgressOnlyConflictsMessage.format(
                            result.progressUpdated,
                            result.progressUploaded,
                            result.conflicts,
                        )
                        else -> syncProgressOnlyCompleteMessage.format(result.progressUpdated, result.progressUploaded)
                    }
                } else {
                    when {
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
                }
                snackbarHostState.showSnackbar(message)
            }
            is GitHubSyncNowResult.InitialSyncConfirmationRequired -> {
                syncBadge = LibrarySyncBadge.FAILED
                startedSnackbar.cancel()
                snackbarHostState.currentSnackbarData?.dismiss()
                initialSyncConfirmationMessage = result.message
            }
            GitHubSyncNowResult.SyncDisabled -> {
                syncBadge = LibrarySyncBadge.FAILED
                startedSnackbar.cancel()
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(syncDisabledMessage)
            }
            GitHubSyncNowResult.ConfigIncomplete -> {
                syncBadge = LibrarySyncBadge.FAILED
                startedSnackbar.cancel()
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(syncConfigMissingMessage)
            }
        }
    }

    fun handleSyncNow(mode: GitHubSyncMode = GitHubSyncMode.FULL, allowInitialSync: Boolean = false) {
        if (syncRunning) return
        coroutineScope.launch {
            syncRunning = true
            try {
                runSyncNow(allowInitialSync, mode)
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
                showSyncNow = true,
                syncRunning = syncRunning,
                syncBadge = syncBadge,
                onSyncNow = { mode -> handleSyncNow(mode = mode) },
                onSettingsClick = onSettingsClick,
                onRecentlyDeletedClick = onRecentlyDeletedClick,
                onShelvesClick = onShelvesClick,
                onQueryChange = onQueryChange,
                onSortChange = onSortChange,
                onFilterChange = onFilterChange,
                onGroupByChange = onGroupByChange,
                onViewModeChange = onViewModeChange,
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
            when (uiState.controls.viewMode) {
                LibraryViewMode.THUMBNAILS -> LibraryGrid(
                    books = uiState.books,
                    groupBy = uiState.controls.groupBy,
                    contentPadding = innerPadding,
                    downloadingBookId = activeDownloadBookId,
                    downloadProgress = cloudBookDownloadProgress?.takeIf { it.isRunning }?.fraction,
                    onBookClick = ::handleBookClick,
                    onMarkFinished = { book -> pendingFinishBook = book },
                )
                LibraryViewMode.LIST -> LibraryList(
                    books = uiState.books,
                    groupBy = uiState.controls.groupBy,
                    contentPadding = innerPadding,
                    downloadingBookId = activeDownloadBookId,
                    downloadProgress = cloudBookDownloadProgress?.takeIf { it.isRunning }?.fraction,
                    onBookClick = ::handleBookClick,
                    onMarkFinished = { book -> pendingFinishBook = book },
                )
            }
        }
    }

    pendingFinishBook?.let { book ->
        ConfirmActionDialog(
            onDismissRequest = { pendingFinishBook = null },
            icon = Icons.Outlined.TaskAlt,
            title = stringResource(R.string.library_mark_finished_confirm_title),
            body = stringResource(R.string.library_mark_finished_confirm_body, book.title),
            confirmLabel = stringResource(R.string.library_mark_finished),
            dismissLabel = stringResource(R.string.library_edit_metadata_cancel),
            onConfirm = {
                pendingFinishBook = null
                onMarkFinished(book.id)
                coroutineScope.launch { snackbarHostState.showSnackbar(markedFinishedMessage) }
            },
        )
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

    if (cloudBookDownloadProgress != null) {
        CloudBookDownloadProgressSheet(
            progress = cloudBookDownloadProgress,
            onDismissRequest = onCloudBookDownloadProgressDismissed,
        )
    }

    initialSyncConfirmationMessage?.let { message ->
        ExpressiveDialogSurface(onDismissRequest = { initialSyncConfirmationMessage = null }) {
            ExpressiveDialogHeader(
                icon = Icons.Outlined.Sync,
                title = initialSyncConfirmationTitle,
                supportingText = initialSyncConfirmationBody,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (message.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radii.medium),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                ) {
                    Text(
                        text = initialSyncConfirmationDetail.format(message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = { initialSyncConfirmationMessage = null },
                    shape = Radii.buttonShape,
                ) {
                    Text(initialSyncCancel)
                }
                Button(
                    onClick = {
                        initialSyncConfirmationMessage = null
                        handleSyncNow(allowInitialSync = true)
                    },
                    shape = Radii.buttonShape,
                ) {
                    Text(initialSyncConfirm)
                }
            }
        }
    }
}

@Composable
private fun AddPhysicalBookDialog(onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }

    ExpressiveDialogSurface(onDismissRequest = onDismiss, scrollable = true) {
        ExpressiveDialogHeader(
            icon = Icons.Outlined.AutoStories,
            title = stringResource(R.string.library_add_physical_book_title),
            supportingText = stringResource(R.string.library_add_physical_book_hint),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.library_add_physical_book_book_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radii.medium),
            colors = expressiveTextFieldColors(),
        )
        OutlinedTextField(
            value = author,
            onValueChange = { author = it },
            label = { Text(stringResource(R.string.library_add_physical_book_author)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radii.medium),
            colors = expressiveTextFieldColors(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = onDismiss, shape = Radii.buttonShape) {
                Text(stringResource(R.string.settings_reset_all_cancel))
            }
            Button(
                onClick = { onConfirm(title.trim(), author.trim().takeIf { it.isNotBlank() }) },
                enabled = title.isNotBlank(),
                shape = Radii.buttonShape,
            ) {
                Text(stringResource(R.string.library_add_physical_book_confirm))
            }
        }
    }
}

@Composable
private fun expressiveTextFieldColors(): androidx.compose.material3.TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )

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
                VayanaLinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = Spacing.md))
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
        -> VayanaCircularProgressIndicator(modifier = Modifier.size(Sizes.icon))
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
        shape = Radii.sheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Paddings.screenHorizontal)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radii.extraLarge),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = Elevations.level1,
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = null,
                                modifier = Modifier.padding(Spacing.sm),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.library_sync_progress_title),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = progress.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f),
                        strokeCap = StrokeCap.Round,
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radii.large),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            ) {
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
                    modifier = Modifier.padding(Spacing.md),
                )
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(rows, key = { it.name }) { step ->
                    val status = progress.statusFor(step)
                    GitHubSyncProgressRow(
                        label = step.label(),
                        status = status,
                        detail = progress.detailFor(step, status),
                    )
                }
            }
            if (!progress.isRunning) {
                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                    shape = Radii.buttonShape,
                ) {
                    Text(stringResource(R.string.library_import_done))
                }
            }
        }
    }
}

@Composable
private fun GitHubSyncProgressRow(label: String, status: GitHubSyncStepStatus, detail: String? = null) {
    val colors = status.containerAndContentColor()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.medium),
        color = colors.first,
        contentColor = colors.second,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            GitHubSyncStepIcon(status = status, modifier = Modifier.size(Sizes.icon))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.second,
                )
                Text(
                    text = detail ?: status.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.second.copy(alpha = 0.74f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GitHubSyncStepIcon(status: GitHubSyncStepStatus, modifier: Modifier = Modifier) {
    when (status) {
        GitHubSyncStepStatus.RUNNING -> VayanaCircularProgressIndicator(modifier = modifier)
        GitHubSyncStepStatus.DONE -> Icon(Icons.Outlined.TaskAlt, contentDescription = null, modifier = modifier)
        GitHubSyncStepStatus.FAILED -> Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = modifier)
        GitHubSyncStepStatus.WAITING -> Icon(Icons.Outlined.HourglassEmpty, contentDescription = null, modifier = modifier)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CloudBookDownloadProgressSheet(
    progress: CloudBookDownloadProgressState,
    onDismissRequest: () -> Unit,
) {
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
            Text(text = stringResource(R.string.library_download_progress_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = progress.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.xs),
            )
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
                    R.string.library_download_progress_percent,
                    (progress.fraction * 100f).roundToInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
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

private enum class LibrarySyncBadge {
    SUCCESS,
    FAILED,
}

@Composable
private fun LibraryTopBar(
    controls: LibraryControls,
    showSyncNow: Boolean,
    syncRunning: Boolean,
    syncBadge: LibrarySyncBadge?,
    onSyncNow: (GitHubSyncMode) -> Unit,
    onSettingsClick: () -> Unit,
    onRecentlyDeletedClick: () -> Unit,
    onShelvesClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onGroupByChange: (LibraryGroupBy) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
) {
    var filterExpanded by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    var syncExpanded by remember { mutableStateOf(false) }

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
            Text(text = stringResource(R.string.library_title), style = MaterialTheme.typography.headlineMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (showSyncNow) {
                    Box {
                        LibrarySyncTopBarIconButton(
                            onClick = { syncExpanded = true },
                            enabled = !syncRunning,
                            badge = syncBadge.takeUnless { syncRunning },
                        ) {
                            if (syncRunning) {
                                VayanaCircularProgressIndicator(modifier = Modifier.size(Sizes.icon))
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Sync,
                                    contentDescription = stringResource(R.string.library_sync_now_content_description),
                                    modifier = Modifier.size(Sizes.icon),
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = syncExpanded,
                            onDismissRequest = { syncExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_sync_all)) },
                                onClick = {
                                    syncExpanded = false
                                    onSyncNow(GitHubSyncMode.FULL)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_sync_reading_progress_only)) },
                                onClick = {
                                    syncExpanded = false
                                    onSyncNow(GitHubSyncMode.READING_PROGRESS_ONLY)
                                },
                            )
                        }
                    }
                }
                LibraryTopBarIconButton(onClick = { filterExpanded = true }) {
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
                LibraryTopBarIconButton(
                    onClick = {
                        onViewModeChange(
                            if (controls.viewMode == LibraryViewMode.THUMBNAILS) {
                                LibraryViewMode.LIST
                            } else {
                                LibraryViewMode.THUMBNAILS
                            },
                        )
                    },
                ) {
                    Icon(
                        imageVector = if (controls.viewMode == LibraryViewMode.THUMBNAILS) {
                            Icons.AutoMirrored.Outlined.ViewList
                        } else {
                            Icons.Outlined.GridView
                        },
                        contentDescription = if (controls.viewMode == LibraryViewMode.THUMBNAILS) {
                            stringResource(R.string.library_view_list_content_description)
                        } else {
                            stringResource(R.string.library_view_thumbnails_content_description)
                        },
                        modifier = Modifier.size(Sizes.icon),
                    )
                }
                Box {
                    LibraryTopBarIconButton(onClick = { moreExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.library_more_content_description),
                            modifier = Modifier.size(Sizes.icon),
                        )
                    }
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_group_content_description)) },
                            leadingIcon = { Icon(Icons.Outlined.Category, contentDescription = null) },
                            enabled = false,
                            onClick = {},
                        )
                        LibraryGroupBy.entries.forEach { groupBy ->
                            DropdownMenuItem(
                                text = { Text(groupBy.label()) },
                                leadingIcon = {
                                    if (controls.groupBy == groupBy) {
                                        Icon(Icons.Outlined.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    moreExpanded = false
                                    onGroupByChange(groupBy)
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_shelves_title)) },
                            leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null) },
                            onClick = {
                                moreExpanded = false
                                onShelvesClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_recently_deleted_title)) },
                            leadingIcon = { Icon(Icons.Outlined.RestoreFromTrash, contentDescription = null) },
                            onClick = {
                                moreExpanded = false
                                onRecentlyDeletedClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_settings_title)) },
                            leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                            onClick = {
                                moreExpanded = false
                                onSettingsClick()
                            },
                        )
                    }
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
private fun LibraryTopBarIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.size(Sizes.touchTarget),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (enabled) 0.68f else 0.34f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.42f),
        tonalElevation = Elevations.none,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
        ) {
            content()
        }
    }
}

@Composable
private fun LibrarySyncTopBarIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    badge: LibrarySyncBadge?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.size(Sizes.touchTarget)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (enabled) 0.72f else 0.42f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (enabled) 1f else 0.42f),
            tonalElevation = Elevations.level1,
        ) {
            IconButton(
                onClick = onClick,
                enabled = enabled,
            ) {
                content()
            }
        }
        if (badge != null) {
            LibrarySyncStatusBadge(
                badge = badge,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun LibrarySyncStatusBadge(
    badge: LibrarySyncBadge,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (badge) {
        LibrarySyncBadge.SUCCESS -> MaterialTheme.colorScheme.primary
        LibrarySyncBadge.FAILED -> MaterialTheme.colorScheme.error
    }
    val contentColor = when (badge) {
        LibrarySyncBadge.SUCCESS -> MaterialTheme.colorScheme.onPrimary
        LibrarySyncBadge.FAILED -> MaterialTheme.colorScheme.onError
    }
    val icon = when (badge) {
        LibrarySyncBadge.SUCCESS -> Icons.Outlined.Check
        LibrarySyncBadge.FAILED -> Icons.Outlined.Close
    }
    Surface(
        modifier = modifier.size(Sizes.iconSmall),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = Elevations.level1,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Sizes.swatchSmall),
            )
        }
    }
}

@Composable
private fun LibraryAddFab(onImportFiles: () -> Unit, onImportFolder: () -> Unit) {
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

private data class LibraryDisplayBooks(val hero: Book?, val rows: List<Book>)
private data class LibraryGroupSection(val label: String, val books: List<Book>)

@Composable
private fun LibraryGrid(
    books: List<Book>,
    groupBy: LibraryGroupBy,
    contentPadding: PaddingValues,
    downloadingBookId: Long?,
    downloadProgress: Float?,
    onBookClick: (Book) -> Unit,
    onMarkFinished: (Book) -> Unit,
) {
    val displayBooks = rememberLibraryDisplayBooks(books)
    val sections = displayBooks.rows.toGroupSections(groupBy)

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
        displayBooks.hero?.let { heroBook ->
            item(key = "hero:${heroBook.id}", span = { GridItemSpan(maxLineSpan) }) {
                LibraryHeroCard(
                    book = heroBook,
                    isDownloading = heroBook.id == downloadingBookId,
                    downloadProgress = if (heroBook.id == downloadingBookId) downloadProgress else null,
                    onClick = { onBookClick(heroBook) },
                    onMarkFinished = { onMarkFinished(heroBook) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        if (sections == null) {
            gridItems(displayBooks.rows, key = { it.id }) { book ->
                BookCoverCell(
                    book = book,
                    isDownloading = book.id == downloadingBookId,
                    downloadProgress = if (book.id == downloadingBookId) downloadProgress else null,
                    onMarkFinished = { onMarkFinished(book) },
                    modifier = Modifier.animateItem(),
                    onClick = { onBookClick(book) },
                )
            }
        } else {
            sections.forEach { section ->
                item(key = "header:${section.label}", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = section.label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(top = Spacing.sm, bottom = Spacing.xs)
                            .animateItem(),
                    )
                }
                gridItems(section.books, key = { it.id }) { book ->
                    BookCoverCell(
                        book = book,
                        isDownloading = book.id == downloadingBookId,
                        downloadProgress = if (book.id == downloadingBookId) downloadProgress else null,
                        onMarkFinished = { onMarkFinished(book) },
                        modifier = Modifier.animateItem(),
                        onClick = { onBookClick(book) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryList(
    books: List<Book>,
    groupBy: LibraryGroupBy,
    contentPadding: PaddingValues,
    downloadingBookId: Long?,
    downloadProgress: Float?,
    onBookClick: (Book) -> Unit,
    onMarkFinished: (Book) -> Unit,
) {
    val displayBooks = rememberLibraryDisplayBooks(books)
    val sections = displayBooks.rows.toGroupSections(groupBy)

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
        displayBooks.hero?.let { heroBook ->
            item(key = "hero:${heroBook.id}") {
                LibraryHeroCard(
                    book = heroBook,
                    isDownloading = heroBook.id == downloadingBookId,
                    downloadProgress = if (heroBook.id == downloadingBookId) downloadProgress else null,
                    onClick = { onBookClick(heroBook) },
                    onMarkFinished = { onMarkFinished(heroBook) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        if (sections == null) {
            items(displayBooks.rows, key = { it.id }) { book ->
                LibraryListRow(
                    book = book,
                    isDownloading = book.id == downloadingBookId,
                    downloadProgress = if (book.id == downloadingBookId) downloadProgress else null,
                    onClick = { onBookClick(book) },
                    onMarkFinished = { onMarkFinished(book) },
                    modifier = Modifier.animateItem(),
                )
            }
        } else {
            sections.forEach { section ->
                item(key = "header:${section.label}") {
                    Text(
                        text = section.label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(top = Spacing.sm, bottom = Spacing.xs)
                            .animateItem(),
                    )
                }
                items(section.books, key = { it.id }) { book ->
                    LibraryListRow(
                        book = book,
                        isDownloading = book.id == downloadingBookId,
                        downloadProgress = if (book.id == downloadingBookId) downloadProgress else null,
                        onClick = { onBookClick(book) },
                        onMarkFinished = { onMarkFinished(book) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberLibraryDisplayBooks(books: List<Book>): LibraryDisplayBooks =
    remember(books) {
        val hero = books.maxByOrNull { it.lastReadAt ?: 0L }
            ?.takeIf { (it.lastReadAt ?: 0L) > 0L }
        LibraryDisplayBooks(
            hero = hero,
            rows = if (hero == null) books else books.filter { it.id != hero.id },
        )
    }

@Composable
private fun LibraryListRow(
    book: Book,
    isDownloading: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
    onMarkFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isDownloading, onClick = onClick),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.none,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(modifier = Modifier.width(Sizes.coverWidthMin * 0.58f)) {
                BookCover(book = book, modifier = Modifier.fillMaxWidth())
                BookFinishedTick(
                    book = book,
                    onMarkFinished = onMarkFinished,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
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
                book.series?.takeIf { it.isNotBlank() }?.let { series ->
                    Text(
                        text = book.seriesNumber?.takeIf { it.isNotBlank() }?.let { "$series · #$it" } ?: series,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LibraryListRowStatus(
                    book = book,
                    isDownloading = isDownloading,
                    downloadProgress = downloadProgress,
                )
            }
        }
    }
}

@Composable
private fun LibraryListRowStatus(book: Book, isDownloading: Boolean, downloadProgress: Float?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        when {
            isDownloading -> {
                CircularProgressIndicator(
                    progress = { downloadProgress ?: 0f },
                    modifier = Modifier.size(Sizes.iconSmall),
                    strokeWidth = Spacing.xs,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.library_book_cloud_downloading),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            book.fileAvailability == BookFileAvailability.CLOUD_ONLY -> {
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
            book.readingPercent > 0f -> {
                LinearProgressIndicator(
                    progress = { book.readingPercent.coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f),
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    text = stringResource(R.string.library_progress_value, (book.readingPercent * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            else -> {
                Text(
                    text = stringResource(R.string.library_imported_on, book.createdAt.formatDate()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LibraryHeroCard(
    book: Book,
    isDownloading: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
    onMarkFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isDownloading, onClick = onClick),
        shape = RoundedCornerShape(Radii.extraLargeIncreased),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.shadowSmall,
    ) {
        Row(
            modifier = Modifier.padding(Paddings.card),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                BookCover(
                    book = book,
                    modifier = Modifier
                        .width(Sizes.coverWidthMin)
                        .clip(RoundedCornerShape(Radii.small)),
                )
                BookFinishedTick(
                    book = book,
                    onMarkFinished = onMarkFinished,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
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
                        if (isDownloading) {
                            CircularProgressIndicator(
                                progress = { downloadProgress ?: 0f },
                                modifier = Modifier.size(Sizes.iconSmall),
                                strokeWidth = Spacing.xs,
                            )
                        } else {
                            Icon(
                                imageVector = if (book.fileAvailability == BookFileAvailability.CLOUD_ONLY) Icons.Outlined.CloudDownload else Icons.Outlined.AutoStories,
                                contentDescription = null,
                                modifier = Modifier.size(Sizes.iconSmall),
                            )
                        }
                        Text(
                            text = if (isDownloading) {
                                stringResource(R.string.library_book_cloud_downloading)
                            } else if (book.fileAvailability == BookFileAvailability.CLOUD_ONLY) {
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
    isDownloading: Boolean,
    downloadProgress: Float?,
    onMarkFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier.clickable(enabled = !isDownloading, onClick = onClick)) {
        Box {
            BookCover(book = book, modifier = Modifier.fillMaxWidth())
            BookFinishedTick(
                book = book,
                onMarkFinished = onMarkFinished,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        Text(
            text = book.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        if (isDownloading) {
            LinearProgressIndicator(
                progress = { downloadProgress ?: 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                strokeCap = StrokeCap.Round,
            )
        } else if (book.readingPercent > 0f) {
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
                if (isDownloading) {
                    CircularProgressIndicator(
                        progress = { downloadProgress ?: 0f },
                        modifier = Modifier.size(Sizes.iconSmall),
                        strokeWidth = Spacing.xs,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(Sizes.iconSmall),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = if (isDownloading) {
                        stringResource(R.string.library_book_cloud_downloading)
                    } else {
                        stringResource(R.string.library_cloud_book_badge)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Tick over a cover's corner: a tappable outline that marks the book finished, or a filled badge once it is.
 * Uses the same "finished" test as the detail screen's menu so the two never disagree.
 */
@Composable
private fun BookFinishedTick(book: Book, onMarkFinished: () -> Unit, modifier: Modifier = Modifier) {
    val isFinished = book.finishedReadingAt != null || book.readingPercent >= 1f
    val badge = @Composable { description: String ->
        Surface(
            shape = CircleShape,
            color = if (isFinished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (isFinished) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            tonalElevation = Elevations.shadowSmall,
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = description,
                modifier = Modifier
                    .padding(Spacing.xs)
                    .size(Sizes.iconSmall),
            )
        }
    }
    if (isFinished) {
        Box(modifier = modifier.size(Sizes.touchTarget), contentAlignment = Alignment.Center) {
            badge(stringResource(R.string.library_book_finished))
        }
    } else {
        IconButton(onClick = onMarkFinished, modifier = modifier.size(Sizes.touchTarget)) {
            badge(stringResource(R.string.library_mark_finished))
        }
    }
}

private data class BookShareImageOptions(
    val layout: BookShareCardLayout = BookShareCardLayout.CLASSIC,
    val theme: ShareCardTheme = ShareCardTheme.LIGHT,
    val showCover: Boolean = true,
    val showAuthor: Boolean = true,
    val showStatus: Boolean = true,
    val showProgress: Boolean = true,
    val showReadTime: Boolean = true,
    val showRating: Boolean = true,
    val showTags: Boolean = true,
    /** Mutually exclusive with [showTags]; the options panel keeps at most one of them on. */
    val showImportedDate: Boolean = false,
    val showTagline: Boolean = true,
)

@Composable
private fun BookDetailScreen(
    modifier: Modifier = Modifier,
    book: Book?,
    showSyncReadingProgress: Boolean,
    syncReadingProgressRunning: Boolean,
    progressChangePrompt: BookProgressChange?,
    onKeepSyncedProgress: () -> Unit,
    onRevertSyncedProgress: (BookProgressChange) -> Unit,
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
    onUpdateMetadata: (String, String, String, String, String, String) -> Unit,
    onUpdateRating: (Float) -> Unit,
    onReplaceSource: (android.content.ContentResolver, Uri) -> Unit,
    onReplaceCover: (android.content.ContentResolver, Uri) -> Unit,
    onRemoveCover: () -> Unit,
    onRemoveFromDevice: () -> Unit,
    onSyncReadingProgress: suspend () -> GitHubSyncNowResult,
    onImportQuotes: (String) -> Unit,
    onImportQuotesFile: (Uri) -> Unit,
    onDetailMessageShown: () -> Unit,
    onMarkFinished: () -> Unit,
    onDeleteBook: () -> Unit,
    goodreadsImport: GoodreadsImportState,
    onImportGoodreads: (String) -> Unit,
    onImportGoodreadsCapture: (GoodreadsBookMetadata, List<ParsedQuote>?) -> Unit,
    onApplyGoodreads: () -> Unit,
    onResetReadingStats: () -> Unit,
    onDismissGoodreads: () -> Unit,
    onUseCover: (CoverSource) -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showEditDescriptionDialog by remember { mutableStateOf(false) }
    var showCoverPreview by remember { mutableStateOf(false) }
    var showImportQuotesDialog by remember { mutableStateOf(false) }
    var showGoodreadsDialog by remember { mutableStateOf(false) }
    var goodreadsBrowserUrl by remember { mutableStateOf<String?>(null) }
    var showResetStatsDialog by remember { mutableStateOf(false) }
    var showEditCoverDialog by remember { mutableStateOf(false) }
    var showShareBookDialog by remember { mutableStateOf(false) }
    var showRemoveFromDeviceDialog by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }
    var shareImageOptions by remember { mutableStateOf(BookShareImageOptions()) }
    val detailMessageText = detailMessage?.label()
    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onReplaceSource(context.contentResolver, uri)
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onReplaceCover(context.contentResolver, uri)
    }
    val shareBookTitle = stringResource(R.string.library_share_book)
    val syncProgressOnlyCompleteMessage = stringResource(R.string.library_sync_progress_only_complete)
    val syncProgressOnlyConflictsMessage = stringResource(R.string.library_sync_progress_only_conflicts)
    val syncProgressOnlyFailedMessage = stringResource(R.string.library_sync_progress_only_failed)
    val syncDisabledMessage = stringResource(R.string.library_sync_disabled)
    val syncConfigMissingMessage = stringResource(R.string.library_sync_config_missing)
    val syncStartedMessage = stringResource(R.string.library_sync_started)

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
                Spacer(modifier = Modifier.weight(1f))
                if (book != null) {
                    Box {
                        IconButton(onClick = { actionsExpanded = true }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.library_book_actions_content_description),
                            )
                        }
                        DropdownMenu(
                            expanded = actionsExpanded,
                            onDismissRequest = { actionsExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_goodreads_import)) },
                                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                                onClick = {
                                    actionsExpanded = false
                                    onDismissGoodreads()
                                    showGoodreadsDialog = true
                                },
                            )
                            book.goodreadsUrl?.takeIf { it.isNotBlank() }?.let { goodreadsUrl ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_goodreads_refresh)) },
                                    leadingIcon = { Icon(Icons.Outlined.Sync, contentDescription = null) },
                                    onClick = {
                                        actionsExpanded = false
                                        onDismissGoodreads()
                                        showGoodreadsDialog = true
                                        onImportGoodreads(goodreadsUrl)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_import_quotes)) },
                                leadingIcon = { Icon(Icons.Outlined.EditNote, contentDescription = null) },
                                onClick = {
                                    actionsExpanded = false
                                    showImportQuotesDialog = true
                                },
                            )
                            if (book.hasLocalReadableSource()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_share_file)) },
                                    leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                    onClick = {
                                        actionsExpanded = false
                                        context.shareBookFile(book)
                                    },
                                )
                            }
                            if (book.canRemoveLocalFileFromDevice()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_remove_from_device)) },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                    onClick = {
                                        actionsExpanded = false
                                        showRemoveFromDeviceDialog = true
                                    },
                                )
                            }
                            if (book.format != BookFormat.PHYSICAL) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_replace_source_file)) },
                                    leadingIcon = { Icon(Icons.Outlined.AutoStories, contentDescription = null) },
                                    onClick = {
                                        actionsExpanded = false
                                        sourcePicker.launch(arrayOf("application/epub+zip", "application/octet-stream", "*/*"))
                                    },
                                )
                            }
                            if (book.hasReadingStats()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_reset_reading_stats)) },
                                    leadingIcon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                                    onClick = {
                                        actionsExpanded = false
                                        showResetStatsDialog = true
                                    },
                                )
                            }
                            if (book.finishedReadingAt == null && book.readingPercent < 1f) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_mark_finished)) },
                                    leadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) },
                                    onClick = {
                                        actionsExpanded = false
                                        onMarkFinished()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.library_delete_book),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    actionsExpanded = false
                                    showDeleteDialog = true
                                },
                            )
                        }
                    }
                }
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
                        // Cover on the left, identity (title, author, series, Goodreads) beside it; cover editing
                        // lives in its own dialog so the top of the screen stays compact.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                BookCover(
                                    book = book,
                                    modifier = Modifier
                                        .size(width = Sizes.coverWidthDetail, height = Sizes.coverWidthDetail / Sizes.coverAspectRatio)
                                        .clickable { showCoverPreview = true },
                                )
                                TextButton(
                                    onClick = { showEditCoverDialog = true },
                                    contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(Sizes.iconSmall),
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(stringResource(R.string.library_edit_cover), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
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
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = Spacing.xs),
                                    )
                                }
                                GoodreadsInfoLine(book = book, modifier = Modifier.padding(top = Spacing.sm))
                            }
                        }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            BookRatingRow(
                                rating = book.rating,
                                onRatingChange = onUpdateRating,
                            )
                            BookTagsRow(
                                tags = book.tags(),
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.library_progress_value, (book.readingPercent * 100).roundToInt()),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (showSyncReadingProgress) {
                            TextButton(
                                onClick = {
                                    if (syncReadingProgressRunning) return@TextButton
                                    coroutineScope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val startedSnackbar = launch {
                                            snackbarHostState.showSnackbar(syncStartedMessage)
                                        }
                                        when (val result = onSyncReadingProgress()) {
                                            is GitHubSyncNowResult.Complete -> {
                                                startedSnackbar.cancel()
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                val message = when {
                                                    result.pullFailed || !result.metadataSynced -> syncProgressOnlyFailedMessage.format(result.failureMessage.orEmpty())
                                                    result.conflicts > 0 -> syncProgressOnlyConflictsMessage.format(
                                                        result.progressUpdated,
                                                        result.progressUploaded,
                                                        result.conflicts,
                                                    )
                                                    else -> syncProgressOnlyCompleteMessage.format(result.progressUpdated, result.progressUploaded)
                                                }
                                                snackbarHostState.showSnackbar(message)
                                            }
                                            is GitHubSyncNowResult.InitialSyncConfirmationRequired -> {
                                                startedSnackbar.cancel()
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                snackbarHostState.showSnackbar(syncProgressOnlyFailedMessage.format(result.message))
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
                                },
                                enabled = !syncReadingProgressRunning,
                                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs),
                            ) {
                                if (syncReadingProgressRunning) {
                                    VayanaCircularProgressIndicator(
                                        modifier = Modifier.size(Sizes.iconSmall),
                                        strokeWidth = Spacing.xs,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(Sizes.iconSmall),
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.library_sync_progress_button),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(start = Spacing.xs),
                                )
                            }
                        }
                    }
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
                                Icon(if (isQueued) Icons.Outlined.Check else Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = null)
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
                    }
                }
                item { Spacer(modifier = Modifier.height(Sizes.bottomNavHeight)) }
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmActionDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = Icons.Outlined.Delete,
            title = stringResource(R.string.library_delete_title),
            body = stringResource(R.string.library_delete_body),
            confirmLabel = stringResource(R.string.library_delete_confirm),
            dismissLabel = stringResource(R.string.settings_reset_all_cancel),
            destructive = true,
            onConfirm = {
                showDeleteDialog = false
                onDeleteBook()
            },
        )
    }

    if (showRemoveFromDeviceDialog) {
        ConfirmActionDialog(
            onDismissRequest = { showRemoveFromDeviceDialog = false },
            icon = Icons.Outlined.CloudDownload,
            title = stringResource(R.string.library_remove_from_device_title),
            body = stringResource(R.string.library_remove_from_device_body),
            confirmLabel = stringResource(R.string.library_remove_from_device_confirm),
            dismissLabel = stringResource(R.string.settings_reset_all_cancel),
            onConfirm = {
                showRemoveFromDeviceDialog = false
                onRemoveFromDevice()
            },
        )
    }

    progressChangePrompt?.let { prompt ->
        ReadingProgressSyncDialog(
            prompt = prompt,
            onKeepSyncedProgress = onKeepSyncedProgress,
            onRevertSyncedProgress = { onRevertSyncedProgress(prompt) },
        )
    }

    if (showEditDialog && book != null) {
        EditMetadataDialog(
            book = book,
            libraryBooks = libraryBooks,
            onDismiss = { showEditDialog = false },
            onSave = { title, author, series, seriesNumber, tagsCsv ->
                showEditDialog = false
                onUpdateMetadata(title, author, series, seriesNumber, book.description.orEmpty(), tagsCsv)
            },
        )
    }

    if (showEditDescriptionDialog && book != null) {
        EditDescriptionDialog(
            description = book.description.orEmpty(),
            onDismiss = { showEditDescriptionDialog = false },
            onSave = { description ->
                showEditDescriptionDialog = false
                onUpdateMetadata(book.title, book.author.orEmpty(), book.series.orEmpty(), book.seriesNumber.orEmpty(), description, book.tagsCsv.orEmpty())
            },
        )
    }

    if (showGoodreadsDialog && book != null) {
        LaunchedEffect(goodreadsImport) {
            if (goodreadsImport is GoodreadsImportState.Done) {
                showGoodreadsDialog = false
                onDismissGoodreads()
            }
        }
        when (val importState = goodreadsImport) {
            is GoodreadsImportState.Preview -> GoodreadsPreviewDialog(
                book = book,
                metadata = importState.metadata,
                capturedQuoteCount = importState.capturedQuotes?.size,
                onApply = onApplyGoodreads,
                onDismiss = {
                    showGoodreadsDialog = false
                    onDismissGoodreads()
                },
            )
            else -> GoodreadsImportDialog(
                state = importState,
                initialLink = book.goodreadsUrl.orEmpty(),
                browseFallbackQuery = listOfNotNull(book.title, book.author).joinToString(" "),
                onImport = onImportGoodreads,
                onBrowse = { url ->
                    showGoodreadsDialog = false
                    onDismissGoodreads()
                    goodreadsBrowserUrl = url
                },
                onDismiss = {
                    showGoodreadsDialog = false
                    onDismissGoodreads()
                },
            )
        }
    }

    if (showResetStatsDialog && book != null) {
        ConfirmActionDialog(
            onDismissRequest = { showResetStatsDialog = false },
            icon = Icons.Outlined.RestartAlt,
            title = stringResource(R.string.library_reset_reading_stats_title),
            body = stringResource(R.string.library_reset_reading_stats_body, book.title),
            confirmLabel = stringResource(R.string.library_reset_reading_stats_confirm),
            dismissLabel = stringResource(R.string.library_edit_metadata_cancel),
            destructive = true,
            onConfirm = {
                showResetStatsDialog = false
                onResetReadingStats()
            },
        )
    }

    goodreadsBrowserUrl?.let { url ->
        GoodreadsBrowserDialog(
            startUrl = url,
            onCaptured = { capture ->
                goodreadsBrowserUrl = null
                // Reopen the import dialog so the cover download shows progress, then closes itself when done.
                showGoodreadsDialog = true
                onImportGoodreadsCapture(capture.metadata, capture.quotes)
            },
            onDismiss = { goodreadsBrowserUrl = null },
        )
    }

    if (showEditCoverDialog && book != null) {
        EditCoverDialog(
            book = book,
            onChangeCover = { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemoveCover = onRemoveCover,
            onUseCover = onUseCover,
            onDismiss = { showEditCoverDialog = false },
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
            shareImageFileName = book.shareFileBaseName(),
            title = shareBookTitle,
            options = {
                BookShareImageOptionsPanel(
                    options = shareImageOptions,
                    hasRating = book.rating > 0f,
                    hasTags = book.tags().isNotEmpty(),
                    onOptionsChange = { shareImageOptions = it },
                )
            },
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
                ratingValue = book.rating.takeIf { it > 0f }?.let { stringResource(R.string.share_card_stat_rating_value, it) },
                ratingLabel = stringResource(R.string.share_card_stat_rating_label),
                tags = book.tags(),
                footerLeft = stringResource(R.string.library_imported_on, book.createdAt.formatDate()),
                footerRight = stringResource(R.string.share_card_tagline),
                watermark = stringResource(R.string.share_card_watermark),
                theme = shareImageOptions.theme,
                showCover = shareImageOptions.showCover,
                showAuthor = shareImageOptions.showAuthor,
                showStatus = shareImageOptions.showStatus,
                showProgress = shareImageOptions.showProgress,
                showReadTime = shareImageOptions.showReadTime,
                showRating = shareImageOptions.showRating,
                showTags = shareImageOptions.showTags,
                showImportedDate = shareImageOptions.showImportedDate,
                showTagline = shareImageOptions.showTagline,
                layout = shareImageOptions.layout,
                progressFraction = book.readingPercent,
            ) {
                // Fill whatever box the layout gives the cover, including Backdrop's full square.
                BookCover(book = book, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Inline controls under the share-card preview; each change redraws the card above immediately. */
@Composable
private fun BookShareImageOptionsPanel(
    options: BookShareImageOptions,
    hasRating: Boolean,
    hasTags: Boolean,
    onOptionsChange: (BookShareImageOptions) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ShareImageOptionsLabel(stringResource(R.string.share_card_image_options_layout))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            BookShareCardLayout.entries.forEachIndexed { index, layout ->
                SegmentedButton(
                    selected = options.layout == layout,
                    onClick = { onOptionsChange(options.copy(layout = layout)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = BookShareCardLayout.entries.size),
                    label = {
                        Text(
                            stringResource(
                                when (layout) {
                                    BookShareCardLayout.CLASSIC -> R.string.share_card_image_options_layout_classic
                                    BookShareCardLayout.SPOTLIGHT -> R.string.share_card_image_options_layout_spotlight
                                    BookShareCardLayout.MINIMAL -> R.string.share_card_image_options_layout_minimal
                                    BookShareCardLayout.BACKDROP -> R.string.share_card_image_options_layout_backdrop
                                },
                            ),
                            maxLines = 1,
                        )
                    },
                )
            }
        }
        ShareImageOptionsLabel(stringResource(R.string.share_card_image_options_theme))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ShareCardTheme.entries.forEachIndexed { index, theme ->
                SegmentedButton(
                    selected = options.theme == theme,
                    onClick = { onOptionsChange(options.copy(theme = theme)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ShareCardTheme.entries.size),
                    label = {
                        Text(
                            stringResource(
                                when (theme) {
                                    ShareCardTheme.LIGHT -> R.string.share_card_image_options_light
                                    ShareCardTheme.DARK -> R.string.share_card_image_options_dark
                                },
                            ),
                        )
                    },
                )
            }
        }
        ShareImageOptionsLabel(stringResource(R.string.share_card_image_options_include))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            // Chips a layout never draws are hidden rather than left as toggles that change nothing.
            if (options.layout != BookShareCardLayout.MINIMAL) {
                ShareImageOptionChip(
                    selected = options.showCover,
                    label = stringResource(R.string.share_card_image_options_cover),
                    onClick = { onOptionsChange(options.copy(showCover = !options.showCover)) },
                )
            }
            ShareImageOptionChip(
                selected = options.showAuthor,
                label = stringResource(R.string.share_card_image_options_author),
                onClick = { onOptionsChange(options.copy(showAuthor = !options.showAuthor)) },
            )
            ShareImageOptionChip(
                selected = options.showStatus,
                label = stringResource(R.string.share_card_image_options_status),
                onClick = { onOptionsChange(options.copy(showStatus = !options.showStatus)) },
            )
            ShareImageOptionChip(
                selected = options.showProgress,
                label = stringResource(R.string.share_card_image_options_progress),
                onClick = { onOptionsChange(options.copy(showProgress = !options.showProgress)) },
            )
            ShareImageOptionChip(
                selected = options.showReadTime,
                label = stringResource(R.string.share_card_image_options_read_time),
                onClick = { onOptionsChange(options.copy(showReadTime = !options.showReadTime)) },
            )
            // An unrated book has nothing to show here, so the toggle would change nothing in the preview.
            if (hasRating) {
                ShareImageOptionChip(
                    selected = options.showRating,
                    label = stringResource(R.string.share_card_image_options_rating),
                    onClick = { onOptionsChange(options.copy(showRating = !options.showRating)) },
                )
            }
            // Same as rating: a book without tags would get a toggle that changes nothing.
            if (hasTags) {
                ShareImageOptionChip(
                    selected = options.showTags,
                    label = stringResource(R.string.share_card_image_options_tags),
                    // Tags and the imported date compete for the same space, so showing one hides the other.
                    onClick = {
                        val showTags = !options.showTags
                        onOptionsChange(options.copy(showTags = showTags, showImportedDate = options.showImportedDate && !showTags))
                    },
                )
            }
            ShareImageOptionChip(
                selected = options.showImportedDate,
                label = stringResource(R.string.share_card_image_options_imported_date),
                onClick = {
                    val showImportedDate = !options.showImportedDate
                    onOptionsChange(options.copy(showImportedDate = showImportedDate, showTags = options.showTags && !showImportedDate))
                },
            )
            ShareImageOptionChip(
                selected = options.showTagline,
                label = stringResource(R.string.share_card_image_options_tagline),
                onClick = { onOptionsChange(options.copy(showTagline = !options.showTagline)) },
            )
        }
    }
}

@Composable
private fun ShareImageOptionsLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs),
    )
}

@Composable
private fun ShareImageOptionChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun BookRatingRow(
    rating: Float,
    onRatingChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedRating = ((rating * 2f).roundToInt() / 2f).coerceIn(0f, 5f)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.library_rating_label),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (normalizedRating > 0f) {
                    stringResource(R.string.library_rating_value, normalizedRating)
                } else {
                    stringResource(R.string.library_rating_unrated)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            repeat(5) { index ->
                val starNumber = index + 1
                HalfStepStar(
                    rating = normalizedRating,
                    starNumber = starNumber,
                    onRatingChange = onRatingChange,
                )
            }
            if (normalizedRating > 0f) {
                TextButton(onClick = { onRatingChange(0f) }) {
                    Text(stringResource(R.string.library_rating_clear))
                }
            }
        }
    }
}

@Composable
private fun BookTagsRow(
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        tags.forEach { tag ->
            Surface(
                shape = RoundedCornerShape(Radii.full),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun HalfStepStar(
    rating: Float,
    starNumber: Int,
    onRatingChange: (Float) -> Unit,
) {
    val halfRating = starNumber - 0.5f
    val fullRating = starNumber.toFloat()
    val halfLabel = stringResource(R.string.library_rating_set_content_description, halfRating)
    val fullLabel = stringResource(R.string.library_rating_set_content_description, fullRating)
    Box(
        modifier = Modifier
            .size(Sizes.touchTarget),
        contentAlignment = Alignment.Center,
    ) {
        val icon = when {
            rating >= fullRating -> Icons.Filled.Star
            rating >= halfRating -> Icons.AutoMirrored.Filled.StarHalf
            else -> Icons.Outlined.StarBorder
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (rating >= halfRating) Palette.Gold500 else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(Sizes.iconLarge),
        )
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(onClickLabel = halfLabel) { onRatingChange(halfRating) },
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(onClickLabel = fullLabel) { onRatingChange(fullRating) },
            )
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
        ExpressiveDialogSurface(onDismissRequest = { showAddDialog = false }, scrollable = true) {
            ExpressiveDialogHeader(
                icon = Icons.Outlined.CollectionsBookmark,
                title = stringResource(R.string.library_shelves_add_to_shelf),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            allShelves.forEach { shelf ->
                val onShelf = shelf.id in memberIds
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (onShelf) onRemoveFromShelf(shelf.id) else onAddToShelf(shelf.id)
                        },
                    shape = RoundedCornerShape(Radii.medium),
                    color = if (onShelf) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = if (onShelf) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Checkbox(checked = onShelf, onCheckedChange = { checked -> if (checked) onAddToShelf(shelf.id) else onRemoveFromShelf(shelf.id) })
                        Text(
                            text = shelf.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            OutlinedTextField(
                value = newShelfName,
                onValueChange = { newShelfName = it },
                singleLine = true,
                label = { Text(stringResource(R.string.library_shelves_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radii.medium),
                colors = expressiveTextFieldColors(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = { showAddDialog = false }, shape = Radii.buttonShape) {
                    Text(stringResource(R.string.settings_reset_all_cancel))
                }
                Button(
                    onClick = {
                        if (newShelfName.isNotBlank()) onCreateShelf(newShelfName)
                        showAddDialog = false
                    },
                    shape = Radii.buttonShape,
                ) { Text(if (newShelfName.isNotBlank()) stringResource(R.string.library_shelves_create) else stringResource(R.string.notes_edit_save)) }
            }
        }
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
    onSave: (String, String, String, String, String) -> Unit,
) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author.orEmpty()) }
    var series by remember(book.id) { mutableStateOf(book.series.orEmpty()) }
    var seriesNumber by remember(book.id) { mutableStateOf(book.seriesNumber.orEmpty()) }
    var tagsCsv by remember(book.id) { mutableStateOf(book.tagsCsv.orEmpty().take(MaxBookTagsInputChars)) }
    val authorSuggestions = remember(libraryBooks) { libraryBooks.metadataSuggestions { it.author } }
    val seriesSuggestions = remember(libraryBooks) { libraryBooks.metadataSuggestions { it.series } }
    val tagSuggestions = remember(libraryBooks) { libraryBooks.flatMap { it.tags() }.distinctSortedIgnoreCase() }
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

                TagSuggestionField(
                    value = tagsCsv,
                    onValueChange = { tagsCsv = it.take(MaxBookTagsInputChars) },
                    suggestions = tagSuggestions,
                    textFieldColors = textFieldColors,
                )

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
                        onClick = { onSave(title, author, series, seriesNumber, tagsCsv) },
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
private fun TagSuggestionField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    textFieldColors: androidx.compose.material3.TextFieldColors,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val matches = remember(value, suggestions) { suggestions.matchingTagSuggestions(value) }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            label = { Text(stringResource(R.string.library_edit_metadata_tags_label)) },
            supportingText = { Text(stringResource(R.string.library_edit_metadata_tags_supporting)) },
            singleLine = true,
            shape = RoundedCornerShape(Radii.medium),
            colors = textFieldColors,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = {
                        onValueChange("")
                        focusRequester.requestFocus()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.input_clear_content_description),
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )

        if (matches.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                matches.take(6).forEach { suggestion ->
                    SuggestionChip(
                        onClick = {
                            onValueChange(value.withCurrentTagSuggestion(suggestion))
                            focusRequester.requestFocus()
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

/**
 * Everything cover-related in one place: a live preview, the Your cover / Goodreads switch (when both exist),
 * and change/remove. Changes apply straight away, so the preview always shows what the library will.
 */
@Composable
private fun EditCoverDialog(
    book: Book,
    onChangeCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onUseCover: (CoverSource) -> Unit,
    onDismiss: () -> Unit,
) {
    ExpressiveDialogSurface(onDismissRequest = onDismiss) {
        ExpressiveDialogHeader(
            icon = Icons.Outlined.Image,
            title = stringResource(R.string.library_edit_cover),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Surface(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(Radii.large),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        ) {
            BookCover(
                book = book,
                modifier = Modifier
                    .padding(Spacing.md)
                    .size(width = Sizes.coverWidthMax, height = Sizes.coverWidthMax / Sizes.coverAspectRatio),
            )
        }
        if (book.customCoverPath != null && book.goodreadsCoverPath != null) {
            CoverSourceSwitch(
                selected = if (book.coverPath == book.goodreadsCoverPath) CoverSource.GOODREADS else CoverSource.CUSTOM,
                onSelect = onUseCover,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OutlinedButton(
                onClick = onRemoveCover,
                enabled = book.coverPath != null,
                modifier = Modifier.weight(1f),
                shape = Radii.buttonShape,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.library_remove_cover), maxLines = 1)
            }
            Button(
                onClick = onChangeCover,
                modifier = Modifier.weight(1f),
                shape = Radii.buttonShape,
            ) {
                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.library_change_cover), maxLines = 1)
            }
        }
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = Radii.buttonShape,
        ) {
            Text(stringResource(R.string.library_edit_cover_done))
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

@Composable
private fun GitHubSyncStepStatus.containerAndContentColor() = when (this) {
    GitHubSyncStepStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    GitHubSyncStepStatus.DONE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    GitHubSyncStepStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    GitHubSyncStepStatus.WAITING -> MaterialTheme.colorScheme.surfaceContainerLow to MaterialTheme.colorScheme.onSurfaceVariant
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

private fun GitHubSyncProgressState.detailFor(step: GitHubSyncProgressStep, status: GitHubSyncStepStatus): String? =
    detail.takeIf {
        step == GitHubSyncProgressStep.SAVING_SNAPSHOT &&
            this.step == step &&
            status == GitHubSyncStepStatus.RUNNING
    }

@Composable
private fun BookDetailMessage.label(): String = when (this) {
    BookDetailMessage.METADATA_SAVED -> stringResource(R.string.library_metadata_saved)
    BookDetailMessage.RATING_SAVED -> stringResource(R.string.library_rating_saved)
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
    is BookDetailMessage.QUOTES_IMPORTED -> stringResource(R.string.library_quotes_imported_message, added, skipped)
    BookDetailMessage.MARKED_FINISHED -> stringResource(R.string.library_marked_finished)
    BookDetailMessage.GOODREADS_APPLIED -> stringResource(R.string.library_goodreads_applied)
    is BookDetailMessage.GOODREADS_APPLIED_WITH_QUOTES -> stringResource(R.string.library_goodreads_applied_quotes, quotesAdded, quotesSkipped)
    BookDetailMessage.GOODREADS_COVER_FAILED -> stringResource(R.string.library_goodreads_cover_failed)
    BookDetailMessage.GOODREADS_QUOTES_FAILED -> stringResource(R.string.library_goodreads_quotes_failed)
    BookDetailMessage.READING_STATS_RESET -> stringResource(R.string.library_reading_stats_reset)
    BookDetailMessage.READING_STATS_RESET_FAILED -> stringResource(R.string.library_reading_stats_reset_failed)
    BookDetailMessage.GOODREADS_FAILED -> stringResource(R.string.library_goodreads_apply_failed)
}

/** Whether there's anything for "Reset reading stats" to clear. */
private fun Book.hasReadingStats(): Boolean =
    readingPercent > 0f || startedReadingAt != null || finishedReadingAt != null || totalReadingSeconds > 0L || lastReadAt != null

/** Goodreads rating and original year under the series line; tapping it opens the book on Goodreads. */
@Composable
private fun GoodreadsInfoLine(book: Book, modifier: Modifier = Modifier) {
    val rating = book.goodreadsRating?.let { value ->
        val count = book.goodreadsRatingsCount
        if (count != null) {
            stringResource(R.string.library_goodreads_rating_with_count, value, NumberFormat.getIntegerInstance().format(count))
        } else {
            stringResource(R.string.library_goodreads_rating, value)
        }
    }
    val year = book.originalPublicationYear?.let { stringResource(R.string.library_goodreads_first_published, it) }
    val text = listOfNotNull(rating, year).joinToString("  ·  ")
    if (text.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    val url = book.goodreadsUrl
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.then(if (url != null) Modifier.clickable { uriHandler.openUri(url) } else Modifier),
    )
}

@Composable
private fun CoverSourceSwitch(selected: CoverSource, onSelect: (CoverSource) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        CoverSource.entries.forEachIndexed { index, source ->
            SegmentedButton(
                selected = selected == source,
                onClick = { if (selected != source) onSelect(source) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = CoverSource.entries.size),
                label = {
                    Text(
                        text = stringResource(
                            when (source) {
                                CoverSource.CUSTOM -> R.string.library_cover_source_custom
                                CoverSource.GOODREADS -> R.string.library_cover_source_goodreads
                            },
                        ),
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
private fun ReadingProgressSyncDialog(
    prompt: BookProgressChange,
    onKeepSyncedProgress: () -> Unit,
    onRevertSyncedProgress: () -> Unit,
) {
    val previousPercent = (prompt.previousPercent * 100).roundToInt()
    val newPercent = (prompt.newPercent * 100).roundToInt()
    ExpressiveDialogSurface(onDismissRequest = onRevertSyncedProgress) {
        ExpressiveDialogHeader(
            icon = Icons.Outlined.Sync,
            title = stringResource(R.string.library_book_progress_sync_prompt_title),
            supportingText = stringResource(R.string.library_book_progress_sync_prompt_body, previousPercent, newPercent),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stackChoices = maxWidth < 360.dp
            if (stackChoices) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ProgressChoiceCard(
                        label = stringResource(R.string.library_book_progress_sync_prompt_local),
                        percent = previousPercent,
                        timestamp = prompt.previousLastReadAt ?: prompt.previousUpdatedAt,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ProgressChoiceCard(
                        label = stringResource(R.string.library_book_progress_sync_prompt_synced),
                        percent = newPercent,
                        timestamp = prompt.newLastReadAt ?: prompt.newUpdatedAt,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ProgressChoiceCard(
                        label = stringResource(R.string.library_book_progress_sync_prompt_local),
                        percent = previousPercent,
                        timestamp = prompt.previousLastReadAt ?: prompt.previousUpdatedAt,
                        modifier = Modifier.weight(1f),
                    )
                    ProgressChoiceCard(
                        label = stringResource(R.string.library_book_progress_sync_prompt_synced),
                        percent = newPercent,
                        timestamp = prompt.newLastReadAt ?: prompt.newUpdatedAt,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilledTonalButton(onClick = onRevertSyncedProgress, shape = Radii.buttonShape) {
                Text(stringResource(R.string.library_book_progress_sync_prompt_revert, previousPercent))
            }
            Button(onClick = onKeepSyncedProgress, shape = Radii.buttonShape) {
                Text(stringResource(R.string.library_book_progress_sync_prompt_keep))
            }
        }
    }
}

@Composable
private fun ProgressChoiceCard(label: String, percent: Int, timestamp: Long, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = Radii.cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        tonalElevation = Elevations.level1,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = timestamp.formatDate(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Paste a Goodreads link and read it into a preview. Applying the preview fills in details, cover and popular quotes.
 */
@Composable
private fun GoodreadsImportDialog(
    state: GoodreadsImportState,
    initialLink: String,
    browseFallbackQuery: String,
    onImport: (String) -> Unit,
    onBrowse: (url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var link by rememberSaveable { mutableStateOf(initialLink) }
    // The pasted book's page if there is one, otherwise a Goodreads search for this book.
    val browseUrl = goodreadsBookIdOf(link)?.let(::goodreadsBookUrl) ?: goodreadsSearchUrl(browseFallbackQuery)
    val working = state as? GoodreadsImportState.Working
    val canImport = link.isNotBlank() && working == null
    ExpressiveDialogSurface(
        // An import in flight can't be abandoned halfway; the dialog only closes once it's done or failed.
        onDismissRequest = { if (working == null) onDismiss() },
    ) {
        ExpressiveDialogHeader(
            icon = Icons.Outlined.Link,
            title = stringResource(R.string.library_goodreads_import),
            supportingText = stringResource(R.string.library_goodreads_import_detail),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            label = { Text(stringResource(R.string.library_goodreads_link_label)) },
            placeholder = { Text(stringResource(R.string.library_goodreads_link_hint)) },
            singleLine = true,
            enabled = working == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (canImport) onImport(link) }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radii.medium),
            colors = expressiveTextFieldColors(),
        )
        OutlinedButton(
            onClick = { onBrowse(browseUrl) },
            enabled = working == null,
            modifier = Modifier.fillMaxWidth(),
            shape = Radii.buttonShape,
        ) {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.library_goodreads_browse))
        }
        if (working != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radii.large),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    VayanaCircularProgressIndicator(modifier = Modifier.size(Sizes.iconSmall))
                    Text(
                        text = stringResource(
                            when (working.step) {
                                GoodreadsImportStep.FETCHING_BOOK -> R.string.library_goodreads_step_book
                                GoodreadsImportStep.FETCHING_COVER_AND_QUOTES -> R.string.library_goodreads_step_extras
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        (state as? GoodreadsImportState.Failed)?.let { failed ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radii.medium),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(
                    text = stringResource(
                        when (failed.error) {
                            GoodreadsFetchError.INVALID_LINK -> R.string.library_goodreads_invalid_link
                            GoodreadsFetchError.NOT_FOUND -> R.string.library_goodreads_not_found
                            GoodreadsFetchError.BLOCKED -> R.string.library_goodreads_blocked
                            GoodreadsFetchError.FAILED -> R.string.library_goodreads_failed
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onDismiss,
                enabled = working == null,
                shape = Radii.buttonShape,
            ) {
                Text(stringResource(R.string.library_edit_metadata_cancel))
            }
            Button(
                onClick = { onImport(link) },
                enabled = canImport,
                shape = Radii.buttonShape,
            ) {
                Text(stringResource(R.string.library_goodreads_import_action))
            }
        }
    }
}

@Composable
private fun GoodreadsPreviewDialog(
    book: Book,
    metadata: GoodreadsBookMetadata,
    capturedQuoteCount: Int?,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentGenres = book.tags()
    val proposedGenres = book.tagsCsv.withGoodreadsGenresPreview(metadata.genres)
    ExpressiveDialogSurface(onDismissRequest = onDismiss, scrollable = true) {
        ExpressiveDialogHeader(
            icon = Icons.Outlined.Link,
            title = stringResource(R.string.library_goodreads_preview_title),
            supportingText = stringResource(R.string.library_goodreads_preview_body),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            metadata.series?.let {
                GoodreadsPreviewRow(
                    label = stringResource(R.string.library_goodreads_preview_series),
                    current = book.seriesDisplayOrNone().ifBlank { stringResource(R.string.library_goodreads_preview_empty) },
                    proposed = listOfNotNull(it, metadata.seriesNumber?.let { number -> "#$number" }).joinToString(" "),
                )
            }
            metadata.description?.takeIf { it.isNotBlank() }?.let {
                GoodreadsPreviewRow(
                    label = stringResource(R.string.library_goodreads_preview_description),
                    current = book.description?.cleanHtml()?.take(PreviewTextLimit).orEmpty().ifBlank {
                        stringResource(R.string.library_goodreads_preview_empty)
                    },
                    proposed = it.take(PreviewTextLimit),
                )
            }
            if (metadata.genres.isNotEmpty()) {
                GoodreadsPreviewRow(
                    label = stringResource(R.string.library_goodreads_preview_tags),
                    current = currentGenres.joinToString(", ").ifBlank { stringResource(R.string.library_goodreads_preview_empty) },
                    proposed = proposedGenres.joinToString(", "),
                )
            }
            metadata.coverUrl?.let {
                GoodreadsPreviewRow(
                    label = stringResource(R.string.library_goodreads_preview_cover),
                    current = if (book.coverPath.isNullOrBlank()) {
                        stringResource(R.string.library_goodreads_preview_empty)
                    } else {
                        stringResource(R.string.library_goodreads_preview_present)
                    },
                    proposed = stringResource(R.string.library_goodreads_preview_goodreads_cover),
                )
            }
            val rating = metadata.averageRating
            if (rating != null || metadata.originalPublicationYear != null) {
                GoodreadsPreviewRow(
                    label = stringResource(R.string.library_goodreads_preview_goodreads_info),
                    current = listOfNotNull(
                        book.goodreadsRating?.let { stringResource(R.string.library_goodreads_rating, it) },
                        book.originalPublicationYear?.let { stringResource(R.string.library_goodreads_first_published, it) },
                    ).joinToString(" · ").ifBlank { stringResource(R.string.library_goodreads_preview_empty) },
                    proposed = listOfNotNull(
                        rating?.let { stringResource(R.string.library_goodreads_rating, it) },
                        metadata.originalPublicationYear?.let { stringResource(R.string.library_goodreads_first_published, it) },
                    ).joinToString(" · "),
                )
            }
            GoodreadsPreviewRow(
                label = stringResource(R.string.library_goodreads_preview_quotes),
                current = stringResource(R.string.library_goodreads_preview_current_quotes),
                proposed = capturedQuoteCount?.let { stringResource(R.string.library_goodreads_preview_captured_quotes, it) }
                    ?: stringResource(R.string.library_goodreads_preview_fetch_quotes),
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilledTonalButton(onClick = onDismiss, shape = Radii.buttonShape) {
                Text(stringResource(R.string.library_edit_metadata_cancel))
            }
            Button(onClick = onApply, shape = Radii.buttonShape) {
                Text(stringResource(R.string.library_goodreads_preview_apply))
            }
        }
    }
}

@Composable
private fun GoodreadsPreviewRow(label: String, current: String, proposed: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Radii.cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        tonalElevation = Elevations.level1,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.library_goodreads_preview_current, current),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.library_goodreads_preview_proposed, proposed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun android.content.Context.shareBookFile(book: Book) {
    val source = File(book.filePath)
    val sharedFile = source.copyToSharedBookFile(this, book.shareFileName(source))
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", sharedFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = book.format.shareMimeType()
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.library_share_file)))
}

private fun File.copyToSharedBookFile(context: android.content.Context, fileName: String): File {
    val dir = File(context.cacheDir, "shared_books").apply { mkdirs() }
    return copyTo(File(dir, fileName), overwrite = true)
}

private fun Book.shareFileName(source: File): String {
    val extension = source.extension.toShareFileExtension().ifBlank { format.defaultExtension() }
    val suffix = extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
    return "${shareFileBaseName()}$suffix"
}

private fun Book.shareFileBaseName(): String =
    "${title.toShareFileSegment(fallback = "Book")}_${author.orEmpty().toShareFileSegment(fallback = "UnknownAuthor")}"

private fun String.toShareFileSegment(fallback: String): String {
    val segment = split(Regex("[^\\p{L}\\p{N}]+"))
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
            }
        }
        .take(MaxSharedBookFileSegmentChars)
    return segment.ifBlank { fallback }
}

private fun String.toShareFileExtension(): String =
    lowercase(Locale.ROOT)
        .filter { it in 'a'..'z' || it in '0'..'9' }
        .take(MaxSharedBookFileExtensionChars)

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

private fun BookFormat.defaultExtension(): String = when (this) {
    BookFormat.EPUB -> "epub"
    BookFormat.PDF -> "pdf"
    BookFormat.TXT -> "txt"
    BookFormat.MOBI -> "mobi"
    BookFormat.AZW3 -> "azw3"
    BookFormat.FB2 -> "fb2"
    BookFormat.PHYSICAL -> ""
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

private fun List<String>.matchingTagSuggestions(value: String): List<String> {
    val existingTags = value.tags().map { it.metadataKey() }.toSet()
    val query = value.substringAfterLast(',').normalizedBookTag()
    if (query.isEmpty()) return emptyList()
    return filter { suggestion ->
        suggestion.contains(query, ignoreCase = true) &&
            suggestion.metadataKey() !in existingTags &&
            !suggestion.equals(query, ignoreCase = true)
    }.take(TagSuggestionLimit)
}

private fun List<String>.distinctSortedIgnoreCase(): List<String> =
    map { it.normalizedBookTag() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.metadataKey() }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

private fun Book.tags(): List<String> = tagsCsv.orEmpty().tags()

private fun Book.seriesDisplayOrNone(): String =
    listOfNotNull(series?.takeIf { it.isNotBlank() }, seriesNumber?.takeIf { it.isNotBlank() }?.let { "#$it" })
        .joinToString(" ")

private fun String?.withGoodreadsGenresPreview(genres: List<String>): List<String> =
    (orEmpty().split(",").map { it.trim() } + genres.take(GoodreadsMaxGenreTags))
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }

private fun String.tags(): List<String> =
    split(",")
        .map { it.normalizedBookTag() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.metadataKey() }
        .take(MaxBookTags)

private fun String.withCurrentTagSuggestion(suggestion: String): String {
    val before = substringBeforeLast(',', missingDelimiterValue = "")
    val prefix = before.trim().takeIf { it.isNotEmpty() }
    val cleanSuggestion = suggestion.normalizedBookTag()
    if (cleanSuggestion.isBlank()) return this
    return if (contains(',') && prefix != null) {
        "$prefix, $cleanSuggestion, "
    } else {
        "$cleanSuggestion, "
    }
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

private fun String.normalizedBookTag(): String =
    map { if (Character.isISOControl(it)) ' ' else it }
        .joinToString("")
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(MaxBookTagChars)
        .trim()

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
        shape = RoundedCornerShape(Radii.extraLarge),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        tonalElevation = Elevations.level1,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Timer,
                        contentDescription = null,
                        modifier = Modifier.padding(Spacing.sm),
                    )
                }
                Text(
                    text = stringResource(R.string.library_reading_stats_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                ReadingStatPill(
                    label = stringResource(R.string.library_stat_started),
                    value = startedAt.formatDate(),
                )
                if (isFinished) {
                    ReadingStatPill(
                        label = stringResource(R.string.library_stat_finished),
                        value = (book.finishedReadingAt ?: book.updatedAt).formatDate(),
                    )
                }
                ReadingStatPill(
                    label = stringResource(R.string.library_stat_time_taken),
                    value = timeTakenText,
                )
                ReadingStatPill(
                    label = stringResource(R.string.library_stat_days_taken),
                    value = daysTakenText,
                )
            }
        }
    }
}

@Composable
private fun ReadingStatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(min = 132.dp),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
private const val TagSuggestionLimit = 6
private const val MaxBookTags = 32
private const val MaxBookTagChars = 40
private const val MaxBookTagsInputChars = 1_024
private const val MaxSharedBookFileSegmentChars = 80
private const val MaxSharedBookFileExtensionChars = 8
private const val PreviewTextLimit = 180
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

    ExpressiveDialogSurface(onDismissRequest = onDismiss, scrollable = true) {
        ExpressiveDialogHeader(
            icon = Icons.Outlined.EditNote,
            title = stringResource(R.string.library_import_quotes_dialog_title),
            supportingText = stringResource(R.string.library_import_quotes_subtitle),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = { Text(stringResource(R.string.library_import_quotes_tab_paste)) },
                shape = Radii.chipShape,
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = { Text(stringResource(R.string.library_import_quotes_tab_file)) },
                shape = Radii.chipShape,
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
                shape = RoundedCornerShape(Radii.medium),
                colors = expressiveTextFieldColors(),
            )

            if (parsedQuotes.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radii.large),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = stringResource(R.string.library_import_quotes_detected, parsedQuotes.size),
                            style = MaterialTheme.typography.titleSmall,
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
                                    text = "\"${quote.quoteText.take(45)}...\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Surface(
                                    shape = RoundedCornerShape(Radii.small),
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ) {
                                    Text(
                                        text = "${quote.highlightsCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                    )
                                }
                            }
                        }
                        if (parsedQuotes.size > 3) {
                            Text(
                                text = "+ ${parsedQuotes.size - 3} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
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
                shape = Radii.buttonShape,
            ) {
                Icon(Icons.Outlined.EditNote, contentDescription = null)
                Text(
                    text = stringResource(R.string.library_import_quotes_file_button),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = onDismiss, shape = Radii.buttonShape) {
                Text(stringResource(R.string.settings_reset_all_cancel))
            }
            if (selectedTab == 0) {
                Button(
                    onClick = { onImportText(rawText) },
                    enabled = parsedQuotes.isNotEmpty(),
                    shape = Radii.buttonShape,
                ) {
                    Text(stringResource(R.string.library_import_quotes_confirm, parsedQuotes.size))
                }
            }
        }
    }
}
