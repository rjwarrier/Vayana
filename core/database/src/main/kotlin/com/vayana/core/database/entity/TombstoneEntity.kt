package com.vayana.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Records a hard deletion so a future sync merge can distinguish "never existed on this device"
 * from "existed and was deliberately purged" (docs/GITHUB_SYNC_IMPLEMENTATION_PLAN.md §4:
 * "Maintain tombstones for hard deletions, including memberships").
 *
 * Schema only for now - milestone 1 does not write or read this table yet; nothing calls into it
 * until the merge milestone lands.
 */
@Entity(
    tableName = "tombstones",
    indices = [Index(value = ["entityType"]), Index(value = ["deletedAt"])],
)
data class TombstoneEntity(
    @PrimaryKey val syncId: String,
    val entityType: String,
    val deletedAt: Long,
)
