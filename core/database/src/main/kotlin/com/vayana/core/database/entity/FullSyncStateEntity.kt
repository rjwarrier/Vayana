package com.vayana.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Singleton flag set by database triggers when progress-only sync cannot publish a local change. */
@Entity(tableName = "full_sync_state")
data class FullSyncStateEntity(
    @PrimaryKey val id: Int = SingletonId,
    val required: Boolean = false,
) {
    companion object {
        const val SingletonId = 0
    }
}
