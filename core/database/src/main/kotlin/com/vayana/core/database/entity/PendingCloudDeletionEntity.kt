package com.vayana.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An encrypted cloud asset (book file or cover) of a permanently deleted book that still has to be removed from the
 * sync repository. Local only: each device queues the asset ids it knows about and clears a row once that asset is
 * gone.
 */
@Entity(tableName = "pending_cloud_deletions")
data class PendingCloudDeletionEntity(
    @PrimaryKey val assetId: String,
    /** A [com.vayana.core.database.repository.CloudAssetKind] value. */
    val kind: String,
    val queuedAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)
