package com.vayana.feature.library

import androidx.room.Room
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.entity.AnnotationEntity
import com.vayana.core.database.entity.ReadingSessionEntity
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.repository.BookRepositoryImpl
import com.vayana.core.database.repository.CloudWordLookupCounter
import com.vayana.core.database.repository.WordLookupCounterMergeResult
import com.vayana.core.database.repository.WordLookupStatRepositoryImpl
import com.vayana.core.database.sync.FullSyncChangeTrackerCallback
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The triggers behind full sync's "unchanged slice" skip: each write bumps exactly the slices it can change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SyncSliceVersionsTest {
    private lateinit var database: VayanaDatabase
    private lateinit var books: BookRepositoryImpl

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), VayanaDatabase::class.java)
            .addCallback(FullSyncChangeTrackerCallback)
            .allowMainThreadQueries()
            .build()
        books = BookRepositoryImpl(
            database = database,
            bookDao = database.bookDao(),
            bookAliasDao = database.bookAliasDao(),
            tombstoneDao = database.tombstoneDao(),
            readingSessionDao = database.readingSessionDao(),
            vocabularyCardDao = database.vocabularyCardDao(),
            pendingCloudDeletionDao = database.pendingCloudDeletionDao(),
        )
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun writesBumpOnlyTheSlicesTheyChange() = runBlocking {
        val book = books.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null)
        var before = versions()
        assertEquals(9, before.size)

        database.annotationDao().insert(annotation(book.id, "note-1", "epubcfi(/6/2)"))
        assertEquals(setOf("annotations"), changedSince(before))

        before = versions()
        val quoteId = database.annotationDao().insert(annotation(book.id, "quote-1", "goodreads-quote:1"))
        assertEquals(setOf("communityQuotes"), changedSince(before))

        before = versions()
        val quote = database.annotationDao().getById(quoteId)!!
        database.annotationDao().update(quote.copy(locator = "epubcfi(/6/4)"))
        assertEquals(setOf("annotations", "communityQuotes"), changedSince(before)) // moved between slices

        before = versions()
        database.readingSessionDao().insert(ReadingSessionEntity(bookId = book.id, startedAt = 1, endedAt = 61, durationSeconds = 60))
        assertEquals(setOf("readingSessions"), changedSince(before))

        before = versions()
        val row = database.bookDao().getById(book.id)!!
        database.bookDao().update(row.copy(title = "Renamed"))
        assertEquals(emptySet(), changedSince(before)) // a title isn't in any slice

        database.bookDao().update(row.copy(fileHash = "physical:other"))
        assertEquals(
            setOf("annotations", "communityQuotes", "shelfMemberships", "readingSessions", "vocabularyCards"),
            changedSince(before),
        )
    }

    @Test
    fun mergingACounterThatIsNoNewerWritesNothing() = runBlocking {
        // Robolectric's SQLite is 3.22 (as on Android 8–10): this also proves no upsert syntax is needed.
        val counters = WordLookupStatRepositoryImpl(database.wordLookupStatDao(), database, database.tombstoneDao())
        val counter = CloudWordLookupCounter(word = "Serendipity", writerOrigin = "phone", count = 3, lastLookedUpAt = 100)
        assertEquals(WordLookupCounterMergeResult.MERGED, counters.mergeCloudCounter(counter))
        val before = versions()

        assertEquals(WordLookupCounterMergeResult.SKIPPED, counters.mergeCloudCounter(counter))
        assertEquals(WordLookupCounterMergeResult.SKIPPED, counters.mergeCloudCounter(counter.copy(count = 2, lastLookedUpAt = 50)))
        assertEquals(emptySet(), changedSince(before))

        assertEquals(WordLookupCounterMergeResult.MERGED, counters.mergeCloudCounter(counter.copy(count = 4)))
        assertEquals(setOf("wordLookupCounters"), changedSince(before))
        counters.recordLookup("serendipity", "phone")
        assertEquals(5, database.wordLookupStatDao().getAllForSync().single().count)
    }

    private suspend fun versions(): Map<String, Long> =
        database.syncedSnapshotSliceDao().getSliceVersions().associate { it.sliceKey to it.version }

    private suspend fun changedSince(before: Map<String, Long>): Set<String> =
        versions().filter { (key, version) -> before[key] != version }.keys

    private fun annotation(bookId: Long, syncId: String, locator: String) = AnnotationEntity(
        syncId = syncId,
        bookId = bookId,
        type = "HIGHLIGHT",
        colorKey = "yellow",
        locator = locator,
        chapterTitle = null,
        chapterHref = null,
        selectedText = "text",
        readerNote = null,
        createdAt = 1,
        updatedAt = 1,
    )
}
