package com.vayana.core.designsystem.sharecard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.vayana.core.common.QuoteCitation
import com.vayana.core.common.shareText
import com.vayana.core.designsystem.theme.rememberCoverColorFilter
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.ShareCardTypography
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.io.File

/** Arrangement of the quote card's body; the wordmark and footer are shared by all of them. */
enum class QuoteShareCardLayout {
    /** Quote mark, quote, and the book beside an accent bar (or its cover). */
    CLASSIC,

    /** Everything centred, the cover above the quote. */
    CENTERED,

    /** Larger type and a one-line attribution; no quote mark, no cover. */
    MINIMAL,

    /** [CLASSIC] over the cover stretched across the whole card, faded to [BackdropCoverAlpha]. */
    BACKDROP,
}

/** What the viewer picked for the quote card; the dialog keeps it while it is open. */
data class QuoteShareOptions(
    val layout: QuoteShareCardLayout = QuoteShareCardLayout.CLASSIC,
    val theme: ShareCardTheme = ShareCardTheme.DARK,
    val showCover: Boolean = false,
    val showQuoteMark: Boolean = true,
    val showBookTitle: Boolean = true,
    val showAuthor: Boolean = true,
    val showSeries: Boolean = true,
    val showCaption: Boolean = true,
    val showTagline: Boolean = true,
)

/** A quote in the [QuoteShareCard] preview, customisable, shared as an image or as a cited text quote. */
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
    var options by remember { mutableStateOf(QuoteShareOptions()) }
    val availableCover = remember(coverPath) { coverPath?.takeIf { File(it).exists() } }
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
        options = {
            QuoteShareOptionsPanel(
                options = options,
                hasCover = availableCover != null,
                hasBookTitle = !bookTitle.isNullOrBlank(),
                hasAuthor = !author.isNullOrBlank(),
                hasSeries = seriesLabel != null,
                onOptionsChange = { options = it },
            )
        },
    ) {
        QuoteShareCard(
            text = text,
            author = author,
            bookTitle = bookTitle,
            series = seriesLabel,
            coverPath = availableCover,
            watermark = stringResource(R.string.share_card_watermark),
            footerRight = stringResource(R.string.share_card_tagline),
            options = options,
        )
    }
}

/** A quote/highlight/note as a shareable square card in the app's Material colours, laid out per [options]. */
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
    val colors = rememberShareCardColors(options.theme)
    val layout = options.layout
    val content = QuoteShareContent(
        text = text,
        bookTitle = bookTitle?.takeIf { options.showBookTitle && it.isNotBlank() },
        author = author?.takeIf { options.showAuthor && it.isNotBlank() },
        series = series?.takeIf { options.showSeries && it.isNotBlank() },
        coverPath = coverPath?.takeIf { options.showCover && layout != QuoteShareCardLayout.MINIMAL },
        showQuoteMark = options.showQuoteMark && layout != QuoteShareCardLayout.MINIMAL,
    )
    Box(
        modifier = modifier
            .width(Sizes.shareCardWidth)
            .aspectRatio(1f)
            .background(colors.background),
    ) {
        if (layout == QuoteShareCardLayout.BACKDROP && content.coverPath != null) {
            ShareCardCover(
                coverPath = content.coverPath,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(DefaultBackdropCoverAlpha),
            )
        }
        Column(
            modifier = Modifier
                .matchParentSize()
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            ShareCardWordmark(tint = colors.accent, wordmark = watermark)
            val bodyModifier = Modifier.weight(1f)
            when (layout) {
                QuoteShareCardLayout.CLASSIC -> ClassicQuoteBody(content, colors, inlineCover = true, bodyModifier)
                QuoteShareCardLayout.BACKDROP -> ClassicQuoteBody(content, colors, inlineCover = false, bodyModifier)
                QuoteShareCardLayout.CENTERED -> CenteredQuoteBody(content, colors, bodyModifier)
                QuoteShareCardLayout.MINIMAL -> MinimalQuoteBody(content, colors, bodyModifier)
            }
            if (options.showCaption || options.showTagline) {
                Column {
                    HorizontalDivider(color = colors.divider)
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (options.showCaption) {
                            ShareCardCaption(text = stringResource(R.string.share_card_highlighted_in, watermark), color = colors.accent)
                        } else {
                            Spacer(modifier = Modifier)
                        }
                        if (options.showTagline) {
                            ShareCardCaption(text = footerRight, color = colors.mutedText)
                        }
                    }
                }
            }
        }
    }
}

/** Quote mark and quote over the book: its cover when [inlineCover] and one is shown, else an accent bar. */
@Composable
private fun ClassicQuoteBody(content: QuoteShareContent, colors: ShareCardColors, inlineCover: Boolean, modifier: Modifier) {
    val cover = content.coverPath?.takeIf { inlineCover }
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        if (content.showQuoteMark) {
            Text(text = "“", style = ShareCardTypography.quoteMark, color = colors.accent)
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
        Text(
            text = content.text,
            style = ShareCardTypography.quoteBody,
            color = colors.primaryText,
            maxLines = if (cover != null) 6 else 8,
            overflow = TextOverflow.Ellipsis,
        )
        if (content.hasAttribution || cover != null) {
            Spacer(modifier = Modifier.height(Spacing.lg))
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
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
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
            style = ShareCardTypography.quoteBody,
            color = colors.primaryText,
            textAlign = TextAlign.Center,
            maxLines = if (content.coverPath != null) 5 else 7,
            overflow = TextOverflow.Ellipsis,
        )
        if (content.hasAttribution) {
            Spacer(modifier = Modifier.height(Spacing.md))
            QuoteAttribution(content, colors, textAlign = TextAlign.Center)
        }
    }
}

