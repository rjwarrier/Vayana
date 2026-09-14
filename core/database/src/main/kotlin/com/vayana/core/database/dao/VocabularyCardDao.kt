package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.vayana.core.database.entity.VocabularyCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyCardDao {
    @Query("SELECT * FROM vocabulary_cards ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VocabularyCardEntity>>

    @Insert
    suspend fun insert(card: VocabularyCardEntity): Long

    @Update
    suspend fun update(card: VocabularyCardEntity)

    @Query("SELECT * FROM vocabulary_cards WHERE id = :id")
    suspend fun getById(id: Long): VocabularyCardEntity?

    @Query("SELECT * FROM vocabulary_cards WHERE syncId = :syncId LIMIT 1")
    suspend fun findBySyncId(syncId: String): VocabularyCardEntity?

    /** Cards due by [now]: new ones first, then the longest overdue - "review five words" pulls from the front. */
    @Query(
        "SELECT * FROM vocabulary_cards WHERE known = 0 AND (dueAt IS NULL OR dueAt <= :now) " +
            "ORDER BY (dueAt IS NOT NULL), dueAt ASC, createdAt ASC LIMIT :limit",
    )
    suspend fun getForReview(now: Long, limit: Int): List<VocabularyCardEntity>

    @Query("SELECT COUNT(*) FROM vocabulary_cards WHERE known = 0 AND (dueAt IS NULL OR dueAt <= :now)")
    fun observeDueCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM vocabulary_cards WHERE known = 0 AND (dueAt IS NULL OR dueAt <= :now)")
    suspend fun countDue(now: Long): Int

    @Query(
        "UPDATE vocabulary_cards SET lastReviewedAt = :reviewedAt, known = :known, dueAt = :dueAt, " +
            "intervalDays = :intervalDays, easeFactor = :easeFactor, repetitions = :repetitions WHERE id = :id",
    )
    suspend fun updateSchedule(
        id: Long,
        reviewedAt: Long,
        known: Boolean,
        dueAt: Long,
        intervalDays: Int,
        easeFactor: Float,
        repetitions: Int,
    )

    /** The card for [word], ignoring case; a known card wins if the word was somehow saved twice. */
    @Query("SELECT * FROM vocabulary_cards WHERE word = :word COLLATE NOCASE ORDER BY known DESC LIMIT 1")
    suspend fun findByWord(word: String): VocabularyCardEntity?

    @Query("SELECT word FROM vocabulary_cards WHERE known = 1")
    fun observeKnownWords(): Flow<List<String>>

    @Query("UPDATE vocabulary_cards SET lastReviewedAt = :reviewedAt, known = :known WHERE id = :id")
    suspend fun markReviewed(id: Long, reviewedAt: Long, known: Boolean)

    @Query("DELETE FROM vocabulary_cards WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM vocabulary_cards WHERE syncId = :syncId")
    suspend fun deleteBySyncId(syncId: String): Int

    /** A permanently deleted book's words stay; they just stop pointing at it. */
    @Query("UPDATE vocabulary_cards SET bookId = NULL WHERE bookId = :bookId")
    suspend fun detachBook(bookId: Long)

    @Query("SELECT * FROM vocabulary_cards ORDER BY syncId ASC")
    suspend fun getAllForSync(): List<VocabularyCardEntity>
}
