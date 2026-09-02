package com.vayana.feature.reader

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vayana.core.database.model.Annotation
import com.vayana.core.datastore.settings.ReaderFontFamily
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.designsystem.tokens.Paddings
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
        onFontSizeChange = viewModel::updateFontSize,
        onLineHeightChange = viewModel::updateLineHeight,
        onFontFamilyChange = viewModel::updateFontFamily,
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
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onFontFamilyChange: (ReaderFontFamily) -> Unit,
    onBack: () -> Unit,
) {
    var chromeVisible by remember { mutableStateOf(false) }
    var selectedPanel by remember { mutableStateOf(ReaderPanel.CONTENTS) }
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
                        else -> chromeVisible = !chromeVisible
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
            )
        }
    }
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
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = Spacing.xs,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Paddings.screenHorizontal, vertical = Spacing.sm),
        ) {
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
                )
                ReaderPanel.NOTES -> NotesPanel(uiState = uiState, onAnnotationClick = onAnnotationClick)
                ReaderPanel.READ_ALOUD, ReaderPanel.SEARCH -> ComingSoonPanel()
            }
        }
    }
}

@Composable
private fun ReaderPanelButton(icon: ImageVector, labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.padding(top = Spacing.xs)) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(labelRes),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.xs),
        )
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
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Text(text = stringResource(R.string.settings_reader_font_size_title), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = settings.readerFontSizePercent.toFloat(),
            onValueChange = { onFontSizeChange((it / 5f).roundToInt() * 5) },
            valueRange = 80f..160f,
            steps = 15,
        )
        Text(text = stringResource(R.string.settings_reader_line_height_title), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = settings.readerLineHeight,
            onValueChange = { onLineHeightChange(((it / 0.1f).roundToInt() * 0.1f).coerceIn(1.2f, 2.0f)) },
            valueRange = 1.2f..2.0f,
            steps = 7,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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

private data class TocDisplayItem(val entry: TocEntry, val depth: Int)

private fun List<TocEntry>.flattenToc(depth: Int = 0): List<TocDisplayItem> =
    flatMap { entry -> listOf(TocDisplayItem(entry, depth)) + entry.children.flattenToc(depth + 1) }
