package com.vayana.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.vayana.core.database.model.Book
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

@Composable
internal fun ReadingStatsCard(book: Book, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val startedAt = book.startedReadingAt ?: book.lastReadAt ?: book.createdAt
    val isFinished = book.isFinished()
    val daysTaken = remember(startedAt, book.finishedReadingAt) {
        calculateDaysTaken(startedAt = startedAt, finishedAt = book.finishedReadingAt)
    }
    val timeTakenText = remember(book.totalReadingSeconds, context) {
        formatReadingDuration(book.totalReadingSeconds, context)
    }
    val daysTakenText = remember(daysTaken, context) {
        formatDaysTaken(daysTaken, context)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.extraLarge),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        tonalElevation = Elevations.level1,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Timer,
                        contentDescription = null,
                        modifier = Modifier.padding(Spacing.sm),
                    )
                }
                Text(
                    text = stringResource(R.string.library_reading_stats_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                ReadingStatPill(
                    label = stringResource(R.string.library_stat_started),
                    value = startedAt.formatDate(),
                )
                if (isFinished) {
                    ReadingStatPill(
                        label = stringResource(R.string.library_stat_finished),
                        value = (book.finishedReadingAt ?: book.updatedAt).formatDate(),
                    )
                }
                ReadingStatPill(
                    label = stringResource(R.string.library_stat_time_taken),
                    value = timeTakenText,
                )
                ReadingStatPill(
                    label = stringResource(R.string.library_stat_days_taken),
                    value = daysTakenText,
                )
            }
        }
    }
}

@Composable
private fun ReadingStatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(min = Sizes.chipMinWidth),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun formatReadingDuration(totalSeconds: Long, context: android.content.Context): String {
    if (totalSeconds < 60L) {
        return context.getString(R.string.reading_time_less_than_minute)
    }
    val totalMinutes = totalSeconds / 60L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> context.getString(R.string.reading_time_hours_and_minutes, hours, minutes)
        hours > 0L -> context.getString(R.string.reading_time_hours_only, hours)
        else -> context.getString(R.string.reading_time_minutes_only, minutes)
    }
}

private fun calculateDaysTaken(startedAt: Long, finishedAt: Long?): Int {
    val startCal = java.util.Calendar.getInstance().apply {
        timeInMillis = startedAt
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val endCal = java.util.Calendar.getInstance().apply {
        timeInMillis = finishedAt ?: System.currentTimeMillis()
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val diffMillis = endCal.timeInMillis - startCal.timeInMillis
    val days = (diffMillis / (24 * 60 * 60 * 1000L)).toInt() + 1
    return days.coerceAtLeast(1)
}

private fun formatDaysTaken(days: Int, context: android.content.Context): String =
    if (days == 1) {
        context.getString(R.string.reading_days_single)
    } else {
        context.getString(R.string.reading_days_plural, days)
    }
