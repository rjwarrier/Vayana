package com.vayana.core.database.repository

import com.vayana.core.database.model.VocabularyCard
import kotlinx.coroutines.flow.Flow

data class CloudVocabularyCardRecord(
    val syncId: String,
    val word: String,
    val definition: String,
    val sentence: String?,
    val bookSyncId: String?,
    val bookTitle: String?,
    val createdAt: Long,
    val lastReviewedAt: Long?,
    val known: Boolean,
    val dueAt: Long? = null,
    val intervalDays: Int = 0,
    val easeFactor: Float = VocabularySchedule.DefaultEase,
    val repetitions: Int = 0,
)

enum class VocabularyCardMergeResult { CREATED, UPDATED, SKIPPED }

interface VocabularyCardRepository {
    fun observeAll(): Flow<List<VocabularyCard>>

    /** Saves [word] as a new card; returns false, saving nothing, when the word is already a card. */
    suspend fun save(word: String, definition: String, sentence: String?, bookId: Long?, bookTitle: String?): Boolean

    /** Up to [limit] cards due for review now: new cards first, then the longest overdue - backs "Review five words". */
    suspend fun getForReview(limit: Int): List<VocabularyCard>

    /** Records how a review went and schedules the card's next one ([VocabularySchedule]). */
    suspend fun review(id: Long, grade: ReviewGrade)

    /** Marks a card as known: it leaves review, and looking the word up shows it as known. */
    suspend fun markKnown(id: Long)

    /** Cards due for review as of [now]. */
    fun observeDueCount(now: Long = System.currentTimeMillis()): Flow<Int>

    suspend fun countDue(now: Long = System.currentTimeMillis()): Int

    /** The card for [word], ignoring case, or null when it hasn't been saved. */
    suspend fun findByWord(word: String): VocabularyCard?

    /** Words marked as known, as saved. */
    fun observeKnownWords(): Flow<List<String>>

    suspend fun delete(id: Long)

    suspend fun mergeCloudCard(record: CloudVocabularyCardRecord): VocabularyCardMergeResult
}
