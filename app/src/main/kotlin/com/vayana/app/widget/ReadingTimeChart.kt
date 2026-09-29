package com.vayana.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import com.vayana.core.database.model.ReadingSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

/** Reading minutes for the seven days ending [today], oldest first, and their average. */
data class ReadingWeek(val today: LocalDate, val minutesByDay: List<Int>) {
    val todayMinutes: Int get() = minutesByDay.last()
    val days: List<LocalDate> get() = List(DaysShown) { today.minusDays((DaysShown - 1 - it).toLong()) }
    val averageMinutes: Float get() = minutesByDay.sum().toFloat() / DaysShown

    companion object {
        const val DaysShown = 7
    }
}

/**
 * The week from sessions (this device's and synced ones), counted as Statistics counts them: sessions of a minute or
 * more, on the day they started.
 */
fun readingWeek(sessions: List<ReadingSession>, today: LocalDate, zone: ZoneId): ReadingWeek {
    val first = today.minusDays((ReadingWeek.DaysShown - 1).toLong())
    val seconds = LongArray(ReadingWeek.DaysShown)
    sessions.forEach { session ->
        if (session.durationSeconds < MinCountedSessionSeconds) return@forEach
        val day = Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate()
        val index = (day.toEpochDay() - first.toEpochDay()).toInt()
        if (index in seconds.indices) seconds[index] += session.durationSeconds
    }
    return ReadingWeek(today, seconds.map { (it / SecondsPerMinute).toInt() })
}

/**
 * The week drawn as two alpha masks the widget tints from its theme (so it follows dark mode and the wallpaper's
 * colours like the rest of the widget): [frame] holds the gridlines and the dashed average line, [line] the reading
 * line, its fill and its points. Days sit at the centres of seven equal columns, under which the widget's day labels
 * line up.
 */
class ReadingTimeChart(val frame: Bitmap, val line: Bitmap)

fun drawReadingTimeChart(week: ReadingWeek, widthPx: Int, heightPx: Int, density: Float): ReadingTimeChart {
    val frame = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ALPHA_8)
    val line = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ALPHA_8)
    val inset = PlotInsetDp * density
    val top = inset
    val bottom = heightPx - inset
    val scale = niceScale(maxOf(week.minutesByDay.max().toFloat(), week.averageMinutes, MinScaleMinutes))
    val columnWidth = widthPx.toFloat() / ReadingWeek.DaysShown
    fun x(index: Int) = columnWidth * (index + 0.5f)
    fun y(minutes: Float) = bottom - (bottom - top) * (minutes / scale).coerceIn(0f, 1f)

    Canvas(frame).apply {
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = GridStrokeDp * density
        }
        // Horizontal gridlines at quarters of the scale, the baseline a little stronger.
        for (step in 0..GridSteps) {
            grid.alpha = if (step == 0) BaselineAlpha else GridAlpha
            val gy = y(scale * step / GridSteps)
            drawLine(0f, gy, widthPx.toFloat(), gy, grid)
        }
        grid.alpha = VerticalGridAlpha
        for (index in 0 until ReadingWeek.DaysShown) drawLine(x(index), top, x(index), bottom, grid)
        // The week's average: a dashed horizontal line across the whole chart.
        val average = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = AverageStrokeDp * density
            strokeCap = Paint.Cap.ROUND
            pathEffect = DashPathEffect(floatArrayOf(DashDp * density, GapDp * density), 0f)
        }
        val ay = y(week.averageMinutes)
        drawLine(0f, ay, widthPx.toFloat(), ay, average)
    }

    Canvas(line).apply {
        // A smooth curve: each segment eases out of one day and into the next, never overshooting either.
        val curve = Path()
        week.minutesByDay.forEachIndexed { index, minutes ->
            val px = x(index)
            val py = y(minutes.toFloat())
            if (index == 0) {
                curve.moveTo(px, py)
            } else {
                val previousX = x(index - 1)
                val previousY = y(week.minutesByDay[index - 1].toFloat())
                val midX = (previousX + px) / 2
                curve.cubicTo(midX, previousY, midX, py, px, py)
            }
        }
        val fill = Path(curve).apply {
            lineTo(x(ReadingWeek.DaysShown - 1), bottom)
            lineTo(x(0), bottom)
            close()
        }
        drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = FillAlpha })
        drawPath(
            curve,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = LineStrokeDp * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            },
        )
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        week.minutesByDay.forEachIndexed { index, minutes ->
            val today = index == ReadingWeek.DaysShown - 1
            drawCircle(x(index), y(minutes.toFloat()), (if (today) TodayDotDp else DotDp) * density, dot)
        }
    }
    return ReadingTimeChart(frame, line)
}

/** The chart's top value: the next of 15, 30, 45, 60 minutes, then whole half-hours, so gridlines fall on round times. */
internal fun niceScale(minutes: Float): Float {
    val step = if (minutes <= SmallScaleLimit) SmallScaleStep else LargeScaleStep
    return ceil(minutes / step) * step
}

private const val MinCountedSessionSeconds = 60L
private const val SecondsPerMinute = 60L
private const val MinScaleMinutes = 15f
private const val SmallScaleLimit = 60f
private const val SmallScaleStep = 15f
private const val LargeScaleStep = 30f
private const val GridSteps = 4
private const val PlotInsetDp = 6f
private const val GridStrokeDp = 1f
private const val AverageStrokeDp = 1.5f
private const val LineStrokeDp = 3f
private const val DotDp = 3f
private const val TodayDotDp = 4.5f
private const val DashDp = 5f
private const val GapDp = 4f
private const val BaselineAlpha = 150
private const val GridAlpha = 60
private const val VerticalGridAlpha = 35
private const val FillAlpha = 55
