package com.vayana.feature.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.vayana.core.common.QuoteCitation
import com.vayana.core.common.shareText
import com.vayana.core.designsystem.sharecard.QuoteShareCard
import com.vayana.core.designsystem.sharecard.ShareCardDialog
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HighlightReviewRoute(
    onBack: () -> Unit,
    onOpenReader: (bookId: Long, locator: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HighlightReviewViewModel = hiltViewModel()
    val items by viewModel.items.collectAsState()
    val index by viewModel.index.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.highlight_review_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.notes_back_content_description))
                    }
                },
            )
        },
    ) { innerPadding ->
        val loaded = items ?: return@Scaffold
        when {
            loaded.isEmpty() -> HighlightReviewMessage(
                contentPadding = innerPadding,
                title = stringResource(R.string.highlight_review_empty_title),
                body = stringResource(R.string.highlight_review_empty_body),
                onDone = null,
            )
            index >= loaded.size -> HighlightReviewMessage(
                contentPadding = innerPadding,
                title = stringResource(R.string.highlight_review_done_title),
                body = stringResource(R.string.highlight_review_done_body),
                onDone = onBack,
            )
            else -> HighlightReviewCard(
                contentPadding = innerPadding,
                item = loaded[index],
                position = index + 1,
                total = loaded.size,
                onNext = viewModel::next,
                onOpenReader = onOpenReader,
            )
        }
    }
}

@Composable
private fun HighlightReviewCard(
    contentPadding: PaddingValues,
    item: HighlightReviewItem,
    position: Int,
    total: Int,
    onNext: () -> Unit,
    onOpenReader: (bookId: Long, locator: String) -> Unit,
) {
    val context = LocalContext.current
    var sharing by remember(item.annotation.id) { mutableStateOf(false) }
    val annotation = item.annotation

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(Paddings.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        LinearProgressIndicator(progress = { position.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.highlight_review_progress, position, total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(Radii.extraLarge),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Paddings.card),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Icon(Icons.Outlined.FormatQuote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(text = annotation.selectedText, style = MaterialTheme.typography.headlineSmall)
                annotation.readerNote?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(text = note, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = listOfNotNull(item.bookTitle, item.bookAuthor?.takeIf { it.isNotBlank() }, annotation.chapterTitle?.takeIf { it.isNotBlank() })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            IconButton(onClick = { sharing = true }) {
                Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.highlight_review_share))
            }
            if (item.canOpen) {
                FilledTonalButton(
                    onClick = { onOpenReader(annotation.bookId, annotation.locator.ifBlank { "text:${annotation.id}" }) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
                    Text(stringResource(R.string.highlight_review_open_book))
                }
            }
            Button(onClick = onNext, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.highlight_review_next))
            }
        }
    }

    if (sharing) {
        val shareContentDescription = stringResource(R.string.notes_share_content_description)
        ShareCardDialog(
            onDismiss = { sharing = false },
            onShareText = {
                context.shareText(
                    QuoteCitation.format(
                        text = annotation.selectedText,
                        author = item.bookAuthor,
                        bookTitle = item.bookTitle,
                        chapterTitle = annotation.chapterTitle,
                    ),
                    shareContentDescription,
                )
                sharing = false
            },
            chooserTitle = stringResource(R.string.share_card_image_chooser_title),
            shareTextLabel = stringResource(R.string.share_card_share_text),
            shareImageLabel = stringResource(R.string.share_card_share_image),
        ) {
            QuoteShareCard(
                text = annotation.selectedText,
                author = item.bookAuthor,
                bookTitle = item.bookTitle,
                pageLabel = annotation.chapterTitle,
                watermark = stringResource(R.string.share_card_watermark),
                footerRight = stringResource(R.string.share_card_tagline),
            )
        }
    }
}

@Composable
private fun HighlightReviewMessage(contentPadding: PaddingValues, title: String, body: String, onDone: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(Paddings.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(Icons.Outlined.FormatQuote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text = title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            onDone?.let { done ->
                Button(onClick = done, modifier = Modifier.padding(top = Spacing.md)) {
                    Text(stringResource(R.string.highlight_review_done))
                }
            }
        }
    }
}
