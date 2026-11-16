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
import com.vayana.core.datastore.settings.WidgetChartDaysMax
import com.vayana.core.datastore.settings.WidgetChartDaysMin
import com.vayana.core.datastore.settings.WidgetChartStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Reading minutes for the days before [today] and for today itself, oldest first (today last): a week and today up to
 * a month. The average covers the seven days before today only, counting empty days, however many days are shown.
 */
data class ReadingWeek(val today: LocalDate, val minutesByDay: List<Int>) {
    init {
        require(minutesByDay.size in MinDays..MaxDays)
    }

    val todayMinutes: Int get() = minutesByDay.last()
    val days: List<LocalDate> get() = List(minutesByDay.size) { today.minusDays((minutesByDay.size - 1 - it).toLong()) }

    /** No reading in the whole window: there is nothing to average or compare with. */
    val hasHistory: Boolean get() = minutesByDay.any { it > 0 }

    val averageMinutes: Int get() =
        (minutesByDay.dropLast(1).takeLast(AveragedDays).sum().toDouble() / AveragedDays).roundToInt()

    companion object {
        const val MinDays = WidgetChartDaysMin
        const val MaxDays = WidgetChartDaysMax
        const val AveragedDays = 7
    }
}

/**
 * The week from sessions (this device's and synced ones), counted as Statistics counts them: sessions of a minute or
 * more, on the day they started.
 */
