package com.vayana.feature.reader

import android.widget.Toast
import com.vayana.core.designsystem.component.asString
import android.Manifest
import android.app.Activity
import android.graphics.BitmapFactory
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import android.view.ActionMode
import android.view.KeyEvent as AndroidKeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import com.vayana.core.designsystem.component.cloudSyncStatusText
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vayana.core.common.QuoteCitation
import com.vayana.core.designsystem.sharecard.QuoteShareDialog
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.datastore.settings.FloatSetting
import com.vayana.core.datastore.settings.IntSetting
import com.vayana.core.datastore.settings.ReaderTextAlign
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.FormatAlignJustify
import com.vayana.core.datastore.settings.ReaderFontFamily
import com.vayana.core.datastore.settings.ReaderTheme
import com.vayana.core.datastore.settings.SettingsRegistry
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.datastore.settings.TapZoneMode
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.LocalEinkPalette
import com.vayana.core.designsystem.theme.isMonochrome
import com.vayana.core.designsystem.theme.PageKeyDirection
import com.vayana.core.designsystem.theme.pageKeyDirection
import com.vayana.core.designsystem.theme.LocalDisplayProfile
import com.vayana.core.designsystem.theme.ThemeMode
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
import com.vayana.core.designsystem.theme.vayanaContentTransform
import com.vayana.core.designsystem.theme.vayanaFadeIn
import com.vayana.core.designsystem.theme.vayanaFadeOut
import com.vayana.core.designsystem.theme.vayanaScaleIn
import com.vayana.core.designsystem.theme.vayanaScaleOut
import com.vayana.core.designsystem.theme.vayanaSlideInVertically
import com.vayana.core.designsystem.theme.vayanaSlideOutVertically
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Palette
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.tokens.Strokes
import com.vayana.core.resources.R
import com.vayana.dictionary.api.DictionaryEntry
import com.vayana.dictionary.api.OnlineDictionarySource
import com.vayana.reader.api.BookEngine
import com.vayana.reader.api.Locator
import com.vayana.reader.api.TocEntry
import com.vayana.reader.web.FoliateBookEngine
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import android.view.WindowManager
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Translate
import com.vayana.reader.api.Footnote

@Composable
fun ReaderRoute(onBack: () -> Unit, modifier: Modifier = Modifier, onReviewVocabulary: () -> Unit = {}) {
    val viewModel: ReaderViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.effectiveSettings.collectAsStateWithLifecycle()
    val usingCustomStyle by viewModel.usingCustomStyle.collectAsStateWithLifecycle()
    val dictionaryLookup by viewModel.dictionaryLookup.collectAsStateWithLifecycle()
    val readingPositionPrompt by viewModel.readingPositionPrompt.collectAsStateWithLifecycle()
    val recentLookups by viewModel.recentLookups.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val activeReadingSessionSeconds by viewModel.activeReadingSessionSeconds.collectAsStateWithLifecycle()
    val paceFactor by viewModel.paceFactor.collectAsStateWithLifecycle()
    val engineGeneration by viewModel.engineGeneration.collectAsStateWithLifecycle()
    val readAloudVoices by viewModel.readAloudVoices.collectAsStateWithLifecycle()
    val readAloudEngines by viewModel.readAloudEngines.collectAsStateWithLifecycle()
    val pronunciations by viewModel.pronunciations.collectAsStateWithLifecycle()
    val pdfBookPreferences by viewModel.pdfBookPreferences.collectAsStateWithLifecycle()
    val pdfPasswordPrompt by viewModel.pdfPasswordPrompt.collectAsStateWithLifecycle()
    val pdfThumbnails by viewModel.pdfThumbnails.collectAsStateWithLifecycle()
    val readerControlsRequest by viewModel.readerControlsRequest.collectAsStateWithLifecycle()
    var showPdfPageBrowser by rememberSaveable { mutableStateOf(false) }
    val pdfPageControls = remember(viewModel, pdfBookPreferences?.rotationDegrees) {
        PdfPageControls(
            onCropMarginsChange = viewModel::updatePdfCropMargins,
            onFitWidthChange = viewModel::updatePdfFitWidth,
            onDarkenTextChange = viewModel::updateBolderText,
            rotationDegrees = pdfBookPreferences?.rotationDegrees ?: 0,
            onRotateClockwise = viewModel::rotatePdfClockwise,
            onBrowsePages = { showPdfPageBrowser = true },
        )
    }
    val readAloudVoiceControls = remember(readAloudVoices, readAloudEngines, pronunciations) {
        ReadAloudVoiceControls(
            voices = readAloudVoices,
            engines = readAloudEngines,
            onLoad = viewModel::loadReadAloudVoices,
            onVoiceChange = viewModel::updateReadAloudVoice,
            onEngineChange = viewModel::updateReadAloudEngine,
            pronunciations = pronunciations,
            onSavePronunciation = viewModel::savePronunciation,
            onRemovePronunciation = viewModel::removePronunciation,
        )
    }
    val context = LocalContext.current
    val dictionaryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            viewModel.cancelDictionaryInstall()
        } else {
            viewModel.installEnglishDictionary(uri.toString())
        }
    }
    var readAloudFromSelectionPending by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Notification permission controls visibility only; declining it must not prevent read-aloud itself.
        viewModel.startReadAloud(fromSelection = readAloudFromSelectionPending)
    }
    val startReadAloud = { fromSelection: Boolean ->
        val permissionGranted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission(Build.VERSION.SDK_INT, permissionGranted)) {
            readAloudFromSelectionPending = fromSelection
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startReadAloud(fromSelection)
        }
    }

    // Tablet landscape: book and notes side by side, instead of notes living only in the bottom chrome.
    // Gated by the "Two-column landscape layout" setting - landscapeTwoColumnLayout passes through
    // effectiveSettings unchanged (it isn't one of the per-book style overrides), so reading it here is safe.
    val configuration = LocalConfiguration.current
    val showNotesSidePanel = settings.landscapeTwoColumnLayout &&
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        configuration.screenWidthDp >= TabletLandscapeMinWidthDp

    // Routine auto-sync fires every few page turns; a snackbar per run interrupts reading (and on E-Ink
    // costs a full-screen refresh), so the outcome shows as a standing dot beside the clock instead.
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val footnote by viewModel.footnote.collectAsStateWithLifecycle()
    val bookFinishedPrompt by viewModel.bookFinishedPrompt.collectAsStateWithLifecycle()
    val readAloud by viewModel.readAloud.collectAsStateWithLifecycle()
    val returnRecap by viewModel.returnRecap.collectAsStateWithLifecycle()
    val chapterWords by viewModel.chapterWords.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
    if (showNotesSidePanel) {
        var notesSidePanelVisible by rememberSaveable { mutableStateOf(true) }
        Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            ReaderScreen(
                modifier = Modifier.weight(if (notesSidePanelVisible) ReaderPaneWeight else 1f).fillMaxHeight(),
                uiState = uiState,
                settings = settings,
                syncStatus = syncStatus,
                usingCustomStyle = usingCustomStyle,
                onUseCustomStyleChange = viewModel::setUseCustomStyle,
                onSaveJournal = viewModel::saveJournalEntry,
                dictionaryLookup = dictionaryLookup,
                readingPositionPrompt = readingPositionPrompt,
                recentLookups = recentLookups,
                searchResults = searchResults,
                onSearchQueryChange = viewModel::search,
                onSearchResultClick = viewModel::openSearchResult,
                onClearSearch = viewModel::clearSearch,
                onLookupWord = viewModel::lookupWord,
                onLookupOnline = viewModel::lookupOnline,
                onSaveLookupAsNote = viewModel::saveLookupAsNote,
                onSaveLookupAsVocabulary = viewModel::saveLookupAsVocabularyCard,
                onAcceptReadingPositionPrompt = viewModel::acceptReadingPositionPrompt,
                onDismissReadingPositionPrompt = viewModel::dismissReadingPositionPrompt,
                engineGeneration = engineGeneration,
                readerControlsRequest = readerControlsRequest,
                onEngineReady = viewModel::bindEngine,
                onEngineReleased = viewModel::releaseEngine,
                activeReadingSessionSeconds = activeReadingSessionSeconds,
                paceFactor = paceFactor,
                onReaderInteraction = viewModel::onReaderInteraction,
                onTapPrevious = viewModel::previousPage,
                onTapNext = viewModel::nextPage,
                onOpenTocEntry = viewModel::openTocEntry,
                onProgressChange = viewModel::goToProgress,
                onGoToPage = viewModel::goToPage,
                onPreparePageJump = viewModel::preparePageJump,
                onCancelPageJump = viewModel::cancelPageJump,
                onAnnotationClick = viewModel::openAnnotation,
                onReturnToPreviousPosition = viewModel::returnToPreviousPosition,
                onCreateHighlight = viewModel::createHighlight,
                onUpdateHighlightColor = viewModel::updateActiveHighlightColor,
                onDeleteHighlight = viewModel::deleteActiveHighlight,
                onConvertHighlightToUnderline = viewModel::convertActiveHighlightToUnderline,
                onDismissHighlight = viewModel::dismissActiveHighlight,
                onUpdateHighlightNote = viewModel::updateAnnotationNote,
                onCreateUnderline = viewModel::createUnderline,
                onCreateNote = viewModel::createNote,
                onCreateBookmark = viewModel::createBookmark,
                onClearSelection = viewModel::clearSelection,
                onDownloadDictionary = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(EnglishDictionaryDownloadUrl)))
                    }
                },
                onInstallDictionary = {
                    viewModel.prepareDictionaryInstall()
                    dictionaryPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                },
                onFontSizeChange = viewModel::updateFontSize,
                onLineHeightChange = viewModel::updateLineHeight,
                onFontFamilyChange = viewModel::updateFontFamily,
                onTextAlignChange = viewModel::updateTextAlign,
                onCustomFontChange = viewModel::updateCustomFont,
                onReaderThemeChange = viewModel::updateReaderTheme,
                onSideMarginChange = viewModel::updateSideMargin,
                onVolumeKeysChange = viewModel::updateVolumeKeys,
                onKeepAwakeChange = viewModel::updateKeepAwake,
                onShowHeadersChange = viewModel::updateShowHeaders,
                onShowFooterChange = viewModel::updateShowFooter,
                onOverridePublisherTypographyChange = viewModel::updateOverridePublisherTypography,
                onBionicReadingChange = viewModel::updateBionicReading,
                pdfPageControls = pdfPageControls,
                onReadAloudRateChange = viewModel::updateReadAloudRate,
                onReadAloudPitchChange = viewModel::updateReadAloudPitch,
                readAloudVoiceControls = readAloudVoiceControls,
                onPause = viewModel::onPause,
                onResume = viewModel::onResume,
                footnote = footnote,
                onDismissFootnote = viewModel::dismissFootnote,
                onOpenFootnote = viewModel::openFootnoteTarget,
                readAloud = readAloud,
                onStartReadAloud = { startReadAloud(false) },
                onReadAloudFromSelection = { startReadAloud(true) },
                onReadAloudFromHighlight = viewModel::startReadAloudFromActiveHighlight,
                onToggleReadAloud = viewModel::toggleReadAloud,
                onPauseReadAloud = viewModel::pauseReadAloud,
                onStopReadAloud = viewModel::stopReadAloud,
                onCycleReadAloudSleepTimer = viewModel::cycleReadAloudSleepTimer,
                onDismissReadAloudVoiceMissing = viewModel::dismissReadAloudVoiceMissing,
                returnRecap = returnRecap,
                onDismissReturnRecap = viewModel::dismissReturnRecap,
                onBrightnessChange = viewModel::updateBrightness,
                onWarmLightChange = viewModel::updateWarmLight,
                chapterWords = chapterWords,
                onLoadChapterWords = viewModel::loadChapterWords,
                onSaveChapterWord = viewModel::saveChapterWord,
                onReviewVocabulary = onReviewVocabulary,
                onBack = onBack,
            )
            if (notesSidePanelVisible) {
                NotesSidePanel(
                    modifier = Modifier.weight(NotesPaneWeight).fillMaxHeight(),
                    uiState = uiState,
                    onAnnotationClick = viewModel::openAnnotation,
                    onEditNote = viewModel::updateAnnotationNote,
                )
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clickable { notesSidePanelVisible = !notesSidePanelVisible },
            shape = RoundedCornerShape(topStart = Radii.large, bottomStart = Radii.large),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = Elevations.shadowSmall,
        ) {
            Icon(
                imageVector = if (notesSidePanelVisible) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = stringResource(
                    if (notesSidePanelVisible) R.string.reader_hide_notes_panel else R.string.reader_show_notes_panel,
                ),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.md),
            )
        }
        }
    } else {
    ReaderScreen(
        modifier = Modifier.fillMaxSize(),
        uiState = uiState,
        settings = settings,
        syncStatus = syncStatus,
        usingCustomStyle = usingCustomStyle,
        onUseCustomStyleChange = viewModel::setUseCustomStyle,
        onSaveJournal = viewModel::saveJournalEntry,
        dictionaryLookup = dictionaryLookup,
        readingPositionPrompt = readingPositionPrompt,
        recentLookups = recentLookups,
        searchResults = searchResults,
        onSearchQueryChange = viewModel::search,
        onSearchResultClick = viewModel::openSearchResult,
        onClearSearch = viewModel::clearSearch,
        onLookupWord = viewModel::lookupWord,
        onLookupOnline = viewModel::lookupOnline,
        onSaveLookupAsNote = viewModel::saveLookupAsNote,
        onSaveLookupAsVocabulary = viewModel::saveLookupAsVocabularyCard,
        onAcceptReadingPositionPrompt = viewModel::acceptReadingPositionPrompt,
        onDismissReadingPositionPrompt = viewModel::dismissReadingPositionPrompt,
        engineGeneration = engineGeneration,
        readerControlsRequest = readerControlsRequest,
        onEngineReady = viewModel::bindEngine,
        onEngineReleased = viewModel::releaseEngine,
        activeReadingSessionSeconds = activeReadingSessionSeconds,
        paceFactor = paceFactor,
        onReaderInteraction = viewModel::onReaderInteraction,
        onTapPrevious = viewModel::previousPage,
        onTapNext = viewModel::nextPage,
        onOpenTocEntry = viewModel::openTocEntry,
        onProgressChange = viewModel::goToProgress,
        onGoToPage = viewModel::goToPage,
        onPreparePageJump = viewModel::preparePageJump,
        onCancelPageJump = viewModel::cancelPageJump,
        onAnnotationClick = viewModel::openAnnotation,
        onReturnToPreviousPosition = viewModel::returnToPreviousPosition,
        onCreateHighlight = viewModel::createHighlight,
        onUpdateHighlightColor = viewModel::updateActiveHighlightColor,
        onDeleteHighlight = viewModel::deleteActiveHighlight,
        onConvertHighlightToUnderline = viewModel::convertActiveHighlightToUnderline,
        onDismissHighlight = viewModel::dismissActiveHighlight,
        onUpdateHighlightNote = viewModel::updateAnnotationNote,
        onCreateUnderline = viewModel::createUnderline,
        onCreateNote = viewModel::createNote,
        onCreateBookmark = viewModel::createBookmark,
        onClearSelection = viewModel::clearSelection,
        onDownloadDictionary = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(EnglishDictionaryDownloadUrl)))
            }
        },
        onInstallDictionary = {
            viewModel.prepareDictionaryInstall()
            dictionaryPicker.launch(arrayOf("application/zip", "application/octet-stream"))
        },
        onFontSizeChange = viewModel::updateFontSize,
        onLineHeightChange = viewModel::updateLineHeight,
        onFontFamilyChange = viewModel::updateFontFamily,
        onTextAlignChange = viewModel::updateTextAlign,
        onCustomFontChange = viewModel::updateCustomFont,
        onReaderThemeChange = viewModel::updateReaderTheme,
        onSideMarginChange = viewModel::updateSideMargin,
        onVolumeKeysChange = viewModel::updateVolumeKeys,
        onKeepAwakeChange = viewModel::updateKeepAwake,
        onShowHeadersChange = viewModel::updateShowHeaders,
        onShowFooterChange = viewModel::updateShowFooter,
        onOverridePublisherTypographyChange = viewModel::updateOverridePublisherTypography,
        onBionicReadingChange = viewModel::updateBionicReading,
        pdfPageControls = pdfPageControls,
        onReadAloudRateChange = viewModel::updateReadAloudRate,
        onReadAloudPitchChange = viewModel::updateReadAloudPitch,
        readAloudVoiceControls = readAloudVoiceControls,
        onPause = viewModel::onPause,
        onResume = viewModel::onResume,
        footnote = footnote,
        onDismissFootnote = viewModel::dismissFootnote,
        onOpenFootnote = viewModel::openFootnoteTarget,
        readAloud = readAloud,
        onStartReadAloud = { startReadAloud(false) },
        onReadAloudFromSelection = { startReadAloud(true) },
        onReadAloudFromHighlight = viewModel::startReadAloudFromActiveHighlight,
        onToggleReadAloud = viewModel::toggleReadAloud,
        onPauseReadAloud = viewModel::pauseReadAloud,
        onStopReadAloud = viewModel::stopReadAloud,
        onCycleReadAloudSleepTimer = viewModel::cycleReadAloudSleepTimer,
        onDismissReadAloudVoiceMissing = viewModel::dismissReadAloudVoiceMissing,
        returnRecap = returnRecap,
        onDismissReturnRecap = viewModel::dismissReturnRecap,
        onBrightnessChange = viewModel::updateBrightness,
        onWarmLightChange = viewModel::updateWarmLight,
        chapterWords = chapterWords,
        onLoadChapterWords = viewModel::loadChapterWords,
        onSaveChapterWord = viewModel::saveChapterWord,
        onReviewVocabulary = onReviewVocabulary,
        onBack = onBack,
    )
    }
    bookFinishedPrompt?.let { prompt ->
        BookFinishedDialog(
            initialRating = prompt.rating,
            onConfirm = viewModel::confirmBookFinished,
            onDismiss = viewModel::dismissBookFinishedPrompt,
        )
    }
    pdfPasswordPrompt?.let { prompt ->
        PdfPasswordDialog(
            incorrect = prompt.incorrect,
            onSubmit = viewModel::providePdfPassword,
            onCancel = {
                viewModel.providePdfPassword(null)
                onBack()
            },
        )
    }
    if (showPdfPageBrowser) {
        val loaded = uiState as? ReaderUiState.Loaded
        PdfPageBrowserDialog(
            pageLabels = loaded?.pageLabels.orEmpty(),
            currentPage = loaded?.currentLocator?.currentPage,
            thumbnails = pdfThumbnails,
            onLoadThumbnail = viewModel::loadPdfThumbnail,
            onSelectPage = { index ->
                showPdfPageBrowser = false
                viewModel.goToPdfPage(index)
            },
            onDismiss = { showPdfPageBrowser = false },
        )
    }
    }
}

