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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
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
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.vayana.core.common.shareText as shareTextWithChooser
import com.vayana.core.designsystem.sharecard.BookShareCard
import com.vayana.core.designsystem.sharecard.BookShareCardLayout
import com.vayana.core.designsystem.sharecard.DefaultBackdropCoverAlpha
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
import com.vayana.core.designsystem.theme.LocalDisplayProfile
import com.vayana.core.designsystem.theme.DisplayProfile
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material.icons.outlined.Description
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.outlined.PhonelinkErase
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.PhysicalBookOwnership
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Palette
import com.vayana.core.designsystem.component.VayanaDropdownMenu
import com.vayana.core.designsystem.component.VayanaMenuGroup
import com.vayana.core.designsystem.component.VayanaMenuItem
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.theme.LocalFloatingNavigationInset
import com.vayana.core.designsystem.theme.VayanaSnackbarHost
import com.vayana.core.homelibrary.HomeLibraryLauncher
import com.vayana.core.common.ParsedQuote
import com.vayana.core.resources.R
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow

@Composable
fun BookDetailRoute(
    bookId: Long,
    onBack: () -> Unit,
    onContinueReading: (Long, String?) -> Unit,
    transitionSource: BookOpenTransitionSource? = null,
    /** Opens the Goodreads picker once the book loads, e.g. right after adding a book read outside the app. */
    openGoodreads: Boolean = false,
    onOpenNotes: ((Long) -> Unit)? = null,
    onReadFromStart: ((Long) -> Unit)? = null,
    useWideActions: Boolean = false,
    onReadableSourceChanged: (Boolean) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val libraryBooks by viewModel.libraryBooks.collectAsStateWithLifecycle()
    val bookFlow = remember(bookId) { viewModel.observeBook(bookId) }
    val observedBook by bookFlow.collectAsStateWithLifecycle(initialValue = null)
    val book = observedBook ?: libraryBooks.firstOrNull { libraryBook -> libraryBook.id == bookId }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val yearlyBooksGoal by viewModel.yearlyBooksGoal.collectAsStateWithLifecycle()
    val finishByDates by viewModel.finishByDates.collectAsStateWithLifecycle()
    val detailMessage by viewModel.bookDetailMessage.collectAsStateWithLifecycle()
    val coverImageDownloadInProgress by viewModel.coverImageDownloadInProgress.collectAsStateWithLifecycle()
    val allShelves by viewModel.shelves.collectAsStateWithLifecycle()
    val shelvesForBook by remember(bookId) { viewModel.observeShelvesForBook(bookId) }.collectAsStateWithLifecycle()
    val goodreadsImport by viewModel.goodreadsImport.collectAsStateWithLifecycle()
    val highlightCount by remember(bookId) { viewModel.observeAnnotationCount(bookId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val communityQuoteCount by remember(bookId) { viewModel.observeCommunityQuoteCount(bookId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val pendingLaunchProgressChange by viewModel.pendingLaunchProgressChange.collectAsStateWithLifecycle()
    var syncReadingProgressRunning by remember { mutableStateOf(false) }
    var progressChangePrompt by remember { mutableStateOf<BookProgressChange?>(null) }

    LaunchedEffect(book?.id, book?.hasLocalReadableSource()) {
        onReadableSourceChanged(book?.hasLocalReadableSource() == true)
    }

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
        transitionSource = transitionSource,
        openGoodreadsOnStart = openGoodreads,
        onChangeOfflineFormat = { format -> viewModel.updateOfflineFormat(bookId, format) },
        onUpdateOfflinePages = { pageCount, currentPage -> viewModel.updateOfflinePages(bookId, pageCount, currentPage) },
        physicalTimer = { physicalBook -> PhysicalReadingTimerCard(physicalBook, viewModel) },
        onMarkPhysicalReading = { viewModel.markPhysicalBookReading(bookId) },
        onUpdatePhysicalBookLoan = { ownership, returnAt -> viewModel.updatePhysicalBookLoan(bookId, ownership, returnAt) },
        showSyncReadingProgress = uiState.githubSyncReady,
        syncReadingProgressRunning = syncReadingProgressRunning,
        progressChangePrompt = progressChangePrompt,
        onKeepSyncedProgress = { prompt ->
            progressChangePrompt = null
            onContinueReading(prompt.bookId, prompt.newLocator)
        },
        onRevertSyncedProgress = { prompt ->
            progressChangePrompt = null
            viewModel.revertReadingProgress(prompt.bookId, prompt.previousLocator, prompt.previousPercent) {
                onContinueReading(prompt.bookId, prompt.previousLocator)
            }
        },
        libraryBooks = libraryBooks,
        yearlyBooksGoal = yearlyBooksGoal,
        finishByDate = book?.syncId?.let(finishByDates::get),
        onFinishByChange = { date -> book?.let { viewModel.setFinishBy(it.syncId, date) } },
        detailMessage = detailMessage,
        coverImageDownloadInProgress = coverImageDownloadInProgress,
        allShelves = allShelves,
        shelvesForBook = shelvesForBook,
        onCreateShelf = viewModel::createShelf,
        onAddToShelf = { shelfId -> viewModel.addBookToShelf(bookId, shelfId) },
        onRemoveFromShelf = { shelfId -> viewModel.removeBookFromShelf(bookId, shelfId) },
        onSetReadNext = viewModel::setReadNext,
        readNextBumped = viewModel.readNextBumped,
        onBack = onBack,
        onContinueReading = onContinueReading,
        onOpenNotes = onOpenNotes,
        onReadFromStart = onReadFromStart,
        useWideActions = useWideActions,
        onUpdateMetadata = { title, author, series, seriesNumber, description, tagsCsv ->
            viewModel.updateMetadata(bookId, title, author, series, seriesNumber, description, tagsCsv)
        },
        onUpdateReadingDates = { started, finished -> viewModel.updateReadingDates(bookId, started, finished) },
        onUpdateRating = { rating ->
            viewModel.updateRating(bookId, rating)
        },
        onReplaceSource = { contentResolver, uri ->
            viewModel.replaceSource(bookId, contentResolver, uri)
        },
        onReplaceCover = { contentResolver, uri ->
            viewModel.replaceCover(bookId, contentResolver, uri)
        },
        onReplaceCoverFromWeb = { request ->
            viewModel.replaceCoverFromWeb(bookId, request)
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
        communityQuoteCount = communityQuoteCount,
    )
}

/** The two dialogs of deleting a book: pick how, then confirm a permanent delete. */
private enum class DeleteStep { CHOICE, CONFIRM_PERMANENT }

internal data class BookShareImageOptions(
    val layout: BookShareCardLayout = BookShareCardLayout.CLASSIC,
    val theme: ShareCardTheme = ShareCardTheme.LIGHT,
    val backdropCoverAlpha: Float = DefaultBackdropCoverAlpha,
    val showCover: Boolean = true,
    val showAuthor: Boolean = true,
    val showSeries: Boolean = true,
    val showStatus: Boolean = true,
    val showProgress: Boolean = true,
    val showReadTime: Boolean = true,
    val showRating: Boolean = true,
    val showTags: Boolean = true,
    /** Mutually exclusive with [showTags]; the options panel keeps at most one of them on. */
    val showImportedDate: Boolean = false,
    val showTagline: Boolean = true,
    val showYearlyGoal: Boolean = false,
) {
    companion object {
        /**
         * [current] with the stats a book in [state] can fill in: an unread book has no progress or reading time,
         * so they start off rather than printing "0%" and "0h 0m" on the card.
         */
        fun forState(state: BookReadingState, current: BookShareImageOptions): BookShareImageOptions {
            val hasProgress = state != BookReadingState.NOT_STARTED
            return current.copy(showProgress = hasProgress, showReadTime = hasProgress)
        }
    }
}

@Composable
private fun BookDetailScreen(
    onMarkPhysicalReading: suspend () -> Unit,
    finishByDate: java.time.LocalDate?,
    onFinishByChange: suspend (java.time.LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    book: Book?,
    transitionSource: BookOpenTransitionSource?,
    openGoodreadsOnStart: Boolean,
    onChangeOfflineFormat: (BookFormat) -> Unit,
    onUpdateOfflinePages: (pageCount: Int?, currentPage: Int?) -> Unit,
    physicalTimer: @Composable (Book) -> Unit,
    onUpdatePhysicalBookLoan: (ownership: PhysicalBookOwnership, returnAt: Long?) -> Unit,
    showSyncReadingProgress: Boolean,
    syncReadingProgressRunning: Boolean,
    progressChangePrompt: BookProgressChange?,
    onKeepSyncedProgress: (BookProgressChange) -> Unit,
    onRevertSyncedProgress: (BookProgressChange) -> Unit,
    libraryBooks: List<Book>,
    yearlyBooksGoal: Int,
    detailMessage: BookDetailMessage?,
    coverImageDownloadInProgress: Boolean,
    allShelves: List<com.vayana.core.database.model.Shelf>,
    shelvesForBook: List<com.vayana.core.database.model.Shelf>,
    onCreateShelf: (String) -> Unit,
    onAddToShelf: (Long) -> Unit,
    onRemoveFromShelf: (Long) -> Unit,
    onSetReadNext: (bookId: Long, queued: Boolean) -> Unit,
    readNextBumped: Flow<List<Book>>,
    onBack: () -> Unit,
    onContinueReading: (Long, String?) -> Unit,
    onOpenNotes: ((Long) -> Unit)?,
    onReadFromStart: ((Long) -> Unit)?,
    useWideActions: Boolean,
    onUpdateMetadata: (String, String, String, String, String, String) -> Unit,
    onUpdateReadingDates: (startedAt: Long?, finishedAt: Long?) -> Unit,
    onUpdateRating: (Float) -> Unit,
    onReplaceSource: (android.content.ContentResolver, Uri) -> Unit,
    onReplaceCover: (android.content.ContentResolver, Uri) -> Unit,
    onReplaceCoverFromWeb: (CoverImageRequest) -> Unit,
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
    communityQuoteCount: Int?,
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
    var physicalStatusBusy by remember(book?.id) { mutableStateOf(false) }
    val physicalStatusFailed = stringResource(R.string.physical_timer_failed)
    val homeLibraryOpenFailedMessage = stringResource(R.string.home_library_open_failed)
    var deleteStep by remember { mutableStateOf<DeleteStep?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showEditDescriptionDialog by remember { mutableStateOf(false) }
    var showCoverPreview by remember { mutableStateOf(false) }
    var showImportQuotesDialog by remember { mutableStateOf(false) }
    var showGoodreadsDialog by remember { mutableStateOf(false) }
    var goodreadsBrowserUrl by remember { mutableStateOf<String?>(null) }
    var openGoodreadsHandled by rememberSaveable { mutableStateOf(false) }
    var showOfflinePagesDialog by rememberSaveable { mutableStateOf(false) }
    var showBorrowReturnDateDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(book != null) {
        if (openGoodreadsOnStart && !openGoodreadsHandled && book != null) {
            openGoodreadsHandled = true
            onDismissGoodreads()
            goodreadsBrowserUrl = book.goodreadsSearchUrl()
        }
    }
    var showResetStatsDialog by remember { mutableStateOf(false) }
    var showEditCoverDialog by remember { mutableStateOf(false) }
    var showCoverImageSearch by remember { mutableStateOf(false) }
    var coverImageDownloadStarted by remember { mutableStateOf(false) }
    var showShareBookDialog by remember { mutableStateOf(false) }
    var showRemoveFromDeviceDialog by remember { mutableStateOf(false) }
    var readNextSeriesBreakWarning by remember { mutableStateOf<ReadNextSeriesBreakWarning?>(null) }
    var actionsExpanded by remember { mutableStateOf(false) }
    var shareImageOptions by remember { mutableStateOf(BookShareImageOptions()) }
    var shareImageBookTitle by rememberSaveable(book?.id, book?.title, book?.homeLibraryOriginalTitle) {
        mutableStateOf(book?.homeLibraryDisplayTitle.orEmpty())
    }
    var editingReadingDate by remember { mutableStateOf<ReadingDateField?>(null) }
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

    LaunchedEffect(coverImageDownloadInProgress) {
        if (coverImageDownloadInProgress) {
            coverImageDownloadStarted = true
        } else if (coverImageDownloadStarted) {
            coverImageDownloadStarted = false
            showCoverImageSearch = false
        }
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

    /** A finished book's read button starts it over ("Read again"); any other book resumes where it was left. */
    fun openForReading(book: Book) {
        if (book.readingState() == BookReadingState.FINISHED && onReadFromStart != null) {
            onReadFromStart(book.id)
        } else {
            onContinueReading(book.id, null)
        }
    }

    fun syncReadingProgress() {
        if (syncReadingProgressRunning) return
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
                        result.pullFailed || !result.metadataSynced -> syncProgressOnlyFailedMessage.format(result.failureMessage?.resolve(context.resources).orEmpty())
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
                    snackbarHostState.showSnackbar(syncProgressOnlyFailedMessage.format(result.message?.resolve(context.resources).orEmpty()))
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
    }

    val floatingNavigationInset = LocalFloatingNavigationInset.current
    val usesFloatingNavigation = floatingNavigationInset.value > 0f
    Scaffold(
        modifier = modifier,
        snackbarHost = { VayanaSnackbarHost(snackbarHostState) },
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.sm),
                )
                if (book != null) {
                    // Home Library owns a mirrored book catalog fields: no editing here.
                    if (!book.isHomeLibrary) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.library_edit_metadata),
                            )
                        }
                    }
                    if (useWideActions) {
                        if (book.hasLocalReadableSource()) {
                            Button(onClick = { openForReading(book) }) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoStories,
                                    contentDescription = null,
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(book.readingState().readActionLabel())
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { actionsExpanded = true }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.library_book_actions_content_description),
                            )
                        }
                        VayanaDropdownMenu(
                            expanded = actionsExpanded,
                            onDismissRequest = { actionsExpanded = false },
                            groups = listOf(
                                VayanaMenuGroup(
                                    buildList {
                                        if (book.isHomeLibrary) {
                                            add(
                                                VayanaMenuItem(
                                                    label = stringResource(R.string.home_library_view_book),
                                                    icon = Icons.Outlined.LocalLibrary,
                                                    onClick = {
                                                        if (!HomeLibraryLauncher.showBook(context, book.syncUuid.orEmpty())) {
                                                            coroutineScope.launch { snackbarHostState.showSnackbar(homeLibraryOpenFailedMessage) }
                                                        }
                                                    },
                                                ),
                                            )
                                        }
                                        add(
                                            VayanaMenuItem(
                                                label = stringResource(R.string.library_goodreads_import),
                                                icon = Icons.Outlined.Link,
                                                onClick = {
                                                    onDismissGoodreads()
                                                    goodreadsBrowserUrl = book.goodreadsSearchUrl()
                                                },
                                            ),
                                        )
                                        book.goodreadsUrl?.takeIf { it.isNotBlank() }?.let { goodreadsUrl ->
                                            add(
                                                VayanaMenuItem(
                                                    label = stringResource(R.string.library_goodreads_refresh),
                                                    icon = Icons.Outlined.Sync,
                                                    onClick = {
                                                        onDismissGoodreads()
                                                        showGoodreadsDialog = true
                                                        onImportGoodreads(goodreadsUrl)
                                                    },
                                                ),
                                            )
                                        }
                                    },
                                ),
                                VayanaMenuGroup(
                                    buildList {
                                        add(
                                            VayanaMenuItem(
                                                label = stringResource(R.string.library_import_quotes),
                                                icon = Icons.Outlined.EditNote,
                                                onClick = { showImportQuotesDialog = true },
                                            ),
                                        )
                                        if (book.hasLocalReadableSource()) {
                                            add(
                                                VayanaMenuItem(
                                                    label = stringResource(R.string.library_share_file),
                                                    icon = Icons.Outlined.Share,
                                                    onClick = { coroutineScope.launch { context.shareBookFile(book) } },
                                                ),
                                            )
                                        }
                                        if (!book.format.isOffline) {
                                            add(
                                                VayanaMenuItem(
                                                    label = stringResource(R.string.library_replace_source_file),
                                                    icon = Icons.Outlined.AutoStories,
                                                    onClick = {
                                                        sourcePicker.launch(arrayOf("application/epub+zip", "application/pdf", "application/octet-stream", "*/*"))
                                                    },
                                                ),
                                            )
                                        }
                                    },
                                ),
                                // Reading state. While reading, finishing is a quick action under the title; here it
                                // covers books read elsewhere. An empty group (finished, no stats) is left out.
                                VayanaMenuGroup(
                                    buildList {
                                        if (book.readingState() == BookReadingState.NOT_STARTED) {
                                            add(
                                                VayanaMenuItem(
                                                    label = stringResource(R.string.library_mark_finished),
                                                    icon = Icons.Outlined.Check,
                                                    onClick = onMarkFinished,
                                                ),
                                            )
                                        }
                                        if (book.hasReadingStats()) {
                                            add(
                                                VayanaMenuItem(
                                                    label = stringResource(R.string.library_reset_reading_stats),
                                                    icon = Icons.Outlined.RestartAlt,
                                                    onClick = { showResetStatsDialog = true },
                                                ),
                                            )
                                        }
                                    },
                                ),
                                VayanaMenuGroup(
                                    buildList {
                                        if (book.canRemoveLocalFileFromDevice()) {
                                            add(
                                                VayanaMenuItem(
                                                    label = stringResource(R.string.library_remove_from_device),
                                                    icon = Icons.Outlined.PhonelinkErase,
                                                    onClick = { showRemoveFromDeviceDialog = true },
                                                ),
                                            )
                                        }
                                        // A mirrored book goes when Home Library removes it.
                                        if (!book.isHomeLibrary) {
                                            add(
                                                VayanaMenuItem(
                                                    label = stringResource(R.string.library_delete_book),
                                                    icon = Icons.Outlined.Delete,
                                                    destructive = true,
                                                    onClick = { deleteStep = DeleteStep.CHOICE },
                                                ),
                                            )
                                        }
                                    },
                                ),
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (book?.hasLocalReadableSource() == true && !useWideActions && !usesFloatingNavigation) {
                FloatingActionButton(
                    onClick = { openForReading(book) },
                    modifier = Modifier
                        .padding(bottom = floatingNavigationInset)
                        .size(Sizes.fab),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = Elevations.shadowSmall),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoStories,
                        contentDescription = book.readingState().readActionLabel(),
                        modifier = Modifier.size(Sizes.iconLarge),
                    )
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
            // Staggered, not a row-aligned grid: on a wide screen each card drops into the shorter column
            // instead of leaving a gap beside a tall neighbour.
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(if (useWideActions) 2 else 1),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(Paddings.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalItemSpacing = Spacing.lg,
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .bookSharedBounds(
                                bookId = book.id,
                                enabled = transitionSource == BookOpenTransitionSource.HERO_CARD,
                            )
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        BookDispositionControl(book)
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
                                        .bookSharedElement(
                                            bookId = book.id,
                                            source = transitionSource ?: BookOpenTransitionSource.COVER,
                                            enabled = transitionSource == BookOpenTransitionSource.COVER ||
                                                transitionSource == BookOpenTransitionSource.READ_NEXT_COVER,
                                        )
                                        .size(width = Sizes.coverWidthDetail, height = Sizes.coverWidthDetail / Sizes.coverAspectRatio)
                                        .clickable { showCoverPreview = true },
                                )
                                if (!book.isHomeLibrary) AssistChip(
                                    onClick = { showEditCoverDialog = true },
                                    label = { Text(stringResource(R.string.library_edit_cover), style = MaterialTheme.typography.labelMedium) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                                        )
                                    },
                                    modifier = Modifier.padding(top = Spacing.sm),
                                    shape = RoundedCornerShape(Radii.full),
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                                    border = null,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = book.homeLibraryDisplayTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                HomeLibraryCatalogTitle(book)
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
                                BookStatusLine(book = book, modifier = Modifier.padding(top = Spacing.xs))
                                GoodreadsInfoLine(
                                    book = book,
                                    communityQuoteCount = communityQuoteCount,
                                    modifier = Modifier.padding(top = Spacing.sm),
                                )
                            }
                        }
                        BookQuickActions(
                            book = book,
                            state = book.readingState(),
                            notesCount = highlightCount ?: 0,
                            onMarkFinished = onMarkFinished,
                            onMarkPhysicalReading = {
                                if (!physicalStatusBusy) {
                                    physicalStatusBusy = true
                                    coroutineScope.launch {
                                        try { onMarkPhysicalReading() }
                                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                        catch (_: Exception) { snackbarHostState.showSnackbar(physicalStatusFailed) }
                                        finally { physicalStatusBusy = false }
                                    }
                                }
                            },
                            physicalStatusBusy = physicalStatusBusy,
                            onReadAgain = onReadFromStart
                                ?.takeIf { book.hasLocalReadableSource() && book.finishedLongAgo(System.currentTimeMillis()) }
                                ?.let { read -> { read(book.id) } },
                            onToggleReadNext = {
                                if (book.readNextAddedAt != null) {
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
                            onOpenNotes = onOpenNotes?.let { open -> { open(book.id) } },
                            onShare = {
                                shareImageOptions = BookShareImageOptions.forState(book.readingState(), shareImageOptions)
                                showShareBookDialog = true
                            },
                        )
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val state = book.readingState()
                            // A book not started yet has nothing to rate - unless it was rated anyway (e.g. read before).
                            if (book.isHomeLibrary) {
                                if (book.rating > 0f) {
                                    Text(
                                        text = stringResource(R.string.library_rating_label) + " · " +
                                            stringResource(R.string.library_rating_value, book.rating),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                }
                            } else if (state != BookReadingState.NOT_STARTED || book.rating > 0f) {
                                BookRatingRow(
                                    rating = book.rating,
                                    onRatingChange = onUpdateRating,
                                    promptToRate = state == BookReadingState.FINISHED,
                                )
                            }
                            BookTagsRow(
                                tags = book.tags(),
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
                        }
                    }
                }
                val readingState = book.readingState()
                item { FinishByCard(book, finishByDate, onFinishByChange) }
                val syncAction: (@Composable () -> Unit)? = if (showSyncReadingProgress) {
                    { SyncProgressButton(running = syncReadingProgressRunning, onClick = ::syncReadingProgress) }
                } else {
                    null
                }
                if (readingState == BookReadingState.NOT_STARTED && !book.format.isOffline) {
                    // Nothing read here yet, but another device may have started it: keep just the sync button.
                    if (syncAction != null) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { syncAction() }
                        }
                    }
                } else {
                    item {
                        ReadingStatsCard(
                            book = book,
                            finished = readingState == BookReadingState.FINISHED,
                            // Reading-progress sync is about reader positions, which a book read elsewhere has none of.
                            action = syncAction.takeUnless { book.format.isOffline },
                            onEditStarted = { editingReadingDate = ReadingDateField.STARTED },
                            onEditFinished = { editingReadingDate = ReadingDateField.FINISHED },
                            onChangeFormat = onChangeOfflineFormat,
                            onEditPages = { showOfflinePagesDialog = true },
                            onChangeOwnership = { ownership ->
                                onUpdatePhysicalBookLoan(ownership, book.borrowReturnAt)
                            },
                            onEditBorrowReturnDate = { showBorrowReturnDateDialog = true },
                        )
                    }
                }
                if (book.format == BookFormat.PHYSICAL) {
                    item { physicalTimer(book) }
                }
                if (book.isHomeLibrary) {
                    item {
                        HomeLibraryDetailsSection(
                            book = book,
                            onViewInHomeLibrary = {
                                if (!HomeLibraryLauncher.showBook(context, book.syncUuid.orEmpty())) {
                                    coroutineScope.launch { snackbarHostState.showSnackbar(homeLibraryOpenFailedMessage) }
                                }
                            },
                        )
                    }
                } else item {
                    BookDetailSection(
                        icon = Icons.Outlined.Description,
                        title = stringResource(R.string.library_about_book),
                        action = {
                            IconButton(onClick = { showEditDescriptionDialog = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.library_edit_description),
                                )
                            }
                        },
                    ) {
                        if (!cleanedDescription.isNullOrBlank()) {
                            ScrollableDescription(text = cleanedDescription)
                        } else {
                            Text(
                                text = stringResource(R.string.library_description_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = SectionDividerAlpha))
                        val footer = buildList {
                            add(stringResource(R.string.library_imported_on, book.createdAt.formatDate()))
                            if (!book.hasStartedReading()) {
                                book.lastReadAt?.let { add(stringResource(R.string.library_last_read_on, it.formatDate())) }
                            }
                        }
                        Text(
                            text = footer.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                item(span = StaggeredGridItemSpan.FullLine) { Spacer(modifier = Modifier.height(Sizes.bottomNavHeight)) }
            }
        }
    }

    when (deleteStep) {
        DeleteStep.CHOICE -> DeleteBookChoiceDialog(
            bookTitle = book?.title.orEmpty(),
            onDismissRequest = { deleteStep = null },
            onMoveToRecentlyDeleted = {
                deleteStep = null
                onDeleteBook()
            },
            onDeletePermanently = { deleteStep = DeleteStep.CONFIRM_PERMANENT },
        )
        DeleteStep.CONFIRM_PERMANENT -> if (book != null) {
            PermanentDeleteConfirmDialog(
                book = book,
                highlightCount = highlightCount,
                onDismissRequest = { deleteStep = null },
                onConfirm = {
                    deleteStep = null
                    onDeletePermanently()
                },
            )
        }
        null -> Unit
    }

    if (showOfflinePagesDialog && book != null) {
        OfflinePagesDialog(
            book = book,
            onDismiss = { showOfflinePagesDialog = false },
            onSave = { pageCount, currentPage ->
                showOfflinePagesDialog = false
                onUpdateOfflinePages(pageCount, currentPage)
            },
        )
    }

    val editingDate = editingReadingDate
    if (editingDate != null && book != null) {
        BookReadingDatePickerDialog(
            field = editingDate,
            book = book,
            onConfirm = { started, finished ->
                editingReadingDate = null
                onUpdateReadingDates(started, finished)
            },
            onDismiss = { editingReadingDate = null },
        )
    }

    if (showBorrowReturnDateDialog && book?.format == BookFormat.PHYSICAL) {
        BorrowReturnDatePickerDialog(
            current = book.borrowReturnAt,
            onConfirm = { returnAt ->
                showBorrowReturnDateDialog = false
                onUpdatePhysicalBookLoan(PhysicalBookOwnership.BORROWED, returnAt)
            },
            onClear = {
                showBorrowReturnDateDialog = false
                onUpdatePhysicalBookLoan(PhysicalBookOwnership.BORROWED, null)
            },
            onDismiss = { showBorrowReturnDateDialog = false },
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
            onKeepSyncedProgress = { onKeepSyncedProgress(prompt) },
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
                quoteLanguages = importState.quoteLanguages,
                onApply = onApplyGoodreads,
                onDismiss = {
                    showGoodreadsDialog = false
                    onDismissGoodreads()
                },
            )
            else -> GoodreadsImportStatusDialog(
                state = importState,
                onBrowse = {
                    showGoodreadsDialog = false
                    onDismissGoodreads()
                    goodreadsBrowserUrl = book.goodreadsUrl ?: book.goodreadsSearchUrl()
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
                // Open the review step with the book and quotes captured from the selected result.
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
            onSearchCover = {
                showEditCoverDialog = false
                showCoverImageSearch = true
            },
            onRemoveCover = onRemoveCover,
            onUseCover = onUseCover,
            onDismiss = { showEditCoverDialog = false },
        )
    }

    if (showCoverImageSearch && book != null) {
        CoverImageSearchBrowser(
            bookTitle = book.title,
            imageDownloadInProgress = coverImageDownloadInProgress,
            onImageSelected = { request ->
                onReplaceCoverFromWeb(request)
            },
            onDismiss = {
                showCoverImageSearch = false
                showEditCoverDialog = true
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
        val shareStats = book.shareStats()
        // Walks the whole library, so it isn't redone for every share-option toggle.
        val yearlyGoalReadCount = remember(libraryBooks, book, yearlyBooksGoal) {
            yearlyBookShareProgress(libraryBooks, book, yearlyBooksGoal)?.readCount
        }
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
                    bookTitle = shareImageBookTitle,
                    onBookTitleChange = { shareImageBookTitle = it },
                    hasRating = book.rating > 0f,
                    hasSeries = !book.series.isNullOrBlank() || !book.seriesNumber.isNullOrBlank(),
                    hasTags = book.tags().isNotEmpty(),
                    hasYearlyGoal = yearlyBooksGoal > 0,
                    onOptionsChange = { shareImageOptions = it },
                    stats = shareStats,
                    originalScriptTitle = book.homeLibraryOriginalTitle,
                    catalogTitle = book.title,
                )
            },
        ) {
            BookShareCard(
                title = shareImageBookTitle.trim().ifBlank { book.homeLibraryDisplayTitle },
                author = book.author,
                series = book.seriesDisplay(),
                statusLabel = shareStats.statusLabel,
                stat1Value = shareStats.stat1Value,
                stat1Label = shareStats.stat1Label,
                stat2Value = shareStats.stat2Value,
                stat2Label = shareStats.stat2Label,
                ratingValue = book.rating.takeIf { it > 0f }?.let { stringResource(R.string.share_card_stat_rating_value, it) },
                ratingLabel = stringResource(R.string.share_card_stat_rating_label),
                tags = book.tags(),
                footerLeft = shareStats.footerLeft,
                footerRight = stringResource(R.string.share_card_tagline),
                watermark = stringResource(R.string.share_card_watermark),
                theme = shareImageOptions.theme,
                showCover = shareImageOptions.showCover,
                showAuthor = shareImageOptions.showAuthor,
                showSeries = shareImageOptions.showSeries,
                showStatus = shareImageOptions.showStatus,
                showProgress = shareImageOptions.showProgress,
                showReadTime = shareImageOptions.showReadTime,
                showRating = shareImageOptions.showRating,
                showTags = shareImageOptions.showTags,
                showImportedDate = shareImageOptions.showImportedDate,
                showTagline = shareImageOptions.showTagline,
                layout = shareImageOptions.layout,
                backdropCoverAlpha = shareImageOptions.backdropCoverAlpha,
                progressFraction = shareStats.progressFraction,
                yearlyGoalReadCount = yearlyGoalReadCount.takeIf { shareImageOptions.showYearlyGoal },
                yearlyGoalTarget = yearlyBooksGoal.takeIf { shareImageOptions.showYearlyGoal && it > 0 },
            ) {
                // Fill whatever box the layout gives the cover, including Backdrop's full square.
                BookCover(book = book, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * The book's everyday actions as a row of labelled icon buttons under its title, so they are in view without
 * scrolling; the rarer ones stay in the overflow menu. What is offered follows [state]: Read next is for books not
 * started (or already queued, so they can come off), finishing is for the book being read, and notes only show when
 * there are some (the Notes screen would have nothing to show).
 */
@Composable
private fun BookQuickActions(
    onMarkPhysicalReading: () -> Unit,
    physicalStatusBusy: Boolean,
    book: Book,
    state: BookReadingState,
    notesCount: Int,
    onToggleReadNext: () -> Unit,
    onMarkFinished: () -> Unit,
    /** Set for a book finished long enough ago to be worth reading again; it takes Read next's place. */
    onReadAgain: (() -> Unit)?,
    onOpenNotes: (() -> Unit)?,
    onShare: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (book.format == BookFormat.PHYSICAL && state != BookReadingState.FINISHED) {
            BookQuickAction(
                icon = Icons.Outlined.AutoStories,
                label = stringResource(if (state == BookReadingState.NOT_STARTED)
                    R.string.physical_mark_reading else R.string.physical_read_today),
                onClick = onMarkPhysicalReading,
                enabled = !physicalStatusBusy,
                modifier = Modifier.weight(1f),
            )
        }
        val queued = book.readNextAddedAt != null
        if (onReadAgain != null && !queued) {
            BookQuickAction(
                icon = Icons.Outlined.Replay,
                label = stringResource(R.string.library_read_again),
                onClick = onReadAgain,
                modifier = Modifier.weight(1f),
            )
        } else if (!book.format.isOffline && (state == BookReadingState.NOT_STARTED || queued)) {
            BookQuickAction(
                icon = if (queued) Icons.Outlined.Check else Icons.AutoMirrored.Outlined.PlaylistAdd,
                label = stringResource(if (queued) R.string.library_quick_read_next_queued else R.string.library_quick_read_next),
                selected = queued,
                onClick = onToggleReadNext,
                modifier = Modifier.weight(1f),
            )
        }
        if (state == BookReadingState.READING) {
            BookQuickAction(
                icon = Icons.Outlined.TaskAlt,
                label = stringResource(R.string.library_quick_mark_finished),
                onClick = onMarkFinished,
                modifier = Modifier.weight(1f),
            )
        }
        if (onOpenNotes != null && notesCount > 0) {
            BookQuickAction(
                icon = Icons.AutoMirrored.Outlined.Notes,
                label = pluralStringResource(R.plurals.library_quick_notes, notesCount, notesCount),
                onClick = onOpenNotes,
                modifier = Modifier.weight(1f),
            )
        }
        BookQuickAction(
            icon = Icons.Outlined.Share,
            label = stringResource(R.string.library_quick_share),
            onClick = onShare,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BookQuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.large))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(Spacing.md)
                    .size(Sizes.icon),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/** Format and where the reader is with the book, e.g. "EPUB · 18% read". */
@Composable
private fun BookStatusLine(book: Book, modifier: Modifier = Modifier) {
    val status = when (book.readingState()) {
        BookReadingState.NOT_STARTED -> stringResource(R.string.library_status_not_started)
        BookReadingState.READING -> if (book.format.isOffline && book.pageCount == null) {
            stringResource(R.string.offline_book_status_reading)
        } else {
            stringResource(R.string.library_progress_value, (book.readingPercent * 100).roundToInt())
        }
        BookReadingState.FINISHED -> book.finishedReadingAt?.let { stringResource(R.string.library_status_finished_on, it.formatDate()) }
            ?: stringResource(R.string.library_status_finished)
    }
    Text(
        text = stringResource(
            R.string.library_status_line,
            book.format.displayLabel(),
            status,
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun SyncProgressButton(running: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = !running,
        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        if (running) {
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

@Composable
private fun BookReadingState.readActionLabel(): String = stringResource(
    when (this) {
        BookReadingState.NOT_STARTED -> R.string.library_start_reading
        BookReadingState.READING -> R.string.library_continue_reading
        BookReadingState.FINISHED -> R.string.library_read_again
    },
)

/**
 * Book details' date picker: corrects one reading date of [book] and hands both back. A book read here falls back to
 * its reading history for a start that was never recorded; one read outside the app keeps its dates as entered.
 */
@Composable
private fun BookReadingDatePickerDialog(
    field: ReadingDateField,
    book: Book,
    onConfirm: (startedAt: Long?, finishedAt: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    val shownStart = if (book.format.isOffline) {
        book.startedReadingAt
    } else {
        book.startedReadingAt ?: book.lastReadAt ?: book.createdAt
    }
    ReadingDatePickerDialog(
        field = field,
        current = when (field) {
            ReadingDateField.STARTED -> shownStart ?: now
            ReadingDateField.FINISHED -> book.finishedReadingAt ?: if (book.format.isOffline) now else book.updatedAt
        },
        startedAt = shownStart,
        finishedAt = book.finishedReadingAt,
        onConfirm = { chosen ->
            when (field) {
                ReadingDateField.STARTED -> onConfirm(chosen, book.finishedReadingAt)
                ReadingDateField.FINISHED -> onConfirm(shownStart, chosen)
            }
        },
        onDismiss = onDismiss,
    )
}

/**
 * The book's description in a fixed-height scroll area with a scrollbar in the theme's colours; edges fade where
 * there is more text to scroll to (not on E-Ink, where a gradient only dithers). Both appear only when it overflows.
 */
@Composable
private fun ScrollableDescription(text: String) {
    val scrollState = rememberScrollState()
    val thumbColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ScrollbarTrackAlpha)
    val fadeColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val fades = LocalDisplayProfile.current != DisplayProfile.E_INK
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = Sizes.bookDescriptionMaxHeight)
            .drawWithContent {
                drawContent()
                val overflow = scrollState.maxValue
                if (overflow <= 0 || overflow == Int.MAX_VALUE) return@drawWithContent
                val fadeHeight = DescriptionFadeHeight.toPx()
                if (fades && scrollState.value > 0) {
                    drawRect(
                        brush = Brush.verticalGradient(listOf(fadeColor, Color.Transparent), endY = fadeHeight),
                        size = Size(size.width, fadeHeight),
                    )
                }
                if (fades && scrollState.value < overflow) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.Transparent, fadeColor),
                            startY = size.height - fadeHeight,
                            endY = size.height,
                        ),
                        topLeft = Offset(0f, size.height - fadeHeight),
                        size = Size(size.width, fadeHeight),
                    )
                }
                val barWidth = ScrollbarWidth.toPx()
                val radius = CornerRadius(barWidth / 2)
                val x = size.width - barWidth
                drawRoundRect(color = trackColor, topLeft = Offset(x, 0f), size = Size(barWidth, size.height), cornerRadius = radius)
                val viewport = size.height
                val thumbHeight = (viewport * viewport / (viewport + overflow)).coerceAtLeast(ScrollbarMinThumb.toPx())
                val thumbTop = (viewport - thumbHeight) * scrollState.value / overflow
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(x, thumbTop),
                    size = Size(barWidth, thumbHeight),
                    cornerRadius = radius,
                )
            },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(end = Spacing.lg),
        )
    }
}

private const val SectionDividerAlpha = 0.5f
private const val ScrollbarTrackAlpha = 0.35f
private val ScrollbarWidth = 4.dp
private val ScrollbarMinThumb = 24.dp
private val DescriptionFadeHeight = 24.dp

@Composable
private fun BookRatingRow(
    rating: Float,
    onRatingChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** A finished, unrated book asks for a rating instead of just labelling the stars. */
    promptToRate: Boolean = false,
) {
    val normalizedRating = ((rating * 2f).roundToInt() / 2f).coerceIn(0f, 5f)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val asking = promptToRate && normalizedRating == 0f
            Text(
                text = stringResource(if (asking) R.string.library_rating_prompt else R.string.library_rating_label),
                style = MaterialTheme.typography.titleSmall,
                color = if (asking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // Empty stars already say "unrated"; only a given rating needs spelling out.
            if (normalizedRating > 0f) {
                Text(
                    text = stringResource(R.string.library_rating_value, normalizedRating),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

    BookDetailSection(
        icon = Icons.Outlined.CollectionsBookmark,
        title = stringResource(R.string.library_shelves_section_title),
        action = {
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.library_shelves_add_to_shelf))
            }
        },
    ) {
        if (shelvesForBook.isEmpty()) {
            Text(
                text = stringResource(R.string.library_shelves_none_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
    BookDetailMessage.QUEUE_FULL -> stringResource(R.string.queue_full)
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

private fun Book.goodreadsSearchUrl(): String =
    goodreadsSearchUrl(listOf(title, author.orEmpty()).filter(String::isNotBlank).joinToString(" "))
