package com.vayana.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import com.vayana.core.database.model.ReadingSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Reading minutes for the seven days before [today] and for today itself, oldest first ([minutesByDay] has
 * [DaysShown] entries, today last). The average covers the seven days before today only, counting empty days.
 */
data class ReadingWeek(val today: LocalDate, val minutesByDay: List<Int>) {
    init {
        require(minutesByDay.size == DaysShown)
    }

    val todayMinutes: Int get() = minutesByDay.last()
    val days: List<LocalDate> get() = List(DaysShown) { today.minusDays((DaysShown - 1 - it).toLong()) }

    /** No reading in the whole window: there is nothing to average or compare with. */
    val hasHistory: Boolean get() = minutesByDay.any { it > 0 }

    val averageMinutes: Int get() = (minutesByDay.take(AveragedDays).sum().toDouble() / AveragedDays).roundToInt()

    companion object {
        const val DaysShown = 8
        const val AveragedDays = DaysShown - 1
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

/** Colours the chart is drawn in; the widget resolves them from the app's theme. */
class ReadingChartColors(
    val plotBackground: Int,
    val grid: Int,
    val frame: Int,
    val line: Int,
    val average: Int,
    val textPrimary: Int,
    val textMuted: Int,
    /** Fill of the past points' rings, the widget's own surface. */
    val surface: Int,
    /** E-ink has no hue to tell the average from the line, so the average is dashed there. */
    val eInk: Boolean,
)

/** Labels under and beside the plot. */
class ReadingChartLabels(val days: List<String>) {
    init {
        require(days.size == ReadingWeek.DaysShown)
    }
}

/**
 * The chart: a framed grid with three equal steps, the reading line in straight segments with a point per day (today's
 * larger and on the right edge), and the seven-day average as a horizontal line. [density] and [scaledDensity] turn
 * dp and sp into pixels.
 */
fun drawReadingTimeChart(
    week: ReadingWeek,
    labels: ReadingChartLabels,
    colors: ReadingChartColors,
    widthPx: Int,
    heightPx: Int,
    density: Float,
    scaledDensity: Float,
): Bitmap {
    val bitmap = createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1))
    val canvas = Canvas(bitmap)
    val showLabels = heightPx >= MinLabelledHeightDp * density
    val left = if (showLabels) GutterDp * density else PointRadiusInsetDp * density
    val right = widthPx - RightInsetDp * density
    val top = TopInsetDp * density
    val bottom = (if (showLabels) heightPx - LabelBandDp * density else heightPx - TopInsetDp * density)
        .coerceAtLeast(top + 1f)

    val step = scaleStep(max(week.minutesByDay.max(), if (week.hasHistory) week.averageMinutes else 0))
    val yMax = step * Intervals.toFloat()
    fun x(index: Int) = left + (right - left) * index / (ReadingWeek.DaysShown - 1)
    fun y(minutes: Number) = bottom - (bottom - top) * (minutes.toFloat() / yMax).coerceIn(0f, 1f)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.style = Paint.Style.FILL
    paint.color = colors.plotBackground
    canvas.drawRect(left, top, right, bottom, paint)

    // Graph paper: fine lines between the steps and between the days, then the steps and days a little bolder, then
    // the frame. Only as many fine lines as leave cells of MinCellDp, so a small widget stays calm.
    val rows = (((bottom - top) / Intervals / density / MinCellDp).toInt()).coerceIn(1, HorizontalSubdivisions)
    val columns = (((right - left) / (ReadingWeek.DaysShown - 1) / density / MinCellDp).toInt())
        .coerceIn(1, VerticalSubdivisions)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = MinorGridStrokeDp * density
    paint.color = ColorUtils.blendARGB(colors.grid, colors.frame, MinorGridBlend)
    for (line in 1 until Intervals * rows) {
        if (line % rows == 0) continue
        val ly = y(step * line / rows.toFloat())
        canvas.drawLine(left, ly, right, ly, paint)
    }
    for (line in 1 until (ReadingWeek.DaysShown - 1) * columns) {
        if (line % columns == 0) continue
        val lx = left + (right - left) * line / ((ReadingWeek.DaysShown - 1) * columns)
        canvas.drawLine(lx, top, lx, bottom, paint)
    }
    paint.strokeWidth = GridStrokeDp * density
    paint.color = ColorUtils.blendARGB(colors.grid, colors.frame, GridBlend)
    for (interval in 1 until Intervals) canvas.drawLine(left, y(step * interval), right, y(step * interval), paint)
    for (index in 1 until ReadingWeek.DaysShown - 1) canvas.drawLine(x(index), top, x(index), bottom, paint)

    paint.strokeWidth = FrameStrokeDp * density
    paint.color = colors.frame
    canvas.drawRect(left, top, right, bottom, paint)

