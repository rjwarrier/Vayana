package com.vayana.core.database.repository

import com.vayana.core.database.model.WordLookupStat
import kotlinx.coroutines.flow.Flow

interface WordLookupStatRepository {
    fun observeTop(limit: Int): Flow<List<WordLookupStat>>
    suspend fun recordLookup(word: String)
}
