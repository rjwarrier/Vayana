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

    override suspend fun save(word: String, definition: String, sentence: String?, bookId: Long?, bookTitle: String?): Boolean =
        database.withTransaction {
            if (vocabularyCardDao.findByWord(word) != null) return@withTransaction false
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
            true
        }

    override suspend fun getForReview(limit: Int): List<VocabularyCard> =
        vocabularyCardDao.getForReview(System.currentTimeMillis(), limit).map { it.toDomain() }

    override suspend fun review(id: Long, grade: ReviewGrade) {
        val card = vocabularyCardDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        val next = VocabularySchedule.next(card.repetitions, card.intervalDays, card.easeFactor, grade, now)
        vocabularyCardDao.updateSchedule(
            id = id,
            reviewedAt = now,
            known = next.known,
            dueAt = next.dueAt,
            intervalDays = next.intervalDays,
            easeFactor = next.easeFactor,
            repetitions = next.repetitions,
        )
    }

    override suspend fun markKnown(id: Long) {
        vocabularyCardDao.markKnown(id, System.currentTimeMillis())
    }

    override fun observeDueCount(now: Long): Flow<Int> = vocabularyCardDao.observeDueCount(now)

    override suspend fun findByWord(word: String): VocabularyCard? = vocabularyCardDao.findByWord(word)?.toDomain()

    override fun observeKnownWords(): Flow<List<String>> = vocabularyCardDao.observeKnownWords()

    override suspend fun delete(id: Long) {
        database.withTransaction {
            vocabularyCardDao.getById(id)?.let { card ->
                tombstoneDao.upsert(TombstoneEntity(syncId = card.syncId, entityType = TombstoneEntityType.VOCABULARY_CARD.value, deletedAt = System.currentTimeMillis()))
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
        val existingVersion = existing?.let { it.lastReviewedAt ?: it.createdAt }
        if (tombstone != null && tombstoneDao.supersedes(tombstone, remoteVersion, existingVersion)) {
            return@withTransaction VocabularyCardMergeResult.SKIPPED
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
                    dueAt = record.dueAt,
                    intervalDays = record.intervalDays,
                    easeFactor = record.easeFactor,
                    repetitions = record.repetitions,
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
                dueAt = record.dueAt,
                intervalDays = record.intervalDays,
                easeFactor = record.easeFactor,
                repetitions = record.repetitions,
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
    dueAt = dueAt,
    intervalDays = intervalDays,
    easeFactor = easeFactor,
    repetitions = repetitions,
)
