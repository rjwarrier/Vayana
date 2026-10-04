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
import kotlin.test.assertSame
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class LibraryReadingToolsTest {
    private val today = LocalDate.of(2026, 10, 3)
    private val day = 86_400_000L

    @Test fun emptyRulesDoNotSubscribeToLibraryNotesThresholdOrClock() = runBlocking {
        val result = observeSmartShelfItems(
            books = flow { error("Unneeded books subscription") }, definitions = flowOf(emptyList()),
            notes = { error("Unneeded notes query") }, threshold = flow { error("Unneeded threshold subscription") },
            clock = flow { error("Unneeded clock subscription") },
        ).first()
        assertTrue(result.isEmpty())
    }

    @Test fun ordinaryRulesDoNotQueryNotesOrStartRefreshClock() = runBlocking {
        val rule = SmartShelf(id = "all", name = "All")
        val result = observeSmartShelfItems(flowOf(listOf(book())), flowOf(listOf(rule)),
            notes = { error("Unneeded notes query") }, threshold = flowOf(.95f),
            clock = flow { error("Unneeded clock subscription") }).first()
        assertEquals(listOf(SmartShelfItem(rule, listOf(book()))), result)
    }

    @Test fun timedRulesRefreshMembershipAndReuseOrdinaryShelfResults() = runBlocking {
        val ordinary = SmartShelf(id = "ordinary", name = "All")
        val dormant = SmartShelf(id = "dormant", name = "Dormant", dormantDays = 7)
        val result = observeSmartShelfItems(flowOf(listOf(book())), flowOf(listOf(ordinary, dormant)),
            notes = { error("Unneeded notes query") }, threshold = flowOf(.95f),
            clock = flowOf(8 * day - 1, 8 * day, 8 * day + 1)).toList()
        assertEquals(2, result.size)
        assertTrue(result.first()[1].books.isEmpty())
        assertEquals(listOf(book()), result.last()[1].books)
        assertSame(result.first()[0], result.last()[0])
        assertSame(result.first()[0].books, result.last()[0].books)
    }

    @Test fun noteRulesStillSubscribeAndFilterMembership() = runBlocking {
        var queried = 0
        val rule = SmartShelf(name = "Notes", withNotes = true)
        val result = observeSmartShelfItems(flowOf(listOf(book(), book().copy(id = 2))), flowOf(listOf(rule)),
            notes = { queried++; flowOf(setOf(2L)) }, threshold = flowOf(.95f), clock = flowOf(day)).first()
        assertEquals(1, queried)
        assertEquals(listOf(2L), result.single().books.map { it.id })
    }

    @Test fun editingRulesStartsAndStopsTheNotesSubscription() = runBlocking {
        withTimeout(5_000) {
            val ordinary = SmartShelf(id = "rule", name = "Books")
            val rules = MutableStateFlow(listOf(ordinary))
            val output = Channel<List<SmartShelfItem>>(Channel.UNLIMITED)
            var activeNotesSubscriptions = 0
            val collector = launch {
                observeSmartShelfItems(flowOf(listOf(book(), book().copy(id = 2))), rules,
                    notes = { flow {
                        activeNotesSubscriptions++
                        try { emit(setOf(2L)); awaitCancellation() }
                        finally { activeNotesSubscriptions-- }
                    } }, threshold = flowOf(.95f), clock = flow { error("Unneeded clock") })
                    .collect { output.send(it) }
            }
            try {
                assertEquals(2, output.receive().single().books.size)
                assertEquals(0, activeNotesSubscriptions)
                rules.value = listOf(ordinary.copy(withNotes = true))
                assertEquals(listOf(2L), output.receive().single().books.map { it.id })
                assertEquals(1, activeNotesSubscriptions)
                rules.value = listOf(ordinary)
                assertEquals(2, output.receive().single().books.size)
                assertEquals(0, activeNotesSubscriptions)
            } finally { collector.cancelAndJoin(); output.close() }
        }
    }
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
