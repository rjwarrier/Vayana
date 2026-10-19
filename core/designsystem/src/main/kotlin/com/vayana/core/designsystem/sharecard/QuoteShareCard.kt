package com.vayana.core.designsystem.sharecard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.vayana.core.common.QuoteCitation
import com.vayana.core.common.shareText
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
import com.vayana.core.designsystem.theme.rememberCoverColorFilter
import com.vayana.core.designsystem.tokens.FontFamilies
import com.vayana.core.designsystem.tokens.Palette
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.ShareCardTypography
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.tokens.Strokes
import com.vayana.core.resources.R
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Arrangement of the quote card's body; the wordmark and footer are shared by all of them. */
enum class QuoteShareCardLayout {
    /** Quote mark, quote, and the book beside an accent bar (or its cover). */
    CLASSIC,

    /** Everything centred, the cover above the quote. */
    CENTERED,

    /** Larger type and a one-line attribution; no quote mark, no cover. */
    MINIMAL,

    /** [CLASSIC] over the cover stretched across the whole card, faded to the chosen opacity. */
    BACKDROP,
}

/** The card's shape, as width ÷ height: a square post, a 4:5 portrait post, or a 9:16 story. */
enum class QuoteCardShape(val ratio: Float) {
    SQUARE(1f),
    PORTRAIT(4f / 5f),
    STORY(9f / 16f),
}

/** The card's colours: the app's own (light or dark, see [ShareCardTheme]) or a fixed palette. */
enum class QuoteCardPalette { APP, PAPER, SEPIA, ROSE, FOREST, MIDNIGHT, INK }

/** Typeface for the quote or the book title; all bundled, so the image looks the same wherever it lands. */
enum class QuoteCardFont { SERIF, SANS, MONO }

/** The quote's weight. Playfair (Serif) starts at Regular, so Light looks the same as Regular there. */
enum class QuoteCardWeight(val fontWeight: FontWeight) {
    LIGHT(FontWeight.Light),
    REGULAR(FontWeight.Normal),
    MEDIUM(FontWeight.Medium),
    BOLD(FontWeight.Bold),
}

/** How the quote lines up: the layout's own choice, or forced to the start or centre. */
enum class QuoteCardAlign { AUTO, START, CENTER }

/** What the viewer picked for the quote card; the editor keeps it while it is open. */
data class QuoteShareOptions(
    val layout: QuoteShareCardLayout = QuoteShareCardLayout.CLASSIC,
    val theme: ShareCardTheme = ShareCardTheme.DARK,
    val palette: QuoteCardPalette = QuoteCardPalette.APP,
    val shape: QuoteCardShape = QuoteCardShape.SQUARE,
    val font: QuoteCardFont = QuoteCardFont.SERIF,
    val titleFont: QuoteCardFont = QuoteCardFont.SERIF,
    val weight: QuoteCardWeight = QuoteCardWeight.MEDIUM,
    val italic: Boolean = false,
    val align: QuoteCardAlign = QuoteCardAlign.AUTO,
    val backdropOpacity: Float = DefaultBackdropCoverAlpha,
    /** Space between the quote and the book details, as a multiple of the layout's own spacing. */
    val detailsGap: Float = 1f,
    val showDetailsDivider: Boolean = false,
    val showCover: Boolean = false,
    val showQuoteMark: Boolean = true,
    val showBookTitle: Boolean = true,
    val showAuthor: Boolean = true,
    val showSeries: Boolean = true,
    val showCaption: Boolean = true,
    val showTagline: Boolean = true,
    /** The reader's seal ([ShareSeal]) at the top right, when one is set. */
    val showSeal: Boolean = true,
    /** The reader's profile name ([ShareProfile]) at the bottom right, when one is set. */
    val showProfileName: Boolean = true,
    /** The seal's size, as a multiple of the wordmark-sized default. */
    val sealScale: Float = 1f,
    val quoteFontScale: Float = 1f,
)

