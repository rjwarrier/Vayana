package com.vayana.feature.reader

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vayana.core.database.model.Annotation
import com.vayana.core.datastore.settings.ReaderFontFamily
import com.vayana.core.datastore.settings.ReaderTheme
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import com.vayana.reader.api.BookEngine
import com.vayana.reader.api.TocEntry
import com.vayana.reader.web.FoliateBookEngine
import kotlin.math.roundToInt

@Composable
fun ReaderRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ReaderViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()

    ReaderScreen(
        modifier = modifier,
        uiState = uiState,
        settings = settings,
        onEngineReady = viewModel::bindEngine,
        onTapPrevious = viewModel::previousPage,
        onTapNext = viewModel::nextPage,
        onOpenTocEntry = viewModel::openTocEntry,
        onProgressChange = viewModel::goToProgress,
        onAnnotationClick = viewModel::openAnnotation,
        onCreateHighlight = viewModel::createHighlight,
        onCreateUnderline = viewModel::createUnderline,
        onCreateNote = viewModel::createNote,
        onFontSizeChange = viewModel::updateFontSize,
        onLineHeightChange = viewModel::updateLineHeight,
        onFontFamilyChange = viewModel::updateFontFamily,
        onReaderThemeChange = viewModel::updateReaderTheme,
        onSideMarginChange = viewModel::updateSideMargin,
        onBack = onBack,
    )
}

private enum class ReaderPanel { CONTENTS, NOTES, PROGRESS, STYLE, READ_ALOUD, SEARCH }

@Composable
private fun ReaderScreen(
    modifier: Modifier = Modifier,
    uiState: ReaderUiState,
    settings: SettingsSnapshot,
    onEngineReady: (BookEngine) -> Unit,
    onTapPrevious: () -> Unit,
    onTapNext: () -> Unit,
    onOpenTocEntry: (String) -> Unit,
    onProgressChange: (Float) -> Unit,
    onAnnotationClick: (Annotation) -> Unit,
    onCreateHighlight: () -> Unit,
    onCreateUnderline: () -> Unit,
    onCreateNote: (String) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onFontFamilyChange: (ReaderFontFamily) -> Unit,
    onReaderThemeChange: (ReaderTheme) -> Unit,
    onSideMarginChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var chromeVisible by remember { mutableStateOf(false) }
    var selectedPanel by remember { mutableStateOf(ReaderPanel.CONTENTS) }
    var noteDialogVisible by remember { mutableStateOf(false) }
    var containerWidthPx by remember { mutableIntStateOf(0) }
    val onEngineReadyState = rememberUpdatedState(onEngineReady)
    val lifecycleOwner = LocalLifecycleOwner.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Keep WebView lifecycle explicit; Compose disposal is not enough for this hardware surface.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webViewRef?.onPause()
                Lifecycle.Event.ON_RESUME -> webViewRef?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size -> containerWidthPx = size.width }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val third = containerWidthPx / 3f
                    when {
                        offset.x < third -> onTapPrevious()
                        offset.x > third * 2 -> onTapNext()
                        chromeVisible -> chromeVisible = false
                        else -> {
                            selectedPanel = ReaderPanel.STYLE
                            chromeVisible = true
                        }
                    }
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val webView = WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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

        if (chromeVisible) {
            ReaderChrome(
                modifier = Modifier.align(Alignment.BottomStart),
                uiState = uiState,
                settings = settings,
                selectedPanel = selectedPanel,
                onPanelSelected = { selectedPanel = it },
                onBack = onBack,
                onOpenTocEntry = {
                    chromeVisible = false
                    onOpenTocEntry(it)
                },
                onProgressChange = onProgressChange,
                onAnnotationClick = {
                    chromeVisible = false
                    onAnnotationClick(it)
                },
                onFontSizeChange = onFontSizeChange,
                onLineHeightChange = onLineHeightChange,
                onFontFamilyChange = onFontFamilyChange,
                onReaderThemeChange = onReaderThemeChange,
                onSideMarginChange = onSideMarginChange,
            )
        }

        (uiState as? ReaderUiState.Loaded)?.selection?.let { selection ->
            SelectionActions(
                modifier = Modifier.align(Alignment.TopCenter),
                selectedText = selection.selectedText,
                onHighlight = onCreateHighlight,
                onUnderline = onCreateUnderline,
                onNote = { noteDialogVisible = true },
            )
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
}

