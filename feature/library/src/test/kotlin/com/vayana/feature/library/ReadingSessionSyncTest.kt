package com.vayana.feature.library

import androidx.room.Room
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.entity.TombstoneEntity
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ReadingSessionSyncTest {
    private lateinit var db: VayanaDatabase
    private lateinit var books: BookRepositoryImpl
    private lateinit var sessions: ReadingSessionRepositoryImpl

    @BeforeTest fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), VayanaDatabase::class.java)
            .allowMainThreadQueries().build()
        books = BookRepositoryImpl(db, db.bookDao(), db.bookAliasDao(), db.tombstoneDao(), db.readingSessionDao(),
            db.vocabularyCardDao(), db.pendingCloudDeletionDao())
        sessions = ReadingSessionRepositoryImpl(db, db.readingSessionDao(), db.bookDao(), db.bookAliasDao(), db.tombstoneDao())
    }
    @AfterTest fun close() = db.close()

    @Test fun checkpointsSurviveRepositoryRecreationAndOutOfOrderRetriesWithoutDoubleCounting() = runBlocking {
        val book = books.insertOfflineBook("Book", null, BookFormat.PHYSICAL, null, null)
        sessions.recordCheckpoint(book.id, "active", 1000, 61000, 60)
        val recreated = ReadingSessionRepositoryImpl(db, db.readingSessionDao(), db.bookDao(), db.bookAliasDao(), db.tombstoneDao())
        recreated.recordCheckpoint(book.id, "active", 1000, 181000, 180)
        recreated.recordCheckpoint(book.id, "active", 1000, 61000, 60)
        recreated.recordCheckpoint(book.id, "active", 1000, 181000, 180)
        assertEquals(1, db.readingSessionDao().getAllForSync().size)
        assertEquals(180L, db.readingSessionDao().findBySyncId("active")!!.durationSeconds)
        assertEquals(180L, books.getById(book.id)!!.totalReadingSeconds)
    }

    @Test fun invalidOrConflictingCheckpointsAreDroppedWithoutThrowing() = runBlocking {
        val book = books.insertOfflineBook("Book", null, BookFormat.PHYSICAL, null, null)
        sessions.recordCheckpoint(book.id, "backward", 61000, 1000, 60)
        sessions.recordCheckpoint(book.id, "active", 1000, 61000, 60)
        sessions.recordCheckpoint(book.id, "active", 2000, 181000, 180)
        assertNull(db.readingSessionDao().findBySyncId("backward"))
        assertEquals(60L, db.readingSessionDao().findBySyncId("active")!!.durationSeconds)
        assertEquals(60L, books.getById(book.id)!!.totalReadingSeconds)
    }

    @Test fun independentDeviceSessionsAddEvenWhenBookAggregateWasAlreadyPulled() = runBlocking {
        val book = books.insertOfflineBook("Book", null, BookFormat.PHYSICAL, null, null)
        sessions.recordCheckpoint(book.id, "device-a", 1000, 601000, 600)
        // Existing progress sync takes the larger device total: 1200, not the combined 1800.
        db.bookDao().update(db.bookDao().getById(book.id)!!.copy(totalReadingSeconds = 1200))
        val remote = CloudReadingSessionRecord("device-b", book.syncId, 1000000, 2200000, 1200, 0, 10)
        assertEquals(ReadingSessionMergeResult.CREATED, sessions.mergeCloudSession(remote))
        assertEquals(1800L, books.getById(book.id)!!.totalReadingSeconds)
        assertEquals(ReadingSessionMergeResult.SKIPPED, sessions.mergeCloudSession(remote))
        assertEquals(1800L, books.getById(book.id)!!.totalReadingSeconds)
        assertEquals(ReadingSessionMergeResult.UPDATED, sessions.mergeCloudSession(remote.copy(endedAt = 2500000, durationSeconds = 1500)))
        assertEquals(2100L, books.getById(book.id)!!.totalReadingSeconds)
        sessions.mergeCloudSession(remote)
        assertEquals(2100L, books.getById(book.id)!!.totalReadingSeconds)
    }

    @Test fun independentlyImportedBookMatchesByHashWithoutAnAlias() = runBlocking {
        val book = books.insertOfflineBook("Book", null, BookFormat.PHYSICAL, null, null)
        val remote = CloudReadingSessionRecord("remote", "other-device-book-id", 1000, 121000, 120,
            bookFileHash = book.fileHash)
        assertEquals(ReadingSessionMergeResult.CREATED, sessions.mergeCloudSession(remote))
        assertEquals(book.id, db.readingSessionDao().findBySyncId("remote")!!.bookId)
        assertEquals(120L, books.getById(book.id)!!.totalReadingSeconds)
        assertEquals(ReadingSessionMergeResult.SKIPPED, sessions.mergeCloudSession(remote.copy(syncId = "wrong", bookFileHash = "wrong-hash")))
    }

    @Test fun existingSessionCannotMoveToAnotherBookOrStartTime() = runBlocking {
        val a = books.insertOfflineBook("A", null, BookFormat.PHYSICAL, null, null)
        val b = books.insertOfflineBook("B", null, BookFormat.PHYSICAL, null, null)
        val remote = CloudReadingSessionRecord("same", a.syncId, 1000, 61000, 60)
        sessions.mergeCloudSession(remote)
        assertEquals(ReadingSessionMergeResult.SKIPPED, sessions.mergeCloudSession(remote.copy(bookSyncId = b.syncId, endedAt = 121000, durationSeconds = 120)))
        assertEquals(ReadingSessionMergeResult.SKIPPED, sessions.mergeCloudSession(remote.copy(startedAt = 2000, endedAt = 121000, durationSeconds = 120)))
        assertEquals(60L, books.getById(a.id)!!.totalReadingSeconds)
        assertEquals(0L, books.getById(b.id)!!.totalReadingSeconds)
    }

    @Test fun resetBlocksAnUnseenRemoteSessionAndALateLocalCheckpoint() = runBlocking {
        val book = books.insertOfflineBook("Book", null, BookFormat.PHYSICAL, null, null)
        db.tombstoneDao().upsert(TombstoneEntity(syncId = readingProgressResetTombstoneId(book.syncId),
            entityType = TombstoneEntityType.READING_PROGRESS_RESET.value, deletedAt = 100000))
        sessions.recordCheckpoint(book.id, "local", 1000, 61000, 60)
        assertEquals(ReadingSessionMergeResult.SKIPPED, sessions.mergeCloudSession(
            CloudReadingSessionRecord("remote", book.syncId, 1000, 61000, 60)))
        assertTrue(db.readingSessionDao().getAllForSync().isEmpty())
        assertEquals(0L, books.getById(book.id)!!.totalReadingSeconds)
    }

    @Test fun pausedPhysicalIntervalsAndLegacyTotalsArePreserved() = runBlocking {
        val book = books.insertOfflineBook("Book", null, BookFormat.PHYSICAL, null, null)
        db.bookDao().update(db.bookDao().getById(book.id)!!.copy(totalReadingSeconds = 600))
        val remote = CloudReadingSessionRecord("physical", book.syncId, 1000, 181000, 120, 1, 5,
            "1000:61000,121000:181000")
        sessions.mergeCloudSession(remote)
        assertEquals(remote.activeIntervals, db.readingSessionDao().findBySyncId("physical")!!.activeIntervals)
        assertEquals(600L, books.getById(book.id)!!.totalReadingSeconds)
    }

    @Test fun syncedSessionDeletionAdjustsTheBookTotalExactlyOnce() = runBlocking {
        val book = books.insertOfflineBook("Book", null, BookFormat.PHYSICAL, null, null)
        sessions.mergeCloudSession(CloudReadingSessionRecord("a", book.syncId, 1000, 61000, 60))
        sessions.mergeCloudSession(CloudReadingSessionRecord("b", book.syncId, 100000, 220000, 120))
        assertEquals(1, sessions.deleteBySyncId("a"))
        assertEquals(120L, books.getById(book.id)!!.totalReadingSeconds)
        assertEquals(0, sessions.deleteBySyncId("a"))
        assertEquals(120L, books.getById(book.id)!!.totalReadingSeconds)
    }
}
