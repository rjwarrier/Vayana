package com.vayana.feature.reader

/**
 * How long the reader really takes, against the engine's fixed estimate. Every forward step they read adds the time it
 * took ([actualSeconds]) and the time the estimate gave it ([estimatedSeconds]); their ratio scales "time left".
 */
internal data class ReadingPaceTotals(val actualSeconds: Float = 0f, val estimatedSeconds: Float = 0f)

/** One step forward: [actualSeconds] taken to read what the estimate puts at [estimatedSeconds]. */
internal data class PaceSample(val actualSeconds: Float, val estimatedSeconds: Float)

internal object ReadingPace {
    /** Until this much reading has been measured, the fixed estimate is as good as any guess. */
    private const val MinEstimatedSeconds = 600f

    /** Older reading counts for less, so the estimate follows the reader as they get faster or the books get harder. */
    private const val KeepWhenAdding = 0.9995f

    private const val MinFactor = 0.4f
    private const val MaxFactor = 2.5f

    /** The multiplier for the engine's estimate: 1 until there is enough to go on. */
    fun factor(totals: ReadingPaceTotals): Float =
        if (totals.estimatedSeconds < MinEstimatedSeconds || totals.actualSeconds <= 0f) {
            1f
        } else {
            (totals.actualSeconds / totals.estimatedSeconds).coerceIn(MinFactor, MaxFactor)
        }

    fun add(totals: ReadingPaceTotals, sample: PaceSample) = ReadingPaceTotals(
        actualSeconds = totals.actualSeconds * KeepWhenAdding + sample.actualSeconds,
        estimatedSeconds = totals.estimatedSeconds * KeepWhenAdding + sample.estimatedSeconds,
    )

    /** [minutes] of the engine's estimate, as this reader would take; never rounds a real estimate down to nothing. */
    fun scaled(minutes: Int, factor: Float): Int =
        if (minutes <= 0) minutes else kotlin.math.ceil(minutes * factor).toInt().coerceAtLeast(1)
}

/**
 * Turns the book's "time left" readings into [PaceSample]s: the drop since the last reading is the estimate for what was
 * just read, the clock says how long it took. A jump (a chapter skip, search, or going back), a long gap (the screen
 * off, a phone call) and a flip faster than anyone reads are not reading, and give no sample.
 */
internal class ReadingPaceTracker(private val now: () -> Long) {
    private var lastLeft: Double? = null
    private var lastAt = 0L

    /** Forget the last position: the reader was away, or opened another book. */
    fun reset() {
        lastLeft = null
    }

    fun onRelocate(minutesLeft: Double?): PaceSample? {
        val at = now()
        val previous = lastLeft
        val previousAt = lastAt
        lastLeft = minutesLeft
        lastAt = at
        if (minutesLeft == null || previous == null) return null
        val estimatedSeconds = (previous - minutesLeft) * 60.0
        val actualSeconds = (at - previousAt) / 1000.0
        val plausible = estimatedSeconds >= MinStepSeconds && estimatedSeconds <= MaxStepSeconds &&
            actualSeconds >= MinGapSeconds && actualSeconds <= MaxGapSeconds &&
            // Under a fifth of the estimate is skimming or flipping, not reading.
            actualSeconds >= estimatedSeconds / FastestSpeedUp
        return if (plausible) PaceSample(actualSeconds.toFloat(), estimatedSeconds.toFloat()) else null
    }

    private companion object {
        const val MinStepSeconds = 1.0
        const val MaxStepSeconds = 180.0
        const val MinGapSeconds = 2.0
        const val MaxGapSeconds = 300.0
        const val FastestSpeedUp = 5.0
    }
}
