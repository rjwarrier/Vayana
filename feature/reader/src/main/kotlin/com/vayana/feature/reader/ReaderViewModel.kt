package com.vayana.feature.reader

import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.common.ApplicationScope
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.ReadingSessionRepository
import com.vayana.core.database.repository.VocabularyCardRepository
import com.vayana.core.database.repository.WordLookupStatRepository
import com.vayana.core.datastore.settings.ReaderFontFamily
import com.vayana.core.datastore.settings.ReaderHyphenation
import com.vayana.core.datastore.settings.ReaderTextAlign
import com.vayana.core.datastore.settings.ReaderTheme
import com.vayana.core.datastore.settings.SettingsRegistry
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.designsystem.tokens.Palette
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.ThemeMode
import com.vayana.core.filesystem.StorageRoots
import com.vayana.core.sync.progress.ReadingProgressOnlySyncer
import com.vayana.core.sync.progress.ReadingProgressSyncResult
import com.vayana.core.sync.progress.ReadingProgressSyncStatus
import com.vayana.dictionary.api.DictionaryEntry
import com.vayana.dictionary.api.DictionaryPackState
import com.vayana.dictionary.api.DictionaryRepository
import com.vayana.reader.api.BookEngine
import com.vayana.reader.api.BookHyphenation
import com.vayana.reader.api.BookStyle
import com.vayana.reader.api.BookSource
import com.vayana.reader.api.BookTextAlign
import com.vayana.reader.api.Locator
import com.vayana.reader.api.NavTarget
import com.vayana.reader.api.OpenBook
import com.vayana.reader.api.ReadTheme
import com.vayana.reader.api.ReaderAnnotation
import com.vayana.reader.api.ReaderAnnotationType
import com.vayana.reader.api.ReaderSelection
import com.vayana.reader.api.Footnote
import com.vayana.reader.api.FootnoteOpened
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.database.model.VocabularyCard
import com.vayana.core.database.model.isCommunityQuote
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Standing state of background reading-progress sync, rendered as a dot beside the reader clock.
 *
 * A state, not a one-shot event: routine auto-sync fires every few page turns, and a transient snackbar for
 * each one interrupts reading (and on E-Ink costs a full-screen refresh). [reason] carries the failure detail
 * that the old snackbar text used to show; the dot itself only signals that something needs attention, and the
 * specifics stay in the diagnostics log.
 */
sealed interface ReaderSyncStatus {
    /** Sync is off, or nothing has run yet this session - draw no dot at all. */
    data object Idle : ReaderSyncStatus
    data object Syncing : ReaderSyncStatus
    data object Synced : ReaderSyncStatus
    data class Failed(val reason: String?) : ReaderSyncStatus
}

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Loaded(
        val bookTitle: String,
        val bookAuthor: String? = null,
        val toc: List<com.vayana.reader.api.TocEntry>,
        val currentLocator: Locator?,
        val annotations: List<Annotation> = emptyList(),
        val selection: ReaderSelection? = null,
        /** Position to jump back to via [ReaderViewModel.returnToPreviousPosition], set right before a TOC/search/note jump. */
        val returnLocator: Locator? = null,
    ) : ReaderUiState
    data class Failed(val message: String) : ReaderUiState
}

/** Shown when a book is reopened after a while: how long since it was last read, and the reader's latest highlight in it. */
data class ReaderRecap(val awayMillis: Long, val highlight: String?, val dueWords: Int = 0)

/** Whether a looked-up word is already a vocabulary card, and whether it has been marked as known. */
enum class SavedWordStatus { NOT_SAVED, SAVED, KNOWN }

private fun VocabularyCard?.toSavedWordStatus(): SavedWordStatus = when {
    this == null -> SavedWordStatus.NOT_SAVED
    known -> SavedWordStatus.KNOWN
    else -> SavedWordStatus.SAVED
}

sealed interface DictionaryLookupState {
    data object Hidden : DictionaryLookupState
    data class PackRequired(val word: String) : DictionaryLookupState
    data class LookingUp(val word: String) : DictionaryLookupState
    data class Found(val entry: DictionaryEntry, val savedStatus: SavedWordStatus = SavedWordStatus.NOT_SAVED) : DictionaryLookupState
    data class NotFound(val word: String) : DictionaryLookupState
    data class Installing(val word: String) : DictionaryLookupState
    data class Failed(val word: String, val message: String) : DictionaryLookupState
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val annotationRepository: AnnotationRepository,
    private val storageRoots: StorageRoots,
    private val settingsRepository: SettingsRepository,
    private val dictionaryRepository: DictionaryRepository,
    private val wordLookupStatRepository: WordLookupStatRepository,
    private val readingSessionRepository: ReadingSessionRepository,
    private val vocabularyCardRepository: VocabularyCardRepository,
    private val readingProgressOnlySyncer: ReadingProgressOnlySyncer,
    private val dispatchers: DispatcherProvider,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    val bookId: Long = checkNotNull(savedStateHandle["bookId"])
    val targetLocator: String? = savedStateHandle["targetLocator"]

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState

    private val _dictionaryLookup = MutableStateFlow<DictionaryLookupState>(DictionaryLookupState.Hidden)
    val dictionaryLookup: StateFlow<DictionaryLookupState> = _dictionaryLookup

    private val _readingPositionPrompt = MutableStateFlow<ReadingPositionPrompt?>(null)
    val readingPositionPrompt: StateFlow<ReadingPositionPrompt?> = _readingPositionPrompt

