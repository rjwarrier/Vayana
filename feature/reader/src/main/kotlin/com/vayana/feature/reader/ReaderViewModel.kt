package com.vayana.feature.reader

import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Loaded(
        val bookTitle: String,
        val bookAuthor: String? = null,
        val toc: List<com.vayana.reader.api.TocEntry>,
        val currentLocator: Locator?,
        val annotations: List<Annotation> = emptyList(),
        val selection: ReaderSelection? = null,
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
) : ViewModel() {

    val bookId: Long = checkNotNull(savedStateHandle["bookId"])
    val targetLocator: String? = savedStateHandle["targetLocator"]

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState

    private val _dictionaryLookup = MutableStateFlow<DictionaryLookupState>(DictionaryLookupState.Hidden)
    val dictionaryLookup: StateFlow<DictionaryLookupState> = _dictionaryLookup

    val settings: StateFlow<SettingsSnapshot> = settingsRepository.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsSnapshot())

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

    /** Called once the [BookEngine] exists (i.e. once the WebView has been created by the Compose factory). */
    @OptIn(FlowPreview::class)
    fun bindEngine(engine: BookEngine) {
        if (boundEngine === engine) return
        cancelEngineJobs()
        boundEngine = engine
        bookOpen = false

        engineJobs += viewModelScope.launch {
            val book = bookRepository.getById(bookId)
            if (book == null) {
                _uiState.value = ReaderUiState.Failed("Book not found")
                return@launch
            }

            val source = BookSource(storageRoots.resolve(book.filePath).absolutePath)
            val initialLocatorString = targetLocator?.takeIf { it.isNotBlank() } ?: book.lastLocator
            val resumeLocator = initialLocatorString?.let {
                Locator(cfi = it, href = null, progression = book.readingPercent, chapterTitle = null)
            }

            engine.open(source, resumeLocator)
                .onSuccess { openBook: OpenBook ->
                    bookOpen = true
                    viewModelScope.launch { bookRepository.recordBookOpened(bookId) }
                    onResume()
                    applyReaderStyle(engine, settings.value)
                    _uiState.value = ReaderUiState.Loaded(
                        bookTitle = openBook.title,
                        bookAuthor = book.author,
                        toc = openBook.toc,
                        currentLocator = resumeLocator,
                    )
                    observeAnnotations(engine)
                    if (!targetLocator.isNullOrBlank()) {
                        engine.goTo(NavTarget.ToLocator(Locator(cfi = targetLocator, href = null, progression = 0f, chapterTitle = null)))
                    }
                }
                .onFailure { throwable ->
                    _uiState.value = ReaderUiState.Failed(throwable.message ?: "Could not open book")
                }
        }

        engineJobs += viewModelScope.launch {
            engine.location.filterNotNull().collect { locator ->
                locator.cfi?.let { cfi -> bookRepository.updateLocator(bookId, cfi, locator.progression) }
                _uiState.update { current ->
                    if (current is ReaderUiState.Loaded) current.copy(currentLocator = locator) else current
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
                    is com.vayana.reader.api.EngineEvent.Error,
                    is com.vayana.reader.api.EngineEvent.Relocated,
                    -> Unit
                }
            }
        }

        engineJobs += viewModelScope.launch {
            settings.debounce(StyleUpdateDebounceMillis).collectLatest { snapshot ->
                if (bookOpen) applyReaderStyle(engine, snapshot)
            }
        }
    }

    fun nextPage() = dispatch(NavTarget.NextPage)
    fun previousPage() = dispatch(NavTarget.PreviousPage)

    fun openTocEntry(href: String) = dispatch(NavTarget.ToHref(href))

    fun goToProgress(fraction: Float) = dispatch(NavTarget.ToFraction(fraction.coerceIn(0f, 1f)))

    fun openAnnotation(annotation: Annotation) {
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

    fun updateFontSize(percent: Int) {
        viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderFontSize, percent) }
    }

    fun updateLineHeight(lineHeight: Float) {
        viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderLineHeight, lineHeight) }
    }

    fun updateFontFamily(fontFamily: ReaderFontFamily) {
        viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderFontFamily, fontFamily) }
    }

    fun updateReaderTheme(theme: ReaderTheme) {
        viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderTheme, theme) }
    }

    fun updateSideMargin(percent: Int) {
        viewModelScope.launch { settingsRepository.update(SettingsRegistry.ReaderSideMargin, percent) }
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

    fun createUnderline() {
        createAnnotation(type = AnnotationType.UNDERLINE, readerNote = null)
    }

    fun createNote(note: String) {
        createAnnotation(type = AnnotationType.NOTE, readerNote = note)
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
            ),
            theme = snapshot.readTheme,
        )
    }

    private fun observeAnnotations(engine: BookEngine) {
        engineJobs += viewModelScope.launch {
            annotationRepository.observeForBook(bookId).collect { annotations ->
                engine.renderAnnotations(annotations.mapNotNull { it.toReaderAnnotation() })
                _uiState.update { current ->
                    if (current is ReaderUiState.Loaded) current.copy(annotations = annotations) else current
                }
            }
        }
    }

    private var activeSessionStart: Long = 0L
    private var trackingJob: Job? = null

    fun onResume() {
        if (bookOpen) {
            activeSessionStart = System.currentTimeMillis()
            startReadingTimeTicker()
        }
    }

    fun onPause() {
        flushReadingTime()
        trackingJob?.cancel()
        trackingJob = null
    }

    private fun startReadingTimeTicker() {
        trackingJob?.cancel()
        trackingJob = viewModelScope.launch {
            while (true) {
                delay(10_000L)
                flushReadingTime()
            }
        }
    }

    private fun flushReadingTime() {
        if (activeSessionStart > 0L) {
            val now = System.currentTimeMillis()
            val elapsedSeconds = ((now - activeSessionStart) / 1000L).coerceAtLeast(0L)
            if (elapsedSeconds > 0L) {
                activeSessionStart = now
                viewModelScope.launch {
                    bookRepository.addReadingTime(bookId, elapsedSeconds)
                }
            }
        }
    }

    override fun onCleared() {
        flushReadingTime()
        trackingJob?.cancel()
        trackingJob = null
        cancelEngineJobs()
        boundEngine = null
        dictionaryLookupJob?.cancel()
        dictionaryInstallJob?.cancel()
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
private const val RecentLookupsLimit = 5
private val DictionarySelectionWordRegex = Regex("^[\\p{L}]+(?:['’\\-][\\p{L}]+)*$")
