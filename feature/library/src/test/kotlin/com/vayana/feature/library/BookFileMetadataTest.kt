package com.vayana.feature.library

import com.vayana.core.database.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookFileMetadataTest {
    @Test
    fun `EPUB and PDF are readable, other formats are not yet`() {
        assertEquals(setOf(BookFormat.EPUB, BookFormat.PDF), ReadableBookFormats)
        assertTrue(BookFormat.PHYSICAL !in ReadableBookFormats)
    }

    @Test
    fun `a file without an extension is recognised by its MIME type`() {
        assertEquals("epub", readableExtensionForMimeType("application/epub+zip"))
        assertEquals("pdf", readableExtensionForMimeType("application/pdf"))
        assertEquals("", readableExtensionForMimeType("application/octet-stream"))
        assertEquals("", readableExtensionForMimeType(null))
        assertEquals("pdf", readableExtension("download", "application/pdf"))
        assertEquals("epub", readableExtension("BOOK.EPUB", "application/octet-stream"))
    }

    @Test
    fun `a PDF without its own title is named after its file`() {
        assertEquals("The Art-of Reading", titleFromFileName("The_Art-of  Reading.pdf"))
        assertEquals("notes.v2", titleFromFileName("notes.v2.pdf"))
        assertEquals("Untitled", titleFromFileName(".pdf"))
    }
}
