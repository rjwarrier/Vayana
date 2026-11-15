package com.vayana.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.vayana.app.MainActivity
import com.vayana.app.R
import com.vayana.core.common.ApplicationScope
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.database.model.ReadingSession
import com.vayana.core.database.model.isFinished
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.ReadingSessionRepository
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.filesystem.StorageRoots
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vayana.core.resources.R as Res

/** The overview widget: today's reading, the current streak, books finished, the book being read, and a 7-day chart. */
@AndroidEntryPoint
class OverviewWidget : AppWidgetProvider() {
    @Inject lateinit var updater: OverviewWidgetUpdater

    // Added, resized or restored after a reboot: the app may not be running, so draw from the database now.
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updater.start()
        val pending = goAsync()
        updater.refresh { pending.finish() }
    }

    override fun onEnabled(context: Context) = updater.start()

    override fun onDisabled(context: Context) = updater.stop()

    // Resized: the chart is drawn for the widget's real size, so draw it again.
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        val pending = goAsync()
        updater.refresh { pending.finish() }
    }
}

private data class OverviewTheme(val appearance: WidgetAppearance, val colors: WidgetTheme)

private fun overviewTheme(settings: SettingsSnapshot) = OverviewTheme(settings.widgetAppearance(), settings.widgetTheme())

private data class OverviewState(
    val book: WidgetBook?,
    val week: ReadingWeek,
    val streakDays: Int,
    val finishedBooks: Int,
    val appearance: WidgetAppearance,
    val colors: WidgetTheme,
)

/**
 * Redraws the overview widget when the book being read changes, a session is recorded or synced in, the widget style
 * changes, and the day turns over.
 */
