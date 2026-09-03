package com.vayana.core.database.model

/** Formats accepted at import time (PROMPT2appbuild.md §3). Only EPUB is parsed natively so far. */
enum class BookFormat {
    EPUB, TXT, MOBI, AZW3, FB2, PDF
}

/**
 * Domain-facing book model — the public surface of `:core:database`. Callers never see
 * [com.vayana.core.database.entity.BookEntity] directly, keeping Room out of feature/UI code (§0.5).
 */
data class Book(
    val id: Long,
    val title: String,
    val author: String?,
    val series: String?,
    val seriesNumber: String?,
    val description: String?,
    val coverPath: String?,
    val filePath: String,
    val format: BookFormat,
    val fileHash: String,
    val readingPercent: Float,
    val rating: Float,
    val createdAt: Long,
    val updatedAt: Long,
    val lastReadAt: Long?,
    val lastLocator: String?,
    val startedReadingAt: Long? = null,
    val finishedReadingAt: Long? = null,
    val totalReadingSeconds: Long = 0L,
)
