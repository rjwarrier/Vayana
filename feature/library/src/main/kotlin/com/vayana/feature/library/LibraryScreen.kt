package com.vayana.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.core.text.HtmlCompat
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.vayana.core.designsystem.theme.PagedLazyColumn
import com.vayana.core.designsystem.theme.PagedLazyVerticalGrid
import com.vayana.core.designsystem.dialog.ConfirmActionDialog
import com.vayana.core.designsystem.dialog.ExpressiveDialogHeader
import com.vayana.core.designsystem.dialog.ExpressiveDialogSurface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.vayana.core.designsystem.theme.rememberCoverColorFilter
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
import com.vayana.core.designsystem.theme.LocalFloatingNavigationInset
import com.vayana.core.designsystem.theme.VayanaSnackbarHost
import com.vayana.core.designsystem.theme.VayanaLinearProgressIndicator
import com.vayana.core.designsystem.theme.VayanaLinearWavyProgressIndicator
import com.vayana.core.designsystem.theme.vayanaPressScale
import com.vayana.core.designsystem.theme.vayanaAnimateContentSize
import com.vayana.core.designsystem.theme.vayanaSpring
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Palette
import com.vayana.core.designsystem.component.VayanaDropdownMenu
import com.vayana.core.designsystem.component.VayanaMenuGroup
import com.vayana.core.designsystem.component.VayanaMenuItem
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.tokens.Strokes
import com.vayana.core.resources.R
import java.io.File
import com.vayana.core.designsystem.theme.asAppDate
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.vayana.core.database.model.MaxBookTags
import com.vayana.core.database.model.normalizedBookTag

