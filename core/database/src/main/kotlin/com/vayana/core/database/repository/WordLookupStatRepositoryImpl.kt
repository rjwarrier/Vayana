package com.vayana.core.database.repository

import com.vayana.core.database.dao.WordLookupStatDao
import com.vayana.core.database.entity.WordLookupStatEntity
import com.vayana.core.database.model.WordLookupStat
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WordLookupStatRepositoryImpl @Inject constructor(
    private val dao: WordLookupStatDao,
) : WordLookupStatRepository {

    override fun observeTop(limit: Int): Flow<List<WordLookupStat>> =
        dao.observeTop(limit).map { entities -> entities.map { it.toDomain() } }

    override fun observeRecent(limit: Int): Flow<List<WordLookupStat>> =
        dao.observeRecent(limit).map { entities -> entities.map { it.toDomain() } }

    override suspend fun recordLookup(word: String) {
        dao.recordLookup(word, System.currentTimeMillis())
    }
}

private fun WordLookupStatEntity.toDomain(): WordLookupStat = WordLookupStat(
    word = word,
    count = count,
    lastLookedUpAt = lastLookedUpAt,
)
