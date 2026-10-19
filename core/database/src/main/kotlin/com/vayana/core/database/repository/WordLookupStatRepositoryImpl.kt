package com.vayana.core.database.repository

import androidx.room.withTransaction
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.dao.TombstoneDao
import com.vayana.core.database.dao.WordLookupStatDao
import com.vayana.core.database.entity.TombstoneEntity
import com.vayana.core.database.entity.LegacyWriterOrigin
import com.vayana.core.database.entity.WordLookupStatEntity
import com.vayana.core.database.model.WordLookupStat
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WordLookupStatRepositoryImpl @Inject constructor(
    private val dao: WordLookupStatDao,
    private val database: VayanaDatabase,
    private val tombstoneDao: TombstoneDao,
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
        val now = System.currentTimeMillis()
        if (dao.insertIfAbsent(WordLookupStatEntity(normalizedWord, 1, now, normalizedWriterOrigin)) == -1L) {
            dao.incrementLookup(normalizedWord, normalizedWriterOrigin, now)
        }
    }

    override suspend fun mergeCloudCounter(counter: CloudWordLookupCounter): WordLookupCounterMergeResult {
        val normalizedWord = counter.word.normalizedLookupWord() ?: return WordLookupCounterMergeResult.SKIPPED
        val normalizedWriterOrigin = counter.writerOrigin.normalizedWriterOrigin()
        if (counter.count <= 0 || counter.lastLookedUpAt <= 0L) return WordLookupCounterMergeResult.SKIPPED
        // Removed from Learn words since: another device's older counter mustn't bring it back.
        val removedAt = tombstoneDao.findBySyncId(wordLookupTombstoneId(normalizedWord))?.deletedAt
        if (removedAt != null && counter.lastLookedUpAt <= removedAt) return WordLookupCounterMergeResult.SKIPPED
        val inserted = dao.insertIfAbsent(
            WordLookupStatEntity(normalizedWord, counter.count, counter.lastLookedUpAt, normalizedWriterOrigin),
        ) != -1L
        val raised = !inserted && dao.raiseCounter(normalizedWord, normalizedWriterOrigin, counter.count, counter.lastLookedUpAt) > 0
        return if (inserted || raised) WordLookupCounterMergeResult.MERGED else WordLookupCounterMergeResult.SKIPPED
    }

    // ponytail: a progress push takes the larger of two counts per device, so a word removed and then looked up
    // again here can come back with its old count from the cloud copy; tombstone-aware patching if that matters.
    override suspend fun removeWord(word: String) {
        val normalizedWord = word.normalizedLookupWord() ?: return
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.deleteWord(normalizedWord, now)
            tombstoneDao.upsert(
                TombstoneEntity(syncId = wordLookupTombstoneId(normalizedWord), entityType = TombstoneEntityType.WORD_LOOKUP.value, deletedAt = now),
            )
        }
    }

    override suspend fun applyRemoval(word: String, removedAt: Long): Int =
        word.normalizedLookupWord()?.let { dao.deleteWord(it, removedAt) } ?: 0
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