@Singleton
class OverviewWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val readingSessionRepository: ReadingSessionRepository,
    private val storageRoots: StorageRoots,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
    @param:ApplicationScope private val scope: CoroutineScope,
) : VayanaWidget {
    override val provider = OverviewWidget::class.java
    private var observer: Job? = null

    // The last cover drawn: a style-only redraw needn't decode the same cover again.
    private var cover: Pair<String, Bitmap?>? = null

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Today, emitted again just after each midnight. */
    private val today: Flow<LocalDate> = flow {
        while (true) {
            val now = LocalDate.now(zone)
            emit(now)
            val nextMidnight = now.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            delay(nextMidnight - System.currentTimeMillis() + MidnightSlackMillis)
        }
    }.distinctUntilChanged()

    // Wide enough to carry a long streak and a month-long chart setting; sessions outside it would only extend a
    // streak already long past what the stat has room to show.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val windowSessions: Flow<List<ReadingSession>> = today.flatMapLatest { day ->
        readingSessionRepository.observeSince(windowStart(day))
    }

    private val finishedBooks: Flow<Int> = bookRepository.observeAll().map { books -> books.count { it.isFinished() } }

    private val state: Flow<OverviewState> = combine(
        bookRepository.observeContinueReading().map { it?.toWidgetBook() },
        windowSessions,
        today,
        finishedBooks,
        settingsRepository.snapshot.map(::overviewTheme),
    ) { book, sessions, day, finished, theme ->
        OverviewState(
            book = book,
            week = readingWeek(sessions, day, zone, theme.appearance.chartDays),
            streakDays = currentStreakDays(sessions, day, zone),
            finishedBooks = finished,
            appearance = theme.appearance,
            colors = theme.colors,
        )
    }.distinctUntilChanged()

    @Synchronized
    fun start() {
        if (observer?.isActive == true || !hasWidgets()) return
        observer = scope.launch { state.collect { render(it) } }
    }

    @Synchronized
    fun stop() {
        observer?.cancel()
        observer = null
        cover = null
    }

    fun refresh(onDone: () -> Unit) {
        scope.launch {
            try {
                render(state.first())
            } finally {
                onDone()
            }
        }
    }

    // The configure screen redraws its preview on every slider step: the window is read once and sliced for each
    // Days value tried, kept only briefly so a later visit sees fresh reading.
    @Volatile private var previewWindow: Pair<Long, List<ReadingSession>>? = null

    override suspend fun preview(appearance: WidgetAppearance): RemoteViews = withContext(dispatchers.io) {
        val theme = settingsRepository.snapshot.map(::overviewTheme).first()
        val today = LocalDate.now(zone)
        val now = SystemClock.elapsedRealtime()
        val sessions = previewWindow?.takeIf { (at, _) -> now - at < PreviewCacheMillis }?.second
            ?: readingSessionRepository.observeSince(windowStart(today)).first().also { previewWindow = now to it }
        val book = bookRepository.observeContinueReading().first()?.toWidgetBook()
        val finished = bookRepository.observeAll().first().count { it.isFinished() }
        val state = OverviewState(
            book = book,
            week = readingWeek(sessions, today, zone, appearance.chartDays),
            streakDays = currentStreakDays(sessions, today, zone),
            finishedBooks = finished,
            appearance = appearance,
            colors = theme.colors,
        )
        views(state, coverFor(state.book), OverviewLayout.forSize(DefaultWidthDp, DefaultHeightDp), state.colors.palette(context))
    }

    private fun windowStart(day: LocalDate): Long = day.minusDays((StreakWindowDays - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun coverFor(book: WidgetBook?): Bitmap? = book?.coverPath?.let { path ->
        cover?.takeIf { it.first == path }?.second ?: loadCoverBitmap(storageRoots, path, CoverHeightPx).also { cover = path to it }
    }

    private suspend fun render(state: OverviewState) = withContext(dispatchers.io) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, OverviewWidget::class.java))
        if (ids.isEmpty()) return@withContext
        val palette = state.colors.palette(context)
        val cover = coverFor(state.book)
        ids.forEach { id ->
            val options = manager.getAppWidgetOptions(id)
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val widthKey = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
            val heightKey = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
            val width = options.getInt(widthKey).takeIf { it > 0 } ?: DefaultWidthDp
            val height = options.getInt(heightKey).takeIf { it > 0 } ?: DefaultHeightDp
            manager.updateAppWidget(id, views(state, cover, OverviewLayout.forSize(width, height), palette))
        }
    }

    private fun hasWidgets(): Boolean = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, OverviewWidget::class.java))
        .isNotEmpty()

    private fun views(state: OverviewState, cover: Bitmap?, layout: OverviewLayout, palette: WidgetPalette): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_overview).apply {
            // Bitmaps are drawn at most at 2x; the image views scale them up on denser screens.
            val density = minOf(context.resources.displayMetrics.density, MaxBitmapDensity)
            val scaledDensity = density * context.resources.configuration.fontScale
            applySurface(R.id.overview_background, R.id.overview_border, state.appearance, palette)
            setOnClickPendingIntent(android.R.id.background, openStatistics())
            setViewPadding(
                R.id.overview_content,
                dpToPx(layout.paddingDp), dpToPx(layout.paddingDp), dpToPx(layout.paddingDp), dpToPx(layout.paddingDp),
            )
            applyPalette(palette)

            val book = state.book
            setViewVisibility(R.id.overview_book, if (book != null && layout.showBookRow) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.overview_empty, if (book == null && layout.showBookRow) View.VISIBLE else View.GONE)
            if (book != null && layout.showBookRow) {
                setTextViewText(R.id.overview_title, book.title)
                setTextViewText(R.id.overview_author, book.author.orEmpty())
                setViewVisibility(
                    R.id.overview_author,
                    if (layout.showAuthor && !book.author.isNullOrBlank()) View.VISIBLE else View.GONE,
                )
                setTextViewText(R.id.overview_percent, context.getString(Res.string.library_progress_value, book.percent))
                setOnClickPendingIntent(R.id.overview_book, openBook(book.id))
                if (cover != null) {
                    setImageViewBitmap(R.id.overview_cover, cover)
                    setViewPadding(R.id.overview_cover, 0, 0, 0, 0)
                } else {
                    setImageViewResource(R.id.overview_cover, R.drawable.ic_widget_book)
                    val inset = dpToPx(CoverPlaceholderInsetDp)
                    setViewPadding(R.id.overview_cover, inset, inset, inset, inset)
                }
            }

            setTextViewText(R.id.overview_today_value, duration(state.week.todayMinutes))
            setTextViewText(R.id.overview_streak_value, state.streakDays.toString())
            setTextViewText(R.id.overview_finished_value, state.finishedBooks.toString())
            for (label in intArrayOf(R.id.overview_today_label, R.id.overview_streak_label, R.id.overview_finished_label)) {
                setViewVisibility(label, if (layout.showStatLabels) View.VISIBLE else View.GONE)
            }

            val chartWidth = ((layout.widthDp - layout.paddingDp * 2) * density).roundToInt().coerceAtLeast(MinChartPx)
            val chartHeight = (layout.chartHeightDp * density).roundToInt().coerceAtLeast(MinChartPx)
            val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
            // A week reads by weekday; longer spans repeat the letters, so they read by date.
            val byDate = state.week.days.size > ReadingWeek.MinDays
            val dayNames = state.week.days.map { if (byDate) "${it.dayOfMonth}" else it.dayOfWeek.getDisplayName(TextStyle.NARROW, locale) }
            val chart = drawReadingTimeChart(
                week = state.week,
                labels = ReadingChartLabels(dayNames),
                colors = palette.chartColors(),
                widthPx = chartWidth,
                heightPx = chartHeight,
                density = density,
                scaledDensity = scaledDensity,
                style = state.appearance.chartStyle,
                showAxisLabels = state.appearance.chartAxisLabels,
                gridOpacity = state.appearance.chartGridOpacity,
            )
            setImageViewBitmap(R.id.overview_chart, chart)
            setContentDescription(R.id.overview_chart, chartDescription(state.week, locale))
        }

    private fun RemoteViews.applyPalette(palette: WidgetPalette) {
        setTextColor(R.id.overview_title, palette.onSurface)
        setTextColor(R.id.overview_author, palette.onSurfaceVariant)
        setTextColor(R.id.overview_percent, palette.onPrimaryContainer)
        setTextColor(R.id.overview_empty_text, palette.onSurfaceVariant)
        tintBackground(R.id.overview_percent, palette.primaryContainer)
        tintBackground(R.id.overview_cover, palette.secondaryContainer)
        tintBackground(R.id.overview_empty_icon, palette.primaryContainer)
        for (value in intArrayOf(R.id.overview_today_value, R.id.overview_streak_value, R.id.overview_finished_value)) {
            setTextColor(value, palette.onSurface)
        }
        for (label in intArrayOf(R.id.overview_today_label, R.id.overview_streak_label, R.id.overview_finished_label)) {
            setTextColor(label, palette.onSurfaceVariant)
        }
        setInt(R.id.overview_divider_1, "setBackgroundColor", palette.outlineVariant)
        setInt(R.id.overview_divider_2, "setBackgroundColor", palette.outlineVariant)
    }

    private fun chartDescription(week: ReadingWeek, locale: Locale): String {
        val days = week.days.mapIndexed { index, day ->
            val name = if (index == week.days.lastIndex) {
                context.getString(Res.string.widget_reading_today)
            } else {
                day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            }
            "$name ${duration(week.minutesByDay[index])}"
        }
        val average = if (week.hasHistory) duration(week.averageMinutes) else context.getString(Res.string.widget_reading_avg_none)
        return context.getString(Res.string.widget_reading_chart_description, days.joinToString(", "), average)
    }

    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).roundToInt()

    private fun duration(minutes: Int): String = when {
        minutes < MinutesPerHour -> context.getString(Res.string.reader_duration_minutes, minutes)
        minutes % MinutesPerHour == 0 -> context.getString(Res.string.widget_reading_duration_hours, minutes / MinutesPerHour)
        else -> context.getString(Res.string.reader_duration_hours_minutes, minutes / MinutesPerHour, minutes % MinutesPerHour)
    }

    /** Opens the book being read. */
    private fun openBook(bookId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ContinueReadingWidgetUpdater.ActionOpenBook)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(ContinueReadingWidgetUpdater.ExtraBookId, bookId)
        return PendingIntent.getActivity(context, OpenBookRequestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openStatistics(): PendingIntent = PendingIntent.getActivity(
        context,
        OpenStatisticsRequestCode,
        Intent(context, MainActivity::class.java)
            .setAction(AppShortcuts.ActionStatistics)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val DefaultWidthDp = 320
        const val DefaultHeightDp = 220
        const val MinChartPx = 32
        const val MaxBitmapDensity = 2f
        const val PreviewCacheMillis = 30_000L
        const val MinutesPerHour = 60
        const val MidnightSlackMillis = 1_000L
        // A year-ish of lookback: long enough that a streak this widget can't show anyway (its number has no more
        // room) is never the reason it looks broken.
        const val StreakWindowDays = 371
        const val CoverHeightPx = 140
        const val CoverPlaceholderInsetDp = 10
        const val OpenBookRequestCode = 30
        const val OpenStatisticsRequestCode = 31
    }
}

