package com.vayana.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Palette
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.common.ParsedQuote
import com.vayana.core.resources.R
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow

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
    val highlightCount by remember(bookId) { viewModel.observeAnnotationCount(bookId) }.collectAsState(initial = null)
    val pendingLaunchProgressChange by viewModel.pendingLaunchProgressChange.collectAsState()
    var syncReadingProgressRunning by remember { mutableStateOf(false) }
    var progressChangePrompt by remember { mutableStateOf<BookProgressChange?>(null) }

    LaunchedEffect(bookId, pendingLaunchProgressChange) {
        val prompt = pendingLaunchProgressChange ?: return@LaunchedEffect
        if (prompt.bookId == bookId) {
            progressChangePrompt = prompt
            viewModel.acknowledgePendingLaunchProgressChange(bookId)
        }
    }

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
        onSetReadNext = viewModel::setReadNext,
        readNextBumped = viewModel.readNextBumped,
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
        onApplyGoodreads = { options -> viewModel.applyPendingGoodreads(bookId, options) },
        onResetReadingStats = { viewModel.resetReadingStats(bookId) },
        onDismissGoodreads = viewModel::dismissGoodreadsImport,
        onUseCover = { source -> viewModel.useCover(bookId, source) },
        onDeleteBook = {
            viewModel.deleteBook(bookId)
            onBack()
        },
        onDeletePermanently = {
            viewModel.deletePermanently(bookId)
            onBack()
        },
        highlightCount = highlightCount,
    )
}

internal data class BookShareImageOptions(
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
    onSetReadNext: (bookId: Long, queued: Boolean) -> Unit,
    readNextBumped: Flow<List<Book>>,
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
    onDeletePermanently: () -> Unit,
    highlightCount: Int?,
    goodreadsImport: GoodreadsImportState,
    onImportGoodreads: (String) -> Unit,
    onImportGoodreadsCapture: (GoodreadsBookMetadata, List<ParsedQuote>?) -> Unit,
    onApplyGoodreads: (GoodreadsImportOptions) -> Unit,
    onResetReadingStats: () -> Unit,
    onDismissGoodreads: () -> Unit,
    onUseCover: (CoverSource) -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPermanentDeleteDialog by remember { mutableStateOf(false) }
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
    var readNextSeriesBreakWarning by remember { mutableStateOf<ReadNextSeriesBreakWarning?>(null) }
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

    val readNextBumpedOneMessage = stringResource(R.string.library_read_next_bumped_one)
    val readNextBumpedManyMessage = stringResource(R.string.library_read_next_bumped_many)
    LaunchedEffect(readNextBumped) {
        readNextBumped.collect { bumped ->
            val message = bumped.singleOrNull()?.let { readNextBumpedOneMessage.format(it.title) }
                ?: readNextBumpedManyMessage.format(bumped.size)
            snackbarHostState.showSnackbar(message)
        }
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
                            ElevatedButton(
                                onClick = {
                                    if (isQueued) {
                                        onSetReadNext(book.id, false)
                                    } else {
                                        val warning = libraryBooks.readNextSeriesBreakWarningFor(book)
                                        if (warning == null) {
                                            onSetReadNext(book.id, true)
                                        } else {
                                            readNextSeriesBreakWarning = warning
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
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
        DeleteBookChoiceDialog(
            bookTitle = book?.title.orEmpty(),
            onDismissRequest = { showDeleteDialog = false },
            onMoveToRecentlyDeleted = {
                showDeleteDialog = false
                onDeleteBook()
            },
            onDeletePermanently = {
                showDeleteDialog = false
                showPermanentDeleteDialog = true
            },
        )
    }

    if (showPermanentDeleteDialog && book != null) {
        PermanentDeleteConfirmDialog(
            book = book,
            highlightCount = highlightCount,
            onDismissRequest = { showPermanentDeleteDialog = false },
            onConfirm = {
                showPermanentDeleteDialog = false
                onDeletePermanently()
            },
        )
    }

    readNextSeriesBreakWarning?.let { warning ->
        ReadNextSeriesBreakDialog(
            warning = warning,
            onDismissRequest = { readNextSeriesBreakWarning = null },
            onFollowCurrentSeries = {
                readNextSeriesBreakWarning = null
                onSetReadNext(warning.nextBook.id, true)
            },
            onConfirm = {
                readNextSeriesBreakWarning = null
                onSetReadNext(warning.queuedBook.id, true)
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

@Composable
internal fun BookDetailMessage.label(): String = when (this) {
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

private fun Book.canRemoveLocalFileFromDevice(): Boolean =
    hasLocalReadableSource() &&
        !fileAssetId.isNullOrBlank() &&
        !fileAssetSha256.isNullOrBlank() &&
        fileAssetSizeBytes != null &&
        fileAssetUploadedAt != null
