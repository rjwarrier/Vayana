package com.vayana.feature.reader

import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.ReadingSessionRepository
import com.vayana.core.database.repository.VocabularyCardRepository
import com.vayana.core.database.repository.WordLookupStatRepository
import com.vayana.core.datastore.settings.ReaderFontFamily
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
import com.vayana.reader.api.BookStyle
import com.vayana.reader.api.BookSource
import com.vayana.reader.api.Locator
import com.vayana.reader.api.NavTarget
import com.vayana.reader.api.OpenBook
import com.vayana.reader.api.ReadTheme
import com.vayana.reader.api.ReaderAnnotation
import com.vayana.reader.api.ReaderAnnotationType
import com.vayana.reader.api.ReaderSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One-shot snackbar-worthy outcomes from background reading-progress sync. */
sealed interface ReaderSyncMessage {
    data class Pushed(val count: Int) : ReaderSyncMessage
    data class Pulled(val count: Int) : ReaderSyncMessage
    data class Synced(val pulled: Int, val pushed: Int) : ReaderSyncMessage
    data object ConfigIncomplete : ReaderSyncMessage
    data object CloudMissing : ReaderSyncMessage
    data class Failed(val reason: String?) : ReaderSyncMessage
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

sealed interface DictionaryLookupState {
    data object Hidden : DictionaryLookupState
    data class PackRequired(val word: String) : DictionaryLookupState
    data class LookingUp(val word: String) : DictionaryLookupState
    data class Found(val entry: DictionaryEntry) : DictionaryLookupState
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
                readerSideMarginPercent = override.sideMarginPercent ?: snapshot.readerSideMarginPercent,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsSnapshot())

