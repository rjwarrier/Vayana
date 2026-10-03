package com.vayana.feature.library

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import com.vayana.core.datastore.settings.SmartShelf
import com.vayana.core.datastore.settings.SmartShelfStatus
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryReadingToolsTest {
    private val today = LocalDate.of(2026, 10, 3)
    private val day = 86_400_000L
    private fun book() = Book(id = 1, syncId = "stable", title = "Earthsea", author = "Ursula Le Guin",
        series = null, seriesNumber = null, description = "Fantasy", tagsCsv = "Favorite, Fiction",
        coverPath = null, filePath = "", fileAvailability = BookFileAvailability.LOCAL, format = BookFormat.EPUB,
        fileHash = "hash", readingPercent = .25f, rating = 0f, createdAt = 0, updatedAt = 0,
        lastReadAt = day, lastLocator = null)

    @Test fun shelfCombinesCriteriaAndTracksProgressAndNotes() {
        val rule = SmartShelf(name = "Stalled fantasy", query = "fantasy", author = "le guin", tag = "favorite",
            status = SmartShelfStatus.READING, dormantDays = 7, withNotes = true)
        assertTrue(rule.matches(book(), setOf(1), .95f, 8 * day))
        assertFalse(rule.matches(book(), emptySet(), .95f, 8 * day))
        assertFalse(rule.matches(book(), setOf(1), .95f, 8 * day - 1))
        assertFalse(rule.matches(book().copy(readingPercent = .96f), setOf(1), .95f, 8 * day))
        assertTrue(rule.matches(book().copy(readingPercent = .96f), setOf(1), .99f, 8 * day))
        assertFalse(rule.matches(book().copy(tagsCsv = "NotFavorite"), setOf(1), .95f, 8 * day))
    }

    @Test fun neverOpenedBooksAreUnreadButNotDormant() {
        val unread = book().copy(readingPercent = 0f, lastReadAt = null)
        assertTrue(SmartShelf(name = "Unread", status = SmartShelfStatus.UNREAD).matches(unread, emptySet(), .95f, day))
        assertFalse(SmartShelf(name = "Dormant", dormantDays = 1).matches(unread, emptySet(), .95f, day))
    }

    @Test fun pagePlansIncludeTodayAndRoundUp() {
        assertEquals(FinishByPlan(3, 100, PlanUnit.PAGES), finishByPlan(book().copy(pageCount = 400), today.plusDays(2), today))
        assertEquals(FinishByPlan(1, 300, PlanUnit.PAGES), finishByPlan(book().copy(pageCount = 400), today, today))
    }

    @Test fun shelfStatusesHonorExplicitDatesEvenWithoutPageProgress() {
        val reading = book().copy(readingPercent = 0f, startedReadingAt = 10)
        val finished = reading.copy(finishedReadingAt = 20)
        assertTrue(SmartShelf(name = "Started", status = SmartShelfStatus.READING).matches(reading, emptySet(), .95f, day))
        assertFalse(SmartShelf(name = "Unread", status = SmartShelfStatus.UNREAD).matches(reading, emptySet(), .95f, day))
        assertTrue(SmartShelf(name = "Finished", status = SmartShelfStatus.FINISHED).matches(finished, emptySet(), .95f, day))
    }

    @Test fun timeEstimateRequiresMeasuredProgressAndFallsBackToPercent() {
        assertEquals(FinishByPlan(3, 60, PlanUnit.MINUTES), finishByPlan(book().copy(totalReadingSeconds = 3600), today.plusDays(2), today))
        assertEquals(FinishByPlan(3, 25, PlanUnit.PERCENT), finishByPlan(book(), today.plusDays(2), today))
        assertEquals(PlanUnit.PERCENT, finishByPlan(book().copy(readingPercent = 0f, totalReadingSeconds = 3600), today, today).unit)
    }

    @Test fun finishedAndPastTargetsHaveExplicitStates() {
        assertEquals(PlanUnit.OVERDUE, finishByPlan(book(), today.minusDays(1), today).unit)
        assertEquals(PlanUnit.FINISHED, finishByPlan(book().copy(finishedReadingAt = 10), today.minusDays(1), today).unit)
    }
}
