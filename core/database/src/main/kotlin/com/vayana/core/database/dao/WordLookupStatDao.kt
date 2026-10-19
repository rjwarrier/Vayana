package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    // No `INSERT … ON CONFLICT DO UPDATE` here: upserts need SQLite 3.24 (Android 11), and the app runs from Android 8.
    // Callers insert if absent, then update.

    /** -1 when the (word, writerOrigin) row already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(stat: WordLookupStatEntity): Long

    @Query(
        "UPDATE word_lookup_stats SET count = count + 1, lastLookedUpAt = :lookedUpAt " +
            "WHERE word = :word AND writerOrigin = :writerOrigin",
    )
    suspend fun incrementLookup(word: String, writerOrigin: String, lookedUpAt: Long)

    /** Takes the larger count and later lookup; a counter that is no newer changes nothing (and fires no triggers). */
    @Query(
        "UPDATE word_lookup_stats SET count = MAX(count, :count), lastLookedUpAt = MAX(lastLookedUpAt, :lastLookedUpAt) " +
            "WHERE word = :word AND writerOrigin = :writerOrigin AND (:count > count OR :lastLookedUpAt > lastLookedUpAt)",
    )
    suspend fun raiseCounter(word: String, writerOrigin: String, count: Int, lastLookedUpAt: Long): Int

    /** Every device's counter for [word] last looked up by [before]; a later lookup is a fresh one and stays. */
    @Query("DELETE FROM word_lookup_stats WHERE word = :word AND lastLookedUpAt <= :before")
    suspend fun deleteWord(word: String, before: Long): Int

    @Query("SELECT * FROM word_lookup_stats ORDER BY word COLLATE NOCASE ASC, writerOrigin ASC")
    suspend fun getAllForSync(): List<WordLookupStatEntity>
}