/**
 * A full-screen quote-card editor: the card previewed at the top (scaled to fit whatever its shape), the
 * appearance in tabs below it, and Share text / Share image along the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteShareDialog(
    text: String,
    author: String?,
    bookTitle: String?,
    chapterTitle: String?,
    onDismiss: () -> Unit,
    coverPath: String? = null,
    series: String? = null,
    seriesNumber: String? = null,
) {
    val seriesLabel = shareSeriesLabel(series, seriesNumber)
    val context = LocalContext.current
    val textChooserTitle = stringResource(R.string.share_card_share_text)
    val citationPattern = stringResource(R.string.quote_citation)
    // The last quote's look, kept across quotes and app restarts (QuoteShareOptionsStore).
    var options by remember { mutableStateOf(QuoteShareOptionsStore.load(context)) }
    LaunchedEffect(options) { QuoteShareOptionsStore.save(context, options) }
    var editedText by remember(text) { mutableStateOf(text) }
    val sharedText = editedText.trim().ifEmpty { text.trim() }
    val availableCover = remember(coverPath) { coverPath?.takeIf { File(it).exists() } }

    ShareCardEditor(
        title = stringResource(R.string.share_quote_title),
        shape = options.shape,
        tabs = EditorTabs.map { stringResource(it) },
        onDismiss = onDismiss,
        onReset = { options = QuoteShareOptions(); editedText = text },
        onShareText = {
            context.shareText(QuoteCitation.format(sharedText, author, bookTitle, chapterTitle, citationPattern), textChooserTitle)
        },
        card = {
            QuoteShareCard(
                text = editedText,
                author = author,
                bookTitle = bookTitle,
                series = seriesLabel,
                coverPath = availableCover,
                watermark = stringResource(R.string.share_card_watermark),
                footerRight = stringResource(R.string.share_card_tagline),
                options = options,
            )
        },
    ) { tab ->
        val change: (QuoteShareOptions) -> Unit = { options = it }
        when (tab) {
            0 -> QuoteTextTab(options, editedText, { editedText = it.take(MaxShareQuoteCharacters) }, change)
            1 -> QuoteLayoutTab(options, hasCover = availableCover != null, change)
            2 -> ShareCardPalettePicker(
                palette = options.palette,
                theme = options.theme,
                onPaletteChange = { change(options.copy(palette = it)) },
                onThemeChange = { change(options.copy(theme = it)) },
            )
            else -> QuoteIncludeTab(
                options = options,
                hasCover = availableCover != null,
                hasBookTitle = !bookTitle.isNullOrBlank(),
                hasAuthor = !author.isNullOrBlank(),
                hasSeries = seriesLabel != null,
                onOptionsChange = change,
            )
        }
    }
}

/**
 * A full-screen share-card editor: [card] previewed at the top (scaled to fit whatever its [shape]), [tabs] of
 * appearance controls below it ([tabContent] draws the selected one), and Share text / Share image along the bottom.
 * The shared image is the card recorded at the export size, never a screenshot of the preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCardEditor(
    title: String,
    shape: QuoteCardShape,
    tabs: List<String>,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onShareText: () -> Unit,
    card: @Composable () -> Unit,
    shareImageFileName: String? = null,
    tabContent: @Composable (tab: Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLayer = rememberGraphicsLayer()
    var capturing by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val textChooserTitle = stringResource(R.string.share_card_share_text)
    val imageChooserTitle = stringResource(R.string.share_card_image_chooser_title)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.share_quote_close))
                        }
                    },
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        TextButton(onClick = onReset) { Text(stringResource(R.string.share_quote_reset)) }
                    },
                )
            },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = Paddings.screenHorizontal, vertical = Spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        OutlinedButton(onClick = { onShareText(); onDismiss() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Outlined.ShortText, null, Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(textChooserTitle, maxLines = 1)
                        }
                        Button(
                            onClick = {
                                capturing = true
                                scope.launch {
                                    try {
                                        exportLayer.shareCardImage(context, imageChooserTitle, shareImageFileName)
                                        onDismiss()
                                    } finally {
                                        capturing = false
                                    }
                                }
                            },
                            enabled = !capturing,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (capturing) {
                                VayanaCircularProgressIndicator(
                                    modifier = Modifier.size(Sizes.iconSmall),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(Icons.Outlined.Image, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.share_card_share_image), maxLines = 1)
                            }
                        }
                    }
                }
            },
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
            ) {
                val previewHeight = maxHeight * PreviewHeightFraction
                val previewWidth = maxWidth - Paddings.screenHorizontal * 2
                Column(Modifier.fillMaxSize()) {
                    ShareCardPreview(
                        shape = shape,
                        maxWidth = previewWidth,
                        maxHeight = previewHeight - Spacing.md * 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewHeight),
                        exportLayer = exportLayer,
                        card = card,
                    )
                    PrimaryTabRow(selectedTabIndex = tab.coerceAtMost(tabs.lastIndex)) {
                        tabs.forEachIndexed { index, label ->
                            Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label, maxLines = 1) })
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Paddings.screenHorizontal, vertical = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        tabContent(tab)
                    }
                }
            }
        }
    }
}

/**
 * The card at its real size, scaled to fit [maxWidth] × [maxHeight] for the screen. The export is recorded inside
 * the scale, so the shared image is always the full-size card whatever the preview's size.
 */
