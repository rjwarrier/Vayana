package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.vayana.core.database.entity.WordLookupStatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordLookupStatDao {
    @Query("SELECT * FROM word_lookup_stats ORDER BY count DESC, lastLookedUpAt DESC LIMIT :limit")
    fun observeTop(limit: Int): Flow<List<WordLookupStatEntity>>

    @Query(
        """
        INSERT INTO word_lookup_stats (word, count, lastLookedUpAt)
        VALUES (:word, 1, :lookedUpAt)
        ON CONFLICT(word) DO UPDATE SET count = count + 1, lastLookedUpAt = :lookedUpAt
        """,
    )
    suspend fun recordLookup(word: String, lookedUpAt: Long)
}