    val settings: StateFlow<SettingsSnapshot> = settingsRepository.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsSnapshot())

    /** Non-null while this book has its own font/line-height/margin overrides (PROMPT2 per-book reading preferences). */
    private val _bookStyleOverride = MutableStateFlow<BookStyleOverride?>(null)

    val usingCustomStyle: StateFlow<Boolean> = _bookStyleOverride
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** [settings] with this book's overrides layered on top - what the reader actually renders with and the Style panel shows. */
    val effectiveSettings: StateFlow<SettingsSnapshot> = combine(settings, _bookStyleOverride) { snapshot, override ->
        if (override == null) {
            snapshot
        } else {
            snapshot.copy(
                readerFontSizePercent = override.fontSizePercent ?: snapshot.readerFontSizePercent,
                readerLineHeight = override.lineHeight ?: snapshot.readerLineHeight,
                readerFontFamily = override.fontFamily ?: snapshot.readerFontFamily,
                readerCustomFontId = if (override.fontFamily == null) snapshot.readerCustomFontId else null,
                readerSideMarginPercent = override.sideMarginPercent ?: snapshot.readerSideMarginPercent,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsSnapshot())

    val recentLookups: StateFlow<List<String>> = wordLookupStatRepository.observeRecent(RecentLookupsLimit)
        .map { stats -> stats.map { it.word } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var boundEngine: BookEngine? = null
    private var bookOpen = false

    /** Bumped each time the reader must build a fresh engine because the WebView's renderer died. */
    private val _engineGeneration = MutableStateFlow(0)
    val engineGeneration: StateFlow<Int> = _engineGeneration
    private val rendererRestartPolicy = RendererRestartPolicy()

    /** Where to reopen after a renderer crash: the position at the crash beats the screen's original target. */
    private var restartCfi: String? = null
    private val engineJobs = mutableListOf<Job>()
    private var dictionaryLookupJob: Job? = null
    private var dictionaryInstallJob: Job? = null
    private var pendingDictionaryWord: String? = null
    private var dictionaryPickerActive = false
    private var lastUsedHighlightColor: String = DefaultAnnotationColor
    private var autoMarkedSelectionCfi: String? = null
    private val pageTurnAutoSyncGate = PageTurnAutoSyncGate { settings.value.readingAutoSyncEveryPages }
    private var autoProgressSyncJob: Job? = null
    private var autoProgressSyncRequested = false
    private val _syncStatus = MutableStateFlow<ReaderSyncStatus>(ReaderSyncStatus.Idle)
    val syncStatus: StateFlow<ReaderSyncStatus> = _syncStatus

    private val _footnote = MutableStateFlow<Footnote?>(null)
    val footnote: StateFlow<Footnote?> = _footnote

    private val _returnRecap = MutableStateFlow<ReaderRecap?>(null)
    val returnRecap: StateFlow<ReaderRecap?> = _returnRecap

    private val readAloudPlayer = ReadAloudPlayer(
        output = AndroidSpeechOutput(appContext),
        scope = viewModelScope,
        engine = { boundEngine?.takeIf { bookOpen } },
        onSpeaking = ::onSpeaking,
        focus = AndroidPlaybackFocus(appContext),
    )
    private var lastSpeechInteractionAt = 0L
    val readAloud: StateFlow<ReadAloudState> = readAloudPlayer.state
    internal val readAloudVoices: StateFlow<List<SpeechVoiceOption>> = readAloudPlayer.voices
    internal val readAloudEngines: StateFlow<List<SpeechEngineOption>> = readAloudPlayer.engines

    private val _chapterWords = MutableStateFlow<ChapterWordsState>(ChapterWordsState.Idle)
    val chapterWords: StateFlow<ChapterWordsState> = _chapterWords
    private var chapterWordsJob: Job? = null
    private var lastReaderWrittenLocator: String? = null
    private var locatorPersistJob: Job? = null

    /** The locator the debounced write still owes the database, or null once that write has landed. */
    private var pendingLocatorWrite: PendingLocatorWrite? = null
    private var pendingRemoteReadingPosition: SavedReadingPosition? = null
    private val readingPositionPromptDecider = ReadingPositionPromptDecider()
    private val _activeReadingSessionSeconds = MutableStateFlow(0L)
    val activeReadingSessionSeconds: StateFlow<Long> = _activeReadingSessionSeconds

    /** Called once the [BookEngine] exists (i.e. once the WebView has been created by the Compose factory). */
    @OptIn(FlowPreview::class)
    fun bindEngine(engine: BookEngine) {
        if (boundEngine === engine) return
        cancelEngineJobs()
        boundEngine?.close()
        boundEngine = engine
        bookOpen = false
        _readingPositionPrompt.value = null
        pendingRemoteReadingPosition = null
        lastReaderWrittenLocator = null

        engineJobs += viewModelScope.launch {
            val book = bookRepository.getById(bookId)
            if (book == null) {
                _uiState.value = ReaderUiState.Failed("Book not found")
                return@launch
            }
            _bookStyleOverride.value = book.toStyleOverrideOrNull()
            // Read before recordBookOpened below replaces it; a jump to a note or search hit isn't a "return".
            val restoredCfi = restartCfi?.takeIf { it.isNotBlank() }
            restartCfi = null
            val openTarget = restoredCfi ?: targetLocator
            val previousReadAt = book.lastReadAt.takeIf { openTarget.isNullOrBlank() && book.readingPercent > 0f }
            if (book.fileAvailability == BookFileAvailability.CLOUD_ONLY) {
                _uiState.value = ReaderUiState.Failed("This book is in your cloud library. Download support is being wired next.")
                return@launch
            }
            if (book.fileAvailability != BookFileAvailability.LOCAL || book.filePath.isBlank()) {
                _uiState.value = ReaderUiState.Failed("This book file is not available on this device.")
                return@launch
            }

            val localFile = storageRoots.resolve(book.filePath)
            if (!withContext(dispatchers.io) { localFile.isFile }) {
                _uiState.value = ReaderUiState.Failed("This book file is missing from this device.")
                return@launch
            }

            val source = BookSource(localFile.absolutePath)
            val savedLocator = book.lastLocator?.takeIf { it.isNotBlank() }
            val initialLocatorString = openTarget?.takeIf { it.isNotBlank() }
            val resumeLocator = initialLocatorString?.let {
                Locator(cfi = it, href = null, progression = book.readingPercent, chapterTitle = null)
            }

            engine.open(source, resumeLocator)
                .onSuccess { openBook: OpenBook ->
                    bookOpen = true
                    // A reopen after a renderer crash is the same reading session, not a new open.
                    if (restoredCfi == null) viewModelScope.launch { bookRepository.recordBookOpened(bookId) }
                    if (readerResumed) onResume()
                    applyReaderStyle(engine, effectiveSettings.value)
                    _uiState.value = ReaderUiState.Loaded(
                        bookTitle = openBook.title,
                        bookAuthor = book.author,
                        toc = openBook.toc,
                        currentLocator = resumeLocator,
                    )
                    observeAnnotations(engine)
                    showReturnRecap(previousReadAt)
                    if (!openTarget.isNullOrBlank()) {
                        engine.goTo(NavTarget.ToLocator(Locator(cfi = openTarget, href = null, progression = 0f, chapterTitle = null)))
                    } else if (savedLocator != null && book.readingPercent > 0.001f) {
                        engine.goTo(NavTarget.ToFraction(book.readingPercent.coerceIn(0f, 0.999f)))
                    }
                    observeRemoteReadingProgress()
                    // The sync merge/prompt design intends a conflict to "surface... when the
                    // book opens" - previously this only got checked lazily, once the page-turn
                    // auto-sync gate happened to fire a few pages in. Check once immediately so a
                    // conflict that existed before this session started is caught up front,
                    // instead of reading a stretch of pages on a stale position first.
                    requestAutoProgressSync()
                }
                .onFailure { throwable ->
                    _uiState.value = ReaderUiState.Failed(throwable.message ?: "Could not open book")
                }
        }

        engineJobs += viewModelScope.launch {
            engine.location.filterNotNull().collect { locator ->
                _uiState.update { current ->
                    if (current is ReaderUiState.Loaded) current.copy(currentLocator = locator) else current
                }
                refreshReadingPositionPromptCurrentLocation(locator)
                pendingRemoteReadingPosition?.let { remotePosition ->
                    pendingRemoteReadingPosition = null
                    showRemoteReadingPositionPromptIfNeeded(remotePosition, locator)
                }
                if (_readingPositionPrompt.value == null) {
                    locator.cfi?.let { cfi ->
                        lastReaderWrittenLocator = cfi
                        // The WebView bridge can fire several 'relocate' events for a single page
                        // turn in quick succession; debounce so each one doesn't hit Room.
                        val write = PendingLocatorWrite(cfi, locator.progression)
                        pendingLocatorWrite = write
                        locatorPersistJob?.cancel()
                        locatorPersistJob = viewModelScope.launch {
                            delay(LocatorPersistDebounceMillis)
                            bookRepository.updateLocator(bookId, write.cfi, write.progression, settings.value.finishedFraction)
                            if (pendingLocatorWrite === write) pendingLocatorWrite = null
                        }
                    }
                    onPageMoved(locator)
                }
            }
        }

        engineJobs += viewModelScope.launch {
            engine.events().collect { event ->
                when (event) {
                    is com.vayana.reader.api.EngineEvent.SelectionChanged -> {
                        _uiState.update { current ->
                            if (current is ReaderUiState.Loaded) current.copy(selection = event.selection) else current
                        }
                        lookupSelection(event.selection?.selectedText)
                        maybeAutoMarkSelection(event.selection)
                    }
                    is com.vayana.reader.api.EngineEvent.SearchCompleted -> {
                        if (event.query == lastSearchQuery) _searchResults.value = event.results
                    }
                    is FootnoteOpened -> _footnote.value = event.footnote
                    com.vayana.reader.api.EngineEvent.RendererGone -> onRendererGone()
                    is com.vayana.reader.api.EngineEvent.Error,
                    is com.vayana.reader.api.EngineEvent.Relocated,
                    -> Unit
                }
            }
        }

        engineJobs += viewModelScope.launch {
            // Only what the page is drawn from: a brightness swipe or a read-aloud speed change rewrites the settings too,
            // and re-applying an identical style still re-lays-out the whole chapter.
            effectiveSettings
                .map { snapshot -> snapshot.toBookStyle() to snapshot.readTheme }
                .distinctUntilChanged()
                .debounce(StyleUpdateDebounceMillis)
                .collectLatest { (style, theme) ->
                    if (bookOpen) engine.applyStyle(style, theme)
                }
        }
    }

    /**
     * The WebView's renderer crashed or was killed (often out of memory), so the engine is dead. Remember where the
     * reader was and ask the screen for a fresh WebView; [bindEngine] then reopens the book at that spot.
     */
    private fun onRendererGone() {
        val position = (_uiState.value as? ReaderUiState.Loaded)?.currentLocator?.cfi
        bookOpen = false
        readAloudPlayer.stop()
        flushPendingLocatorWrite()
        if (!rendererRestartPolicy.allowRestart(System.currentTimeMillis())) {
            _uiState.value = ReaderUiState.Failed("The reader stopped working repeatedly. Close the book and open it again.")
            return
        }
        restartCfi = position
        _uiState.value = ReaderUiState.Loading
        _engineGeneration.update { it + 1 }
    }

    fun releaseEngine(engine: BookEngine) {
        if (boundEngine === engine) {
            readAloudPlayer.stop()
            cancelEngineJobs()
            boundEngine = null
            bookOpen = false
        }
        engine.close()
    }

    private val _searchResults = MutableStateFlow<List<com.vayana.reader.api.SearchResult>>(emptyList())
    val searchResults: StateFlow<List<com.vayana.reader.api.SearchResult>> = _searchResults
    private var lastSearchQuery: String = ""
    private var searchJob: Job? = null

    fun search(query: String) {
        val trimmed = query.trim()
        lastSearchQuery = trimmed
        searchJob?.cancel()
        if (trimmed.isEmpty()) {
            _searchResults.value = emptyList()
            viewModelScope.launch { boundEngine?.clearSearch() }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SearchDebounceMillis)
            boundEngine?.search(trimmed)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        lastSearchQuery = ""
        _searchResults.value = emptyList()
        viewModelScope.launch { boundEngine?.clearSearch() }
    }

    fun openSearchResult(result: com.vayana.reader.api.SearchResult) {
        pushReturnLocator()
        dispatch(
            NavTarget.ToLocator(
                Locator(cfi = result.cfi, href = null, progression = 0f, chapterTitle = result.chapterTitle),
            ),
        )
    }

    fun nextPage() = dispatch(NavTarget.NextPage)
    fun previousPage() = dispatch(NavTarget.PreviousPage)

    fun openTocEntry(href: String) {
        pushReturnLocator()
        dispatch(NavTarget.ToHref(href))
    }

    fun goToProgress(fraction: Float) = dispatch(NavTarget.ToFraction(fraction.coerceIn(0f, 1f)))

    fun openAnnotation(annotation: Annotation) {
        pushReturnLocator()
        dispatch(
            NavTarget.ToLocator(
                Locator(
                    cfi = annotation.locator,
                    href = annotation.chapterHref,
                    progression = 0f,
                    chapterTitle = annotation.chapterTitle,
                ),
            ),
        )
    }

    /** Records the position to return to right before a TOC/search/note jump moves away from it. */
    private fun pushReturnLocator() {
        val state = uiState.value as? ReaderUiState.Loaded ?: return
        val current = state.currentLocator ?: return
        _uiState.update { existing ->
            if (existing is ReaderUiState.Loaded) existing.copy(returnLocator = current) else existing
        }
    }

    /** Jumps back to the position recorded by [pushReturnLocator], then clears it. */
    fun returnToPreviousPosition() {
        val state = uiState.value as? ReaderUiState.Loaded ?: return
        val target = state.returnLocator ?: return
        _uiState.update { existing ->
            if (existing is ReaderUiState.Loaded) existing.copy(returnLocator = null) else existing
        }
        dispatch(NavTarget.ToLocator(target))
    }

    fun updateFontSize(percent: Int) {
        if (_bookStyleOverride.value != null) {
            updateBookOverride { it.copy(fontSizePercent = percent) }
        } else {
            viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderFontSize, percent) }
        }
    }

    fun updateLineHeight(lineHeight: Float) {
        if (_bookStyleOverride.value != null) {
            updateBookOverride { it.copy(lineHeight = lineHeight) }
        } else {
            viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderLineHeight, lineHeight) }
        }
    }

    fun updateFontFamily(fontFamily: ReaderFontFamily) {
        if (_bookStyleOverride.value != null) {
            updateBookOverride { it.copy(fontFamily = fontFamily) }
        } else {
            viewModelScope.launch {
                settingsRepository.updateReaderCustomFontId(null)
                settingsRepository.update(SettingsRegistry.ReaderFontFamily, fontFamily)
            }
        }
    }

    fun updateCustomFont(fontId: String?) {
        if (_bookStyleOverride.value != null) {
            updateBookOverride { it.copy(fontFamily = null) }
        }
        viewModelScope.launch { settingsRepository.updateReaderCustomFontId(fontId) }
    }

    fun updateReaderTheme(theme: ReaderTheme) {
        viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderTheme, theme) }
    }

    fun updateSideMargin(percent: Int) {
        if (_bookStyleOverride.value != null) {
            updateBookOverride { it.copy(sideMarginPercent = percent) }
        } else {
            viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderSideMargin, percent) }
        }
    }

    /** Turns per-book style overrides on (seeded from the current effective style) or off (reset to the global default). */
    fun setUseCustomStyle(enabled: Boolean) {
        if (enabled) {
            val snapshot = effectiveSettings.value
            updateBookOverride {
                BookStyleOverride(
                    fontSizePercent = snapshot.readerFontSizePercent,
                    lineHeight = snapshot.readerLineHeight,
                    fontFamily = if (snapshot.readerCustomFontId == null) snapshot.readerFontFamily else null,
                    sideMarginPercent = snapshot.readerSideMarginPercent,
                )
            }
        } else {
            _bookStyleOverride.value = null
            viewModelScope.launch { bookRepository.clearReaderPrefs(bookId) }
        }
    }

    private fun updateBookOverride(transform: (BookStyleOverride) -> BookStyleOverride) {
        val updated = transform(_bookStyleOverride.value ?: BookStyleOverride())
        _bookStyleOverride.value = updated
        viewModelScope.launch {
            bookRepository.updateReaderPrefs(
                id = bookId,
                fontSizePercent = updated.fontSizePercent,
                lineHeight = updated.lineHeight,
                fontFamily = updated.fontFamily?.name,
                sideMarginPercent = updated.sideMarginPercent,
            )
        }
    }

    fun updateVolumeKeys(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderVolumeKeys, enabled) }
    }

    fun updateKeepAwake(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderKeepAwake, enabled) }
    }

    fun updateShowHeaders(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderShowHeaders, enabled) }
    }

    fun updateShowFooter(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderShowFooter, enabled) }
    }

    fun updateBionicReading(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderBionicReading, enabled) }
    }

    fun createHighlight(colorKey: String = DefaultAnnotationColor) {
        lastUsedHighlightColor = colorKey
        createAnnotation(type = AnnotationType.HIGHLIGHT, colorKey = colorKey, readerNote = null)
    }

    private fun maybeAutoMarkSelection(selection: ReaderSelection?) {
        if (selection == null) {
            autoMarkedSelectionCfi = null
            return
        }
        // A double-tapped word is being looked up, not marked.
        if (!settings.value.readerAutoMarkSelection || selection.isWordLookup || selection.cfi == autoMarkedSelectionCfi) return
        autoMarkedSelectionCfi = selection.cfi
        createHighlight(lastUsedHighlightColor)
    }

    fun dismissFootnote() {
        _footnote.value = null
    }

    /** Leaves the footnote popup for the note itself, with "back to where I was" ready. */
    fun openFootnoteTarget() {
        val href = _footnote.value?.href?.takeIf { it.isNotBlank() } ?: return
        _footnote.value = null
        pushReturnLocator()
        dispatch(NavTarget.ToHref(href))
    }

    fun dismissReturnRecap() {
        _returnRecap.value = null
    }

    /**
     * Listening counts as reading time, just as turning pages does. Throttled: each interaction can save reading time
     * and restart the session ticker, which a new sentence every few seconds would do far too often.
     */
    private fun onSpeaking() {
        val now = System.currentTimeMillis()
        if (now - lastSpeechInteractionAt < SpeechInteractionIntervalMillis) return
        lastSpeechInteractionAt = now
        onReaderInteraction()
    }

    /** Starts read aloud at the top of the page, or, with [fromSelection], at the sentence holding the selection. */
    fun startReadAloud(fromSelection: Boolean = false) {
        if (!settings.value.readerAudioFeaturesEnabled) return
        _returnRecap.value = null
        val selectionCfi = if (fromSelection) (uiState.value as? ReaderUiState.Loaded)?.selection?.cfi else null
        // Choosing a new starting point while already reading restarts from there.
        if (selectionCfi != null) readAloudPlayer.stop()
        readAloudPlayer.start(
            rate = settings.value.readAloudRate,
            pitch = settings.value.readAloudPitch,
            voiceName = settings.value.readAloudVoiceName,
            fromCfi = selectionCfi,
            speechEngine = settings.value.readAloudEngine,
            wordHighlight = settings.value.displayProfile != DisplayProfile.E_INK,
        )
        if (selectionCfi != null) clearSelection()
    }

    fun toggleReadAloud() = readAloudPlayer.togglePlayback()

    fun skipReadAloudSentence(sentences: Int) = readAloudPlayer.skip(sentences)

    fun stopReadAloud() = readAloudPlayer.stop()

    fun cycleReadAloudSleepTimer() = readAloudPlayer.cycleSleepTimer()

    fun dismissReadAloudVoiceMissing() = readAloudPlayer.dismissVoiceMissing()

    fun updateReadAloudRate(rate: Float) {
        val next = rate.coerceIn(SettingsRegistry.ReadAloudRate.range)
        readAloudPlayer.setRate(next)
        viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReadAloudRate, next) }
    }

    fun updateReadAloudPitch(pitch: Float) {
        val next = pitch.coerceIn(SettingsRegistry.ReadAloudPitch.range)
        readAloudPlayer.setPitch(next)
        viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReadAloudPitch, next) }
    }

    fun loadReadAloudVoices() {
        // The panel may open before anything has been read: make sure the voices listed are the chosen engine's.
        readAloudPlayer.useEngine(settings.value.readAloudEngine)
        readAloudPlayer.loadVoices()
    }

    fun updateReadAloudEngine(name: String) {
        readAloudPlayer.useEngine(name)
        readAloudPlayer.loadVoices()
        viewModelScope.launch {
            settingsRepository.update(SettingsRegistry.ReadAloudEngine, name)
            // Voice names belong to their engine; the new engine starts on its own default voice.
            settingsRepository.update(SettingsRegistry.ReadAloudVoiceName, "")
        }
    }

    fun updateReadAloudVoice(name: String) {
        readAloudPlayer.setVoice(name)
        viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReadAloudVoiceName, name) }
    }

    fun updateBrightness(percent: Int) {
        viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderBrightness, percent) }
    }

    fun updateWarmLight(percent: Int) {
        viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderWarmLight, percent) }
    }

    /** Re-runs a dictionary lookup for an arbitrary word (e.g. tapping a synonym), independent of any live selection. */
    fun lookupWord(word: String) {
        lookupSelection(word)
    }

    fun saveLookupAsNote(entry: DictionaryEntry) {
        val definition = entry.senses.firstOrNull()?.definition ?: return
        createNote("${entry.headword}: $definition")
    }

    /** Saves a dictionary lookup as a vocabulary flashcard, using the current selection as the example sentence. */
    fun saveLookupAsVocabularyCard(entry: DictionaryEntry) {
        val definition = entry.senses.firstOrNull()?.definition ?: return
        val state = uiState.value as? ReaderUiState.Loaded
        val sentence = state?.selection?.selectedText?.takeIf { it.isNotBlank() && !it.equals(entry.headword, ignoreCase = true) }
        viewModelScope.launch {
            vocabularyCardRepository.save(
                word = entry.headword,
                definition = definition,
                sentence = sentence,
                bookId = bookId,
                bookTitle = state?.bookTitle,
            )
            _dictionaryLookup.update { current ->
                if (current is DictionaryLookupState.Found && current.entry.headword == entry.headword) {
                    current.copy(savedStatus = SavedWordStatus.SAVED)
                } else {
                    current
                }
            }
        }
    }

    /**
     * Finds the likeliest unfamiliar words in the chapter on screen - long, rare in the chapter, in the dictionary and
     * not marked as known - with their first definitions.
     */
    fun loadChapterWords() {
        val engine = boundEngine?.takeIf { bookOpen } ?: return
        chapterWordsJob?.cancel()
        chapterWordsJob = viewModelScope.launch {
            if (dictionaryRepository.englishPackState.value !is DictionaryPackState.Installed) {
                _chapterWords.value = ChapterWordsState.DictionaryRequired
                return@launch
            }
            _chapterWords.value = ChapterWordsState.Loading
            val cardsByWord = vocabularyCardRepository.observeAll().first().associateBy { it.word.lowercase() }
            val knownWords = cardsByWord.filterValues { it.known }.keys
            val candidates = unusualWordCandidates(engine.chapterWordCounts(MinUnusualWordLength), knownWords)
            // One hop to IO for all lookups, stopping as soon as there are enough words.
            val words = withContext(dispatchers.io) {
                buildList<ChapterWord> {
                    for (candidate in candidates) {
                        if (size >= MaxChapterWords) break
                        val entry = runCatchingCancellable { dictionaryRepository.lookupEnglish(candidate) }.getOrNull() ?: continue
                        val definition = entry.senses.firstOrNull()?.definition ?: continue
                        if (any { it.word.equals(entry.headword, ignoreCase = true) }) continue
                        add(ChapterWord(word = entry.headword, definition = definition, saved = entry.headword.lowercase() in cardsByWord))
                    }
                }
            }
            _chapterWords.value = ChapterWordsState.Ready(words)
        }
    }

    fun saveChapterWord(word: ChapterWord) {
        val bookTitle = (uiState.value as? ReaderUiState.Loaded)?.bookTitle
        viewModelScope.launch {
            vocabularyCardRepository.save(word = word.word, definition = word.definition, sentence = null, bookId = bookId, bookTitle = bookTitle)
            _chapterWords.update { state ->
                if (state is ChapterWordsState.Ready) {
                    state.copy(words = state.words.map { if (it.word == word.word) it.copy(saved = true) else it })
                } else {
                    state
                }
            }
        }
    }

    fun createUnderline() {
        createAnnotation(type = AnnotationType.UNDERLINE, readerNote = null)
    }

    fun createNote(note: String) {
        createAnnotation(type = AnnotationType.NOTE, readerNote = note)
    }

    /** Edits an existing annotation's note text in place - used by the tablet-landscape side-by-side notes panel. */
    fun updateAnnotationNote(annotation: Annotation, note: String) {
        viewModelScope.launch {
            annotationRepository.update(annotation.copy(readerNote = note.takeIf { it.isNotBlank() }))
        }
    }

    fun createBookmark() {
        val state = uiState.value as? ReaderUiState.Loaded ?: return
        val locator = state.currentLocator ?: return
        val cfi = locator.cfi?.takeIf { it.isNotBlank() } ?: return
        if (state.annotations.any { it.type == AnnotationType.BOOKMARK && it.locator == cfi }) return

        viewModelScope.launch {
            annotationRepository.create(
                bookId = bookId,
                type = AnnotationType.BOOKMARK,
                colorKey = DefaultBookmarkColor,
                locator = cfi,
                chapterTitle = locator.chapterTitle,
                chapterHref = locator.href,
                selectedText = "",
                readerNote = null,
            )
        }
    }

    fun clearSelection() {
        val engine = boundEngine ?: return
        viewModelScope.launch {
            engine.clearSelection()
            dictionaryLookupJob?.cancel()
            pendingDictionaryWord = null
            _dictionaryLookup.value = DictionaryLookupState.Hidden
            _uiState.update { current ->
                if (current is ReaderUiState.Loaded) current.copy(selection = null) else current
            }
        }
    }

    fun prepareDictionaryInstall() {
        val word = currentLookupWord() ?: return
        pendingDictionaryWord = word
        dictionaryPickerActive = true
        _dictionaryLookup.value = DictionaryLookupState.Installing(word)
    }

    fun cancelDictionaryInstall() {
        dictionaryPickerActive = false
        pendingDictionaryWord?.let { word ->
            _dictionaryLookup.value = DictionaryLookupState.PackRequired(word)
        }
    }

    fun installEnglishDictionary(sourceUri: String) {
        val word = currentLookupWord() ?: return
        dictionaryPickerActive = false
        // Installing supersedes any pending lookup, but must not itself be cancellable by
        // later selection changes / highlight taps, which only ever touch dictionaryLookupJob.
        dictionaryLookupJob?.cancel()
        dictionaryInstallJob?.cancel()
        dictionaryInstallJob = viewModelScope.launch {
            _dictionaryLookup.value = DictionaryLookupState.Installing(word)
            try {
                dictionaryRepository.installEnglish(sourceUri)
            } catch (throwable: CancellationException) {
                throw throwable
            } catch (throwable: Throwable) {
                _dictionaryLookup.value = DictionaryLookupState.Failed(
                    word,
                    throwable.message ?: "Dictionary installation failed",
                )
                return@launch
            }
            dictionaryInstallJob = null
            lookupSelection(word)
        }
    }

    private fun dispatch(target: NavTarget) {
        val engine = boundEngine ?: return
        viewModelScope.launch { engine.goTo(target) }
    }

    private suspend fun applyReaderStyle(engine: BookEngine, snapshot: SettingsSnapshot) {
        engine.applyStyle(style = snapshot.toBookStyle(), theme = snapshot.readTheme)
    }

    private fun observeAnnotations(engine: BookEngine) {
        engineJobs += viewModelScope.launch {
            // Room's Flow re-emits whenever the annotations table is invalidated by any write
            // (even to a different book), not just when this book's rows actually changed - skip
            // the JS round trip when the content is identical to what we last rendered.
            annotationRepository.observeForBook(bookId).distinctUntilChanged().collect { annotations ->
                engine.renderAnnotations(annotations.mapNotNull { it.toReaderAnnotation() })
                _uiState.update { current ->
                    if (current is ReaderUiState.Loaded) current.copy(annotations = annotations) else current
                }
            }
        }
    }

    private fun observeRemoteReadingProgress() {
        engineJobs += viewModelScope.launch {
            bookRepository.remoteReadingProgressApplied.collect { remoteProgress ->
                if (remoteProgress.bookId != bookId) return@collect
                // Don't silently swap out a conflict the user hasn't responded to yet - a second
                // remote update arriving before they've chosen would either overwrite the prompt
                // they're mid-decision on, or race with whichever they pick. Rare (needs two other
                // devices pushing in quick succession), but if it still hasn't resolved by the
                // next auto-sync tick, it'll be re-evaluated fresh then anyway.
                if (_readingPositionPrompt.value != null) return@collect
                val remotePosition = SavedReadingPosition(
                    locator = remoteProgress.locator,
                    progress = remoteProgress.readingPercent,
                    version = remoteProgress.version,
                )
                val currentLocator = (uiState.value as? ReaderUiState.Loaded)?.currentLocator
                if (currentLocator == null) {
                    pendingRemoteReadingPosition = remotePosition
                } else {
                    showRemoteReadingPositionPromptIfNeeded(remotePosition, currentLocator)
                }
            }
        }
    }

    private fun showRemoteReadingPositionPromptIfNeeded(
        remotePosition: SavedReadingPosition,
        currentLocator: Locator,
    ) {
        // Sync applies remote-newer progress unconditionally; this only decides whether to
        // interrupt the user about it. A remote echo of this reader's own last saved locator
        // (routine solo-reading auto-sync) should stay quiet - only a genuinely different
        // position, most often from another device, is worth asking about.
        readingPositionPromptDecider.promptForRemote(
            remotePosition = remotePosition,
            currentLocator = currentLocator,
            lastReaderWrittenLocator = lastReaderWrittenLocator,
        )?.let { prompt -> _readingPositionPrompt.value = prompt }
    }

    private fun refreshReadingPositionPromptCurrentLocation(locator: Locator) {
        _readingPositionPrompt.update { prompt ->
            prompt?.copy(
                currentProgress = locator.progression,
                currentPage = locator.currentPage,
            )
        }
    }

    private val readingTimeTracker = ReadingSessionContinuationStore.take(bookId) ?: ReadingTimeTracker(
        idleTimeoutMillis = IdleSessionTimeoutMs,
        continuationGraceMillis = SessionContinuationGraceMs,
    )
    private var readerResumed = false
    private var trackingJob: Job? = null

    init {
        viewModelScope.launch {
            combine(readAloud, uiState) { playback, readerState ->
                val loaded = readerState as? ReaderUiState.Loaded
                // Progress only matters while reading aloud; when idle the snapshot must not change with every page.
                if (!playback.active) {
                    ReadAloudNotificationSnapshot.Inactive
                } else {
                    ReadAloudNotificationSnapshot(
                        active = true,
                        playing = playback.playing,
                        bookTitle = loaded?.bookTitle,
                        progressPercent = readAloudProgressPercent(loaded?.currentLocator?.progression),
                    )
                }
            }
                .distinctUntilChanged()
                .collectLatest { notification ->
                    if (notification.active) {
                        ReadAloudForegroundService.show(
                            context = appContext,
                            playing = notification.playing,
                            bookTitle = notification.bookTitle,
                            progressPercent = notification.progressPercent,
                        )
                    } else {
                        ReadAloudForegroundService.stop(appContext)
                    }
                }
        }
        viewModelScope.launch {
            ReadAloudNotificationCommands.commands.collect { command ->
                when (command) {
                    ReadAloudCommand.PLAY -> readAloudPlayer.play()
                    ReadAloudCommand.PAUSE -> readAloudPlayer.pause()
                    ReadAloudCommand.NEXT -> readAloudPlayer.skip(1)
                    ReadAloudCommand.PREVIOUS -> readAloudPlayer.skip(-1)
                    ReadAloudCommand.STOP -> readAloudPlayer.stop()
                }
            }
        }
        viewModelScope.launch {
            settings
                .map { it.readerAudioFeaturesEnabled }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    if (!enabled) readAloudPlayer.stop()
                }
        }
        viewModelScope.launch {
            ReadingSessionContinuationStore.drainExpired(System.currentTimeMillis()).forEach { (expiredBookId, update) ->
                update.session?.let { session ->
                    readingSessionRepository.record(
                        bookId = expiredBookId,
                        startedAt = session.startedAt,
                        endedAt = session.endedAt,
                        durationSeconds = session.durationSeconds,
                    )
                }
            }
        }
        _activeReadingSessionSeconds.value = readingTimeTracker.elapsedSeconds
    }

    fun onResume() {
        readerResumed = true
        persistReadingTime(readingTimeTracker.flush(System.currentTimeMillis()))
    }

    fun onPause() {
        readerResumed = false
        persistReadingTime(readingTimeTracker.pause(System.currentTimeMillis()))
        trackingJob?.cancel()
        trackingJob = null
        flushPendingLocatorWrite()
    }

    /** Guarantees the last-seen position is saved immediately, bypassing the debounce, when the
     *  reader is about to go away (backgrounded or destroyed) and might not get another event. */
    private fun flushPendingLocatorWrite() {
        locatorPersistJob?.cancel()
        locatorPersistJob = null
        // Gate on the owed write, never on the debounce job's isActive: onCleared() runs *after* ViewModel.clear()
        // has already cancelled viewModelScope, so the job is always inactive there and an isActive check would
        // skip exactly the flush this function exists for.
        val write = pendingLocatorWrite ?: return
        pendingLocatorWrite = null
        // applicationScope, not viewModelScope: same reason - a launch on the cancelled viewModelScope from
        // onCleared() would silently never run its body.
        applicationScope.launch {
            bookRepository.updateLocator(bookId, write.cfi, write.progression, settings.value.finishedFraction)
        }
    }

    fun acceptReadingPositionPrompt() {
        val prompt = _readingPositionPrompt.value ?: return
        _readingPositionPrompt.value = null
        lastReaderWrittenLocator = prompt.targetLocator
        dispatch(
            NavTarget.ToLocator(
                Locator(cfi = prompt.targetLocator, href = null, progression = prompt.targetProgress, chapterTitle = null),
            ),
        )
    }

    fun dismissReadingPositionPrompt() {
        _readingPositionPrompt.value = null
        val currentLocator = (uiState.value as? ReaderUiState.Loaded)?.currentLocator ?: return
        val currentCfi = currentLocator.cfi?.takeIf { it.isNotBlank() } ?: return
        lastReaderWrittenLocator = currentCfi
        viewModelScope.launch {
            bookRepository.updateLocator(bookId, currentCfi, currentLocator.progression, settings.value.finishedFraction)
        }
    }

    private suspend fun onPageMoved(locator: Locator) {
        if (!readerResumed || !bookOpen) return
        if (pageTurnAutoSyncGate.onLocator(locator)) {
            requestAutoProgressSync()
        }
    }

    fun onReaderInteraction() {
        if (!readerResumed || !bookOpen) return
        persistReadingTime(readingTimeTracker.interact(System.currentTimeMillis()))
        startReadingTimeTicker()
    }

    private fun requestAutoProgressSync() {
        val runningJob = autoProgressSyncJob
        if (runningJob?.isActive == true) {
            autoProgressSyncRequested = true
            return
        }
        autoProgressSyncJob = viewModelScope.launch {
            do {
                autoProgressSyncRequested = false
                _syncStatus.value = ReaderSyncStatus.Syncing
                val result = readingProgressOnlySyncer.syncReadingProgress()
                // Throttled means we didn't actually check anything; looping immediately would
                // just spin until the window clears. A later page turn will trigger a fresh call.
                if (result.status == ReadingProgressSyncStatus.THROTTLED) {
                    // Nothing ran, so the dot must not keep claiming a sync is in flight. Fall back to
                    // whatever the last real attempt concluded rather than inventing a fresh verdict.
                    _syncStatus.value = lastSettledSyncStatus
                    break
                }
                updateSyncStatus(result)
            } while (autoProgressSyncRequested)
        }
    }

    /** The last non-transient verdict, so a throttled or skipped run can restore it instead of showing Syncing forever. */
    private var lastSettledSyncStatus: ReaderSyncStatus = ReaderSyncStatus.Idle

    private fun updateSyncStatus(result: ReadingProgressSyncResult) {
        val status: ReaderSyncStatus = when (result.status) {
            ReadingProgressSyncStatus.PUSHED,
            ReadingProgressSyncStatus.PULLED,
            ReadingProgressSyncStatus.SYNCED,
            ReadingProgressSyncStatus.NO_CHANGES,
            -> ReaderSyncStatus.Synced
            // Config gaps and a missing cloud snapshot are things the user has to go fix, same as a hard
            // failure; the dot makes no distinction between them beyond the reason it carries.
            ReadingProgressSyncStatus.CONFIG_INCOMPLETE -> ReaderSyncStatus.Failed("Sync is not fully configured")
            ReadingProgressSyncStatus.CLOUD_MISSING -> ReaderSyncStatus.Failed("No snapshot in the cloud yet")
            ReadingProgressSyncStatus.FAILED -> ReaderSyncStatus.Failed(result.failureMessage)
            // Sync is switched off entirely - show no dot rather than a stale verdict from a previous session.
            ReadingProgressSyncStatus.SYNC_DISABLED -> ReaderSyncStatus.Idle
            ReadingProgressSyncStatus.THROTTLED -> return
        }
        lastSettledSyncStatus = status
        _syncStatus.value = status
    }

    private fun startReadingTimeTicker() {
        trackingJob?.cancel()
        trackingJob = viewModelScope.launch {
            while (true) {
                delay(10_000L)
                persistReadingTime(readingTimeTracker.flush(System.currentTimeMillis()))
                if (!readingTimeTracker.isTimingActive) break
            }
            trackingJob = null
        }
    }

    private fun persistReadingTime(update: ReadingTimeUpdate) {
        if (update.addedSeconds == 0L && update.session == null) return
        viewModelScope.launch {
            persistReadingTimeNow(update)
        }
    }

    private suspend fun persistReadingTimeNow(update: ReadingTimeUpdate) {
        if (update.addedSeconds == 0L && update.session == null) return
        if (update.addedSeconds > 0L) bookRepository.addReadingTime(bookId, update.addedSeconds)
        _activeReadingSessionSeconds.value = update.activeSessionSeconds
        update.session?.let { session ->
            readingSessionRepository.record(bookId, session.startedAt, session.endedAt, session.durationSeconds)
        }
    }

    override fun onCleared() {
        trackingJob?.cancel()
        trackingJob = null
        // viewModelScope's Job is already cancelled by the time onCleared() runs (androidx.lifecycle.ViewModel.clear()
        // closes it before invoking onCleared()), so a viewModelScope.launch here would never run its body. Use the
        // process-lifetime applicationScope instead so this final flush actually reaches the database.
        val finalUpdate = readingTimeTracker.pause(System.currentTimeMillis())
        ReadingSessionContinuationStore.put(bookId, readingTimeTracker)
        if (finalUpdate.addedSeconds != 0L || finalUpdate.session != null) {
            applicationScope.launch { persistReadingTimeNow(finalUpdate) }
        }
        flushPendingLocatorWrite()
        cancelEngineJobs()
        boundEngine?.close()
        boundEngine = null
        dictionaryLookupJob?.cancel()
        dictionaryInstallJob?.cancel()
        autoProgressSyncJob?.cancel()
        autoProgressSyncJob = null
        readAloudPlayer.release()
        ReadAloudForegroundService.stop(appContext)
    }

    private fun showReturnRecap(previousReadAt: Long?) {
        val awayMillis = System.currentTimeMillis() - (previousReadAt ?: return)
        if (awayMillis < ReturnRecapMinAwayMillis) return
        viewModelScope.launch {
            val highlight = annotationRepository.observeForBook(bookId).first()
                .firstOrNull { it.type != AnnotationType.BOOKMARK && it.selectedText.isNotBlank() && !it.isCommunityQuote() }
                ?.selectedText
            _returnRecap.value = ReaderRecap(
                awayMillis = awayMillis,
                highlight = highlight,
                dueWords = vocabularyCardRepository.observeDueCount().first(),
            )
        }
    }

    private fun cancelEngineJobs() {
        engineJobs.forEach { it.cancel() }
        engineJobs.clear()
    }

    private fun createAnnotation(type: AnnotationType, colorKey: String = DefaultAnnotationColor, readerNote: String?) {
        val engine = boundEngine ?: return
        val selection = (uiState.value as? ReaderUiState.Loaded)?.selection ?: return
        val existing = (uiState.value as? ReaderUiState.Loaded)?.annotations.orEmpty()
        viewModelScope.launch {
            // Selecting across a highlight (or underline) of the same kind grows that one instead of stacking a second.
            val candidates = if (type == AnnotationType.NOTE) {
                emptyList()
            } else {
                // Only ranges in the selection's own section (the CFI before '!') can overlap it.
                val section = selection.cfi.substringBefore('!')
                existing.filter {
                    it.type == type && it.locator.startsWith(CfiPrefix) && it.locator != selection.cfi &&
                        it.locator.substringBefore('!') == section
                }
            }
            val union = runCatchingCancellable { engine.mergeRanges(selection.cfi, candidates.map { it.locator }) }.getOrNull()
            val swallowed = union?.merged?.let { merged -> candidates.filter { it.locator in merged } }.orEmpty()
            if (union != null && swallowed.isNotEmpty()) {
                val kept = swallowed.minBy { it.createdAt }
                val notes = (swallowed.map { it.readerNote } + readerNote).mapNotNull { it?.trim()?.ifEmpty { null } }.distinct()
                annotationRepository.update(
                    kept.copy(
                        locator = union.cfi,
                        selectedText = union.text.ifBlank { selection.selectedText },
                        colorKey = colorKey,
                        readerNote = notes.joinToString("\n\n").ifEmpty { null },
                    ),
                )
                annotationRepository.softDeleteAll(swallowed.map { it.id } - kept.id)
            } else {
                annotationRepository.create(
                    bookId = bookId,
                    type = type,
                    colorKey = colorKey,
                    locator = selection.cfi,
                    chapterTitle = selection.chapterTitle,
                    chapterHref = null,
                    selectedText = selection.selectedText,
                    readerNote = readerNote?.takeIf { it.isNotBlank() },
                )
            }
            engine.clearSelection()
            dictionaryLookupJob?.cancel()
            pendingDictionaryWord = null
            _dictionaryLookup.value = DictionaryLookupState.Hidden
            _uiState.update { current ->
                if (current is ReaderUiState.Loaded) current.copy(selection = null) else current
            }
        }
    }

    private fun lookupSelection(selectedText: String?) {
        dictionaryLookupJob?.cancel()
        val word = selectedText?.toDictionaryWord()
        if (word == null) {
            if (dictionaryPickerActive) return
            _dictionaryLookup.value = DictionaryLookupState.Hidden
            return
        }
        pendingDictionaryWord = word
        if (dictionaryRepository.englishPackState.value !is DictionaryPackState.Installed) {
            _dictionaryLookup.value = DictionaryLookupState.PackRequired(word)
            return
        }
        dictionaryLookupJob = viewModelScope.launch {
            _dictionaryLookup.value = DictionaryLookupState.LookingUp(word)
            wordLookupStatRepository.recordLookup(word, settingsRepository.snapshot.first().lookupWriterOrigin())
            val entry = try {
                dictionaryRepository.lookupEnglish(word)
            } catch (throwable: CancellationException) {
                throw throwable
            } catch (throwable: Throwable) {
                _dictionaryLookup.value = DictionaryLookupState.Failed(
                    word,
                    throwable.message ?: "Dictionary lookup failed",
                )
                return@launch
            }
            _dictionaryLookup.value = if (entry == null) {
                DictionaryLookupState.NotFound(word)
            } else {
                DictionaryLookupState.Found(entry, vocabularyCardRepository.findByWord(entry.headword).toSavedWordStatus())
            }
        }
    }

    private fun currentLookupWord(): String? = when (val state = _dictionaryLookup.value) {
        is DictionaryLookupState.PackRequired -> state.word
        is DictionaryLookupState.Installing -> state.word
        is DictionaryLookupState.Failed -> state.word
        else -> pendingDictionaryWord
    }
}