    val recentLookups: StateFlow<List<String>> = wordLookupStatRepository.observeRecent(RecentLookupsLimit)
        .map { stats -> stats.map { it.word } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var boundEngine: BookEngine? = null
    private var bookOpen = false
    private val engineJobs = mutableListOf<Job>()
    private var dictionaryLookupJob: Job? = null
    private var dictionaryInstallJob: Job? = null
    private var pendingDictionaryWord: String? = null
    private var dictionaryPickerActive = false
    private var lastUsedHighlightColor: String = DefaultAnnotationColor
    private var autoMarkedSelectionCfi: String? = null
    private val pageTurnAutoSyncGate = PageTurnAutoSyncGate(AutoSyncEveryPages)
    private var autoProgressSyncJob: Job? = null
    private var autoProgressSyncRequested = false
    private val _syncMessages = Channel<ReaderSyncMessage>(Channel.BUFFERED)
    val syncMessages: Flow<ReaderSyncMessage> = _syncMessages.receiveAsFlow()
    private var lastSyncIssueSignature: String? = null
    private var lastReaderWrittenLocator: String? = null
    private var locatorPersistJob: Job? = null
    private var pendingRemoteReadingPosition: SavedReadingPosition? = null
    private val readingPositionPromptDecider = ReadingPositionPromptDecider()

    /** Called once the [BookEngine] exists (i.e. once the WebView has been created by the Compose factory). */
    @OptIn(FlowPreview::class)
    fun bindEngine(engine: BookEngine) {
        if (boundEngine === engine) return
        cancelEngineJobs()
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
            val initialLocatorString = targetLocator?.takeIf { it.isNotBlank() }
            val resumeLocator = initialLocatorString?.let {
                Locator(cfi = it, href = null, progression = book.readingPercent, chapterTitle = null)
            }

            engine.open(source, resumeLocator)
                .onSuccess { openBook: OpenBook ->
                    bookOpen = true
                    viewModelScope.launch { bookRepository.recordBookOpened(bookId) }
                    if (readerResumed) onResume()
                    applyReaderStyle(engine, effectiveSettings.value)
                    _uiState.value = ReaderUiState.Loaded(
                        bookTitle = openBook.title,
                        bookAuthor = book.author,
                        toc = openBook.toc,
                        currentLocator = resumeLocator,
                    )
                    observeAnnotations(engine)
                    if (!targetLocator.isNullOrBlank()) {
                        engine.goTo(NavTarget.ToLocator(Locator(cfi = targetLocator, href = null, progression = 0f, chapterTitle = null)))
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
                        locatorPersistJob?.cancel()
                        locatorPersistJob = viewModelScope.launch {
                            delay(LocatorPersistDebounceMillis)
                            bookRepository.updateLocator(bookId, cfi, locator.progression)
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
                    is com.vayana.reader.api.EngineEvent.Error,
                    is com.vayana.reader.api.EngineEvent.Relocated,
                    -> Unit
                }
            }
        }

        engineJobs += viewModelScope.launch {
            effectiveSettings.debounce(StyleUpdateDebounceMillis).collectLatest { snapshot ->
                if (bookOpen) applyReaderStyle(engine, snapshot)
            }
        }
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
            viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderFontFamily, fontFamily) }
        }
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
                    fontFamily = snapshot.readerFontFamily,
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
        if (!settings.value.readerAutoMarkSelection || selection.cfi == autoMarkedSelectionCfi) return
        autoMarkedSelectionCfi = selection.cfi
        createHighlight(lastUsedHighlightColor)
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
        engine.applyStyle(
            style = BookStyle(
                fontSizePercent = snapshot.readerFontSizePercent,
                lineHeight = snapshot.readerLineHeight,
                fontFamily = snapshot.readerFontFamily.cssFamily,
                sideMarginPercent = snapshot.readerSideMarginPercent,
                bionicReading = snapshot.readerBionicReading,
            ),
            theme = snapshot.readTheme,
        )
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

    private val readingTimeTracker = ReadingTimeTracker(IdleSessionTimeoutMs)
    private var readerResumed = false
    private var trackingJob: Job? = null

    fun onResume() {
        readerResumed = true
        if (bookOpen) {
            readingTimeTracker.resume(System.currentTimeMillis())
            startReadingTimeTicker()
        }
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
        val job = locatorPersistJob ?: return
        if (!job.isActive) return
        job.cancel()
        locatorPersistJob = null
        val currentLocator = (uiState.value as? ReaderUiState.Loaded)?.currentLocator ?: return
        val cfi = currentLocator.cfi?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            bookRepository.updateLocator(bookId, cfi, currentLocator.progression)
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
            bookRepository.updateLocator(bookId, currentCfi, currentLocator.progression)
        }
    }

    private suspend fun onPageMoved(locator: Locator) {
        if (!readerResumed || !bookOpen) return
        persistReadingTimeNow(readingTimeTracker.interact(System.currentTimeMillis()))
        if (pageTurnAutoSyncGate.onLocator(locator)) {
            requestAutoProgressSync()
        }
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
                val result = readingProgressOnlySyncer.syncReadingProgress()
                // Throttled means we didn't actually check anything; looping immediately would
                // just spin until the window clears. A later page turn will trigger a fresh call.
                if (result.status == ReadingProgressSyncStatus.THROTTLED) break
                emitSyncMessage(result)
            } while (autoProgressSyncRequested)
        }
    }

    private fun emitSyncMessage(result: ReadingProgressSyncResult) {
        val message: ReaderSyncMessage = when (result.status) {
            ReadingProgressSyncStatus.PUSHED -> ReaderSyncMessage.Pushed(result.pushed)
            ReadingProgressSyncStatus.PULLED -> ReaderSyncMessage.Pulled(result.pulled)
            ReadingProgressSyncStatus.SYNCED -> ReaderSyncMessage.Synced(pulled = result.pulled, pushed = result.pushed)
            ReadingProgressSyncStatus.CONFIG_INCOMPLETE -> ReaderSyncMessage.ConfigIncomplete
            ReadingProgressSyncStatus.CLOUD_MISSING -> ReaderSyncMessage.CloudMissing
            ReadingProgressSyncStatus.FAILED -> ReaderSyncMessage.Failed(result.failureMessage)
            ReadingProgressSyncStatus.NO_CHANGES,
            ReadingProgressSyncStatus.THROTTLED,
            ReadingProgressSyncStatus.SYNC_DISABLED,
            -> return
        }
        // Issues repeat identically every auto-sync trigger while the underlying cause persists
        // (e.g. offline). Only surface a given issue once until either it changes or a sync succeeds.
        val issueSignature = when (message) {
            is ReaderSyncMessage.ConfigIncomplete -> "config"
            is ReaderSyncMessage.CloudMissing -> "cloud_missing"
            is ReaderSyncMessage.Failed -> "failed:${message.reason}"
            else -> null
        }
        if (issueSignature != null) {
            if (issueSignature == lastSyncIssueSignature) return
            lastSyncIssueSignature = issueSignature
        } else {
            lastSyncIssueSignature = null
        }
        _syncMessages.trySend(message)
    }

    private fun startReadingTimeTicker() {
        trackingJob?.cancel()
        trackingJob = viewModelScope.launch {
            while (true) {
                delay(10_000L)
                persistReadingTime(readingTimeTracker.flush(System.currentTimeMillis()))
            }
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
        update.session?.let { session ->
            readingSessionRepository.record(bookId, session.startedAt, session.endedAt)
        }
    }

    override fun onCleared() {
        trackingJob?.cancel()
        trackingJob = null
        flushPendingLocatorWrite()
        cancelEngineJobs()
        boundEngine = null
        dictionaryLookupJob?.cancel()
        dictionaryInstallJob?.cancel()
        autoProgressSyncJob?.cancel()
        autoProgressSyncJob = null
    }

    private fun cancelEngineJobs() {
        engineJobs.forEach { it.cancel() }
        engineJobs.clear()
    }

    private fun createAnnotation(type: AnnotationType, colorKey: String = DefaultAnnotationColor, readerNote: String?) {
        val engine = boundEngine ?: return
        val selection = (uiState.value as? ReaderUiState.Loaded)?.selection ?: return
        viewModelScope.launch {
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
            wordLookupStatRepository.recordLookup(word)
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
                DictionaryLookupState.Found(entry)
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

private const val DefaultAnnotationColor = "yellow"
private const val DefaultBookmarkColor = "bookmark"
private const val StyleUpdateDebounceMillis = 80L
private const val LocatorPersistDebounceMillis = 400L
private const val RecentLookupsLimit = 5
private const val AutoSyncEveryPages = 3

/** No page turn for this long ends the current reading session (PROMPT: idle stops a session). */
private const val IdleSessionTimeoutMs = 5 * 60 * 1000L

private const val SearchDebounceMillis = 400L
private val DictionarySelectionWordRegex = Regex("^[\\p{L}]+(?:['’\\-][\\p{L}]+)*$")
