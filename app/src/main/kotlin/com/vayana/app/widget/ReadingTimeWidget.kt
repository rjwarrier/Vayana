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
import com.vayana.core.database.repository.ReadingSessionRepository
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.datastore.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
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

/** The reading-time widget: today's minutes across devices and the last seven days as a line chart. */
@AndroidEntryPoint
class ReadingTimeWidget : AppWidgetProvider() {
    @Inject lateinit var updater: ReadingTimeWidgetUpdater

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

/**
 * Redraws the reading-time widget when a session is recorded or synced in, when the widget style changes, and when the
 * day turns over; each placed widget gets a chart drawn for its own size.
 */
@Singleton
class ReadingTimeWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val readingSessionRepository: ReadingSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
    @param:ApplicationScope private val scope: CoroutineScope,
) : VayanaWidget {
    override val provider = ReadingTimeWidget::class.java
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val week: Flow<ReadingWeek> = combine(
        today,
        settingsRepository.snapshot.map { it.widgetChartDays }.distinctUntilChanged(),
    ) { day, days -> day to days }.flatMapLatest { (day, days) -> weekOf(day, days) }

    private fun weekOf(day: LocalDate, days: Int): Flow<ReadingWeek> {
        val since = day.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        return readingSessionRepository.observeSince(since).map { readingWeek(it, day, zone, days) }
    }

    private val state: Flow<Pair<ReadingWeek, ReadingWidgetTheme>> = combine(
        week,
        settingsRepository.snapshot.map(::readingWidgetTheme),
    ) { week, theme -> week to theme }.distinctUntilChanged()

    @Synchronized
    fun start() {
        if (observer?.isActive == true || !hasWidgets()) return
        observer = scope.launch { state.collect { (week, theme) -> render(week, theme) } }
    }

    @Synchronized
    fun stop() {
        observer?.cancel()
        observer = null
    }

    fun refresh(onDone: () -> Unit) {
        scope.launch {
            try {
                val (week, theme) = state.first()
                render(week, theme)
            } finally {
                onDone()
            }
        }
    }

    // The configure screen redraws its preview on every slider step: a month of days is read once and sliced for
    // each Days value tried, kept only briefly so a later visit sees fresh reading.
    @Volatile private var previewMonth: Pair<Long, ReadingWeek>? = null

    override suspend fun preview(appearance: WidgetAppearance): RemoteViews = withContext(dispatchers.io) {
        val theme = settingsRepository.snapshot.map(::readingWidgetTheme).first()
        val today = LocalDate.now(zone)
        val now = SystemClock.elapsedRealtime()
        val month = previewMonth?.takeIf { (at, week) -> week.today == today && now - at < PreviewCacheMillis }?.second
            ?: weekOf(today, ReadingWeek.MaxDays).first().also { previewMonth = now to it }
        // The days being previewed may not be saved yet.
        val week = ReadingWeek(today, month.minutesByDay.takeLast(appearance.chartDays))
        views(week, theme.copy(appearance = appearance), DefaultWidthDp, DefaultHeightDp)
    }

    private suspend fun render(week: ReadingWeek, theme: ReadingWidgetTheme) = withContext(dispatchers.io) {
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, ReadingTimeWidget::class.java)).forEach { id ->
            val options = manager.getAppWidgetOptions(id)
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val widthKey = if (landscape) {
                AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
            } else {
                AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
            }
            val heightKey = if (landscape) {
                AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
            } else {
                AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
            }
            val width = options.getInt(widthKey).takeIf { it > 0 } ?: DefaultWidthDp
            val height = options.getInt(heightKey).takeIf { it > 0 } ?: DefaultHeightDp
            manager.updateAppWidget(id, views(week, theme, width, height))
        }
    }

    private fun hasWidgets(): Boolean = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, ReadingTimeWidget::class.java))
        .isNotEmpty()

    private fun views(week: ReadingWeek, theme: ReadingWidgetTheme, widthDp: Int, heightDp: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_reading_time).apply {
            // Bitmaps are drawn at most at 2x; the image views scale them up on denser screens.
            val density = minOf(context.resources.displayMetrics.density, MaxBitmapDensity)
            val scaledDensity = density * context.resources.configuration.fontScale
            val fill = theme.appearance.chartFillsWidget
            val layout = ReadingWidgetLayout.forSize(widthDp, heightDp, theme.appearance.chartHeader, fill, theme.appearance.border)
            val palette = theme.colors.palette(context)
            // The shared corner shapes, tinted to the app's theme: no full-size bitmap to send on every redraw.
            applySurface(R.id.reading_background, R.id.reading_border, theme.appearance, palette)
            setOnClickPendingIntent(android.R.id.background, openStatistics())
            setTextViewText(R.id.reading_today, duration(week.todayMinutes))
            setTextViewText(
                R.id.reading_average,
                if (week.hasHistory) duration(week.averageMinutes) else context.getString(Res.string.widget_reading_avg_none),
            )
            applyLayout(layout)
            applyPalette(palette)
            val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
            // A week reads by weekday; longer spans repeat the letters, so they read by date.
            val byDate = week.days.size > ReadingWeek.MinDays
            val dayNames = week.days.map { if (byDate) "${it.dayOfMonth}" else it.dayOfWeek.getDisplayName(TextStyle.NARROW, locale) }
            val chartWidth = ((widthDp - layout.paddingHorizontalDp * 2) * density).roundToInt().coerceAtLeast(MinChartPx)
            val chartHeight = ((heightDp - layout.chromeHeightDp) * density).roundToInt().coerceAtLeast(MinChartPx)
            // The header's text starts and ends where the plot box does.
            val (plotLeft, plotRight) = plotInsets(
                week,
                chartLabelsShown(theme.appearance.chartAxisLabels, chartHeight, density),
                density,
                scaledDensity,
            )
            // The header's bottom padding is the gap above the chart, so hiding the header closes the gap too.
            setViewVisibility(R.id.reading_header, if (layout.showHeader) View.VISIBLE else View.GONE)
            setViewPadding(
                R.id.reading_header,
                dpToPx(plotLeft / density), 0, dpToPx(plotRight / density), dpToPx(ReadingWidgetLayout.ChartGapDp),
            )
            val chart = drawReadingTimeChart(
                week = week,
                labels = ReadingChartLabels(dayNames),
                colors = palette.chartColors(),
                widthPx = chartWidth,
                heightPx = chartHeight,
                density = density,
                scaledDensity = scaledDensity,
                style = theme.appearance.chartStyle,
                showAxisLabels = theme.appearance.chartAxisLabels,
                gridOpacity = theme.appearance.chartGridOpacity,
                fill = fill,
                // Inside the widget's corners by the inset that keeps its border showing.
                fillCornerPx = (backgroundRadiusPx(theme.appearance, density) - layout.paddingTopDp * density).coerceAtLeast(0f),
            )
            setImageViewBitmap(R.id.reading_chart, chart)
            setContentDescription(R.id.reading_chart, chartDescription(week, locale))
        }

    private fun RemoteViews.applyLayout(layout: ReadingWidgetLayout) {
        setViewPadding(
            R.id.reading_content,
            dpToPx(layout.paddingHorizontalDp), dpToPx(layout.paddingTopDp),
            dpToPx(layout.paddingHorizontalDp), dpToPx(layout.paddingBottomDp),
        )
        setTextViewTextSize(R.id.reading_today, TypedValue.COMPLEX_UNIT_SP, layout.valueSp)
        setViewVisibility(R.id.reading_average_group, if (layout.showAverage) View.VISIBLE else View.GONE)
        // The legend line goes with its words, so it never floats over nothing.
        setViewVisibility(R.id.reading_average_legend, if (layout.showAverageWords) View.VISIBLE else View.GONE)
    }

    private fun RemoteViews.applyPalette(palette: WidgetPalette) {
        setTextColor(R.id.reading_today, palette.onSurface)
        setTextColor(R.id.reading_average_label, palette.onSurfaceVariant)
        setTextColor(R.id.reading_average, palette.tertiary)
        setInt(R.id.reading_average_rule, "setBackgroundColor", palette.tertiary)
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

    private fun dpToPx(dp: Int): Int = dpToPx(dp.toFloat())

    private fun dpToPx(dp: Float): Int = (dp * context.resources.displayMetrics.density).roundToInt()

    /** The widget's corner radius in the chart bitmap's pixels ([density]), whose dp the launcher's radius is given in. */
    private fun backgroundRadiusPx(appearance: WidgetAppearance, density: Float): Float =
        if (appearance.matchesLauncher) {
            context.resources.getDimension(R.dimen.widget_radius) / context.resources.displayMetrics.density * density
        } else {
            appearance.cornerRadiusDp * density
        }

    private fun duration(minutes: Int): String = when {
        minutes < MinutesPerHour -> context.getString(Res.string.reader_duration_minutes, minutes)
        minutes % MinutesPerHour == 0 -> context.getString(Res.string.widget_reading_duration_hours, minutes / MinutesPerHour)
        else -> context.getString(Res.string.reader_duration_hours_minutes, minutes / MinutesPerHour, minutes % MinutesPerHour)
    }

    private fun openStatistics(): PendingIntent = PendingIntent.getActivity(
        context,
        StatisticsRequestCode,
        Intent(context, MainActivity::class.java)
            .setAction(AppShortcuts.ActionStatistics)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val DefaultWidthDp = 320
        const val DefaultHeightDp = 160
        const val MinChartPx = 32
        const val MaxBitmapDensity = 2f
        const val PreviewCacheMillis = 30_000L
        const val MinutesPerHour = 60
        const val MidnightSlackMillis = 1_000L
        const val StatisticsRequestCode = 2
    }
}