private fun SettingsSnapshot.lookupWriterOrigin(): String =
    kindleDeviceName.trim().ifBlank { "Vayana Sync" }

private val SettingsSnapshot.selectedImportedFont
    get() = readerCustomFontId?.let { selectedId -> readerImportedFonts.firstOrNull { it.id == selectedId } }

private val SettingsSnapshot.readerFontFamilyCss: String
    get() = if (selectedImportedFont != null) "'VayanaImportedReaderFont', serif" else readerFontFamily.cssFamily

private fun SettingsSnapshot.toBookStyle(): BookStyle = BookStyle(
    fontSizePercent = readerFontSizePercent,
    lineHeight = readerLineHeight,
    fontFamily = readerFontFamilyCss,
    customFontFileName = selectedImportedFont?.fileName,
    sideMarginPercent = readerSideMarginPercent,
    bionicReading = readerBionicReading,
    boldText = readerBolderText,
    textAlign = when (readerTextAlign) {
        ReaderTextAlign.BOOK -> BookTextAlign.BOOK
        ReaderTextAlign.JUSTIFIED -> BookTextAlign.JUSTIFIED
        ReaderTextAlign.LEFT -> BookTextAlign.LEFT
    },
    hyphenation = when (readerHyphenation) {
        ReaderHyphenation.BOOK -> BookHyphenation.BOOK
        ReaderHyphenation.ON -> BookHyphenation.ON
        ReaderHyphenation.OFF -> BookHyphenation.OFF
    },
    // A sliding page is a run of partial refreshes on E-Ink: all ghosting, and slower than a plain flip.
    pageTurnAnimation = readerPageTurnAnimation && displayProfile != DisplayProfile.E_INK,
)

