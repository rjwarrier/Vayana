package com.vayana.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.vayana.app.MainActivity
import com.vayana.app.R
import com.vayana.core.common.ApplicationScope
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.ReadingSession
import com.vayana.core.database.model.isFinished
import com.vayana.core.database.model.shownReadingPercent
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.ReadingSessionRepository
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.datastore.settings.SettingsSnapshot
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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

/**
 * The overview widget: today's reading and streak, the book being read as a position meter, the yearly book-goal
 * ring, and the library's counts. One size, one shape by [OverviewTier]: a short strip just shows today's chart, a
 * narrow square swaps in a compact ring, and only the two widest, tallest shapes show the full dashboard.
 */
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

    // Resized: the chart, meter and ring are drawn for the widget's real size, so draw them again.
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        val pending = goAsync()
        updater.refresh { pending.finish() }
    }
}

private data class OverviewTheme(
    val appearance: WidgetAppearance,
    val colors: WidgetTheme,
    val yearGoal: Int,
    val streakAtRiskHour: Int,
)

private fun overviewTheme(settings: SettingsSnapshot) =
    OverviewTheme(settings.widgetAppearance(), settings.widgetTheme(), settings.yearlyBooksGoal, settings.widgetStreakAtRiskHour)

/** The book being read, and how long is left in it at the reader's own pace so far (null without enough history). */
private data class BookProgress(val book: WidgetBook, val timeLeftMinutes: Int?)

private const val MinPercentForPaceEstimate = 0.01f
private const val SecondsPerMinute = 60L

private fun Book.toBookProgress(): BookProgress {
    val percent = readingPercent.coerceIn(0f, 1f)
    val timeLeft = if (percent > MinPercentForPaceEstimate && totalReadingSeconds > 0) {
        val remainingSeconds = (totalReadingSeconds / percent) * (1f - percent)
        (remainingSeconds / SecondsPerMinute).roundToInt()
    } else {
        null
    }
    return BookProgress(toWidgetBook(), timeLeft)
}

private data class BookStats(val total: Int, val reading: Int, val unread: Int, val finishedThisYear: Int, val readNext: WidgetBook?)

private data class OverviewState(
    val book: WidgetBook?,
    val timeLeftMinutes: Int?,
    val week: ReadingWeek,
    val weekMinutes: Int,
    val streakDays: Int,
    val streakAtRisk: Boolean,
    val totalBooks: Int,
    val readingBooks: Int,
    val unreadBooks: Int,
    val yearGoal: Int,
    val yearFinished: Int,
    val yearDue: Int,
    val readNext: WidgetBook?,
    val appearance: WidgetAppearance,
    val colors: WidgetTheme,
)

/**
 * Redraws the overview widget when the book being read changes, a session is recorded or synced in, a book is
 * added/finished/queued, the widget style changes, and the day turns over.
 */
