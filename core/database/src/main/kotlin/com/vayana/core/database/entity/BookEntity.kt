package com.vayana.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room schema per PROMPT2appbuild.md §2. [coverPath] and [filePath] are root-relative,
 * never absolute — resolved through `StorageRoots` (`:core:filesystem`) so relocating the
 * library is a single migration job, not a data-model change.
 */
@Entity(
    tableName = "books",
    indices = [Index(value = ["syncId"], unique = true), Index(value = ["fileHash"])],
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
)
