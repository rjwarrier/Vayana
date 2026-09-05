package com.vayana.core.database.repository

import com.vayana.core.database.dao.VocabularyCardDao
import com.vayana.core.database.entity.VocabularyCardEntity
import com.vayana.core.database.model.VocabularyCard
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VocabularyCardRepositoryImpl @Inject constructor(
    private val vocabularyCardDao: VocabularyCardDao,
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
        vocabularyCardDao.delete(id)
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