@Composable
private fun ShareCardPreview(
    shape: QuoteCardShape,
    maxWidth: androidx.compose.ui.unit.Dp,
    maxHeight: androidx.compose.ui.unit.Dp,
    exportLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    modifier: Modifier = Modifier,
    card: @Composable () -> Unit,
) {
    val cardWidth = Sizes.shareCardWidth
    val cardHeight = cardWidth / shape.ratio
    val scale = min(maxWidth / cardWidth, maxHeight / cardHeight)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(cardWidth * scale, cardHeight * scale)
                .clip(RoundedCornerShape(Radii.large)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(cardWidth, cardHeight)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .recordShareCard(exportLayer),
            ) { card() }
        }
    }
}

@Composable
private fun QuoteTextTab(
    options: QuoteShareOptions,
    quoteText: String,
    onQuoteTextChange: (String) -> Unit,
    onOptionsChange: (QuoteShareOptions) -> Unit,
) {
    ShareCardOptionsLabel(stringResource(R.string.share_card_image_options_quote_text))
    OutlinedTextField(value = quoteText, onValueChange = onQuoteTextChange, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6)
    ShareCardOptionsLabel(stringResource(R.string.share_quote_font))
    ShareCardFontRow(selected = options.font, onSelect = { onOptionsChange(options.copy(font = it)) }) {
        ShareCardOptionChip(
            selected = options.italic,
            label = stringResource(R.string.share_quote_italic),
            onClick = { onOptionsChange(options.copy(italic = !options.italic)) },
        )
    }
    ShareCardOptionsLabel(stringResource(R.string.share_quote_weight))
    ShareCardWeightRow(selected = options.weight, onSelect = { onOptionsChange(options.copy(weight = it)) })
    ShareCardOptionsLabel(stringResource(R.string.share_quote_title_font))
    ShareCardFontRow(selected = options.titleFont, onSelect = { onOptionsChange(options.copy(titleFont = it)) })
    ShareCardSliderRow(
        label = stringResource(R.string.settings_reader_font_size_title),
        value = options.quoteFontScale,
        range = MinQuoteFontScale..MaxQuoteFontScale,
        steps = QuoteFontScaleSteps,
        onChange = { onOptionsChange(options.copy(quoteFontScale = it)) },
    )
    ShareCardOptionsLabel(stringResource(R.string.share_quote_align))
    ShareCardChoiceRow(
        choices = QuoteCardAlign.entries,
        selected = options.align,
        label = { stringResource(it.label) },
        onSelect = { onOptionsChange(options.copy(align = it)) },
    )
}

/** Serif / Sans / Mono, each label set in its own typeface, plus any [extra] chips on the same row. */
@Composable
fun ShareCardFontRow(selected: QuoteCardFont, onSelect: (QuoteCardFont) -> Unit, extra: @Composable () -> Unit = {}) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        QuoteCardFont.entries.forEach { font ->
            FilterChip(
                selected = selected == font,
                onClick = { onSelect(font) },
                label = { Text(stringResource(font.label), fontFamily = font.family) },
            )
        }
        extra()
    }
}

@Composable
private fun QuoteLayoutTab(options: QuoteShareOptions, hasCover: Boolean, onOptionsChange: (QuoteShareOptions) -> Unit) {
    ShareCardShapeRow(selected = options.shape, onSelect = { onOptionsChange(options.copy(shape = it)) })
    ShareCardOptionsLabel(stringResource(R.string.share_card_image_options_layout))
    // Backdrop is nothing without the cover, so it is only offered with one, and picking it turns the cover on.
    val layouts = QuoteShareCardLayout.entries.filter { hasCover || it != QuoteShareCardLayout.BACKDROP }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        layouts.forEach { layout ->
            ShareCardOptionChip(
                selected = options.layout == layout,
                label = stringResource(layout.label),
                onClick = {
                    onOptionsChange(options.copy(layout = layout, showCover = options.showCover || layout == QuoteShareCardLayout.BACKDROP))
                },
            )
        }
    }
    ShareCardSliderRow(
        label = stringResource(R.string.share_quote_gap),
        value = options.detailsGap,
        range = MinDetailsGap..MaxDetailsGap,
        steps = DetailsGapSteps,
        onChange = { onOptionsChange(options.copy(detailsGap = it)) },
    )
    ShareCardOptionChip(
        selected = options.showDetailsDivider,
        label = stringResource(R.string.share_quote_divider),
        onClick = { onOptionsChange(options.copy(showDetailsDivider = !options.showDetailsDivider)) },
    )
    if (options.layout == QuoteShareCardLayout.BACKDROP) {
        ShareCardSliderRow(
            label = stringResource(R.string.share_card_image_options_cover_opacity),
            value = options.backdropOpacity,
            range = MinBackdropOpacity..MaxBackdropOpacity,
            steps = 0,
            onChange = { onOptionsChange(options.copy(backdropOpacity = it)) },
        )
    }
}

