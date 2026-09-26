package com.vayana.core.database.repository

import com.vayana.core.database.entity.BookEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BookReadingPercentSanitizerTest {
    @Test
    fun `clamps progress to the reader range`() {
        assertEquals(0f, (-0.25f).sanitizedReadingPercent())
        assertEquals(0.5f, 0.5f.sanitizedReadingPercent())
        assertEquals(1f, 1.25f.sanitizedReadingPercent())
    }

    @Test
    fun `replaces non-finite progress with zero`() {
        assertEquals(0f, Float.NaN.sanitizedReadingPercent())
        assertEquals(0f, Float.POSITIVE_INFINITY.sanitizedReadingPercent())
        assertEquals(0f, Float.NEGATIVE_INFINITY.sanitizedReadingPercent())
    }

    @Test
    fun `sanitizes corrupted database progress at the domain boundary`() {
        assertEquals(0f, bookEntity(readingPercent = Float.NaN).toDomain().readingPercent)
        assertEquals(1f, bookEntity(readingPercent = 2f).toDomain().readingPercent)
    }

    private fun bookEntity(readingPercent: Float) = BookEntity(
        title = "Test book",
        author = null,
        series = null,
        seriesNumber = null,
        description = null,
        coverPath = null,
        filePath = "test.epub",
        format = "EPUB",
        fileHash = "hash",
        lastLocator = null,
        readingPercent = readingPercent,
        rating = 0f,
        groupId = null,
        isDeleted = false,
        wordCount = null,
        pageEstimate = null,
        createdAt = 1L,
        updatedAt = 1L,
        lastReadAt = null,
    )
}
