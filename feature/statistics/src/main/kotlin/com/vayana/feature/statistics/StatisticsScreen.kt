package com.vayana.feature.statistics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vayana.core.designsystem.theme.PagedLazyVerticalStaggeredGrid
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.WordLookupStat
import com.vayana.core.designsystem.theme.VayanaLinearProgressIndicator
import com.vayana.core.designsystem.theme.vayanaAnimateContentSize
import com.vayana.core.designsystem.theme.vayanaSpring
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.theme.LocalFloatingNavigationInset
import com.vayana.core.designsystem.tokens.Strokes
import com.vayana.core.resources.R
import android.text.format.Formatter
import java.time.LocalDate
import com.vayana.core.designsystem.theme.asAppDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StatisticsRoute(
    onReviewVocabulary: () -> Unit,
    onOpenLearnWords: () -> Unit,
    onReviewHighlights: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: StatisticsViewModel = hiltViewModel()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val vocabularyCardCount by viewModel.vocabularyCardCount.collectAsStateWithLifecycle()
    val vocabularyDueCount by viewModel.vocabularyDueCount.collectAsStateWithLifecycle()
    val highlightsDue by viewModel.highlightsDue.collectAsStateWithLifecycle()
    val libraryStorage by viewModel.libraryStorage.collectAsStateWithLifecycle()
    val deviceStorage by viewModel.deviceStorage.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refreshHighlightDue() }

    StatisticsScreen(
        modifier = modifier,
        summary = summary,
        vocabularyCardCount = vocabularyCardCount,
        vocabularyDueCount = vocabularyDueCount,
        highlightsDue = highlightsDue,
        libraryStorage = libraryStorage,
        deviceStorage = deviceStorage,
        onReviewVocabulary = onReviewVocabulary,
        onOpenLearnWords = onOpenLearnWords,
        onReviewHighlights = onReviewHighlights,
    )
}

@Composable
private fun StatisticsScreen(
    modifier: Modifier = Modifier,
    summary: StatisticsSummary,
    vocabularyCardCount: Int,
    vocabularyDueCount: Int,
    highlightsDue: List<Annotation>,
    libraryStorage: LibraryStorage?,
    deviceStorage: DeviceStorage?,
    onReviewVocabulary: () -> Unit,
    onOpenLearnWords: () -> Unit,
    onReviewHighlights: () -> Unit,
) {
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
        if (summary.totalBooks == 0 && summary.totalAnnotations == 0 && vocabularyCardCount == 0) {
            StatisticsEmptyState(contentPadding = innerPadding)
        } else {
            StatisticsDashboard(
                contentPadding = innerPadding,
                summary = summary,
                vocabularyCardCount = vocabularyCardCount,
                vocabularyDueCount = vocabularyDueCount,
                highlightsDue = highlightsDue,
                libraryStorage = libraryStorage,
                deviceStorage = deviceStorage,
                onReviewVocabulary = onReviewVocabulary,
                onOpenLearnWords = onOpenLearnWords,
                onReviewHighlights = onReviewHighlights,
            )
        }
    }
}

