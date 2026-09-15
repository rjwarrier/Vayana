package com.vayana.core.designsystem.sharecard

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import com.vayana.core.common.ShareImageFormat
import androidx.compose.ui.res.stringResource
import com.vayana.core.resources.R
import com.vayana.core.common.shareText
import com.vayana.core.common.QuoteCitation
import com.vayana.core.common.shareBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntSize
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
import com.vayana.core.designsystem.tokens.Palette
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.ShareCardTypography
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import kotlinx.coroutines.launch

/**
 * Renders [card] into a preview dialog with "share as text" and "share as image" actions.
 * [card] should size itself to [Sizes.shareCardWidth] and stay opaque and unclipped so the capture looks
 * right: only the preview is rounded, so the exported image is a full-bleed square with no transparent
 * corners (which most share targets flatten to black).
 *
 * [options], when given, sits between the preview and the actions and scrolls on its own, so the preview
 * stays in view and redraws live as the viewer changes what the card shows.
 */
@Composable
fun ShareCardDialog(
    onDismiss: () -> Unit,
    onShareText: () -> Unit,
    chooserTitle: String,
    shareTextLabel: String,
    shareImageLabel: String,
    modifier: Modifier = Modifier,
    shareImageFileName: String? = null,
    title: String? = null,
    options: (@Composable () -> Unit)? = null,
    card: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLayer = rememberGraphicsLayer()
    var isCapturing by remember { mutableStateOf(false) }

    fun shareImage() {
        isCapturing = true
        scope.launch {
            try {
                val captured = exportLayer.toImageBitmap().asAndroidBitmap()
                // Hardware bitmaps can't be read back for encoding; take a plain copy first.
                val bitmap = if (captured.config == Bitmap.Config.HARDWARE) {
                    captured.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    captured
                }
                context.shareBitmap(bitmap, chooserTitle, shareImageFileName, ShareImageFormat.JPEG)
                onDismiss()
            } finally {
                isCapturing = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(Radii.extraLargeIncreased),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.lg)
                    .wrapContentWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.md),
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radii.large))
                        .drawWithContent {
                            // Recorded straight at the export size instead of captured from the screen and
                            // upscaled, so text and shapes stay sharp in the saved image.
                            val exportSize = Sizes.shareCardExportPx
                            val exportScale = exportSize / size.width
                            exportLayer.record(size = IntSize(exportSize, exportSize)) {
                                scale(exportScale, pivot = Offset.Zero) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                            drawContent()
                        },
                ) {
                    card()
                }
                if (options != null) {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        options()
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    OutlinedButton(
                        onClick = onShareText,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ShortText,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = shareTextLabel)
                    }
                    Button(
                        onClick = ::shareImage,
                        enabled = !isCapturing,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isCapturing) {
                            VayanaCircularProgressIndicator(
                                modifier = Modifier.height(Sizes.iconSmall),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                            Text(text = shareImageLabel)
                        }
                    }
                }
            }
        }
    }
}

/** A quote in the [QuoteShareCard] preview, shared as an image or as a cited text quote. */
@Composable
fun QuoteShareDialog(
    text: String,
    author: String?,
    bookTitle: String?,
    chapterTitle: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val textChooserTitle = stringResource(R.string.share_card_share_text)
    ShareCardDialog(
        onDismiss = onDismiss,
        onShareText = {
            context.shareText(
                QuoteCitation.format(text = text, author = author, bookTitle = bookTitle, chapterTitle = chapterTitle),
                textChooserTitle,
            )
            onDismiss()
        },
        chooserTitle = stringResource(R.string.share_card_image_chooser_title),
        shareTextLabel = textChooserTitle,
        shareImageLabel = stringResource(R.string.share_card_share_image),
    ) {
        QuoteShareCard(
            text = text,
            author = author,
            bookTitle = bookTitle,
            pageLabel = chapterTitle,
            watermark = stringResource(R.string.share_card_watermark),
            footerRight = stringResource(R.string.share_card_tagline),
        )
    }
}