private val ReaderFontFamily.cssFamily: String
    get() = when (this) {
        ReaderFontFamily.SERIF -> "serif"
        ReaderFontFamily.SANS -> "sans-serif"
        ReaderFontFamily.MONO -> "monospace"
    }

private val SettingsSnapshot.readTheme: ReadTheme
    get() = when {
        displayProfile == DisplayProfile.E_INK -> Palette.EinkBackground.toReadTheme(Palette.EinkForeground)
        readerTheme == ReaderTheme.LIGHT -> Palette.ReaderLightBackground.toReadTheme(Palette.ReaderLightText)
        readerTheme == ReaderTheme.PAPER -> Palette.ReaderPaperBackground.toReadTheme(Palette.ReaderPaperText)
        readerTheme == ReaderTheme.SEPIA -> Palette.ReaderSepiaBackground.toReadTheme(Palette.ReaderSepiaText)
        readerTheme == ReaderTheme.MINT -> Palette.ReaderMintBackground.toReadTheme(Palette.ReaderMintText)
        readerTheme == ReaderTheme.SKY -> Palette.ReaderSkyBackground.toReadTheme(Palette.ReaderSkyText)
        readerTheme == ReaderTheme.ROSE -> Palette.ReaderRoseBackground.toReadTheme(Palette.ReaderRoseText)
        readerTheme == ReaderTheme.DARK -> Palette.ReaderDarkBackground.toReadTheme(Palette.ReaderDarkText)
        readerTheme == ReaderTheme.OLED -> Palette.ReaderOledBackground.toReadTheme(Palette.ReaderOledText)
        themeMode == ThemeMode.DARK -> Palette.ReaderDarkBackground.toReadTheme(Palette.ReaderDarkText)
        else -> Palette.ReaderPaperBackground.toReadTheme(Palette.ReaderPaperText)
    }

