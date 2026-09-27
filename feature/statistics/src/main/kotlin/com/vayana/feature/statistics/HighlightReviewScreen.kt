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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.vayana.core.database.repository.ReviewGrade
import com.vayana.core.designsystem.sharecard.QuoteShareDialog
import com.vayana.core.designsystem.theme.VayanaLinearProgressIndicator
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
    val session by viewModel.session.collectAsStateWithLifecycle()
    val index by viewModel.index.collectAsStateWithLifecycle()

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
        val loaded = session ?: return@Scaffold
        val items = loaded.items
        when {
            items.isEmpty() && loaded.reviewableCount == 0 -> HighlightReviewMessage(
                contentPadding = innerPadding,
                title = stringResource(R.string.highlight_review_empty_title),
                body = stringResource(R.string.highlight_review_empty_body),
            )
            items.isEmpty() -> HighlightReviewMessage(
                contentPadding = innerPadding,
                title = stringResource(R.string.highlight_review_caught_up_title),
                body = stringResource(R.string.highlight_review_caught_up_body),
                actionLabel = stringResource(R.string.highlight_review_practice),
                onAction = viewModel::practiceAnyway,
            )
            index >= items.size -> HighlightReviewMessage(
                contentPadding = innerPadding,
                title = stringResource(R.string.highlight_review_done_title),
                body = stringResource(
                    if (loaded.scheduled) R.string.highlight_review_done_body else R.string.highlight_review_practice_done_body,
                ),
                actionLabel = stringResource(R.string.highlight_review_done),
                onAction = onBack,
            )
            else -> HighlightReviewCard(
                contentPadding = innerPadding,
                item = items[index],
                position = index + 1,
                total = items.size,
                scheduled = loaded.scheduled,
                onGrade = viewModel::grade,
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
    scheduled: Boolean,
    onGrade: (ReviewGrade) -> Unit,
    onNext: () -> Unit,
    onOpenReader: (bookId: Long, locator: String) -> Unit,
) {
    var sharing by remember(item.annotation.id) { mutableStateOf(false) }
    val annotation = item.annotation

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(Paddings.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        VayanaLinearProgressIndicator(
            progress = { position.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
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
            verticalAlignment = Alignment.CenterVertically,
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
            if (!scheduled) {
                Button(onClick = onNext, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.highlight_review_next))
                }
            }
        }
        if (scheduled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedButton(onClick = { onGrade(ReviewGrade.AGAIN) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.highlight_review_again))
                }
                Button(onClick = { onGrade(ReviewGrade.GOOD) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.highlight_review_good))
                }
                FilledTonalButton(onClick = { onGrade(ReviewGrade.EASY) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.highlight_review_easy))
                }
            }
        }
    }

    if (sharing) {
        QuoteShareDialog(
            text = annotation.selectedText,
            author = item.bookAuthor,
            bookTitle = item.bookTitle,
            chapterTitle = annotation.chapterTitle,
            onDismiss = { sharing = false },
            coverPath = item.bookCoverPath,
            series = item.bookSeries,
            seriesNumber = item.bookSeriesNumber,
        )
    }
}

@Composable
private fun HighlightReviewMessage(
    contentPadding: PaddingValues,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
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
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction, modifier = Modifier.padding(top = Spacing.md)) {
                    Text(actionLabel)
                }
            }
        }
    }
}
