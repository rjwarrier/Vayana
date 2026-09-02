package com.vayana.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room schema per PROMPT2appbuild.md §2. [coverPath] and [filePath] are root-relative,
 * never absolute — resolved through `StorageRoots` (`:core:filesystem`) so relocating the
 * library is a single migration job, not a data-model change.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String?,
    val description: String?,
    val coverPath: String?,
    val filePath: String,
    val format: String,
    val fileHash: String,
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
)
