package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vayana.core.database.entity.ReadingSessionEntity
import com.vayana.core.database.model.PhysicalReadingSessionSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {
    @Query("SELECT * FROM reading_sessions WHERE syncId = :syncId LIMIT 1")
    suspend fun findBySyncId(syncId: String): ReadingSessionEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM reading_sessions WHERE durationSeconds > 0 AND startedAt < :endedAt AND endedAt > :startedAt)")
    suspend fun hasOverlap(startedAt: Long, endedAt: Long): Boolean

    @Query("SELECT * FROM reading_sessions WHERE startedAt < :endedAt AND endedAt > :startedAt AND durationSeconds > 0")
    suspend fun overlapping(startedAt: Long, endedAt: Long): List<ReadingSessionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: ReadingSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(session: ReadingSessionEntity): Long

    @Update
    suspend fun update(session: ReadingSessionEntity)

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM reading_sessions WHERE bookId = :bookId")
    suspend fun totalSecondsForBook(bookId: Long): Long

    @Query("SELECT * FROM reading_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<ReadingSessionEntity>>

    /** Sessions started at or after [since]: a few days' worth, where watching them all would reread years. */
    @Query("SELECT * FROM reading_sessions WHERE startedAt >= :since ORDER BY startedAt ASC")
    fun observeSince(since: Long): Flow<List<ReadingSessionEntity>>

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM reading_sessions WHERE startedAt >= :since")
    fun observeSecondsSince(since: Long): Flow<Long>

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY startedAt DESC")
    fun observeForBook(bookId: Long): Flow<List<ReadingSessionEntity>>

    @Query("SELECT COUNT(*) AS sessionCount, COALESCE(SUM(durationSeconds), 0) AS totalSeconds " +
        "FROM reading_sessions WHERE bookId = :bookId AND durationSeconds > 0 AND startPage IS NOT NULL AND endPage IS NOT NULL")
    fun observePhysicalSummary(bookId: Long): Flow<PhysicalReadingSessionSummary>

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId AND durationSeconds > 0 AND startPage IS NOT NULL AND endPage IS NOT NULL " +
        "AND (:forwardOnly = 0 OR (durationSeconds > 0 AND startPage >= 0 AND endPage > startPage)) " +
        "ORDER BY startedAt DESC, id DESC LIMIT :limit")
    fun observeRecentPhysicalSessions(bookId: Long, limit: Int, forwardOnly: Boolean): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions ORDER BY syncId ASC")
    suspend fun getAllForSync(): List<ReadingSessionEntity>

    @Query("SELECT syncId FROM reading_sessions WHERE bookId = :bookId")
    suspend fun syncIdsForBook(bookId: Long): List<String>

    @Query("UPDATE reading_sessions SET startPage = :startPage, endPage = :endPage WHERE syncId = :syncId")
    suspend fun updatePages(syncId: String, startPage: Int, endPage: Int): Int

    @Query("DELETE FROM reading_sessions WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)

    @Query("DELETE FROM reading_sessions WHERE syncId = :syncId")
    suspend fun deleteBySyncId(syncId: String): Int

    @Query("DELETE FROM reading_sessions WHERE bookId = :bookId AND startedAt <= :before")
    suspend fun deleteForBookStartedBefore(bookId: Long, before: Long): Int
}