/** Small brand lockup ("vayana", optionally led by a book glyph) printed on every share card, fixed brand colors. */
@Composable
private fun ShareCardWordmark(tint: Color, wordmark: String, showGlyph: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showGlyph) {
            Icon(
                imageVector = Icons.Outlined.AutoStories,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(Sizes.icon),
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
        }
        Text(text = wordmark, style = ShareCardTypography.wordmark, color = tint)
    }
}

/** Small letter-spaced caps label, matching the share-card handoff's eyebrow style. */
@Composable
private fun ShareCardCaption(text: String, color: Color) {
    Text(text = text, style = ShareCardTypography.caption, color = color)
}

/** A quote/highlight/note rendered as a shareable dark image card (handoff "Quote — dark"). */
@Composable
fun QuoteShareCard(
    text: String,
    author: String?,
    bookTitle: String?,
    pageLabel: String?,
    watermark: String,
    footerRight: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(Sizes.shareCardWidth)
            .aspectRatio(1f)
            .background(Palette.Navy900)
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        ShareCardWordmark(tint = Palette.Teal500, wordmark = watermark)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "“", style = ShareCardTypography.quoteMark, color = Palette.Teal500)
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = text,
                style = ShareCardTypography.quoteBody,
                color = Palette.Cream50,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
            if (!bookTitle.isNullOrBlank() || !author.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Spacing.lg))
                Row {
                    Box(
                        modifier = Modifier
                            .width(Sizes.shareCardAccentBarWidth)
                            .height(Sizes.shareCardQuoteMarkHeight)
                            .background(Palette.Teal500),
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Column {
                        bookTitle?.takeIf { it.isNotBlank() }?.let {
                            Text(text = it, style = ShareCardTypography.cardTitleDark, color = Palette.Cream100)
                        }
                        val subtitle = listOfNotNull(author?.takeIf { it.isNotBlank() }, pageLabel?.takeIf { it.isNotBlank() })
                            .joinToString(" · ")
                        if (subtitle.isNotBlank()) {
                            Text(text = subtitle, style = ShareCardTypography.cardSubtitleMono, color = Palette.TextMutedDark)
                        }
                    }
                }
            }
        }
        Column {
            HorizontalDivider(color = Palette.Navy600)
            Spacer(modifier = Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ShareCardCaption(text = "Highlighted in $watermark", color = Palette.Teal300)
                ShareCardCaption(text = footerRight, color = Palette.TextMutedDark)
            }
        }
    }
}

