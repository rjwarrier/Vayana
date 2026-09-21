package com.vayana.core.database.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CommunityHighlightCountTest {
    private fun annotation(note: String?, locator: String = "goodreads-quote:0:x", colorKey: String = "popular") = Annotation(
        id = 1,
        bookId = 1,
        type = AnnotationType.UNDERLINE,
        colorKey = colorKey,
        locator = locator,
        chapterTitle = null,
        chapterHref = null,
        selectedText = "quote",
        readerNote = note,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun readsTheCountTheImportWrites() {
        assertEquals(21, annotation("21 highlights").communityHighlightCount())
        assertEquals(1, annotation("1 highlight").communityHighlightCount())
        assertEquals(7, annotation("7 highlights", locator = "quote:3:y").communityHighlightCount())
    }

    @Test
    fun nullWhenNotACommunityQuoteOrNoteEdited() {
        assertNull(annotation("21 highlights", colorKey = "yellow").communityHighlightCount())
        assertNull(annotation("my own thoughts").communityHighlightCount())
        assertNull(annotation(null).communityHighlightCount())
    }
}
