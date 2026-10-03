package com.vayana.feature.notes

import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NotesMarkdownExportTest {
    @Test
    fun chaptersQuotesNotesAndJournalBodiesSurviveExport() {
        val output = highlightsMarkdown(book(), listOf(
            annotation(text = "First\nsecond", note = "My thought #idea", chapter = "Chapter 1"),
            annotation(text = "", note = "Where I stopped #journal", chapter = "Reading journal", type = AnnotationType.NOTE),
            annotation(text = "", note = "bookmark omitted", chapter = null, type = AnnotationType.BOOKMARK),
            annotation(text = "deleted quote", note = null, chapter = null).copy(isDeleted = true),
        ))

        assertTrue(output.contains("# Book\n*Author*"))
        assertTrue(output.contains("## Chapter 1\n\n> First\n> second\n\nMy thought #idea"))
        assertTrue(output.contains("## Reading journal\n\nWhere I stopped #journal"))
        assertTrue(output.contains("tags: [\"fiction\", \"idea\", \"journal\"]"))
        assertFalse(output.contains("bookmark omitted"))
        assertFalse(output.contains("deleted quote"))
    }

    @Test
    fun metadataCannotBreakOutOfFrontMatter() {
        val output = highlightsMarkdown(book().copy(title = "Quoted \"book\"\n---\nother: true", author = "A\\B\tC"), emptyList())
        assertTrue(output.startsWith("---\ntitle: \"Quoted \\\"book\\\"\\n---\\nother: true\"\nauthor: \"A\\\\B\\tC\""))
        assertTrue(output.contains("# Quoted \"book\" --- other: true"))
    }

    @Test
    fun filenamesRemainDistinctAndCannotTraverseDirectories() {
        val unsafe = book().copy(title = "../../\\same:*?", id = 1)
        val collision = book().copy(title = "same", id = 2)
        assertEquals("same-1.md", notebookFileName(unsafe))
        assertEquals("same-2.md", notebookFileName(collision))
        assertNotEquals(notebookFileName(unsafe), notebookFileName(collision))
        assertEquals("notebook-3.md", notebookFileName(book().copy(title = ".../", id = 3)))
        assertTrue(notebookFileName(book().copy(title = "x".repeat(500))).length < 120)
    }

    private fun book() = Book(
        id = 1, syncId = "book", title = "Book", author = "Author", series = null, seriesNumber = null,
        description = null, tagsCsv = "fiction", coverPath = null, filePath = "", fileAvailability = BookFileAvailability.LOCAL,
        format = BookFormat.EPUB, fileHash = "", readingPercent = 0f, rating = 0f, createdAt = 0, updatedAt = 0,
        lastReadAt = null, lastLocator = null,
    )

    private fun annotation(text: String, note: String?, chapter: String?, type: AnnotationType = AnnotationType.HIGHLIGHT) = Annotation(
        id = 1, bookId = 1, type = type, colorKey = "default", locator = "", chapterTitle = chapter,
        chapterHref = null, selectedText = text, readerNote = note, createdAt = 0, updatedAt = 0,
    )
}
