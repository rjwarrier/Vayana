package com.vayana.feature.library

import androidx.room.Room
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.repository.CloudWordLookupCounter
import com.vayana.core.database.repository.WordLookupCounterMergeResult
import com.vayana.core.database.repository.WordLookupStatRepositoryImpl
import com.vayana.core.database.repository.wordLookupTombstoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Removing a word from Learn words: every device's counter goes, and older synced counters stay out. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class WordLookupRemovalTest {
    private lateinit var database: VayanaDatabase
    private lateinit var words: WordLookupStatRepositoryImpl

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), VayanaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        words = WordLookupStatRepositoryImpl(database.wordLookupStatDao(), database, database.tombstoneDao())
    }

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun removedWordStaysGoneUntilLookedUpAgain() = runBlocking {
        words.recordLookup("Those", "Pixel")
        words.mergeCloudCounter(CloudWordLookupCounter("those", "Reader", 5, 1_000L))
        words.recordLookup("creativity", "Pixel")

        words.removeWord("those")

        assertEquals(listOf("creativity"), words.observeTop(10).first().map { it.word })
        assertNotNull(database.tombstoneDao().findBySyncId(wordLookupTombstoneId("those")))
        // Another device's counter from before the removal doesn't bring it back.
        assertEquals(WordLookupCounterMergeResult.SKIPPED, words.mergeCloudCounter(CloudWordLookupCounter("those", "Reader", 6, 2_000L)))
        // A real lookup afterwards counts afresh.
        words.recordLookup("those", "Pixel")
        assertEquals(1, words.observeTop(10).first().single { it.word == "those" }.count)
    }

    @Test
    fun remoteRemovalOnlyTakesCountersFromBeforeIt() = runBlocking {
        words.mergeCloudCounter(CloudWordLookupCounter("every", "Reader", 3, 1_000L))
        words.mergeCloudCounter(CloudWordLookupCounter("every", "Pixel", 1, 9_000L))
        assertEquals(1, words.applyRemoval("every", removedAt = 5_000L))
        assertEquals(1, words.observeTop(10).first().single().count)
    }
}