    if (showLabels) {
        val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT
            textSize = AxisSp * scaledDensity
            color = colors.textMuted
            textAlign = Paint.Align.RIGHT
        }
        for (interval in 0..Intervals) {
            val minutes = step * interval
            canvas.drawText(axisLabel(minutes), left - AxisGapDp * density, y(minutes) + axis.textSize / 3, axis)
        }
    }

    val path = Path()
    week.minutesByDay.forEachIndexed { index, minutes ->
        if (index == 0) path.moveTo(x(index), y(minutes)) else path.lineTo(x(index), y(minutes))
    }
    val area = Path(path).apply {
        lineTo(x(ReadingWeek.DaysShown - 1), bottom)
        lineTo(x(0), bottom)
        close()
    }
    paint.style = Paint.Style.FILL
    paint.color = ColorUtils.setAlphaComponent(colors.line, AreaAlpha)
    canvas.drawPath(area, paint)

    if (week.hasHistory) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = AverageStrokeDp * density
        paint.color = colors.average
        if (colors.eInk) paint.pathEffect = DashPathEffect(floatArrayOf(DashDp * density, DashDp * density), 0f)
        canvas.drawLine(left, y(week.averageMinutes), right, y(week.averageMinutes), paint)
        paint.pathEffect = null
    }

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = LineStrokeDp * density
    paint.color = colors.line
    paint.strokeJoin = Paint.Join.MITER
    paint.strokeCap = Paint.Cap.SQUARE
    canvas.drawPath(path, paint)

    val last = ReadingWeek.DaysShown - 1
    val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    for (index in 0 until last) {
        ring.style = Paint.Style.FILL
        ring.color = colors.surface
        canvas.drawCircle(x(index), y(week.minutesByDay[index]), PastDotDp * density, ring)
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = DotStrokeDp * density
        ring.color = colors.line
        canvas.drawCircle(x(index), y(week.minutesByDay[index]), PastDotDp * density, ring)
    }
    ring.style = Paint.Style.FILL
    ring.color = colors.line
    canvas.drawCircle(x(last), y(week.todayMinutes), TodayDotDp * density, ring)
    ring.style = Paint.Style.STROKE
    ring.strokeWidth = DotStrokeDp * density
    ring.color = colors.surface
    canvas.drawCircle(x(last), y(week.todayMinutes), TodayDotDp * density, ring)

    if (showLabels) {
        val day = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT
            textSize = DaySp * scaledDensity
            textAlign = Paint.Align.CENTER
        }
        labels.days.forEachIndexed { index, text ->
            val isToday = index == last
            day.color = if (isToday) colors.textPrimary else colors.textMuted
            day.isFakeBoldText = isToday
            canvas.drawText(text, x(index), bottom + DayBaselineDp * density, day)
        }
    }
    return bitmap
}

/** The y-axis step in minutes: the smallest of a few round values whose three intervals hold [maxMinutes]. */
internal fun scaleStep(maxMinutes: Int): Int =
    ScaleSteps.firstOrNull { it * Intervals >= maxMinutes }
        ?: (((maxMinutes + Intervals - 1) / Intervals + LongStepRound - 1) / LongStepRound * LongStepRound)

/** A tick label: `0`, `30m`, `1h`, `1h30`. */
internal fun axisLabel(minutes: Int): String = when {
    minutes == 0 -> "0"
    minutes < MinutesPerHour -> "${minutes}m"
    minutes % MinutesPerHour == 0 -> "${minutes / MinutesPerHour}h"
    else -> "${minutes / MinutesPerHour}h${minutes % MinutesPerHour}"
}

private val ScaleSteps = listOf(10, 20, 30, 40, 60, 90, 120, 180)
private const val Intervals = 3
private const val MinCellDp = 10f
private const val HorizontalSubdivisions = 4
private const val VerticalSubdivisions = 4
private const val LongStepRound = 60
private const val MinutesPerHour = 60
private const val MinCountedSessionSeconds = 60L
private const val SecondsPerMinute = 60L
private const val MinLabelledHeightDp = 64f
private const val GutterDp = 31f
private const val AxisGapDp = 6f
private const val LabelBandDp = 22f
private const val DayBaselineDp = 16f
private const val TopInsetDp = 5f
private const val RightInsetDp = 5f
private const val PointRadiusInsetDp = 5f
private const val MinorGridStrokeDp = 0.75f
private const val GridStrokeDp = 1.25f
private const val MinorGridBlend = 0.45f
private const val GridBlend = 0.75f
private const val FrameStrokeDp = 1.5f
private const val AverageStrokeDp = 1.5f
private const val LineStrokeDp = 2f
private const val PastDotDp = 3f
private const val TodayDotDp = 5f
private const val DotStrokeDp = 1.5f
private const val DashDp = 4f
private const val AxisSp = 8.5f
private const val DaySp = 9f
private const val AreaAlpha = 31
