package com.vayana.core.database.model

/**
 * Formats accepted at import time (docs/PRODUCT_SPEC.md §3). Only EPUB is parsed natively so far.
 * [PHYSICAL], [AUDIOBOOK] and [OTHER_EBOOK] (an ebook read in another app or device) are file-less entries for books
 * read outside the app: they track dates,
 * rating and notes (and count towards the yearly goal) but never open the reader. See [isOffline].
 */
enum class BookFormat {
    EPUB, TXT, MOBI, AZW3, FB2, PDF, PHYSICAL, AUDIOBOOK, OTHER_EBOOK;

    /** Read outside the app: there is no file to open, upload or download. */
    val isOffline: Boolean get() = this == PHYSICAL || this == AUDIOBOOK || this == OTHER_EBOOK

    /** An offline book the reader tracks by page; an audiobook has none. */
    val tracksPages: Boolean get() = this == PHYSICAL || this == OTHER_EBOOK

    companion object {
        val Offline: List<BookFormat> = entries.filter { it.isOffline }
    }
}

/**
 * A page entry for a book read outside the app: [total] pages (only above zero counts) and the [current] page, which
 * only counts once there is a total and is kept within it. [percent] is how far through that puts the reader.
 */
data class OfflinePages(val total: Int?, val current: Int?) {
    val percent: Float? get() = total?.let { pages -> current?.let { it.toFloat() / pages } }

    companion object {
        fun of(pageCount: Int?, currentPage: Int?): OfflinePages {
            val total = pageCount?.takeIf { it > 0 }
            return OfflinePages(total = total, current = total?.let { pages -> currentPage?.coerceIn(0, pages) })
        }
    }
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
    val syncId: String,
    val title: String,
    val author: String?,
    val series: String?,
    val seriesNumber: String?,
    val description: String?,
    val tagsCsv: String? = null,
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
    val readNextUpdatedAt: Long? = null,
    val goodreadsUrl: String? = null,
    val goodreadsRating: Float? = null,
    val goodreadsRatingsCount: Int? = null,
    val originalPublicationYear: Int? = null,
    /** Switchable covers; [coverPath] is the one in use and may equal either. */
    val customCoverPath: String? = null,
    val goodreadsCoverPath: String? = null,
    /**
     * Total pages of a book read outside the app, as the reader entered it (the `pageEstimate` column); always null for
     * a format without pages ([BookFormat.tracksPages]). Its current page is [readingPercent] of this, so progress
     * needs no reader position.
     */
    val pageCount: Int? = null,
)