/** Light/Dark weight-style rows shared by both share-card editors. */
@Composable
fun ShareCardWeightRow(selected: QuoteCardWeight, onSelect: (QuoteCardWeight) -> Unit) {
    ShareCardChoiceRow(choices = QuoteCardWeight.entries, selected = selected, label = { stringResource(it.label) }, onSelect = onSelect)
}

@Composable
fun ShareCardShapeRow(selected: QuoteCardShape, onSelect: (QuoteCardShape) -> Unit) {
    ShareCardOptionsLabel(stringResource(R.string.share_quote_shape))
    ShareCardChoiceRow(choices = QuoteCardShape.entries, selected = selected, label = { stringResource(it.label) }, onSelect = onSelect)
}

/** The palettes as swatches; the app's own colours add a Light/Dark choice (the fixed palettes are one of each). */
@Composable
fun ShareCardPalettePicker(
    palette: QuoteCardPalette,
    theme: ShareCardTheme,
    onPaletteChange: (QuoteCardPalette) -> Unit,
    onThemeChange: (ShareCardTheme) -> Unit,
) {
    ShareCardOptionsLabel(stringResource(R.string.share_quote_colors))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        QuoteCardPalette.entries.forEach { choice ->
            PaletteSwatch(
                colors = quoteCardColors(choice, theme),
                label = stringResource(choice.label),
                selected = palette == choice,
                onClick = { onPaletteChange(choice) },
            )
        }
    }
    if (palette == QuoteCardPalette.APP) ShareCardThemeRow(selected = theme, onSelect = onThemeChange)
}

/** The seal's chip and size slider (when one is set), then the card to add, crop or change it. */
@Composable
fun ShareCardSealControls(show: Boolean, scale: Float, onShowChange: (Boolean) -> Unit, onScaleChange: (Float) -> Unit) {
    val context = LocalContext.current
    val hasSeal = remember(ShareSeal.version) { ShareSeal.exists(context) }
    if (hasSeal) {
        ShareCardOptionChip(show, stringResource(R.string.share_quote_show_seal)) { onShowChange(!show) }
        if (show) {
            ShareCardSliderRow(
                label = stringResource(R.string.share_quote_seal_size),
                value = scale,
                range = MinSealScale..MaxSealScale,
                // An M3 discrete slider: a tick every 10%, fine enough to match the seal to the wordmark.
                steps = SealScaleSteps,
                onChange = onScaleChange,
            )
        }
    }
    ShareSealCard()
}

@Composable
private fun QuoteIncludeTab(
    options: QuoteShareOptions,
    hasCover: Boolean,
    hasBookTitle: Boolean,
    hasAuthor: Boolean,
    hasSeries: Boolean,
    onOptionsChange: (QuoteShareOptions) -> Unit,
) {
    ShareCardOptionsLabel(stringResource(R.string.share_card_image_options_include))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        // Chips for what this quote lacks, or this layout never draws, are hidden rather than left doing nothing.
        val minimal = options.layout == QuoteShareCardLayout.MINIMAL
        if (hasCover && !minimal) {
            ShareCardOptionChip(options.showCover, stringResource(R.string.share_card_image_options_cover)) {
                onOptionsChange(options.copy(showCover = !options.showCover))
            }
        }
        if (!minimal) {
            ShareCardOptionChip(options.showQuoteMark, stringResource(R.string.share_card_image_options_quote_mark)) {
                onOptionsChange(options.copy(showQuoteMark = !options.showQuoteMark))
            }
        }
        if (hasBookTitle) {
            ShareCardOptionChip(options.showBookTitle, stringResource(R.string.share_card_image_options_book_title)) {
                onOptionsChange(options.copy(showBookTitle = !options.showBookTitle))
            }
        }
        if (hasAuthor) {
            ShareCardOptionChip(options.showAuthor, stringResource(R.string.share_card_image_options_author)) {
                onOptionsChange(options.copy(showAuthor = !options.showAuthor))
            }
        }
        if (hasSeries) {
            ShareCardOptionChip(options.showSeries, stringResource(R.string.share_card_image_options_series)) {
                onOptionsChange(options.copy(showSeries = !options.showSeries))
            }
        }
        ShareCardOptionChip(options.showCaption, stringResource(R.string.share_card_image_options_caption)) {
            onOptionsChange(options.copy(showCaption = !options.showCaption))
        }
        ShareCardOptionChip(options.showTagline, stringResource(R.string.share_card_image_options_tagline)) {
            onOptionsChange(options.copy(showTagline = !options.showTagline))
        }
        if (hasShareProfileName()) {
            ShareCardOptionChip(options.showProfileName, stringResource(R.string.share_profile_name)) {
                onOptionsChange(options.copy(showProfileName = !options.showProfileName))
            }
        }
    }
    Spacer(modifier = Modifier.height(Spacing.sm))
    ShareCardSealControls(
        show = options.showSeal,
        scale = options.sealScale,
        onShowChange = { onOptionsChange(options.copy(showSeal = it)) },
        onScaleChange = { onOptionsChange(options.copy(sealScale = it)) },
    )
}