/** The run of consecutive days with reading up to [today], counting an unfinished today as still live. */
private fun currentStreakDays(sessions: List<ReadingSession>, today: LocalDate, zone: ZoneId): Int {
    val readDays = sessions.asSequence()
        .filter { it.durationSeconds >= StreakMinCountedSessionSeconds }
        .map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        .toHashSet()
    var streak = 0
    var probe = if (today in readDays) today else today.minusDays(1)
    while (probe in readDays) {
        streak++
        probe = probe.minusDays(1)
    }
    return streak
}

private const val StreakMinCountedSessionSeconds = 60L

/**
 * What the overview widget shows at a given size: very short sizes drop the book row entirely, narrow ones drop the
 * author line and the stat labels, and the chart always takes whatever height is left.
 */
internal data class OverviewLayout(
    val widthDp: Int,
    val paddingDp: Int,
    val showBookRow: Boolean,
    val showAuthor: Boolean,
    val showStatLabels: Boolean,
    val chartHeightDp: Int,
) {
    companion object {
        fun forSize(widthDp: Int, heightDp: Int): OverviewLayout {
            val compact = widthDp < CompactWidthDp
            val padding = if (compact) 12 else 16
            val showBookRow = heightDp >= MinHeightForBookRowDp
            val rowsDp = (if (showBookRow) BookRowDp else 0) + StatsRowDp
            val gaps = (if (showBookRow) 1 else 0) + 1
            val chrome = padding * 2 + rowsDp + gaps * GapDp
            return OverviewLayout(
                widthDp = widthDp,
                paddingDp = padding,
                showBookRow = showBookRow,
                showAuthor = !compact && heightDp >= MinHeightForBookRowDp + AuthorExtraHeightDp,
                showStatLabels = widthDp >= StatLabelsMinWidthDp,
                chartHeightDp = (heightDp - chrome).coerceAtLeast(MinChartHeightDp),
            )
        }

        private const val CompactWidthDp = 260
        private const val StatLabelsMinWidthDp = 220
        private const val MinHeightForBookRowDp = 150
        private const val AuthorExtraHeightDp = 20
        private const val BookRowDp = 60
        private const val StatsRowDp = 44
        private const val GapDp = 10
        private const val MinChartHeightDp = 40
    }
}