/** Larger type, and the book folded into one "— title · author" line. */
@Composable
private fun MinimalQuoteBody(content: QuoteShareContent, colors: ShareCardColors, modifier: Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        Text(
            text = content.text,
            style = ShareCardTypography.quoteBodyLarge,
            color = colors.primaryText,
            maxLines = 7,
            overflow = TextOverflow.Ellipsis,
        )
        if (content.hasAttribution) {
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = "— " + listOfNotNull(content.bookTitle, content.author, content.series).joinToString(" · "),
                style = ShareCardTypography.cardSubtitleMono,
                color = colors.accent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QuoteAttribution(content: QuoteShareContent, colors: ShareCardColors, textAlign: TextAlign? = null) {
    Column {
        content.bookTitle?.let {
            Text(
                text = it,
                style = ShareCardTypography.cardTitleDark,
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
    AsyncImage(
        model = File(coverPath),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        colorFilter = rememberCoverColorFilter(),
    )
}

/** Inline controls under the quote-card preview; each change redraws the card above immediately. */
@Composable
private fun QuoteShareOptionsPanel(
    options: QuoteShareOptions,
    hasCover: Boolean,
    hasBookTitle: Boolean,
    hasAuthor: Boolean,
    hasSeries: Boolean,
    onOptionsChange: (QuoteShareOptions) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ShareCardOptionsLabel(stringResource(R.string.share_card_image_options_layout))
        ShareCardChoiceRow(
            choices = QuoteShareCardLayout.entries,
            selected = options.layout,
            label = { layout ->
                stringResource(
                    when (layout) {
                        QuoteShareCardLayout.CLASSIC -> R.string.share_card_image_options_layout_classic
                        QuoteShareCardLayout.CENTERED -> R.string.share_card_image_options_layout_centered
                        QuoteShareCardLayout.MINIMAL -> R.string.share_card_image_options_layout_minimal
                        QuoteShareCardLayout.BACKDROP -> R.string.share_card_image_options_layout_backdrop
                    },
                )
            },
            // Backdrop is nothing without the cover, so picking it turns the cover on.
            onSelect = { onOptionsChange(options.copy(layout = it, showCover = options.showCover || it == QuoteShareCardLayout.BACKDROP)) },
        )
        ShareCardThemeRow(selected = options.theme, onSelect = { onOptionsChange(options.copy(theme = it)) })
        ShareCardOptionsLabel(stringResource(R.string.share_card_image_options_include))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            // Chips for what this quote lacks, or this layout never draws, are hidden rather than left doing nothing.
            val minimal = options.layout == QuoteShareCardLayout.MINIMAL
            if (hasCover && !minimal) {
                ShareCardOptionChip(
                    selected = options.showCover,
                    label = stringResource(R.string.share_card_image_options_cover),
                    onClick = { onOptionsChange(options.copy(showCover = !options.showCover)) },
                )
            }
            if (!minimal) {
                ShareCardOptionChip(
                    selected = options.showQuoteMark,
                    label = stringResource(R.string.share_card_image_options_quote_mark),
                    onClick = { onOptionsChange(options.copy(showQuoteMark = !options.showQuoteMark)) },
                )
            }
            if (hasBookTitle) {
                ShareCardOptionChip(
                    selected = options.showBookTitle,
                    label = stringResource(R.string.share_card_image_options_book_title),
                    onClick = { onOptionsChange(options.copy(showBookTitle = !options.showBookTitle)) },
                )
            }
            if (hasAuthor) {
                ShareCardOptionChip(
                    selected = options.showAuthor,
                    label = stringResource(R.string.share_card_image_options_author),
                    onClick = { onOptionsChange(options.copy(showAuthor = !options.showAuthor)) },
                )
            }
            if (hasSeries) {
                ShareCardOptionChip(
                    selected = options.showSeries,
                    label = stringResource(R.string.share_card_image_options_series),
                    onClick = { onOptionsChange(options.copy(showSeries = !options.showSeries)) },
                )
            }
            ShareCardOptionChip(
                selected = options.showCaption,
                label = stringResource(R.string.share_card_image_options_caption),
                onClick = { onOptionsChange(options.copy(showCaption = !options.showCaption)) },
            )
            ShareCardOptionChip(
                selected = options.showTagline,
                label = stringResource(R.string.share_card_image_options_tagline),
                onClick = { onOptionsChange(options.copy(showTagline = !options.showTagline)) },
            )
        }
    }
}

/** The card's text with every show-flag already applied, so each layout only decides placement. */
private data class QuoteShareContent(
    val text: String,
    val bookTitle: String?,
    val author: String?,
    val series: String?,
    val coverPath: String?,
    val showQuoteMark: Boolean,
) {
    val hasAttribution: Boolean get() = bookTitle != null || author != null || series != null
}

/** "Series · Book 3", or whichever half the book has; null outside a series. */
@Composable
fun shareSeriesLabel(series: String?, seriesNumber: String?): String? = listOfNotNull(
    series?.takeIf { it.isNotBlank() },
    seriesNumber?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.library_series_number_value, it) },
).joinToString(" · ").ifBlank { null }