@Composable
private fun StatisticsDashboard(
    contentPadding: PaddingValues,
    summary: StatisticsSummary,
    vocabularyCardCount: Int,
    vocabularyDueCount: Int,
    highlightsDue: List<Annotation>,
    libraryStorage: LibraryStorage?,
    deviceStorage: DeviceStorage?,
    onReviewVocabulary: () -> Unit,
    onOpenLearnWords: () -> Unit,
    onReviewHighlights: () -> Unit,
) {
    var showYearReview by remember { mutableStateOf(false) }
    if (showYearReview) summary.yearReview?.let { review -> YearReviewDialog(review) { showYearReview = false } }
    PagedLazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 340.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Paddings.screenHorizontal,
            top = contentPadding.calculateTopPadding() + Spacing.md,
            end = Paddings.screenHorizontal,
            bottom = contentPadding.calculateBottomPadding() + Spacing.md + LocalFloatingNavigationInset.current,
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalItemSpacing = Spacing.md,
    ) {
        item { StatisticsOverviewCard(summary) }
        item {
            ReadingActivityCard(
                dailyMinutes = summary.dailyReadingMinutes,
                recentWeekMinutes = summary.recentWeekReadingMinutes,
                streakDays = summary.currentStreakDays,
            )
        }
        summary.readingPace?.let { pace ->
            item {
                ReadingPaceCard(pace = pace)
            }
        }
        summary.yearInBooks?.takeIf { it.finishedCount > 0 || summary.yearlyGoalBooks > 0 }?.let { year ->
            item { YearInBooksCard(year, onOpenReview = summary.yearReview?.let { { showYearReview = true } }) }
        }
        summary.readingHabits?.let { habits ->
            item {
                ReadingHabitsCard(habits = habits)
            }
        }
        if (summary.dailyGoalMinutes > 0) {
            item {
                GoalsCard(summary = summary)
            }
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
        if (deviceStorage != null || libraryStorage != null) {
            item { StorageCard(device = deviceStorage, books = libraryStorage) }
        }
        if (summary.readingBooks > 0) {
            item {
                StatisticTile(
                    icon = Icons.Outlined.BarChart,
                    title = stringResource(R.string.statistics_average_progress_title),
                    value = stringResource(R.string.statistics_average_progress_value, summary.averageProgressPercent),
                    supportingText = stringResource(R.string.statistics_average_progress_support),
                )
            }
        }
        item {
            StatisticTile(
                icon = Icons.Outlined.EditNote,
                title = stringResource(R.string.statistics_notes_total_title),
                value = stringResource(R.string.statistics_notes_total_value, summary.totalAnnotations),
                supportingText = stringResource(R.string.statistics_notes_total_support, summary.notesWithText),
            )
        }
        if (summary.uniqueAuthorCount > 0 || summary.topSeries != null) {
            item {
                AuthorSeriesCard(
                    uniqueAuthorCount = summary.uniqueAuthorCount,
                    topAuthor = summary.topAuthor,
                    topSeries = summary.topSeries,
                )
            }
        }
        if (summary.genreStats.isNotEmpty()) {
            item {
                GenreBreakdownCard(genres = summary.genreStats)
            }
        }
        if (summary.topLookedUpWords.isNotEmpty()) {
            item {
                TopWordsCard(words = summary.topLookedUpWords, onOpenLearnWords = onOpenLearnWords)
            }
        }
        summary.vocabularyGrowth?.let { growth ->
            item {
                VocabularyGrowthCard(growth = growth)
            }
        }
        if (vocabularyCardCount > 0) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.medium))
                        .clickable(onClick = onReviewVocabulary),
                    shape = RoundedCornerShape(Radii.medium),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Outlined.Style, contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.statistics_review_vocabulary_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (vocabularyDueCount > 0) {
                                    stringResource(R.string.statistics_review_vocabulary_support, vocabularyDueCount, vocabularyCardCount)
                                } else {
                                    stringResource(R.string.statistics_review_vocabulary_caught_up, vocabularyCardCount)
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
        // Due highlights come first; with none due, today's fixed set is still on offer for practice.
        val revisit = highlightsDue.ifEmpty { summary.highlightsToRevisit }
        revisit.firstOrNull()?.let { highlight ->
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.medium))
                        .clickable(onClick = onReviewHighlights),
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
                        Text(
                            text = if (highlightsDue.isNotEmpty()) {
                                stringResource(R.string.statistics_highlight_due_action, highlightsDue.size)
                            } else {
                                stringResource(R.string.statistics_highlight_review_action, revisit.size)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = Spacing.md),
                        )
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
private fun StatisticsOverviewCard(summary: StatisticsSummary) {
    val metrics = listOf(
        summary.booksFinishedThisYear.toString() to stringResource(R.string.statistics_overview_finished),
        (if (summary.totalReadingSeconds > 0) formatSessionDuration(summary.totalReadingSeconds)
        else stringResource(R.string.reader_duration_minutes, 0)) to stringResource(R.string.statistics_overview_total_time),
        summary.currentStreakDays.toString() to stringResource(R.string.statistics_overview_streak),
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(Paddings.card), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.statistics_overview_title), style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                metrics.forEach { (value, label) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
            }
        }
    }
}

