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

    /** Never-reviewed cards first, then the least-recently reviewed - "review five words" pulls from the front. */
    @Query(
        "SELECT * FROM vocabulary_cards WHERE known = 0 " +
            "ORDER BY (lastReviewedAt IS NOT NULL), lastReviewedAt ASC, createdAt ASC LIMIT :limit",
    )
    suspend fun getForReview(limit: Int): List<VocabularyCardEntity>

    @Query("UPDATE vocabulary_cards SET lastReviewedAt = :reviewedAt, known = :known WHERE id = :id")
    suspend fun markReviewed(id: Long, reviewedAt: Long, known: Boolean)

    @Query("DELETE FROM vocabulary_cards WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM vocabulary_cards WHERE syncId = :syncId")
    suspend fun deleteBySyncId(syncId: String): Int

    @Query("SELECT * FROM vocabulary_cards ORDER BY syncId ASC")
    suspend fun getAllForSync(): List<VocabularyCardEntity>
}
