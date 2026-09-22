package com.vayana.feature.library

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryStatsShareModelTest {
    @Test
    fun countsUseTheWholeLibraryAndNormalizeAuthorNames() {
        val snapshot = libraryStatsSnapshot(listOf(
            book(2, " Ursula K. Le Guin ", 0f),
            book(1, "ursula k. le guin", 1f),
            book(3, null, 0.5f).copy(finishedReadingAt = 42L),
        ))
        assertEquals(3, snapshot.bookCount)
        assertEquals(1, snapshot.authorCount)
        assertEquals(2, snapshot.readCount)
        assertEquals(listOf(1L, 2L, 3L), snapshot.books.map { it.id })
    }

    @Test
    fun smallLibraryGetsOneSpinePerBookWithActualReadStates() {
        val snapshot = LibraryStatsSnapshot(
            listOf(LibraryStatsBook(1, 1, null, true), LibraryStatsBook(2, 2, null, false)), 0, 1)
        val spines = librarySpines(snapshot, LibraryShareFormat.STORY, colorCount = 6)
        assertTrue(spines.representsEveryBook)
        assertEquals(listOf(true, false), spines.spines.map { it.read })
    }

    @Test
    fun posterDistributesTwentyNineBooksAcrossAllThreeShelves() {
        val books = (1L..29L).map { LibraryStatsBook(it, it, null, it <= 3L) }
        val snapshot = LibraryStatsSnapshot(books, 0, 3)
        val story = librarySpines(snapshot, LibraryShareFormat.STORY, colorCount = 6)
        assertTrue(story.representsEveryBook)
        assertEquals(29, story.spines.size)
        assertEquals(setOf(0, 1, 2), story.spines.map { it.shelf }.toSet())
        assertTrue(story.spines.groupingBy { it.shelf }.eachCount().values.all { it in 9..10 })
        assertTrue(story.spines.all { it.x >= 0f && it.x + it.width <= 360f })
        val square = librarySpines(snapshot, LibraryShareFormat.SQUARE, colorCount = 6)
        assertTrue(square.representsEveryBook)
        assertEquals(29, square.spines.size)
    }

    @Test
    fun largeLibraryUsesStableSampleWithinShelves() {
        val books = (1L..200L).map { LibraryStatsBook(it, it, null, it <= 100L) }
        val snapshot = LibraryStatsSnapshot(books, 0, 100)
        val first = librarySpines(snapshot, LibraryShareFormat.SQUARE, colorCount = 6)
        val second = librarySpines(snapshot, LibraryShareFormat.SQUARE, colorCount = 6)
        assertFalse(first.representsEveryBook)
        assertEquals(first, second)
        assertTrue(first.spines.isNotEmpty())
        assertTrue(first.spines.all { it.shelf == 0 && it.x + it.width <= 408f })
    }

    @Test
    fun progressPercentRoundsToNearestWholeNumber() {
        val books = (1L..248L).map { LibraryStatsBook(it, it, null, it <= 171L) }
        assertEquals(69, LibraryStatsSnapshot(books, 0, 171).readPercent)
    }

    private fun book(id: Long, author: String?, progress: Float) = Book(
        id = id, syncId = "sync-$id", title = "Book $id", author = author, series = null,
        seriesNumber = null, description = null, coverPath = null, filePath = "books/$id.epub",
        fileAvailability = BookFileAvailability.LOCAL, format = BookFormat.EPUB, fileHash = "hash-$id",
        readingPercent = progress, rating = 0f, createdAt = id, updatedAt = id,
        lastReadAt = null, lastLocator = null,
    )
}