@Composable
private fun PdfPasswordDialog(
    incorrect: Boolean,
    onSubmit: (String?) -> Unit,
    onCancel: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.reader_pdf_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(if (incorrect) R.string.reader_pdf_password_incorrect else R.string.reader_pdf_password_body))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.reader_pdf_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(password) }, enabled = password.isNotEmpty()) {
                Text(stringResource(R.string.reader_pdf_password_open))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun PdfPageBrowserDialog(
    pageLabels: List<String>,
    currentPage: Int?,
    thumbnails: Map<Int, ByteArray>,
    onLoadThumbnail: (Int, Int) -> Unit,
    onSelectPage: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var pageInput by rememberSaveable { mutableStateOf("") }
    var invalidInput by rememberSaveable { mutableStateOf(false) }
    val pageCount = pageLabels.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_pdf_pages_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = pageInput,
                        onValueChange = {
                            pageInput = it
                            invalidInput = false
                        },
                        label = { Text(stringResource(R.string.reader_pdf_page_jump_label)) },
                        supportingText = if (invalidInput) ({ Text(stringResource(R.string.reader_pdf_page_jump_invalid)) }) else null,
                        isError = invalidInput,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = {
                        val page = resolvePdfPageInput(pageInput, pageLabels)
                        if (page == null) invalidInput = true else onSelectPage(page)
                    }) {
                        Text(stringResource(R.string.reader_pdf_page_jump_action))
                    }
                }
                if (pageCount == 0) {
                    Text(stringResource(R.string.reader_pdf_pages_unavailable))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(112.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                        contentPadding = PaddingValues(vertical = Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        gridItemsIndexed(pageLabels, key = { index, _ -> index }) { index, label ->
                            LaunchedEffect(index) { onLoadThumbnail(index, 240) }
                            val bytes = thumbnails[index]
                            // Decoded off the main thread: the grid fills a cell at a time as thumbnails land.
                            val bitmapState = produceState<ImageBitmap?>(initialValue = null, bytes) {
                                value = bytes?.let {
                                    withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                                }
                            }
                            Surface(
                                onClick = { onSelectPage(index) },
                                shape = RoundedCornerShape(Radii.medium),
                                color = if (currentPage == index + 1) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val bitmap = bitmapState.value
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = stringResource(R.string.reader_pdf_page_thumbnail_description, label),
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 128.dp, max = 190.dp),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().size(128.dp),
                                            contentAlignment = Alignment.Center,
                                        ) { VayanaCircularProgressIndicator() }
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (currentPage == index + 1) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(Spacing.xs),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_pdf_pages_close)) } },
    )
}

internal fun resolvePdfPageInput(input: String, pageLabels: List<String>): Int? {
    val normalized = input.trim()
    if (normalized.isEmpty()) return null
    return pageLabels.indexOfFirst { it.equals(normalized, ignoreCase = true) }.takeIf { it >= 0 }
        ?: normalized.toIntOrNull()?.minus(1)?.takeIf { it in pageLabels.indices }
}

/**
 * Asked when the story ends (before any back matter): mark the book completed, with an optional star rating. The
 * stars start at the book's current rating; tapping the lit last star again clears it.
 */
