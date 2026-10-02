package com.vayana.core.homelibrary

import androidx.room.Room
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.entity.BookEntity
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.BookSource
import com.vayana.core.database.model.HomeLibraryDetails
import com.vayana.core.database.sync.FullSyncChangeTrackerCallback
import java.io.InputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The mirror against a real (in-memory) Room database: identity, read-only fields, and the single transaction. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class RoomHomeLibraryStoreTest {
    private lateinit var database: VayanaDatabase
    private lateinit var store: RoomHomeLibraryStore

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), VayanaDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(FullSyncChangeTrackerCallback)
            .build()
        store = RoomHomeLibraryStore(database, database.bookDao())
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    private fun row(uuid: String, updatedAt: Long = 100, formatCode: String? = "hardcover", title: String? = "Title $uuid") =
        HomeBookRow(
            syncUuid = uuid,
            deleted = false,
            updatedAt = updatedAt,
            title = title,
            authors = "Ursula K. Le Guin; Someone Else",
            formatCode = formatCode,
            pageCount = 300,
            rating = 4.5f,
            mainGenre = "Fiction",
            subGenres = listOf("Science fiction"),
            tags = listOf("favourite"),
            publisher = "Ace",
            publishedYear = 1969,
            room = "Study",
            bookcase = "B2",
            shelf = "3",
            hasCover = true,
        )

    private fun changes(vararg rows: HomeBookRow, deletes: Set<String> = emptySet(), retainOnly: Set<String>? = null) =
        HomeLibraryChanges(rows.toList(), deletes, retainOnly)

    private fun ownEpub(syncUuid: String? = null) = BookEntity(
        title = "My EPUB", author = null, series = null, seriesNumber = null, description = null,
        coverPath = null, filePath = "books/my.epub", format = BookFormat.EPUB.name, fileHash = "hash-my",
        lastLocator = null, readingPercent = 0.4f, rating = 0f, groupId = null, isDeleted = false,
        wordCount = null, pageEstimate = null, createdAt = 1, updatedAt = 1, lastReadAt = null,
        syncUuid = syncUuid,
    )

    @Test
    fun createdBookIsAFileLessOfflineRowCarryingHomeLibraryIdentity() = runBlocking<Unit> {
        val applied = store.apply(changes(row("u1", formatCode = "Audiobook")))

        assertEquals(HomeLibraryApplied(created = 1, updated = 0, deleted = 0), applied)
        val book = database.bookDao().findBySyncUuid("u1")!!
        assertEquals(BookFormat.AUDIOBOOK.name, book.format)
        assertEquals(BookSource.HOME_LIBRARY, book.source)
        assertEquals("", book.filePath)
        assertEquals("audiobook:u1", book.fileHash)
        assertEquals("Ursula K. Le Guin, Someone Else", book.author)
        assertEquals("Fiction, Science fiction, favourite", book.tagsCsv)
        assertEquals(300, book.pageEstimate)
        assertEquals(4.5f, book.rating)
        assertEquals(100L, book.sourceUpdatedAt)
        assertTrue(book.sourceHasCover)
        val details = HomeLibraryDetails.fromJson(book.sourceMetadata)
        assertEquals(listOf("Study", "B2", "3"), details.location)
        assertEquals("Ace", details.publisher)
    }

    @Test
    fun updateReplacesCatalogFieldsButKeepsWhatVayanaTracks() = runBlocking<Unit> {
        store.apply(changes(row("u1", formatCode = "hardcover")))
        val created = database.bookDao().findBySyncUuid("u1")!!
        database.bookDao().update(created.copy(startedReadingAt = 5_000, finishedReadingAt = 9_000, readingPercent = 1f))

        val applied = store.apply(changes(row("u1", updatedAt = 200, title = "New title")))

        assertEquals(HomeLibraryApplied(created = 0, updated = 1, deleted = 0), applied)
        val book = database.bookDao().findBySyncUuid("u1")!!
        assertEquals("New title", book.title)
        assertEquals(200L, book.sourceUpdatedAt)
        assertEquals(5_000L, book.startedReadingAt)
        assertEquals(9_000L, book.finishedReadingAt)
        assertEquals(1f, book.readingPercent)
        assertEquals(created.id, book.id)
    }

    @Test
    fun anUnchangedRowWritesNothing() = runBlocking<Unit> {
        store.apply(changes(row("u1")))

        val applied = store.apply(changes(row("u1")))

        assertEquals(HomeLibraryApplied(created = 0, updated = 0, deleted = 0), applied)
    }

    @Test
    fun tombstoneDeletesTheMirroredBook() = runBlocking<Unit> {
        store.apply(changes(row("u1"), row("u2")))

        val applied = store.apply(changes(deletes = setOf("u1")))

        assertEquals(1, applied.deleted)
        assertNull(database.bookDao().findBySyncUuid("u1"))
        assertNotNull(database.bookDao().findBySyncUuid("u2"))
    }

    @Test
    fun fullResyncRemovesStaleMirroredBooksAndNeverTouchesVayanasOwn() = runBlocking<Unit> {
        val ownId = database.bookDao().insert(ownEpub())
        store.apply(changes(row("keep"), row("stale")))

        val applied = store.apply(changes(row("keep"), retainOnly = setOf("keep")))

        assertEquals(1, applied.deleted)
        assertNull(database.bookDao().findBySyncUuid("stale"))
        assertNotNull(database.bookDao().findBySyncUuid("keep"))
        assertEquals("My EPUB", database.bookDao().getById(ownId)!!.title)
    }

    @Test
    fun aTombstoneForAnUnknownBookOrAnOwnBookDeletesNothing() = runBlocking<Unit> {
        val ownId = database.bookDao().insert(ownEpub(syncUuid = "shared-uuid"))

        val applied = store.apply(changes(deletes = setOf("shared-uuid", "never-seen")))

        assertEquals(0, applied.deleted)
        assertNotNull(database.bookDao().getById(ownId))
    }

    @Test
    fun mirroredBooksStayOutOfTheGitHubSnapshotAndTheFullSyncFlag() = runBlocking<Unit> {
        val before = database.fullSyncStateDao().observeRequired().first()

        store.apply(changes(row("u1")))
        store.apply(changes(row("u1", updatedAt = 300, title = "Changed")))

        assertEquals(false, before)
        assertEquals(false, database.fullSyncStateDao().observeRequired().first())
        assertTrue(database.bookDao().getAllForSync().isEmpty())

        database.bookDao().insert(ownEpub())
        assertEquals(true, database.fullSyncStateDao().observeRequired().first())
        assertEquals(listOf("My EPUB"), database.bookDao().getAllForSync().map { it.title })
    }

    @Test
    fun aFailureMidBatchRollsBackEveryChange() = runBlocking<Unit> {
        // An own book already holds this identity, so inserting the mirrored one violates the unique index.
        database.bookDao().insert(ownEpub(syncUuid = "clash"))

        assertFailsWith<Exception> {
            store.apply(changes(row("fresh"), row("clash")))
        }

        assertNull(database.bookDao().findBySyncUuid("fresh")?.takeIf { it.source == BookSource.HOME_LIBRARY })
        assertTrue(database.bookDao().getHomeLibraryBooks().isEmpty())
    }

    @Test
    fun checkpointStaysPutWhenTheRealTransactionFails() = runBlocking<Unit> {
        database.bookDao().insert(ownEpub(syncUuid = "clash"))
        val checkpoints = MemoryCheckpoints()
        val source = OneShotSource(listOf(row("fresh", 100), row("clash", 200)))
        val engine = HomeLibrarySyncEngine(source, store, checkpoints)

        assertFailsWith<Exception> { engine.sync() }

        assertNull(checkpoints.current.lastSyncUpdatedAt)
        assertTrue(database.bookDao().getHomeLibraryBooks().isEmpty())
    }

    @Test
    fun engineMirrorsAndFullResyncRemovesStaleBooksEndToEnd() = runBlocking<Unit> {
        val checkpoints = MemoryCheckpoints()
        val source = OneShotSource(listOf(row("a", 100), row("b", 200)))
        var now = 1_000L
        val engine = HomeLibrarySyncEngine(source, store, checkpoints, clock = { now })

        engine.sync()
        assertEquals(setOf("a", "b"), database.bookDao().getHomeLibraryBooks().mapNotNull { it.syncUuid }.toSet())

        source.rows = listOf(row("b", 200))
        now += 91L * 24 * 60 * 60 * 1000
        engine.sync()

        assertEquals(setOf("b"), database.bookDao().getHomeLibraryBooks().mapNotNull { it.syncUuid }.toSet())
    }

    @Test
    fun formatCodesMapLooselyAndUnknownMeansPaper() {
        assertEquals(BookFormat.AUDIOBOOK, homeLibraryFormat("audiobook"))
        assertEquals(BookFormat.OTHER_EBOOK, homeLibraryFormat("EBOOK"))
        assertEquals(BookFormat.OTHER_EBOOK, homeLibraryFormat("kindle_edition"))
        assertEquals(BookFormat.PHYSICAL, homeLibraryFormat("paperback"))
        assertEquals(BookFormat.PHYSICAL, homeLibraryFormat(null))
    }

    private class MemoryCheckpoints : HomeLibraryCheckpointStore {
        var current = HomeLibraryCheckpoint()
        override val checkpoint get() = kotlinx.coroutines.flow.flowOf(current)
        override suspend fun read() = current
        override suspend fun save(checkpoint: HomeLibraryCheckpoint) {
            current = checkpoint
        }
    }

    private class OneShotSource(var rows: List<HomeBookRow>) : HomeLibrarySource {
        override suspend fun info() = HomeLibraryInfo(1, rows.count { !it.deleted }, rows.maxOfOrNull { it.updatedAt }, null)
        override suspend fun books(updatedSince: Long?, limit: Int) =
            rows.filter { updatedSince == null || it.updatedAt > updatedSince }.sortedBy { it.updatedAt }.take(limit)
        override suspend fun openCover(syncUuid: String): InputStream? = null
    }
}
