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
)

enum class VocabularyCardMergeResult { CREATED, UPDATED, SKIPPED }

interface VocabularyCardRepository {
    fun observeAll(): Flow<List<VocabularyCard>>

    suspend fun save(word: String, definition: String, sentence: String?, bookId: Long?, bookTitle: String?)

    /** Up to [limit] cards to review, unreviewed first then least-recently reviewed - backs "Review five words". */
    suspend fun getForReview(limit: Int): List<VocabularyCard>

    suspend fun markReviewed(id: Long, known: Boolean)

    suspend fun delete(id: Long)

    suspend fun mergeCloudCard(record: CloudVocabularyCardRecord): VocabularyCardMergeResult
}
