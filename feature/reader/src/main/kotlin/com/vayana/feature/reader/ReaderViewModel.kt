package com.vayana.feature.reader

import com.vayana.core.resources.R
import com.vayana.core.resources.uiText
import com.vayana.core.resources.UiText
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
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
import com.vayana.core.datastore.settings.ReadingPreset
import com.vayana.core.datastore.settings.ReadingToolsRepository
import com.vayana.core.datastore.settings.SettingsRegistry
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.designsystem.tokens.Palette
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.isMonochrome
import com.vayana.core.designsystem.theme.ThemeMode
import com.vayana.core.filesystem.StorageRoots
import com.vayana.core.sync.progress.ReadingProgressOnlySyncer
import com.vayana.core.sync.progress.ReadingProgressSyncResult
import com.vayana.core.sync.progress.ReadingProgressSyncStatus
import com.vayana.dictionary.api.DictionaryEntry
import com.vayana.dictionary.api.DictionaryPackState
import com.vayana.dictionary.api.DictionaryRepository
import com.vayana.dictionary.api.OnlineDictionary
import com.vayana.dictionary.api.OnlineDictionarySource
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
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
    data class Failed(val reason: UiText?) : ReaderSyncStatus
}

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Loaded(
        val bookTitle: String,
        val bookAuthor: String? = null,
        val bookCoverPath: String? = null,
        val bookSeries: String? = null,
        val bookSeriesNumber: String? = null,
        val toc: List<com.vayana.reader.api.TocEntry>,
        val currentLocator: Locator?,
        val annotations: List<Annotation> = emptyList(),
        val selection: ReaderSelection? = null,
        val highlightCard: HighlightCardState? = null,
        /** Position to jump back to via [ReaderViewModel.returnToPreviousPosition], set right before a TOC/search/note jump. */
        val returnLocator: Locator? = null,
        /** A pre-paginated book (PDF): page fit and crop settings take the place of typography. */
        val fixedLayout: Boolean = false,
        /** PDF page labels in physical page order. */
        val pageLabels: List<String> = emptyList(),
        /** The page is larger than the screen (zoomed or fit-width PDF): drags scroll it instead of edge swipes. */
        val pageScrollable: Boolean = false,
        /** The book's declared language, so web lookups use that language's Wikipedia and Wiktionary entries. */
        val bookLanguage: String? = null,
    ) : ReaderUiState
    data class Failed(val message: UiText) : ReaderUiState
}

data class HighlightCardState(
    val annotationId: Long,
    val top: Float?,
    val bottom: Float?,
    val page: Int?,
    val sectionCfi: String?,
)

internal fun HighlightCardState.isAnchoredTo(locator: Locator): Boolean {
    val locatorCfi = locator.cfi
    return when {
        page != null && locator.currentPage != null -> page == locator.currentPage
        sectionCfi != null && locatorCfi != null -> sectionCfi == locatorCfi.substringBefore('!')
        else -> true
    }
}

/** The end-of-story question: finished? and, optionally, a rating ([rating] is the book's current one, 0 if none). */
data class BookFinishedPrompt(val rating: Float)

data class PdfPasswordPrompt(val incorrect: Boolean)

data class PdfBookPreferences(
    val cropMargins: Boolean = true,
    val fitWidth: Boolean = false,
    val rotationDegrees: Int = 0,
)

/** Shown when a book is reopened after a while: how long since it was last read, and the reader's latest highlight in it. */
data class ReaderRecap(val awayMillis: Long, val highlight: String?, val dueWords: Int = 0, val journalText: String? = null)

/** Whether a looked-up word is already a vocabulary card, and whether it has been marked as known. */
enum class SavedWordStatus { NOT_SAVED, SAVED, KNOWN }

internal fun VocabularyCard?.toSavedWordStatus(): SavedWordStatus = when {
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
    data class Failed(val word: String, val message: UiText) : DictionaryLookupState

    /** [word] being looked up on, or missing from, [source] on the web; an entry found there is shown as [Found]. */
    data class Online(val word: String, val source: OnlineDictionarySource, val status: OnlineLookupStatus) : DictionaryLookupState

    /** A selected phrase ("Hagia Sophia", "kick the bucket"): not for the one-word dictionary, but for the web. */
    data class Phrase(val phrase: String) : DictionaryLookupState
}

