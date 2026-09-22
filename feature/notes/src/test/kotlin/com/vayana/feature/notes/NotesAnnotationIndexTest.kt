package com.vayana.feature.notes

import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import kotlin.test.Test
import kotlin.test.assertEquals

class NotesAnnotationIndexTest {
    @Test
    fun preservesBookOrderAndCountsDistinctTagsPerAnnotation() {
        val first = annotation(id = 1, bookId = 2, updatedAt = 20, note = "#Idea #idea #draft")
        val second = annotation(id = 2, bookId = 1, updatedAt = 30, note = "#draft")
        val third = annotation(id = 3, bookId = 2, updatedAt = 10, note = "#idea")

        val index = indexNotesAnnotations(listOf(first, second, third))

        assertEquals(listOf(first, third), index.byBook[2])
        assertEquals(listOf(second), index.byBook[1])
        assertEquals(20L, index.latestByBook[2])
        assertEquals(30L, index.latestByBook[1])
        assertEquals(listOf("draft", "idea"), index.tags)
    }

    private fun annotation(id: Long, bookId: Long, updatedAt: Long, note: String) = Annotation(
        id = id,
        bookId = bookId,
        type = AnnotationType.NOTE,
        colorKey = "default",
        locator = "",
        chapterTitle = null,
        chapterHref = null,
        selectedText = "",
        readerNote = note,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )
}