@Composable
fun ShareCardSliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        ShareCardOptionsLabel(label)
        Text(
            text = stringResource(R.string.share_card_image_options_cover_opacity_value, (value * 100).roundToInt()),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
    Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
}

/** A palette as a round swatch (its background, with the accent as a dot) and its name; a check marks the chosen one. */
@Composable
private fun PaletteSwatch(colors: ShareCardColors, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = colors.background,
            border = androidx.compose.foundation.BorderStroke(
                if (selected) Strokes.emphasis else Strokes.outline,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier
                .size(Sizes.touchTarget)
                .semantics { contentDescription = label },
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(Sizes.iconSmall))
                } else {
                    Box(Modifier.size(Spacing.md).clip(CircleShape).background(colors.accent))
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The reader's seal at its chosen size, in [tint]; nothing when no seal is set. */
@Composable
internal fun ShareCardSeal(tint: Color, scale: Float) {
    ShareSealImage(tint = tint, modifier = Modifier.size(Sizes.iconLarge * scale.coerceIn(MinSealScale, MaxSealScale)))
}

/** A quote/highlight/note as a shareable card in the shape, colours and type [options] ask for. */
@Composable
fun QuoteShareCard(
    text: String,
    author: String?,
    bookTitle: String?,
    series: String?,
    watermark: String,
    footerRight: String,
    modifier: Modifier = Modifier,
    coverPath: String? = null,
    options: QuoteShareOptions = QuoteShareOptions(),
) {
    val colors = quoteCardColors(options.palette, options.theme)
    val layout = options.layout
    val content = QuoteShareContent(
        text = text,
        bookTitle = bookTitle?.takeIf { options.showBookTitle && it.isNotBlank() },
        author = author?.takeIf { options.showAuthor && it.isNotBlank() },
        series = series?.takeIf { options.showSeries && it.isNotBlank() },
        coverPath = coverPath?.takeIf { options.showCover && layout != QuoteShareCardLayout.MINIMAL },
        showQuoteMark = options.showQuoteMark && layout != QuoteShareCardLayout.MINIMAL,
        quoteStyle = { base -> base.forQuote(options) },
        titleStyle = { base -> base.copy(fontFamily = options.titleFont.family) },
        align = options.align,
        detailsGap = options.detailsGap.coerceIn(MinDetailsGap, MaxDetailsGap),
        showDivider = options.showDetailsDivider,
        // A taller card has room for more of a long quote.
        lineScale = 1f / options.shape.ratio,
    )
    Box(
        modifier = modifier
            .width(Sizes.shareCardWidth)
            .aspectRatio(options.shape.ratio)
            .background(colors.background),
    ) {
        if (layout == QuoteShareCardLayout.BACKDROP && content.coverPath != null) {
            ShareCardCover(
                coverPath = content.coverPath,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(options.backdropOpacity.coerceIn(MinBackdropOpacity, MaxBackdropOpacity)),
            )
        }
        Column(
            modifier = Modifier
                .matchParentSize()
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ShareCardWordmark(tint = colors.accent, wordmark = watermark)
                Spacer(modifier = Modifier.weight(1f))
                if (options.showSeal) ShareCardSeal(colors.accent, options.sealScale)
            }
            val bodyModifier = Modifier.weight(1f)
            when (layout) {
                QuoteShareCardLayout.CLASSIC -> ClassicQuoteBody(content, colors, inlineCover = true, bodyModifier)
                QuoteShareCardLayout.BACKDROP -> ClassicQuoteBody(content, colors, inlineCover = false, bodyModifier)
                QuoteShareCardLayout.CENTERED -> CenteredQuoteBody(content, colors, bodyModifier)
                QuoteShareCardLayout.MINIMAL -> MinimalQuoteBody(content, colors, bodyModifier)
            }
            ShareCardFooter(
                left = listOfNotNull(
                    (stringResource(R.string.share_card_highlighted_in, watermark) to colors.accent).takeIf { options.showCaption },
                    (footerRight to colors.mutedText).takeIf { options.showTagline },
                ),
                profileName = shareProfileName(options.showProfileName),
                colors = colors,
            )
        }
    }
}

