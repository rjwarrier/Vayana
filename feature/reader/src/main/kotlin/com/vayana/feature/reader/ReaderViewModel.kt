package com.vayana.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.datastore.settings.ReaderFontFamily
import com.vayana.core.datastore.settings.ReaderTheme
import com.vayana.core.datastore.settings.SettingsRegistry
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.ThemeMode
import com.vayana.core.filesystem.StorageRoots
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Loaded(
        val bookTitle: String,
        val toc: List<com.vayana.reader.api.TocEntry>,
        val currentLocator: Locator?,
        val annotations: List<Annotation> = emptyList(),
        val selection: ReaderSelection? = null,
    ) : ReaderUiState
    data class Failed(val message: String) : ReaderUiState
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val annotationRepository: AnnotationRepository,
    private val storageRoots: StorageRoots,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val bookId: Long = checkNotNull(savedStateHandle["bookId"])

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState

    val settings: StateFlow<SettingsSnapshot> = settingsRepository.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsSnapshot())

    private var boundEngine: BookEngine? = null
    private var bookOpen = false
    private val engineJobs = mutableListOf<Job>()

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
            val resumeLocator = book.lastLocator?.let {
                Locator(cfi = it, href = null, progression = book.readingPercent, chapterTitle = null)
            }

            engine.open(source, resumeLocator)
                .onSuccess { openBook: OpenBook ->
                    bookOpen = true
                    applyReaderStyle(engine, settings.value)
                    _uiState.value = ReaderUiState.Loaded(bookTitle = openBook.title, toc = openBook.toc, currentLocator = resumeLocator)
                    observeAnnotations(engine)
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

    fun createHighlight(colorKey: String = DefaultAnnotationColor) {
        createAnnotation(type = AnnotationType.HIGHLIGHT, colorKey = colorKey, readerNote = null)
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
            _uiState.update { current ->
                if (current is ReaderUiState.Loaded) current.copy(selection = null) else current
            }
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

    override fun onCleared() {
        cancelEngineJobs()
        boundEngine = null
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
            _uiState.update { current ->
                if (current is ReaderUiState.Loaded) current.copy(selection = null) else current
            }
        }
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
        readerTheme == ReaderTheme.LIGHT -> ReadTheme(backgroundColorArgb = 0xFFFFFFFF.toInt(), textColorArgb = 0xFF172033.toInt())
        readerTheme == ReaderTheme.SEPIA -> ReadTheme(backgroundColorArgb = 0xFFFFFBF3.toInt(), textColorArgb = 0xFF172033.toInt())
        readerTheme == ReaderTheme.DARK -> ReadTheme(backgroundColorArgb = 0xFF111827.toInt(), textColorArgb = 0xFFF8F4EC.toInt())
        displayProfile == DisplayProfile.E_INK -> ReadTheme(backgroundColorArgb = 0xFFFFFFFF.toInt(), textColorArgb = 0xFF000000.toInt())
        themeMode == ThemeMode.DARK -> ReadTheme(backgroundColorArgb = 0xFF111827.toInt(), textColorArgb = 0xFFF8F4EC.toInt())
        else -> ReadTheme(backgroundColorArgb = 0xFFFFFBF3.toInt(), textColorArgb = 0xFF172033.toInt())
    }

private fun Annotation.toReaderAnnotation(): ReaderAnnotation? {
    val cfi = locator.takeIf { it.isNotBlank() } ?: return null
    return ReaderAnnotation(
        id = id.toString(),
        type = type.toReaderAnnotationType(),
        cfi = cfi,
        colorKey = colorKey,
        note = readerNote,
    )
}

private fun AnnotationType.toReaderAnnotationType(): ReaderAnnotationType = when (this) {
    AnnotationType.HIGHLIGHT -> ReaderAnnotationType.HIGHLIGHT
    AnnotationType.UNDERLINE -> ReaderAnnotationType.UNDERLINE
    AnnotationType.BOOKMARK -> ReaderAnnotationType.BOOKMARK
    AnnotationType.NOTE -> ReaderAnnotationType.NOTE
}

private const val DefaultAnnotationColor = "yellow"
private const val DefaultBookmarkColor = "bookmark"
private const val StyleUpdateDebounceMillis = 80L
