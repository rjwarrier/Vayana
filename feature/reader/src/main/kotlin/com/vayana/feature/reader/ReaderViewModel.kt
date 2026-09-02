package com.vayana.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.datastore.settings.ReaderFontFamily
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

    /** Called once the [BookEngine] exists (i.e. once the WebView has been created by the Compose factory). */
    fun bindEngine(engine: BookEngine) {
        if (boundEngine === engine) return
        boundEngine = engine

        viewModelScope.launch {
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

        viewModelScope.launch {
            engine.location.filterNotNull().collect { locator ->
                locator.cfi?.let { cfi -> bookRepository.updateLocator(bookId, cfi, locator.progression) }
                _uiState.update { current ->
                    if (current is ReaderUiState.Loaded) current.copy(currentLocator = locator) else current
                }
            }
        }

        viewModelScope.launch {
            settings.collect { snapshot ->
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

    private fun dispatch(target: NavTarget) {
        val engine = boundEngine ?: return
        viewModelScope.launch { engine.goTo(target) }
    }

    private fun applyReaderStyle(engine: BookEngine, snapshot: SettingsSnapshot) {
        viewModelScope.launch {
            engine.applyStyle(
                style = BookStyle(
                    fontSizePercent = snapshot.readerFontSizePercent,
                    lineHeight = snapshot.readerLineHeight,
                    fontFamily = snapshot.readerFontFamily.cssFamily,
                ),
                theme = snapshot.readTheme,
            )
        }
    }

    private fun observeAnnotations(engine: BookEngine) {
        viewModelScope.launch {
            annotationRepository.observeForBook(bookId).collect { annotations ->
                engine.renderAnnotations(annotations.mapNotNull { it.toReaderAnnotation() })
                _uiState.update { current ->
                    if (current is ReaderUiState.Loaded) current.copy(annotations = annotations) else current
                }
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
