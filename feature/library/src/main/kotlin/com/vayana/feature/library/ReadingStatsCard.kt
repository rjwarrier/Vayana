package com.vayana.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
 * Where the reader is with a started book: a heading ([action], reading-progress sync, at its end), the progress bar
 * while still reading, then four equal tiles - start and last read (or finish) dates, time spent and days taken. The
 * date tiles open a date picker when [onEditStarted] / [onEditFinished] are set.
 */
@Composable
internal fun ReadingStatsCard(
    book: Book,
    finished: Boolean,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    /** Set to let the reader correct the start date; the Started tile then opens it. */
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
    val started = ReadingStat(
        label = stringResource(R.string.library_stat_started),
        value = startedAt.formatDate(),
        onClick = onEditStarted,
        clickLabel = stringResource(R.string.library_edit_started_date),
    )
    val stats = if (finished) {
        listOf(
            started,
            ReadingStat(
                label = stringResource(R.string.library_status_finished),
                value = (book.finishedReadingAt ?: book.updatedAt).formatDate(),
                onClick = onEditFinished,
                clickLabel = stringResource(R.string.library_edit_finished_date),
            ),
            ReadingStat(stringResource(R.string.library_stat_time_taken), timeTakenText),
            ReadingStat(stringResource(R.string.library_stat_days_taken), daysTakenText),
        )
    } else {
        listOf(
            started,
            ReadingStat(stringResource(R.string.library_stat_last_read), (book.lastReadAt ?: startedAt).formatDate()),
            ReadingStat(stringResource(R.string.library_stat_time_so_far), timeTakenText),
            ReadingStat(stringResource(R.string.library_stat_days_so_far), daysTakenText),
        )
    }

    BookDetailSection(
        icon = if (finished) Icons.Outlined.TaskAlt else Icons.Outlined.Timer,
        title = if (finished) {
            stringResource(R.string.library_status_finished)
        } else {
            stringResource(R.string.library_progress_value, (book.readingPercent * 100).roundToInt())
        },
        modifier = modifier,
        action = action,
    ) {
        if (!finished) {
            VayanaLinearProgressIndicator(
                progress = { book.readingPercent.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Equal tiles in a fixed two-column grid: each row takes its tallest tile's height, every tile its share
        // of the width, so the grid never wraps into uneven rows.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            stats.chunked(ReadingStatColumns).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    row.forEach { stat ->
                        ReadingStatTile(
                            stat = stat,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    repeat(ReadingStatColumns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
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
private fun ReadingStatTile(stat: ReadingStat, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radii.large)
    val onClick = stat.onClick
    Surface(
        modifier = modifier
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = stat.clickLabel, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = stat.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stat.value,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // At the tile's end rather than beside the label, so editable tiles are exactly as tall as the others.
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = Spacing.xs)
                        .size(Sizes.iconSmall),
                )
            }
        }
    }
}

private const val ReadingStatColumns = 2

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
