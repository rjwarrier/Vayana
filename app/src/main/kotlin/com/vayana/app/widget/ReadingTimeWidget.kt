package com.vayana.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import com.vayana.app.MainActivity
import com.vayana.app.R
import com.vayana.core.common.ApplicationScope
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.database.repository.ReadingSessionRepository
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.designsystem.theme.ColorSchemes
import com.vayana.core.designsystem.theme.DarkVariant
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.EinkPalette
import com.vayana.core.designsystem.theme.ThemeMode
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
    private val week: Flow<ReadingWeek> = today.flatMapLatest { day ->
        val since = day.minusDays((ReadingWeek.DaysShown - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        readingSessionRepository.observeSince(since).map { readingWeek(it, day, zone) }
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

    override suspend fun preview(appearance: WidgetAppearance): RemoteViews = withContext(dispatchers.io) {
        val (week, theme) = state.first()
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
            val layout = ReadingWidgetLayout.forSize(widthDp, heightDp)
            val palette = resolvePalette(theme)
            setImageViewBitmap(R.id.reading_background, drawBackground(widthDp, heightDp, density, theme.appearance, palette))
            setOnClickPendingIntent(android.R.id.background, openStatistics())
            setTextViewText(R.id.reading_today, duration(week.todayMinutes))
            setTextViewText(
                R.id.reading_average,
                if (week.hasHistory) duration(week.averageMinutes) else context.getString(Res.string.widget_reading_avg_none),
            )
            applyLayout(layout)
            applyPalette(palette)
            val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
            val dayNames = week.days.map { it.dayOfWeek.getDisplayName(TextStyle.NARROW, locale) }
            val chartWidth = ((widthDp - layout.paddingHorizontalDp * 2) * density).roundToInt().coerceAtLeast(MinChartPx)
            val chartHeight = ((heightDp - layout.chromeHeightDp) * density).roundToInt().coerceAtLeast(MinChartPx)
            val chart = drawReadingTimeChart(
                week = week,
                labels = ReadingChartLabels(dayNames),
                colors = palette.chartColors(),
                widthPx = chartWidth,
                heightPx = chartHeight,
                density = density,
                scaledDensity = scaledDensity,
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
        setViewVisibility(R.id.reading_average_label, if (layout.showAverageWords) View.VISIBLE else View.GONE)
    }

    private fun RemoteViews.applyPalette(palette: ReadingWidgetPalette) {
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

    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).roundToInt()

    private fun resolvePalette(theme: ReadingWidgetTheme): ReadingWidgetPalette {
        val isDark = when (theme.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        }
        val scheme = if (
            theme.dynamicColor && theme.displayProfile != DisplayProfile.E_INK && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            ColorSchemes.dynamic(context, isDark, theme.darkVariant)
        } else {
            ColorSchemes.forProfile(theme.displayProfile, isDark, theme.darkVariant, theme.einkPalette)
        }
        return scheme.toWidgetPalette(theme.displayProfile == DisplayProfile.E_INK)
    }

    private fun drawBackground(
        widthDp: Int,
        heightDp: Int,
        density: Float,
        appearance: WidgetAppearance,
        palette: ReadingWidgetPalette,
    ): Bitmap {
        val width = (widthDp * density).roundToInt().coerceAtLeast(1)
        val height = (heightDp * density).roundToInt().coerceAtLeast(1)
        val radius = if (appearance.matchesLauncher) {
            context.resources.getDimension(R.dimen.widget_radius)
        } else {
            appearance.cornerRadiusDp * density
        }
        return createBitmap(width, height).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.surface
            })
            val stroke = if (palette.eInk) EinkBorderDp * density else StandardBorderDp * density
            val inset = stroke / 2
            canvas.drawRoundRect(
                RectF(inset, inset, width - inset, height - inset),
                (radius - inset).coerceAtLeast(0f),
                (radius - inset).coerceAtLeast(0f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.outline
                    alpha = if (palette.eInk) 255 else StandardBorderAlpha
                    style = Paint.Style.STROKE
                    strokeWidth = stroke
                },
            )
        }
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
        const val StandardBorderDp = 1f
        const val EinkBorderDp = 1.5f
        const val StandardBorderAlpha = 64
        const val MinutesPerHour = 60
        const val MidnightSlackMillis = 1_000L
        const val StatisticsRequestCode = 2
    }
}

private data class ReadingWidgetTheme(
    val appearance: WidgetAppearance,
    val themeMode: ThemeMode,
    val displayProfile: DisplayProfile,
    val einkPalette: EinkPalette,
    val darkVariant: DarkVariant,
    val dynamicColor: Boolean,
)

private fun readingWidgetTheme(settings: SettingsSnapshot) = ReadingWidgetTheme(
    appearance = WidgetAppearance(settings.widgetCornerRadius, settings.widgetProgressStyle),
    themeMode = settings.themeMode,
    displayProfile = settings.displayProfile,
    einkPalette = settings.einkPalette,
    darkVariant = settings.darkVariant,
    dynamicColor = settings.dynamicColor,
)

private data class ReadingWidgetPalette(
    val surface: Int,
    val surfaceHigh: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val primary: Int,
    val tertiary: Int,
    val outline: Int,
    val outlineVariant: Int,
    val eInk: Boolean,
)

private fun ColorScheme.toWidgetPalette(eInk: Boolean) = ReadingWidgetPalette(
    surface = surfaceContainer.toArgb(),
    surfaceHigh = surfaceContainerHigh.toArgb(),
    onSurface = onSurface.toArgb(),
    onSurfaceVariant = onSurfaceVariant.toArgb(),
    primary = primary.toArgb(),
    tertiary = tertiary.toArgb(),
    outline = outline.toArgb(),
    outlineVariant = outlineVariant.toArgb(),
    eInk = eInk,
)

private fun ReadingWidgetPalette.chartColors() = ReadingChartColors(
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
) {
    companion object {
        fun forSize(widthDp: Int, heightDp: Int): ReadingWidgetLayout {
            val compact = widthDp < AverageMinWidthDp || heightDp < CompactHeightDp
            val top = if (compact) 10 else 14
            val bottom = if (compact) 8 else 12
            val header = if (compact) CompactHeaderDp else HeaderDp
            return ReadingWidgetLayout(
                paddingHorizontalDp = if (compact) 10 else 16,
                paddingTopDp = top,
                paddingBottomDp = bottom,
                valueSp = if (compact) 22f else 27f,
                showAverage = !compact,
                showAverageWords = !compact && widthDp >= WordsMinWidthDp,
                chromeHeightDp = top + bottom + header + ChartGapDp,
            )
        }

        private const val AverageMinWidthDp = 200
        private const val WordsMinWidthDp = 260
        private const val CompactHeightDp = 130
        private const val HeaderDp = 36
        private const val CompactHeaderDp = 30
        private const val ChartGapDp = 10
    }
}
