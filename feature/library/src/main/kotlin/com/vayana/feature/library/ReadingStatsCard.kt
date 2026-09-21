package com.vayana.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
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
import com.vayana.core.designsystem.theme.VayanaLinearProgressIndicator
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import kotlin.math.roundToInt

/**
 * Where the reader is with a started book. While [finished] is false it leads with progress (a bar and the percent
 * read) and counts time so far; once finished it becomes a highlighted summary headed by the finish date. [action]
 * (reading-progress sync) sits at the end of the heading.
 */
@Composable
internal fun ReadingStatsCard(
    book: Book,
    finished: Boolean,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    /** Set to let the reader correct the start date; the Started pill then opens it. */
    onEditStarted: (() -> Unit)? = null,
    /** As [onEditStarted], for the finish date of a finished book. */
    onEditFinished: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val startedAt = book.startedReadingAt ?: book.lastReadAt ?: book.createdAt
    val daysTaken = remember(startedAt, book.finishedReadingAt) {
        calculateDaysTaken(startedAt = startedAt, finishedAt = book.finishedReadingAt)
    }
    val timeTakenText = remember(book.totalReadingSeconds, context) {
        formatReadingDuration(book.totalReadingSeconds, context)
    }
    val daysTakenText = remember(daysTaken, context) {
        formatDaysTaken(daysTaken, context)
    }
    val finishedAt = book.finishedReadingAt
    val stats = buildList {
        add(
            ReadingStat(
                label = stringResource(R.string.library_stat_started),
                value = startedAt.formatDate(),
                onClick = onEditStarted,
                clickLabel = stringResource(R.string.library_edit_started_date),
            ),
        )
        if (finished) {
            add(
                ReadingStat(
                    label = stringResource(R.string.library_status_finished),
                    value = (finishedAt ?: book.updatedAt).formatDate(),
                    onClick = onEditFinished,
                    clickLabel = stringResource(R.string.library_edit_finished_date),
                ),
            )
        }
        add(
            ReadingStat(
                label = stringResource(if (finished) R.string.library_stat_time_taken else R.string.library_stat_time_so_far),
                value = timeTakenText,
            ),
        )
        add(
            ReadingStat(
                label = stringResource(if (finished) R.string.library_stat_days_taken else R.string.library_stat_days_so_far),
                value = daysTakenText,
            ),
        )
    }
    // Finished: the date is one of the (editable) stats, so the heading just says so.
    val title = if (finished) {
        stringResource(R.string.library_status_finished)
    } else {
        stringResource(R.string.library_progress_value, (book.readingPercent * 100).roundToInt())
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.extraLarge),
        color = if (finished) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (finished) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
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
                    color = if (finished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (finished) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(
                        imageVector = if (finished) Icons.Outlined.TaskAlt else Icons.Outlined.Timer,
                        contentDescription = null,
                        modifier = Modifier.padding(Spacing.sm),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
            }
            if (!finished) {
                VayanaLinearProgressIndicator(
                    progress = { book.readingPercent.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val minimumRowWidth = (Sizes.chipMinWidth * stats.size) +
                    (Spacing.sm * (stats.size - 1).coerceAtLeast(0))
                if (maxWidth >= minimumRowWidth) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        stats.forEach { stat ->
                            ReadingStatPill(stat = stat, modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        stats.forEach { stat ->
                            ReadingStatPill(stat = stat)
                        }
                    }
                }
            }
        }
    }
}

/** One figure on the reading card; [onClick] makes it editable (the reading dates). */
private class ReadingStat(
    val label: String,
    val value: String,
    val onClick: (() -> Unit)? = null,
    val clickLabel: String? = null,
)

@Composable
private fun ReadingStatPill(stat: ReadingStat, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radii.large)
    val onClick = stat.onClick
    Surface(
        modifier = modifier
            .widthIn(min = Sizes.chipMinWidth)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = stat.clickLabel, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = stat.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Sizes.iconXSmall),
                    )
                }
            }
            Text(
                text = stat.value,
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
