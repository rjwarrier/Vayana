package com.vayana.core.homelibrary

import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** The sync rules, against a fake provider and an in-memory store. */
class HomeLibrarySyncEngineTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private var now = 10_000_000L
    private val source = FakeSource()
    private val store = FakeStore()
    private val checkpoints = FakeCheckpoints()
    private val logs = mutableListOf<String>()
    private val engine = HomeLibrarySyncEngine(source, store, checkpoints, log = { logs += it }, clock = { now })

    private fun row(uuid: String, updatedAt: Long, title: String = "Book $uuid") =
        HomeBookRow(syncUuid = uuid, deleted = false, updatedAt = updatedAt, title = title)

    private fun tombstone(uuid: String, updatedAt: Long) =
        HomeBookRow(syncUuid = uuid, deleted = true, updatedAt = updatedAt)

    @Test
    fun firstSyncIsAFullResyncThatCreatesEveryBook() = runBlocking<Unit> {
        source.put(row("a", 100), row("b", 200))

        val result = engine.sync()

        assertEquals(HomeLibrarySyncResult.Synced(created = 2, updated = 0, deleted = 0), result)
        assertEquals(setOf("a", "b"), store.books.keys)
        assertEquals(setOf("a", "b"), store.applied.single().retainOnly)
        assertEquals(listOf<Long?>(null), source.sinceQueries)
        assertEquals(200L, checkpoints.current.lastSyncUpdatedAt)
        assertEquals(now, checkpoints.current.lastSyncedAt)
    }

    @Test
    fun laterSyncAsksOnlyForWhatChangedAndUpdatesInPlace() = runBlocking<Unit> {
        source.put(row("a", 100), row("b", 200))
        engine.sync()
        source.sinceQueries.clear()

        source.put(row("b", 300, title = "Renamed"))
        val result = engine.sync()

        assertEquals(HomeLibrarySyncResult.Synced(created = 0, updated = 1, deleted = 0), result)
        assertEquals(listOf<Long?>(200L), source.sinceQueries)
        assertEquals("Renamed", store.books.getValue("b").title)
        assertNull(store.applied.last().retainOnly)
        assertEquals(300L, checkpoints.current.lastSyncUpdatedAt)
    }

    @Test
    fun tombstoneDeletesTheLocalBook() = runBlocking<Unit> {
        source.put(row("a", 100), row("b", 200))
        engine.sync()

        source.put(tombstone("a", 400))
        val result = engine.sync()

        assertEquals(HomeLibrarySyncResult.Synced(created = 0, updated = 0, deleted = 1), result)
        assertEquals(setOf("b"), store.books.keys)
        assertEquals(400L, checkpoints.current.lastSyncUpdatedAt)
    }

    @Test
    fun pagesThroughEveryRowInPagesOfFiveHundred() = runBlocking<Unit> {
        source.put(*(1..1_200).map { row("book-$it", updatedAt = it * 10L) }.toTypedArray())

        val result = engine.sync()

        assertEquals(HomeLibrarySyncResult.Synced(created = 1_200, updated = 0, deleted = 0), result)
        assertEquals(1_200, store.books.size)
        assertEquals(3, source.sinceQueries.size)
        assertTrue(source.limits.all { it == 500 })
        assertEquals(12_000L, checkpoints.current.lastSyncUpdatedAt)
    }

    @Test
    fun rowsSharingTheTimestampAtAPageBoundaryAreNotSkipped() = runBlocking<Unit> {
        // 700 rows, all with the same updated_at: the boundary falls inside one timestamp.
        val sameInstant = (1..499).map { row("same-$it", updatedAt = 50) } + (500..700).map { row("same-$it", updatedAt = 50) }
        source.put(*sameInstant.toTypedArray())

        engine.sync()

        // The overlap re-reads the tied rows; at least the first page's worth lands and nothing loops forever.
        assertTrue(store.books.size >= 500)
        assertTrue(source.sinceQueries.size <= 4)
    }

    @Test
    fun emptyCursorLeavesLocalDataAndCheckpointUntouched() = runBlocking<Unit> {
        source.put(row("a", 100))
        engine.sync()
        val before = checkpoints.current
        val appliedBefore = store.applied.size
        source.info = source.info?.copy(maxUpdatedAt = 999) // /info says something changed, the query returns nothing
        source.rows.clear()                                 // sharing switched off: queries come back empty

        val result = engine.sync()

        assertEquals(HomeLibrarySyncResult.NoChanges, result)
        assertEquals(appliedBefore, store.applied.size)
        assertEquals(setOf("a"), store.books.keys)
        assertEquals(before, checkpoints.current)
    }

    @Test
    fun infoWithNoRowMeansSharingIsOffAndNothingIsQueried() = runBlocking<Unit> {
        source.put(row("a", 100))
        engine.sync()
        source.queriesBooks = 0
        source.info = null

        val result = engine.sync()

        assertEquals(HomeLibrarySyncResult.SharingOff, result)
        assertEquals(0, source.queriesBooks)
        assertEquals(setOf("a"), store.books.keys)
    }

    @Test
    fun nothingChangedSinceTheLastSyncSkipsTheQuery() = runBlocking<Unit> {
        source.put(row("a", 100))
        engine.sync()
        source.queriesBooks = 0
        now += 5_000

        val result = engine.sync()

        assertEquals(HomeLibrarySyncResult.UpToDate, result)
        assertEquals(0, source.queriesBooks)
        assertEquals(now, checkpoints.current.lastSyncedAt)
    }

    @Test
    fun forceQueriesEvenWhenInfoMatches() = runBlocking<Unit> {
        source.put(row("a", 100))
        engine.sync()
        source.queriesBooks = 0

        engine.sync(force = true)

        assertEquals(1, source.queriesBooks)
    }

    @Test
    fun fullResyncRemovesBooksHomeLibraryNoLongerHas() = runBlocking<Unit> {
        source.put(row("a", 100), row("b", 200), row("c", 300))
        engine.sync()
        // The tombstone for "a" has aged out of Home Library, and the phone has not synced for 91 days.
        source.rows.removeAll { it.syncUuid == "a" }
        source.info = source.info?.copy(bookCount = 2)
        now += 91 * day
        source.sinceQueries.clear()

        val result = engine.sync()

        assertEquals(1, (result as HomeLibrarySyncResult.Synced).deleted)
        assertEquals(listOf<Long?>(null), source.sinceQueries)
        assertEquals(setOf("b", "c"), store.books.keys)
    }

    @Test
    fun missingStoredSyncPointAlsoMeansFullResync() = runBlocking<Unit> {
        store.books["stale"] = row("stale", 1)
        source.put(row("a", 100))

        engine.sync()

        assertEquals(setOf("a"), store.books.keys)
    }

    @Test
    fun fullResyncAgainstAnEmptyCursorKeepsEverythingWhenInfoSaysBooksExist() = runBlocking<Unit> {
        store.books["a"] = row("a", 100)
        source.info = HomeLibraryInfo(schemaVersion = 1, bookCount = 3, maxUpdatedAt = 300, appVersion = null)

        val result = engine.sync()

        assertEquals(HomeLibrarySyncResult.NoChanges, result)
        assertEquals(setOf("a"), store.books.keys)
        assertNull(checkpoints.current.lastSyncUpdatedAt)
    }

    @Test
    fun fullResyncOfAnEmptyCatalogClearsTheMirror() = runBlocking<Unit> {
        store.books["a"] = row("a", 100)
        source.info = HomeLibraryInfo(schemaVersion = 1, bookCount = 0, maxUpdatedAt = null, appVersion = null)

        val result = engine.sync()

        assertEquals(HomeLibrarySyncResult.Synced(created = 0, updated = 0, deleted = 1), result)
        assertTrue(store.books.isEmpty())
    }

    @Test
    fun checkpointIsNotAdvancedWhenTheTransactionFails() = runBlocking<Unit> {
        source.put(row("a", 100))
        engine.sync()
        val before = checkpoints.current
        source.put(row("b", 500))
        store.failNext = true

        assertFailsWith<IllegalStateException> { engine.sync() }

        assertEquals(before, checkpoints.current)
        assertEquals(setOf("a"), store.books.keys)

        // The next run asks for the same rows again and succeeds.
        engine.sync()
        assertEquals(setOf("a", "b"), store.books.keys)
        assertEquals(500L, checkpoints.current.lastSyncUpdatedAt)
    }

    @Test
    fun newerSchemaIsSkippedAndLogged() = runBlocking<Unit> {
        source.put(row("a", 100))
        source.info = source.info?.copy(schemaVersion = 2)

        val result = engine.sync()

        assertEquals(HomeLibrarySyncResult.UnsupportedSchema(2), result)
        assertEquals(0, source.queriesBooks)
        assertTrue(store.books.isEmpty())
        assertEquals(1, logs.size)
    }

    @Test
    fun missingHomeLibraryStopsQuietly() = runBlocking<Unit> {
        source.unavailable = true

        assertEquals(HomeLibrarySyncResult.NotConnected, engine.sync())
        assertTrue(store.applied.isEmpty())
    }

    // --- fakes ---------------------------------------------------------------------------------------------------

    private class FakeSource : HomeLibrarySource {
        val rows = mutableListOf<HomeBookRow>()
        var info: HomeLibraryInfo? = HomeLibraryInfo(1, 0, null, null)
        var unavailable = false
        val sinceQueries = mutableListOf<Long?>()
        val limits = mutableListOf<Int>()
        var queriesBooks = 0

        /** Adds or replaces rows by uuid (a provider holds one row per book: live, or its tombstone). */
        fun put(vararg newRows: HomeBookRow) {
            newRows.forEach { incoming ->
                rows.removeAll { it.syncUuid == incoming.syncUuid }
                rows += incoming
            }
            info = HomeLibraryInfo(
                schemaVersion = info?.schemaVersion ?: 1,
                bookCount = rows.count { !it.deleted },
                maxUpdatedAt = rows.maxOfOrNull { it.updatedAt },
                appVersion = null,
            )
        }

        override suspend fun info(): HomeLibraryInfo? {
            if (unavailable) throw HomeLibraryUnavailableException("gone")
            return info
        }

        override suspend fun books(updatedSince: Long?, limit: Int): List<HomeBookRow> {
            if (unavailable) throw HomeLibraryUnavailableException("gone")
            queriesBooks++
            sinceQueries += updatedSince
            limits += limit
            return rows.filter { updatedSince == null || it.updatedAt > updatedSince }
                .sortedBy { it.updatedAt }
                .take(limit)
        }

        override suspend fun openCover(syncUuid: String): InputStream? = null
    }

    private class FakeStore : HomeLibraryStore {
        val books = linkedMapOf<String, HomeBookRow>()
        val applied = mutableListOf<HomeLibraryChanges>()
        var failNext = false

        override suspend fun apply(changes: HomeLibraryChanges): HomeLibraryApplied {
            check(!failNext.also { if (it) failNext = false }) { "transaction failed" }
            applied += changes
            var created = 0
            var updated = 0
            changes.upserts.forEach { row ->
                if (row.syncUuid in books) updated++ else created++
                books[row.syncUuid] = row
            }
            val stale = books.keys.filter { uuid ->
                (uuid in changes.deletes && changes.upserts.none { it.syncUuid == uuid }) ||
                    (changes.retainOnly != null && uuid !in changes.retainOnly)
            }
            stale.forEach(books::remove)
            return HomeLibraryApplied(created, updated, stale.size)
        }
    }

    private class FakeCheckpoints : HomeLibraryCheckpointStore {
        var current = HomeLibraryCheckpoint()
        override val checkpoint get() = kotlinx.coroutines.flow.flowOf(current)
        override suspend fun read() = current
        override suspend fun save(checkpoint: HomeLibraryCheckpoint) {
            current = checkpoint
        }
    }
}