@Singleton
class OverviewWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val readingSessionRepository: ReadingSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
    @param:ApplicationScope private val scope: CoroutineScope,
) : VayanaWidget {
    override val provider = OverviewWidget::class.java
    private var observer: Job? = null

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
    private val windowSessions = today.flatMapLatest { day -> readingSessionRepository.observeSince(windowStart(day)) }

    private val bookProgress: Flow<BookProgress?> = bookRepository.observeContinueReading().map { it?.toBookProgress() }

    private val bookStats: Flow<BookStats> = combine(bookRepository.observeAll(), today) { books, day -> bookStats(books, day) }

    private fun bookStats(books: List<Book>, day: LocalDate): BookStats {
        val finished = books.count { it.isFinished() }
        val reading = books.count { it.shownReadingPercent > 0f && !it.isFinished() }
        val finishedThisYear = books.count { book ->
            val finishedAt = book.finishedReadingAt
            finishedAt != null && book.isFinished() && Instant.ofEpochMilli(finishedAt).atZone(zone).year == day.year
        }
        val readNext = books.filter { it.readNextAddedAt != null }
            .sortedWith(compareByDescending<Book> { it.readNextPinned }.thenByDescending { it.readNextAddedAt })
            .firstOrNull()?.toWidgetBook()
        return BookStats(books.size, reading, books.size - reading - finished, finishedThisYear, readNext)
    }

    private val state: Flow<OverviewState> = combine(
        bookProgress,
        windowSessions,
        today,
        bookStats,
        settingsRepository.snapshot.map(::overviewTheme),
    ) { progress, sessions, day, stats, theme ->
        toState(progress, sessions, day, stats, theme)
    }.distinctUntilChanged()

    private fun toState(progress: BookProgress?, sessions: List<ReadingSession>, day: LocalDate, stats: BookStats, theme: OverviewTheme): OverviewState {
        val week = readingWeek(sessions, day, zone, theme.appearance.chartDays)
        val weekMinutes = week.minutesByDay.takeLast(ReadingWeek.AveragedDays).sum()
        val daysInYear = if (day.isLeapYear) DaysInLeapYear else DaysInYear
        val yearDue = if (theme.yearGoal > 0) ((theme.yearGoal.toLong() * day.dayOfYear) / daysInYear).toInt() else 0
        val streakDays = currentStreakDays(sessions, day, zone)
        val streakAtRisk = streakDays > 0 && week.todayMinutes == 0 &&
            LocalTime.now(zone).hour >= theme.streakAtRiskHour
        return OverviewState(
            book = progress?.book,
            timeLeftMinutes = progress?.timeLeftMinutes,
            week = week,
            weekMinutes = weekMinutes,
            streakDays = streakDays,
            streakAtRisk = streakAtRisk,
            totalBooks = stats.total,
            readingBooks = stats.reading,
            unreadBooks = stats.unread,
            yearGoal = theme.yearGoal,
            yearFinished = stats.finishedThisYear,
            yearDue = yearDue,
            readNext = stats.readNext,
            appearance = theme.appearance,
            colors = theme.colors,
        )
    }

    @Synchronized
    fun start() {
        if (observer?.isActive == true || !hasWidgets()) return
        observer = scope.launch { state.collect { render(it) } }
    }

    @Synchronized
    fun stop() {
        observer?.cancel()
        observer = null
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
        val settings = settingsRepository.snapshot.first()
        val theme = OverviewTheme(appearance, settings.widgetTheme(), settings.yearlyBooksGoal, settings.widgetStreakAtRiskHour)
        val today = LocalDate.now(zone)
        val now = SystemClock.elapsedRealtime()
        val sessions = previewWindow?.takeIf { (at, _) -> now - at < PreviewCacheMillis }?.second
            ?: readingSessionRepository.observeSince(windowStart(today)).first().also { previewWindow = now to it }
        val progress = bookRepository.observeContinueReading().first()?.toBookProgress()
        val stats = bookStats(bookRepository.observeAll().first(), today)
        val state = toState(progress, sessions, today, stats, theme)
        views(state, OverviewLayout.forSize(DefaultWidthDp, DefaultHeightDp), state.colors.palette(context))
    }

    private fun windowStart(day: LocalDate): Long = day.minusDays((StreakWindowDays - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

    private suspend fun render(state: OverviewState) = withContext(dispatchers.io) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, OverviewWidget::class.java))
        if (ids.isEmpty()) return@withContext
        val palette = state.colors.palette(context)
        ids.forEach { id ->
            val options = manager.getAppWidgetOptions(id)
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val widthKey = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
            val heightKey = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
            val width = options.getInt(widthKey).takeIf { it > 0 } ?: DefaultWidthDp
            val height = options.getInt(heightKey).takeIf { it > 0 } ?: DefaultHeightDp
            manager.updateAppWidget(id, views(state, OverviewLayout.forSize(width, height), palette))
        }
    }

    private fun hasWidgets(): Boolean = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, OverviewWidget::class.java))
        .isNotEmpty()

    private fun views(state: OverviewState, layout: OverviewLayout, palette: WidgetPalette): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_overview).apply {
            // Bitmaps are drawn at most at 2x; the image views scale them up on denser screens.
            val density = minOf(context.resources.displayMetrics.density, MaxBitmapDensity)
            val scaledDensity = density * context.resources.configuration.fontScale
            val graphicsColors = OverviewGraphicsColors(filled = palette.primary, due = palette.tertiary, track = palette.outlineVariant)

            applySurface(R.id.overview_background, R.id.overview_border, state.appearance, palette)
            setOnClickPendingIntent(android.R.id.background, openStatistics())
            setViewPadding(
                R.id.overview_content,
                dpToPx(layout.paddingDp), dpToPx(layout.paddingDp), dpToPx(layout.paddingDp), dpToPx(layout.paddingDp),
            )
            applyPalette(palette)

            val todayText = duration(state.week.todayMinutes)
            setImageViewBitmap(R.id.overview_today_value, drawDotText(todayText, dpToPx(HeroDotHeightDp), palette.onSurface))
            setContentDescription(R.id.overview_today_value, todayText)
            applyPills(state, layout, palette)

            // The dashboard tier also fits a chart below the ring row, in whatever height resizing the ring row to
            // its own fixed height (API 31+ only) leaves over; on older Android, or a dashboard too short for that,
            // it goes without one, same as the compact tier always does.
            val dashboardChartHeightDp = layout.dashboardChartHeightDp.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
            val showChart = layout.tier == OverviewTier.STRIP || layout.tier == OverviewTier.CHART ||
                (layout.tier == OverviewTier.DASHBOARD && dashboardChartHeightDp != null)
            setViewVisibility(R.id.overview_chart, if (showChart) View.VISIBLE else View.GONE)
            if (showChart) {
                val chartHeightDp = if (layout.tier == OverviewTier.DASHBOARD) dashboardChartHeightDp!! else layout.chartHeightDp
                applyChart(state, layout.copy(chartHeightDp = chartHeightDp), palette, density, scaledDensity)
            }

            setViewVisibility(R.id.overview_book_row, if (layout.tier == OverviewTier.DASHBOARD) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.overview_ring_row, if (layout.tier == OverviewTier.DASHBOARD) View.VISIBLE else View.GONE)
            if (layout.tier == OverviewTier.DASHBOARD) {
                applyBookRow(state, layout, density, graphicsColors)
                applyRing(R.id.overview_ring_image, R.id.overview_ring_value, R.id.overview_ring_of, RingSizeDp, state, graphicsColors, palette, density)
                applyLibrary(state)
                if (dashboardChartHeightDp != null) {
                    setViewLayoutHeight(R.id.overview_ring_row, OverviewLayout.RingRowFixedDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                }
            }

            setViewVisibility(R.id.overview_compact_row, if (layout.tier == OverviewTier.COMPACT) View.VISIBLE else View.GONE)
            if (layout.tier == OverviewTier.COMPACT) {
                applyRing(
                    R.id.overview_compact_ring_image, R.id.overview_compact_ring_value, R.id.overview_compact_ring_of,
                    CompactRingSizeDp, state, graphicsColors, palette, density,
                )
                applyCompactStatus(state)
            }
        }

    private fun RemoteViews.applyPills(state: OverviewState, layout: OverviewLayout, palette: WidgetPalette) {
        val streakLabel = if (state.streakAtRisk) context.getString(Res.string.widget_overview_streak_at_risk) else streakLabel()
        setPill(R.id.overview_pill1, R.id.overview_pill1_value, R.id.overview_pill1_label, "${state.streakDays}D", streakLabel)
        if (state.streakAtRisk) {
            tintBackground(R.id.overview_pill1, palette.errorContainer)
            setTextColor(R.id.overview_pill1_value, palette.onErrorContainer)
            setTextColor(R.id.overview_pill1_label, palette.onErrorContainer)
        }
        val yearText = "${state.yearFinished}/${state.yearGoal}"
        when (layout.tier) {
            OverviewTier.STRIP -> {
                setViewVisibility(R.id.overview_pill2, View.GONE)
                setPill(R.id.overview_pill3, R.id.overview_pill3_value, R.id.overview_pill3_label, yearText, context.getString(Res.string.widget_overview_year_label))
                setViewVisibility(R.id.overview_pill4, View.GONE)
            }
            OverviewTier.COMPACT -> {
                setViewVisibility(R.id.overview_pill2, View.GONE)
                setViewVisibility(R.id.overview_pill3, View.GONE)
                setViewVisibility(R.id.overview_pill4, View.GONE)
            }
            OverviewTier.CHART -> {
                setPill(R.id.overview_pill2, R.id.overview_pill2_value, R.id.overview_pill2_label, duration(state.weekMinutes), context.getString(Res.string.widget_overview_week_label))
                setPill(R.id.overview_pill3, R.id.overview_pill3_value, R.id.overview_pill3_label, yearText, context.getString(Res.string.widget_overview_year_label))
                setPill(R.id.overview_pill4, R.id.overview_pill4_value, R.id.overview_pill4_label, state.totalBooks.toString(), context.getString(Res.string.statistics_library_total_title))
            }
            OverviewTier.DASHBOARD -> {
                setPill(R.id.overview_pill2, R.id.overview_pill2_value, R.id.overview_pill2_label, duration(state.weekMinutes), context.getString(Res.string.widget_overview_week_label))
                setPill(R.id.overview_pill3, R.id.overview_pill3_value, R.id.overview_pill3_label, yearText, context.getString(Res.string.widget_overview_year_label))
                setPill(R.id.overview_pill4, R.id.overview_pill4_value, R.id.overview_pill4_label, paceText(state), context.getString(Res.string.widget_overview_pace_label))
            }
        }
    }

    private fun RemoteViews.setPill(containerId: Int, valueId: Int, labelId: Int, value: String, label: String) {
        setViewVisibility(containerId, View.VISIBLE)
        setTextViewText(valueId, value)
        setTextViewText(labelId, label)
    }

    private fun streakLabel(): String = context.getString(Res.string.statistics_streak_title)

    /** Ahead, even, or behind the pace that reaches the yearly goal by year's end, as a signed book count. */
    private fun paceText(state: OverviewState): String {
        val diff = state.yearFinished - state.yearDue
        return when {
            diff > 0 -> "+$diff"
            diff < 0 -> diff.toString()
            else -> "0"
        }
    }

    private fun RemoteViews.applyChart(state: OverviewState, layout: OverviewLayout, palette: WidgetPalette, density: Float, scaledDensity: Float) {
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
            averageDashed = true,
        )
        setImageViewBitmap(R.id.overview_chart, chart)
        setContentDescription(R.id.overview_chart, chartDescription(state.week, locale))
    }

    private fun RemoteViews.applyBookRow(state: OverviewState, layout: OverviewLayout, density: Float, colors: OverviewGraphicsColors) {
        val book = state.book
        setViewVisibility(R.id.overview_book_open, if (book != null) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.overview_book_empty, if (book == null) View.VISIBLE else View.GONE)
        if (book != null) {
            setTextViewText(R.id.overview_title, book.title)
            setTextViewText(R.id.overview_percent, context.getString(Res.string.library_progress_value, book.percent))
            setTextViewText(R.id.overview_time_left, state.timeLeftMinutes?.let { context.getString(Res.string.widget_overview_time_left, duration(it)) }.orEmpty())
            setViewVisibility(R.id.overview_time_left, if (state.timeLeftMinutes != null) View.VISIBLE else View.GONE)
            setOnClickPendingIntent(R.id.overview_book_open, openBook(book.id))
            val meterWidth = ((layout.widthDp - layout.paddingDp * 2) * density).roundToInt().coerceAtLeast(1)
            val meterHeight = (MeterHeightDp * density).roundToInt().coerceAtLeast(1)
            setImageViewBitmap(R.id.overview_meter, drawBookMeter(meterWidth, meterHeight, BookMeterSegments, book.percent / PercentScale, colors, density))
        } else {
            val readNext = state.readNext
            setViewVisibility(R.id.overview_read_next, if (readNext != null) View.VISIBLE else View.GONE)
            if (readNext != null) {
                setTextViewText(R.id.overview_read_next, "$ReadNextPrefix${readNext.title}")
                setOnClickPendingIntent(R.id.overview_read_next, openBook(readNext.id))
            }
        }
    }

    private fun RemoteViews.applyCompactStatus(state: OverviewState) {
        val book = state.book
        val open = book != null
        setViewVisibility(R.id.overview_compact_title, if (open) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.overview_compact_percent, if (open) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.overview_compact_empty_text, if (open) View.GONE else View.VISIBLE)
        setViewVisibility(R.id.overview_compact_read_next, if (!open && state.readNext != null) View.VISIBLE else View.GONE)
        if (book != null) {
            setTextViewText(R.id.overview_compact_title, book.title)
            setTextViewText(R.id.overview_compact_percent, context.getString(Res.string.library_progress_value, book.percent))
            setOnClickPendingIntent(R.id.overview_compact_status, openBook(book.id))
        } else {
            state.readNext?.let { readNext ->
                setTextViewText(R.id.overview_compact_read_next, "$ReadNextPrefix${readNext.title}")
                setOnClickPendingIntent(R.id.overview_compact_read_next, openBook(readNext.id))
            }
        }
    }

    private fun RemoteViews.applyRing(
        imageId: Int,
        valueId: Int,
        ofId: Int,
        sizeDp: Int,
        state: OverviewState,
        colors: OverviewGraphicsColors,
        palette: WidgetPalette,
        density: Float,
    ) {
        val sizePx = (sizeDp * density).roundToInt()
        val bitmap = drawYearRing(sizePx, state.yearGoal.coerceAtLeast(1), state.yearFinished, state.yearDue, colors, density)
        setImageViewBitmap(imageId, bitmap)
        setImageViewBitmap(valueId, drawDotText(state.yearFinished.toString(), dpToPx(RingDotHeightDp), palette.onSurface))
        setTextViewText(ofId, context.getString(Res.string.widget_overview_ring_of, state.yearGoal))
    }

    private fun RemoteViews.applyLibrary(state: OverviewState) {
        setTextViewText(R.id.overview_lib_books_value, state.totalBooks.toString())
        setTextViewText(R.id.overview_lib_reading_value, state.readingBooks.toString())
        setTextViewText(R.id.overview_lib_unread_value, state.unreadBooks.toString())
    }

    private fun RemoteViews.applyPalette(palette: WidgetPalette) {
        for (pill in listOf(
            R.id.overview_pill1_value to R.id.overview_pill1_label,
            R.id.overview_pill2_value to R.id.overview_pill2_label,
            R.id.overview_pill3_value to R.id.overview_pill3_label,
            R.id.overview_pill4_value to R.id.overview_pill4_label,
        )) {
            setTextColor(pill.first, palette.onPrimaryContainer)
            setTextColor(pill.second, palette.onPrimaryContainer)
        }
        for (chip in intArrayOf(R.id.overview_pill1, R.id.overview_pill2, R.id.overview_pill3, R.id.overview_pill4)) {
            tintBackground(chip, palette.primaryContainer)
        }
        setTextColor(R.id.overview_title, palette.onSurface)
        setTextColor(R.id.overview_percent, palette.tertiary)
        setTextColor(R.id.overview_time_left, palette.onSurfaceVariant)
        setTextColor(R.id.overview_book_empty_text, palette.onSurfaceVariant)
        setTextColor(R.id.overview_read_next, palette.onPrimaryContainer)
        tintBackground(R.id.overview_read_next, palette.primaryContainer)
        setTextColor(R.id.overview_ring_of, palette.onSurfaceVariant)
        setTextColor(R.id.overview_lib_books_label, palette.onSurfaceVariant)
        setTextColor(R.id.overview_lib_reading_label, palette.onSurfaceVariant)
        setTextColor(R.id.overview_lib_unread_label, palette.onSurfaceVariant)
        setTextColor(R.id.overview_lib_books_value, palette.onSurface)
        setTextColor(R.id.overview_lib_reading_value, palette.onSurface)
        setTextColor(R.id.overview_lib_unread_value, palette.onSurface)
        setTextColor(R.id.overview_compact_ring_of, palette.onSurfaceVariant)
        setTextColor(R.id.overview_compact_title, palette.onSurface)
        setTextColor(R.id.overview_compact_percent, palette.onSurfaceVariant)
        setTextColor(R.id.overview_compact_empty_text, palette.onSurfaceVariant)
        setTextColor(R.id.overview_compact_read_next, palette.onPrimaryContainer)
        tintBackground(R.id.overview_compact_read_next, palette.primaryContainer)
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

    /** Opens the book being read, or the tapped Read Next suggestion. */
    private fun openBook(bookId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ContinueReadingWidgetUpdater.ActionOpenBook)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(ContinueReadingWidgetUpdater.ExtraBookId, bookId)
        return PendingIntent.getActivity(
            context, OpenBookRequestCode + (bookId % OpenBookRequestCodeSpread).toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
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
        const val DaysInYear = 365
        const val DaysInLeapYear = 366
        const val RingSizeDp = 64
        const val CompactRingSizeDp = 56
        const val HeroDotHeightDp = 30
        const val RingDotHeightDp = 16
        const val MeterHeightDp = 10
        const val BookMeterSegments = 21
        const val PercentScale = 100f
        const val ReadNextPrefix = "▸ "
        // A year-ish of lookback: long enough that a streak this widget can't show anyway (its number has no more
        // room) is never the reason it looks broken.
        const val StreakWindowDays = 371
        const val OpenBookRequestCode = 30
        const val OpenBookRequestCodeSpread = 1000L
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

internal enum class OverviewTier { STRIP, COMPACT, CHART, DASHBOARD }

/**
 * What the overview widget shows at a given size: a short strip only has room for today's chart, a narrow square
 * swaps in the compact ring, and only a widget both wide and tall enough gets the full dashboard (book meter, year
 * ring and library counts).
 */
internal data class OverviewLayout(
    val widthDp: Int,
    val paddingDp: Int,
    val chartHeightDp: Int,
    val tier: OverviewTier,
    /** The dashboard tier's chart, once the book meter, the ring row and their gaps have their fixed share; null
     * when there isn't enough left for a chart worth showing (the ring row keeps its full height either way). */
    val dashboardChartHeightDp: Int?,
) {
    companion object {
        fun forSize(widthDp: Int, heightDp: Int): OverviewLayout {
            val tier = when {
                heightDp < StripMaxHeightDp -> OverviewTier.STRIP
                widthDp < CompactMaxWidthDp -> OverviewTier.COMPACT
                heightDp >= DashboardMinHeightDp -> OverviewTier.DASHBOARD
                else -> OverviewTier.CHART
            }
            val compact = widthDp < CompactMaxWidthDp || tier == OverviewTier.STRIP
            val padding = if (compact) 12 else 16
            // Only the strip and chart tiers size the chart to the whole remaining height; the header's own height
            // is subtracted from what's left.
            val headerDp = if (tier == OverviewTier.STRIP) StripHeaderDp else HeaderDp
            val chartHeightDp = (heightDp - padding * 2 - headerDp - ChartGapDp).coerceAtLeast(MinChartHeightDp)
            val dashboardRemainingDp = heightDp - padding * 2 - HeaderDp - BookRowDp - RingRowFixedDp - ChartGapDp * 2
            val dashboardChartHeightDp = dashboardRemainingDp.takeIf { it >= MinDashboardChartHeightDp }
            return OverviewLayout(widthDp, padding, chartHeightDp, tier, dashboardChartHeightDp)
        }

        private const val StripMaxHeightDp = 110
        private const val CompactMaxWidthDp = 260
        private const val DashboardMinHeightDp = 190
        private const val HeaderDp = 32
        private const val StripHeaderDp = 26
        private const val ChartGapDp = 10
        private const val MinChartHeightDp = 30
        // The book-open row's own wrap_content height (title/percent/time-left line, then the meter below it).
        private const val BookRowDp = 36
        const val RingRowFixedDp = 64
        private const val MinDashboardChartHeightDp = 36
    }
}