/** A finished/in-progress book rendered as a shareable light image card (handoff "Book — light"). */
@Composable
fun BookShareCard(
    title: String,
    author: String?,
    statusLabel: String,
    stat1Value: String,
    stat1Label: String,
    stat2Value: String,
    stat2Label: String,
    ratingValue: String?,
    ratingLabel: String,
    tags: List<String>,
    footerLeft: String,
    footerRight: String,
    watermark: String,
    modifier: Modifier = Modifier,
    theme: ShareCardTheme = ShareCardTheme.LIGHT,
    showCover: Boolean = true,
    showAuthor: Boolean = true,
    showStatus: Boolean = true,
    showProgress: Boolean = true,
    showReadTime: Boolean = true,
    showRating: Boolean = true,
    showTags: Boolean = true,
    showImportedDate: Boolean = true,
    showTagline: Boolean = true,
    layout: BookShareCardLayout = BookShareCardLayout.CLASSIC,
    /** Reading progress 0..1, drawn as a bar by [BookShareCardLayout.MINIMAL]. */
    progressFraction: Float? = null,
    cover: @Composable () -> Unit,
) {
    val colors = theme.bookColors()
    val content = BookShareContent(
        title = title,
        author = author?.takeIf { showAuthor && it.isNotBlank() },
        status = statusLabel.takeIf { showStatus },
        tags = if (showTags) {
            tags.map { it.sanitizedShareTag() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .take(MaxBookShareTags)
        } else {
            emptyList()
        },
        stats = buildList {
            if (showProgress) add(BookShareStat(stat1Value, stat1Label))
            if (showReadTime) add(BookShareStat(stat2Value, stat2Label))
            if (showRating && !ratingValue.isNullOrBlank()) add(BookShareStat(ratingValue, ratingLabel))
        },
        progressFraction = progressFraction?.takeIf { showProgress }?.coerceIn(0f, 1f),
    )
    Box(
        modifier = modifier
            .width(Sizes.shareCardWidth)
            .aspectRatio(1f)
            .background(colors.background),
    ) {
        if (layout == BookShareCardLayout.BACKDROP && showCover) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(BackdropCoverAlpha),
            ) {
                cover()
            }
        }
        Column(
            modifier = Modifier
                .matchParentSize()
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            ShareCardWordmark(tint = colors.accent, wordmark = watermark, showGlyph = false)
            val bodyModifier = Modifier.weight(1f)
            when (layout) {
                BookShareCardLayout.CLASSIC -> ClassicBookShareBody(content, colors, showCover, cover, bodyModifier)
                BookShareCardLayout.SPOTLIGHT -> SpotlightBookShareBody(content, colors, showCover, cover, bodyModifier)
                BookShareCardLayout.MINIMAL,
                BookShareCardLayout.BACKDROP,
                -> MinimalBookShareBody(content, colors, bodyModifier)
            }
            if (showImportedDate || showTagline) {
                Column {
                    HorizontalDivider(color = colors.divider)
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (showImportedDate) {
                            ShareCardCaption(text = footerLeft, color = colors.mutedText)
                        } else {
                            Spacer(modifier = Modifier)
                        }
                        if (showTagline) {
                            ShareCardCaption(text = footerRight, color = colors.mutedText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareStat(value: String, label: String, colors: BookShareCardColors, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = ShareCardTypography.statValue,
            color = colors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = ShareCardTypography.caption,
            color = colors.mutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Cover left with details beside it, stats in a full-width row underneath - beside the cover they were
 * squeezed into a third of the card, truncating their labels and leaving the card half empty.
 */
@Composable
private fun ClassicBookShareBody(
    content: BookShareContent,
    colors: BookShareCardColors,
    showCover: Boolean,
    cover: @Composable () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showCover) {
                Box(
                    modifier = Modifier
                        .width(Sizes.shareCardCoverWidth)
                        .height(Sizes.shareCardCoverWidth / Sizes.coverAspectRatio)
                        .clip(RoundedCornerShape(Radii.small)),
                ) {
                    cover()
                }
                Spacer(modifier = Modifier.width(Spacing.lg))
            }
            Column(modifier = Modifier.weight(1f)) {
                content.status?.let {
                    ShareCardCaption(text = it, color = colors.accent)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                }
                Text(
                    text = content.title,
                    style = ShareCardTypography.bookTitle,
                    color = colors.primaryText,
                    maxLines = if (showCover) 3 else 4,
                    overflow = TextOverflow.Ellipsis,
                )
                content.author?.let {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(text = it, style = ShareCardTypography.bookAuthor, color = colors.mutedText)
                }
                if (content.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    ShareTagsText(tags = content.tags, colors = colors, maxLines = 2)
                }
            }
        }
        if (content.stats.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.lg))
            ShareStatsRow(stats = content.stats, colors = colors, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Small centred cover over a centred title, with stats and tags each folded into one line. */
@Composable
private fun SpotlightBookShareBody(
    content: BookShareContent,
    colors: BookShareCardColors,
    showCover: Boolean,
    cover: @Composable () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showCover) {
            Box(
                modifier = Modifier
                    .width(Sizes.shareCardSpotlightCoverWidth)
                    .aspectRatio(Sizes.coverAspectRatio)
                    .clip(RoundedCornerShape(Radii.small)),
            ) {
                cover()
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
        } else {
            // The status caption only has room once the cover is out of the way.
            content.status?.let {
                ShareCardCaption(text = it, color = colors.accent)
                Spacer(modifier = Modifier.height(Spacing.xs))
            }
        }
        Text(
            text = content.title,
            style = ShareCardTypography.bookTitle,
            color = colors.primaryText,
            textAlign = TextAlign.Center,
            maxLines = if (showCover) 2 else 4,
            overflow = TextOverflow.Ellipsis,
        )
        content.author?.let {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = it,
                style = ShareCardTypography.bookAuthor,
                color = colors.mutedText,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (content.stats.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = content.stats.joinToString("  ·  ") { it.value },
                style = ShareCardTypography.cardSubtitleMono,
                color = colors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (content.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = content.tags.joinToString(", "),
                style = ShareCardTypography.cardSubtitleMono,
                color = colors.mutedText,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Type-only: a large title and a progress bar, no cover. */
@Composable
private fun MinimalBookShareBody(
    content: BookShareContent,
    colors: BookShareCardColors,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        content.status?.let {
            ShareCardCaption(text = it, color = colors.accent)
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
        Text(
            text = content.title,
            style = ShareCardTypography.bookTitleLarge,
            color = colors.primaryText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        content.author?.let {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = it,
                style = ShareCardTypography.bookAuthor,
                color = colors.mutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        content.progressFraction?.let { fraction ->
            Spacer(modifier = Modifier.height(Spacing.lg))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Sizes.shareCardProgressBarHeight)
                    .clip(CircleShape)
                    .background(colors.divider),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(colors.accent),
                )
            }
        }
        if (content.stats.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.md))
            ShareStatsRow(stats = content.stats, colors = colors, modifier = Modifier.fillMaxWidth())
        }
        if (content.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            ShareTagsText(tags = content.tags, colors = colors, maxLines = 1)
        }
    }
}

@Composable
private fun ShareStatsRow(stats: List<BookShareStat>, colors: BookShareCardColors, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        stats.forEach { stat ->
            ShareStat(value = stat.value, label = stat.label, colors = colors, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ShareTagsText(tags: List<String>, colors: BookShareCardColors, maxLines: Int) {
    Text(
        text = tags.joinToString(", "),
        style = ShareCardTypography.cardSubtitleMono,
        color = colors.accent,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Arrangement of the book share card's body; the wordmark and footer are shared by all of them. */
enum class BookShareCardLayout {
    CLASSIC,
    SPOTLIGHT,
    MINIMAL,

    /** [MINIMAL]'s type over the cover stretched across the whole card, faded to [BackdropCoverAlpha]. */
    BACKDROP,
}

/** Keeps a full-bleed cover quiet enough for the card's text to stay readable on top of it. */
private const val BackdropCoverAlpha = 0.3f

private data class BookShareStat(val value: String, val label: String)

/** The card's text with every show-flag already applied, so each layout only decides placement. */
private data class BookShareContent(
    val title: String,
    val author: String?,
    val status: String?,
    val tags: List<String>,
    val stats: List<BookShareStat>,
    val progressFraction: Float?,
)

enum class ShareCardTheme {
    LIGHT,
    DARK,
}

private data class BookShareCardColors(
    val background: Color,
    val primaryText: Color,
    val mutedText: Color,
    val accent: Color,
    val divider: Color,
)

private fun ShareCardTheme.bookColors(): BookShareCardColors = when (this) {
    ShareCardTheme.LIGHT -> BookShareCardColors(
        background = Palette.Cream100,
        primaryText = Palette.FgPrimaryLight,
        mutedText = Palette.TextMutedLight,
        accent = Palette.Forest700,
        divider = Palette.Cream300,
    )
    ShareCardTheme.DARK -> BookShareCardColors(
        background = Palette.Navy900,
        primaryText = Palette.Cream50,
        mutedText = Palette.TextMutedDark,
        accent = Palette.Teal300,
        divider = Palette.Navy600,
    )
}

private fun String.sanitizedShareTag(): String =
    map { if (Character.isISOControl(it)) ' ' else it }
        .joinToString("")
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(MaxBookShareTagChars)
        .trim()

private const val MaxBookShareTags = 3
private const val MaxBookShareTagChars = 24