@Composable
private fun BookFinishedDialog(
    initialRating: Float,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var rating by rememberSaveable { mutableFloatStateOf(initialRating.roundToInt().toFloat().coerceIn(0f, 5f)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.TaskAlt, contentDescription = null) },
        title = { Text(stringResource(R.string.reader_book_finished_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(stringResource(R.string.reader_book_finished_body))
                Text(
                    text = stringResource(R.string.reader_book_finished_rating_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    for (star in 1..5) {
                        val lit = star <= rating
                        IconButton(onClick = { rating = if (rating == star.toFloat()) 0f else star.toFloat() }) {
                            Icon(
                                imageVector = if (lit) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = stringResource(R.string.reader_book_finished_rate_star, star),
                                tint = if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(rating) }) {
                Text(stringResource(R.string.reader_book_finished_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reader_book_finished_dismiss))
            }
        },
    )
}

/** A selection captured for the quote card, so the selection itself can be cleared while the card is open. */
private data class SelectionShare(
    val text: String,
    val chapterTitle: String?,
    val author: String?,
    val bookTitle: String?,
    val coverPath: String?,
    val series: String?,
    val seriesNumber: String?,
)

private enum class ReaderPanel { CONTENTS, BOOKMARKS, NOTES, PROGRESS, STYLE, READ_ALOUD, SEARCH, WORDS }

internal enum class HighlightColor(val key: String, val labelRes: Int, val swatch: Color) {
    YELLOW("yellow", R.string.reader_selection_highlight_yellow, Color(0xFFF6C453)),
    GREEN("green", R.string.reader_selection_highlight_green, Color(0xFF7BAE7F)),
    BLUE("blue", R.string.reader_selection_highlight_blue, Color(0xFF5B8DEF)),
    PINK("pink", R.string.reader_selection_highlight_pink, Color(0xFFD77FA1)),
}

@Composable
private fun ReaderScreen(
    modifier: Modifier = Modifier,
    uiState: ReaderUiState,
    settings: SettingsSnapshot,
    syncStatus: ReaderSyncStatus,
    usingCustomStyle: Boolean,
    onUseCustomStyleChange: (Boolean) -> Unit,
    onSaveJournal: suspend (String) -> Unit,
    dictionaryLookup: DictionaryLookupState,
    recentLookups: List<String>,
    searchResults: List<com.vayana.reader.api.SearchResult>,
    onSearchQueryChange: (String) -> Unit,
    onSearchResultClick: (com.vayana.reader.api.SearchResult) -> Unit,
    onClearSearch: () -> Unit,
    onLookupWord: (String) -> Unit,
    onLookupOnline: (OnlineDictionarySource) -> Unit,
    onSaveLookupAsNote: (DictionaryEntry) -> Unit,
    onSaveLookupAsVocabulary: (DictionaryEntry) -> Unit,
    readingPositionPrompt: ReadingPositionPrompt?,
    onAcceptReadingPositionPrompt: () -> Unit,
    onDismissReadingPositionPrompt: () -> Unit,
    engineGeneration: Int,
    readerControlsRequest: Long,
    onEngineReady: (BookEngine) -> Unit,
    onEngineReleased: (BookEngine) -> Unit,
    activeReadingSessionSeconds: Long,
    paceFactor: Float,
    onReaderInteraction: () -> Unit,
    onTapPrevious: () -> Unit,
    onTapNext: () -> Unit,
    onOpenTocEntry: (String) -> Unit,
    onProgressChange: (Float) -> Unit,
    onGoToPage: (Int) -> Unit,
    onPreparePageJump: (() -> Unit) -> Unit,
    onCancelPageJump: () -> Unit,
    onAnnotationClick: (Annotation) -> Unit,
    onReturnToPreviousPosition: () -> Unit,
    onCreateHighlight: (String) -> Unit,
    onUpdateHighlightColor: (String) -> Unit,
    onDeleteHighlight: () -> Unit,
    onConvertHighlightToUnderline: () -> Unit,
    onDismissHighlight: () -> Unit,
    onUpdateHighlightNote: (Annotation, String) -> Unit,
    onCreateUnderline: () -> Unit,
    onCreateNote: (String) -> Unit,
    onCreateBookmark: () -> Unit,
    onClearSelection: () -> Unit,
    onDownloadDictionary: () -> Unit,
    onInstallDictionary: () -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onFontFamilyChange: (ReaderFontFamily) -> Unit,
    onTextAlignChange: (ReaderTextAlign) -> Unit,
    onCustomFontChange: (String?) -> Unit,
    onReaderThemeChange: (ReaderTheme) -> Unit,
    onSideMarginChange: (Int) -> Unit,
    onVolumeKeysChange: (Boolean) -> Unit,
    onKeepAwakeChange: (Boolean) -> Unit,
    onShowHeadersChange: (Boolean) -> Unit,
    onShowFooterChange: (Boolean) -> Unit,
    onOverridePublisherTypographyChange: (Boolean) -> Unit,
    onBionicReadingChange: (Boolean) -> Unit,
    pdfPageControls: PdfPageControls,
    onReadAloudRateChange: (Float) -> Unit,
    onReadAloudPitchChange: (Float) -> Unit,
    readAloudVoiceControls: ReadAloudVoiceControls,
    onPause: () -> Unit,
    onResume: () -> Unit,
    footnote: Footnote?,
    onDismissFootnote: () -> Unit,
    onOpenFootnote: () -> Unit,
    readAloud: ReadAloudState,
    onStartReadAloud: () -> Unit,
    onReadAloudFromSelection: () -> Unit,
    onReadAloudFromHighlight: () -> Unit,
    onToggleReadAloud: () -> Unit,
    onPauseReadAloud: () -> Unit,
    onStopReadAloud: () -> Unit,
    onCycleReadAloudSleepTimer: () -> Unit,
    onDismissReadAloudVoiceMissing: () -> Unit,
    returnRecap: ReaderRecap?,
    onDismissReturnRecap: () -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onWarmLightChange: (Int) -> Unit,
    chapterWords: ChapterWordsState,
    onLoadChapterWords: () -> Unit,
    onSaveChapterWord: (ChapterWord) -> Unit,
    onReviewVocabulary: () -> Unit,
    onBack: () -> Unit,
) {
    var chromeVisible by remember { mutableStateOf(false) }
    var selectedPanel by remember { mutableStateOf(ReaderPanel.CONTENTS) }
    var noteDialogVisible by remember { mutableStateOf(false) }
    var noteHighlight by remember { mutableStateOf<Annotation?>(null) }
    var sharingSelection by remember { mutableStateOf<SelectionShare?>(null) }
    var footerShowsBookTime by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val citationPattern = stringResource(R.string.quote_citation)
    val audioFeaturesEnabled = settings.readerAudioFeaturesEnabled
    val activeHighlightCard = (uiState as? ReaderUiState.Loaded)?.highlightCard
    val onEngineReadyState = rememberUpdatedState(onEngineReady)
    val onEngineReleasedState = rememberUpdatedState(onEngineReleased)
    val onReaderInteractionState = rememberUpdatedState(onReaderInteraction)
    val onPauseState = rememberUpdatedState(onPause)
    val onResumeState = rememberUpdatedState(onResume)
    val readAloudPlayingState = rememberUpdatedState(readAloud.playing)
    val lifecycleOwner = LocalLifecycleOwner.current
    val rootView = LocalView.current
    val focusRequester = remember { FocusRequester() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var volumeKeyDownAt by remember { mutableStateOf(0L) }
    val onReaderTapState = rememberUpdatedState<(Float, Int) -> Unit> { x, width ->
        // The WebView reports annotation hits after ACTION_UP. Its short arbitration delay below lets this state
        // update first, so a highlight in a side tap-zone opens its card instead of also turning the page.
        if (activeHighlightCard != null) return@rememberUpdatedState
        if (shouldPauseReadAloudOnReaderTap(readAloud.playing)) {
            onPauseReadAloud()
            return@rememberUpdatedState
        }
        val menuStart = width / 3f
        val menuEnd = menuStart * 2f
        when {
            chromeVisible -> chromeVisible = false
            x < menuStart && settings.readerTapZoneMode != TapZoneMode.SWIPE_ONLY -> onTapPrevious()
            x > menuEnd && settings.readerTapZoneMode != TapZoneMode.SWIPE_ONLY -> onTapNext()
            // Middle taps are arbitrated inside the book document, where a tap on a word can remain dictionary lookup.
            else -> Unit
        }
    }
    LaunchedEffect(readerControlsRequest) {
        if (readerControlsRequest > 0L) {
            selectedPanel = ReaderPanel.STYLE
            chromeVisible = true
        }
    }
    val readerDocumentReady = uiState is ReaderUiState.Loaded
    LaunchedEffect(
        webViewRef,
        readerDocumentReady,
        settings.readerControlsTapMode,
        chromeVisible,
        readAloud.playing,
    ) {
        if (!readerDocumentReady) return@LaunchedEffect
        webViewRef?.evaluateJavascript(
            "window.VayanaReader && window.VayanaReader.setReaderControlsGesture(" +
                "${settings.readerControlsTapMode.tapCount}, $chromeVisible, ${readAloud.playing})",
            null,
        )
    }
    val onHardwarePageKeyState = rememberUpdatedState<(Int, Int, Long) -> Boolean> { keyCode, action, heldMillis ->
        val pageKey = pageKeyDirection(keyCode)
        if (pageKey != null) {
            // Only the first press turns the page: a held button would repeat and race through the book on a slow panel.
            if (chromeVisible) {
                false
            } else {
                if (action == AndroidKeyEvent.ACTION_DOWN && heldMillis == 0L) {
                    onReaderInteraction()
                    if (pageKey == PageKeyDirection.PREVIOUS) onTapPrevious() else onTapNext()
                }
                true
            }
        } else if (keyCode == AndroidKeyEvent.KEYCODE_VOLUME_UP || keyCode == AndroidKeyEvent.KEYCODE_VOLUME_DOWN) {
            if (!shouldInterceptReaderVolumeKey(readAloud.playing, settings.readerVolumeKeys, chromeVisible)) {
                false
            } else if (chromeVisible) {
                if (action == AndroidKeyEvent.ACTION_UP) {
                    chromeVisible = false
                }
                true
            } else {
                if (action == AndroidKeyEvent.ACTION_UP) {
                    onReaderInteraction()
                    if (heldMillis >= VolumeKeyLongPressMillis) {
                        onBack()
                    } else {
                        when (keyCode) {
                            AndroidKeyEvent.KEYCODE_VOLUME_UP -> onTapPrevious()
                            AndroidKeyEvent.KEYCODE_VOLUME_DOWN -> onTapNext()
                        }
                    }
                }
                true
            }
        } else {
            false
        }
    }

    // Keep WebView lifecycle explicit; Compose disposal is not enough for this hardware surface.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Speech may need the book engine to load the next chapter while the screen is locked.
                    if (shouldPauseReaderWebView(readAloudPlayingState.value)) webViewRef?.onPause()
                    onPauseState.value()
                }
                Lifecycle.Event.ON_RESUME -> {
                    webViewRef?.onResume()
                    onResumeState.value()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onPauseState.value()
        }
    }

    DisposableEffect(settings.readerKeepAwake) {
        val previous = rootView.keepScreenOn
        rootView.keepScreenOn = settings.readerKeepAwake
        onDispose { rootView.keepScreenOn = previous }
    }

    // The level being dragged on one edge during a light swipe; it stands in for the setting until the saved value arrives.
    var liveLight by remember { mutableStateOf<Pair<ReaderEdge, Int>?>(null) }
    var lightSwiping by remember { mutableStateOf(false) }
    val brightnessPercent = liveLight?.takeIf { it.first == ReaderEdge.LEFT }?.second ?: settings.readerBrightnessPercent
    val warmLightPercent = liveLight?.takeIf { it.first == ReaderEdge.RIGHT }?.second ?: settings.readerWarmLightPercent
    LaunchedEffect(settings.readerBrightnessPercent, settings.readerWarmLightPercent) {
        if (!lightSwiping) liveLight = null
    }
    LaunchedEffect(brightnessPercent) {
        val window = (rootView.context as? Activity)?.window ?: return@LaunchedEffect
        window.attributes = window.attributes.apply {
            screenBrightness = if (brightnessPercent > 0) brightnessPercent / 100f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val window = (rootView.context as? Activity)?.window ?: return@onDispose
            window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
    val onEdgeSwipeState = rememberUpdatedState<(ReaderEdge, Float, Boolean) -> Unit> { edge, upFraction, finished ->
        lightSwiping = !finished
        val value = when (edge) {
            ReaderEdge.LEFT -> ((settings.readerBrightnessPercent.takeIf { it > 0 } ?: EdgeSwipeBrightnessStart) + upFraction * 100)
                .roundToInt()
                .coerceIn(EdgeSwipeMinBrightness, SettingsRegistry.ReaderBrightness.range.last)
            ReaderEdge.RIGHT -> (settings.readerWarmLightPercent + upFraction * 100)
                .roundToInt()
                .coerceIn(0, SettingsRegistry.ReaderWarmLight.range.last)
        }
        liveLight = edge to value
        if (finished) {
            when (edge) {
                ReaderEdge.LEFT -> onBrightnessChange(value)
                ReaderEdge.RIGHT -> onWarmLightChange(value)
            }
        }
    }
    // A drag on a PDF page larger than the screen pans it; brightness swipes would take the ones starting at an edge.
    val pageScrollable = (uiState as? ReaderUiState.Loaded)?.pageScrollable == true
    val edgeSwipeEnabledState = rememberUpdatedState(settings.readerEdgeSwipeLight && !chromeVisible && !pageScrollable)

    // Immersive reading: status bar hides with the rest of the chrome, comes back on tap.
    // Full screen hides the navigation bar too.
    DisposableEffect(chromeVisible, settings.readerFullScreen) {
        val window = (rootView.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, rootView)
            if (chromeVisible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(
                    if (settings.readerFullScreen) WindowInsetsCompat.Type.systemBars() else WindowInsetsCompat.Type.statusBars(),
                )
            }
        }
        onDispose {
            val disposeWindow = (rootView.context as? Activity)?.window ?: return@onDispose
            WindowCompat.getInsetsController(disposeWindow, rootView).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(audioFeaturesEnabled) {
        if (!audioFeaturesEnabled && selectedPanel == ReaderPanel.READ_ALOUD) {
            selectedPanel = ReaderPanel.STYLE
        }
    }

    val isEink = settings.displayProfile == DisplayProfile.E_INK
    val currentLocator = (uiState as? ReaderUiState.Loaded)?.currentLocator
    val currentLocatorCfi = currentLocator?.cfi

    // Successive partial E-Ink refreshes accumulate ghosting; periodically forcing one
    // maximal-area repaint (a brief full-black flash) makes the panel's controller do a clean
    // full update, the same trick Kindle/Boox readers use ("refresh every N pages").
    // Panels and menus leave the most ghosting behind, so closing one and entering a new chapter refresh too.
    var einkPageTurnCount by remember { mutableIntStateOf(0) }
    var einkFlashTrigger by remember { mutableIntStateOf(0) }
    var einkFlashVisible by remember { mutableStateOf(false) }
    val einkRefreshEnabled = isEink && settings.einkRefreshEveryPages > 0
    LaunchedEffect(currentLocatorCfi) {
        val refreshEveryPages = settings.einkRefreshEveryPages
        if (currentLocatorCfi != null && einkRefreshEnabled) {
            einkPageTurnCount++
            if (einkPageTurnCount % refreshEveryPages == 0) {
                einkFlashTrigger++
            }
        }
    }
    var chromeWasShown by remember { mutableStateOf(false) }
    LaunchedEffect(chromeVisible) {
        if (chromeVisible) {
            chromeWasShown = true
        } else if (chromeWasShown) {
            chromeWasShown = false
            if (einkRefreshEnabled) einkFlashTrigger++
        }
    }
    val chapterKey = currentLocator?.href?.substringBefore('#')
    var lastChapterKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(chapterKey) {
        val previous = lastChapterKey
        lastChapterKey = chapterKey
        if (previous != null && chapterKey != null && chapterKey != previous && einkRefreshEnabled) einkFlashTrigger++
    }
    LaunchedEffect(einkFlashTrigger) {
        if (einkFlashTrigger == 0) return@LaunchedEffect
        einkFlashVisible = true
        delay(EinkFlashDurationMillis)
        einkFlashVisible = false
    }

    val readAloudPanelVisible = audioFeaturesEnabled && readAloud.active && !chromeVisible
    var readAloudPanelHeightPx by remember { mutableIntStateOf(0) }
    var webViewBounds by remember { mutableStateOf<Rect?>(null) }
    val readAloudPanelHeight = with(LocalDensity.current) { readAloudPanelHeightPx.toDp() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val pageKey = pageKeyDirection(event.key.nativeKeyCode)
                if (pageKey != null) {
                    if (chromeVisible) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        onReaderInteraction()
                        if (pageKey == PageKeyDirection.PREVIOUS) onTapPrevious() else onTapNext()
                    }
                    true
                } else if (event.key == Key.VolumeUp || event.key == Key.VolumeDown) {
                    if (!shouldInterceptReaderVolumeKey(readAloud.playing, settings.readerVolumeKeys, chromeVisible)) {
                        return@onPreviewKeyEvent false
                    }
                    if (chromeVisible) {
                        if (event.type == KeyEventType.KeyUp) {
                            chromeVisible = false
                        }
                        return@onPreviewKeyEvent true
                    }
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (volumeKeyDownAt == 0L) volumeKeyDownAt = System.currentTimeMillis()
                        }
                        KeyEventType.KeyUp -> {
                            val heldMillis = if (volumeKeyDownAt == 0L) 0L else System.currentTimeMillis() - volumeKeyDownAt
                            volumeKeyDownAt = 0L
                            onReaderInteraction()
                            if (heldMillis >= VolumeKeyLongPressMillis) {
                                onBack()
                            } else {
                                when (event.key) {
                                    Key.VolumeUp -> onTapPrevious()
                                    Key.VolumeDown -> onTapNext()
                                }
                            }
                        }
                        else -> Unit
                    }
                    true
                } else {
                    false
                }
            }
            .background(settings.readerBackgroundColor()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (readAloudPanelVisible) readAloudPanelHeight else 0.dp),
        ) {
            // A dead renderer leaves the WebView unusable; a new generation replaces it, and the book reopens.
            key(engineGeneration) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            if (readAloudPanelVisible) {
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                            } else {
                                WindowInsets.safeDrawing
                            },
                        )
                        .onGloballyPositioned { webViewBounds = it.boundsInRoot() },
                factory = { context ->
                    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
                    var downX = 0f
                    var downY = 0f
                    var downTime = 0L
                    val webView = ReaderWebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        overScrollMode = View.OVER_SCROLL_NEVER
                        isHorizontalScrollBarEnabled = false
                        isVerticalScrollBarEnabled = false
                        setOnKeyListener { _, keyCode, event ->
                            onHardwarePageKeyState.value(keyCode, event.action, event.eventTime - event.downTime)
                        }
                        var swipeEdge: ReaderEdge? = null
                        var swiping = false
                        // A second finger makes the gesture a pinch (PDF zoom), never a tap on the page.
                        var multiTouch = false
                        setOnTouchListener { view, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    downX = event.x
                                    downY = event.y
                                    downTime = event.eventTime
                                    swiping = false
                                    multiTouch = false
                                    swipeEdge = when {
                                        !edgeSwipeEnabledState.value -> null
                                        event.x < view.width * EdgeSwipeZoneFraction -> ReaderEdge.LEFT
                                        event.x > view.width * (1 - EdgeSwipeZoneFraction) -> ReaderEdge.RIGHT
                                        else -> null
                                    }
                                }
                                MotionEvent.ACTION_POINTER_DOWN -> multiTouch = true
                                MotionEvent.ACTION_MOVE -> {
                                    val edge = swipeEdge
                                    val dy = downY - event.y
                                    if (edge != null && !swiping && !multiTouch && abs(dy) > touchSlop * 2 && abs(dy) > abs(event.x - downX) * 2) {
                                        swiping = true
                                        // The page mustn't also turn this gesture into a long-press selection.
                                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                                        view.onTouchEvent(cancel)
                                        cancel.recycle()
                                    }
                                    if (swiping && edge != null) {
                                        onEdgeSwipeState.value(edge, dy / view.height.coerceAtLeast(1), false)
                                        return@setOnTouchListener true
                                    }
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    val edge = swipeEdge
                                    if (swiping && edge != null) {
                                        swiping = false
                                        onEdgeSwipeState.value(edge, (downY - event.y) / view.height.coerceAtLeast(1), true)
                                        return@setOnTouchListener true
                                    }
                                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                                        onReaderInteractionState.value()
                                        val isShortTap = event.eventTime - downTime < ViewConfiguration.getLongPressTimeout()
                                        if (!multiTouch && isShortTap && abs(event.x - downX) <= touchSlop && abs(event.y - downY) <= touchSlop) {
                                            val tapX = event.x
                                            val tapWidth = view.width
                                            view.postDelayed(
                                                { onReaderTapState.value(tapX, tapWidth) },
                                                AnnotationTapArbitrationMillis,
                                            )
                                        }
                                    }
                                }
                            }
                            false
                        }
                    }
                    webViewRef = webView
                    val engine = FoliateBookEngine(webView, context.applicationContext)
                    onEngineReadyState.value(engine)
                    FrameLayout(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        tag = engine
                        addView(webView)
                    }
                },
                onRelease = { container ->
                    (container.tag as? BookEngine)?.let { engine ->
                        onEngineReleasedState.value(engine)
                    }
                    if (webViewRef?.parent === container) {
                        webViewRef = null
                    }
                    container.tag = null
                    container.removeAllViews()
                },
            )
            }

        // Tints the page only; no pointer input, so taps and swipes still reach the book underneath.
        if (warmLightPercent > 0 && settings.displayProfile != DisplayProfile.E_INK) {
            Box(modifier = Modifier.fillMaxSize().background(Palette.WarmLight.copy(alpha = warmLightPercent / 100f)))
        }

        // The header gap is tuned to clear a portrait top cutout; landscape has none there, so cap it
        // to keep the chips in the page's top margin instead of pushing them down over the text.
        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val headerGap = settings.readerHeaderGapDp.dp.let { gap ->
            if (isLandscape) gap.coerceAtMost(readerLandscapeHeaderMaxGap) else gap
        }
        if (settings.readerShowHeaders && !chromeVisible) {
            ReaderClockHeader(
                modifier = Modifier.align(Alignment.TopCenter),
                isEink = isEink,
                pageKey = currentLocatorCfi,
                syncStatus = syncStatus,
                headerGap = headerGap,
            )
            ReaderSessionHeader(
                modifier = Modifier.align(Alignment.TopStart),
                activeReadingSessionSeconds = activeReadingSessionSeconds,
                headerGap = headerGap,
            )
        }

        if (uiState is ReaderUiState.Loaded && settings.readerShowHeaders && !chromeVisible) {
            ReaderTimeLeftHeader(
                modifier = Modifier.align(Alignment.TopEnd),
                locator = uiState.currentLocator,
                showBookTime = footerShowsBookTime,
                paceFactor = paceFactor,
                onToggle = { footerShowsBookTime = !footerShowsBookTime },
                headerGap = headerGap,
            )
        }

        if (uiState is ReaderUiState.Loaded && settings.readerShowFooter) {
            ReaderPageNumberFooter(
                modifier = Modifier.align(Alignment.BottomStart),
                locator = uiState.currentLocator,
                footerGap = settings.readerFooterGapDp.dp,
                onGoToPage = onGoToPage,
                onPreparePageJump = onPreparePageJump,
                onCancelPageJump = onCancelPageJump,
            )
            ReaderBookProgressFooter(
                modifier = Modifier.align(Alignment.BottomEnd),
                locator = uiState.currentLocator,
                footerGap = settings.readerFooterGapDp.dp,
                onLongPress = {
                    selectedPanel = ReaderPanel.CONTENTS
                    chromeVisible = true
                },
            )
        }

        if (shouldShowReaderSettingsFooterButton(
                readerLoaded = uiState is ReaderUiState.Loaded,
                chromeVisible = chromeVisible,
                selectionActive = (uiState as? ReaderUiState.Loaded)?.selection != null,
                highlightCardActive = activeHighlightCard != null,
                dictionaryActionsActive = dictionaryLookup.wordOrNull() != null,
            )
        ) {
            ReaderSettingsFooterButton(
                modifier = Modifier.align(Alignment.BottomCenter),
                footerGap = settings.readerFooterGapDp.dp,
                onClick = {
                    selectedPanel = ReaderPanel.STYLE
                    chromeVisible = true
                },
            )
        }

        if (uiState is ReaderUiState.Loading) {
            VayanaCircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        if (uiState is ReaderUiState.Failed) {
            Text(
                text = uiState.message.asString(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(Paddings.screenHorizontal),
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.BottomStart),
            enter = vayanaSlideInVertically(initialOffsetY = { it }),
            exit = vayanaSlideOutVertically(targetOffsetY = { it }),
        ) {
            ReaderChrome(
                uiState = uiState,
                settings = settings,
                usingCustomStyle = usingCustomStyle,
                onUseCustomStyleChange = onUseCustomStyleChange,
                onSaveJournal = onSaveJournal,
                selectedPanel = selectedPanel,
                onPanelSelected = { selectedPanel = it },
                onBack = { chromeVisible = false },
                onOpenTocEntry = {
                    chromeVisible = false
                    onOpenTocEntry(it)
                },
                onProgressChange = onProgressChange,
                onAnnotationClick = {
                    chromeVisible = false
                    onAnnotationClick(it)
                },
                onReturnToPreviousPosition = {
                    chromeVisible = false
                    onReturnToPreviousPosition()
                },
                onFontSizeChange = onFontSizeChange,
                onLineHeightChange = onLineHeightChange,
                onFontFamilyChange = onFontFamilyChange,
                onTextAlignChange = onTextAlignChange,
                onCustomFontChange = onCustomFontChange,
                onReaderThemeChange = onReaderThemeChange,
                onSideMarginChange = onSideMarginChange,
                onVolumeKeysChange = onVolumeKeysChange,
                onKeepAwakeChange = onKeepAwakeChange,
                onShowHeadersChange = onShowHeadersChange,
                onShowFooterChange = onShowFooterChange,
                onOverridePublisherTypographyChange = onOverridePublisherTypographyChange,
                onBionicReadingChange = onBionicReadingChange,
                pdfPageControls = pdfPageControls,
                onReadAloudRateChange = onReadAloudRateChange,
                onReadAloudPitchChange = onReadAloudPitchChange,
                readAloudVoiceControls = readAloudVoiceControls,
                onCreateBookmark = onCreateBookmark,
                onRefreshScreen = { einkFlashTrigger++ },
                searchResults = searchResults,
                onSearchQueryChange = onSearchQueryChange,
                onSearchResultClick = {
                    chromeVisible = false
                    onSearchResultClick(it)
                },
                onClearSearch = onClearSearch,
                onStartReadAloud = {
                    chromeVisible = false
                    onStartReadAloud()
                },
                chapterWords = chapterWords,
                onLoadChapterWords = onLoadChapterWords,
                onChapterWordClick = onLookupWord,
                onSaveChapterWord = onSaveChapterWord,
            )
        }

        liveLight?.takeIf { lightSwiping }?.let { (edge, _) ->
            LightLevelIndicator(
                modifier = Modifier.align(Alignment.Center),
                text = when (edge) {
                    ReaderEdge.LEFT -> if (brightnessPercent > 0) {
                        stringResource(R.string.reader_light_brightness, brightnessPercent)
                    } else {
                        stringResource(R.string.reader_light_brightness_system)
                    }
                    ReaderEdge.RIGHT -> stringResource(R.string.reader_light_warmth, warmLightPercent)
                },
            )
        }

        val recapState = uiState as? ReaderUiState.Loaded
        if (returnRecap != null && recapState != null && !chromeVisible) {
            LaunchedEffect(returnRecap) {
                delay(ReturnRecapVisibleMillis)
                onDismissReturnRecap()
            }
            ReturnRecapCard(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = headerGap),
                recap = returnRecap,
                chapterTitle = recapState.currentLocator?.chapterTitle,
                onDismiss = onDismissReturnRecap,
                onReviewWords = {
                    onDismissReturnRecap()
                    onReviewVocabulary()
                },
            )
        }

        if (einkFlashVisible) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }

        val loadedState = uiState as? ReaderUiState.Loaded
        val selection = loadedState?.selection
        val highlightCard = loadedState?.highlightCard
        val highlightedAnnotation = highlightCard?.let { card ->
            loadedState.annotations.firstOrNull { it.id == card.annotationId && it.type == AnnotationType.HIGHLIGHT }
        }
        val dictionaryWord = dictionaryLookup.wordOrNull()
        // The card sits just above the selection (below it when there is no room above); a word looked up from
        // elsewhere has no selection to sit beside, so it goes at the bottom. It keeps its last spot while fading out.
        val selectionAnchor = selection?.anchorIn(webViewBounds)
        val highlightAnchor = highlightCard?.let { card ->
            val bounds = webViewBounds ?: return@let null
            val top = card.top ?: return@let null
            val bottom = card.bottom ?: return@let null
            SelectionAnchor(
                top = bounds.top + top * bounds.height,
                bottom = bounds.top + bottom * bounds.height,
            )
        }
        val actionAnchor = selectionAnchor ?: highlightAnchor
        val lastSelectionAnchor = remember { mutableStateOf<SelectionAnchor?>(null) }
        LaunchedEffect(actionAnchor) { if (actionAnchor != null) lastSelectionAnchor.value = actionAnchor }
        AnchoredToSelection(
            anchor = when {
                selection != null || highlightedAnnotation != null -> actionAnchor
                dictionaryWord != null -> null
                else -> lastSelectionAnchor.value
            },
            reservedBottomPx = if (readAloudPanelVisible) readAloudPanelHeightPx else 0,
            modifier = Modifier.fillMaxSize(),
        ) {
        AnimatedVisibility(
            visible = selection != null || dictionaryWord != null || highlightedAnnotation != null,
            enter = vayanaScaleIn() + vayanaFadeIn(),
            exit = vayanaScaleOut() + vayanaFadeOut(),
        ) {
            if (highlightedAnnotation != null && selection == null) {
                HighlightActions(
                    currentColorKey = highlightedAnnotation.colorKey,
                    readAloudAvailable = audioFeaturesEnabled,
                    onHighlight = onUpdateHighlightColor,
                    onDelete = onDeleteHighlight,
                    onUnderline = onConvertHighlightToUnderline,
                    onCopy = {
                        context.copyTextToClipboard(
                            QuoteCitation.format(
                                text = highlightedAnnotation.selectedText,
                                author = loadedState.bookAuthor,
                                bookTitle = loadedState.bookTitle,
                                chapterTitle = highlightedAnnotation.chapterTitle,
                                pattern = citationPattern,
                            ),
                        )
                        onDismissHighlight()
                    },
                    onNote = {
                        noteHighlight = highlightedAnnotation
                        noteDialogVisible = true
                    },
                    onShare = {
                        sharingSelection = SelectionShare(
                            text = highlightedAnnotation.selectedText,
                            chapterTitle = highlightedAnnotation.chapterTitle,
                            author = loadedState.bookAuthor,
                            bookTitle = loadedState.bookTitle,
                            coverPath = loadedState.bookCoverPath,
                            series = loadedState.bookSeries,
                            seriesNumber = loadedState.bookSeriesNumber,
                        )
                        onDismissHighlight()
                    },
                    onTranslate = {
                        if (!context.translateText(highlightedAnnotation.selectedText)) {
                            Toast.makeText(context, R.string.reader_translate_unavailable, Toast.LENGTH_LONG).show()
                        }
                    },
                    onReadAloud = onReadAloudFromHighlight,
                )
            } else if (selection != null || dictionaryWord != null) {
                SelectionActions(
                    selectionActionsEnabled = selection != null,
                    readAloudAvailable = audioFeaturesEnabled,
                    onReadAloud = onReadAloudFromSelection,
                    dictionaryLookup = dictionaryLookup,
                    recentLookups = recentLookups,
                    onHighlight = onCreateHighlight,
                    onUnderline = onCreateUnderline,
                    onCopy = {
                        val citation = QuoteCitation.format(
                            text = selection?.selectedText.orEmpty(),
                            author = loadedState?.bookAuthor,
                            bookTitle = loadedState?.bookTitle,
                            chapterTitle = selection?.chapterTitle,
                            pattern = citationPattern,
                        )
                        context.copyTextToClipboard(citation)
                        onClearSelection()
                    },
                    onNote = {
                        noteHighlight = null
                        noteDialogVisible = true
                    },
                    onShare = {
                        sharingSelection = SelectionShare(
                            text = selection?.selectedText.orEmpty(),
                            chapterTitle = selection?.chapterTitle,
                            author = loadedState?.bookAuthor,
                            bookTitle = loadedState?.bookTitle,
                            coverPath = loadedState?.bookCoverPath,
                            series = loadedState?.bookSeries,
                            seriesNumber = loadedState?.bookSeriesNumber,
                        )
                        onClearSelection()
                    },
                    onTranslate = {
                        if (!context.translateText(selection?.selectedText.orEmpty())) {
                            Toast.makeText(context, R.string.reader_translate_unavailable, Toast.LENGTH_LONG).show()
                        }
                    },
                    onDownloadDictionary = onDownloadDictionary,
                    onInstallDictionary = onInstallDictionary,
                    onLookupWord = onLookupWord,
                    onLookupOnline = onLookupOnline,
                    onSaveLookupAsNote = onSaveLookupAsNote,
                    onSaveLookupAsVocabulary = onSaveLookupAsVocabulary,
                )
            }
        }
        }
        }

        if (readAloudPanelVisible) {
            ReadAloudBar(
                state = readAloud,
                onTogglePlayback = onToggleReadAloud,
                onRateChange = onReadAloudRateChange,
                onPitchChange = onReadAloudPitchChange,
                onCycleSleepTimer = onCycleReadAloudSleepTimer,
                onStop = onStopReadAloud,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { readAloudPanelHeightPx = it.height },
            )
        }
    }

    if (noteDialogVisible) {
        NoteDialog(
            initialNote = noteHighlight?.readerNote.orEmpty(),
            onDismiss = {
                noteDialogVisible = false
                noteHighlight = null
            },
            onConfirm = { note ->
                noteDialogVisible = false
                val highlight = noteHighlight
                noteHighlight = null
                if (highlight != null) {
                    onUpdateHighlightNote(highlight, note)
                    onDismissHighlight()
                } else {
                    onCreateNote(note)
                }
            },
        )
    }

    sharingSelection?.let { share ->
        QuoteShareDialog(
            text = share.text,
            author = share.author,
            bookTitle = share.bookTitle,
            chapterTitle = share.chapterTitle,
            onDismiss = { sharingSelection = null },
            coverPath = share.coverPath,
            series = share.series,
            seriesNumber = share.seriesNumber,
        )
    }

    if (readingPositionPrompt != null) {
        ReadingPositionPromptDialog(
            prompt = readingPositionPrompt,
            onGoToRecentLocation = onAcceptReadingPositionPrompt,
            onStayHere = onDismissReadingPositionPrompt,
        )
    }

    footnote?.let {
        FootnoteDialog(footnote = it, onDismiss = onDismissFootnote, onGoToNote = onOpenFootnote)
    }

    if (readAloud.voiceMissing) {
        ReadAloudVoiceMissingDialog(
            onDismiss = onDismissReadAloudVoiceMissing,
            onOpenSettings = {
                onDismissReadAloudVoiceMissing()
                runCatching { context.startActivity(Intent(TtsSettingsAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
        )
    }
}

private const val AnnotationTapArbitrationMillis = 120L

@Composable
private fun ReaderClockHeader(
    modifier: Modifier = Modifier,
    isEink: Boolean,
    /** Changes with every page turn; on E-Ink the clock only catches up then. */
    pageKey: Any?,
    syncStatus: ReaderSyncStatus,
    headerGap: Dp = readerHeaderTopPadding,
) {
    // The clock ticks here rather than in the reader: its state lives in this small header, so each tick recomposes
    // the chip alone instead of the whole reader.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isEink) {
        // A clock that ticks on its own is a partial panel refresh in the middle of a page nobody turned: on E-Ink it
        // only catches up when the page changes (below).
        if (isEink) return@LaunchedEffect
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(30_000)
        }
    }
    LaunchedEffect(pageKey) {
        if (isEink) nowMillis = System.currentTimeMillis()
    }
    val clockText = remember(nowMillis) { DateFormat.format("hh:mm a", nowMillis).toString() }
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .windowInsetsPadding(readerHudHorizontalInsets)
            .padding(top = headerGap),
        color = readerHudSurfaceColor(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = readerHudElevation(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        ) {
            ReaderSyncStatusDot(syncStatus = syncStatus)
            Text(text = clockText, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * The reader's whole sync UI: one dot beside the clock.
 *
 * On a colour display the three states are hues - syncing red, settled green, needs-attention amber. The E-Ink
 * profile has no hue to spend and renders greys too close together to tell apart at this size, so it separates
 * the same three states by *fill* against the single ink colour: an empty ring while a sync is in flight, a
 * solid dot once it lands, and a ring around a centre dot when it needs attention. [ReaderSyncStatus.Idle]
 * draws nothing at all, keeping the header clean when sync is switched off.
 */
@Composable
private fun ReaderSyncStatusDot(syncStatus: ReaderSyncStatus, modifier: Modifier = Modifier) {
    if (syncStatus is ReaderSyncStatus.Idle) return
    val isEink = LocalDisplayProfile.current == DisplayProfile.E_INK
    val description = stringResource(
        when (syncStatus) {
            ReaderSyncStatus.Syncing -> R.string.reader_sync_dot_syncing
            ReaderSyncStatus.Synced -> R.string.reader_sync_dot_synced
            else -> R.string.reader_sync_dot_failed
        },
    )
    val ink = MaterialTheme.colorScheme.onSurface
    val color = when {
        LocalDisplayProfile.current.isMonochrome(LocalEinkPalette.current) -> ink
        syncStatus is ReaderSyncStatus.Syncing -> Palette.SyncActive
        syncStatus is ReaderSyncStatus.Synced -> Palette.SyncSettled
        else -> Palette.SyncWarning
    }
    Canvas(
        modifier = modifier
            .size(if (isEink) ReaderSyncDotEinkSize else ReaderSyncDotSize)
            .semantics { contentDescription = description },
    ) {
        val radius = size.minDimension / 2f
        val stroke = size.minDimension * ReaderSyncDotStrokeFraction
        when {
            // Colour displays carry the state in the hue, so every state is the same solid dot.
            !isEink -> drawCircle(color = color, radius = radius)
            syncStatus is ReaderSyncStatus.Syncing ->
                drawCircle(color = color, radius = radius - stroke / 2f, style = Stroke(width = stroke))
            syncStatus is ReaderSyncStatus.Synced -> drawCircle(color = color, radius = radius)
            else -> {
                drawCircle(color = color, radius = radius - stroke / 2f, style = Stroke(width = stroke))
                drawCircle(color = color, radius = radius * ReaderSyncDotCoreFraction)
            }
        }
    }
}

@Composable
private fun ReaderSessionHeader(
    modifier: Modifier = Modifier,
    activeReadingSessionSeconds: Long,
    headerGap: Dp = readerHeaderTopPadding,
) {
    val sessionMinutes = (activeReadingSessionSeconds / 60L).coerceAtLeast(0L).toInt()
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .windowInsetsPadding(readerHudHorizontalInsets)
            .padding(start = readerHeaderHorizontalPadding, top = headerGap),
        color = readerHudSurfaceColor(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = readerHudElevation(),
    ) {
        Text(
            text = stringResource(R.string.reader_header_session_minutes, sessionMinutes),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun ReaderTimeLeftHeader(
    modifier: Modifier = Modifier,
    locator: Locator?,
    showBookTime: Boolean,
    paceFactor: Float,
    onToggle: () -> Unit,
    headerGap: Dp = readerHeaderTopPadding,
) {
    // The engine's estimate assumes one fixed reading speed; the factor is how this reader's own pace compares.
    val minutesLeft = (if (showBookTime) locator?.bookMinutesLeft else locator?.chapterMinutesLeft)
        ?.let { ReadingPace.scaled(it, paceFactor) }
    if (minutesLeft == null) return
    val labelRes = if (showBookTime) R.string.reader_footer_time_left_book else R.string.reader_footer_time_left_chapter
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .windowInsetsPadding(readerHudHorizontalInsets)
            .padding(end = readerHeaderHorizontalPadding, top = headerGap)
            .clickable(onClick = onToggle),
        color = readerHudSurfaceColor(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = readerHudElevation(),
    ) {
        Text(
            text = stringResource(labelRes, formatMinutes(minutesLeft)),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun ReaderPageNumberFooter(
    modifier: Modifier = Modifier,
    locator: Locator?,
    footerGap: Dp = Spacing.sm,
    onGoToPage: (Int) -> Unit,
    onPreparePageJump: (() -> Unit) -> Unit,
    onCancelPageJump: () -> Unit,
) {
    val currentPage = locator?.currentPage
    val totalPages = locator?.totalPages
    if (currentPage == null || totalPages == null) return
    var showPageDialog by remember { mutableStateOf(false) }
    var dialogTotalPages by remember { mutableStateOf(totalPages) }
    if (showPageDialog) {
        GoToPageDialog(totalPages = dialogTotalPages, onDismiss = {
            showPageDialog = false
            onCancelPageJump()
        }, onGo = {
            showPageDialog = false
            onGoToPage(it)
        })
    }
    ReaderFooterPill(
        modifier = modifier,
        footerGap = footerGap,
        horizontalTouchPadding = Spacing.md,
        onClick = {
            dialogTotalPages = totalPages
            onPreparePageJump { showPageDialog = true }
        },
    ) {
        Text(
            text = stringResource(R.string.reader_progress_page_of, currentPage, totalPages),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun GoToPageDialog(totalPages: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val page = input.toIntOrNull()?.takeIf { it in 1..totalPages }
    val submit = { page?.let(onGo); Unit }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_go_to_page_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { value -> if (value.all { it in '0'..'9' } && value.length <= 10) input = value },
                label = { Text(stringResource(R.string.reader_go_to_page_label)) },
                supportingText = { Text(stringResource(R.string.reader_go_to_page_range, totalPages)) },
                isError = input.isNotEmpty() && page == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = page != null) { Text(stringResource(R.string.reader_pdf_page_jump_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_go_to_page_cancel)) } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderBookProgressFooter(
    modifier: Modifier = Modifier,
    locator: Locator?,
    footerGap: Dp = Spacing.sm,
    onLongPress: () -> Unit = {},
) {
    val progress = locator?.progression ?: return
    ReaderFooterPill(
        modifier = modifier.padding(end = Spacing.md),
        footerGap = footerGap,
        onLongPress = onLongPress,
    ) {
        Text(
            text = stringResource(R.string.reader_progress_percent, (progress * 100).roundToInt()),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ReaderSettingsFooterButton(
    modifier: Modifier = Modifier,
    footerGap: Dp = Spacing.sm,
    onClick: () -> Unit,
) {
    ReaderFooterPill(
        modifier = modifier,
        footerGap = footerGap,
        onClick = onClick,
        color = if (LocalDisplayProfile.current == DisplayProfile.E_INK) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = ReaderHudStandardAlpha)
        },
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.reader_footer_open_settings), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderFooterPill(
    modifier: Modifier,
    footerGap: Dp,
    horizontalTouchPadding: Dp = 0.dp,
    onClick: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    color: Color = readerHudSurfaceColor(),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .windowInsetsPadding(readerHudHorizontalInsets)
            .padding(bottom = footerGap)
            .heightIn(min = 48.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = horizontalTouchPadding),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.heightIn(min = 28.dp),
            color = color,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = readerHudElevation(),
        ) {
            Box(Modifier.padding(horizontal = Spacing.md), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

@Composable
private fun readerHudSurfaceColor(): Color =
    if (LocalDisplayProfile.current == DisplayProfile.E_INK) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = ReaderHudStandardAlpha)
    }

@Composable
private fun readerHudElevation() =
    if (LocalDisplayProfile.current == DisplayProfile.E_INK) Elevations.none else Spacing.xs

@Composable
/** Raised over the page by surface tier rather than a shadow (design spec). */
private fun readerChromeSurfaceColor(): Color =
    if (LocalDisplayProfile.current == DisplayProfile.E_INK) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }

@Composable
private fun readerChromeTopBorderColor(settings: SettingsSnapshot, chromeColor: Color): Color? {
    if (LocalDisplayProfile.current != DisplayProfile.E_INK) return null
    val pageColor = settings.readerBackgroundColor()
    if (!pageColor.isSameIshAs(chromeColor)) return null
    return if ((pageColor.luminance() + chromeColor.luminance()) / 2f > 0.5f) Palette.Amoled else Palette.White
}

@Composable
private fun readerChromeHandleColor(): Color =
    if (LocalDisplayProfile.current == DisplayProfile.E_INK) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
}

/** Keeps WebView's selection handles while Vayana supplies the selection actions and dictionary UI. */
private class ReaderWebView(context: Context) : WebView(context) {
    override fun startActionMode(callback: ActionMode.Callback): ActionMode? =
        super.startActionMode(callback.withoutMenu())

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? =
        super.startActionMode(callback.withoutMenu(), type)

    private fun ActionMode.Callback.withoutMenu(): ActionMode.Callback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val created = this@withoutMenu.onCreateActionMode(mode, menu)
            menu.clear()
            return created
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val prepared = this@withoutMenu.onPrepareActionMode(mode, menu)
            menu.clear()
            return prepared
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
            this@withoutMenu.onActionItemClicked(mode, item)

        override fun onDestroyActionMode(mode: ActionMode) = this@withoutMenu.onDestroyActionMode(mode)
    }
}

@Composable
private fun formatMinutes(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.reader_duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.reader_duration_minutes, minutes)
    }
}

@Composable
private fun NoteDialog(initialNote: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var note by remember(initialNote) { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_note_dialog_title)) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.reader_note_dialog_label)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note) }) {
                Text(stringResource(R.string.reader_note_dialog_save))
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
private fun ReadingPositionPromptDialog(
    prompt: ReadingPositionPrompt,
    onGoToRecentLocation: () -> Unit,
    onStayHere: () -> Unit,
) {
    val recentLocation = prompt.targetPage?.let { page ->
        stringResource(R.string.reader_reading_position_page, page)
    } ?: stringResource(R.string.reader_progress_percent, (prompt.targetProgress * 100).roundToInt())
    val currentLocation = prompt.currentPage?.let { page ->
        stringResource(R.string.reader_reading_position_page, page)
    } ?: stringResource(R.string.reader_progress_percent, (prompt.currentProgress * 100).roundToInt())
    val syncStatus = cloudSyncStatusText(prompt.syncedAt, prompt.syncedDeviceLabel)

    AlertDialog(
        onDismissRequest = onStayHere,
        title = { Text(stringResource(R.string.reader_reading_position_prompt_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(
                        R.string.reader_reading_position_prompt_body,
                        recentLocation,
                        currentLocation,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = syncStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onGoToRecentLocation) {
                Text(stringResource(R.string.reader_reading_position_prompt_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onStayHere) {
                Text(stringResource(R.string.reader_reading_position_prompt_stay))
            }
        },
    )
}

@Composable
private fun ReaderChrome(
    modifier: Modifier = Modifier,
    uiState: ReaderUiState,
    settings: SettingsSnapshot,
    usingCustomStyle: Boolean,
    onUseCustomStyleChange: (Boolean) -> Unit,
    onSaveJournal: suspend (String) -> Unit,
    selectedPanel: ReaderPanel,
    onPanelSelected: (ReaderPanel) -> Unit,
    onBack: () -> Unit,
    onOpenTocEntry: (String) -> Unit,
    onProgressChange: (Float) -> Unit,
    onAnnotationClick: (Annotation) -> Unit,
    onReturnToPreviousPosition: () -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onFontFamilyChange: (ReaderFontFamily) -> Unit,
    onTextAlignChange: (ReaderTextAlign) -> Unit,
    onCustomFontChange: (String?) -> Unit,
    onReaderThemeChange: (ReaderTheme) -> Unit,
    onSideMarginChange: (Int) -> Unit,
    onVolumeKeysChange: (Boolean) -> Unit,
    onKeepAwakeChange: (Boolean) -> Unit,
    onShowHeadersChange: (Boolean) -> Unit,
    onShowFooterChange: (Boolean) -> Unit,
    onOverridePublisherTypographyChange: (Boolean) -> Unit,
    onBionicReadingChange: (Boolean) -> Unit,
    pdfPageControls: PdfPageControls,
    onReadAloudRateChange: (Float) -> Unit,
    onReadAloudPitchChange: (Float) -> Unit,
    readAloudVoiceControls: ReadAloudVoiceControls,
    onCreateBookmark: () -> Unit,
    onRefreshScreen: () -> Unit,
    searchResults: List<com.vayana.reader.api.SearchResult>,
    onSearchQueryChange: (String) -> Unit,
    onSearchResultClick: (com.vayana.reader.api.SearchResult) -> Unit,
    onClearSearch: () -> Unit,
    onStartReadAloud: () -> Unit,
    chapterWords: ChapterWordsState,
    onLoadChapterWords: () -> Unit,
    onChapterWordClick: (String) -> Unit,
    onSaveChapterWord: (ChapterWord) -> Unit,
) {
    var showJournal by remember { mutableStateOf(false) }
    if (showJournal) ReadingJournalDialog(onDismiss = { showJournal = false }, onSave = onSaveJournal)

    // PDF pages have no reflowable text to restyle: the style panel offers page fit and crop instead.
    val fixedLayout = (uiState as? ReaderUiState.Loaded)?.fixedLayout == true
    val chromeSurfaceColor = readerChromeSurfaceColor()
    val chromeTopBorderColor = readerChromeTopBorderColor(settings, chromeSurfaceColor)
    val surfaceModifier = modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .then(
            if (chromeTopBorderColor != null) {
                Modifier.drawBehind {
                    drawLine(
                        color = chromeTopBorderColor,
                        start = Offset.Zero,
                        end = Offset(size.width, 0f),
                        strokeWidth = Strokes.hairline.toPx(),
                    )
                }
            } else {
                Modifier
            },
        )

    Surface(
        modifier = surfaceModifier,
        color = chromeSurfaceColor,
        shape = RoundedCornerShape(topStart = Radii.extraLarge, topEnd = Radii.extraLarge),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Paddings.screenHorizontal)
                .padding(top = Spacing.sm, bottom = Spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = Sizes.touchTarget, height = Spacing.xs)
                    .background(
                        color = readerChromeHandleColor(),
                        shape = CircleShape,
                    ),
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = null,
                    )
                }
                Text(
                    text = (uiState as? ReaderUiState.Loaded)?.bookTitle.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = Sizes.touchTarget),
                )
                Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                    if ((uiState as? ReaderUiState.Loaded)?.returnLocator != null) {
                        IconButton(onClick = onReturnToPreviousPosition) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Undo,
                                contentDescription = stringResource(R.string.reader_return_to_previous_position_content_description),
                            )
                        }
                    }
                    if (settings.displayProfile == DisplayProfile.E_INK) {
                        IconButton(onClick = onRefreshScreen) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.reader_refresh_screen_content_description),
                            )
                        }
                    }
                    IconButton(onClick = { onPanelSelected(ReaderPanel.WORDS) }) {
                        Icon(
                            imageVector = Icons.Outlined.Translate,
                            contentDescription = stringResource(R.string.reader_words_content_description),
                        )
                    }
                    IconButton(onClick = onCreateBookmark) {
                        Icon(
                            imageVector = Icons.Outlined.BookmarkAdd,
                            contentDescription = stringResource(R.string.reader_add_bookmark_content_description),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ReaderPanelButton(Icons.Outlined.Article, R.string.reader_contents, selectedPanel == ReaderPanel.CONTENTS) {
                    onPanelSelected(ReaderPanel.CONTENTS)
                }
                ReaderPanelButton(Icons.Outlined.BookmarkAdd, R.string.notes_filter_bookmarks, selectedPanel == ReaderPanel.BOOKMARKS) {
                    onPanelSelected(ReaderPanel.BOOKMARKS)
                }
                ReaderPanelButton(Icons.Outlined.EditNote, R.string.reader_notes, selectedPanel == ReaderPanel.NOTES) {
                    onPanelSelected(ReaderPanel.NOTES)
                }
                ReaderPanelButton(Icons.Outlined.Tune, R.string.reader_progress, selectedPanel == ReaderPanel.PROGRESS) {
                    onPanelSelected(ReaderPanel.PROGRESS)
                }
                ReaderPanelButton(Icons.Outlined.TextFields, R.string.reader_style, selectedPanel == ReaderPanel.STYLE) {
                    onPanelSelected(ReaderPanel.STYLE)
                }
                if (settings.readerAudioFeaturesEnabled) {
                    ReaderPanelButton(
                        Icons.Outlined.Headphones,
                        R.string.settings_read_aloud_section_title,
                        selectedPanel == ReaderPanel.READ_ALOUD,
                    ) {
                        onPanelSelected(ReaderPanel.READ_ALOUD)
                    }
                }
                ReaderPanelButton(Icons.Outlined.Search, R.string.reader_search, selectedPanel == ReaderPanel.SEARCH) {
                    onPanelSelected(ReaderPanel.SEARCH)
                }
            }
            AnimatedContent(
                targetState = selectedPanel,
                transitionSpec = vayanaContentTransform(),
                label = "ReaderPanelSwitch",
            ) { panel ->
                when (panel) {
                    ReaderPanel.CONTENTS -> ContentsPanel(uiState = uiState, onOpenTocEntry = onOpenTocEntry)
                    ReaderPanel.BOOKMARKS -> BookmarksPanel(
                        uiState = uiState,
                        onCreateBookmark = onCreateBookmark,
                        onBookmarkClick = onAnnotationClick,
                    )
                    ReaderPanel.PROGRESS -> ProgressPanel(
                        uiState = uiState,
                        onProgressChange = onProgressChange,
                        onBrowsePages = pdfPageControls.onBrowsePages.takeIf { fixedLayout },
                    )
                    ReaderPanel.STYLE -> StylePanel(
                        settings = settings,
                        showTypography = !fixedLayout,
                        usingCustomStyle = usingCustomStyle,
                        onUseCustomStyleChange = onUseCustomStyleChange,
                        onFontSizeChange = onFontSizeChange,
                        onLineHeightChange = onLineHeightChange,
                        onFontFamilyChange = onFontFamilyChange,
                        onTextAlignChange = onTextAlignChange,
                        onCustomFontChange = onCustomFontChange,
                        onReaderThemeChange = onReaderThemeChange,
                        onSideMarginChange = onSideMarginChange,
                        onVolumeKeysChange = onVolumeKeysChange,
                        onKeepAwakeChange = onKeepAwakeChange,
                        onShowHeadersChange = onShowHeadersChange,
                        onShowFooterChange = onShowFooterChange,
                        onOverridePublisherTypographyChange = onOverridePublisherTypographyChange,
                        onBionicReadingChange = onBionicReadingChange,
                        pdfPageControls = pdfPageControls,
                    )
                    ReaderPanel.READ_ALOUD -> ReadAloudSettingsPage(
                        settings = settings,
                        controls = readAloudVoiceControls,
                        onStartReading = onStartReadAloud,
                        onRateChange = onReadAloudRateChange,
                        onPitchChange = onReadAloudPitchChange,
                    )
                    ReaderPanel.NOTES -> NotesPanel(uiState = uiState, onAnnotationClick = onAnnotationClick, onJournal = { showJournal = true })
                    ReaderPanel.SEARCH -> SearchPanel(
                        results = searchResults,
                        onQueryChange = onSearchQueryChange,
                        onResultClick = onSearchResultClick,
                        onClear = onClearSearch,
                    )
                    ReaderPanel.WORDS -> WordsPanel(
                        state = chapterWords,
                        onLoad = onLoadChapterWords,
                        onWordClick = onChapterWordClick,
                        onSaveWord = onSaveChapterWord,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderPanelButton(icon: ImageVector, labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    val isEink = LocalDisplayProfile.current == DisplayProfile.E_INK
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = when {
            isEink && selected -> MaterialTheme.colorScheme.inverseSurface
            isEink -> MaterialTheme.colorScheme.surface
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = when {
            isEink && selected -> MaterialTheme.colorScheme.inverseOnSurface
            isEink -> MaterialTheme.colorScheme.onSurface
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        tonalElevation = if (selected && !isEink) Elevations.shadowSmall else Elevations.none,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(Sizes.touchTarget)) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(labelRes),
                modifier = Modifier.padding(Spacing.xs),
            )
        }
    }
}

@Composable
private fun ContentsPanel(uiState: ReaderUiState, onOpenTocEntry: (String) -> Unit) {
    val loaded = uiState as? ReaderUiState.Loaded
    val toc = loaded?.toc.orEmpty()
    val entries = remember(toc) { toc.flattenToc() }
    val locator = loaded?.currentLocator
    val currentIndex = entries.indexOfFirst { it.entry.href == locator?.href }
    // Page numbers only once the engine has measured a layout, same as the page-number footer.
    val tocPages = if (locator?.totalPages != null) locator.tocPages else emptyMap()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentIndex - ContentsScrollContextRows).coerceAtLeast(0),
    )
    LazyColumn(state = listState, modifier = Modifier.heightIn(max = Sizes.contentMaxWidth)) {
        itemsIndexed(entries, key = { _, item -> "${item.depth}:${item.entry.href}:${item.entry.title}" }) { index, item ->
            ContentsEntryRow(
                item = item,
                isCurrent = index == currentIndex,
                pageNumber = tocPages[item.entry.href],
                onClick = { onOpenTocEntry(item.entry.href) },
            )
        }
    }
}

@Composable
private fun ContentsEntryRow(item: TocDisplayItem, isCurrent: Boolean, pageNumber: Int?, onClick: () -> Unit) {
    val isEink = LocalDisplayProfile.current == DisplayProfile.E_INK
    val containerColor = when {
        !isCurrent -> Color.Transparent
        isEink -> MaterialTheme.colorScheme.inverseSurface
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when {
        !isCurrent -> MaterialTheme.colorScheme.onSurface
        isEink -> MaterialTheme.colorScheme.inverseOnSurface
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .clip(MaterialTheme.shapes.medium)
            .background(containerColor)
            .clickable(onClick = onClick)
            .semantics { selected = isCurrent }
            .padding(start = Spacing.md + Spacing.md * item.depth, end = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = item.entry.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.SemiBold else null,
            color = contentColor,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = Spacing.sm),
        )
        pageNumber?.let { page ->
            Text(
                text = page.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrent) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BookmarksPanel(
    uiState: ReaderUiState,
    onCreateBookmark: () -> Unit,
    onBookmarkClick: (Annotation) -> Unit,
) {
    // The reader state changes on every page turn; only a change to the annotations should re-sort them.
    val annotations = (uiState as? ReaderUiState.Loaded)?.annotations.orEmpty()
    val bookmarks = remember(annotations) {
        annotations.filter { it.type == AnnotationType.BOOKMARK }.sortedByDescending { it.createdAt }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = Sizes.contentMaxWidth)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilledTonalButton(onClick = onCreateBookmark) {
            Text(stringResource(R.string.reader_bookmarks_add_current))
        }
        if (bookmarks.isEmpty()) {
            Text(
                text = stringResource(R.string.reader_bookmarks_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        LazyColumn(modifier = Modifier.heightIn(max = Sizes.contentMaxWidth)) {
            items(bookmarks, key = { it.id }) { bookmark ->
                TextButton(onClick = { onBookmarkClick(bookmark) }) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = bookmark.chapterTitle ?: stringResource(R.string.notes_bookmark_without_text),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.reader_bookmarks_location_saved),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressPanel(
    uiState: ReaderUiState,
    onProgressChange: (Float) -> Unit,
    onBrowsePages: (() -> Unit)? = null,
) {
    val locator = (uiState as? ReaderUiState.Loaded)?.currentLocator
    val progress = locator?.progression ?: 0f
    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Text(
            text = stringResource(R.string.reader_progress_percent, (progress * 100).roundToInt()),
            style = MaterialTheme.typography.titleMedium,
        )
        val currentPage = locator?.currentPage
        val totalPages = locator?.totalPages
        if (currentPage != null && totalPages != null) {
            Text(
                text = stringResource(R.string.reader_progress_page_of, currentPage, totalPages),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.reader_progress_pages_left, (totalPages - currentPage).coerceAtLeast(0)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Slider(value = progress, onValueChange = onProgressChange, valueRange = 0f..1f)
        onBrowsePages?.let { browse ->
            FilledTonalButton(onClick = browse) {
                Text(stringResource(R.string.reader_pdf_browse_pages))
            }
        }
    }
}

@Composable
private fun NotesPanel(uiState: ReaderUiState, onAnnotationClick: (Annotation) -> Unit, onJournal: () -> Unit) {
    val annotations = (uiState as? ReaderUiState.Loaded)?.annotations.orEmpty()
    LazyColumn(modifier = Modifier.heightIn(max = Sizes.contentMaxWidth)) {
        item { TextButton(onClick = onJournal) { Text(stringResource(R.string.tools_journal)) } }
        if (annotations.isEmpty()) item {
            Text(stringResource(R.string.reader_notes_empty), modifier = Modifier.padding(Spacing.lg))
        }
        items(annotations, key = { it.id }) { annotation ->
            TextButton(onClick = { onAnnotationClick(annotation) }) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = annotation.selectedText.ifBlank { annotation.readerNote.orEmpty().ifBlank { stringResource(R.string.notes_bookmark_without_text) } },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = annotation.chapterTitle ?: annotation.type.name.lowercase().replaceFirstChar { it.titlecase() },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Persistent notes list shown beside the reader on wide-landscape (tablet) screens, per the product specification's "book and notes side by side" recommendation. */
@Composable
private fun NotesSidePanel(
    modifier: Modifier = Modifier,
    uiState: ReaderUiState,
    onAnnotationClick: (Annotation) -> Unit,
    onEditNote: (Annotation, String) -> Unit,
) {
    val annotations = (uiState as? ReaderUiState.Loaded)?.annotations.orEmpty()
    var editingAnnotation by remember { mutableStateOf<Annotation?>(null) }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.reader_notes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(Spacing.lg),
            )
            if (annotations.isEmpty()) {
                Text(
                    text = stringResource(R.string.reader_notes_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(annotations, key = { it.id }) { annotation ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radii.medium))
                                .clickable { onAnnotationClick(annotation) },
                            shape = RoundedCornerShape(Radii.medium),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Column(modifier = Modifier.padding(Spacing.md)) {
                                if (annotation.selectedText.isNotBlank()) {
                                    Text(
                                        text = stringResource(R.string.quoted_text, annotation.selectedText),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                annotation.readerNote?.takeIf { it.isNotBlank() }?.let { note ->
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = Spacing.xs),
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = Spacing.xs),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = annotation.chapterTitle ?: annotation.type.name.lowercase().replaceFirstChar { it.titlecase() },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    IconButton(onClick = { editingAnnotation = annotation }) {
                                        Icon(
                                            imageVector = Icons.Outlined.EditNote,
                                            contentDescription = stringResource(R.string.notes_edit_content_description),
                                            modifier = Modifier.size(Sizes.iconSmall),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingAnnotation?.let { annotation ->
        var noteText by remember(annotation.id) { mutableStateOf(annotation.readerNote.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingAnnotation = null },
            title = { Text(stringResource(R.string.notes_edit_title)) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.notes_edit_label)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    onEditNote(annotation, noteText)
                    editingAnnotation = null
                }) { Text(stringResource(R.string.notes_edit_save)) }
            },
            dismissButton = {
                FilledTonalButton(onClick = { editingAnnotation = null }) {
                    Text(stringResource(R.string.settings_reset_all_cancel))
                }
            },
        )
    }
}

@Composable
private fun SearchPanel(
    results: List<com.vayana.reader.api.SearchResult>,
    onQueryChange: (String) -> Unit,
    onResultClick: (com.vayana.reader.api.SearchResult) -> Unit,
    onClear: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onQueryChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.reader_search_label)) },
            singleLine = true,
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = {
                        query = ""
                        onClear()
                    }) {
                        Icon(imageVector = Icons.Outlined.Close, contentDescription = stringResource(R.string.reader_search_clear))
                    }
                }
            } else {
                null
            },
        )
        when {
            query.isBlank() -> Unit
            results.isEmpty() -> Text(
                text = stringResource(R.string.reader_search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(modifier = Modifier.heightIn(max = Sizes.contentMaxWidth)) {
                items(results, key = { it.cfi }) { result ->
                    TextButton(onClick = { onResultClick(result) }) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = result.excerpt,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            result.chapterTitle?.let { chapter ->
                                Text(
                                    text = chapter,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StylePanel(
    settings: SettingsSnapshot,
    showTypography: Boolean,
    usingCustomStyle: Boolean,
    onUseCustomStyleChange: (Boolean) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onFontFamilyChange: (ReaderFontFamily) -> Unit,
    onTextAlignChange: (ReaderTextAlign) -> Unit,
    onCustomFontChange: (String?) -> Unit,
    onReaderThemeChange: (ReaderTheme) -> Unit,
    onSideMarginChange: (Int) -> Unit,
    onVolumeKeysChange: (Boolean) -> Unit,
    onKeepAwakeChange: (Boolean) -> Unit,
    onShowHeadersChange: (Boolean) -> Unit,
    onShowFooterChange: (Boolean) -> Unit,
    onOverridePublisherTypographyChange: (Boolean) -> Unit,
    onBionicReadingChange: (Boolean) -> Unit,
    pdfPageControls: PdfPageControls,
) {
    val fontSizeSetting = SettingsRegistry.ReaderFontSize
    val lineHeightSetting = SettingsRegistry.ReaderLineHeight
    val sideMarginSetting = SettingsRegistry.ReaderSideMargin
    var pendingFontSize by remember { mutableIntStateOf(settings.readerFontSizePercent) }
    var pendingLineHeight by remember { mutableFloatStateOf(settings.readerLineHeight) }
    var pendingSideMargin by remember { mutableIntStateOf(settings.readerSideMarginPercent) }

    LaunchedEffect(settings.readerFontSizePercent) {
        pendingFontSize = settings.readerFontSizePercent
    }
    LaunchedEffect(settings.readerLineHeight) {
        pendingLineHeight = settings.readerLineHeight
    }
    LaunchedEffect(settings.readerSideMarginPercent) {
        pendingSideMargin = settings.readerSideMarginPercent
    }
    Column(
        modifier = Modifier
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .heightIn(max = Sizes.contentMaxWidth)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.reader_use_custom_style_for_book),
                style = MaterialTheme.typography.labelLarge,
            )
            Switch(checked = usingCustomStyle, onCheckedChange = onUseCustomStyleChange)
        }


        Text(text = stringResource(R.string.settings_reader_theme_title), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ReaderTheme.entries.forEach { theme ->
                FilterChip(
                    selected = settings.readerTheme == theme,
                    onClick = { onReaderThemeChange(theme) },
                    label = { Text(theme.label()) },
                    leadingIcon = { ReaderThemeSwatch(theme) },
                )
            }
        }

        if (showTypography) {
            Text(stringResource(R.string.settings_reader_text_align_title), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(
                    Triple(ReaderTextAlign.LEFT, Icons.Outlined.FormatAlignLeft, R.string.settings_reader_text_align_left),
                    Triple(ReaderTextAlign.CENTER, Icons.Outlined.FormatAlignCenter, R.string.settings_reader_text_align_center),
                    Triple(ReaderTextAlign.RIGHT, Icons.Outlined.FormatAlignRight, R.string.settings_reader_text_align_right),
                    Triple(ReaderTextAlign.JUSTIFIED, Icons.Outlined.FormatAlignJustify, R.string.settings_reader_text_align_justified),
                ).forEach { (align, icon, label) ->
                    FilterChip(
                        selected = settings.readerTextAlign == align,
                        onClick = { onTextAlignChange(align) },
                        label = { Icon(icon, contentDescription = stringResource(label)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            ReaderStyleLabel(
                title = stringResource(R.string.settings_reader_font_size_title),
                value = "$pendingFontSize%",
            )
            Slider(
                value = pendingFontSize.toFloat(),
                onValueChange = { pendingFontSize = it.roundToStep(fontSizeSetting) },
                onValueChangeFinished = { onFontSizeChange(pendingFontSize) },
                valueRange = fontSizeSetting.sliderRange(),
                steps = fontSizeSetting.sliderSteps(),
            )
            ReaderSettingsSwitchRow(
                title = stringResource(R.string.reader_override_book_typography_title),
                subtitle = stringResource(R.string.reader_override_book_typography_subtitle),
                checked = !settings.readerUsePublisherStyles,
                onCheckedChange = onOverridePublisherTypographyChange,
            )
            ReaderStyleLabel(
                title = stringResource(R.string.settings_reader_line_height_title),
                value = "${pendingLineHeight.roundToTenth()}x",
            )
            Slider(
                value = pendingLineHeight,
                onValueChange = { pendingLineHeight = it.roundToStep(lineHeightSetting) },
                onValueChangeFinished = { onLineHeightChange(pendingLineHeight) },
                valueRange = lineHeightSetting.range,
                steps = lineHeightSetting.sliderSteps(),
            )
            ReaderStyleLabel(
                title = stringResource(R.string.settings_reader_side_margin_title),
                value = "$pendingSideMargin%",
            )
            Slider(
                value = pendingSideMargin.toFloat(),
                onValueChange = { pendingSideMargin = it.roundToStep(sideMarginSetting) },
                onValueChangeFinished = { onSideMarginChange(pendingSideMargin) },
                valueRange = sideMarginSetting.sliderRange(),
                steps = sideMarginSetting.sliderSteps(),
            )

            Text(text = stringResource(R.string.settings_reader_font_family_title), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                ReaderFontFamily.entries.forEach { family ->
                    FilterChip(
                        selected = settings.readerCustomFontId == null && settings.readerFontFamily == family,
                        onClick = { onFontFamilyChange(family) },
                        label = { Text(family.label()) },
                    )
                }
                settings.readerImportedFonts.forEach { font ->
                    FilterChip(
                        selected = settings.readerCustomFontId == font.id,
                        onClick = { onCustomFontChange(font.id) },
                        label = { Text(font.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }

            ReaderSettingsSwitchRow(
                title = stringResource(R.string.settings_reader_bionic_reading_title),
                subtitle = stringResource(R.string.settings_reader_bionic_reading_subtitle),
                checked = settings.readerBionicReading,
                onCheckedChange = onBionicReadingChange,
            )
        } else {
            PdfPageSection(settings = settings, controls = pdfPageControls)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_reader_volume_keys_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = stringResource(R.string.settings_reader_volume_keys_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.readerVolumeKeys,
                onCheckedChange = onVolumeKeysChange,
            )
        }

        ReaderSettingsSwitchRow(
            title = stringResource(R.string.settings_reader_show_headers_title),
            subtitle = stringResource(R.string.settings_reader_show_headers_subtitle),
            checked = settings.readerShowHeaders,
            onCheckedChange = onShowHeadersChange,
        )

        ReaderSettingsSwitchRow(
            title = stringResource(R.string.settings_reader_show_footer_title),
            subtitle = stringResource(R.string.settings_reader_show_footer_subtitle),
            checked = settings.readerShowFooter,
            onCheckedChange = onShowFooterChange,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_reader_keep_awake_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = stringResource(R.string.settings_reader_keep_awake_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.readerKeepAwake,
                onCheckedChange = onKeepAwakeChange,
            )
        }
    }
}

@Composable
private fun ReadAloudSettingsPage(
    settings: SettingsSnapshot,
    controls: ReadAloudVoiceControls,
    onStartReading: () -> Unit,
    onRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
) {
    val voices = controls.voices
    val onLoadVoices = controls.onLoad
    val onVoiceChange = controls.onVoiceChange
    val rateSetting = SettingsRegistry.ReadAloudRate
    val pitchSetting = SettingsRegistry.ReadAloudPitch
    val defaultEngineLabel = stringResource(R.string.settings_read_aloud_engine_default)
    var engineExpanded by remember { mutableStateOf(false) }
    val selectedEngineLabel = controls.engines.firstOrNull { it.name == settings.readAloudEngine }?.label ?: defaultEngineLabel
    val networkVoiceLabel = stringResource(R.string.settings_read_aloud_voice_network)
    val notInstalledVoiceLabel = stringResource(R.string.settings_read_aloud_voice_not_installed)
    val defaultVoiceLabel = stringResource(R.string.settings_read_aloud_voice_default)
    val femaleVoiceLabel = stringResource(R.string.settings_read_aloud_voice_female)
    val maleVoiceLabel = stringResource(R.string.settings_read_aloud_voice_male)
    val voiceLabel = { voice: SpeechVoiceOption ->
        when (voice.gender) {
            VoiceGender.FEMALE -> "$femaleVoiceLabel · ${voice.name}"
            VoiceGender.MALE -> "$maleVoiceLabel · ${voice.name}"
            null -> voice.name
        }
    }
    val languageOptions = remember(voices) { speechLanguageOptions(voices) }
    val defaultVoice = voices.firstOrNull(SpeechVoiceOption::isSystemDefault)
    var pendingVoiceName by rememberSaveable { mutableStateOf(settings.readAloudVoiceName) }
    var selectedLanguageTag by rememberSaveable { mutableStateOf("") }
    var languageExpanded by remember { mutableStateOf(false) }
    var voiceExpanded by remember { mutableStateOf(false) }
    val selectedVoice = voices.firstOrNull { it.name == pendingVoiceName }
    val selectedLanguageLabel = languageOptions.firstOrNull { it.tag == selectedLanguageTag }?.label.orEmpty()

    LaunchedEffect(voices, pendingVoiceName) {
        selectedLanguageTag = resolveSpeechLanguageTag(voices, pendingVoiceName, selectedLanguageTag)
    }
    LaunchedEffect(settings.readAloudVoiceName) {
        pendingVoiceName = settings.readAloudVoiceName
    }
    LaunchedEffect(Unit) {
        onLoadVoices()
    }

    Column(
        modifier = Modifier
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .heightIn(max = Sizes.contentMaxWidth)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Button(
            onClick = onStartReading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.Outlined.Headphones, contentDescription = null)
            Text(
                text = stringResource(R.string.settings_read_aloud_start),
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }

        Text(text = stringResource(R.string.settings_read_aloud_engine_title), style = MaterialTheme.typography.labelLarge)
        MutedCaption(stringResource(R.string.settings_read_aloud_engine_subtitle))
        ReaderSelectField(
            text = selectedEngineLabel,
            enabled = true,
            expanded = engineExpanded,
            onExpandedChange = { engineExpanded = it },
        ) {
            DropdownMenuItem(
                text = { Text(defaultEngineLabel) },
                onClick = {
                    engineExpanded = false
                    pendingVoiceName = ""
                    controls.onEngineChange("")
                },
            )
            controls.engines.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        engineExpanded = false
                        pendingVoiceName = ""
                        controls.onEngineChange(option.name)
                    },
                )
            }
        }

        Text(text = stringResource(R.string.settings_read_aloud_language_title), style = MaterialTheme.typography.labelLarge)
        ReaderSelectField(
            text = selectedLanguageLabel.ifBlank { defaultVoiceLabel },
            enabled = languageOptions.isNotEmpty(),
            expanded = languageExpanded,
            onExpandedChange = { languageExpanded = it },
        ) {
            languageOptions.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        selectedLanguageTag = language.tag
                        languageExpanded = false
                        voices.firstOrNull { it.localeTag == language.tag }?.let { voice ->
                            pendingVoiceName = voice.name
                            onVoiceChange(voice.name)
                        }
                    },
                )
            }
        }

        Text(text = stringResource(R.string.settings_read_aloud_voice_title), style = MaterialTheme.typography.labelLarge)
        Text(
            text = stringResource(R.string.settings_read_aloud_voice_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val visibleVoices = voices.filter { it.localeTag == selectedLanguageTag }
        ReaderSelectField(
            text = selectedVoice?.let(voiceLabel) ?: defaultVoiceLabel,
            enabled = languageOptions.isNotEmpty(),
            expanded = voiceExpanded,
            onExpandedChange = { voiceExpanded = it },
        ) {
            if (defaultVoice?.localeTag == selectedLanguageTag) {
                DropdownMenuItem(
                    text = { Text(defaultVoiceLabel) },
                    onClick = {
                        pendingVoiceName = ""
                        selectedLanguageTag = defaultVoice.localeTag
                        onVoiceChange("")
                        voiceExpanded = false
                    },
                )
            }
            visibleVoices.forEach { voice ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(voiceLabel(voice), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!voice.installed) MutedCaption(notInstalledVoiceLabel)
                            if (voice.requiresNetwork) MutedCaption(networkVoiceLabel)
                        }
                    },
                    onClick = {
                        pendingVoiceName = voice.name
                        onVoiceChange(voice.name)
                        voiceExpanded = false
                    },
                )
            }
        }
        if (voices.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_read_aloud_voice_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ReadAloudAdjustmentSlider(
            label = stringResource(R.string.settings_read_aloud_rate_title),
            value = settings.readAloudRate,
            setting = rateSetting,
            onValueChangeFinished = onRateChange,
            subtitle = stringResource(R.string.settings_read_aloud_rate_subtitle),
        )
        ReadAloudAdjustmentSlider(
            label = stringResource(R.string.settings_read_aloud_pitch_title),
            value = settings.readAloudPitch,
            setting = pitchSetting,
            onValueChangeFinished = onPitchChange,
            subtitle = stringResource(R.string.settings_read_aloud_pitch_subtitle),
        )
        SpeechPronunciationEditor(controls)
    }
}

/** An outlined button showing [text] that opens a full-width dropdown holding [content]. */
@Composable
private fun ReaderSelectField(
    text: String,
    enabled: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { onExpandedChange(true) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
private fun MutedCaption(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The PDF page settings the style panel shows in place of typography. */
internal class PdfPageControls(
    val onCropMarginsChange: (Boolean) -> Unit,
    val onFitWidthChange: (Boolean) -> Unit,
    val onDarkenTextChange: (Boolean) -> Unit,
    val rotationDegrees: Int,
    val onRotateClockwise: () -> Unit,
    val onBrowsePages: () -> Unit,
)

@Composable
private fun PdfPageSection(settings: SettingsSnapshot, controls: PdfPageControls) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = stringResource(R.string.reader_pdf_page_section_title), style = MaterialTheme.typography.labelLarge)
        Text(
            text = stringResource(R.string.reader_pdf_page_section_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReaderSettingsSwitchRow(
            title = stringResource(R.string.reader_pdf_crop_margins_title),
            subtitle = stringResource(R.string.reader_pdf_crop_margins_subtitle),
            checked = settings.readerPdfCropMargins,
            onCheckedChange = controls.onCropMarginsChange,
        )
        ReaderSettingsSwitchRow(
            title = stringResource(R.string.reader_pdf_fit_width_title),
            subtitle = stringResource(R.string.reader_pdf_fit_width_subtitle),
            checked = settings.readerPdfFitWidth,
            onCheckedChange = controls.onFitWidthChange,
        )
        ReaderSettingsSwitchRow(
            title = stringResource(R.string.reader_pdf_darken_text_title),
            subtitle = stringResource(R.string.reader_pdf_darken_text_subtitle),
            checked = settings.readerBolderText,
            onCheckedChange = controls.onDarkenTextChange,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.reader_pdf_rotation_title), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.reader_pdf_rotation_value, controls.rotationDegrees),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = controls.onRotateClockwise) {
                Text(stringResource(R.string.reader_pdf_rotate_action))
            }
        }
        FilledTonalButton(onClick = controls.onBrowsePages) {
            Text(stringResource(R.string.reader_pdf_browse_pages))
        }
    }
}

@Composable
private fun ReaderSettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ReaderStyleLabel(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReaderFontFamily.label(): String = when (this) {
    ReaderFontFamily.SERIF -> stringResource(R.string.settings_reader_font_family_serif)
    ReaderFontFamily.SANS -> stringResource(R.string.settings_reader_font_family_sans)
    ReaderFontFamily.MONO -> stringResource(R.string.settings_reader_font_family_mono)
}

@Composable
private fun ReaderTheme.label(): String = when (this) {
    ReaderTheme.SYSTEM -> stringResource(R.string.settings_reader_theme_system)
    ReaderTheme.LIGHT -> stringResource(R.string.settings_reader_theme_light)
    ReaderTheme.PAPER -> stringResource(R.string.settings_reader_theme_paper)
    ReaderTheme.SEPIA -> stringResource(R.string.settings_reader_theme_sepia)
    ReaderTheme.MINT -> stringResource(R.string.settings_reader_theme_mint)
    ReaderTheme.SKY -> stringResource(R.string.settings_reader_theme_sky)
    ReaderTheme.ROSE -> stringResource(R.string.settings_reader_theme_rose)
    ReaderTheme.DARK -> stringResource(R.string.settings_reader_theme_dark)
    ReaderTheme.OLED -> stringResource(R.string.settings_reader_theme_oled)
}

@Composable
private fun ReaderThemeSwatch(theme: ReaderTheme) {
    Box(
        modifier = Modifier
            .size(Sizes.swatchSmall)
            .background(color = theme.swatchColor(), shape = CircleShape),
    )
}

@Composable
private fun ReaderTheme.swatchColor(): Color = when (this) {
    ReaderTheme.SYSTEM -> MaterialTheme.colorScheme.primary
    ReaderTheme.LIGHT -> Palette.ReaderLightBackground
    ReaderTheme.PAPER -> Palette.ReaderPaperBackground
    ReaderTheme.SEPIA -> Palette.ReaderSepiaBackground
    ReaderTheme.MINT -> Palette.ReaderMintBackground
    ReaderTheme.SKY -> Palette.ReaderSkyBackground
    ReaderTheme.ROSE -> Palette.ReaderRoseBackground
    ReaderTheme.DARK -> Palette.ReaderDarkBackground
    ReaderTheme.OLED -> Palette.ReaderOledBackground
}

@Composable
private fun SettingsSnapshot.readerBackgroundColor(): Color = when {
    displayProfile.isMonochrome(einkPalette) -> Palette.EinkBackground
    readerTheme == ReaderTheme.LIGHT -> Palette.ReaderLightBackground
    readerTheme == ReaderTheme.PAPER -> Palette.ReaderPaperBackground
    readerTheme == ReaderTheme.SEPIA -> Palette.ReaderSepiaBackground
    readerTheme == ReaderTheme.MINT -> Palette.ReaderMintBackground
    readerTheme == ReaderTheme.SKY -> Palette.ReaderSkyBackground
    readerTheme == ReaderTheme.ROSE -> Palette.ReaderRoseBackground
    readerTheme == ReaderTheme.DARK -> Palette.ReaderDarkBackground
    readerTheme == ReaderTheme.OLED -> Palette.ReaderOledBackground
    themeMode == ThemeMode.DARK -> Palette.ReaderDarkBackground
    else -> Palette.ReaderPaperBackground
}

private const val ReaderHudStandardAlpha = 0.9f
private const val SimilarReaderChromeColorDelta = 0.12f
private const val EnglishDictionaryDownloadUrl = "https://en-word.net/static/english-wordnet-2025.zip"
private const val VolumeKeyLongPressMillis = 500L

/** How much of the page width, at each edge, starts a brightness (left) or warm-light (right) swipe. */
private const val EdgeSwipeZoneFraction = 0.12f

/** Brightness a swipe starts from while the reader still follows the system brightness. */
private const val EdgeSwipeBrightnessStart = 50
private const val EdgeSwipeMinBrightness = 5
private const val ReturnRecapVisibleMillis = 10_000L
private const val TtsSettingsAction = "com.android.settings.TTS_SETTINGS"

internal fun shouldInterceptReaderVolumeKey(
    readAloudPlaying: Boolean,
    volumeKeysTurnPages: Boolean,
    chromeVisible: Boolean,
): Boolean = !readAloudPlaying && (volumeKeysTurnPages || chromeVisible)

internal fun shouldPauseReaderWebView(readAloudPlaying: Boolean): Boolean = !readAloudPlaying

internal fun shouldPauseReadAloudOnReaderTap(readAloudPlaying: Boolean): Boolean = readAloudPlaying

internal fun shouldShowReaderSettingsFooterButton(
    readerLoaded: Boolean,
    chromeVisible: Boolean,
    selectionActive: Boolean,
    highlightCardActive: Boolean,
    dictionaryActionsActive: Boolean,
): Boolean = readerLoaded && !chromeVisible && !selectionActive && !highlightCardActive && !dictionaryActionsActive

internal fun needsNotificationPermission(sdkInt: Int, permissionGranted: Boolean): Boolean =
    sdkInt >= 33 && !permissionGranted
private val ReaderSyncDotSize = Sizes.syncDot

private val ReaderSyncDotEinkSize = Sizes.syncDotEink
private const val ReaderSyncDotStrokeFraction = 0.22f
private const val ReaderSyncDotCoreFraction = 0.34f

private const val EinkFlashDurationMillis = 120L

/** Entries kept above the current chapter when the contents list opens scrolled to it. */
private const val ContentsScrollContextRows = 2
private val readerHeaderTopPadding = Spacing.xxxl + Spacing.md
private val readerHeaderHorizontalPadding = Spacing.xl
private val readerLandscapeHeaderMaxGap = Spacing.sm

/** Side insets (landscape cutout / nav bar) the page itself is padded by, so HUD chips line up with the text. */
private val readerHudHorizontalInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)

/** Below this width, landscape stays a single reader pane - matches Library's tablet-landscape breakpoint. */
private const val TabletLandscapeMinWidthDp = 600
private const val ReaderPaneWeight = 0.65f
private const val NotesPaneWeight = 0.35f

private data class TocDisplayItem(val entry: TocEntry, val depth: Int)

private fun List<TocEntry>.flattenToc(depth: Int = 0): List<TocDisplayItem> =
    flatMap { entry -> listOf(TocDisplayItem(entry, depth)) + entry.children.flattenToc(depth + 1) }

private fun IntSetting.sliderRange(): ClosedFloatingPointRange<Float> = range.first.toFloat()..range.last.toFloat()

private fun IntSetting.sliderSteps(): Int = ((range.last - range.first) / step - 1).coerceAtLeast(0)

internal fun FloatSetting.sliderSteps(): Int = (((range.endInclusive - range.start) / step).roundToInt() - 1).coerceAtLeast(0)

private fun Float.roundToStep(setting: IntSetting): Int = ((this / setting.step).roundToInt() * setting.step).coerceIn(setting.range)

internal fun Float.roundToStep(setting: FloatSetting): Float =
    ((this / setting.step).roundToInt() * setting.step).coerceIn(setting.range.start, setting.range.endInclusive)

private fun Float.roundToTenth(): Float = (this * 10).roundToInt() / 10f

private fun Color.isSameIshAs(other: Color): Boolean =
    abs(red - other.red) <= SimilarReaderChromeColorDelta &&
        abs(green - other.green) <= SimilarReaderChromeColorDelta &&
        abs(blue - other.blue) <= SimilarReaderChromeColorDelta

internal fun Context.copyTextToClipboard(text: String) {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(getString(R.string.app_name), text)
    clipboardManager.setPrimaryClip(clip)
}
