package com.vayana.feature.library

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SeriesFoldersTest {
    @Test
    fun groupsOnlyMultiBookSeriesAndKeepsLibraryPosition() {
        val books = listOf(
            book(1, "Standalone"),
            book(3, "Third", "The Expanse", "3"),
            book(4, "Solo series", "Another series", "1"),
            book(2, "Second", " the expanse ", "2"),
        )

        val items = seriesLibraryItems(books)

        assertEquals(3, items.size)
        assertEquals(1L, assertIs<SeriesLibraryItem.Single>(items[0]).book.id)
        assertEquals(listOf(2L, 3L), assertIs<SeriesLibraryItem.Folder>(items[1]).books.map { it.id })
        assertEquals("the expanse", assertIs<SeriesLibraryItem.Folder>(items[1]).key)
        assertEquals(4L, assertIs<SeriesLibraryItem.Single>(items[2]).book.id)
    }

    @Test
    fun ordersFractionalNumbersBeforeUnnumberedBooks() {
        val books = listOf(
            book(4, "Unknown", "Series", null),
            book(3, "Three", "Series", "3"),
            book(2, "Novella", "Series", "2.5"),
            book(1, "Two", "Series", "2"),
        )

        assertEquals(listOf(1L, 2L, 3L, 4L), books.sortedForSeries().map { it.id })
    }

    private fun book(id: Long, title: String, series: String? = null, number: String? = null) = Book(
        id = id,
        syncId = "book-$id",
        title = title,
        author = null,
        series = series,
        seriesNumber = number,
        description = null,
        coverPath = null,
        filePath = "",
        fileAvailability = BookFileAvailability.LOCAL,
        format = BookFormat.EPUB,
        fileHash = "hash-$id",
        readingPercent = 0f,
        rating = 0f,
        createdAt = 0L,
        updatedAt = 0L,
        lastReadAt = null,
        lastLocator = null,
    )
}
