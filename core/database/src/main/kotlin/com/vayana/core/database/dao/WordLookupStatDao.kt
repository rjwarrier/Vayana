package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.vayana.core.database.entity.WordLookupStatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordLookupStatDao {
    @Query(
        """
        SELECT word, SUM(count) AS count, MAX(lastLookedUpAt) AS lastLookedUpAt, 'aggregate' AS writerOrigin
        FROM word_lookup_stats
        GROUP BY word
        ORDER BY count DESC, lastLookedUpAt DESC
        LIMIT :limit
        """,
    )
    fun observeTop(limit: Int): Flow<List<WordLookupStatEntity>>

    @Query(
        """
        SELECT word, SUM(count) AS count, MAX(lastLookedUpAt) AS lastLookedUpAt, 'aggregate' AS writerOrigin
        FROM word_lookup_stats
        GROUP BY word
        ORDER BY lastLookedUpAt DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<WordLookupStatEntity>>

    @Query(
        """
        INSERT INTO word_lookup_stats (word, writerOrigin, count, lastLookedUpAt)
        VALUES (:word, :writerOrigin, 1, :lookedUpAt)
        ON CONFLICT(word, writerOrigin) DO UPDATE SET count = count + 1, lastLookedUpAt = :lookedUpAt
        """,
    )
    suspend fun recordLookup(word: String, lookedUpAt: Long, writerOrigin: String = "legacy-local")

    @Query(
        """
        INSERT INTO word_lookup_stats (word, writerOrigin, count, lastLookedUpAt)
        VALUES (:word, :writerOrigin, :count, :lastLookedUpAt)
        ON CONFLICT(word, writerOrigin) DO UPDATE SET
            count = CASE WHEN excluded.count > count THEN excluded.count ELSE count END,
            lastLookedUpAt = CASE WHEN excluded.lastLookedUpAt > lastLookedUpAt THEN excluded.lastLookedUpAt ELSE lastLookedUpAt END
        """,
    )
    suspend fun mergeCounter(word: String, writerOrigin: String, count: Int, lastLookedUpAt: Long)

    @Query("SELECT * FROM word_lookup_stats ORDER BY word COLLATE NOCASE ASC, writerOrigin ASC")
    suspend fun getAllForSync(): List<WordLookupStatEntity>
}