@Composable
private fun SelectionActions(
    modifier: Modifier = Modifier,
    selectedText: String,
    onHighlight: () -> Unit,
    onUnderline: () -> Unit,
    onNote: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(Paddings.screenHorizontal, Spacing.md),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = Spacing.sm,
        shadowElevation = Spacing.xs,
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = selectedText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FilledTonalButton(onClick = onHighlight) {
                    Text(stringResource(R.string.reader_selection_highlight))
                }
                FilledTonalButton(onClick = onUnderline) {
                    Text(stringResource(R.string.reader_selection_underline))
                }
                FilledTonalButton(onClick = onNote) {
                    Text(stringResource(R.string.reader_selection_note))
                }
            }
        }
    }
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
                label = { Text(stringResource(R.string.reader_note_dialog_label)) },
                minLines = 3,
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
private fun ReaderChrome(
    modifier: Modifier = Modifier,
    uiState: ReaderUiState,
    settings: SettingsSnapshot,
    selectedPanel: ReaderPanel,
    onPanelSelected: (ReaderPanel) -> Unit,
    onBack: () -> Unit,
    onOpenTocEntry: (String) -> Unit,
    onProgressChange: (Float) -> Unit,
    onAnnotationClick: (Annotation) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onFontFamilyChange: (ReaderFontFamily) -> Unit,
    onReaderThemeChange: (ReaderTheme) -> Unit,
    onSideMarginChange: (Int) -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = Radii.extraLarge, topEnd = Radii.extraLarge),
        tonalElevation = Spacing.md,
        shadowElevation = Spacing.sm,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        shape = CircleShape,
                    ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                }
                Text(
                    text = (uiState as? ReaderUiState.Loaded)?.bookTitle.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ReaderPanelButton(Icons.Outlined.Article, R.string.reader_contents, selectedPanel == ReaderPanel.CONTENTS) {
                    onPanelSelected(ReaderPanel.CONTENTS)
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
                ReaderPanelButton(Icons.Outlined.GraphicEq, R.string.reader_read_aloud, selectedPanel == ReaderPanel.READ_ALOUD) {
                    onPanelSelected(ReaderPanel.READ_ALOUD)
                }
                ReaderPanelButton(Icons.Outlined.Search, R.string.reader_search, selectedPanel == ReaderPanel.SEARCH) {
                    onPanelSelected(ReaderPanel.SEARCH)
                }
            }
            when (selectedPanel) {
                ReaderPanel.CONTENTS -> ContentsPanel(uiState = uiState, onOpenTocEntry = onOpenTocEntry)
                ReaderPanel.PROGRESS -> ProgressPanel(uiState = uiState, onProgressChange = onProgressChange)
                ReaderPanel.STYLE -> StylePanel(
                    settings = settings,
                    onFontSizeChange = onFontSizeChange,
                    onLineHeightChange = onLineHeightChange,
                    onFontFamilyChange = onFontFamilyChange,
                    onReaderThemeChange = onReaderThemeChange,
                    onSideMarginChange = onSideMarginChange,
                )
                ReaderPanel.NOTES -> NotesPanel(uiState = uiState, onAnnotationClick = onAnnotationClick)
                ReaderPanel.READ_ALOUD, ReaderPanel.SEARCH -> ComingSoonPanel()
            }
        }
    }
}

@Composable
private fun ReaderPanelButton(icon: ImageVector, labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = if (selected) Spacing.xs else 0.dp,
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
private fun ProgressPanel(uiState: ReaderUiState, onProgressChange: (Float) -> Unit) {
    val progress = (uiState as? ReaderUiState.Loaded)?.currentLocator?.progression ?: 0f
    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Text(
            text = stringResource(R.string.reader_progress_percent, (progress * 100).roundToInt()),
            style = MaterialTheme.typography.titleMedium,
        )
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

@Composable
private fun StylePanel(
    settings: SettingsSnapshot,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onFontFamilyChange: (ReaderFontFamily) -> Unit,
    onReaderThemeChange: (ReaderTheme) -> Unit,
    onSideMarginChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .heightIn(max = Sizes.contentMaxWidth),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
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
            value = "${settings.readerFontSizePercent}%",
        )
        Slider(
            value = settings.readerFontSizePercent.toFloat(),
            onValueChange = { onFontSizeChange((it / 5f).roundToInt() * 5) },
            valueRange = 80f..160f,
            steps = 15,
        )
        ReaderStyleLabel(
            title = stringResource(R.string.settings_reader_line_height_title),
            value = "${(settings.readerLineHeight * 10).roundToInt() / 10f}x",
        )
        Slider(
            value = settings.readerLineHeight,
            onValueChange = { onLineHeightChange(((it / 0.1f).roundToInt() * 0.1f).coerceIn(1.2f, 2.0f)) },
            valueRange = 1.2f..2.0f,
            steps = 7,
        )
        ReaderStyleLabel(
            title = stringResource(R.string.settings_reader_side_margin_title),
            value = "${settings.readerSideMarginPercent}%",
        )
        Slider(
            value = settings.readerSideMarginPercent.toFloat(),
            onValueChange = { onSideMarginChange((it / 2f).roundToInt() * 2) },
            valueRange = 0f..24f,
            steps = 11,
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
private fun ComingSoonPanel() {
    Text(
        text = stringResource(R.string.reader_panel_coming_soon),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(Spacing.lg),
    )
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
    ReaderTheme.SEPIA -> stringResource(R.string.settings_reader_theme_sepia)
    ReaderTheme.DARK -> stringResource(R.string.settings_reader_theme_dark)
}

@Composable
private fun ReaderThemeSwatch(theme: ReaderTheme) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(color = theme.swatchColor(), shape = CircleShape),
    )
}

@Composable
private fun ReaderTheme.swatchColor(): Color = when (this) {
    ReaderTheme.SYSTEM -> MaterialTheme.colorScheme.primary
    ReaderTheme.LIGHT -> Color.White
    ReaderTheme.SEPIA -> Color(0xFFFFE8BF)
    ReaderTheme.DARK -> Color(0xFF111827)
}

private data class TocDisplayItem(val entry: TocEntry, val depth: Int)

private fun List<TocEntry>.flattenToc(depth: Int = 0): List<TocDisplayItem> =
    flatMap { entry -> listOf(TocDisplayItem(entry, depth)) + entry.children.flattenToc(depth + 1) }
