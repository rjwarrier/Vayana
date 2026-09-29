package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vayana.core.database.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: ReadingSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(session: ReadingSessionEntity): Long

    @Query("SELECT * FROM reading_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<ReadingSessionEntity>>

    /** Sessions started at or after [since]: a few days' worth, where watching them all would reread years. */
    @Query("SELECT * FROM reading_sessions WHERE startedAt >= :since ORDER BY startedAt ASC")
    fun observeSince(since: Long): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY startedAt DESC")
    fun observeForBook(bookId: Long): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions ORDER BY syncId ASC")
    suspend fun getAllForSync(): List<ReadingSessionEntity>

    @Query("SELECT syncId FROM reading_sessions WHERE bookId = :bookId")
    suspend fun syncIdsForBook(bookId: Long): List<String>

    @Query("DELETE FROM reading_sessions WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)

    @Query("DELETE FROM reading_sessions WHERE syncId = :syncId")
    suspend fun deleteBySyncId(syncId: String): Int

    @Query("DELETE FROM reading_sessions WHERE bookId = :bookId AND startedAt <= :before")
    suspend fun deleteForBookStartedBefore(bookId: Long, before: Long): Int
}
