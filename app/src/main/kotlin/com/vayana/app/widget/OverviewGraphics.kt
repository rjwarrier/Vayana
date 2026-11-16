package com.vayana.app.widget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Colours for [drawYearRing] and [drawBookMeter], resolved from the app's theme. */
internal class OverviewGraphicsColors(val filled: Int, val due: Int, val track: Int)

/**
 * A 5x7 dot-matrix pixel font: digits and a few punctuation marks at full height, a handful of unit letters smaller
 * and baseline-aligned, as a widget's own hero numerals are drawn rather than set in a bundled font.
 */
internal object DotMatrixGlyphs {
    private fun g(vararg rows: String) = rows.joinToString("")
    private val full = mapOf(
        '0' to "01110100011001110101110011000101110", '1' to "00100011000010000100001000010001110",
        '2' to "01110100010000100010001000100011111", '3' to "11111000100010000010000011000101110",
        '4' to "00010001100101010010111110001000010", '5' to "11111100001111000001000011000101110",
        '6' to "00110010001000011110100011000101110", '7' to "11111000010001000100010000100001000",
        '8' to "01110100011000101110100011000101110", '9' to "01110100011000101111000010001001100",
        '.' to g("00000", "00000", "00000", "00000", "00000", "01100", "01100"),
        ':' to g("00000", "01100", "01100", "00000", "01100", "01100", "00000"),
        '-' to g("00000", "00000", "00000", "11111", "00000", "00000", "00000"),
        '+' to g("00000", "00100", "00100", "11111", "00100", "00100", "00000"),
        ' ' to g("00000", "00000", "00000", "00000", "00000", "00000", "00000"),
    )
    private val small = mapOf(
        '%' to "11000110010001000100010001001100011",
        'h' to g("10000", "10000", "10110", "11001", "10001", "10001", "10001"),
        'm' to g("00000", "00000", "11010", "10101", "10101", "10101", "10101"),
        'd' to g("00001", "00001", "01101", "10011", "10001", "10011", "01101"),
    )
    /** The pattern for [ch], and whether it is drawn small; null for a character the font does not have. */
    fun of(ch: Char): Pair<String, Boolean>? = full[ch]?.let { it to false } ?: small[ch]?.let { it to true }
}

/** The width a dot-matrix string draws at, for [heightPx]-tall digits at full-height column spacing [columnPx]. */
private fun dotTextWidth(text: String, columnPx: Float, smallColumnPx: Float): Float {
    var width = 0f
    for (ch in text) {
        width += if (ch == ' ') {
            SpaceColumns * columnPx
        } else {
            val (_, isSmall) = DotMatrixGlyphs.of(ch) ?: continue
            GlyphColumns * (if (isSmall) smallColumnPx else columnPx)
        }
    }
    return (width - columnPx).coerceAtLeast(1f)
}

/**
 * A hero numeral drawn as dots rather than set in a font: [text]'s digits and punctuation fill [heightPx], with any
 * unit letters ([DotMatrixGlyphs]'s small set) shrunk and sitting on the same baseline, as the widget's own number
 * display always draws them. [dotWeight] (100-900) is how much of each cell's pitch the dot fills.
 */
internal fun drawDotText(text: String, heightPx: Int, color: Int, dotWeight: Int = DefaultDotWeight): android.graphics.Bitmap {
    val pitch = heightPx / GlyphRows.toFloat()
    val smallPitch = pitch * SmallScale
    val width = dotTextWidth(text, pitch, smallPitch).roundToInt().coerceAtLeast(1)
    val bitmap = createBitmap(width, heightPx.coerceAtLeast(1))
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    val dot = MinDotFraction + (dotWeight - 100) / 800f * (MaxDotFraction - MinDotFraction)
    var x = 0f
    for (ch in text) {
        if (ch == ' ') {
            x += SpaceColumns * pitch
            continue
        }
        val (pattern, isSmall) = DotMatrixGlyphs.of(ch) ?: continue
        val cell = if (isSmall) smallPitch else pitch
        val y0 = if (isSmall) heightPx - GlyphRows * cell else 0f
        for (row in 0 until GlyphRows) for (col in 0 until GlyphColumns) if (pattern[row * GlyphColumns + col] == '1') {
            canvas.drawCircle(x + (col + 0.5f) * cell, y0 + (row + 0.5f) * cell, cell * dot, paint)
        }
        x += GlyphColumns * cell
    }
    return bitmap
}

/**
 * A dial made of [segments] dots, one per book in the yearly goal, evenly spaced around the circle: [filledCount] are
 * read (bright), the next [dueCount] are due by today at an even pace but not read yet (the accent colour - how far
 * behind pace shows), the rest aren't due yet (muted). Capped at [MaxRingDots] dots; a larger goal just groups into
 * that many even slices.
 */
internal fun drawYearRing(sizePx: Int, segments: Int, filledCount: Int, dueCount: Int, colors: OverviewGraphicsColors, density: Float) =
    createBitmap(sizePx.coerceAtLeast(1), sizePx.coerceAtLeast(1)).also { bitmap ->
        val canvas = Canvas(bitmap)
        val dots = segments.coerceIn(1, MaxRingDots)
        val scale = segments.toFloat() / dots
        val dotRadius = RingDotRadiusDp * density
        val center = sizePx / 2f
        val ringRadius = center - dotRadius - RingInsetDp * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until dots) {
            val angle = Math.toRadians((RingStartAngle + i * 360f / dots).toDouble())
            val cx = center + ringRadius * cos(angle).toFloat()
            val cy = center + ringRadius * sin(angle).toFloat()
            val readSoFar = (i.toFloat() * scale) < filledCount
            val dueByNow = (i.toFloat() * scale) < dueCount
            paint.color = if (readSoFar) colors.filled else if (dueByNow) colors.due else colors.track
            canvas.drawCircle(cx, cy, dotRadius, paint)
        }
    }

/** A row of [segments] short bars - a book's position, [filledFraction] of the way through. */
internal fun drawBookMeter(widthPx: Int, heightPx: Int, segments: Int, filledFraction: Float, colors: OverviewGraphicsColors, density: Float) =
    createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1)).also { bitmap ->
        val canvas = Canvas(bitmap)
        val gap = MeterGapDp * density
        val segment = (widthPx - gap * (segments - 1)) / segments
        val filled = (segments * filledFraction.coerceIn(0f, 1f)).roundToInt()
        val radius = minOf(segment / 2, MeterCornerDp * density)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until segments) {
            paint.color = if (i < filled) colors.filled else colors.track
            val left = i * (segment + gap)
            canvas.drawRoundRect(RectF(left, 0f, left + segment, heightPx.toFloat()), radius, radius, paint)
        }
    }

private const val GlyphRows = 7
private const val GlyphColumns = 5
private const val SpaceColumns = 3f
private const val SmallScale = 0.56f
private const val MinDotFraction = 0.30f
private const val MaxDotFraction = 0.50f
private const val DefaultDotWeight = 500
private const val MaxRingDots = 32
private const val RingDotRadiusDp = 3.5f
private const val RingInsetDp = 1f
private const val RingStartAngle = -90f
private const val MeterGapDp = 2f
private const val MeterCornerDp = 2f
