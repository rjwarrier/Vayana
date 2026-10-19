package com.vayana.feature.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.vayana.core.designsystem.theme.VayanaLinearProgressIndicator
import com.vayana.core.designsystem.theme.asAppDate
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.time.format.TextStyle
import kotlin.math.abs

/** Week / Month / Year / All time as an M3 Expressive connected toggle group. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PeriodSelector(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit, modifier: Modifier = Modifier) {
    val periods = StatsPeriod.entries
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        periods.forEachIndexed { index, period ->
            ToggleButton(
                checked = period == selected,
                onCheckedChange = { onSelect(period) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    periods.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            ) {
                Text(stringResource(period.labelRes()), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The selected period: time read in large type, how it compares, and a bar per day or month. Tap a bar for its time. */
@Composable
internal fun PeriodHeroCard(stats: PeriodStats) {
    var selected by remember(stats.period) { mutableStateOf<PeriodBucket?>(null) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = StatsCardShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(modifier = Modifier.padding(StatsCardPadding)) {
            Text(
                text = selected?.let { bucket -> bucketLabel(bucket, stats.bucketsAreMonths) } ?: stringResource(stats.period.titleRes()),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = formatDuration(selected?.seconds ?: stats.totalSeconds),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = Spacing.xs),
            )
            // Nothing read in the previous period is no baseline: "30h more than nothing" says nothing.
            stats.previousSeconds?.takeIf { selected == null && it > 0L }?.let { previous ->
                ComparisonChip(stats.totalSeconds - previous, stats.period, Modifier.padding(top = Spacing.sm))
            }
            Text(
                text = listOf(
                    pluralStringResource(R.plurals.statistics_period_sessions, stats.sessionCount, stats.sessionCount),
                    pluralStringResource(R.plurals.statistics_period_days, stats.activeDays, stats.activeDays),
                    pluralStringResource(R.plurals.statistics_period_finished, stats.booksFinished, stats.booksFinished),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.md),
            )
            PeriodBars(stats, selected, onSelect = { bucket -> selected = bucket.takeUnless { it == selected } },
                modifier = Modifier.padding(top = Spacing.lg))
        }
    }
}