fun readingWeek(sessions: List<ReadingSession>, today: LocalDate, zone: ZoneId, days: Int = ReadingWeek.MinDays): ReadingWeek {
    val first = today.minusDays((days - 1).toLong())
    val seconds = LongArray(days)
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

/** The day labels under the plot, one per day shown. */
class ReadingChartLabels(val days: List<String>)

/**
 * The chart: a framed grid with three equal steps, the week in [style] (a line of straight segments or a smooth curve
 * with a point per day, today's larger and on the right edge, or a bar per day), and the seven-day average as a
 * horizontal line. [showAxisLabels] adds the day and hour labels when there is room. [density] and [scaledDensity] turn
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
    style: WidgetChartStyle = WidgetChartStyle.LINE,
    showAxisLabels: Boolean = true,
    gridOpacity: Int = 100,
    /** Edge to edge: no frame, no labels, the plot clipped to [fillCornerPx] (the widget's own corners). */
    fill: Boolean = false,
    fillCornerPx: Float = 0f,
    /** The average line is always dashed, not only on E-Ink where colour alone can't tell it from the week line. */
    averageDashed: Boolean = false,
): Bitmap {
    val bitmap = createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1))
    val canvas = Canvas(bitmap)
    val showLabels = !fill && chartLabelsShown(showAxisLabels, heightPx, density)
    val count = week.minutesByDay.size
    require(labels.days.size == count)
    val last = count - 1
    // Points sit on the day lines; bars sit between them, one column per day.
    val bars = style == WidgetChartStyle.BARS
    val spans = if (bars) count else count - 1
    val step = week.scaleStep()
    val axis = axisPaint(scaledDensity).apply { color = colors.textMuted }
    val axisLabels = (0..Intervals).map { axisLabel(step * it) }
    val (leftInset, rightInset) = if (fill) 0f to 0f else plotInsets(week, showLabels, density, scaledDensity)
    val left = leftInset
    val right = widthPx - rightInset
    val top = if (fill) 0f else TopInsetDp * density
    val bottom = (if (showLabels) heightPx - LabelBandDp * density else heightPx - top).coerceAtLeast(top + 1f)
    // Filled, the plot reaches the edges, so the points keep in by their own size to stay whole.
    val pointInset = if (fill && !bars) (TodayDotDp + DotStrokeDp) * density else 0f
    val xLeft = left + pointInset
    val xRight = right - pointInset

    val yMax = step * Intervals.toFloat()
    val day = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        textSize = DaySp * scaledDensity
    }
    val dayWidth = (xRight - xLeft) / spans
    // Today always has a label; with many days, so does every [every]th day back from it, as many as fit.
    val every = dayLabelEvery(labels.days.maxOf(day::measureText), dayWidth, DayLabelGapDp * density)
    fun x(index: Int) = xLeft + (xRight - xLeft) * (index + if (bars) 0.5f else 0f) / spans
    val yInset = if (fill) (TodayDotDp + DotStrokeDp) * density else 0f
    fun y(minutes: Number) = bottom - yInset - (bottom - top - 2 * yInset) * (minutes.toFloat() / yMax).coerceIn(0f, 1f)

    // A softly rounded plot: everything inside it is clipped to the corners; the line and its points are not, so
    // today's point on the right edge stays whole.
    val corner = if (fill) fillCornerPx else PlotCornerDp * density
    val plot = Path().apply { addRoundRect(left, top, right, bottom, corner, corner, Path.Direction.CW) }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.style = Paint.Style.FILL
    paint.color = colors.plotBackground
    canvas.drawPath(plot, paint)
    canvas.save()
    canvas.clipPath(plot)

    // Graph paper: fine lines between the steps and between the days, then the steps and days a little bolder, then
    // the frame. Only as many fine rows as leave cells of MinCellDp, so a small widget stays calm, and the day columns
    // split to match the rows so the cells come out square.
    val intervalHeight = (bottom - top - 2 * yInset) / Intervals
    val rows = ((intervalHeight / density / MinCellDp).toInt()).coerceIn(1, HorizontalSubdivisions)
    val columns = (dayWidth / (intervalHeight / rows)).roundToInt().coerceIn(1, VerticalSubdivisions)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = MinorGridStrokeDp * density
    val gridAlpha = (255 * gridOpacity.coerceIn(0, 100) / 100f).roundToInt()
    val minorGrid = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(colors.grid, colors.frame, MinorGridBlend), gridAlpha)
    val majorGrid = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(colors.grid, colors.frame, GridBlend), gridAlpha)
    paint.color = minorGrid
    for (line in 1 until Intervals * rows) {
        if (line % rows == 0) continue
        val ly = y(step * line / rows.toFloat())
        canvas.drawLine(left, ly, right, ly, paint)
    }
    for (line in 1 until spans * columns) {
        if (line % columns == 0) continue
        val lx = xLeft + (xRight - xLeft) * line / (spans * columns)
        canvas.drawLine(lx, top, lx, bottom, paint)
    }
    paint.strokeWidth = GridStrokeDp * density
    paint.color = majorGrid
    // Filled, there is no frame: the grid draws its own outer lines, where the points' inset leaves it.
    val edges = if (fill) 0..Intervals else 1 until Intervals
    for (interval in edges) canvas.drawLine(left, y(step * interval), right, y(step * interval), paint)
    // Day lines: bolder where a label sits, fine between them.
    for (span in if (fill) 0..spans else 1 until spans) {
        val major = fill && (span == 0 || span == spans) || (spans - span) % every == 0
        paint.strokeWidth = (if (major) GridStrokeDp else MinorGridStrokeDp) * density
        paint.color = if (major) majorGrid else minorGrid
        val sx = xLeft + (xRight - xLeft) * span / spans
        canvas.drawLine(sx, top, sx, bottom, paint)
    }

    val path = Path()
    paint.style = Paint.Style.FILL
    if (bars) {
        val half = dayWidth * BarWidthFraction / 2
        val radius = BarRadiusDp * density
        week.minutesByDay.forEachIndexed { index, minutes ->
            if (minutes <= 0) return@forEachIndexed
            val barTop = y(minutes)
            paint.color = if (index == last) colors.line else ColorUtils.setAlphaComponent(colors.line, PastBarAlpha)
            // Rounded at the top, square on the baseline.
            canvas.drawRoundRect(x(index) - half, barTop, x(index) + half, bottom, radius, radius, paint)
            canvas.drawRect(x(index) - half, (barTop + radius).coerceAtMost(bottom), x(index) + half, bottom, paint)
        }
    } else {
        week.minutesByDay.forEachIndexed { index, minutes ->
            when {
                index == 0 -> path.moveTo(x(index), y(minutes))
                // Control points level with each end, half a day apart: smooth, and never past the days it joins.
                style == WidgetChartStyle.CURVE -> {
                    val mid = (x(index - 1) + x(index)) / 2
                    path.cubicTo(mid, y(week.minutesByDay[index - 1]), mid, y(minutes), x(index), y(minutes))
                }
                else -> path.lineTo(x(index), y(minutes))
            }
        }
        // Filled, the shading carries level to the widget's edges past the inset end points.
        val area = Path(path).apply {
            if (fill) lineTo(right, y(week.todayMinutes))
            lineTo(if (fill) right else x(last), bottom)
            lineTo(if (fill) left else x(0), bottom)
            if (fill) lineTo(left, y(week.minutesByDay.first()))
            close()
        }
        paint.color = ColorUtils.setAlphaComponent(colors.line, AreaAlpha)
        canvas.drawPath(area, paint)
    }

    if (week.hasHistory) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = AverageStrokeDp * density
        paint.color = colors.average
        if (colors.eInk || averageDashed) paint.pathEffect = DashPathEffect(floatArrayOf(DashDp * density, DashDp * density), 0f)
        canvas.drawLine(left, y(week.averageMinutes), right, y(week.averageMinutes), paint)
        paint.pathEffect = null
    }
    canvas.restore()

    // Filled, the widget's own edge is the frame.
    if (!fill) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = FrameStrokeDp * density
        paint.color = colors.frame
        canvas.drawPath(plot, paint)
    }

    if (showLabels) {
        // Centred on its grid line by the font's own metrics.
        val centre = (axis.fontMetrics.ascent + axis.fontMetrics.descent) / 2
        axisLabels.forEachIndexed { interval, text -> canvas.drawText(text, 0f, y(step * interval) - centre, axis) }
    }

    if (!bars) {
        val pastDots = dayWidth >= MinDotSpacingDp * density
        drawLineAndPoints(canvas, path, week, colors, density, style, pastDots, ::x, ::y)
    }

    if (showLabels) {
        day.textAlign = Paint.Align.CENTER
        labels.days.forEachIndexed { index, text ->
            if ((last - index) % every != 0) return@forEachIndexed
            val isToday = index == last
            day.color = if (isToday) colors.textPrimary else colors.textMuted
            day.isFakeBoldText = isToday
            // Kept whole at the chart's edges.
            val half = day.measureText(text) / 2
            canvas.drawText(text, x(index).coerceIn(half, widthPx - half), bottom + DayBaselineDp * density, day)
        }
    }
    return bitmap
}

