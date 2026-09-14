package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vayana.core.database.entity.PendingCloudDeletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingCloudDeletionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(deletions: List<PendingCloudDeletionEntity>)

    /** Oldest first, so a queue that can't drain in one sync still makes progress in order. */
    @Query("SELECT * FROM pending_cloud_deletions ORDER BY queuedAt ASC, assetId ASC LIMIT :limit")
    suspend fun getBatch(limit: Int): List<PendingCloudDeletionEntity>

    @Query("SELECT COUNT(*) FROM pending_cloud_deletions")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_cloud_deletions")
    suspend fun count(): Int

    @Query("DELETE FROM pending_cloud_deletions WHERE assetId = :assetId")
    suspend fun delete(assetId: String)

    @Query("UPDATE pending_cloud_deletions SET attempts = attempts + 1, lastError = :error WHERE assetId = :assetId")
    suspend fun recordFailure(assetId: String, error: String)
}
