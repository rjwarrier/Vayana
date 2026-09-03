package com.vayana.feature.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
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
        summary.highlightToRevisit?.let { highlight ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShapeCompat,
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
        shape = RoundedCornerShapeCompat,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(supportingText) },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.icon),
                )
            },
            trailingContent = {
                Text(text = value, style = MaterialTheme.typography.titleLarge)
            },
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

private val RoundedCornerShapeCompat
    @Composable get() = androidx.compose.foundation.shape.RoundedCornerShape(Radii.large)