/**
 * Everything the app keeps on this device as one total, split by what it's for in a stacked bar and a list; then the
 * book files themselves (count, average, largest, smallest, formats).
 */
@Composable
private fun StorageCard(device: DeviceStorage?, books: LibraryStorage?) {
    val context = LocalContext.current
    // Locale-aware units (MB, GB), the same as the system's storage screens: the total precise, the rest short.
    val size = remember(context) { { bytes: Long -> Formatter.formatShortFileSize(context, bytes) } }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(Paddings.card)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(
                    imageVector = Icons.Outlined.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Sizes.iconSmall),
                )
                Text(text = stringResource(R.string.statistics_storage_title), style = MaterialTheme.typography.titleMedium)
            }
            device?.let { storage ->
                Text(
                    text = Formatter.formatFileSize(context, storage.totalBytes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                Text(
                    text = stringResource(R.string.statistics_storage_on_device),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val colors = storage.categories.map { it.category.color() }
                StackedShareBar(
                    shares = storage.categories.map { it.bytes },
                    colors = colors,
                    modifier = Modifier.padding(top = Spacing.md),
                )
                storage.categories.forEachIndexed { index, category ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        LegendDot(colors[index])
                        Text(
                            text = stringResource(category.category.labelRes()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(text = size(category.bytes), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            books?.let { storage ->
                Text(
                    text = stringResource(R.string.statistics_storage_category_books),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.statistics_storage_support,
                        storage.bookCount,
                        storage.bookCount,
                        size(storage.averageBytes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (storage.bookCount > 1) {
                    BookSizeRow(stringResource(R.string.statistics_storage_largest), storage.largest, size, Modifier.padding(top = Spacing.sm))
                    BookSizeRow(stringResource(R.string.statistics_storage_smallest), storage.smallest, size, Modifier.padding(top = Spacing.sm))
                }
                if (storage.byFormat.size > 1) {
                    FlowRow(
                        modifier = Modifier.padding(top = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        storage.byFormat.forEach { format ->
                            Text(
                                // Format names (EPUB, PDF) are the same in every language.
                                text = format.format.name + " " + size(format.bytes),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.statistics_storage_outside_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
    }
}

/** One bar split into [shares] in [colors]; a sliver stays visible however small a share is. */
@Composable
private fun StackedShareBar(shares: List<Long>, colors: List<Color>, modifier: Modifier = Modifier) {
    val total = shares.sum().coerceAtLeast(1L)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.iconSmall / 2)
            .clip(RoundedCornerShape(Radii.full)),
        horizontalArrangement = Arrangement.spacedBy(Strokes.outline),
    ) {
        shares.forEachIndexed { index, share ->
            Box(
                modifier = Modifier
                    .weight(share.coerceAtLeast(total / MinShareDivisor + 1).toFloat())
                    .fillMaxHeight()
                    .background(colors[index]),
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier = Modifier
            .size(Sizes.iconSmall / 2)
            .clip(RoundedCornerShape(Radii.full))
            .background(color),
    )
}

/** Fixed per category, so a category keeps its colour however the sizes rank. */
@Composable
private fun StorageCategory.color(): Color = when (this) {
    StorageCategory.BOOKS -> MaterialTheme.colorScheme.primary
    StorageCategory.COVERS -> MaterialTheme.colorScheme.tertiary
    StorageCategory.LIBRARY_DATA -> MaterialTheme.colorScheme.secondary
    StorageCategory.DICTIONARY -> MaterialTheme.colorScheme.primary.copy(alpha = SecondaryShareAlpha)
    StorageCategory.FONTS -> MaterialTheme.colorScheme.tertiary.copy(alpha = SecondaryShareAlpha)
    StorageCategory.CACHE -> MaterialTheme.colorScheme.outline
    StorageCategory.OTHER -> MaterialTheme.colorScheme.outlineVariant
}

private fun StorageCategory.labelRes(): Int = when (this) {
    StorageCategory.BOOKS -> R.string.statistics_storage_category_books
    StorageCategory.COVERS -> R.string.statistics_storage_category_covers
    StorageCategory.LIBRARY_DATA -> R.string.statistics_storage_category_library_data
    StorageCategory.DICTIONARY -> R.string.statistics_storage_category_dictionary
    StorageCategory.FONTS -> R.string.statistics_storage_category_fonts
    StorageCategory.CACHE -> R.string.statistics_storage_category_cache
    StorageCategory.OTHER -> R.string.statistics_storage_category_other
}

@Composable
private fun BookSizeRow(label: String, book: BookSize, size: (Long) -> String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = size(book.bytes), style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** No share of a stacked bar is drawn narrower than 1/this of it. */
private const val MinShareDivisor = 50L
private const val SecondaryShareAlpha = 0.55f

@Composable
private fun formatSessionDuration(seconds: Long): String {
    val totalMinutes = (seconds / 60L).toInt().coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.reader_duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.reader_duration_minutes, minutes)
    }
}

@Composable
private fun GoalsCard(summary: StatisticsSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(Paddings.card)) {
            Text(text = stringResource(R.string.statistics_goals_title), style = MaterialTheme.typography.titleMedium)
            if (summary.dailyGoalMinutes > 0) {
                GoalRow(
                    modifier = Modifier.padding(top = Spacing.md),
                    label = stringResource(R.string.statistics_goals_daily_label),
                    valueText = stringResource(
                        R.string.statistics_goals_daily_value,
                        summary.todayReadingMinutes,
                        summary.dailyGoalMinutes,
                    ),
                    fraction = (summary.todayReadingMinutes.toFloat() / summary.dailyGoalMinutes).coerceIn(0f, 1f),
                )
            }
        }
    }
}

@Composable
private fun GoalRow(modifier: Modifier = Modifier, label: String, valueText: String, fraction: Float) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(text = valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        VayanaLinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun ReadingHabitsCard(habits: ReadingHabits) {
    val locale = LocalLocale.current.platformLocale
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(Paddings.card)) {
            Text(text = stringResource(R.string.statistics_habits_title), style = MaterialTheme.typography.titleMedium)
            habits.busiestDayOfWeek?.let { day ->
                HabitFactRow(
                    stringResource(
                        R.string.statistics_habits_busiest_day,
                        day.getDisplayName(TextStyle.FULL, locale),
                    ),
                )
            }
            if (habits.averageSessionMinutes > 0) {
                HabitFactRow(stringResource(R.string.statistics_habits_average_session, habits.averageSessionMinutes))
            }
            habits.averageDaysToFinish?.let { days ->
                HabitFactRow(stringResource(R.string.statistics_habits_days_to_finish, days))
            }
            if (habits.booksFinishedLastYear > 0) {
                HabitFactRow(stringResource(R.string.statistics_habits_last_year, habits.booksFinishedLastYear))
            }
        }
    }
}

@Composable
private fun HabitFactRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.iconSmall / 3)
                .clip(RoundedCornerShape(Radii.full))
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AuthorSeriesCard(uniqueAuthorCount: Int, topAuthor: AuthorStat?, topSeries: SeriesProgress?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(Paddings.card)) {
            Text(text = stringResource(R.string.statistics_authors_title), style = MaterialTheme.typography.titleMedium)
            if (uniqueAuthorCount > 0) {
                HabitFactRow(stringResource(R.string.statistics_authors_unique_count, uniqueAuthorCount))
            }
            topAuthor?.let { author ->
                HabitFactRow(
                    stringResource(
                        R.string.statistics_authors_most_read,
                        author.author,
                        formatSessionDuration(author.totalSeconds),
                    ),
                )
            }
            topSeries?.let { series ->
                HabitFactRow(
                    stringResource(
                        R.string.statistics_authors_series_progress,
                        series.seriesName,
                        series.finishedCount,
                        series.totalCount,
                    ),
                )
            }
        }
    }
}

@Composable
private fun GenreBreakdownCard(genres: List<GenreStat>) {
    val maxSeconds = genres.maxOfOrNull { it.totalSeconds }?.coerceAtLeast(1L) ?: 1L
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(Paddings.card)) {
            Text(text = stringResource(R.string.statistics_genres_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.statistics_genres_support),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            genres.forEach { genre ->
                GenreRow(genre = genre, maxSeconds = maxSeconds, modifier = Modifier.padding(top = Spacing.md))
            }
        }
    }
}

@Composable
private fun GenreRow(genre: GenreStat, maxSeconds: Long, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = genre.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = stringResource(R.string.statistics_genres_row_value, genre.bookCount, formatSessionDuration(genre.totalSeconds)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .padding(top = Spacing.xs)
                .fillMaxWidth()
                .height(Sizes.iconSmall / 4)
                .clip(RoundedCornerShape(Radii.full))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((genre.totalSeconds.toFloat() / maxSeconds).coerceIn(0.04f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Radii.full))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun VocabularyGrowthCard(growth: VocabularyGrowth) {
    val maxCount = growth.weeklyNewCards.maxOrNull()?.coerceAtLeast(1) ?: 1
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(Paddings.card)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(R.string.statistics_vocabulary_growth_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.statistics_vocabulary_growth_mastered, (growth.masteredFraction * 100).roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            VayanaLinearProgressIndicator(
                progress = { growth.masteredFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lg)
                    .height(Sizes.chartHeight / 2),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.Bottom,
            ) {
                growth.weeklyNewCards.forEach { count ->
                    val fraction = if (count <= 0) 0f else (count.toFloat() / maxCount).coerceIn(0.08f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = Radii.extraSmall, topEnd = Radii.extraSmall))
                            .background(
                                if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                    )
                }
            }
            Text(
                text = stringResource(R.string.statistics_vocabulary_growth_support),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun TopWordsCard(words: List<WordLookupStat>, onOpenLearnWords: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.medium))
            .clickable(onClick = onOpenLearnWords),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(Paddings.card)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(Sizes.icon))
                Text(
                    text = stringResource(R.string.statistics_vocabulary_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.statistics_vocabulary_see_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Sizes.iconSmall),
                )
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
private fun ReadingActivityCard(dailyMinutes: List<DailyReadingMinutes>, recentWeekMinutes: Int, streakDays: Int) {
    val today = remember { LocalDate.now() }
    val weeks = remember(dailyMinutes) { dailyMinutes.chunked(7) }
    val monthLabels = remember(weeks) {
        var lastMonth: java.time.Month? = null
        weeks.map { week ->
            val month = week.first().date.month
            (month != lastMonth).also { if (it) lastMonth = month }
                .let { isNewMonth -> if (isNewMonth) month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) else null }
        }
    }
    val scrollState = rememberLazyListState()
    var selectedDay by remember { mutableStateOf<DailyReadingMinutes?>(null) }
    LaunchedEffect(weeks.size) {
        if (weeks.isNotEmpty()) scrollState.scrollToItem(weeks.lastIndex)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .vayanaAnimateContentSize(),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(Paddings.card)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = stringResource(R.string.statistics_activity_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = selectedDay?.let { day ->
                            stringResource(R.string.statistics_activity_day_detail, day.date.asAppDate(), day.minutes)
                        } ?: stringResource(R.string.statistics_activity_support, recentWeekMinutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (streakDays > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Sizes.iconSmall),
                        )
                        Text(
                            text = stringResource(R.string.statistics_streak_value, streakDays),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Row(modifier = Modifier.padding(top = Spacing.lg)) {
                DayOfWeekLabels(days = weeks.firstOrNull()?.map { it.date.dayOfWeek }.orEmpty())
                LazyRow(
                    state = scrollState,
                    modifier = Modifier.padding(start = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(HeatmapCellGap),
                ) {
                    items(weeks.size, key = { weekIndex -> weeks[weekIndex].first().date.toEpochDay() }) { weekIndex ->
                        WeekColumn(
                            week = weeks[weekIndex],
                            monthLabel = monthLabels[weekIndex],
                            today = today,
                            isSelected = { it == selectedDay },
                            onDayClick = { day -> selectedDay = if (day == selectedDay) null else day },
                        )
                    }
                }
            }
            HeatmapLegend(modifier = Modifier.padding(top = Spacing.md).align(Alignment.End))
        }
    }
}

@Composable
private fun DayOfWeekLabels(days: List<java.time.DayOfWeek>) {
    val locale = LocalLocale.current.platformLocale
    Column(verticalArrangement = Arrangement.spacedBy(HeatmapCellGap)) {
        Spacer(modifier = Modifier.height(HeatmapMonthLabelHeight))
        // GitHub labels every other row so the labels don't crowd the small row height; [days] follows the week start.
        days.forEachIndexed { index, day ->
            Box(modifier = Modifier.height(HeatmapCellSize), contentAlignment = Alignment.CenterStart) {
                if (index % 2 == 1) {
                    Text(
                        text = day.getDisplayName(TextStyle.SHORT, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekColumn(
    week: List<DailyReadingMinutes>,
    monthLabel: String?,
    today: LocalDate,
    isSelected: (DailyReadingMinutes) -> Boolean,
    onDayClick: (DailyReadingMinutes) -> Unit,
) {
    Column {
        Box(modifier = Modifier.height(HeatmapMonthLabelHeight)) {
            monthLabel?.let {
                Text(text = it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(HeatmapCellGap)) {
            week.forEach { day ->
                HeatmapCell(
                    day = day,
                    isToday = day.date == today,
                    isSelected = isSelected(day),
                    onClick = { onDayClick(day) },
                )
            }
        }
    }
}

@Composable
private fun HeatmapCell(day: DailyReadingMinutes, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    if (day.isFuture) {
        Spacer(modifier = Modifier.size(HeatmapCellSize))
        return
    }
    val level = activityLevel(day.minutes)
    val baseColor = when (level) {
        0 -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.primary.copy(alpha = HeatmapLevelAlphas[level])
    }
    Box(
        modifier = Modifier
            .size(HeatmapCellSize)
            .clip(RoundedCornerShape(Radii.extraSmall))
            .background(baseColor)
            .then(
                if (isToday || isSelected) {
                    Modifier.border(HeatmapTodayBorderWidth, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(Radii.extraSmall))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun HeatmapLegend(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(text = stringResource(R.string.statistics_activity_legend_less), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HeatmapLevelAlphas.indices.forEach { level ->
            val color = if (level == 0) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary.copy(alpha = HeatmapLevelAlphas[level])
            Box(
                modifier = Modifier
                    .size(HeatmapCellSize)
                    .clip(RoundedCornerShape(Radii.extraSmall))
                    .background(color),
            )
        }
        Text(text = stringResource(R.string.statistics_activity_legend_more), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Fixed minute thresholds rather than relative-to-max buckets, so a single long session one day
 *  doesn't wash out every other day's color by comparison - the scale means the same thing every time. */
private fun activityLevel(minutes: Int): Int = when {
    minutes <= 0 -> 0
    minutes < 15 -> 1
    minutes < 30 -> 2
    minutes < 60 -> 3
    else -> 4
}

private val HeatmapLevelAlphas = listOf(0f, 0.3f, 0.5f, 0.75f, 1f)
private val HeatmapCellSize = Sizes.heatmapCell
private val HeatmapCellGap = Paddings.heatmapCellGap
private val HeatmapMonthLabelHeight = Sizes.heatmapMonthLabelHeight
private val HeatmapTodayBorderWidth = Strokes.emphasis

@Composable
private fun ReadingPaceCard(pace: ReadingPaceEstimate) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(Paddings.card),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.AutoMirrored.Outlined.TrendingUp, contentDescription = null)
            Column {
                Text(text = stringResource(R.string.statistics_pace_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.statistics_pace_body, pace.bookTitle, pace.estimatedDaysRemaining),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
