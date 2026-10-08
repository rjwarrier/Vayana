package com.vayana.app.wear

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.BookFileAvailability
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchBooksTest {
    @Test fun invalidPageCountsAreUnknownAndDoNotBlockOtherBooks() {
        val reading = book().copy(startedReadingAt = 10, readingPercent = .25f)
        val result = watchBooks(listOf(reading.copy(pageCount = -20),
            reading.copy(id = 2, syncId = "book-2", pageCount = 0),
            reading.copy(id = 3, syncId = "book-3")))
        assertEquals(listOf(null, null, 200), result.map { it.total })
        assertEquals(listOf(0, 0, 50), result.map { it.page })
    }
    private fun book(id: Long = 1) = Book(
        id = id, syncId = "book-$id", title = "Book $id", author = null, series = null,
        seriesNumber = null, description = null, coverPath = null, filePath = "",
        fileAvailability = BookFileAvailability.LOCAL, format = BookFormat.PHYSICAL,
        fileHash = "physical-$id", readingPercent = 0f, rating = 0f,
        createdAt = 1, updatedAt = 2, lastReadAt = null, lastLocator = null, pageCount = 200,
    )

    @Test fun includesEverySupportedSignOfReading() {
        val books = listOf(book(1).copy(startedReadingAt = 10), book(2).copy(readingPercent = .25f),
            book(3).copy(lastReadAt = 20), book(4).copy(totalReadingSeconds = 60))
        assertEquals(setOf("book-1", "book-2", "book-3", "book-4"), watchBooks(books).map { it.id }.toSet())
        assertEquals(50, watchBooks(books).first { it.id == "book-2" }.page)
    }

    @Test fun excludesUnreadFinishedInactiveAndDigitalBooks() {
        val reading = book().copy(startedReadingAt = 10)
        assertEquals(emptyList(), watchBooks(listOf(book(), reading.copy(finishedReadingAt = 20),
            reading.copy(readingPercent = 1f), reading.copy(readingDisposition = "PAUSED"),
            reading.copy(format = BookFormat.EPUB), reading.copy(format = BookFormat.OTHER_EBOOK))))
    }
}
