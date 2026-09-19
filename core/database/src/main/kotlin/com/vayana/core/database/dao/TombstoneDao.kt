package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vayana.core.database.entity.TombstoneEntity

@Dao
interface TombstoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tombstone: TombstoneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tombstones: List<TombstoneEntity>)

    @Query("SELECT * FROM tombstones WHERE entityType = :entityType")
    suspend fun getByType(entityType: String): List<TombstoneEntity>

    @Query("SELECT * FROM tombstones WHERE syncId = :syncId LIMIT 1")
    suspend fun findBySyncId(syncId: String): TombstoneEntity?

    @Query("SELECT * FROM tombstones")
    suspend fun getAll(): List<TombstoneEntity>

    @Query("DELETE FROM tombstones WHERE syncId = :syncId")
    suspend fun deleteBySyncId(syncId: String)
}
