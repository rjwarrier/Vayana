package com.vayana.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room schema per docs/PRODUCT_SPEC.md §2. [coverPath] and [filePath] are root-relative,
 * never absolute — resolved through `StorageRoots` (`:core:filesystem`) so relocating the
 * library is a single migration job, not a data-model change.
 */
@Entity(
    tableName = "books",
    indices = [Index(value = ["syncId"], unique = true), Index(value = ["fileHash"]), Index(value = ["gutenbergId"]), Index(value = ["syncUuid"], unique = true)],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "book-${UUID.randomUUID()}",
    val title: String,
    val author: String?,
    val series: String?,
    val seriesNumber: String?,
    val description: String?,
    val tagsCsv: String? = null,
    val coverPath: String?,
    val filePath: String,
    val fileAvailability: String = "LOCAL",
    val format: String,
    val fileHash: String,
    val fileAssetId: String? = null,
    val fileAssetSha256: String? = null,
    val fileAssetSizeBytes: Long? = null,
    val fileAssetUploadedAt: Long? = null,
    val coverAssetId: String? = null,
    val coverAssetSha256: String? = null,
    val coverAssetSizeBytes: Long? = null,
    val coverAssetUploadedAt: Long? = null,
    val lastLocator: String?,
    val readingPercent: Float,
    val rating: Float,
    val groupId: Long?,
    val isDeleted: Boolean,
    val wordCount: Int?,
    val pageEstimate: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastReadAt: Long?,
    val startedReadingAt: Long? = null,
    val finishedReadingAt: Long? = null,
    val totalReadingSeconds: Long = 0L,
    /** Per-book reader style overrides - null means "use the global reader settings" for that field. */
    val customFontSizePercent: Int? = null,
    val customLineHeight: Float? = null,
    val customFontFamily: String? = null,
    val customSideMarginPercent: Int? = null,
    /** Non-null while queued in "Read next", ordered ascending (earliest add = next up). */
    val readNextAddedAt: Long? = null,
    /** When the queue entry was last added or removed; syncs "Read next" separately from [updatedAt], which reading also bumps. */
    val readNextUpdatedAt: Long? = null,
    /** When the book was last deleted or restored; synced deletes and restores compare this, which reading never bumps. */
    val deletionUpdatedAt: Long? = null,
    /** Goodreads import extras synced as optional portable metadata. */
    val goodreadsUrl: String? = null,
    val goodreadsRating: Float? = null,
    val goodreadsRatingsCount: Int? = null,
    val originalPublicationYear: Int? = null,
    /**
     * The two covers a book can switch between once Goodreads has supplied one (root-relative, like
     * [coverPath]). [coverPath] is always the one in use and may equal either.
     */
    val customCoverPath: String? = null,
    val goodreadsCoverPath: String? = null,
    /** Ownership and return details apply only to physical books. */
    val physicalOwnership: String? = null,
    val borrowReturnAt: Long? = null,
    /** Stable Project Gutenberg identity, when this row was imported from its catalogue. */
    val gutenbergId: Long? = null,
    /**
     * Identity of a row mirrored from another app (Home Library's `sync_uuid`); null for Vayana's own books. Unique,
     * so a mirrored book is matched on it and never on title or ISBN.
     */
    val syncUuid: String? = null,
    /** Which app owns a mirrored row ([com.vayana.core.database.model.BookSource]); null for Vayana's own books. */
    val source: String? = null,
    /** The owner's own `updated_at` for the row, so a changed cover is fetched again. */
    val sourceUpdatedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val sourceHasCover: Boolean = false,
    /** The owner's extra fields (publisher, shelf location, ...) as JSON, see `HomeLibraryDetails`. */
    val sourceMetadata: String? = null,
    @ColumnInfo(defaultValue = "0") val readNextPinned: Boolean = false,
    @ColumnInfo(defaultValue = "'ACTIVE'") val readingDisposition: String = "ACTIVE",
    val dispositionReason: String? = null,
    val dispositionUpdatedAt: Long? = null,
)