@Composable
private fun ComparisonChip(differenceSeconds: Long, period: StatsPeriod, modifier: Modifier = Modifier) {
    val up = differenceSeconds >= 0
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Surface(
            shape = RoundedCornerShape(Radii.full),
            // Inverted against the hero's container so it reads on any palette.
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (up) 1f else ChipDownAlpha),
            contentColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(
                    imageVector = if (up) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.iconSmall),
                )
                Text(
                    text = stringResource(
                        if (up) R.string.statistics_period_more else R.string.statistics_period_less,
                        formatDuration(abs(differenceSeconds)),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Text(text = stringResource(period.compareRes()), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PeriodBars(stats: PeriodStats, selected: PeriodBucket?, onSelect: (PeriodBucket) -> Unit, modifier: Modifier = Modifier) {
    val buckets = stats.buckets
    val max = buckets.maxOfOrNull { it.seconds }?.coerceAtLeast(1L) ?: 1L
    val locale = LocalLocale.current.platformLocale
    val today = remember { java.time.LocalDate.now() }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(Sizes.chartBarMaxHeight),
            horizontalArrangement = Arrangement.spacedBy(if (buckets.size > 12) Paddings.heatmapCellGap else Spacing.xs),
            verticalAlignment = Alignment.Bottom,
        ) {
            buckets.forEach { bucket ->
                val current = if (stats.bucketsAreMonths) bucket.start.year == today.year && bucket.start.month == today.month
                    else bucket.start == today
                val ink = MaterialTheme.colorScheme.onPrimaryContainer
                val color = when {
                    bucket.isFuture -> ink.copy(alpha = FutureBarAlpha)
                    bucket == selected || (selected == null && current) -> ink
                    bucket.seconds > 0L -> ink.copy(alpha = InactiveBarAlpha)
                    else -> ink.copy(alpha = EmptyBarAlpha)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = !bucket.isFuture) { onSelect(bucket) },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = Sizes.chartBarMaxWidth)
                            .fillMaxWidth()
                            .fillMaxHeight((bucket.seconds.toFloat() / max).coerceIn(MinBarFraction, 1f))
                            .clip(RoundedCornerShape(Radii.extraSmall))
                            .background(color),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
            buckets.forEachIndexed { index, bucket ->
                val label = when (stats.period) {
                    StatsPeriod.WEEK -> bucket.start.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
                    StatsPeriod.MONTH -> bucket.start.dayOfMonth.takeIf { index % 7 == 0 }?.toString()
                    StatsPeriod.YEAR -> bucket.start.month.getDisplayName(TextStyle.NARROW, locale)
                    StatsPeriod.ALL -> when {
                        buckets.size <= 12 -> bucket.start.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
                        bucket.start.monthValue == 1 || index == 0 -> bucket.start.year.toString()
                        else -> null
                    }
                }
                Text(
                    text = label.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                    // Day and month letters sit under their bar; sparse labels (month days, years) start at theirs.
                    textAlign = if (stats.period == StatsPeriod.MONTH || buckets.size > 12) TextAlign.Start else TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun bucketLabel(bucket: PeriodBucket, months: Boolean): String =
    if (months) "${bucket.start.month.getDisplayName(TextStyle.FULL_STANDALONE, LocalLocale.current.platformLocale)} ${bucket.start.year}"
    else bucket.start.asAppDate()

/** Streak and today's goal side by side: the two numbers a daily habit runs on. */
@Composable
internal fun StreakGoalCard(streakDays: Int, todayMinutes: Int, goalMinutes: Int) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = StatsCardShape, color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
        Row(modifier = Modifier.padding(StatsCardPadding), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                    Text(stringResource(R.string.statistics_streak_title), style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    text = pluralStringResource(R.plurals.statistics_streak_days, streakDays, streakDays),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            if (goalMinutes > 0) Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.statistics_goals_daily_label), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = stringResource(R.string.statistics_goals_daily_value, todayMinutes, goalMinutes),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                VayanaLinearProgressIndicator(
                    progress = { (todayMinutes.toFloat() / goalMinutes).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                )
            }
        }
    }
}

/** When in the day and the week the period's reading happened, and how long sessions ran. */
@Composable
internal fun ReadingRhythmCard(stats: PeriodStats) {
    val locale = LocalLocale.current.platformLocale
    val total = stats.timeOfDaySeconds.sum().coerceAtLeast(1L)
    val peak = TimeOfDay.entries[stats.timeOfDaySeconds.indices.maxBy { stats.timeOfDaySeconds[it] }]
    Surface(modifier = Modifier.fillMaxWidth(), shape = StatsCardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(StatsCardPadding)) {
            Text(text = stringResource(R.string.statistics_rhythm_title), style = statsCardTitle)
            Text(
                text = stringResource(peak.peakRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TimeOfDay.entries.forEach { slot ->
                    val share = (stats.timeOfDaySeconds[slot.ordinal] * 100 / total).toInt()
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Radii.large),
                        color = if (slot == peak) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (slot == peak) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    ) {
                        Column(modifier = Modifier.padding(vertical = Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(slot.icon(), contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                            Text(text = stringResource(R.string.statistics_rhythm_share, share), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = stringResource(slot.labelRes()),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            val weekdayMax = stats.weekdaySeconds.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
            val busiest = stats.weekdaySeconds.maxByOrNull { it.second }?.takeIf { it.second > 0L }?.first
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg).height(Sizes.chartBarMaxHeight / 2),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Bottom,
            ) {
                stats.weekdaySeconds.forEach { (day, seconds) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight((seconds.toFloat() / weekdayMax).coerceIn(MinBarFraction, 1f))
                            .clip(RoundedCornerShape(Radii.extraSmall))
                            .background(
                                if (day == busiest) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = InactiveBarAlpha),
                            ),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                stats.weekdaySeconds.forEach { (day, _) ->
                    Text(
                        text = day.getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            listOfNotNull(
                busiest?.let { stringResource(R.string.statistics_habits_busiest_day, it.getDisplayName(TextStyle.FULL, locale)) },
                stringResource(R.string.statistics_rhythm_average, formatDuration(stats.averageSessionSeconds)),
                stringResource(R.string.statistics_rhythm_longest, formatDuration(stats.longestSessionSeconds)),
            ).forEach { fact ->
                Text(text = fact, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.sm))
            }
        }
    }
}

/** The books the period's time went to, most first. */
@Composable
internal fun TopBooksCard(books: List<BookTime>, onOpenBook: (Long) -> Unit) {
    val max = books.firstOrNull()?.seconds?.coerceAtLeast(1L) ?: 1L
    Surface(modifier = Modifier.fillMaxWidth(), shape = StatsCardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(StatsCardPadding)) {
            Text(text = stringResource(R.string.statistics_top_books_title), style = statsCardTitle)
            books.forEach { (book, seconds) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md)
                        .clip(RoundedCornerShape(Radii.medium))
                        .clickable { onOpenBook(book.id) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    BookCoverThumb(book)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        book.author?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        VayanaLinearProgressIndicator(
                            progress = { seconds.toFloat() / max },
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                        )
                    }
                    Text(formatDuration(seconds), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** A section title in the grid, spanning every column. */
@Composable
internal fun StatsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.md, start = Spacing.xs),
    )
}

/** "1 h 5 min", "12 min", "0 min": like [formatSessionDuration] but honest about zero. */
@Composable
internal fun formatDuration(seconds: Long): String {
    val totalMinutes = (seconds / 60L).toInt()
    return if (totalMinutes >= 60) stringResource(R.string.reader_duration_hours_minutes, totalMinutes / 60, totalMinutes % 60)
    else stringResource(R.string.reader_duration_minutes, totalMinutes)
}

private fun StatsPeriod.labelRes(): Int = when (this) {
    StatsPeriod.WEEK -> R.string.statistics_period_week
    StatsPeriod.MONTH -> R.string.statistics_period_month
    StatsPeriod.YEAR -> R.string.statistics_period_year
    StatsPeriod.ALL -> R.string.statistics_period_all
}

private fun StatsPeriod.titleRes(): Int = when (this) {
    StatsPeriod.WEEK -> R.string.statistics_period_title_week
    StatsPeriod.MONTH -> R.string.statistics_period_title_month
    StatsPeriod.YEAR -> R.string.statistics_period_title_year
    StatsPeriod.ALL -> R.string.statistics_period_all
}

private fun StatsPeriod.compareRes(): Int = when (this) {
    StatsPeriod.WEEK -> R.string.statistics_period_compare_week
    StatsPeriod.MONTH -> R.string.statistics_period_compare_month
    StatsPeriod.YEAR, StatsPeriod.ALL -> R.string.statistics_period_compare_year
}

private fun TimeOfDay.labelRes(): Int = when (this) {
    TimeOfDay.MORNING -> R.string.statistics_time_morning
    TimeOfDay.AFTERNOON -> R.string.statistics_time_afternoon
    TimeOfDay.EVENING -> R.string.statistics_time_evening
    TimeOfDay.NIGHT -> R.string.statistics_time_night
}

private fun TimeOfDay.peakRes(): Int = when (this) {
    TimeOfDay.MORNING -> R.string.statistics_rhythm_peak_morning
    TimeOfDay.AFTERNOON -> R.string.statistics_rhythm_peak_afternoon
    TimeOfDay.EVENING -> R.string.statistics_rhythm_peak_evening
    TimeOfDay.NIGHT -> R.string.statistics_rhythm_peak_night
}

private fun TimeOfDay.icon(): ImageVector = when (this) {
    TimeOfDay.MORNING -> Icons.Outlined.Coffee
    TimeOfDay.AFTERNOON -> Icons.Outlined.WbSunny
    TimeOfDay.EVENING -> Icons.Outlined.WbTwilight
    TimeOfDay.NIGHT -> Icons.Outlined.Bedtime
}

private const val InactiveBarAlpha = 0.45f
private const val EmptyBarAlpha = 0.12f
private const val FutureBarAlpha = 0.06f
private const val ChipDownAlpha = 0.7f
private const val MinBarFraction = 0.04f
