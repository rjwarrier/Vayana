package com.vayana.feature.reader

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.datastore.settings.FloatSetting
import com.vayana.core.datastore.settings.IntSetting
import com.vayana.core.datastore.settings.ReaderFontFamily
import com.vayana.core.datastore.settings.ReaderTheme
import com.vayana.core.datastore.settings.SettingsRegistry
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.LocalDisplayProfile
import com.vayana.core.designsystem.theme.ThemeMode
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
import com.vayana.core.resources.R
import com.vayana.core.common.QuoteCitation
import com.vayana.dictionary.api.DictionaryEntry
import com.vayana.dictionary.api.PartOfSpeech
import com.vayana.reader.api.BookEngine
import com.vayana.reader.api.Locator
import com.vayana.reader.api.TocEntry
import com.vayana.reader.web.FoliateBookEngine
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun ReaderRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ReaderViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.effectiveSettings.collectAsState()
    val usingCustomStyle by viewModel.usingCustomStyle.collectAsState()
    val dictionaryLookup by viewModel.dictionaryLookup.collectAsState()
    val readingPositionPrompt by viewModel.readingPositionPrompt.collectAsState()
    val recentLookups by viewModel.recentLookups.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val context = LocalContext.current
    val dictionaryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            viewModel.cancelDictionaryInstall()
        } else {
            viewModel.installEnglishDictionary(uri.toString())
        }
    }

    // Tablet landscape: book and notes side by side, instead of notes living only in the bottom chrome.
    // Gated by the "Two-column landscape layout" setting - landscapeTwoColumnLayout passes through
    // effectiveSettings unchanged (it isn't one of the per-book style overrides), so reading it here is safe.
    val configuration = LocalConfiguration.current
    val showNotesSidePanel = settings.landscapeTwoColumnLayout &&
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        configuration.screenWidthDp >= TabletLandscapeMinWidthDp

    if (showNotesSidePanel) {
        var notesSidePanelVisible by rememberSaveable { mutableStateOf(true) }
        Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            ReaderScreen(
                modifier = Modifier.weight(if (notesSidePanelVisible) ReaderPaneWeight else 1f).fillMaxHeight(),
                uiState = uiState,
                settings = settings,
                usingCustomStyle = usingCustomStyle,
                onUseCustomStyleChange = viewModel::setUseCustomStyle,
                dictionaryLookup = dictionaryLookup,
                readingPositionPrompt = readingPositionPrompt,
                recentLookups = recentLookups,
                searchResults = searchResults,
                onSearchQueryChange = viewModel::search,
                onSearchResultClick = viewModel::openSearchResult,
                onClearSearch = viewModel::clearSearch,
                onLookupWord = viewModel::lookupWord,
                onSaveLookupAsNote = viewModel::saveLookupAsNote,
                onSaveLookupAsVocabulary = viewModel::saveLookupAsVocabularyCard,
                onAcceptReadingPositionPrompt = viewModel::acceptReadingPositionPrompt,
                onDismissReadingPositionPrompt = viewModel::dismissReadingPositionPrompt,
                onEngineReady = viewModel::bindEngine,
                onTapPrevious = viewModel::previousPage,
                onTapNext = viewModel::nextPage,
                onOpenTocEntry = viewModel::openTocEntry,
                onProgressChange = viewModel::goToProgress,
                onAnnotationClick = viewModel::openAnnotation,
                onReturnToPreviousPosition = viewModel::returnToPreviousPosition,
                onCreateHighlight = viewModel::createHighlight,
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
                onReaderThemeChange = viewModel::updateReaderTheme,
                onSideMarginChange = viewModel::updateSideMargin,
                onVolumeKeysChange = viewModel::updateVolumeKeys,
                onKeepAwakeChange = viewModel::updateKeepAwake,
                onShowHeadersChange = viewModel::updateShowHeaders,
                onShowFooterChange = viewModel::updateShowFooter,
                onBionicReadingChange = viewModel::updateBionicReading,
                onPause = viewModel::onPause,
                onResume = viewModel::onResume,
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
        return
    }

    ReaderScreen(
        modifier = modifier,
        uiState = uiState,
        settings = settings,
        usingCustomStyle = usingCustomStyle,
        onUseCustomStyleChange = viewModel::setUseCustomStyle,
        dictionaryLookup = dictionaryLookup,
        readingPositionPrompt = readingPositionPrompt,
        recentLookups = recentLookups,
        searchResults = searchResults,
        onSearchQueryChange = viewModel::search,
        onSearchResultClick = viewModel::openSearchResult,
        onClearSearch = viewModel::clearSearch,
        onLookupWord = viewModel::lookupWord,
        onSaveLookupAsNote = viewModel::saveLookupAsNote,
        onSaveLookupAsVocabulary = viewModel::saveLookupAsVocabularyCard,
        onAcceptReadingPositionPrompt = viewModel::acceptReadingPositionPrompt,
        onDismissReadingPositionPrompt = viewModel::dismissReadingPositionPrompt,
        onEngineReady = viewModel::bindEngine,
        onTapPrevious = viewModel::previousPage,
        onTapNext = viewModel::nextPage,
        onOpenTocEntry = viewModel::openTocEntry,
        onProgressChange = viewModel::goToProgress,
        onAnnotationClick = viewModel::openAnnotation,
        onReturnToPreviousPosition = viewModel::returnToPreviousPosition,
        onCreateHighlight = viewModel::createHighlight,
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
        onReaderThemeChange = viewModel::updateReaderTheme,
        onSideMarginChange = viewModel::updateSideMargin,
        onVolumeKeysChange = viewModel::updateVolumeKeys,
        onKeepAwakeChange = viewModel::updateKeepAwake,
        onShowHeadersChange = viewModel::updateShowHeaders,
        onShowFooterChange = viewModel::updateShowFooter,
        onBionicReadingChange = viewModel::updateBionicReading,
        onPause = viewModel::onPause,
        onResume = viewModel::onResume,
        onBack = onBack,
    )
}

