package com.vayana.core.database.repository

import com.vayana.core.database.dao.WordLookupStatDao
import com.vayana.core.database.entity.LegacyWriterOrigin
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

    override fun observeAllAggregated(limit: Int): Flow<List<WordLookupStat>> = observeTop(limit)

    override suspend fun getAllForSync(): List<CloudWordLookupCounter> =
        dao.getAllForSync().map { it.toCloudCounter() }

    override suspend fun recordLookup(word: String, writerOrigin: String) {
        val normalizedWord = word.normalizedLookupWord() ?: return
        val normalizedWriterOrigin = writerOrigin.normalizedWriterOrigin()
        dao.recordLookup(normalizedWord, System.currentTimeMillis(), normalizedWriterOrigin)
    }

    override suspend fun mergeCloudCounter(counter: CloudWordLookupCounter): WordLookupCounterMergeResult {
        val normalizedWord = counter.word.normalizedLookupWord() ?: return WordLookupCounterMergeResult.SKIPPED
        val normalizedWriterOrigin = counter.writerOrigin.normalizedWriterOrigin()
        if (counter.count <= 0 || counter.lastLookedUpAt <= 0L) return WordLookupCounterMergeResult.SKIPPED
        dao.mergeCounter(
            word = normalizedWord,
            writerOrigin = normalizedWriterOrigin,
            count = counter.count,
            lastLookedUpAt = counter.lastLookedUpAt,
        )
        return WordLookupCounterMergeResult.MERGED
    }
}

private fun WordLookupStatEntity.toDomain(): WordLookupStat = WordLookupStat(
    word = word,
    count = count,
    lastLookedUpAt = lastLookedUpAt,
)

private fun WordLookupStatEntity.toCloudCounter(): CloudWordLookupCounter = CloudWordLookupCounter(
    word = word,
    writerOrigin = writerOrigin,
    count = count,
    lastLookedUpAt = lastLookedUpAt,
)

private fun String.normalizedLookupWord(): String? = trim()
    .lowercase()
    .takeIf { it.isNotEmpty() && it.length <= MaxLookupWordChars }

private fun String.normalizedWriterOrigin(): String = trim()
    .ifBlank { LegacyWriterOrigin }
    .take(MaxWriterOriginChars)

private const val MaxLookupWordChars = 120
private const val MaxWriterOriginChars = 120
