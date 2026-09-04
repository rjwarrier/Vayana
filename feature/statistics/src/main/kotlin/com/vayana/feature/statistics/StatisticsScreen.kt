package com.vayana.feature.statistics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.vayana.core.database.model.WordLookupStat
import com.vayana.core.designsystem.theme.vayanaAnimateContentSize
import com.vayana.core.designsystem.theme.vayanaSpring
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

@Composable
fun StatisticsRoute(modifier: Modifier = Modifier) {
    val viewModel: StatisticsViewModel = hiltViewModel()
    val summary by viewModel.summary.collectAsState()

    StatisticsScreen(modifier = modifier, summary = summary)
}

@Composable
private fun StatisticsScreen(modifier: Modifier = Modifier, summary: StatisticsSummary) {
    Scaffold(
        modifier = modifier,
        topBar = {
            Text(
                text = stringResource(R.string.statistics_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(Paddings.screenHorizontal),
            )
        },
    ) { innerPadding ->
        if (summary.totalBooks == 0 && summary.totalAnnotations == 0) {
            StatisticsEmptyState(contentPadding = innerPadding)
        } else {
            StatisticsDashboard(contentPadding = innerPadding, summary = summary)
        }
    }
}

@Composable
private fun StatisticsDashboard(contentPadding: PaddingValues, summary: StatisticsSummary) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Paddings.screenHorizontal,
            top = contentPadding.calculateTopPadding() + Spacing.md,
            end = Paddings.screenHorizontal,
            bottom = contentPadding.calculateBottomPadding() + Spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            ReadingMixCard(summary = summary)
        }
        item {
            StatisticTile(
                icon = Icons.Outlined.AutoStories,
                title = stringResource(R.string.statistics_library_total_title),
                value = stringResource(R.string.statistics_library_total_value, summary.totalBooks),
                supportingText = stringResource(
                    R.string.statistics_library_total_support,
                    summary.readingBooks,
                    summary.finishedBooks,
                ),
            )
        }
        item {
            StatisticTile(
                icon = Icons.Outlined.BarChart,
                title = stringResource(R.string.statistics_average_progress_title),
                value = stringResource(R.string.statistics_average_progress_value, summary.averageProgressPercent),
                supportingText = stringResource(R.string.statistics_average_progress_support),
            )
        }
        item {
            StatisticTile(
                icon = Icons.Outlined.EditNote,
                title = stringResource(R.string.statistics_notes_total_title),
                value = stringResource(R.string.statistics_notes_total_value, summary.totalAnnotations),
                supportingText = stringResource(R.string.statistics_notes_total_support, summary.notesWithText),
            )
        }
        if (summary.topLookedUpWords.isNotEmpty()) {
            item {
                TopWordsCard(words = summary.topLookedUpWords)
            }
        }
        summary.highlightToRevisit?.let { highlight ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radii.medium),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        Text(
                            text = stringResource(R.string.statistics_highlight_revisit_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = highlight.selectedText,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                        highlight.chapterTitle?.let { chapter ->
                            Text(
                                text = chapter,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticTile(icon: ImageVector, title: String, value: String, supportingText: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Paddings.card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Surface(
                shape = RoundedCornerShape(Radii.small),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(Spacing.sm)
                        .size(Sizes.icon),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun TopWordsCard(words: List<WordLookupStat>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(Paddings.card)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(Sizes.icon))
                Text(text = stringResource(R.string.statistics_vocabulary_title), style = MaterialTheme.typography.titleMedium)
            }
            FlowRow(
                modifier = Modifier.padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                words.forEach { word ->
                    Surface(
                        shape = RoundedCornerShape(Radii.small),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.statistics_vocabulary_word_count, word.word, word.count),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingMixCard(summary: StatisticsSummary) {
    val maxValue = listOf(summary.readingBooks, summary.finishedBooks, summary.totalAnnotations).maxOrNull()?.coerceAtLeast(1) ?: 1
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .vayanaAnimateContentSize(),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(Paddings.card)) {
            Text(
                text = stringResource(R.string.statistics_chart_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lg)
                    .height(Sizes.chartHeight),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalAlignment = Alignment.Bottom,
            ) {
                ChartBar(
                    label = stringResource(R.string.statistics_chart_reading),
                    value = summary.readingBooks,
                    maxValue = maxValue,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                ChartBar(
                    label = stringResource(R.string.statistics_chart_finished),
                    value = summary.finishedBooks,
                    maxValue = maxValue,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                ChartBar(
                    label = stringResource(R.string.statistics_chart_notes),
                    value = summary.totalAnnotations,
                    maxValue = maxValue,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ChartBar(label: String, value: Int, maxValue: Int, color: Color, modifier: Modifier = Modifier) {
    val targetFraction = (value.toFloat() / maxValue).coerceIn(0.08f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = vayanaSpring(),
        label = "ChartBarFraction",
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(text = value.toString(), style = MaterialTheme.typography.labelLarge)
        Box(
            modifier = Modifier
                .padding(top = Spacing.xs)
                .widthIn(min = Sizes.chartBarMinWidth, max = Sizes.chartBarMaxWidth)
                .fillMaxWidth()
                .height(Sizes.chartBarMaxHeight * animatedFraction)
                .clip(RoundedCornerShape(topStart = Radii.small, topEnd = Radii.small))
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun StatisticsEmptyState(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(Paddings.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.BarChart,
            contentDescription = null,
            modifier = Modifier.size(Sizes.iconLarge),
        )
        Text(
            text = stringResource(R.string.statistics_empty_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Text(
            text = stringResource(R.string.statistics_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}
