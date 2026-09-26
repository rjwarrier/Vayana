package com.vayana.feature.library

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReadNextTest {

    @Test
    fun suggestsTheNextUnreadBookInTheCurrentSeries() {
        val current = book(2, "Caliban's War", series = "The Expanse", number = "2", reading = true)
        val books = listOf(
            book(1, "Leviathan Wakes", series = "The Expanse", number = "1", finished = true),
            current,
            book(4, "Cibola Burn", series = "The Expanse", number = "4"),
            book(3, "Abaddon's Gate", series = "The Expanse", number = "3"),
        )

        assertEquals(listOf(3L), suggestedReadNext(books, current).map { it.id })
    }

    @Test
    fun skipsFinishedAndPhysicalBooksAndMatchesSeriesLoosely() {
        val current = book(2, "Caliban's War", series = "The Expanse", number = "2", reading = true)
        val books = listOf(
            current,
            book(3, "Abaddon's Gate", series = "the expanse ", number = "3", finished = true),
            book(4, "Cibola Burn", series = " The Expanse", number = "4", format = BookFormat.PHYSICAL),
            book(5, "Nemesis Games", series = "THE EXPANSE", number = "5"),
        )

        assertEquals(listOf(5L), suggestedReadNext(books, current).map { it.id })
    }

    @Test
    fun ordersFractionalSeriesNumbers() {
        val current = book(2, "Caliban's War", series = "The Expanse", number = "2", reading = true)
        val books = listOf(
            current,
            book(3, "Abaddon's Gate", series = "The Expanse", number = "3"),
            book(9, "The Churn", series = "The Expanse", number = "2.5"),
        )

        assertEquals(listOf(9L), suggestedReadNext(books, current).map { it.id })
    }

    @Test
    fun fallsBackToTheSameAuthorOnceTheSeriesRunsOut() {
        val current = book(9, "Leviathan Falls", author = "James S. A. Corey", series = "The Expanse", number = "9", reading = true)
        val books = listOf(
            current,
            book(1, "Leviathan Wakes", author = "James S. A. Corey", series = "The Expanse", number = "1"),
            book(20, "The Mercy of Gods", author = "james s. a. corey", series = "The Captive's War", number = "1"),
            book(30, "Unrelated", author = "Someone Else"),
        )

        assertEquals(listOf(20L), suggestedReadNext(books, current).map { it.id })
    }

    @Test
    fun authorFallbackUsesSeriesNumberThenTitleOrdering() {
        val current = book(
            9,
            "Last Current Book",
            author = "Author",
            series = "Finished Series",
            number = "9",
            reading = true,
        )
        val books = listOf(
            current,
            book(30, "Later", author = "Author", series = "Other Series", number = "2"),
            book(20, "Zulu", author = "Author", series = "Other Series", number = "1"),
            book(10, "Alpha", author = "Author", series = "Other Series", number = "1"),
        )

        assertEquals(listOf(10L), suggestedReadNext(books, current).map { it.id })
    }

    @Test
    fun noSuggestionWithoutAnActiveSeriesBook() {
        val notStarted = book(1, "Leviathan Wakes", series = "The Expanse", number = "1")
        val finished = book(2, "Caliban's War", series = "The Expanse", number = "2", reading = true, finished = true)
        val standalone = book(3, "Standalone", reading = true)
        val books = listOf(notStarted, finished, standalone, book(4, "Cibola Burn", series = "The Expanse", number = "4"))

        assertEquals(emptyList(), suggestedReadNext(books, null))
        assertEquals(emptyList(), suggestedReadNext(books, notStarted))
        assertEquals(emptyList(), suggestedReadNext(books, finished))
        assertEquals(emptyList(), suggestedReadNext(books, standalone))
    }

    @Test
    fun warnsWhenQueueingABookOutsideTheSeriesBeingRead() {
        val current = book(2, "Caliban's War", series = "The Expanse", number = "2", reading = true)
        val next = book(3, "Abaddon's Gate", series = "The Expanse", number = "3")
        val other = book(10, "Piranesi")
        val books = listOf(current, next, other)

        val warning = books.readNextSeriesBreakWarningFor(other)

        assertEquals(current, warning?.currentBook)
        assertEquals("2", warning?.currentBook?.seriesNumber)
        assertEquals(next, warning?.nextBook)
        assertEquals(other, warning?.queuedBook)
    }

    @Test
    fun noWarningWhenQueueingWithinTheSeriesOrWhenNothingIsNext() {
        val current = book(2, "Caliban's War", series = "The Expanse", number = "2", reading = true)
        val next = book(3, "Abaddon's Gate", series = "The Expanse", number = "3")
        val other = book(10, "Piranesi")

        assertNull(listOf(current, next, other).readNextSeriesBreakWarningFor(next))
        assertNull(listOf(current, other).readNextSeriesBreakWarningFor(other))
        assertNull(listOf(current, next.copy(finishedReadingAt = 5L), other).readNextSeriesBreakWarningFor(other))
    }

    private fun book(
        id: Long,
        title: String,
        author: String? = null,
        series: String? = null,
        number: String? = null,
        reading: Boolean = false,
        finished: Boolean = false,
        format: BookFormat = BookFormat.EPUB,
    ) = Book(
        id = id,
        syncId = "book-$id",
        title = title,
        author = author,
        series = series,
        seriesNumber = number,
        description = null,
        coverPath = null,
        filePath = "",
        fileAvailability = BookFileAvailability.LOCAL,
        format = format,
        fileHash = "hash-$id",
        readingPercent = if (reading) 0.3f else 0f,
        rating = 0f,
        createdAt = 0L,
        updatedAt = 0L,
        lastReadAt = if (reading) 1_000L + id else null,
        lastLocator = null,
        finishedReadingAt = if (finished) 2_000L else null,
    )
}
