package com.vayana.core.database.repository

import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VocabularyScheduleTest {
    private val now = 1_000_000L

    @Test
    fun goodAnswersSpreadReviewsOut() {
        val first = VocabularySchedule.next(0, 0, 2.5f, ReviewGrade.GOOD, now)
        assertEquals(1, first.intervalDays)
        assertEquals(now + TimeUnit.DAYS.toMillis(1), first.dueAt)

        val second = VocabularySchedule.next(first.repetitions, first.intervalDays, first.easeFactor, ReviewGrade.GOOD, now)
        assertEquals(3, second.intervalDays)

        val third = VocabularySchedule.next(second.repetitions, second.intervalDays, second.easeFactor, ReviewGrade.GOOD, now)
        assertEquals(8, third.intervalDays)
        assertFalse(third.known)
    }

    @Test
    fun againStartsOverSoonAndLowersEase() {
        val schedule = VocabularySchedule.next(4, 20, 2.5f, ReviewGrade.AGAIN, now)
        assertEquals(0, schedule.repetitions)
        assertEquals(0, schedule.intervalDays)
        assertEquals(2.3f, schedule.easeFactor, 0.001f)
        assertEquals(now + TimeUnit.MINUTES.toMillis(10), schedule.dueAt)
        assertFalse(schedule.known)
    }

    @Test
    fun easyJumpsFurtherAndRaisesEase() {
        val schedule = VocabularySchedule.next(0, 0, 2.5f, ReviewGrade.EASY, now)
        assertEquals(4, schedule.intervalDays)
        assertEquals(2.65f, schedule.easeFactor, 0.001f)
    }

    @Test
    fun longIntervalsCountAsKnown() {
        val schedule = VocabularySchedule.next(5, 10, 2.5f, ReviewGrade.GOOD, now)
        assertEquals(25, schedule.intervalDays)
        assertTrue(schedule.known)
    }

    @Test
    fun badEaseFallsBackToDefault() {
        val schedule = VocabularySchedule.next(2, 3, Float.NaN, ReviewGrade.GOOD, now)
        assertEquals(VocabularySchedule.DefaultEase, schedule.easeFactor)
    }
}
