package com.vayana.core.designsystem.sharecard

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import com.vayana.core.designsystem.theme.ColorSchemes
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.LocalDarkVariant
import com.vayana.core.designsystem.theme.LocalDisplayProfile
import com.vayana.core.designsystem.theme.LocalDynamicColor
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
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import com.vayana.core.common.shareBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Brand lockup on every share card: the launcher icon, as it looks on the home screen, beside the "vayana" wordmark. */
@Composable
internal fun ShareCardWordmark(tint: Color, wordmark: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(Sizes.icon)
                .clip(RoundedCornerShape(Radii.extraSmall))
                .background(Palette.LauncherCream),
        ) {
            // The mark is drawn on the adaptive icon's 108dp canvas, of which a launcher shows the middle two
            // thirds; scale it up by the same amount so it fills the tile the way it fills the app icon.
            Image(
                painter = painterResource(R.drawable.vayana_app_mark),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .scale(AdaptiveIconVisibleScale),
            )
        }
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(text = wordmark, style = ShareCardTypography.wordmark, color = tint)
    }
}

/** Small letter-spaced caps label, matching the share-card handoff's eyebrow style. */
@Composable
internal fun ShareCardCaption(text: String, color: Color) {
    Text(text = text, style = ShareCardTypography.caption, color = color)
}

/** A finished/in-progress book rendered as a shareable light image card (handoff "Book — light"). */
@Composable
fun BookShareCard(
    title: String,
    author: String?,
    series: String?,
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
    showSeries: Boolean = true,
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
    yearlyGoalReadCount: Int? = null,
    yearlyGoalTarget: Int? = null,
    cover: @Composable () -> Unit,
) {
    val colors = rememberShareCardColors(theme)
    val content = BookShareContent(
        title = title,
        author = author?.takeIf { showAuthor && it.isNotBlank() },
        series = series?.takeIf { showSeries && it.isNotBlank() },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShareCardWordmark(tint = colors.accent, wordmark = watermark)
                if (yearlyGoalReadCount != null && yearlyGoalTarget != null && yearlyGoalTarget > 0) {
                    YearlyGoalBadge(yearlyGoalReadCount, yearlyGoalTarget, colors)
                }
            }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun YearlyGoalBadge(readCount: Int, target: Int, colors: ShareCardColors) {
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { (readCount.toFloat() / target).coerceIn(0f, 1f) },
            modifier = Modifier.matchParentSize(),
            color = colors.accent,
            trackColor = colors.divider,
        )
        Text(
            text = "$readCount/$target",
            style = ShareCardTypography.cardSubtitleMono.copy(fontSize = 9.sp),
            color = colors.primaryText,
            maxLines = 1,
        )
    }
}

@Composable
private fun ShareStat(value: String, label: String, colors: ShareCardColors, modifier: Modifier = Modifier) {
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
    colors: ShareCardColors,
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
                content.series?.let {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    ShareSeriesText(series = it, colors = colors)
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
    colors: ShareCardColors,
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
        content.series?.let {
            Spacer(modifier = Modifier.height(Spacing.xs))
            ShareSeriesText(series = it, colors = colors, textAlign = TextAlign.Center)
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
    colors: ShareCardColors,
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
        content.series?.let {
            Spacer(modifier = Modifier.height(Spacing.xs))
            ShareSeriesText(series = it, colors = colors)
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
private fun ShareStatsRow(stats: List<BookShareStat>, colors: ShareCardColors, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        stats.forEach { stat ->
            ShareStat(value = stat.value, label = stat.label, colors = colors, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ShareSeriesText(series: String, colors: ShareCardColors, textAlign: TextAlign? = null) {
    Text(
        text = series,
        style = ShareCardTypography.cardSubtitleMono,
        color = colors.mutedText,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ShareTagsText(tags: List<String>, colors: ShareCardColors, maxLines: Int) {
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
internal const val BackdropCoverAlpha = 0.3f

private data class BookShareStat(val value: String, val label: String)

/** The card's text with every show-flag already applied, so each layout only decides placement. */
private data class BookShareContent(
    val title: String,
    val author: String?,
    val series: String?,
    val status: String?,
    val tags: List<String>,
    val stats: List<BookShareStat>,
    val progressFraction: Float?,
)

enum class ShareCardTheme {
    LIGHT,
    DARK,
}

internal data class ShareCardColors(
    val background: Color,
    val primaryText: Color,
    val mutedText: Color,
    val accent: Color,
    val divider: Color,
)

/**
 * The card in the app's own Material colours - wallpaper colours when those are on - in the light or dark
 * version [theme] asks for, whichever the app itself is showing. E-Ink's monochrome scheme has no dark
 * version and would wash out a shared image, so that profile falls back to the standard schemes.
 */
@Composable
internal fun rememberShareCardColors(theme: ShareCardTheme): ShareCardColors {
    val context = LocalContext.current
    val dynamicColor = LocalDynamicColor.current
    val darkVariant = LocalDarkVariant.current
    val profile = LocalDisplayProfile.current.takeIf { it != DisplayProfile.E_INK } ?: DisplayProfile.STANDARD
    return remember(theme, dynamicColor, darkVariant, profile) {
        val isDark = theme == ShareCardTheme.DARK
        val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ColorSchemes.dynamic(context, isDark, darkVariant)
        } else {
            ColorSchemes.forProfile(profile, isDark, darkVariant)
        }
        ShareCardColors(
            background = scheme.surfaceContainer,
            primaryText = scheme.onSurface,
            mutedText = scheme.onSurfaceVariant,
            accent = scheme.primary,
            divider = scheme.outlineVariant,
        )
    }
}

private fun String.sanitizedShareTag(): String =
    map { if (Character.isISOControl(it)) ' ' else it }
        .joinToString("")
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(MaxBookShareTagChars)
        .trim()

/** How much of the adaptive icon's canvas a launcher crops away: it shows the middle 72 of 108dp. */
private const val AdaptiveIconVisibleScale = 108f / 72f

private const val MaxBookShareTags = 3
private const val MaxBookShareTagChars = 24
