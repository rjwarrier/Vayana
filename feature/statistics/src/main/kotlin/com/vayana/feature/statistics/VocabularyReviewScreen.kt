package com.vayana.feature.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.vayana.core.database.model.VocabularyCard
import com.vayana.core.designsystem.theme.vayanaAnimateContentSize
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VocabularyReviewRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: VocabularyReviewViewModel = hiltViewModel()
    val cards by viewModel.cards.collectAsState()
    val index by viewModel.index.collectAsState()
    val flipped by viewModel.flipped.collectAsState()
    val loaded by viewModel.loaded.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vocabulary_review_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.notes_back_content_description))
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            !loaded -> Unit
            cards.isEmpty() -> VocabularyReviewEmptyState(contentPadding = innerPadding)
            index >= cards.size -> VocabularyReviewDoneState(
                contentPadding = innerPadding,
                onBack = onBack,
                onReviewMore = viewModel::reviewMore,
            )
            else -> VocabularyReviewCardScreen(
                contentPadding = innerPadding,
                card = cards[index],
                position = index + 1,
                total = cards.size,
                flipped = flipped,
                onFlip = viewModel::flip,
                onMarkKnown = { viewModel.markCurrent(true) },
                onMarkStillLearning = { viewModel.markCurrent(false) },
            )
        }
    }
}

@Composable
private fun VocabularyReviewCardScreen(
    contentPadding: PaddingValues,
    card: VocabularyCard,
    position: Int,
    total: Int,
    flipped: Boolean,
    onFlip: () -> Unit,
    onMarkKnown: () -> Unit,
    onMarkStillLearning: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(Paddings.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        LinearProgressIndicator(
            progress = { position.toFloat() / total.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.vocabulary_review_progress, position, total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(enabled = !flipped, onClick = onFlip),
            shape = RoundedCornerShape(Radii.extraLargeIncreased),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = Elevations.shadowLarge,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.xl)
                    .vayanaAnimateContentSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = card.word, style = MaterialTheme.typography.displaySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                card.sentence?.takeIf { it.isNotBlank() }?.let { sentence ->
                    Text(
                        text = "“$sentence”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
                if (flipped) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.lg),
                        shape = RoundedCornerShape(Radii.medium),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(
                            text = card.definition,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(Spacing.lg),
                        )
                    }
                    card.bookTitle?.takeIf { it.isNotBlank() }?.let { bookTitle ->
                        Text(
                            text = bookTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.vocabulary_review_flip_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.lg),
                    )
                }
            }
        }

        if (flipped) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedButton(onClick = onMarkStillLearning, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Close, contentDescription = null)
                    Text(text = stringResource(R.string.vocabulary_review_still_learning), modifier = Modifier.padding(start = Spacing.sm))
                }
                Button(
                    onClick = onMarkKnown,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Text(text = stringResource(R.string.vocabulary_review_know_it), modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }
    }
}

@Composable
private fun VocabularyReviewEmptyState(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(Paddings.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Style,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Sizes.iconLarge).padding(bottom = Spacing.md),
            )
            Text(text = stringResource(R.string.vocabulary_review_empty_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.vocabulary_review_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun VocabularyReviewDoneState(contentPadding: PaddingValues, onBack: () -> Unit, onReviewMore: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(Paddings.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.vocabulary_review_done_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = Spacing.md),
            )
            Text(
                text = stringResource(R.string.vocabulary_review_done_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            Button(onClick = onReviewMore, modifier = Modifier.padding(top = Spacing.lg)) {
                Text(stringResource(R.string.vocabulary_review_more))
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = Spacing.sm)) {
                Text(stringResource(R.string.notes_back_content_description))
            }
        }
    }
}
