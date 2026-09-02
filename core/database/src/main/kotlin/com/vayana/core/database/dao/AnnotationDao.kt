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
    @Query("SELECT * FROM annotations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY updatedAt DESC")
    fun observeForBook(bookId: Long): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE id = :id")
    suspend fun getById(id: Long): AnnotationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(annotation: AnnotationEntity): Long

    @Update
    suspend fun update(annotation: AnnotationEntity)

    @Delete
    suspend fun delete(annotation: AnnotationEntity)
}
