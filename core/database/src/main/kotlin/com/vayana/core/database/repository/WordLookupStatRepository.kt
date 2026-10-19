package com.vayana.core.database.repository

import com.vayana.core.database.entity.LegacyWriterOrigin
import com.vayana.core.database.model.WordLookupStat
import kotlinx.coroutines.flow.Flow

data class CloudWordLookupCounter(
    val word: String,
    val writerOrigin: String,
    val count: Int,
    val lastLookedUpAt: Long,
)

enum class WordLookupCounterMergeResult {
    MERGED,
    SKIPPED,
}

interface WordLookupStatRepository {
    fun observeTop(limit: Int): Flow<List<WordLookupStat>>
    fun observeRecent(limit: Int): Flow<List<WordLookupStat>>
    fun observeAllAggregated(limit: Int): Flow<List<WordLookupStat>>
    suspend fun getAllForSync(): List<CloudWordLookupCounter>
    suspend fun recordLookup(word: String, writerOrigin: String = LegacyWriterOrigin)
    suspend fun mergeCloudCounter(counter: CloudWordLookupCounter): WordLookupCounterMergeResult

    /** Removes [word] from Learn words here and, through a synced tombstone, on every device. */
    suspend fun removeWord(word: String)

    /** Applies another device's removal: counters last looked up by [removedAt] go. */
    suspend fun applyRemoval(word: String, removedAt: Long): Int
}
