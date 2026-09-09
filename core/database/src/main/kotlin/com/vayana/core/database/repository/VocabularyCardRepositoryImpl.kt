package com.vayana.core.database.repository

import androidx.room.withTransaction
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.dao.BookAliasDao
import com.vayana.core.database.dao.BookDao
import com.vayana.core.database.dao.TombstoneDao
import com.vayana.core.database.dao.VocabularyCardDao
import com.vayana.core.database.entity.TombstoneEntity
import com.vayana.core.database.entity.VocabularyCardEntity
import com.vayana.core.database.model.VocabularyCard
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VocabularyCardRepositoryImpl @Inject constructor(
    private val database: VayanaDatabase,
    private val vocabularyCardDao: VocabularyCardDao,
    private val bookDao: BookDao,
    private val bookAliasDao: BookAliasDao,
    private val tombstoneDao: TombstoneDao,
) : VocabularyCardRepository {

    override fun observeAll(): Flow<List<VocabularyCard>> =
        vocabularyCardDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun save(word: String, definition: String, sentence: String?, bookId: Long?, bookTitle: String?) {
        vocabularyCardDao.insert(
            VocabularyCardEntity(
                word = word,
                definition = definition,
                sentence = sentence,
                bookId = bookId,
                bookTitle = bookTitle,
                createdAt = System.currentTimeMillis(),
                lastReviewedAt = null,
                known = false,
            ),
        )
    }

    override suspend fun getForReview(limit: Int): List<VocabularyCard> =
        vocabularyCardDao.getForReview(limit).map { it.toDomain() }

    override suspend fun markReviewed(id: Long, known: Boolean) {
        vocabularyCardDao.markReviewed(id, System.currentTimeMillis(), known)
    }

    override suspend fun delete(id: Long) {
        database.withTransaction {
            vocabularyCardDao.getById(id)?.let { card ->
                tombstoneDao.upsert(TombstoneEntity(syncId = card.syncId, entityType = TombstoneEntityType.VOCABULARY_CARD, deletedAt = System.currentTimeMillis()))
            }
            vocabularyCardDao.delete(id)
        }
    }

    override suspend fun mergeCloudCard(record: CloudVocabularyCardRecord): VocabularyCardMergeResult = database.withTransaction {
        if (record.syncId.isBlank() || record.word.isBlank() || record.definition.isBlank() || record.createdAt <= 0L) {
            return@withTransaction VocabularyCardMergeResult.SKIPPED
        }
        val existing = vocabularyCardDao.findBySyncId(record.syncId)
        val tombstone = tombstoneDao.findBySyncId(record.syncId)
        val remoteVersion = record.lastReviewedAt ?: record.createdAt
        if (tombstone != null) {
            if (remoteVersion <= tombstone.deletedAt && (existing == null || (existing.lastReviewedAt ?: existing.createdAt) <= tombstone.deletedAt)) {
                return@withTransaction VocabularyCardMergeResult.SKIPPED
            }
            tombstoneDao.deleteBySyncId(record.syncId)
        }
        val localBookId = record.bookSyncId?.let { bookDao.findActiveBySyncIdOrAlias(it, bookAliasDao)?.id }
        if (existing == null) {
            vocabularyCardDao.insert(
                VocabularyCardEntity(
                    syncId = record.syncId,
                    word = record.word,
                    definition = record.definition,
                    sentence = record.sentence,
                    bookId = localBookId,
                    bookTitle = record.bookTitle,
                    createdAt = record.createdAt,
                    lastReviewedAt = record.lastReviewedAt,
                    known = record.known,
                ),
            )
            return@withTransaction VocabularyCardMergeResult.CREATED
        }
        val localVersion = existing.lastReviewedAt ?: existing.createdAt
        if (remoteVersion <= localVersion) return@withTransaction VocabularyCardMergeResult.SKIPPED
        vocabularyCardDao.update(
            existing.copy(
                word = record.word,
                definition = record.definition,
                sentence = record.sentence,
                bookId = localBookId ?: existing.bookId,
                bookTitle = record.bookTitle ?: existing.bookTitle,
                lastReviewedAt = record.lastReviewedAt,
                known = record.known,
            ),
        )
        VocabularyCardMergeResult.UPDATED
    }
}

private fun VocabularyCardEntity.toDomain(): VocabularyCard = VocabularyCard(
    id = id,
    word = word,
    definition = definition,
    sentence = sentence,
    bookId = bookId,
    bookTitle = bookTitle,
    createdAt = createdAt,
    lastReviewedAt = lastReviewedAt,
    known = known,
)