private fun androidx.compose.ui.graphics.Color.toReadTheme(textColor: androidx.compose.ui.graphics.Color): ReadTheme =
    ReadTheme(backgroundColorArgb = toArgb(), textColorArgb = textColor.toArgb())

/** A locator write the debounce still owes the database, kept so teardown can flush it without the job. */
private data class PendingLocatorWrite(
    val cfi: String,
    val progression: Float,
)

private data class BookStyleOverride(
    val fontSizePercent: Int? = null,
    val lineHeight: Float? = null,
    val fontFamily: ReaderFontFamily? = null,
    val sideMarginPercent: Int? = null,
)

private fun Book.toStyleOverrideOrNull(): BookStyleOverride? {
    if (customFontSizePercent == null && customLineHeight == null && customFontFamily == null && customSideMarginPercent == null) {
        return null
    }
    return BookStyleOverride(
        fontSizePercent = customFontSizePercent,
        lineHeight = customLineHeight,
        fontFamily = customFontFamily?.let { name -> ReaderFontFamily.entries.firstOrNull { it.name == name } },
        sideMarginPercent = customSideMarginPercent,
    )
}

private fun Annotation.toReaderAnnotation(): ReaderAnnotation? {
    if (type == AnnotationType.BOOKMARK) return null
    val cfi = locator.ifBlank { "text:${id}" }
    return ReaderAnnotation(
        id = id.toString(),
        type = type.toReaderAnnotationType(),
        cfi = cfi,
        colorKey = colorKey,
        note = readerNote,
        text = selectedText,
    )
}