private data class ReadingWidgetTheme(val appearance: WidgetAppearance, val colors: WidgetTheme)

private fun readingWidgetTheme(settings: SettingsSnapshot) = ReadingWidgetTheme(settings.widgetAppearance(), settings.widgetTheme())

internal fun WidgetPalette.chartColors() = ReadingChartColors(
    plotBackground = surfaceHigh,
    grid = outlineVariant,
    frame = outline,
    line = primary,
    average = tertiary,
    textPrimary = onSurface,
    textMuted = onSurfaceVariant,
    surface = surface,
    eInk = eInk,
)

/** What the widget shows at a given size: the header sheds the average's words, then the average itself, as it shrinks. */
internal data class ReadingWidgetLayout(
    val paddingHorizontalDp: Int,
    val paddingTopDp: Int,
    val paddingBottomDp: Int,
    val valueSp: Float,
    val showAverage: Boolean,
    val showAverageWords: Boolean,
    val chromeHeightDp: Int,
    val showHeader: Boolean,
) {
    companion object {
        /**
         * Without a [header] the chart rises to the top margin; with [fill] too, it reaches the widget's edges, inset
         * only enough to leave the widget's border showing.
         */
        fun forSize(
            widthDp: Int,
            heightDp: Int,
            header: Boolean = true,
            fill: Boolean = false,
            border: Boolean = true,
        ): ReadingWidgetLayout {
            val compact = widthDp < AverageMinWidthDp || heightDp < CompactHeightDp
            // One margin on all four sides.
            val margin = when {
                // Filled, the graph stops short of the border so it shows; with no border it reaches the edge.
                fill && !header -> if (border) FillInsetDp else 0
                compact -> 10
                else -> 16
            }
            val headerDp = if (compact) CompactHeaderDp else HeaderDp
            return ReadingWidgetLayout(
                paddingHorizontalDp = margin,
                paddingTopDp = margin,
                paddingBottomDp = margin,
                valueSp = if (compact) 19f else 22f,
                showAverage = !compact,
                showAverageWords = !compact && widthDp >= WordsMinWidthDp,
                chromeHeightDp = margin * 2 + if (header) headerDp + ChartGapDp else 0,
                showHeader = header,
            )
        }

        private const val AverageMinWidthDp = 200
        private const val WordsMinWidthDp = 260
        private const val CompactHeightDp = 130
        // The value's line height without font padding; the average row is shorter.
        private const val HeaderDp = 26
        private const val CompactHeaderDp = 22
        const val ChartGapDp = 10
        private const val FillInsetDp = 2
    }
}
