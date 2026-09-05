package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vayana.core.database.entity.AnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun observeForBook(bookId: Long): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE id = :id")
    suspend fun getById(id: Long): AnnotationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(annotation: AnnotationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(annotations: List<AnnotationEntity>): List<Long>

    @Update
    suspend fun update(annotation: AnnotationEntity)

    @Query("UPDATE annotations SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long)

    @Query("UPDATE annotations SET isDeleted = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long)

    /** Permanently purges an already soft-deleted row - never call this directly on a live one. */
    @Delete
    suspend fun delete(annotation: AnnotationEntity)

    @Query("SELECT * FROM annotations ORDER BY syncId ASC")
    suspend fun getAllForSync(): List<AnnotationEntity>
}
