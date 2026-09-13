package com.vayana.feature.search

import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchResultsTest {

    @Test
    fun keepsDatabaseOrderAndLabelsEveryMatchedField() {
        val rings = book(1, "The Lord of the Rings", author = "J. R. R. Tolkien")
        val hobbit = book(2, "The Hobbit", author = "J. R. R. Tolkien", tags = "fantasy, classic")

        val results = searchResults("tolk ring", bookIds = listOf(2, 1), annotations = emptyList(), books = listOf(rings, hobbit))

        assertEquals("tolk ring", results.query)
        assertEquals(listOf(2L, 1L), results.books.map { it.book.id })
        assertEquals(listOf(SearchMatchedField.AUTHOR), results.books[0].matchedFields)
        assertEquals(listOf(SearchMatchedField.TITLE, SearchMatchedField.AUTHOR), results.books[1].matchedFields)
    }

    @Test
    fun dropsRowsWhoseBookIsNoLongerInTheLibrary() {
        val rings = book(1, "The Lord of the Rings")
        val orphan = annotation(id = 10, bookId = 99, text = "Not all those who wander are lost")
        val kept = annotation(id = 11, bookId = 1, text = "All we have to decide", note = "Gandalf on time", chapter = "The Shadow of the Past")

        val results = searchResults("gandalf", bookIds = listOf(99, 1), annotations = listOf(orphan, kept), books = listOf(rings))

        assertEquals(listOf(1L), results.books.map { it.book.id })
        assertEquals(listOf(11L), results.annotations.map { it.annotation.id })
        assertEquals(rings, results.annotations.single().book)
        assertEquals(listOf(SearchMatchedField.NOTE), results.annotations.single().matchedFields)
    }

    @Test
    fun matchesSeriesNumberAndTagsOnWordPrefixes() {
        val book = book(1, "Abaddon's Gate", series = "The Expanse", number = "3", tags = "space opera")

        val results = searchResults("expan 3 oper", bookIds = listOf(1), annotations = emptyList(), books = listOf(book))

        assertEquals(listOf(SearchMatchedField.SERIES, SearchMatchedField.TAGS), results.books.single().matchedFields)
    }

    private fun book(
        id: Long,
        title: String,
        author: String? = null,
        series: String? = null,
        number: String? = null,
        tags: String? = null,
    ) = Book(
        id = id,
        syncId = "book-$id",
        title = title,
        author = author,
        series = series,
        seriesNumber = number,
        description = null,
        tagsCsv = tags,
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

    private fun annotation(id: Long, bookId: Long, text: String, note: String? = null, chapter: String? = null) = Annotation(
        id = id,
        bookId = bookId,
        type = AnnotationType.entries.first(),
        colorKey = "yellow",
        locator = "epubcfi(/6/2)",
        chapterTitle = chapter,
        chapterHref = null,
        selectedText = text,
        readerNote = note,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
