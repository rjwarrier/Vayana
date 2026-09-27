package com.vayana.feature.library

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.vayana.core.database.model.Book
import com.vayana.core.resources.R

/**
 * What a book's share card shows in its status, two stats and footer, and what the option chips that toggle them are
 * called. A book read outside the app has no reading progress or time here, so its format and days taken stand in,
 * and its progress bar only appears once its page count is known.
 */
internal class BookShareStats(
    val statusLabel: String,
    val stat1Value: String,
    val stat1Label: String,
    val stat2Value: String,
    val stat2Label: String,
    val footerLeft: String,
    val progressFraction: Float?,
    @param:StringRes val stat1OptionLabel: Int,
    @param:StringRes val stat2OptionLabel: Int,
    @param:StringRes val dateOptionLabel: Int,
)

@Composable
internal fun Book.shareStats(): BookShareStats {
    val percent = (readingPercent * 100).toInt()
    if (!format.isOffline) {
        val seconds = totalReadingSeconds
        return BookShareStats(
            statusLabel = if (finishedReadingAt != null) {
                stringResource(R.string.share_card_status_finished)
            } else {
                stringResource(R.string.share_card_status_progress, percent)
            },
            stat1Value = "$percent%",
            stat1Label = stringResource(R.string.share_card_stat_progress_label),
            stat2Value = stringResource(R.string.share_card_stat_hours, (seconds / 3600).toInt(), ((seconds % 3600) / 60).toInt()),
            stat2Label = stringResource(R.string.share_card_stat_read_time_label),
            footerLeft = stringResource(R.string.library_imported_on, createdAt.formatDate()),
            progressFraction = readingPercent,
            stat1OptionLabel = R.string.share_card_image_options_progress,
            stat2OptionLabel = R.string.share_card_image_options_read_time,
            dateOptionLabel = R.string.share_card_image_options_imported_date,
        )
    }
    val days = remember(startedReadingAt, finishedReadingAt) {
        startedReadingAt?.let { calculateDaysTaken(it, finishedReadingAt) }
    }
    return BookShareStats(
        statusLabel = stringResource(
            if (finishedReadingAt != null) R.string.share_card_status_finished else R.string.share_card_status_reading,
        ),
        stat1Value = format.displayLabel(),
        stat1Label = stringResource(R.string.share_card_stat_format_label),
        stat2Value = days?.toString() ?: "-",
        stat2Label = stringResource(R.string.share_card_stat_days_label),
        footerLeft = stringResource(R.string.library_added_on, createdAt.formatDate()),
        progressFraction = readingPercent.takeIf { pageCount != null },
        stat1OptionLabel = R.string.share_card_image_options_format,
        stat2OptionLabel = R.string.share_card_image_options_days,
        dateOptionLabel = R.string.share_card_image_options_added_date,
    )
}
