package com.vayana.core.database.model

/**
 * Formats accepted at import time (PROMPT2appbuild.md §3). Only EPUB is parsed natively so far.
 * [PHYSICAL] is a file-less entry for a paper book the user owns but doesn't read in-app — it
 * exists only to hold manually-typed quotes/notes, never opens the reader.
 */
enum class BookFormat {
    EPUB, TXT, MOBI, AZW3, FB2, PDF, PHYSICAL
}

enum class BookFileAvailability {
    LOCAL,
    CLOUD_ONLY,
    MISSING,
    UPLOAD_PENDING,
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
    val fileAvailability: BookFileAvailability,
    val format: BookFormat,
    val fileHash: String,
    val fileAssetId: String? = null,
    val fileAssetSha256: String? = null,
    val fileAssetSizeBytes: Long? = null,
    val fileAssetUploadedAt: Long? = null,
    val coverAssetId: String? = null,
    val coverAssetSha256: String? = null,
    val coverAssetSizeBytes: Long? = null,
    val coverAssetUploadedAt: Long? = null,
    val readingPercent: Float,
    val rating: Float,
    val createdAt: Long,
    val updatedAt: Long,
    val lastReadAt: Long?,
    val lastLocator: String?,
    val startedReadingAt: Long? = null,
    val finishedReadingAt: Long? = null,
    val totalReadingSeconds: Long = 0L,
    val customFontSizePercent: Int? = null,
    val customLineHeight: Float? = null,
    val customFontFamily: String? = null,
    val customSideMarginPercent: Int? = null,
    val readNextAddedAt: Long? = null,
)
