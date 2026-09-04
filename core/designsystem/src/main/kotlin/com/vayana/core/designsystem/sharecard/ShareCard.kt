package com.vayana.core.designsystem.sharecard

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import com.vayana.core.common.shareBitmap
import com.vayana.core.designsystem.tokens.Palette
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.ShareCardTypography
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import kotlinx.coroutines.launch

/**
 * Renders [card] into a preview dialog with "share as text" and "share as image" actions.
 * [card] should size itself to [Sizes.shareCardWidth] and stay opaque so the capture looks right.
 */
@Composable
fun ShareCardDialog(
    onDismiss: () -> Unit,
    onShareText: () -> Unit,
    chooserTitle: String,
    shareTextLabel: String,
    shareImageLabel: String,
    modifier: Modifier = Modifier,
    card: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    var isCapturing by remember { mutableStateOf(false) }

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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radii.large))
                        .drawWithContent {
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(graphicsLayer)
                        },
                ) {
                    card()
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
                        Text(text = shareTextLabel)
                    }
                    Button(
                        onClick = {
                            isCapturing = true
                            scope.launch {
                                val captured = graphicsLayer.toImageBitmap().asAndroidBitmap()
                                val exportSize = Sizes.shareCardExportPx
                                val squared = Bitmap.createScaledBitmap(captured, exportSize, exportSize, true)
                                context.shareBitmap(squared, chooserTitle)
                                isCapturing = false
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(Sizes.iconSmall),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(text = shareImageLabel)
                        }
                    }
                }
            }
        }
    }
}

/** Small brand lockup ("vayana" + a book glyph) printed on every share card, fixed brand colors. */
@Composable
private fun ShareCardWordmark(tint: Color, wordmark: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.AutoStories,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Sizes.icon),
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
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
    footerLeft: String,
    footerRight: String,
    watermark: String,
    modifier: Modifier = Modifier,
    cover: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .width(Sizes.shareCardWidth)
            .aspectRatio(1f)
            .background(Palette.Cream100)
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        ShareCardWordmark(tint = Palette.Forest700, wordmark = watermark)
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(Sizes.shareCardCoverWidth)
                    .height(Sizes.shareCardCoverWidth / Sizes.coverAspectRatio)
                    .clip(RoundedCornerShape(Radii.small)),
            ) {
                cover()
            }
            Spacer(modifier = Modifier.width(Spacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                ShareCardCaption(text = statusLabel, color = Palette.Forest700)
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = title,
                    style = ShareCardTypography.bookTitle,
                    color = Palette.FgPrimaryLight,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                author?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(text = it, style = ShareCardTypography.bookAuthor, color = Palette.TextMutedLight)
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    ShareStat(value = stat1Value, label = stat1Label)
                    ShareStat(value = stat2Value, label = stat2Label)
                }
            }
        }
        Column {
            HorizontalDivider(color = Palette.Cream300)
            Spacer(modifier = Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ShareCardCaption(text = footerLeft, color = Palette.TextMutedLight)
                ShareCardCaption(text = footerRight, color = Palette.TextMutedLight)
            }
        }
    }
}

@Composable
private fun ShareStat(value: String, label: String) {
    Column {
        Text(text = value, style = ShareCardTypography.statValue, color = Palette.FgPrimaryLight)
        ShareCardCaption(text = label, color = Palette.TextMutedLight)
    }
}
