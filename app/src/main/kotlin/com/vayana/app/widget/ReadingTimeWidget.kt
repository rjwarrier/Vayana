package com.vayana.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.vayana.app.MainActivity
import com.vayana.app.R
import com.vayana.core.common.ApplicationScope
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.database.repository.ReadingSessionRepository
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
    @ApplicationContext private val context: Context,
    private val readingSessionRepository: ReadingSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
    @ApplicationScope private val scope: CoroutineScope,
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
    private val week: Flow<ReadingWeek> = today.flatMapLatest { day ->
        val since = day.minusDays((ReadingWeek.DaysShown - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        readingSessionRepository.observeSince(since).map { readingWeek(it, day, zone) }
    }

    private val state: Flow<Pair<ReadingWeek, WidgetAppearance>> = combine(
        week,
        settingsRepository.snapshot.map { WidgetAppearance(it.widgetCornerRadius, it.widgetProgressStyle) },
    ) { week, appearance -> week to appearance }.distinctUntilChanged()

    @Synchronized
    fun start() {
        if (observer?.isActive == true || !hasWidgets()) return
        observer = scope.launch { state.collect { (week, appearance) -> render(week, appearance) } }
    }

    @Synchronized
    fun stop() {
        observer?.cancel()
        observer = null
    }

    fun refresh(onDone: () -> Unit) {
        scope.launch {
            try {
                val (week, appearance) = state.first()
                render(week, appearance)
            } finally {
                onDone()
            }
        }
    }

    override suspend fun preview(appearance: WidgetAppearance): RemoteViews = withContext(dispatchers.io) {
        views(state.first().first, appearance, DefaultWidthDp, DefaultHeightDp)
    }

    private suspend fun render(week: ReadingWeek, appearance: WidgetAppearance) = withContext(dispatchers.io) {
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, ReadingTimeWidget::class.java)).forEach { id ->
            // Portrait: the width the launcher gives at its narrowest, the height at its tallest.
            val options = manager.getAppWidgetOptions(id)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: DefaultWidthDp
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: DefaultHeightDp
            manager.updateAppWidget(id, views(week, appearance, width, height))
        }
    }

    private fun hasWidgets(): Boolean = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, ReadingTimeWidget::class.java))
        .isNotEmpty()

    private fun views(week: ReadingWeek, appearance: WidgetAppearance, widthDp: Int, heightDp: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_reading_time).apply {
            appearance.applyTo(this)
            setOnClickPendingIntent(android.R.id.background, openStatistics())
            setTextViewText(R.id.reading_today, duration(week.todayMinutes))
            setTextViewText(
                R.id.reading_average,
                context.getString(Res.string.widget_reading_average, duration(week.averageMinutes.roundToInt())),
            )
            val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
            week.days.forEachIndexed { index, day ->
                setTextViewText(DayLabels[index], day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale))
            }
            val density = context.resources.displayMetrics.density
            val chartWidth = ((widthDp - ChromeWidthDp) * density).roundToInt().coerceAtLeast(MinChartPx)
            val chartHeight = ((heightDp - ChromeHeightDp) * density).roundToInt().coerceAtLeast(MinChartPx)
            val chart = drawReadingTimeChart(week, chartWidth, chartHeight, density)
            setImageViewBitmap(R.id.reading_chart_frame, chart.frame)
            setImageViewBitmap(R.id.reading_chart_line, chart.line)
            setContentDescription(
                R.id.reading_chart_line,
                context.getString(Res.string.widget_reading_chart_description, duration(week.averageMinutes.roundToInt())),
            )
        }

    private fun duration(minutes: Int): String = if (minutes >= MinutesPerHour) {
        context.getString(Res.string.reader_duration_hours_minutes, minutes / MinutesPerHour, minutes % MinutesPerHour)
    } else {
        context.getString(Res.string.reader_duration_minutes, minutes)
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
        val DayLabels = intArrayOf(
            R.id.reading_day_0, R.id.reading_day_1, R.id.reading_day_2, R.id.reading_day_3,
            R.id.reading_day_4, R.id.reading_day_5, R.id.reading_day_6,
        )
        const val DefaultWidthDp = 320
        const val DefaultHeightDp = 150
        // The widget's padding (2 x 14dp) across; padding, header, gap and day labels down.
        const val ChromeWidthDp = 28
        const val ChromeHeightDp = 100
        const val MinChartPx = 48
        const val MinutesPerHour = 60
        const val MidnightSlackMillis = 1_000L
        const val StatisticsRequestCode = 2
    }
}