enum class LibraryAddAction {
    IMPORT_FILES,
    IMPORT_FOLDER,
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LibraryRoute(
    onBookClick: (Long, BookOpenTransitionSource) -> Unit,
    onSeriesFolderClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    showSettingsAction: Boolean = true,
    selectedBookId: Long? = null,
    onSearchClick: () -> Unit,
    onRecentlyDeletedClick: () -> Unit,
    onShelvesClick: () -> Unit,
    addBookAction: LibraryAddAction? = null,
    onAddBookActionHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val importSummary by viewModel.importSummary.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val cloudBookDownloadProgress by viewModel.cloudBookDownloadProgress.collectAsState()
    val readNextQueue by viewModel.readNextQueue.collectAsState()
    val deletionNotice by viewModel.permanentDeletionNotice.collectAsState()
    val remoteBookDeletions by viewModel.remoteBookDeletions.collectAsState()

    LibraryScreen(
        modifier = modifier,
        uiState = uiState,
        allBooks = uiState.allBooks,
        readNextQueue = readNextQueue,
        deletionNotice = deletionNotice,
        onDeletionNoticeShown = viewModel::consumePermanentDeletionNotice,
        remoteBookDeletions = remoteBookDeletions,
        onRemoteBookDeletionsShown = viewModel::consumeRemoteBookDeletions,
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
        addBookAction = addBookAction,
        onAddBookActionHandled = onAddBookActionHandled,
        onBookClick = onBookClick,
        onSeriesFolderClick = onSeriesFolderClick,
        onMarkFinished = { bookId -> viewModel.markFinished(bookId, announce = false) },
        onSetReadNext = viewModel::setReadNext,
        onDownloadCloudBook = viewModel::downloadCloudBook,
        onSyncNow = viewModel::syncNow,
        onSettingsClick = onSettingsClick,
        showSettingsAction = showSettingsAction,
        selectedBookId = selectedBookId,
        onSearchClick = onSearchClick,
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
private fun LibraryScreen(
    modifier: Modifier = Modifier,
    uiState: LibraryUiState,
    allBooks: List<Book>,
    readNextQueue: List<Book>,
    deletionNotice: PermanentDeletionNotice?,
    onDeletionNoticeShown: (PermanentDeletionNotice) -> Unit,
    remoteBookDeletions: List<String>,
    onRemoteBookDeletionsShown: (List<String>) -> Unit,
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
    addBookAction: LibraryAddAction?,
    onAddBookActionHandled: () -> Unit,
    onBookClick: (Long, BookOpenTransitionSource) -> Unit,
    onSeriesFolderClick: (String) -> Unit,
    onMarkFinished: (Long) -> Unit,
    onSetReadNext: (Long, Boolean) -> Unit,
    onDownloadCloudBook: suspend (Book) -> CloudBookDownloadResult,
    onSyncNow: suspend (Boolean, GitHubSyncMode) -> GitHubSyncNowResult,
    onSettingsClick: () -> Unit,
    showSettingsAction: Boolean,
    selectedBookId: Long?,
    onSearchClick: () -> Unit,
    onRecentlyDeletedClick: () -> Unit,
    onShelvesClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onGroupByChange: (LibraryGroupBy) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
) {
    val context = LocalContext.current
    val rootView = LocalView.current
    val searchFocusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    PermanentDeletionNoticeEffect(deletionNotice, snackbarHostState, onDeletionNoticeShown)
    RemoteBookDeletionNoticeEffect(remoteBookDeletions, snackbarHostState, onRemoteBookDeletionsShown)
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
    LaunchedEffect(addBookAction) {
        when (addBookAction ?: return@LaunchedEffect) {
            LibraryAddAction.IMPORT_FILES -> filesPicker.launch(arrayOf("*/*"))
            LibraryAddAction.IMPORT_FOLDER -> folderPicker.launch(null)
        }
        onAddBookActionHandled()
    }
    var pendingFinishBook by remember { mutableStateOf<Book?>(null) }
    var showStatsShareDialog by remember { mutableStateOf(false) }
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
    // Read Next only shows on the unfiltered library.
    val readNextShelfShown = uiState.controls.query.isBlank() && uiState.controls.filter == LibraryFilter.ALL
    val activeReadNextQueue = readNextQueue.takeIf { readNextShelfShown }.orEmpty()
    val showReadNextSuggestions = readNextShelfShown && readNextQueue.isEmpty()

    fun handleBookClick(book: Book, transitionSource: BookOpenTransitionSource) {
        if (activeDownloadBookId != null) return
        when (book.fileAvailability) {
            BookFileAvailability.LOCAL -> onBookClick(book.id, transitionSource)
            BookFileAvailability.CLOUD_ONLY -> coroutineScope.launch {
                snackbarHostState.showSnackbar(cloudDownloadStartedMessage)
                when (onDownloadCloudBook(book)) {
                    CloudBookDownloadResult.DOWNLOADED -> {
                        snackbarHostState.showSnackbar(cloudDownloadCompleteMessage)
                        onBookClick(book.id, transitionSource)
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
        val keepScreenOnBeforeSync = rootView.keepScreenOn
        if (mode == GitHubSyncMode.FULL) rootView.keepScreenOn = true
        val result = try {
            onSyncNow(allowInitialSync, mode)
        } finally {
            if (mode == GitHubSyncMode.FULL) rootView.keepScreenOn = keepScreenOnBeforeSync
        }
        when (result) {
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
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            if (event.isCtrlPressed && event.key == Key.F) {
                searchFocusRequester.requestFocus()
                return@onPreviewKeyEvent true
            }
            val books = uiState.books
            if (books.isEmpty()) return@onPreviewKeyEvent false
            val currentIndex = books.indexOfFirst { it.id == selectedBookId }.coerceAtLeast(0)
            val targetIndex = when (event.key) {
                Key.DirectionDown, Key.DirectionRight -> (currentIndex + 1).coerceAtMost(books.lastIndex)
                Key.DirectionUp, Key.DirectionLeft -> (currentIndex - 1).coerceAtLeast(0)
                Key.Enter, Key.NumPadEnter -> currentIndex
                else -> return@onPreviewKeyEvent false
            }
            handleBookClick(books[targetIndex], BookOpenTransitionSource.COVER)
            true
        },
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = Elevations.none,
            ) {
                LibraryTopBar(
                    controls = uiState.controls,
                    searchFocusRequester = searchFocusRequester,
                    showSyncNow = true,
                    syncRunning = syncRunning,
                    syncBadge = syncBadge,
                    onSyncNow = { mode -> handleSyncNow(mode = mode) },
                    showAddAction = LocalFloatingNavigationInset.current == 0.dp,
                    onImportFiles = { filesPicker.launch(arrayOf("*/*")) },
                    onImportFolder = { folderPicker.launch(null) },
                    onSettingsClick = onSettingsClick,
                    showSettingsAction = showSettingsAction,
                    onSearchClick = onSearchClick,
                    onRecentlyDeletedClick = onRecentlyDeletedClick,
                    onShelvesClick = onShelvesClick,
                    onStatsShareClick = { showStatsShareDialog = true },
                    onQueryChange = onQueryChange,
                    onSortChange = onSortChange,
                    onFilterChange = onFilterChange,
                    onGroupByChange = onGroupByChange,
                    onViewModeChange = onViewModeChange,
                )
            }
        },
        snackbarHost = { VayanaSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (uiState.books.isEmpty()) {
            LibraryEmptyState(
                contentPadding = innerPadding,
                hasControls = uiState.controls != LibraryControls(),
                onImportFiles = { filesPicker.launch(arrayOf("*/*")) },
                onImportFolder = { folderPicker.launch(null) },
            )
        } else {
            when (uiState.controls.viewMode) {
                LibraryViewMode.THUMBNAILS -> LibraryGrid(
                    books = uiState.books,
                    readNextQueue = activeReadNextQueue,
                    showReadNextSuggestions = showReadNextSuggestions,
                    groupBy = if (uiState.controls.groupBy == LibraryGroupBy.SERIES_FOLDERS &&
                        (uiState.controls.query.isNotBlank() || uiState.controls.filter != LibraryFilter.ALL)
                    ) LibraryGroupBy.NONE else uiState.controls.groupBy,
                    contentPadding = innerPadding,
                    downloadingBookId = activeDownloadBookId,
                    downloadProgress = cloudBookDownloadProgress?.takeIf { it.isRunning }?.fraction,
                    selectedBookId = selectedBookId,
                    onBookClick = ::handleBookClick,
                    onSeriesFolderClick = onSeriesFolderClick,
                    onMarkFinished = { book -> pendingFinishBook = book },
                    onRemoveFromReadNext = { book -> onSetReadNext(book.id, false) },
                    onViewAllReadNext = onShelvesClick,
                )
                LibraryViewMode.LIST -> LibraryList(
                    books = uiState.books,
                    readNextQueue = activeReadNextQueue,
                    showReadNextSuggestions = showReadNextSuggestions,
                    groupBy = if (uiState.controls.groupBy == LibraryGroupBy.SERIES_FOLDERS &&
                        (uiState.controls.query.isNotBlank() || uiState.controls.filter != LibraryFilter.ALL)
                    ) LibraryGroupBy.NONE else uiState.controls.groupBy,
                    contentPadding = innerPadding,
                    downloadingBookId = activeDownloadBookId,
                    downloadProgress = cloudBookDownloadProgress?.takeIf { it.isRunning }?.fraction,
                    selectedBookId = selectedBookId,
                    onBookClick = ::handleBookClick,
                    onSeriesFolderClick = onSeriesFolderClick,
                    onMarkFinished = { book -> pendingFinishBook = book },
                    onRemoveFromReadNext = { book -> onSetReadNext(book.id, false) },
                    onViewAllReadNext = onShelvesClick,
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
                    border = BorderStroke(Strokes.outline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
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
    if (showStatsShareDialog) {
        LibraryStatsShareDialog(books = allBooks, onDismiss = { showStatsShareDialog = false })
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
internal fun expressiveTextFieldColors(): androidx.compose.material3.TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )

private enum class LibrarySyncBadge {
    SUCCESS,
    FAILED,
}

@Composable
private fun LibraryTopBar(
    controls: LibraryControls,
    searchFocusRequester: FocusRequester,
    showSyncNow: Boolean,
    showAddAction: Boolean,
    syncRunning: Boolean,
    syncBadge: LibrarySyncBadge?,
    onSyncNow: (GitHubSyncMode) -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onSettingsClick: () -> Unit,
    showSettingsAction: Boolean,
    onSearchClick: () -> Unit,
    onRecentlyDeletedClick: () -> Unit,
    onShelvesClick: () -> Unit,
    onStatsShareClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onGroupByChange: (LibraryGroupBy) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
) {
    var addExpanded by remember { mutableStateOf(false) }
    var filterExpanded by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    var syncExpanded by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useCompactActions = maxWidth < Sizes.libraryToolbarExpandedActionsBreakpoint

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
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = Spacing.sm),
                )
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
                            VayanaDropdownMenu(
                                expanded = syncExpanded,
                                onDismissRequest = { syncExpanded = false },
                                groups = listOf(
                                    VayanaMenuGroup(
                                        listOf(
                                            VayanaMenuItem(
                                                label = stringResource(R.string.library_sync_all),
                                                icon = Icons.Outlined.Sync,
                                                onClick = { onSyncNow(GitHubSyncMode.FULL) },
                                            ),
                                            VayanaMenuItem(
                                                label = stringResource(R.string.library_sync_reading_progress_only),
                                                icon = Icons.Outlined.AutoStories,
                                                onClick = { onSyncNow(GitHubSyncMode.READING_PROGRESS_ONLY) },
                                            ),
                                        ),
                                    ),
                                ),
                            )
                        }
                    }
                    if (showAddAction) {
                        Box {
                            LibraryTopBarIconButton(onClick = { addExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = stringResource(R.string.library_add_content_description),
                                    modifier = Modifier.size(Sizes.icon),
                                )
                            }
                            VayanaDropdownMenu(
                                expanded = addExpanded,
                                onDismissRequest = { addExpanded = false },
                                groups = listOf(
                                    VayanaMenuGroup(
                                        listOf(
                                            VayanaMenuItem(
                                                label = stringResource(R.string.library_import_files),
                                                icon = Icons.Outlined.AutoStories,
                                                onClick = onImportFiles,
                                            ),
                                            VayanaMenuItem(
                                                label = stringResource(R.string.library_import_folder),
                                                icon = Icons.Outlined.CreateNewFolder,
                                                onClick = onImportFolder,
                                            ),
                                        ),
                                    ),
                                ),
                            )
                        }
                    }
                    LibraryTopBarIconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.library_search_content_description),
                            modifier = Modifier.size(Sizes.icon),
                        )
                    }
                    if (!useCompactActions) {
                        LibraryTopBarIconButton(onClick = { filterExpanded = true }) {
                            Icon(
                                imageVector = Icons.Outlined.FilterList,
                                contentDescription = stringResource(R.string.library_filter_content_description),
                                modifier = Modifier.size(Sizes.icon),
                            )
                        }
                        VayanaDropdownMenu(
                            expanded = filterExpanded,
                            onDismissRequest = { filterExpanded = false },
                            groups = listOf(librarySortMenuGroup(controls, onSortChange)),
                        )
                        LibraryTopBarIconButton(
                            onClick = { onViewModeChange(controls.viewMode.toggled()) },
                        ) {
                            Icon(
                                imageVector = controls.viewMode.toggleIcon(),
                                contentDescription = stringResource(controls.viewMode.toggleLabelRes()),
                                modifier = Modifier.size(Sizes.icon),
                            )
                        }
                    }
                    Box {
                        LibraryTopBarIconButton(onClick = { moreExpanded = true }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.library_more_content_description),
                                modifier = Modifier.size(Sizes.icon),
                            )
                        }
                        VayanaDropdownMenu(
                            expanded = moreExpanded,
                            onDismissRequest = { moreExpanded = false },
                            groups = buildList {
                                // Narrow layouts have no room for the sort and view buttons, so they move in here.
                                if (useCompactActions) {
                                    add(librarySortMenuGroup(controls, onSortChange))
                                    add(
                                        VayanaMenuGroup(
                                            listOf(
                                                VayanaMenuItem(
                                                    label = stringResource(controls.viewMode.toggleLabelRes()),
                                                    icon = controls.viewMode.toggleIcon(),
                                                    onClick = { onViewModeChange(controls.viewMode.toggled()) },
                                                ),
                                            ),
                                        ),
                                    )
                                }
                                add(
                                    VayanaMenuGroup(
                                        label = stringResource(R.string.library_group_content_description),
                                        items = LibraryGroupBy.entries.map { groupBy ->
                                            VayanaMenuItem(
                                                label = groupBy.label(),
                                                selected = controls.groupBy == groupBy,
                                                onClick = { onGroupByChange(groupBy) },
                                            )
                                        },
                                    ),
                                )
                                add(VayanaMenuGroup(listOf(
                                    VayanaMenuItem(
                                        label = stringResource(R.string.library_stats_share_action),
                                        icon = Icons.Outlined.Share,
                                        onClick = onStatsShareClick,
                                    ),
                                )))
                                if (showSettingsAction) {
                                    add(
                                        VayanaMenuGroup(
                                            listOf(
                                                VayanaMenuItem(
                                                    label = stringResource(R.string.library_shelves_title),
                                                    icon = Icons.Outlined.CollectionsBookmark,
                                                    onClick = onShelvesClick,
                                                ),
                                                VayanaMenuItem(
                                                    label = stringResource(R.string.library_recently_deleted_title),
                                                    icon = Icons.Outlined.RestoreFromTrash,
                                                    onClick = onRecentlyDeletedClick,
                                                ),
                                            ),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    if (showSettingsAction) {
                        LibraryTopBarIconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.library_settings_content_description),
                                modifier = Modifier.size(Sizes.icon),
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
                    .focusRequester(searchFocusRequester)
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
}

private fun LibraryViewMode.toggled(): LibraryViewMode = when (this) {
    LibraryViewMode.THUMBNAILS -> LibraryViewMode.LIST
    LibraryViewMode.LIST -> LibraryViewMode.THUMBNAILS
}

private fun LibraryViewMode.toggleIcon(): ImageVector = when (this) {
    LibraryViewMode.THUMBNAILS -> Icons.AutoMirrored.Outlined.ViewList
    LibraryViewMode.LIST -> Icons.Outlined.GridView
}

private fun LibraryViewMode.toggleLabelRes(): Int = when (this) {
    LibraryViewMode.THUMBNAILS -> R.string.library_view_list_content_description
    LibraryViewMode.LIST -> R.string.library_view_thumbnails_content_description
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



private data class LibraryDisplayBooks(val hero: Book?, val rows: List<Book>)
private data class LibraryGroupSection(val label: String, val books: List<Book>)
@Composable
private fun LibraryGrid(
    books: List<Book>,
    readNextQueue: List<Book>,
    showReadNextSuggestions: Boolean,
    groupBy: LibraryGroupBy,
    contentPadding: PaddingValues,
    downloadingBookId: Long?,
    downloadProgress: Float?,
    selectedBookId: Long?,
    onBookClick: (Book, BookOpenTransitionSource) -> Unit,
    onSeriesFolderClick: (String) -> Unit,
    onMarkFinished: (Book) -> Unit,
    onRemoveFromReadNext: (Book) -> Unit,
    onViewAllReadNext: () -> Unit,
) {
    val displayBooks = if (groupBy == LibraryGroupBy.SERIES_FOLDERS) LibraryDisplayBooks(null, books) else rememberLibraryDisplayBooks(books)
    val readNextBooks = rememberReadNextShelfBooks(readNextQueue, showReadNextSuggestions, books, displayBooks.hero)
    val sections = displayBooks.rows.toGroupSections(groupBy)
    val folderItems = if (groupBy == LibraryGroupBy.SERIES_FOLDERS) seriesLibraryItems(displayBooks.rows) else emptyList()
    val placementSpec = rememberLazyItemPlacementSpec()

    PagedLazyVerticalGrid(
        showPageButtons = false,
        columns = GridCells.Adaptive(minSize = Sizes.libraryGridCoverWidthMin),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Paddings.screenHorizontal,
            end = Paddings.screenHorizontal,
            top = contentPadding.calculateTopPadding() + Spacing.md,
            bottom = contentPadding.calculateBottomPadding() + Spacing.md + LocalFloatingNavigationInset.current,
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        displayBooks.hero?.let { heroBook ->
            item(key = "hero:${heroBook.id}", contentType = "hero", span = { GridItemSpan(maxLineSpan) }) {
                LibraryHeroCard(
                    book = heroBook,
                    isDownloading = heroBook.id == downloadingBookId,
                    downloadProgress = if (heroBook.id == downloadingBookId) downloadProgress else null,
                    onClick = { onBookClick(heroBook, BookOpenTransitionSource.HERO_CARD) },
                    onMarkFinished = { onMarkFinished(heroBook) },
                    modifier = Modifier.animateItem(placementSpec = placementSpec),
                )
            }
        }

        if (readNextBooks.isNotEmpty()) {
            item(key = if (readNextQueue.isNotEmpty()) "read-next" else "read-next-suggestions", contentType = "read-next", span = { GridItemSpan(maxLineSpan) }) {
                ReadNextShelf(
                    books = readNextBooks,
                    isSuggestion = readNextQueue.isEmpty(),
                    downloadingBookId = downloadingBookId,
                    downloadProgress = downloadProgress,
                    onBookClick = { book -> onBookClick(book, BookOpenTransitionSource.READ_NEXT_COVER) },
                    onRemove = onRemoveFromReadNext,
                    onViewAll = onViewAllReadNext,
                    modifier = Modifier.animateItem(placementSpec = placementSpec),
                )
            }
        }

        if (groupBy == LibraryGroupBy.SERIES_FOLDERS) {
            folderItems.forEach { entry ->
                when (entry) {
                    is SeriesLibraryItem.Single -> item(key = "book:${entry.book.id}", contentType = "book") {
                        val book = entry.book
                        BookCoverCell(
                            book = book,
                            selected = book.id == selectedBookId,
                            isDownloading = book.id == downloadingBookId,
                            downloadProgress = if (book.id == downloadingBookId) downloadProgress else null,
                            onMarkFinished = { onMarkFinished(book) },
                            onClick = { onBookClick(book, BookOpenTransitionSource.COVER) },
                        )
                    }
                    is SeriesLibraryItem.Folder -> item(key = "series:${entry.key}", contentType = "series") {
                        SeriesFolderCell(entry, onClick = { onSeriesFolderClick(entry.key) })
                    }
                }
            }
        } else if (sections == null) {
            gridItems(displayBooks.rows, key = { it.id }, contentType = { "book" }) { book ->
                BookCoverCell(
                    book = book,
                    selected = book.id == selectedBookId,
                    isDownloading = book.id == downloadingBookId,
                    downloadProgress = if (book.id == downloadingBookId) downloadProgress else null,
                    onMarkFinished = { onMarkFinished(book) },
                    modifier = Modifier.animateItem(placementSpec = placementSpec),
                    onClick = { onBookClick(book, BookOpenTransitionSource.COVER) },
                )
            }
        } else {
            sections.forEach { section ->
                item(key = "header:${section.label}", contentType = "header", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = section.label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(top = Spacing.sm, bottom = Spacing.xs)
                            .animateItem(placementSpec = placementSpec),
                    )
                }
                gridItems(section.books, key = { it.id }, contentType = { "book" }) { book ->
                    BookCoverCell(
                        book = book,
                        selected = book.id == selectedBookId,
                        isDownloading = book.id == downloadingBookId,
                        downloadProgress = if (book.id == downloadingBookId) downloadProgress else null,
                        onMarkFinished = { onMarkFinished(book) },
                        modifier = Modifier.animateItem(placementSpec = placementSpec),
                        onClick = { onBookClick(book, BookOpenTransitionSource.COVER) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryList(
    books: List<Book>,
    readNextQueue: List<Book>,
    showReadNextSuggestions: Boolean,
    groupBy: LibraryGroupBy,
    contentPadding: PaddingValues,
    downloadingBookId: Long?,
    downloadProgress: Float?,
    selectedBookId: Long?,
    onBookClick: (Book, BookOpenTransitionSource) -> Unit,
    onSeriesFolderClick: (String) -> Unit,
    onMarkFinished: (Book) -> Unit,
    onRemoveFromReadNext: (Book) -> Unit,
    onViewAllReadNext: () -> Unit,
) {
    val displayBooks = if (groupBy == LibraryGroupBy.SERIES_FOLDERS) LibraryDisplayBooks(null, books) else rememberLibraryDisplayBooks(books)
    val readNextBooks = rememberReadNextShelfBooks(readNextQueue, showReadNextSuggestions, books, displayBooks.hero)
    val sections = displayBooks.rows.toGroupSections(groupBy)
    val folderItems = if (groupBy == LibraryGroupBy.SERIES_FOLDERS) seriesLibraryItems(displayBooks.rows) else emptyList()
    val placementSpec = rememberLazyItemPlacementSpec()

    PagedLazyColumn(
        showPageButtons = false,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Paddings.screenHorizontal,
            end = Paddings.screenHorizontal,
            top = contentPadding.calculateTopPadding() + Spacing.md,
            bottom = contentPadding.calculateBottomPadding() + Spacing.md + LocalFloatingNavigationInset.current,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        displayBooks.hero?.let { heroBook ->
            item(key = "hero:${heroBook.id}") {
                LibraryHeroCard(
                    book = heroBook,
                    isDownloading = heroBook.id == downloadingBookId,
                    downloadProgress = if (heroBook.id == downloadingBookId) downloadProgress else null,
                    onClick = { onBookClick(heroBook, BookOpenTransitionSource.HERO_CARD) },
                    onMarkFinished = { onMarkFinished(heroBook) },
                    modifier = Modifier.animateItem(placementSpec = placementSpec),
                )
            }
        }

        if (readNextBooks.isNotEmpty()) {
            item(key = if (readNextQueue.isNotEmpty()) "read-next" else "read-next-suggestions") {
                ReadNextShelf(
                    books = readNextBooks,
                    isSuggestion = readNextQueue.isEmpty(),
                    downloadingBookId = downloadingBookId,
                    downloadProgress = downloadProgress,
                    onBookClick = { book -> onBookClick(book, BookOpenTransitionSource.READ_NEXT_COVER) },
                    onRemove = onRemoveFromReadNext,
                    onViewAll = onViewAllReadNext,
                    modifier = Modifier.animateItem(placementSpec = placementSpec),
                )
            }
        }

        if (groupBy == LibraryGroupBy.SERIES_FOLDERS) {
            folderItems.forEach { entry ->
                when (entry) {
                    is SeriesLibraryItem.Single -> item(key = "book:${entry.book.id}") {
                        val book = entry.book
                        LibraryListRow(
                            book = book,
                            selected = book.id == selectedBookId,
                            isDownloading = book.id == downloadingBookId,
                            downloadProgress = if (book.id == downloadingBookId) downloadProgress else null,
                            onClick = { onBookClick(book, BookOpenTransitionSource.COVER) },
                            onMarkFinished = { onMarkFinished(book) },
                        )
                    }
                    is SeriesLibraryItem.Folder -> item(key = "series:${entry.key}") {
                        SeriesFolderListRow(entry, onClick = { onSeriesFolderClick(entry.key) })
                    }
                }
            }
        } else if (sections == null) {
            items(displayBooks.rows, key = { it.id }) { book ->
                LibraryListRow(
                    book = book,
                    selected = book.id == selectedBookId,
                    isDownloading = book.id == downloadingBookId,
                    downloadProgress = if (book.id == downloadingBookId) downloadProgress else null,
                    onClick = { onBookClick(book, BookOpenTransitionSource.COVER) },
                    onMarkFinished = { onMarkFinished(book) },
                    modifier = Modifier.animateItem(placementSpec = placementSpec),
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
                            .animateItem(placementSpec = placementSpec),
                    )
                }
                items(section.books, key = { it.id }) { book ->
                    LibraryListRow(
                        book = book,
                        selected = book.id == selectedBookId,
                        isDownloading = book.id == downloadingBookId,
                        downloadProgress = if (book.id == downloadingBookId) downloadProgress else null,
                        onClick = { onBookClick(book, BookOpenTransitionSource.COVER) },
                        onMarkFinished = { onMarkFinished(book) },
                        modifier = Modifier.animateItem(placementSpec = placementSpec),
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
private fun rememberLazyItemPlacementSpec() = vayanaSpring<IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

@Composable
private fun LibraryListRow(
    book: Book,
    selected: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
    onMarkFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .vayanaPressScale(interactionSource)
            .clickable(
                enabled = !isDownloading,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(Radii.medium),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(Strokes.emphasis, MaterialTheme.colorScheme.primary) else null,
        tonalElevation = Elevations.none,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(modifier = Modifier.width(Sizes.coverWidthMin * 0.58f)) {
                BookCover(
                    book = book,
                    modifier = Modifier
                        .bookSharedElement(book.id, BookOpenTransitionSource.COVER)
                        .fillMaxWidth(),
                )
                BookFinishedBadge(
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
    val importedDate = book.createdAt.formatDate()

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
                VayanaLinearProgressIndicator(
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
                    text = stringResource(R.string.library_imported_on, importedDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryHeroCard(
    book: Book,
    isDownloading: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
    onMarkFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .bookSharedBounds(book.id)
            .fillMaxWidth()
            .vayanaPressScale(interactionSource)
            .clickable(
                enabled = !isDownloading,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
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
                BookFinishedBadge(
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
                        VayanaLinearWavyProgressIndicator(
                            progress = { book.readingPercent.coerceIn(0f, 1f) },
                            modifier = Modifier.weight(1f),
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
    if (mode == LibraryGroupBy.NONE || mode == LibraryGroupBy.SERIES_FOLDERS) return null
    val unknownLabel = when (mode) {
        LibraryGroupBy.AUTHOR -> stringResource(R.string.library_group_unknown_author)
        LibraryGroupBy.SERIES -> stringResource(R.string.library_group_unknown_series)
        LibraryGroupBy.SERIES_FOLDERS -> ""
        LibraryGroupBy.NONE -> ""
    }
    return remember(this, mode, unknownLabel) {
        val keyOf: (Book) -> String? = when (mode) {
            LibraryGroupBy.AUTHOR -> { book -> book.author }
            LibraryGroupBy.SERIES -> { book -> book.series }
            LibraryGroupBy.SERIES_FOLDERS -> { _ -> null }
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
    selected: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float?,
    onMarkFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // Only a selected cell is a rounded tile; otherwise the cell is not clipped, so neither the cover nor the
    // title's last line loses its corners.
    val cellShape = if (selected) RoundedCornerShape(Radii.medium) else RectangleShape
    Column(
        modifier = modifier
            .background(
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                shape = cellShape,
            )
            .then(
                if (selected) {
                    Modifier.border(
                        width = Strokes.emphasis,
                        color = MaterialTheme.colorScheme.primary,
                        shape = cellShape,
                    )
                } else {
                    Modifier
                },
            )
            .clip(cellShape)
            .padding(if (selected) Spacing.sm else 0.dp)
            .vayanaPressScale(interactionSource, pressedScale = 0.975f)
            .clickable(
                enabled = !isDownloading,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box {
            BookCover(
                book = book,
                modifier = Modifier
                    .bookSharedElement(book.id, BookOpenTransitionSource.COVER)
                    .fillMaxWidth(),
                shape = RectangleShape,
            )
            BookFinishedBadge(
                book = book,
                onMarkFinished = onMarkFinished,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        Text(
            text = book.title,
            style = MaterialTheme.typography.labelLarge,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        if (isDownloading) {
            VayanaLinearProgressIndicator(
                progress = { downloadProgress ?: 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                strokeCap = StrokeCap.Round,
            )
        } else if (book.readingPercent > 0f) {
            VayanaLinearProgressIndicator(
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
private fun BookFinishedBadge(book: Book, onMarkFinished: () -> Unit, modifier: Modifier = Modifier) {
    if (book.isFinished()) {
        // A word, not a tick: unfinished books already show a tick as their "mark as finished" button.
        val finishedDescription = stringResource(R.string.library_book_finished)
        Surface(
            modifier = modifier
                .padding(Spacing.xs)
                .clearAndSetSemantics { contentDescription = finishedDescription },
            shape = RoundedCornerShape(Radii.full),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            tonalElevation = Elevations.shadowSmall,
        ) {
            Text(
                text = stringResource(R.string.library_book_read_badge),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = Paddings.badgeHorizontal, vertical = Paddings.badgeVertical),
            )
        }
    } else {
        IconButton(onClick = onMarkFinished, modifier = modifier.size(Sizes.touchTarget)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = Elevations.shadowSmall,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.library_mark_finished),
                    modifier = Modifier
                        .padding(Spacing.xs)
                        .size(Sizes.iconSmall),
                )
            }
        }
    }
}

@Composable
internal fun BookCover(book: Book, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(Radii.small)) {
    val coverPath = book.coverPath
    if (coverPath != null) {
        AsyncImage(
            model = File(coverPath),
            contentDescription = stringResource(R.string.library_book_cover_content_description, book.title),
            modifier = modifier
                .aspectRatio(Sizes.coverAspectRatio)
                .clip(shape),
            contentScale = ContentScale.Crop,
            colorFilter = rememberCoverColorFilter(),
        )
    } else {
        GeneratedCover(title = book.title, author = book.author, shape = shape, modifier = modifier)
    }
}

@Composable
private fun GeneratedCover(title: String, author: String?, shape: Shape, modifier: Modifier = Modifier) {
    val colors = listOf(Palette.Forest700, Palette.Teal700, Palette.Navy500, Palette.Gold700)
    val background = colors[title.hashCode().mod(colors.size)]

    Column(
        modifier = modifier
            .aspectRatio(Sizes.coverAspectRatio)
            .background(background, shape)
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
private fun LibraryEmptyState(
    contentPadding: PaddingValues,
    hasControls: Boolean,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
) {
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
        if (!hasControls) {
            Spacer(modifier = Modifier.height(Spacing.xl))
            Button(
                onClick = onImportFiles,
                modifier = Modifier.fillMaxWidth(0.7f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoStories,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.icon),
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(text = stringResource(R.string.library_import_files))
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            OutlinedButton(
                onClick = onImportFolder,
                modifier = Modifier.fillMaxWidth(0.7f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CreateNewFolder,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.icon),
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(text = stringResource(R.string.library_import_folder))
            }
        }
    }
}

@Composable
internal fun LibrarySort.label(): String = when (this) {
    LibrarySort.IMPORT_DATE -> stringResource(R.string.library_sort_import_date)
    LibrarySort.TITLE -> stringResource(R.string.library_sort_title)
    LibrarySort.AUTHOR -> stringResource(R.string.library_sort_author)
    LibrarySort.LAST_READ -> stringResource(R.string.library_sort_last_read)
    LibrarySort.PROGRESS -> stringResource(R.string.library_sort_progress)
}

internal fun LibrarySortDirection.icon(): ImageVector = when (this) {
    LibrarySortDirection.ASCENDING -> Icons.Outlined.ArrowUpward
    LibrarySortDirection.DESCENDING -> Icons.Outlined.ArrowDownward
}

@Composable
internal fun LibraryGroupBy.label(): String = when (this) {
    LibraryGroupBy.NONE -> stringResource(R.string.library_group_none)
    LibraryGroupBy.AUTHOR -> stringResource(R.string.library_group_author)
    LibraryGroupBy.SERIES -> stringResource(R.string.library_group_series)
    LibraryGroupBy.SERIES_FOLDERS -> stringResource(R.string.library_group_series_folders)
}

@Composable
internal fun LibraryFilter.label(): String = when (this) {
    LibraryFilter.ALL -> stringResource(R.string.library_filter_all)
    LibraryFilter.READING -> stringResource(R.string.library_filter_reading)
    LibraryFilter.FINISHED -> stringResource(R.string.library_filter_finished)
    LibraryFilter.NOT_STARTED -> stringResource(R.string.library_filter_not_started)
}

internal fun Book.hasLocalReadableSource(): Boolean =
    format != BookFormat.PHYSICAL &&
        fileAvailability == BookFileAvailability.LOCAL &&
        filePath.isNotBlank()

/** The library's sort orders as a pick-one menu group; the current one also shows its direction. */
@Composable
private fun librarySortMenuGroup(controls: LibraryControls, onSortChange: (LibrarySort) -> Unit): VayanaMenuGroup =
    VayanaMenuGroup(
        label = stringResource(R.string.library_filter_content_description),
        items = LibrarySort.entries.map { sort ->
            val selected = controls.sort == sort
            VayanaMenuItem(
                label = sort.label(),
                selected = selected,
                trailingIcon = if (selected) controls.sortDirection.icon() else null,
                onClick = { onSortChange(sort) },
            )
        },
    )

internal fun Book.tags(): List<String> = tagsCsv.orEmpty().tags()

internal fun Book.seriesDisplayOrNone(): String =
    listOfNotNull(series?.takeIf { it.isNotBlank() }, seriesNumber?.takeIf { it.isNotBlank() }?.let { "#$it" })
        .joinToString(" ")

internal fun String.tags(): List<String> =
    split(",")
        .map { it.normalizedBookTag() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.metadataKey() }
        .take(MaxBookTags)

internal fun String.metadataKey(): String = trim().lowercase()

@Composable
internal fun Book.seriesDisplay(): String = listOfNotNull(
    series?.takeIf { it.isNotBlank() },
    seriesNumber?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.library_series_number_value, it) },
).joinToString(" · ")

@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun Long.formatDate(): String = asAppDate()

internal fun Book.hasStartedReading(): Boolean =
    startedReadingAt != null || readingPercent > 0f || lastReadAt != null || totalReadingSeconds > 0L

internal fun Book.isFinished(): Boolean = finishedReadingAt != null || readingPercent >= 1f

/** Where the reader is with a book, which decides what its details screen offers and shows. */
internal enum class BookReadingState { NOT_STARTED, READING, FINISHED }

internal fun Book.readingState(): BookReadingState = when {
    isFinished() -> BookReadingState.FINISHED
    hasStartedReading() -> BookReadingState.READING
    else -> BookReadingState.NOT_STARTED
}

/**
 * Finished at least [ReadAgainAfterMillis] before [now], counting from the finish date or, for a book finished only by
 * reaching the end, when it was last read.
 */
internal fun Book.finishedLongAgo(now: Long): Boolean {
    if (readingState() != BookReadingState.FINISHED) return false
    val finishedAt = finishedReadingAt ?: lastReadAt ?: return false
    return now - finishedAt >= ReadAgainAfterMillis
}

internal const val ReadAgainAfterMillis = 365L * 24 * 60 * 60 * 1000

internal const val MetadataSuggestionLimit = 5
internal const val TagSuggestionLimit = 6
internal const val MaxBookTagsInputChars = 1_024
internal const val MaxSharedBookFileSegmentChars = 80
internal const val MaxSharedBookFileExtensionChars = 8
internal const val PreviewTextLimit = 180
private const val SearchFieldUnfocusedBorderAlpha = 0.35f
private const val GeneratedCoverAuthorAlpha = 0.8f

internal fun String.cleanHtml(): String {
    val unescaped = try {
        HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
    } catch (_: Throwable) {
        this.replace(Regex("<[^>]*>"), "")
    }
    return unescaped.replace(Regex("\n{3,}"), "\n\n").trim()
}