/** Quote mark and quote over the book: its cover when [inlineCover] and one is shown, else an accent bar. */
@Composable
private fun ClassicQuoteBody(content: QuoteShareContent, colors: ShareCardColors, inlineCover: Boolean, modifier: Modifier) {
    val cover = content.coverPath?.takeIf { inlineCover }
    val centered = content.align == QuoteCardAlign.CENTER
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        if (content.showQuoteMark) {
            Text(text = "“", style = ShareCardTypography.quoteMark, color = colors.accent)
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
        Text(
            text = content.text,
            style = content.quoteStyle(ShareCardTypography.quoteBody),
            // Gives way to the book details below, ending in an ellipsis rather than pushing them off the card.
            modifier = Modifier.weight(1f, fill = false),
            color = colors.primaryText,
            textAlign = content.textAlign(default = TextAlign.Start),
            maxLines = content.lines(if (cover != null) 6 else 8),
            overflow = TextOverflow.Ellipsis,
        )
        if (content.hasAttribution || cover != null) {
            DetailsSeparator(content, colors, base = Spacing.lg, short = centered)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cover != null) {
                    ShareCardCover(
                        coverPath = cover,
                        modifier = Modifier
                            .height(Sizes.shareCardQuoteCoverHeight)
                            .aspectRatio(Sizes.coverAspectRatio)
                            .clip(RoundedCornerShape(Radii.extraSmall)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .width(Sizes.shareCardAccentBarWidth)
                            .height(Sizes.shareCardQuoteMarkHeight)
                            .background(colors.accent),
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.sm))
                QuoteAttribution(content, colors)
            }
        }
    }
}

@Composable
private fun CenteredQuoteBody(content: QuoteShareContent, colors: ShareCardColors, modifier: Modifier) {
    val start = content.align == QuoteCardAlign.START
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (start) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content.coverPath?.let {
            ShareCardCover(
                coverPath = it,
                modifier = Modifier
                    .height(Sizes.shareCardQuoteCoverHeight)
                    .aspectRatio(Sizes.coverAspectRatio)
                    .clip(RoundedCornerShape(Radii.extraSmall)),
            )
            Spacer(modifier = Modifier.height(Spacing.md))
        }
        if (content.showQuoteMark) {
            Text(text = "“", style = ShareCardTypography.quoteMark, color = colors.accent)
        }
        Text(
            text = content.text,
            style = content.quoteStyle(ShareCardTypography.quoteBody),
            // Gives way to the book details below, ending in an ellipsis rather than pushing them off the card.
            modifier = Modifier.weight(1f, fill = false),
            color = colors.primaryText,
            textAlign = content.textAlign(default = TextAlign.Center),
            maxLines = content.lines(if (content.coverPath != null) 5 else 7),
            overflow = TextOverflow.Ellipsis,
        )
        if (content.hasAttribution) {
            DetailsSeparator(content, colors, base = Spacing.md, short = !start)
            QuoteAttribution(content, colors, textAlign = if (start) TextAlign.Start else TextAlign.Center)
        }
    }
}

