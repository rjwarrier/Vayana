package com.vayana.core.database.repository

import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.max

/** How a saved word went in review. */
enum class ReviewGrade { AGAIN, GOOD, EASY }

/** A card's spacing after a review: when it's next due and what the following review builds on. */
data class ReviewSchedule(
    val repetitions: Int,
    val intervalDays: Int,
    val easeFactor: Float,
    val dueAt: Long,
    /** Spaced far enough apart to count as learned (Statistics' "mastered" share). */
    val known: Boolean,
)

/**
 * SM-2 style spaced repetition, simplified to three answers. A word got right comes back after 1 day, then 3, then
 * a growing gap scaled by its ease; "easy" jumps further and raises the ease, "again" brings it back in a few
 * minutes and lowers the ease. Pure, so it can be tested and synced as plain numbers.
 */
object VocabularySchedule {
    const val DefaultEase = 2.5f
    const val MinEase = 1.3f
    const val MasteredIntervalDays = 21
    private const val MaxIntervalDays = 3_650
    private const val EaseStep = 0.15f
    private const val AgainEasePenalty = 0.2f
    private const val EasyBonus = 1.3
    private val AgainDelayMillis = TimeUnit.MINUTES.toMillis(10)

    fun next(repetitions: Int, intervalDays: Int, easeFactor: Float, grade: ReviewGrade, now: Long): ReviewSchedule {
        val ease = easeFactor.takeIf { it.isFinite() }?.coerceAtLeast(MinEase) ?: DefaultEase
        if (grade == ReviewGrade.AGAIN) {
            return ReviewSchedule(
                repetitions = 0,
                intervalDays = 0,
                easeFactor = max(MinEase, ease - AgainEasePenalty),
                dueAt = now + AgainDelayMillis,
                known = false,
            )
        }
        val nextRepetitions = repetitions.coerceAtLeast(0) + 1
        val previousInterval = intervalDays.coerceAtLeast(0)
        val (interval, nextEase) = when (grade) {
            ReviewGrade.GOOD -> when (nextRepetitions) {
                1 -> 1
                2 -> 3
                else -> max(previousInterval + 1, ceil(previousInterval * ease.toDouble()).toInt())
            } to ease
            else -> when (nextRepetitions) {
                1 -> 4
                else -> max(previousInterval + 1, ceil(max(previousInterval, 1) * ease.toDouble() * EasyBonus).toInt())
            } to ease + EaseStep
        }
        val cappedInterval = interval.coerceAtMost(MaxIntervalDays)
        return ReviewSchedule(
            repetitions = nextRepetitions,
            intervalDays = cappedInterval,
            easeFactor = nextEase,
            dueAt = now + TimeUnit.DAYS.toMillis(cappedInterval.toLong()),
            known = cappedInterval >= MasteredIntervalDays,
        )
    }
}
