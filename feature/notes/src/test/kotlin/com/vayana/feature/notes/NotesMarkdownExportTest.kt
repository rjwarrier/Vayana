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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

class NotesMarkdownExportTest {
    @Test fun streamingComparisonKeepsOwnershipChecksAndHandlesBufferBoundaries() {
        val marker = "<!-- Vayana generated notebook: test -->"
        val content = "$marker\n" + "Café 雪 passage\n".repeat(2000)
        assertTrue(notebookIsUnchanged(content.reader(), content, marker))
        assertFalse(notebookIsUnchanged((content + "extra").reader(), content, marker))
        assertFalse(notebookIsUnchanged(content.dropLast(1).reader(), content, marker))
        assertFalse(notebookIsUnchanged(content.replace("Café", "Other").reader(), content, marker))
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            notebookIsUnchanged("Handwritten notes".reader(), content, marker)
        }
        // An early change must not force a full read of a large notebook.
        var readCount = 0
        val counting = object : java.io.FilterReader(content.replaceFirst("Café", "Other").reader()) {
            override fun read(): Int = super.read().also { if (it >= 0) readCount++ }
            override fun read(buffer: CharArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, length).also { if (it > 0) readCount += it }
        }
        assertFalse(notebookIsUnchanged(counting, content, marker))
        assertTrue(readCount <= marker.length + 1 + 8192)
    }

    @Test fun onlyExportedBookMetadataTriggersNotebookRefresh() {
        val original = book()
        assertEquals(notebookMetadata(original), notebookMetadata(original.copy(
            readingPercent = .6f, lastReadAt = 500, lastLocator = "epubcfi(test)", totalReadingSeconds = 900,
            updatedAt = 1000, readingDisposition = "PAUSED", readNextAddedAt = 200, readNextPinned = true)))
        assertNotEquals(notebookMetadata(original), notebookMetadata(original.copy(title = "Renamed")))
        assertNotEquals(notebookMetadata(original), notebookMetadata(original.copy(tagsCsv = "new tag")))
    }

    @Test fun notebookContainsReviewQuestionAndStablePassageLink() {
        val note = annotation("Answer", null, "Chapter").copy(syncId = "note & café", reviewQuestion = "What happened?")
        val exported = highlightsMarkdown(book(), listOf(note))
        assertTrue(exported.contains("What happened?"))
        assertTrue(exported.contains(com.vayana.core.common.passageLink("book", "note & café")))
        assertTrue(exported.contains("> Answer"))
        assertFalse(automaticNotebookFileName("../unsafe").contains("/"))
        assertNotEquals(automaticNotebookFileName("book"), automaticNotebookFileName("other"))
    }

    @Test
    fun zipRetainsEachNotebookAndDeduplicatesBooks() = runBlocking {
        val first = BookNotesItem(book().copy(title = "Same 📚"), listOf(annotation("Unicode café", "#thought", "Chapter")))
        val second = BookNotesItem(book().copy(id = 2, title = "Same 📚"), listOf(annotation("", "Journal body", null, AnnotationType.NOTE)))
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { writeMarkdownNotebooks(it, listOf(first, first, second)) }
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(mapOf(notebookFileName(first.book) to highlightsMarkdown(first.book, first.annotations),
            notebookFileName(second.book) to highlightsMarkdown(second.book, second.annotations)), entries)
    }

    @Test
    fun cancelledExportStopsBeforeWritingEntries() = runBlocking {
        val output = ByteArrayOutputStream()
        var cancelled = false
        ZipOutputStream(output).use { zip ->
            try {
                withContext(Job().apply { cancel() }) {
                    writeMarkdownNotebooks(zip, listOf(BookNotesItem(book(), emptyList())))
                }
            } catch (_: CancellationException) { cancelled = true }
        }
        assertTrue(cancelled)
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { kotlin.test.assertNull(it.nextEntry) }
    }

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