private fun drawLineAndPoints(
    canvas: Canvas,
    path: Path,
    week: ReadingWeek,
    colors: ReadingChartColors,
    density: Float,
    style: WidgetChartStyle,
    pastDots: Boolean,
    x: (Int) -> Float,
    y: (Number) -> Float,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = LineStrokeDp * density
    paint.color = colors.line
    val curve = style == WidgetChartStyle.CURVE
    paint.strokeJoin = if (curve) Paint.Join.ROUND else Paint.Join.MITER
    paint.strokeCap = if (curve) Paint.Cap.ROUND else Paint.Cap.SQUARE
    canvas.drawPath(path, paint)

    val last = week.minutesByDay.lastIndex
    val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    // Too close together, past points would bead the line; today's point always shows.
    for (index in 0 until if (pastDots) last else 0) {
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
}

private fun ReadingWeek.scaleStep() = scaleStep(max(minutesByDay.max(), if (hasHistory) averageMinutes else 0))

private fun axisPaint(scaledDensity: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = Typeface.DEFAULT
    textSize = AxisSp * scaledDensity
}

/** Whether the day and hour labels draw: asked for, and the chart tall enough to hold them. */
internal fun chartLabelsShown(showAxisLabels: Boolean, heightPx: Int, density: Float): Boolean =
    showAxisLabels && heightPx >= MinLabelledHeightDp * density

/**
 * How far the plot box sits in from the chart's left and right edges, in px. The left inset is the hour-label gutter
 * (the widest label plus a gap) or, without labels, room for the first point; the widget pads its header by the same
 * amounts so the header text lines up with the box.
 */
internal fun plotInsets(week: ReadingWeek, showLabels: Boolean, density: Float, scaledDensity: Float): Pair<Float, Float> {
    val left = if (showLabels) {
        val axis = axisPaint(scaledDensity)
        val step = week.scaleStep()
        (0..Intervals).maxOf { axis.measureText(axisLabel(step * it)) } + AxisGapDp * density
    } else {
        PointRadiusInsetDp * density
    }
    return left to RightInsetDp * density
}

/** Every how many days a day label fits beside the next: 1 labels every day. */
internal fun dayLabelEvery(labelWidthPx: Float, dayWidthPx: Float, gapPx: Float): Int =
    ceil((labelWidthPx + gapPx) / dayWidthPx).toInt().coerceAtLeast(1)

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
internal const val MinCountedSessionSeconds = 60L
private const val SecondsPerMinute = 60L
private const val MinLabelledHeightDp = 64f
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
private const val PlotCornerDp = 6f
private const val AverageStrokeDp = 1.5f
private const val LineStrokeDp = 2f
private const val PastDotDp = 3f
private const val TodayDotDp = 5f
private const val DotStrokeDp = 1.5f
private const val DashDp = 4f
private const val AxisSp = 10f
private const val DaySp = 10f
private const val AreaAlpha = 31
private const val DayLabelGapDp = 6f
private const val MinDotSpacingDp = 12f
private const val BarWidthFraction = 0.56f
private const val BarRadiusDp = 3f
private const val PastBarAlpha = 150