enum class OnlineLookupStatus { LOOKING_UP, NOT_FOUND, FAILED }

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val readAloudStatusHolder: ReadAloudStatusHolder,
    private val annotationRepository: AnnotationRepository,
    private val storageRoots: StorageRoots,
    private val settingsRepository: SettingsRepository,
    private val readingToolsRepository: ReadingToolsRepository,
    private val dictionaryRepository: DictionaryRepository,
    private val onlineDictionary: OnlineDictionary,
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

    /** Consumed by the first open, so a restore after process death resumes where the reader got to instead. */
    private var openFromStart: Boolean = savedStateHandle[FromStartKey] ?: false

    // Opened from the widget's play button: start reading aloud once the book is open, once.
    private var startReadAloudOnOpen: Boolean = savedStateHandle[ReadAloudKey] ?: false

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState

    private val _dictionaryLookup = MutableStateFlow<DictionaryLookupState>(DictionaryLookupState.Hidden)
    val dictionaryLookup: StateFlow<DictionaryLookupState> = _dictionaryLookup

    private val _readingPositionPrompt = MutableStateFlow<ReadingPositionPrompt?>(null)
    val readingPositionPrompt: StateFlow<ReadingPositionPrompt?> = _readingPositionPrompt

    val settings: StateFlow<SettingsSnapshot> = settingsRepository.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsSnapshot())

    val presets: StateFlow<List<ReadingPreset>> = readingToolsRepository.presets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // How fast this reader reads against the engine's fixed estimate, learned from their own page turns.
    private val paceTracker = ReadingPaceTracker(System::currentTimeMillis)
    private val paceTotals = MutableStateFlow<ReadingPaceTotals?>(null)

    /** What to multiply the engine's "time left" by: 1 until the reader has been measured, or when they turned it off. */
    val paceFactor: StateFlow<Float> = combine(paceTotals, settings.map { it.readerPersonalPace }.distinctUntilChanged()) { totals, enabled ->
        if (enabled && totals != null) ReadingPace.factor(totals) else 1f
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

    private fun learnPace(minutesLeft: Double?) {
        // Pages turned by read aloud go at the narrator's speed, not the reader's.
        if (readAloud.value.active) {
            paceTracker.reset()
            return
        }
        val sample = paceTracker.onRelocate(minutesLeft) ?: return
        paceTotals.update { current -> current?.let { ReadingPace.add(it, sample) } }
    }

    /** Non-null while this book has its own font/line-height/margin overrides (the product specification's per-book reading preferences). */
    private val _bookStyleOverride = MutableStateFlow<BookStyleOverride?>(null)
    private val _pdfBookPreferences = MutableStateFlow<PdfBookPreferences?>(null)
    val pdfBookPreferences: StateFlow<PdfBookPreferences?> = _pdfBookPreferences

    val usingCustomStyle: StateFlow<Boolean> = _bookStyleOverride
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** [settings] with this book's overrides layered on top - what the reader actually renders with and the Style panel shows. */
    val effectiveSettings: StateFlow<SettingsSnapshot> = combine(settings, _bookStyleOverride, _pdfBookPreferences) { snapshot, override, pdf ->
        val styled = if (override == null) snapshot else snapshot.copy(
                readerFontSizePercent = override.fontSizePercent ?: snapshot.readerFontSizePercent,
                readerLineHeight = override.lineHeight ?: snapshot.readerLineHeight,
                readerFontFamily = override.fontFamily ?: snapshot.readerFontFamily,
                readerCustomFontId = if (override.fontFamily == null) snapshot.readerCustomFontId else null,
                readerSideMarginPercent = override.sideMarginPercent ?: snapshot.readerSideMarginPercent,
            )
        if (pdf == null) styled else styled.copy(
            readerPdfCropMargins = pdf.cropMargins,
            readerPdfFitWidth = pdf.fitWidth,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsSnapshot())

    private val _pdfPasswordPrompt = MutableStateFlow<PdfPasswordPrompt?>(null)
    val pdfPasswordPrompt: StateFlow<PdfPasswordPrompt?> = _pdfPasswordPrompt

    private val _pdfThumbnails = MutableStateFlow<Map<Int, ByteArray>>(emptyMap())
    val pdfThumbnails: StateFlow<Map<Int, ByteArray>> = _pdfThumbnails
    private val thumbnailJobs = mutableMapOf<Int, Job>()

    /** Monotonic signal from the book document; the screen owns the actual controls visibility. */
    private val _readerControlsRequest = MutableStateFlow(0L)
    val readerControlsRequest: StateFlow<Long> = _readerControlsRequest

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
    private var highlightMutationJob: Job? = null
    private var pendingDictionaryWord: String? = null
    private var dictionaryPickerActive = false
    private var lastUsedHighlightColor: String = DefaultAnnotationColor
    private var autoMarkedSelectionCfi: String? = null
    private val pageTurnAutoSyncGate = PageTurnAutoSyncGate { settings.value.readingAutoSyncEveryPages }
    private val resumeProgressSyncGate = ReaderResumeSyncGate()
    private var autoProgressSyncJob: Job? = null
    private var autoProgressSyncRequested = false
    private var autoProgressSyncForceRequested = false
    private var autoProgressSyncResumeGeneration: Long? = null
    private val _syncStatus = MutableStateFlow<ReaderSyncStatus>(ReaderSyncStatus.Idle)
    val syncStatus: StateFlow<ReaderSyncStatus> = _syncStatus

    private val _footnote = MutableStateFlow<Footnote?>(null)
    val footnote: StateFlow<Footnote?> = _footnote

    private val _bookFinishedPrompt = MutableStateFlow<BookFinishedPrompt?>(null)

    /** Asks whether the book is finished once the story ends; null while there is nothing to ask. */
    val bookFinishedPrompt: StateFlow<BookFinishedPrompt?> = _bookFinishedPrompt

    /** Only a book that wasn't finished when it was opened is asked about; a reread doesn't finish it again. */
    private var unfinishedWhenOpened = false

    /** Asked at most once while the reader is open, whatever the answer. */
    private var bookFinishedPromptOffered = false

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
    private val pronunciationStore = SpeechPronunciationStore(appContext)
    private var pronunciationBookKey: String? = null
    private val _pronunciations = MutableStateFlow<List<SpeechPronunciation>>(emptyList())
    internal val pronunciations: StateFlow<List<SpeechPronunciation>> = _pronunciations
    internal val readAloudVoices: StateFlow<List<SpeechVoiceOption>> = readAloudPlayer.voices
    internal val readAloudEngines: StateFlow<List<SpeechEngineOption>> = readAloudPlayer.engines

    private val _chapterWords = MutableStateFlow<ChapterWordsState>(ChapterWordsState.Idle)
    val chapterWords: StateFlow<ChapterWordsState> = _chapterWords
    private var chapterWordsJob: Job? = null
    private var lastReaderWrittenLocator: String? = null
    private var locatorPersistJob: Job? = null

    /** The locator the debounced write still owes the database, or null once that write has landed. */
    private var pendingLocatorWrite: PendingLocatorWrite? = null
    private var locatorDeferredByResumeSync: Locator? = null
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
        locatorDeferredByResumeSync = null
        lastReaderWrittenLocator = null

        engineJobs += viewModelScope.launch {
            val book = bookRepository.getById(bookId)
            unfinishedWhenOpened = book != null && book.finishedReadingAt == null && book.readingPercent < 1f
            if (book == null) {
                _uiState.value = ReaderUiState.Failed(UiText.Res(R.string.reader_error_book_not_found))
                return@launch
            }
            _bookStyleOverride.value = book.toStyleOverrideOrNull()
            pronunciationBookKey = book.syncId
            _pronunciations.value = pronunciationStore.load(book.syncId)
            readAloudPlayer.setPronunciations(_pronunciations.value)
            _pdfBookPreferences.value = if (book.format == BookFormat.PDF) loadPdfBookPreferences() else null
            _pdfThumbnails.value = emptyMap()
            // Read before recordBookOpened below replaces it; a jump to a note or search hit isn't a "return".
            val restoredCfi = restartCfi?.takeIf { it.isNotBlank() }
            restartCfi = null
            val openTarget = restoredCfi ?: targetLocator
            val fromStart = openFromStart && openTarget.isNullOrBlank()
            if (openFromStart) {
                openFromStart = false
                savedStateHandle[FromStartKey] = false
            }
            // Reading again from the start is not a return to where the reader left off.
            val previousReadAt = book.lastReadAt.takeIf { openTarget.isNullOrBlank() && !fromStart && book.readingPercent > 0f }
            if (book.fileAvailability == BookFileAvailability.CLOUD_ONLY) {
                _uiState.value = ReaderUiState.Failed(UiText.Res(R.string.reader_error_cloud_only))
                return@launch
            }
            if (book.fileAvailability != BookFileAvailability.LOCAL || book.filePath.isBlank()) {
                _uiState.value = ReaderUiState.Failed(UiText.Res(R.string.reader_error_file_unavailable))
                return@launch
            }

            val localFile = storageRoots.resolve(book.filePath)
            if (!withContext(dispatchers.io) { localFile.isFile }) {
                _uiState.value = ReaderUiState.Failed(UiText.Res(R.string.reader_error_file_missing))
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
                        // A PDF's embedded title is often a file or tool name; the library's title is the one people set.
                        bookTitle = if (openBook.fixedLayout) book.title else openBook.title.ifBlank { book.title },
                        bookAuthor = book.author,
                        // Database paths are library-root relative; the share-card UI needs a real file path so
                        // it can offer and render the cover.
                        bookCoverPath = book.coverPath
                            ?.let(storageRoots::resolve)
                            ?.takeIf { it.isFile }
                            ?.absolutePath,
                        bookSeries = book.series,
                        bookSeriesNumber = book.seriesNumber,
                        toc = openBook.toc,
                        currentLocator = resumeLocator,
                        fixedLayout = openBook.fixedLayout,
                        bookLanguage = openBook.language,
                        pageLabels = openBook.pageLabels,
                    )
                    observeAnnotations(engine)
                    showReturnRecap(previousReadAt)
                    if (!openTarget.isNullOrBlank()) {
                        engine.goTo(NavTarget.ToLocator(Locator(cfi = openTarget, href = null, progression = 0f, chapterTitle = null)))
                    } else if (!fromStart && savedLocator != null && book.readingPercent > 0.001f) {
                        engine.goTo(NavTarget.ToFraction(book.readingPercent.coerceIn(0f, 0.999f)))
                    }
                    observeRemoteReadingProgress()
                    // The sync merge/prompt design intends a conflict to "surface... when the
                    // book opens" - previously this only got checked lazily, once the page-turn
                    // auto-sync gate happened to fire a few pages in. Check once immediately so a
                    // conflict that existed before this session started is caught up front,
                    // instead of reading a stretch of pages on a stale position first.
                    requestAutoProgressSync()
                    if (startReadAloudOnOpen) {
                        startReadAloudOnOpen = false
                        savedStateHandle[ReadAloudKey] = false
                        startReadAloud()
                    }
                }
                .onFailure { throwable ->
                    _uiState.value = ReaderUiState.Failed(throwable.uiText(R.string.reader_error_open_failed))
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
                    if (resumeProgressSyncGate.blocksPositionWrites) {
                        // Keep only the newest relocation. If the sync finds no remote conflict it
                        // still needs to be persisted; dropping it can lose a page turn made while
                        // a slow resume check is running.
                        locatorDeferredByResumeSync = locator
                    } else {
                        locatorDeferredByResumeSync = null
                        persistReaderLocator(locator)
                    }
                }
            }
        }

        engineJobs += viewModelScope.launch {
            engine.events().collect { event ->
                when (event) {
                    is com.vayana.reader.api.EngineEvent.SelectionChanged -> {
                        _uiState.update { current ->
                            if (current is ReaderUiState.Loaded) {
                                current.copy(
                                    selection = event.selection,
                                    highlightCard = if (event.selection != null) null else current.highlightCard,
                                )
                            } else {
                                current
                            }
                        }
                        lookupSelection(event.selection?.selectedText)
                        maybeAutoMarkSelection(event.selection)
                    }
                    is com.vayana.reader.api.EngineEvent.AnnotationTapped -> {
                        _uiState.update { current ->
                            if (current !is ReaderUiState.Loaded) return@update current
                            val annotationId = event.annotationId?.toLongOrNull()
                            val highlight = current.annotations.firstOrNull { annotation ->
                                annotation.id == annotationId &&
                                    annotation.type == AnnotationType.HIGHLIGHT &&
                                    !annotation.isCommunityQuote()
                            }
                            current.copy(
                                selection = if (highlight != null) null else current.selection,
                                highlightCard = highlight?.let {
                                    HighlightCardState(
                                        annotationId = it.id,
                                        top = event.top,
                                        bottom = event.bottom,
                                        page = current.currentLocator?.currentPage,
                                        sectionCfi = current.currentLocator?.cfi?.substringBefore('!'),
                                    )
                                },
                            )
                        }
                        if (event.annotationId != null) {
                            dictionaryLookupJob?.cancel()
                            pendingDictionaryWord = null
                            _dictionaryLookup.value = DictionaryLookupState.Hidden
                        }
                    }
                    is com.vayana.reader.api.EngineEvent.FontSizeStepRequested -> {
                        stepFontSize(event.direction)
                    }
                    is com.vayana.reader.api.EngineEvent.SearchCompleted -> {
                        if (event.query == lastSearchQuery) _searchResults.value = event.results
                    }
                    is FootnoteOpened -> _footnote.value = event.footnote
                    com.vayana.reader.api.EngineEvent.StoryEndReached -> offerBookFinishedPrompt()
                    com.vayana.reader.api.EngineEvent.RendererGone -> onRendererGone()
                    is com.vayana.reader.api.EngineEvent.PageScrollableChanged -> _uiState.update { current ->
                        if (current is ReaderUiState.Loaded) current.copy(pageScrollable = event.scrollable) else current
                    }
                    is com.vayana.reader.api.EngineEvent.PdfPasswordRequired -> {
                        _pdfPasswordPrompt.value = PdfPasswordPrompt(event.incorrect)
                    }
                    com.vayana.reader.api.EngineEvent.ControlsRequested -> _readerControlsRequest.update { it + 1L }
                    is com.vayana.reader.api.EngineEvent.Relocated -> {
                        learnPace(event.locator.bookMinutesLeftExact)
                        _uiState.update { current ->
                            if (current is ReaderUiState.Loaded &&
                                current.highlightCard?.isAnchoredTo(event.locator) == false
                            ) {
                                current.copy(highlightCard = null)
                            } else {
                                current
                            }
                        }
                    }
                    is com.vayana.reader.api.EngineEvent.Error -> Unit
                }
            }
        }

        engineJobs += viewModelScope.launch {
            // Only what the page is drawn from: a brightness swipe or a read-aloud speed change rewrites the settings too,
            // and re-applying an identical style still re-lays-out the whole chapter.
            combine(effectiveSettings, _pdfBookPreferences) { snapshot, pdf ->
                snapshot.toBookStyle(pdf?.rotationDegrees ?: 0) to snapshot.readTheme
            }
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
            _uiState.value = ReaderUiState.Failed(UiText.Res(R.string.reader_error_renderer_crashed))
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
            thumbnailJobs.values.forEach(Job::cancel)
            thumbnailJobs.clear()
            _pdfPasswordPrompt.value = null
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

    fun goToPdfPage(pageIndex: Int) {
        pushReturnLocator()
        dispatch(NavTarget.ToPage(pageIndex))
    }

    fun loadPdfThumbnail(pageIndex: Int, maxWidthPx: Int = 240) {
        if (pageIndex < 0 || _pdfThumbnails.value.containsKey(pageIndex) || thumbnailJobs.containsKey(pageIndex)) return
        val engine = boundEngine ?: return
        thumbnailJobs[pageIndex] = viewModelScope.launch {
            try {
                engine.pageThumbnail(pageIndex, maxWidthPx)?.let { bytes ->
                    _pdfThumbnails.update { current ->
                        val next = if (current.size >= MaxPdfThumbnailCache) current - current.keys.first() else current
                        next + (pageIndex to bytes)
                    }
                }
            } finally {
                thumbnailJobs.remove(pageIndex)
            }
        }
    }

    fun providePdfPassword(password: String?) {
        _pdfPasswordPrompt.value = null
        viewModelScope.launch { boundEngine?.providePdfPassword(password) }
    }

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

    suspend fun saveReadingPreset(name: String) {
        readingToolsRepository.savePreset(ReadingPreset.capture(name, effectiveSettings.value))
    }

    suspend fun deleteReadingPreset(id: String) {
        readingToolsRepository.deletePreset(id)
    }

    suspend fun applyReadingPreset(preset: ReadingPreset) {
        val snapshot = settingsRepository.snapshot.first()
        val customFontId = preset.customFontId?.takeIf { id -> snapshot.readerImportedFonts.any { it.id == id } }
        val globalValues = mutableMapOf(
            SettingsRegistry.ReaderTheme.key to preset.theme.name,
            SettingsRegistry.ReaderBolderText.key to preset.bolderText.toString(),
            SettingsRegistry.ReaderTextAlign.key to preset.textAlign.name,
            SettingsRegistry.ReaderHyphenation.key to preset.hyphenation.name,
            SettingsRegistry.ReaderPublisherStyles.key to preset.usePublisherStyles.toString(),
        )
        val override = _bookStyleOverride.value
        if (override == null) {
            globalValues[SettingsRegistry.ReaderFontSize.key] = preset.fontSizePercent.toString()
            globalValues[SettingsRegistry.ReaderLineHeight.key] = preset.lineHeight.toString()
            globalValues[SettingsRegistry.ReaderFontFamily.key] = preset.fontFamily.name
            globalValues[SettingsRegistry.ReaderSideMargin.key] = preset.sideMarginPercent.toString()
        } else {
            val updated = override.copy(
                fontSizePercent = preset.fontSizePercent.coerceIn(SettingsRegistry.ReaderFontSize.range),
                lineHeight = preset.lineHeight.coerceIn(SettingsRegistry.ReaderLineHeight.range),
                fontFamily = if (customFontId == null) preset.fontFamily else null,
                sideMarginPercent = preset.sideMarginPercent.coerceIn(SettingsRegistry.ReaderSideMargin.range),
            )
            bookRepository.updateReaderPrefs(
                id = bookId,
                fontSizePercent = updated.fontSizePercent,
                lineHeight = updated.lineHeight,
                fontFamily = updated.fontFamily?.name,
                sideMarginPercent = updated.sideMarginPercent,
            )
            _bookStyleOverride.value = updated
        }
        settingsRepository.importFromMap(globalValues)
        settingsRepository.updateReaderCustomFontId(customFontId)
    }

    suspend fun saveJournalEntry(text: String) {
        val body = text.trim().take(4000)
        require(body.isNotBlank())
        val state = uiState.value as? ReaderUiState.Loaded ?: error("Book is not open")
        val locator = state.currentLocator ?: error("Reading position unavailable")
        val cfi = locator.cfi?.takeIf { it.isNotBlank() } ?: error("Reading position unavailable")
        annotationRepository.create(
            bookId = bookId,
            type = AnnotationType.NOTE,
            colorKey = "journal",
            locator = cfi,
            chapterTitle = locator.chapterTitle,
            chapterHref = locator.href,
            selectedText = "",
            readerNote = body,
        )
    }

    fun updateFontSize(percent: Int) {
        if (_bookStyleOverride.value != null) {
            updateBookOverride { it.copy(fontSizePercent = percent) }
        } else {
            viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderFontSize, percent) }
        }
    }

    private fun stepFontSize(direction: Int) {
        if (direction == 0) return
        val setting = SettingsRegistry.ReaderFontSize
        val current = effectiveSettings.value.readerFontSizePercent
        val next = (current + direction.coerceIn(-1, 1) * setting.step).coerceIn(setting.range)
        if (next != current) updateFontSize(next)
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

    fun updateOverridePublisherTypography(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update(SettingsRegistry.ReaderPublisherStyles, !enabled)
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

    fun updatePdfCropMargins(enabled: Boolean) {
        updatePdfBookPreferences { it.copy(cropMargins = enabled) }
    }

    fun updatePdfFitWidth(enabled: Boolean) {
        updatePdfBookPreferences { it.copy(fitWidth = enabled) }
    }

    fun rotatePdfClockwise() {
        updatePdfBookPreferences { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
        _pdfThumbnails.value = emptyMap()
    }

    private fun updatePdfBookPreferences(transform: (PdfBookPreferences) -> PdfBookPreferences) {
        val updated = transform(_pdfBookPreferences.value ?: loadPdfBookPreferences())
        _pdfBookPreferences.value = updated
        appContext.getSharedPreferences(PdfPreferencesFile, Context.MODE_PRIVATE).edit {
            putBoolean("$bookId.crop", updated.cropMargins)
            putBoolean("$bookId.fit", updated.fitWidth)
            putInt("$bookId.rotation", updated.rotationDegrees)
        }
    }

    private fun loadPdfBookPreferences(): PdfBookPreferences {
        val preferences = appContext.getSharedPreferences(PdfPreferencesFile, Context.MODE_PRIVATE)
        return PdfBookPreferences(
            cropMargins = preferences.getBoolean("$bookId.crop", true),
            fitWidth = preferences.getBoolean("$bookId.fit", settings.value.readerPdfFitWidth),
            rotationDegrees = preferences.getInt("$bookId.rotation", 0),
        )
    }

    /** PDF "Darken text" and EPUB "Bolder text" are one setting: both make thin, faint print easier to read. */
    fun updateBolderText(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderBolderText, enabled) }
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

    private fun offerBookFinishedPrompt() {
        if (!unfinishedWhenOpened || bookFinishedPromptOffered) return
        bookFinishedPromptOffered = true
        viewModelScope.launch {
            val book = bookRepository.getById(bookId) ?: return@launch
            // Finished meanwhile (from another device, or the book's end crossing the "finished" threshold) still gets
            // asked for a rating, unless it already has one.
            if (book.finishedReadingAt != null && book.rating > 0f) return@launch
            _bookFinishedPrompt.value = BookFinishedPrompt(rating = book.rating)
        }
    }

    /** Marks the book finished and, when [rating] is given (above zero) and new, saves it as the book's rating. */
    fun confirmBookFinished(rating: Float) {
        val prompt = _bookFinishedPrompt.value ?: return
        _bookFinishedPrompt.value = null
        applicationScope.launch {
            bookRepository.markFinished(bookId)
            if (rating > 0f && rating != prompt.rating) bookRepository.updateRating(bookId, rating)
        }
    }

    fun dismissBookFinishedPrompt() {
        _bookFinishedPrompt.value = null
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

    fun pauseReadAloud() = readAloudPlayer.pause()

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

    internal fun savePronunciation(original: String, spoken: String) {
        val rule = normalizedPronunciations(listOf(SpeechPronunciation(original, spoken))).firstOrNull() ?: return
        updatePronunciations(_pronunciations.value.filterNot { it.original.equals(rule.original, ignoreCase = true) } + rule)
    }

    internal fun removePronunciation(original: String) {
        updatePronunciations(_pronunciations.value.filterNot { it.original.equals(original, ignoreCase = true) })
    }

    private fun updatePronunciations(rules: List<SpeechPronunciation>) {
        val key = pronunciationBookKey ?: return
        val next = normalizedPronunciations(rules)
        pronunciationStore.save(key, next)
        _pronunciations.value = next
        readAloudPlayer.setPronunciations(next)
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

    /** Looks the word the dictionary card is showing up on [source]; only ever on the reader's tap, as it goes online. */
    fun lookupOnline(source: OnlineDictionarySource) {
        val word = currentLookupWord() ?: return
        dictionaryLookupJob?.cancel()
        dictionaryLookupJob = viewModelScope.launch {
            _dictionaryLookup.value = DictionaryLookupState.Online(word, source, OnlineLookupStatus.LOOKING_UP)
            val entry = try {
                onlineDictionary.lookup(word, source, (uiState.value as? ReaderUiState.Loaded)?.bookLanguage)
            } catch (throwable: CancellationException) {
                throw throwable
            } catch (_: Throwable) {
                _dictionaryLookup.value = DictionaryLookupState.Online(word, source, OnlineLookupStatus.FAILED)
                return@launch
            }
            _dictionaryLookup.value = if (entry == null) {
                DictionaryLookupState.Online(word, source, OnlineLookupStatus.NOT_FOUND)
            } else {
                DictionaryLookupState.Found(entry, vocabularyCardRepository.findByWord(entry.headword).toSavedWordStatus())
            }
        }
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

    fun updateActiveHighlightColor(colorKey: String) {
        val annotation = activeHighlight() ?: return
        val annotationId = annotation.id
        if (annotation.colorKey.equals(colorKey, ignoreCase = true)) return
        lastUsedHighlightColor = colorKey
        _uiState.update { current ->
            if (current is ReaderUiState.Loaded) {
                current.copy(
                    annotations = current.annotations.map {
                        if (it.id == annotationId) it.copy(colorKey = colorKey) else it
                    },
                )
            } else {
                current
            }
        }
        enqueueHighlightMutation { annotationRepository.update(annotation.copy(colorKey = colorKey)) }
    }

    fun deleteActiveHighlight() {
        val annotation = activeHighlight() ?: return
        _uiState.update { current ->
            if (current is ReaderUiState.Loaded) {
                current.copy(
                    annotations = current.annotations.filterNot { it.id == annotation.id },
                    highlightCard = null,
                )
            } else {
                current
            }
        }
        enqueueHighlightMutation { annotationRepository.softDelete(annotation.id) }
    }

    fun convertActiveHighlightToUnderline() {
        val annotation = activeHighlight() ?: return
        val underline = annotation.copy(type = AnnotationType.UNDERLINE)
        _uiState.update { current ->
            if (current is ReaderUiState.Loaded) {
                current.copy(
                    annotations = current.annotations.map { if (it.id == annotation.id) underline else it },
                    highlightCard = null,
                )
            } else {
                current
            }
        }
        enqueueHighlightMutation { annotationRepository.update(underline) }
    }

    fun dismissActiveHighlight() {
        _uiState.update { current ->
            if (current is ReaderUiState.Loaded) current.copy(highlightCard = null) else current
        }
    }

    fun startReadAloudFromActiveHighlight() {
        if (!settings.value.readerAudioFeaturesEnabled) return
        val annotation = activeHighlight() ?: return
        _returnRecap.value = null
        readAloudPlayer.stop()
        readAloudPlayer.start(
            rate = settings.value.readAloudRate,
            pitch = settings.value.readAloudPitch,
            voiceName = settings.value.readAloudVoiceName,
            fromCfi = annotation.locator,
            speechEngine = settings.value.readAloudEngine,
            wordHighlight = settings.value.displayProfile != DisplayProfile.E_INK,
        )
        dismissActiveHighlight()
    }

    private fun activeHighlight(): Annotation? {
        val state = uiState.value as? ReaderUiState.Loaded ?: return null
        val annotationId = state.highlightCard?.annotationId ?: return null
        return state.annotations.firstOrNull {
            it.id == annotationId && it.type == AnnotationType.HIGHLIGHT && !it.isCommunityQuote()
        }
    }

    /** Preserve the order of rapid colour/delete/type taps so an older full-row update cannot resurrect stale data. */
    private fun enqueueHighlightMutation(mutation: suspend () -> Unit) {
        val previous = highlightMutationJob
        highlightMutationJob = viewModelScope.launch {
            previous?.join()
            mutation()
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
                    throwable.uiText(R.string.reader_error_dictionary_install_failed),
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
        engine.applyStyle(
            style = snapshot.toBookStyle(_pdfBookPreferences.value?.rotationDegrees ?: 0),
            theme = snapshot.readTheme,
        )
    }

    private fun observeAnnotations(engine: BookEngine) {
        engineJobs += viewModelScope.launch {
            // Room's Flow re-emits whenever the annotations table is invalidated by any write
            // (even to a different book), not just when this book's rows actually changed - skip
            // the JS round trip when the content is identical to what we last rendered.
            annotationRepository.observeForBook(bookId).distinctUntilChanged().collectLatest { annotations ->
                val readerAnnotations = withContext(dispatchers.default) { annotations.mapNotNull { it.toReaderAnnotation() } }
                engine.renderAnnotations(readerAnnotations)
                _uiState.update { current ->
                    if (current is ReaderUiState.Loaded) {
                        current.copy(
                            annotations = annotations,
                            highlightCard = current.highlightCard?.takeIf { card ->
                                annotations.any { it.id == card.annotationId && it.type == AnnotationType.HIGHLIGHT }
                            },
                        )
                    } else {
                        current
                    }
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
                    syncedDeviceLabel = remoteProgress.syncedDeviceLabel,
                    syncedAt = remoteProgress.syncedAt,
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
        )?.let { prompt ->
            locatorDeferredByResumeSync = null
            _readingPositionPrompt.value = prompt
        }
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
            // Start from what earlier reading measured, then save a few seconds after it last changed: every page
            // turn changes it, and the settings file need not be rewritten that often.
            val saved = settingsRepository.snapshot.first()
            paceTotals.value = ReadingPaceTotals(saved.readerPaceActualSeconds, saved.readerPaceEstimatedSeconds)
            paceTotals.filterNotNull().drop(1).debounce(PaceSaveDebounceMillis).collect { totals ->
                settingsRepository.update(SettingsRegistry.ReaderPaceActualSeconds, totals.actualSeconds)
                settingsRepository.update(SettingsRegistry.ReaderPaceEstimatedSeconds, totals.estimatedSeconds)
            }
        }
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
                    // The widget's play/pause button follows this book's read-aloud.
                    if (notification.active) {
                        readAloudStatusHolder.publish(ReadAloudStatus(bookId, notification.playing))
                    } else {
                        readAloudStatusHolder.clear(bookId)
                    }
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
            // The widget's play button, pressed while this book is already open in the reader.
            readAloudStatusHolder.startRequests.collect { requestedBookId ->
                if (requestedBookId != bookId) return@collect
                if (bookOpen) startReadAloud() else startReadAloudOnOpen = true
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
        // Time away is not time reading.
        paceTracker.reset()
        persistReadingTime(readingTimeTracker.flush(System.currentTimeMillis()))
        resumeProgressSyncGate.onResume(bookOpen)?.let { generation ->
            // A reader kept open in the background has not seen progress made on another device.
            // Force a pull before this WebView can save its stale locator as the newest position.
            requestAutoProgressSync(force = true, resumeGeneration = generation)
        }
    }

    fun onPause() {
        readerResumed = false
        paceTracker.reset()
        persistReadingTime(readingTimeTracker.pause(System.currentTimeMillis()))
        trackingJob?.cancel()
        trackingJob = null
        flushPendingLocatorWrite()
        resumeProgressSyncGate.onPause(bookOpen)
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
        locatorDeferredByResumeSync = null
        lastReaderWrittenLocator = prompt.targetLocator
        dispatch(
            NavTarget.ToLocator(
                Locator(cfi = prompt.targetLocator, href = null, progression = prompt.targetProgress, chapterTitle = null),
            ),
        )
    }

    fun dismissReadingPositionPrompt() {
        _readingPositionPrompt.value = null
        locatorDeferredByResumeSync = null
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

    private suspend fun persistReaderLocator(locator: Locator) {
        locator.cfi?.let { cfi ->
            lastReaderWrittenLocator = cfi
            // The WebView bridge can fire several 'relocate' events for a single page turn in
            // quick succession; debounce so each one doesn't hit Room.
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

    private suspend fun persistLocatorDeferredByResumeSync() {
        // Give a remote-progress event emitted by the sync one main-loop turn to install its
        // prompt before deciding that the retained local relocation is safe to save.
        yield()
        if (resumeProgressSyncGate.blocksPositionWrites) return
        if (_readingPositionPrompt.value != null) {
            locatorDeferredByResumeSync = null
            return
        }
        val locator = locatorDeferredByResumeSync ?: return
        locatorDeferredByResumeSync = null
        persistReaderLocator(locator)
    }

    fun onReaderInteraction() {
        if (!readerResumed || !bookOpen) return
        persistReadingTime(readingTimeTracker.interact(System.currentTimeMillis()))
        startReadingTimeTicker()
    }

    private fun requestAutoProgressSync(
        force: Boolean = false,
        resumeGeneration: Long? = null,
    ) {
        autoProgressSyncRequested = true
        autoProgressSyncForceRequested = autoProgressSyncForceRequested || force
        if (resumeGeneration != null) autoProgressSyncResumeGeneration = resumeGeneration
        val runningJob = autoProgressSyncJob
        if (runningJob?.isActive == true) {
            return
        }
        autoProgressSyncJob = viewModelScope.launch {
            do {
                autoProgressSyncRequested = false
                val forceThisRun = autoProgressSyncForceRequested
                autoProgressSyncForceRequested = false
                val resumeGenerationThisRun = autoProgressSyncResumeGeneration
                autoProgressSyncResumeGeneration = null
                _syncStatus.value = ReaderSyncStatus.Syncing
                val result = try {
                    readingProgressOnlySyncer.syncReadingProgress(force = forceThisRun)
                } catch (throwable: Throwable) {
                    val released = resumeGenerationThisRun?.let(resumeProgressSyncGate::onSyncFinished) == true
                    if (released) locatorDeferredByResumeSync = null
                    throw throwable
                }
                val released = resumeGenerationThisRun?.let(resumeProgressSyncGate::onSyncFinished) == true
                if (released) {
                    if (result.remoteCheckCompleted) {
                        viewModelScope.launch { persistLocatorDeferredByResumeSync() }
                    } else {
                        // This relocation was emitted automatically by the surviving WebView on
                        // resume. If cloud state was not checked, timestamping it as fresh could
                        // later overwrite progress made on another device. A real page turn after
                        // the gate opens will still persist normally.
                        locatorDeferredByResumeSync = null
                    }
                }
                // Throttled means we didn't actually check anything; looping immediately would
                // just spin until the window clears. A later page turn will trigger a fresh call.
                // A queued forced resume check is different: let the loop run it immediately.
                if (result.status == ReadingProgressSyncStatus.THROTTLED) {
                    // Nothing ran, so the dot must not keep claiming a sync is in flight. Fall back to
                    // whatever the last real attempt concluded rather than inventing a fresh verdict.
                    _syncStatus.value = lastSettledSyncStatus
                    if (!autoProgressSyncRequested) break
                    continue
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
            ReadingProgressSyncStatus.CONFIG_INCOMPLETE -> ReaderSyncStatus.Failed(UiText.Res(R.string.reader_sync_config_incomplete))
            ReadingProgressSyncStatus.CLOUD_MISSING -> ReaderSyncStatus.Failed(UiText.Res(R.string.reader_sync_cloud_missing))
            ReadingProgressSyncStatus.FAILED -> ReaderSyncStatus.Failed(result.failureMessage?.let(UiText::Raw))
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
        readAloudStatusHolder.clear(bookId)
    }

    private fun showReturnRecap(previousReadAt: Long?) {
        val awayMillis = System.currentTimeMillis() - (previousReadAt ?: return)
        if (awayMillis < ReturnRecapMinAwayMillis) return
        viewModelScope.launch {
            val annotations = annotationRepository.observeForBook(bookId).first()
            val highlight = annotations
                .firstOrNull { it.type != AnnotationType.BOOKMARK && it.selectedText.isNotBlank() && !it.isCommunityQuote() }
                ?.selectedText
            _returnRecap.value = ReaderRecap(
                awayMillis = awayMillis,
                highlight = highlight,
                dueWords = vocabularyCardRepository.observeDueCount().first(),
                journalText = annotations.filter { it.type == AnnotationType.NOTE && it.colorKey == "journal" }
                    .maxByOrNull { it.createdAt }?.readerNote?.takeIf { it.isNotBlank() },
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
            val exact = existing.exactEditableMark(selection.cfi, type)
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
            if (exact != null) {
                annotationRepository.update(
                    exact.copy(
                        colorKey = colorKey,
                        selectedText = selection.selectedText.ifBlank { exact.selectedText },
                        chapterTitle = selection.chapterTitle ?: exact.chapterTitle,
                        readerNote = readerNote?.takeIf { it.isNotBlank() } ?: exact.readerNote,
                    ),
                )
            } else if (union != null && swallowed.isNotEmpty()) {
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
            _dictionaryLookup.value = selectedText?.toLookupPhrase()?.let(DictionaryLookupState::Phrase)
                ?: DictionaryLookupState.Hidden
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
                    throwable.uiText(R.string.reader_error_dictionary_lookup_failed),
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
        is DictionaryLookupState.NotFound -> state.word
        is DictionaryLookupState.Online -> state.word
        is DictionaryLookupState.Phrase -> state.phrase
        else -> pendingDictionaryWord
    }
}

internal fun SettingsSnapshot.lookupWriterOrigin(): String =
    kindleDeviceName.trim().ifBlank { "Vayana Sync" }

private val SettingsSnapshot.selectedImportedFont
    get() = readerCustomFontId?.let { selectedId -> readerImportedFonts.firstOrNull { it.id == selectedId } }

private val SettingsSnapshot.readerFontFamilyCss: String
    get() = if (selectedImportedFont != null) "'VayanaImportedReaderFont', serif" else readerFontFamily.cssFamily

internal fun SettingsSnapshot.toBookStyle(pdfRotationDegrees: Int = 0): BookStyle = BookStyle(
    fontSizePercent = readerFontSizePercent,
    lineHeight = readerLineHeight,
    fontFamily = readerFontFamilyCss,
    customFontFileName = selectedImportedFont?.fileName,
    sideMarginPercent = readerSideMarginPercent,
    overridePublisherTypography = !readerUsePublisherStyles,
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
    pdfCropMargins = readerPdfCropMargins,
    pdfFitWidth = readerPdfFitWidth,
    pdfRotationDegrees = pdfRotationDegrees,
)

private val ReaderFontFamily.cssFamily: String
    get() = when (this) {
        ReaderFontFamily.SERIF -> "'Libron', serif"
        ReaderFontFamily.SANS -> "sans-serif"
        ReaderFontFamily.MONO -> "monospace"
    }

internal val SettingsSnapshot.readTheme: ReadTheme
    get() = when {
        displayProfile.isMonochrome(einkPalette) -> Palette.EinkBackground.toReadTheme(Palette.EinkForeground)
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
    }.copy(
        eink = displayProfile == DisplayProfile.E_INK,
        monochrome = displayProfile.isMonochrome(einkPalette),
    )

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
    if (type == AnnotationType.BOOKMARK || (type == AnnotationType.NOTE && colorKey == "journal")) return null
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

internal fun List<Annotation>.exactEditableMark(selectionCfi: String, type: AnnotationType): Annotation? =
    takeUnless { type == AnnotationType.NOTE }
        ?.firstOrNull { it.type == type && it.locator == selectionCfi && !it.isCommunityQuote() }

private fun AnnotationType.toReaderAnnotationType(): ReaderAnnotationType = when (this) {
    AnnotationType.HIGHLIGHT -> ReaderAnnotationType.HIGHLIGHT
    AnnotationType.UNDERLINE -> ReaderAnnotationType.UNDERLINE
    AnnotationType.BOOKMARK -> ReaderAnnotationType.BOOKMARK
    AnnotationType.NOTE -> ReaderAnnotationType.NOTE
}

internal fun String.toDictionaryWord(): String? {
    val candidate = trim().trim(*SelectionEdgePunctuation)
    return candidate.takeIf { DictionarySelectionWordRegex.matches(it) }
}

/**
 * A short multi-word selection to look up whole on the web, with its line breaks and runs of spaces made single
 * spaces. Null for one word, a long passage, or text broken by quotes, brackets or sentence punctuation: those are
 * reading, not a name or an idiom. Full stops stay allowed, for "St. Petersburg" and "J. R. R. Tolkien".
 */
internal fun String.toLookupPhrase(): String? {
    // Selections change on every handle drag and can span pages: rule long ones out before any regex work. Even
    // collapsed whitespace can't shrink a phrase's worth of text past this.
    if (length > MaxLookupPhraseChars * MaxRawPhraseCharsFactor) return null
    val phrase = trim().trim(*SelectionEdgePunctuation).replace(SelectionWhitespaceRegex, " ")
    if (phrase.length > MaxLookupPhraseChars || phrase.any { it in PhraseBreakCharacters }) return null
    val words = phrase.split(' ')
    if (words.size !in 2..MaxLookupPhraseWords || words.none { word -> word.any(Char::isLetter) }) return null
    return phrase
}

private val SelectionEdgePunctuation = charArrayOf('“', '”', '‘', '’', '\'', '"', '.', ',', ';', ':', '!', '?', '(', ')', '[', ']')
private const val PhraseBreakCharacters = "!?;:\"“”()[]{}…"
private val SelectionWhitespaceRegex = Regex("\\s+")
private const val MaxLookupPhraseWords = 6
private const val MaxLookupPhraseChars = 80
private const val MaxRawPhraseCharsFactor = 2

private const val SpeechInteractionIntervalMillis = 30_000L
private const val MaxChapterWords = 25

/** Reopening a book within this long of last reading it is just carrying on, not a return worth a recap. */
private const val ReturnRecapMinAwayMillis = 12 * 60 * 60 * 1000L

private const val DefaultAnnotationColor = "yellow"
private const val CfiPrefix = "epubcfi("
private const val DefaultBookmarkColor = "bookmark"
private const val StyleUpdateDebounceMillis = 80L
private const val PaceSaveDebounceMillis = 10_000L
private const val LocatorPersistDebounceMillis = 400L
private const val RecentLookupsLimit = 5
private const val MaxPdfThumbnailCache = 72
private const val PdfPreferencesFile = "pdf_reader_preferences"

/** No page turn for this long ends the current reading session (PROMPT: idle stops a session). */
private const val IdleSessionTimeoutMs = 5 * 60 * 1000L
private const val SessionContinuationGraceMs = 60 * 1000L

private const val SearchDebounceMillis = 400L
// Letters and the marks joined to them: Malayalam and Tamil vowel signs (and viramas) are marks, not letters.
private val DictionarySelectionWordRegex = Regex("^[\\p{L}\\p{M}]+(?:['’\\-][\\p{L}\\p{M}]+)*$")

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

private const val FromStartKey = "fromStart"
private const val ReadAloudKey = "readAloud"
