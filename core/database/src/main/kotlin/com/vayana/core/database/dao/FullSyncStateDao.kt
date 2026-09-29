package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FullSyncStateDao {
    @Query("SELECT EXISTS(SELECT 1 FROM full_sync_state WHERE id = 0 AND required = 1)")
    fun observeRequired(): Flow<Boolean>

    @Query("INSERT OR REPLACE INTO full_sync_state(id, required) VALUES (0, 0)")
    suspend fun markSatisfied()
}
