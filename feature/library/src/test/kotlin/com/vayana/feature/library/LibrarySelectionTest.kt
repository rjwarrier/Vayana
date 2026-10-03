package com.vayana.feature.library

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LibrarySelectionTest {
    @Test
    fun presentationChangesReuseSelectionButQuerySortAndProgressRemainLive() {
        val selector = LibrarySelection()
        val books = listOf(book(1, "Zebra"), book(2, "Apple"), book(3, "Physical", BookFormat.PHYSICAL))
        val controls = LibraryControls(sort = LibrarySort.TITLE, sortDirection = LibrarySortDirection.ASCENDING)
        val first = selector.select(books, controls, .95f)
        assertEquals(listOf(2L, 1L), first.map { it.id })
        assertSame(first, selector.select(books, controls.copy(viewMode = LibraryViewMode.LIST, groupBy = LibraryGroupBy.AUTHOR), .95f))
        assertEquals(listOf(1L), selector.select(books, controls.copy(query = "zebra"), .95f).map { it.id })
        assertEquals(listOf(1L, 2L), selector.select(books, controls.copy(sortDirection = LibrarySortDirection.DESCENDING), .95f).map { it.id })
        val updated = books.map { if (it.id == 1L) it.copy(readingPercent = .96f) else it }
        assertEquals(listOf(1L), selector.select(updated, controls.copy(filter = LibraryFilter.FINISHED), .95f).map { it.id })
        assertEquals(emptyList(), selector.select(updated, controls.copy(filter = LibraryFilter.FINISHED), .99f))
    }

    private fun book(id: Long, title: String, format: BookFormat = BookFormat.EPUB) = Book(
        id = id, syncId = "$id", title = title, author = null, series = null, seriesNumber = null,
        description = null, coverPath = null, filePath = "", fileAvailability = BookFileAvailability.LOCAL,
        format = format, fileHash = "$id", readingPercent = 0f, rating = 0f, createdAt = id, updatedAt = id,
        lastReadAt = null, lastLocator = null,
    )
}