private enum class ReaderPanel { CONTENTS, BOOKMARKS, NOTES, PROGRESS, STYLE, SEARCH }

private enum class HighlightColor(val key: String, val labelRes: Int, val swatch: Color) {
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
    usingCustomStyle: Boolean,
    onUseCustomStyleChange: (Boolean) -> Unit,
    dictionaryLookup: DictionaryLookupState,
    recentLookups: List<String>,
    searchResults: List<com.vayana.reader.api.SearchResult>,
    onSearchQueryChange: (String) -> Unit,
    onSearchResultClick: (com.vayana.reader.api.SearchResult) -> Unit,
    onClearSearch: () -> Unit,
    onLookupWord: (String) -> Unit,
    onSaveLookupAsNote: (DictionaryEntry) -> Unit,
    onSaveLookupAsVocabulary: (DictionaryEntry) -> Unit,
    readingPositionPrompt: ReadingPositionPrompt?,
    onAcceptReadingPositionPrompt: () -> Unit,
    onDismissReadingPositionPrompt: () -> Unit,
    onEngineReady: (BookEngine) -> Unit,
    onTapPrevious: () -> Unit,
    onTapNext: () -> Unit,
    onOpenTocEntry: (String) -> Unit,
    onProgressChange: (Float) -> Unit,
    onAnnotationClick: (Annotation) -> Unit,
    onReturnToPreviousPosition: () -> Unit,
    onCreateHighlight: (String) -> Unit,
    onCreateUnderline: () -> Unit,
    onCreateNote: (String) -> Unit,
    onCreateBookmark: () -> Unit,
    onClearSelection: () -> Unit,
    onDownloadDictionary: () -> Unit,
    onInstallDictionary: () -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onFontFamilyChange: (ReaderFontFamily) -> Unit,
    onReaderThemeChange: (ReaderTheme) -> Unit,
    onSideMarginChange: (Int) -> Unit,
    onVolumeKeysChange: (Boolean) -> Unit,
    onKeepAwakeChange: (Boolean) -> Unit,
    onShowHeadersChange: (Boolean) -> Unit,
    onShowFooterChange: (Boolean) -> Unit,
    onBionicReadingChange: (Boolean) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onBack: () -> Unit,
) {
    var chromeVisible by remember { mutableStateOf(false) }
    var selectedPanel by remember { mutableStateOf(ReaderPanel.CONTENTS) }
    var noteDialogVisible by remember { mutableStateOf(false) }
    var footerShowsBookTime by remember { mutableStateOf(false) }
    val sessionStartMillis = remember { System.currentTimeMillis() }
    var nowMillis by remember { mutableLongStateOf(sessionStartMillis) }
    val context = LocalContext.current
    val onEngineReadyState = rememberUpdatedState(onEngineReady)
    val onPauseState = rememberUpdatedState(onPause)
    val onResumeState = rememberUpdatedState(onResume)
    val lifecycleOwner = LocalLifecycleOwner.current
    val rootView = LocalView.current
    val focusRequester = remember { FocusRequester() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var volumeKeyDownAt by remember { mutableStateOf(0L) }
    val onReaderTapState = rememberUpdatedState<(Float, Int) -> Unit> { x, width ->
        val menuStart = width / 3f
        val menuEnd = menuStart * 2f
        when {
            x < menuStart -> onTapPrevious()
            x > menuEnd -> onTapNext()
            chromeVisible -> chromeVisible = false
            else -> {
                selectedPanel = ReaderPanel.STYLE
                chromeVisible = true
            }
        }
    }
    val onHardwarePageKeyState = rememberUpdatedState<(Int, Int, Long) -> Boolean> { keyCode, action, heldMillis ->
        if (keyCode == AndroidKeyEvent.KEYCODE_VOLUME_UP || keyCode == AndroidKeyEvent.KEYCODE_VOLUME_DOWN) {
            if (chromeVisible) {
                if (action == AndroidKeyEvent.ACTION_UP) {
                    chromeVisible = false
                }
                true
            } else if (!settings.readerVolumeKeys) {
                false
            } else {
                if (action == AndroidKeyEvent.ACTION_UP) {
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
                    webViewRef?.onPause()
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

    // Immersive reading: status bar hides with the rest of the chrome, comes back on tap.
    DisposableEffect(chromeVisible) {
        val window = (rootView.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, rootView)
            if (chromeVisible) {
                controller.show(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
        onDispose {
            val disposeWindow = (rootView.context as? Activity)?.window ?: return@onDispose
            WindowCompat.getInsetsController(disposeWindow, rootView).show(WindowInsetsCompat.Type.statusBars())
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(30_000)
        }
    }

    // Successive partial E-Ink refreshes accumulate ghosting; periodically forcing one
    // maximal-area repaint (a brief full-black flash) makes the panel's controller do a clean
    // full update, the same trick Kindle/Boox readers use ("refresh every N pages").
    var einkPageTurnCount by remember { mutableIntStateOf(0) }
    var einkFlashTrigger by remember { mutableIntStateOf(0) }
    var einkFlashVisible by remember { mutableStateOf(false) }
    val currentLocatorCfi = (uiState as? ReaderUiState.Loaded)?.currentLocator?.cfi
    LaunchedEffect(currentLocatorCfi) {
        if (currentLocatorCfi != null && settings.displayProfile == DisplayProfile.E_INK) {
            einkPageTurnCount++
            if (einkPageTurnCount % EinkFullRefreshEveryPages == 0) {
                einkFlashTrigger++
            }
        }
    }
    LaunchedEffect(einkFlashTrigger) {
        if (einkFlashTrigger == 0) return@LaunchedEffect
        einkFlashVisible = true
        delay(EinkFlashDurationMillis)
        einkFlashVisible = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.VolumeUp || event.key == Key.VolumeDown) {
                    if (chromeVisible) {
                        if (event.type == KeyEventType.KeyUp) {
                            chromeVisible = false
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (!settings.readerVolumeKeys) return@onPreviewKeyEvent false
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (volumeKeyDownAt == 0L) volumeKeyDownAt = System.currentTimeMillis()
                        }
                        KeyEventType.KeyUp -> {
                            val heldMillis = if (volumeKeyDownAt == 0L) 0L else System.currentTimeMillis() - volumeKeyDownAt
                            volumeKeyDownAt = 0L
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
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
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
                    setOnTouchListener { view, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                downX = event.x
                                downY = event.y
                                downTime = event.eventTime
                            }
                            MotionEvent.ACTION_UP -> {
                                val isShortTap = event.eventTime - downTime < ViewConfiguration.getLongPressTimeout()
                                if (isShortTap && abs(event.x - downX) <= touchSlop && abs(event.y - downY) <= touchSlop) {
                                    onReaderTapState.value(event.x, view.width)
                                }
                            }
                        }
                        false
                    }
                }
                webViewRef = webView
                onEngineReadyState.value(FoliateBookEngine(webView, context.applicationContext))
                FrameLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    addView(webView)
                }
            },
            onRelease = { container ->
                webViewRef?.destroy()
                webViewRef = null
                container.removeAllViews()
            },
        )

        if (settings.readerShowHeaders) {
            ReaderClockHeader(
                modifier = Modifier.align(Alignment.TopCenter),
                nowMillis = nowMillis,
            )
            ReaderSessionHeader(
                modifier = Modifier.align(Alignment.TopStart),
                nowMillis = nowMillis,
                sessionStartMillis = sessionStartMillis,
            )
        }

        if (uiState is ReaderUiState.Loaded && settings.readerShowHeaders) {
            ReaderTimeLeftHeader(
                modifier = Modifier.align(Alignment.TopEnd),
                locator = uiState.currentLocator,
                showBookTime = footerShowsBookTime,
                onToggle = { footerShowsBookTime = !footerShowsBookTime },
            )
        }

        if (uiState is ReaderUiState.Loaded && settings.readerShowFooter) {
            ReaderPageNumberFooter(
                modifier = Modifier.align(Alignment.BottomStart),
                locator = uiState.currentLocator,
            )
            ReaderBookProgressFooter(
                modifier = Modifier.align(Alignment.BottomEnd),
                locator = uiState.currentLocator,
                onLongPress = {
                    selectedPanel = ReaderPanel.CONTENTS
                    chromeVisible = true
                },
            )
        }

        if (uiState is ReaderUiState.Loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        if (uiState is ReaderUiState.Failed) {
            Text(
                text = uiState.message,
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
                onReaderThemeChange = onReaderThemeChange,
                onSideMarginChange = onSideMarginChange,
                onVolumeKeysChange = onVolumeKeysChange,
                onKeepAwakeChange = onKeepAwakeChange,
                onShowHeadersChange = onShowHeadersChange,
                onShowFooterChange = onShowFooterChange,
                onBionicReadingChange = onBionicReadingChange,
                onCreateBookmark = onCreateBookmark,
                onRefreshScreen = { einkFlashTrigger++ },
                searchResults = searchResults,
                onSearchQueryChange = onSearchQueryChange,
                onSearchResultClick = {
                    chromeVisible = false
                    onSearchResultClick(it)
                },
                onClearSearch = onClearSearch,
            )
        }

        if (einkFlashVisible) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }

        val loadedState = uiState as? ReaderUiState.Loaded
        val selection = loadedState?.selection
        val dictionaryWord = dictionaryLookup.wordOrNull()
        val placeSelectionCardAtBottom = selection?.verticalPosition?.let { it < 0.5f } == true
        AnimatedVisibility(
            visible = selection != null || dictionaryWord != null,
            modifier = Modifier.align(
                if (placeSelectionCardAtBottom) Alignment.BottomCenter else Alignment.TopCenter,
            ),
            enter = vayanaScaleIn() + vayanaFadeIn(),
            exit = vayanaScaleOut() + vayanaFadeOut(),
        ) {
            if (selection != null || dictionaryWord != null) {
                SelectionActions(
                    modifier = if (placeSelectionCardAtBottom) {
                        Modifier.navigationBarsPadding()
                    } else {
                        Modifier.statusBarsPadding()
                    },
                    selectedText = selection?.selectedText ?: dictionaryWord.orEmpty(),
                    selectionActionsEnabled = selection != null,
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
                        )
                        context.copyTextToClipboard(citation)
                        onClearSelection()
                    },
                    onNote = { noteDialogVisible = true },
                    onShare = {
                        val citation = QuoteCitation.format(
                            text = selection?.selectedText.orEmpty(),
                            author = loadedState?.bookAuthor,
                            bookTitle = loadedState?.bookTitle,
                            chapterTitle = selection?.chapterTitle,
                        )
                        context.shareText(citation)
                    },
                    onDownloadDictionary = onDownloadDictionary,
                    onInstallDictionary = onInstallDictionary,
                    onLookupWord = onLookupWord,
                    onSaveLookupAsNote = onSaveLookupAsNote,
                    onSaveLookupAsVocabulary = onSaveLookupAsVocabulary,
                )
            }
        }
    }

    if (noteDialogVisible) {
        NoteDialog(
            onDismiss = { noteDialogVisible = false },
            onConfirm = { note ->
                noteDialogVisible = false
                onCreateNote(note)
            },
        )
    }

    if (readingPositionPrompt != null) {
        ReadingPositionPromptDialog(
            prompt = readingPositionPrompt,
            onGoToRecentLocation = onAcceptReadingPositionPrompt,
            onStayHere = onDismissReadingPositionPrompt,
        )
    }
}

@Composable
private fun ReaderClockHeader(modifier: Modifier = Modifier, nowMillis: Long) {
    val clockText = remember(nowMillis) { DateFormat.format("hh:mm a", nowMillis).toString() }
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = readerHeaderTopPadding),
        color = readerHudSurfaceColor(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = readerHudElevation(),
    ) {
        Text(
            text = clockText,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun ReaderSessionHeader(
    modifier: Modifier = Modifier,
    nowMillis: Long,
    sessionStartMillis: Long,
) {
    val sessionMinutes = ((nowMillis - sessionStartMillis) / 60_000L).coerceAtLeast(0L).toInt()
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(start = Spacing.md, top = readerHeaderTopPadding),
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
    onToggle: () -> Unit,
) {
    val minutesLeft = if (showBookTime) locator?.bookMinutesLeft else locator?.chapterMinutesLeft
    if (minutesLeft == null) return
    val labelRes = if (showBookTime) R.string.reader_footer_time_left_book else R.string.reader_footer_time_left_chapter
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(end = Spacing.md, top = readerHeaderTopPadding)
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
) {
    val currentPage = locator?.currentPage
    val totalPages = locator?.totalPages
    if (currentPage == null || totalPages == null) return
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = Spacing.md, bottom = Spacing.sm),
        color = readerHudSurfaceColor(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = readerHudElevation(),
    ) {
        Text(
            text = stringResource(R.string.reader_progress_page_of, currentPage, totalPages),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderBookProgressFooter(
    modifier: Modifier = Modifier,
    locator: Locator?,
    onLongPress: () -> Unit = {},
) {
    val progress = locator?.progression ?: return
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(end = Spacing.md, bottom = Spacing.sm)
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
        color = readerHudSurfaceColor(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = readerHudElevation(),
    ) {
        Text(
            text = stringResource(R.string.reader_progress_percent, (progress * 100).roundToInt()),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
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
private fun readerChromeSurfaceColor(): Color =
    if (LocalDisplayProfile.current == DisplayProfile.E_INK) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

@Composable
private fun readerChromeElevation() =
    if (LocalDisplayProfile.current == DisplayProfile.E_INK) Elevations.none else Spacing.sm

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
private fun SelectionActions(
    modifier: Modifier = Modifier,
    selectedText: String,
    selectionActionsEnabled: Boolean,
    dictionaryLookup: DictionaryLookupState,
    recentLookups: List<String>,
    onHighlight: (String) -> Unit,
    onUnderline: () -> Unit,
    onCopy: () -> Unit,
    onNote: () -> Unit,
    onShare: () -> Unit,
    onDownloadDictionary: () -> Unit,
    onInstallDictionary: () -> Unit,
    onLookupWord: (String) -> Unit,
    onSaveLookupAsNote: (DictionaryEntry) -> Unit,
    onSaveLookupAsVocabulary: (DictionaryEntry) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight * DictionaryCardMaximumHeightFraction)
                .padding(Paddings.screenHorizontal, Spacing.md),
            color = readerHudSurfaceColor(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = if (LocalDisplayProfile.current == DisplayProfile.E_INK) Elevations.none else Spacing.sm,
            shadowElevation = if (LocalDisplayProfile.current == DisplayProfile.E_INK) Elevations.none else Spacing.xs,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md),
            ) {
            if (dictionaryLookup !is DictionaryLookupState.Found) {
                Text(
                    text = selectedText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DictionaryLookupContent(
                state = dictionaryLookup,
                recentLookups = recentLookups,
                onDownloadDictionary = onDownloadDictionary,
                onInstallDictionary = onInstallDictionary,
                onLookupWord = onLookupWord,
                onSaveLookupAsNote = onSaveLookupAsNote,
                onSaveLookupAsVocabulary = onSaveLookupAsVocabulary,
            )
            if (selectionActionsEnabled) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    HighlightColor.entries.forEach { color ->
                        FilledTonalButton(onClick = { onHighlight(color.key) }) {
                            Box(
                                modifier = Modifier
                                    .size(Sizes.swatchSmall)
                                    .background(color = color.swatch, shape = CircleShape),
                            )
                            Text(
                                text = stringResource(color.labelRes),
                                modifier = Modifier.padding(start = Spacing.xs),
                            )
                        }
                    }
                    FilledTonalButton(onClick = onUnderline) {
                        Text(stringResource(R.string.reader_selection_underline))
                    }
                    FilledTonalButton(onClick = onCopy) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.iconSmall),
                        )
                        Text(stringResource(R.string.reader_selection_copy))
                    }
                    FilledTonalButton(onClick = onNote) {
                        Text(stringResource(R.string.reader_selection_note))
                    }
                    FilledTonalButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.iconSmall),
                        )
                        Text(stringResource(R.string.reader_selection_share))
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun DictionaryLookupContent(
    state: DictionaryLookupState,
    recentLookups: List<String>,
    onDownloadDictionary: () -> Unit,
    onInstallDictionary: () -> Unit,
    onLookupWord: (String) -> Unit,
    onSaveLookupAsNote: (DictionaryEntry) -> Unit,
    onSaveLookupAsVocabulary: (DictionaryEntry) -> Unit,
) {
    val context = LocalContext.current
    when (state) {
        DictionaryLookupState.Hidden -> Unit
        is DictionaryLookupState.LookingUp,
        is DictionaryLookupState.Installing,
        -> Row(
            modifier = Modifier.padding(top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(Sizes.iconSmall))
            Text(
                text = stringResource(
                    if (state is DictionaryLookupState.Installing) {
                        R.string.reader_dictionary_installing
                    } else {
                        R.string.reader_dictionary_looking_up
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        is DictionaryLookupState.PackRequired -> Column(modifier = Modifier.padding(top = Spacing.sm)) {
            Text(
                text = stringResource(R.string.reader_dictionary_pack_required),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = onDownloadDictionary) {
                    Text(stringResource(R.string.reader_dictionary_download))
                }
                TextButton(onClick = onInstallDictionary) {
                    Text(stringResource(R.string.reader_dictionary_install_zip))
                }
            }
            if (recentLookups.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.reader_dictionary_recent_lookups),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                Row(
                    modifier = Modifier
                        .padding(top = Spacing.xs)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    recentLookups.forEach { word ->
                        AssistChip(onClick = { onLookupWord(word) }, label = { Text(word) })
                    }
                }
            }
        }
        is DictionaryLookupState.Found -> Column(modifier = Modifier.padding(top = Spacing.sm)) {
            Text(
                text = state.entry.headword,
                style = MaterialTheme.typography.titleLarge,
            )
            state.entry.senses.take(MaxDisplayedDictionarySenses).forEach { sense ->
                Text(
                    text = "${sense.partOfSpeech.shortLabel()}  ${sense.definition}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                sense.examples.firstOrNull()?.let { example ->
                    Text(
                        text = "“$example”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val otherSynonyms = sense.synonyms.filterNot { it.equals(state.entry.headword, ignoreCase = true) }
                if (otherSynonyms.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .padding(top = Spacing.xs)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        otherSynonyms.forEach { synonym ->
                            Text(
                                text = synonym,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.clickable { onLookupWord(synonym) },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.entry.attribution,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    val definition = state.entry.senses.firstOrNull()?.definition.orEmpty()
                    context.copyTextToClipboard("${state.entry.headword}: $definition")
                }) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.reader_dictionary_copy),
                        modifier = Modifier.size(Sizes.iconSmall),
                    )
                }
                IconButton(onClick = { onSaveLookupAsNote(state.entry) }) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = stringResource(R.string.reader_dictionary_add_to_notes),
                        modifier = Modifier.size(Sizes.iconSmall),
                    )
                }
                IconButton(onClick = { onSaveLookupAsVocabulary(state.entry) }) {
                    Icon(
                        imageVector = Icons.Outlined.Style,
                        contentDescription = stringResource(R.string.reader_dictionary_add_to_vocabulary),
                        modifier = Modifier.size(Sizes.iconSmall),
                    )
                }
            }
        }
        is DictionaryLookupState.NotFound -> Text(
            text = stringResource(R.string.reader_dictionary_not_found, state.word),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        is DictionaryLookupState.Failed -> Column(modifier = Modifier.padding(top = Spacing.sm)) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onInstallDictionary) {
                Text(stringResource(R.string.reader_dictionary_choose_another))
            }
        }
    }
}

private fun PartOfSpeech.shortLabel(): String = when (this) {
    PartOfSpeech.NOUN -> "noun"
    PartOfSpeech.VERB -> "verb"
    PartOfSpeech.ADJECTIVE -> "adj."
    PartOfSpeech.ADVERB -> "adv."
    PartOfSpeech.UNKNOWN -> ""
}

private fun DictionaryLookupState.wordOrNull(): String? = when (this) {
    DictionaryLookupState.Hidden -> null
    is DictionaryLookupState.PackRequired -> word
    is DictionaryLookupState.LookingUp -> word
    is DictionaryLookupState.NotFound -> word
    is DictionaryLookupState.Installing -> word
    is DictionaryLookupState.Failed -> word
    is DictionaryLookupState.Found -> entry.headword
}

@Composable
private fun NoteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var note by remember { mutableStateOf("") }

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

    AlertDialog(
        onDismissRequest = onStayHere,
        title = { Text(stringResource(R.string.reader_reading_position_prompt_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.reader_reading_position_prompt_body,
                    recentLocation,
                    currentLocation,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
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
    onReaderThemeChange: (ReaderTheme) -> Unit,
    onSideMarginChange: (Int) -> Unit,
    onVolumeKeysChange: (Boolean) -> Unit,
    onKeepAwakeChange: (Boolean) -> Unit,
    onShowHeadersChange: (Boolean) -> Unit,
    onShowFooterChange: (Boolean) -> Unit,
    onBionicReadingChange: (Boolean) -> Unit,
    onCreateBookmark: () -> Unit,
    onRefreshScreen: () -> Unit,
    searchResults: List<com.vayana.reader.api.SearchResult>,
    onSearchQueryChange: (String) -> Unit,
    onSearchResultClick: (com.vayana.reader.api.SearchResult) -> Unit,
    onClearSearch: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = readerChromeSurfaceColor(),
        shape = RoundedCornerShape(topStart = Radii.extraLarge, topEnd = Radii.extraLarge),
        tonalElevation = readerChromeElevation(),
        shadowElevation = readerChromeElevation(),
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
                    ReaderPanel.PROGRESS -> ProgressPanel(uiState = uiState, onProgressChange = onProgressChange)
                    ReaderPanel.STYLE -> StylePanel(
                        settings = settings,
                        usingCustomStyle = usingCustomStyle,
                        onUseCustomStyleChange = onUseCustomStyleChange,
                        onFontSizeChange = onFontSizeChange,
                        onLineHeightChange = onLineHeightChange,
                        onFontFamilyChange = onFontFamilyChange,
                        onReaderThemeChange = onReaderThemeChange,
                        onSideMarginChange = onSideMarginChange,
                        onVolumeKeysChange = onVolumeKeysChange,
                        onKeepAwakeChange = onKeepAwakeChange,
                        onShowHeadersChange = onShowHeadersChange,
                        onShowFooterChange = onShowFooterChange,
                        onBionicReadingChange = onBionicReadingChange,
                    )
                    ReaderPanel.NOTES -> NotesPanel(uiState = uiState, onAnnotationClick = onAnnotationClick)
                    ReaderPanel.SEARCH -> SearchPanel(
                        results = searchResults,
                        onQueryChange = onSearchQueryChange,
                        onResultClick = onSearchResultClick,
                        onClear = onClearSearch,
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
    val entries = (uiState as? ReaderUiState.Loaded)?.toc.orEmpty().flattenToc()
    LazyColumn(modifier = Modifier.heightIn(max = Sizes.contentMaxWidth)) {
        items(entries, key = { "${it.depth}:${it.entry.href}:${it.entry.title}" }) { item ->
            TextButton(onClick = { onOpenTocEntry(item.entry.href) }) {
                Text(
                    text = "${"  ".repeat(item.depth)}${item.entry.title}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun BookmarksPanel(
    uiState: ReaderUiState,
    onCreateBookmark: () -> Unit,
    onBookmarkClick: (Annotation) -> Unit,
) {
    val bookmarks = (uiState as? ReaderUiState.Loaded)
        ?.annotations
        .orEmpty()
        .filter { it.type == AnnotationType.BOOKMARK }
        .sortedByDescending { it.createdAt }
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
private fun ProgressPanel(uiState: ReaderUiState, onProgressChange: (Float) -> Unit) {
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
    }
}

@Composable
private fun NotesPanel(uiState: ReaderUiState, onAnnotationClick: (Annotation) -> Unit) {
    val annotations = (uiState as? ReaderUiState.Loaded)?.annotations.orEmpty()
    if (annotations.isEmpty()) {
        Text(
            text = stringResource(R.string.reader_notes_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.lg),
        )
        return
    }

    LazyColumn(modifier = Modifier.heightIn(max = Sizes.contentMaxWidth)) {
        items(annotations, key = { it.id }) { annotation ->
            TextButton(onClick = { onAnnotationClick(annotation) }) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = annotation.selectedText.ifBlank { stringResource(R.string.notes_bookmark_without_text) },
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

/** Persistent notes list shown beside the reader on wide-landscape (tablet) screens, per PROMPT2's "book and notes side by side" recommendation. */
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
                                        text = "“${annotation.selectedText}”",
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
    usingCustomStyle: Boolean,
    onUseCustomStyleChange: (Boolean) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onFontFamilyChange: (ReaderFontFamily) -> Unit,
    onReaderThemeChange: (ReaderTheme) -> Unit,
    onSideMarginChange: (Int) -> Unit,
    onVolumeKeysChange: (Boolean) -> Unit,
    onKeepAwakeChange: (Boolean) -> Unit,
    onShowHeadersChange: (Boolean) -> Unit,
    onShowFooterChange: (Boolean) -> Unit,
    onBionicReadingChange: (Boolean) -> Unit,
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
                    selected = settings.readerFontFamily == family,
                    onClick = { onFontFamilyChange(family) },
                    label = { Text(family.label()) },
                )
            }
        }

        ReaderSettingsSwitchRow(
            title = stringResource(R.string.settings_reader_bionic_reading_title),
            subtitle = stringResource(R.string.settings_reader_bionic_reading_subtitle),
            checked = settings.readerBionicReading,
            onCheckedChange = onBionicReadingChange,
        )

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
    displayProfile == DisplayProfile.E_INK -> Palette.EinkBackground
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
private const val MaxDisplayedDictionarySenses = 3
private const val DictionaryCardMaximumHeightFraction = 0.58f
private const val EnglishDictionaryDownloadUrl = "https://en-word.net/static/english-wordnet-2025.zip"
private const val VolumeKeyLongPressMillis = 500L
private const val EinkFullRefreshEveryPages = 6
private const val EinkFlashDurationMillis = 120L
private val readerHeaderTopPadding = Spacing.lg

/** Below this width, landscape stays a single reader pane - matches Library's tablet-landscape breakpoint. */
private const val TabletLandscapeMinWidthDp = 600
private const val ReaderPaneWeight = 0.65f
private const val NotesPaneWeight = 0.35f

private data class TocDisplayItem(val entry: TocEntry, val depth: Int)

private fun List<TocEntry>.flattenToc(depth: Int = 0): List<TocDisplayItem> =
    flatMap { entry -> listOf(TocDisplayItem(entry, depth)) + entry.children.flattenToc(depth + 1) }

private fun IntSetting.sliderRange(): ClosedFloatingPointRange<Float> = range.first.toFloat()..range.last.toFloat()

private fun IntSetting.sliderSteps(): Int = ((range.last - range.first) / step - 1).coerceAtLeast(0)

private fun FloatSetting.sliderSteps(): Int = (((range.endInclusive - range.start) / step).roundToInt() - 1).coerceAtLeast(0)

private fun Float.roundToStep(setting: IntSetting): Int = ((this / setting.step).roundToInt() * setting.step).coerceIn(setting.range)

private fun Float.roundToStep(setting: FloatSetting): Float =
    ((this / setting.step).roundToInt() * setting.step).coerceIn(setting.range.start, setting.range.endInclusive)

private fun Float.roundToTenth(): Float = (this * 10).roundToInt() / 10f

private fun Context.copyTextToClipboard(text: String) {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(getString(R.string.app_name), text)
    clipboardManager.setPrimaryClip(clip)
}

private fun Context.shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.reader_selection_share)))
}
