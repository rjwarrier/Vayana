package com.vayana.feature.library

import com.vayana.core.database.model.BorrowedReadingPlan
import com.vayana.core.database.model.borrowedReadingPlan
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.PhysicalBookOwnership
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.theme.VayanaLinearWavyProgressIndicator
import com.vayana.core.resources.R
import kotlin.math.roundToInt

/**
 * Where the reader is with a started book: a heading ([action], reading-progress sync, at its end), the progress bar
 * while still reading, then four equal tiles - start and last read (or finish) dates, time spent and days taken. The
 * date tiles open a date picker when [onEditStarted] / [onEditFinished] are set.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    /** For a book read outside the app: switches it to another offline type (paper, audiobook, other ebook). */
    onChangeFormat: ((BookFormat) -> Unit)? = null,
    /** For a book read outside the app: opens the page editor. */
    onEditPages: (() -> Unit)? = null,
    /** For a physical book: switches between owned and borrowed. */
    onChangeOwnership: ((PhysicalBookOwnership) -> Unit)? = null,
    /** For a borrowed physical book: opens its return-date editor. */
    onEditBorrowReturnDate: (() -> Unit)? = null,
) {
    if (book.format.isOffline) {
        OfflineReadingStatsCard(
            book = book,
            finished = finished,
            modifier = modifier,
            onEditStarted = onEditStarted,
            onEditFinished = onEditFinished,
            onChangeFormat = onChangeFormat,
            onEditPages = onEditPages,
            onChangeOwnership = onChangeOwnership,
            onEditBorrowReturnDate = onEditBorrowReturnDate,
        )
        return
    }
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
            VayanaLinearWavyProgressIndicator(
                progress = { book.readingPercent.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ReadingStatGrid(stats)
    }
}

/**
 * The reading card for a book read outside the app: only what the reader entered - the page they are on (with the
 * progress bar, as for books read here), start and finish dates (shown as not set until chosen), the days between
 * them, and the book's type, which its tile toggles. Every entered value is editable from its tile.
 */
@Composable
private fun OfflineReadingStatsCard(
    book: Book,
    finished: Boolean,
    modifier: Modifier,
    onEditStarted: (() -> Unit)?,
    onEditFinished: (() -> Unit)?,
    onChangeFormat: ((BookFormat) -> Unit)?,
    onEditPages: (() -> Unit)?,
    onChangeOwnership: ((PhysicalBookOwnership) -> Unit)?,
    onEditBorrowReturnDate: (() -> Unit)?,
) {
    val context = LocalContext.current
    val notSet = stringResource(R.string.offline_book_date_not_set)
    val pageCount = book.pageCount
    val currentPage = book.currentPage()
    val borrowReturnAt = book.borrowReturnAt
    val startedAt = book.startedReadingAt
    val daysText = remember(startedAt, book.finishedReadingAt, context) {
        startedAt?.let { formatDaysTaken(calculateDaysTaken(it, book.finishedReadingAt), context) }
    }
    // Each tap moves to the next type, wrapping round: Physical, Audiobook, Other ebook.
    val offline = BookFormat.Offline
    val otherFormat = offline[(offline.indexOf(book.format) + 1) % offline.size]
    val ownership = book.physicalOwnership ?: PhysicalBookOwnership.OWNED
    val otherOwnership = if (ownership == PhysicalBookOwnership.OWNED) {
        PhysicalBookOwnership.BORROWED
    } else {
        PhysicalBookOwnership.OWNED
    }
    val readingPlan = remember(book.format, ownership, borrowReturnAt, pageCount, currentPage) {
        if (
            book.format == BookFormat.PHYSICAL &&
            ownership == PhysicalBookOwnership.BORROWED &&
            borrowReturnAt != null &&
            pageCount != null &&
            currentPage != null
        ) {
            borrowedReadingPlan(pageCount, currentPage, borrowReturnAt)
        } else {
            null
        }
    }
    val stats = listOfNotNull(
        // An audiobook has no pages, so it gets no page tile.
        if (!book.format.tracksPages) null else ReadingStat(
            label = stringResource(R.string.offline_book_pages_label),
            value = if (pageCount != null && currentPage != null) {
                stringResource(R.string.offline_book_pages_value, currentPage, pageCount)
            } else {
                notSet
            },
            onClick = onEditPages,
            clickLabel = stringResource(R.string.offline_book_edit_pages),
        ),
        ReadingStat(
            label = stringResource(R.string.library_stat_started),
            value = startedAt?.formatDate() ?: notSet,
            onClick = onEditStarted,
            clickLabel = stringResource(R.string.library_edit_started_date),
        ),
        ReadingStat(
            label = stringResource(R.string.library_status_finished),
            value = book.finishedReadingAt?.formatDate() ?: notSet,
            onClick = onEditFinished,
            clickLabel = stringResource(R.string.library_edit_finished_date),
        ),
        ReadingStat(
            label = stringResource(if (finished) R.string.library_stat_days_taken else R.string.library_stat_days_so_far),
            value = daysText ?: notSet,
        ),
    )
    BookDetailSection(
        icon = book.format.offlineIcon(),
        title = when {
            finished -> stringResource(R.string.library_status_finished)
            pageCount != null -> stringResource(R.string.library_progress_value, (book.readingPercent * 100).roundToInt())
            startedAt != null -> stringResource(R.string.offline_book_status_reading)
            else -> stringResource(R.string.library_status_not_started)
        },
        modifier = modifier,
    ) {
        if (!finished && pageCount != null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                VayanaLinearWavyProgressIndicator(
                    progress = { book.readingPercent.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                readingPlan?.let { BorrowedReadingPlanText(it) }
            }
        }
        OfflineBookMetadataChips(
            book = book,
            ownership = ownership,
            otherFormat = otherFormat,
            otherOwnership = otherOwnership,
            onChangeFormat = onChangeFormat,
            onChangeOwnership = onChangeOwnership,
            onEditBorrowReturnDate = onEditBorrowReturnDate,
        )
        ReadingStatGrid(stats)
    }
}

@Composable
private fun OfflineBookMetadataChips(
    book: Book,
    ownership: PhysicalBookOwnership,
    otherFormat: BookFormat,
    otherOwnership: PhysicalBookOwnership,
    onChangeFormat: ((BookFormat) -> Unit)?,
    onChangeOwnership: ((PhysicalBookOwnership) -> Unit)?,
    onEditBorrowReturnDate: (() -> Unit)?,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        AssistChip(
            onClick = { onChangeFormat?.invoke(otherFormat) },
            enabled = onChangeFormat != null,
            label = { Text(book.format.displayLabel()) },
            leadingIcon = {
                Icon(
                    imageVector = book.format.offlineIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
        )
        if (book.format == BookFormat.PHYSICAL) {
            AssistChip(
                onClick = { onChangeOwnership?.invoke(otherOwnership) },
                enabled = onChangeOwnership != null,
                label = { Text(ownership.displayLabel()) },
            )
            if (ownership == PhysicalBookOwnership.BORROWED) {
                AssistChip(
                    onClick = { onEditBorrowReturnDate?.invoke() },
                    enabled = onEditBorrowReturnDate != null,
                    label = {
                        Text(
                            book.borrowReturnAt?.let {
                                stringResource(R.string.offline_book_return_date_value, it.formatDate())
                            } ?: stringResource(R.string.offline_book_return_date_label),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Event,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun BorrowedReadingPlanText(plan: BorrowedReadingPlan) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = pluralStringResource(
                R.plurals.offline_book_days_remaining,
                plan.daysRemaining,
                plan.daysRemaining,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = plan.pagesPerDay?.let { pagesPerDay ->
                pluralStringResource(
                    R.plurals.offline_book_pages_per_day,
                    pagesPerDay,
                    pagesPerDay,
                    plan.finishByAt.formatDate(),
                )
            } ?: stringResource(R.string.offline_book_no_reading_days_remaining),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadingStatGrid(stats: List<ReadingStat>) {
    // Every tile has the same two single-line text slots, so equal-width tiles naturally share a height. Avoiding
    // intrinsic measurement keeps this frequently recomposed card to a single measurement pass.
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (rowStart in stats.indices step ReadingStatColumns) {
            val first = stats[rowStart]
            val second = stats.getOrNull(rowStart + 1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (second == null) {
                    ReadingStatTile(
                        stat = first,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    ReadingStatTile(stat = first, modifier = Modifier.weight(1f))
                    ReadingStatTile(stat = second, modifier = Modifier.weight(1f))
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

internal fun calculateDaysTaken(startedAt: Long, finishedAt: Long?): Int {
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
