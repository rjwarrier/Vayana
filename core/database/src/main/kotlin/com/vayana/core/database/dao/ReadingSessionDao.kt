package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vayana.core.database.entity.ReadingSessionEntity
import com.vayana.core.database.model.PhysicalReadingSessionSummary
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

    @Query("SELECT COUNT(*) AS sessionCount, COALESCE(SUM(durationSeconds), 0) AS totalSeconds " +
        "FROM reading_sessions WHERE bookId = :bookId AND startPage IS NOT NULL AND endPage IS NOT NULL")
    fun observePhysicalSummary(bookId: Long): Flow<PhysicalReadingSessionSummary>

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId AND startPage IS NOT NULL AND endPage IS NOT NULL " +
        "AND (:forwardOnly = 0 OR (durationSeconds > 0 AND startPage >= 0 AND endPage > startPage)) " +
        "ORDER BY startedAt DESC, id DESC LIMIT :limit")
    fun observeRecentPhysicalSessions(bookId: Long, limit: Int, forwardOnly: Boolean): Flow<List<ReadingSessionEntity>>

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