private fun AnnotationType.toReaderAnnotationType(): ReaderAnnotationType = when (this) {
    AnnotationType.HIGHLIGHT -> ReaderAnnotationType.HIGHLIGHT
    AnnotationType.UNDERLINE -> ReaderAnnotationType.UNDERLINE
    AnnotationType.BOOKMARK -> ReaderAnnotationType.BOOKMARK
    AnnotationType.NOTE -> ReaderAnnotationType.NOTE
}

private fun String.toDictionaryWord(): String? {
    val candidate = trim().trim('“', '”', '‘', '’', '\'', '"', '.', ',', ';', ':', '!', '?', '(', ')', '[', ']')
    return candidate.takeIf { DictionarySelectionWordRegex.matches(it) }
}

private const val SpeechInteractionIntervalMillis = 30_000L
private const val MaxChapterWords = 25

/** Reopening a book within this long of last reading it is just carrying on, not a return worth a recap. */
private const val ReturnRecapMinAwayMillis = 12 * 60 * 60 * 1000L

private const val DefaultAnnotationColor = "yellow"
private const val CfiPrefix = "epubcfi("
private const val DefaultBookmarkColor = "bookmark"
private const val StyleUpdateDebounceMillis = 80L
private const val LocatorPersistDebounceMillis = 400L
private const val RecentLookupsLimit = 5

/** No page turn for this long ends the current reading session (PROMPT: idle stops a session). */
private const val IdleSessionTimeoutMs = 5 * 60 * 1000L
private const val SessionContinuationGraceMs = 60 * 1000L

private const val SearchDebounceMillis = 400L
private val DictionarySelectionWordRegex = Regex("^[\\p{L}]+(?:['’\\-][\\p{L}]+)*$")

private data class ReadAloudNotificationSnapshot(
    val active: Boolean,
    val playing: Boolean,
    val bookTitle: String?,
    val progressPercent: Int?,
) {
    companion object {
        val Inactive = ReadAloudNotificationSnapshot(active = false, playing = false, bookTitle = null, progressPercent = null)
    }
}

internal fun readAloudProgressPercent(progression: Float?): Int? =
    progression?.times(100)?.roundToInt()?.coerceIn(0, 100)