/** Larger type, and the book folded into one "— title · author" line. */
@Composable
private fun MinimalQuoteBody(content: QuoteShareContent, colors: ShareCardColors, modifier: Modifier) {
    val centered = content.align == QuoteCardAlign.CENTER
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = content.text,
            style = content.quoteStyle(ShareCardTypography.quoteBodyLarge),
            // Gives way to the book details below, ending in an ellipsis rather than pushing them off the card.
            modifier = Modifier.weight(1f, fill = false),
            color = colors.primaryText,
            textAlign = content.textAlign(default = TextAlign.Start),
            maxLines = content.lines(7),
            overflow = TextOverflow.Ellipsis,
        )
        if (content.hasAttribution) {
            DetailsSeparator(content, colors, base = Spacing.md, short = centered)
            Text(
                text = "— " + listOfNotNull(content.bookTitle, content.author, content.series).joinToString(" · "),
                style = content.minimalAttributionStyle(ShareCardTypography.cardSubtitleMono),
                color = colors.accent,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The space between the quote and the book details ([base] × the chosen gap), with the optional divider in the
 * middle of it. A centred layout gets a short centred rule rather than one across the card.
 */
@Composable
private fun DetailsSeparator(content: QuoteShareContent, colors: ShareCardColors, base: Dp, short: Boolean) {
    val gap = base * content.detailsGap
    if (!content.showDivider) {
        Spacer(modifier = Modifier.height(gap))
        return
    }
    Spacer(modifier = Modifier.height(gap / 2))
    HorizontalDivider(
        color = colors.divider,
        modifier = if (short) Modifier.fillMaxWidth(ShortDividerFraction) else Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(gap / 2))
}

@Composable
private fun QuoteAttribution(content: QuoteShareContent, colors: ShareCardColors, textAlign: TextAlign? = null) {
    Column {
        content.bookTitle?.let {
            Text(
                text = it,
                style = content.titleStyle(ShareCardTypography.cardTitleDark),
                color = colors.primaryText,
                textAlign = textAlign,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        content.author?.let {
            Text(
                text = it,
                style = ShareCardTypography.cardSubtitleMono,
                color = colors.mutedText,
                textAlign = textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        content.series?.let {
            Text(
                text = it,
                style = ShareCardTypography.cardSeriesMono,
                color = colors.mutedText,
                textAlign = textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ShareCardCover(coverPath: String, modifier: Modifier) {
    val context = LocalContext.current
    // Decoded at export resolution, not screen size, so the shared image isn't an upscaled thumbnail.
    val request = remember(coverPath) { ImageRequest.Builder(context).data(File(coverPath)).size(CoverDecodePx).build() }
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        colorFilter = rememberCoverColorFilter(),
        filterQuality = FilterQuality.High,
    )
}

/** The card's text with every show-flag already applied, so each layout only decides placement. */
private data class QuoteShareContent(
    val text: String,
    val bookTitle: String?,
    val author: String?,
    val series: String?,
    val coverPath: String?,
    val showQuoteMark: Boolean,
    val quoteStyle: (TextStyle) -> TextStyle,
    val titleStyle: (TextStyle) -> TextStyle,
    val align: QuoteCardAlign,
    val detailsGap: Float,
    val showDivider: Boolean,
    val lineScale: Float,
) {
    val hasAttribution: Boolean get() = bookTitle != null || author != null || series != null

    /** Minimal's one-line "— title · author" keeps its mono look unless another title font is picked. */
    fun minimalAttributionStyle(base: TextStyle): TextStyle {
        val styled = titleStyle(base)
        return if (styled.fontFamily == FontFamilies.Display) base else styled
    }

    fun lines(base: Int): Int = (base * lineScale).roundToInt()

    fun textAlign(default: TextAlign): TextAlign = when (align) {
        QuoteCardAlign.AUTO -> default
        QuoteCardAlign.START -> TextAlign.Start
        QuoteCardAlign.CENTER -> TextAlign.Center
    }
}

private fun TextStyle.forQuote(options: QuoteShareOptions): TextStyle {
    val scale = options.quoteFontScale.coerceIn(MinQuoteFontScale, MaxQuoteFontScale)
    return copy(
        fontFamily = options.font.family,
        fontWeight = options.weight.fontWeight,
        fontStyle = if (options.italic) FontStyle.Italic else FontStyle.Normal,
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
    )
}

/** The app's own colours for [QuoteCardPalette.APP]; otherwise the fixed palette, the same in light or dark mode. */
@Composable
internal fun quoteCardColors(palette: QuoteCardPalette, theme: ShareCardTheme): ShareCardColors {
    val app = rememberShareCardColors(theme)
    return remember(palette, app) {
        when (palette) {
            QuoteCardPalette.APP -> app
            QuoteCardPalette.PAPER -> fixedColors(Palette.ReaderPaperBackground, Palette.ReaderPaperText, Palette.TextMutedLight, Palette.Forest500)
            QuoteCardPalette.SEPIA -> fixedColors(Palette.ReaderSepiaBackground, Palette.ReaderSepiaText, Palette.ShareSepiaAccent, Palette.ShareSepiaAccent)
            QuoteCardPalette.ROSE -> fixedColors(Palette.ReaderRoseBackground, Palette.ReaderRoseText, Palette.TextMutedLight, Palette.ShareRoseAccent)
            QuoteCardPalette.FOREST -> fixedColors(Palette.Forest900, Palette.Cream100, Palette.Forest100, Palette.Forest300)
            QuoteCardPalette.MIDNIGHT -> fixedColors(Palette.Navy800, Palette.ReaderDarkText, Palette.TextMutedDark, Palette.Teal300)
            QuoteCardPalette.INK -> fixedColors(Palette.Navy950, Palette.White, Palette.TextMutedDark, Palette.WarmWhite)
        }
    }
}

private fun fixedColors(background: Color, text: Color, muted: Color, accent: Color) = ShareCardColors(
    background = background,
    primaryText = text,
    mutedText = muted,
    accent = accent,
    divider = muted.copy(alpha = DividerAlpha),
)

internal val QuoteCardFont.label: Int get() = when (this) {
    QuoteCardFont.SERIF -> R.string.share_quote_font_serif
    QuoteCardFont.SANS -> R.string.share_quote_font_sans
    QuoteCardFont.MONO -> R.string.share_quote_font_mono
}

/** Serif is the card's own Playfair Display; Sans and Mono are the app's bundled Inter and JetBrains Mono. */
internal val QuoteCardFont.family: FontFamily get() = when (this) {
    QuoteCardFont.SERIF -> FontFamilies.Display
    QuoteCardFont.SANS -> FontFamilies.Ui
    QuoteCardFont.MONO -> FontFamilies.Mono
}

private val QuoteCardWeight.label: Int get() = when (this) {
    QuoteCardWeight.LIGHT -> R.string.share_quote_weight_light
    QuoteCardWeight.REGULAR -> R.string.share_quote_weight_regular
    QuoteCardWeight.MEDIUM -> R.string.share_quote_weight_medium
    QuoteCardWeight.BOLD -> R.string.share_quote_weight_bold
}

private val QuoteCardAlign.label: Int get() = when (this) {
    QuoteCardAlign.AUTO -> R.string.share_quote_align_auto
    QuoteCardAlign.START -> R.string.share_quote_align_start
    QuoteCardAlign.CENTER -> R.string.share_quote_align_center
}

private val QuoteCardShape.label: Int get() = when (this) {
    QuoteCardShape.SQUARE -> R.string.share_quote_shape_square
    QuoteCardShape.PORTRAIT -> R.string.share_quote_shape_portrait
    QuoteCardShape.STORY -> R.string.share_quote_shape_story
}

private val QuoteShareCardLayout.label: Int get() = when (this) {
    QuoteShareCardLayout.CLASSIC -> R.string.share_card_image_options_layout_classic
    QuoteShareCardLayout.CENTERED -> R.string.share_card_image_options_layout_centered
    QuoteShareCardLayout.MINIMAL -> R.string.share_card_image_options_layout_minimal
    QuoteShareCardLayout.BACKDROP -> R.string.share_card_image_options_layout_backdrop
}

private val QuoteCardPalette.label: Int get() = when (this) {
    QuoteCardPalette.APP -> R.string.share_quote_palette_app
    QuoteCardPalette.PAPER -> R.string.share_quote_palette_paper
    QuoteCardPalette.SEPIA -> R.string.share_quote_palette_sepia
    QuoteCardPalette.ROSE -> R.string.share_quote_palette_rose
    QuoteCardPalette.FOREST -> R.string.share_quote_palette_forest
    QuoteCardPalette.MIDNIGHT -> R.string.share_quote_palette_midnight
    QuoteCardPalette.INK -> R.string.share_quote_palette_ink
}

private val EditorTabs = listOf(
    R.string.share_quote_tab_text,
    R.string.share_quote_tab_layout,
    R.string.share_quote_tab_colors,
    R.string.share_quote_tab_show,
)

private const val MinQuoteFontScale = 0.7f
private const val MaxQuoteFontScale = 1.4f
private const val QuoteFontScaleSteps = 6
private const val MaxShareQuoteCharacters = 2_000
internal const val MinBackdropOpacity = 0.1f
internal const val MaxBackdropOpacity = 0.6f
private const val DividerAlpha = 0.4f
private const val MinDetailsGap = 0f
private const val MaxDetailsGap = 3f
private const val DetailsGapSteps = 11
private const val ShortDividerFraction = 0.3f
private const val CoverDecodePx = 1600
private const val MinSealScale = 0.3f
private const val MaxSealScale = 2.5f
/** Ticks between the ends: (2.5 - 0.3) / 0.1 = 22 intervals. */
private const val SealScaleSteps = 21

/** Share of the editor's height the preview gets; the tabs and their controls take the rest. */
private const val PreviewHeightFraction = 0.45f

/** "Series · Book 3", or whichever half the book has; null outside a series. */
@Composable
fun shareSeriesLabel(series: String?, seriesNumber: String?): String? = listOfNotNull(
    series?.takeIf { it.isNotBlank() },
    seriesNumber?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.library_series_number_value, it) },
).joinToString(" · ").ifBlank { null }
