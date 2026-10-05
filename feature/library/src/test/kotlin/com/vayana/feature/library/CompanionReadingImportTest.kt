package com.vayana.feature.library

import androidx.room.Room
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.repository.BookRepositoryImpl
import com.vayana.core.database.repository.CompanionReadingRepository
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CompanionReadingImportTest {
    private lateinit var db: VayanaDatabase
    private lateinit var books: BookRepositoryImpl
    private lateinit var importer: CompanionReadingRepository
    @BeforeTest fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), VayanaDatabase::class.java)
            .allowMainThreadQueries().build()
        books = BookRepositoryImpl(db, db.bookDao(), db.bookAliasDao(), db.tombstoneDao(), db.readingSessionDao(),
            db.vocabularyCardDao(), db.pendingCloudDeletionDao())
        importer = CompanionReadingRepository(db, db.bookDao(), db.readingSessionDao(), db.tombstoneDao())
    }
    @AfterTest fun close() = db.close()
    private suspend fun book() = books.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null, 200, 10)
    private suspend fun receive(book: Book, id: String = "watch-1", start: Long = 1000, startPage: Int = 10, endPage: Int = 20) =
        importer.importSession(book.syncId, id, start, start + 60_000, 60, startPage, endPage, 200, book.updatedAt)

    @Test fun duplicateDeliveryAfterMissingAcknowledgementNeverAddsTimeTwice() = runBlocking {
        val b = book()
        assertEquals("saved", receive(b))
        assertEquals("duplicate", receive(b))
        assertEquals(60L, books.getById(b.id)!!.totalReadingSeconds)
        assertEquals(1, db.readingSessionDao().getAllForSync().size)
        assertEquals(0.1f, books.getById(b.id)!!.readingPercent)
    }
    @Test fun multipleOfflineSessionsImportInOrderAndAdvancePages() = runBlocking {
        val b = book()
        assertEquals("saved", receive(b))
        assertEquals("saved", receive(b, "watch-2", 62_000, 20, 30))
        assertEquals(120L, books.getById(b.id)!!.totalReadingSeconds)
        assertEquals(0.15f, books.getById(b.id)!!.readingPercent)
    }
    @Test fun changedPhoneProgressRetainsWatchCheckpointsWithoutOverwritingPage() = runBlocking {
        val b = book()
        books.updateOfflinePages(b.id, 200, 90)
        assertEquals("page_kept", receive(b))
        assertEquals(0.45f, books.getById(b.id)!!.readingPercent)
        assertEquals(20, db.readingSessionDao().findBySyncId("watch-1")!!.endPage)
        assertEquals(60L, books.getById(b.id)!!.totalReadingSeconds)
    }
    @Test fun overlappingSameBookCountsOnlyNewTimeWithoutChangingExistingRecord() = runBlocking {
        val b = book()
        receive(b)
        val before = db.readingSessionDao().findBySyncId("watch-1")!!
        assertEquals("page_kept", receive(b, "watch-2", 30_000))
        assertEquals(before, db.readingSessionDao().findBySyncId("watch-1"))
        assertEquals(2, db.readingSessionDao().getAllForSync().size)
        assertEquals(89L, books.getById(b.id)!!.totalReadingSeconds)
    }
    @Test fun deletionAndResetDoNotResurrectHistory() = runBlocking {
        val b = book()
        books.softDelete(b.id)
        assertEquals("book_missing", receive(b))
        books.restore(b.id)
        books.resetReadingStats(b.id)
        assertEquals("reset", receive(b))
        assertTrue(db.readingSessionDao().getAllForSync().isEmpty())
    }

    @Test fun pausedGapsDoNotConflictAcrossBooks() = runBlocking {
        val a = book(); val b = book()
        assertEquals("saved", importer.importSession(a.syncId, "paused", 1000, 61000, 20, 10, 20, 200, a.updatedAt,
            "1000:11000,51000:61000"))
        assertEquals("saved", importer.importSession(b.syncId, "gap", 11000, 51000, 40, 10, 20, 200, b.updatedAt, "11000:51000"))
        assertEquals("overlap_other_book", importer.importSession(b.syncId, "actual-overlap", 1000, 11000, 10, 10, 20, 200, b.updatedAt, "1000:11000"))
    }
    @Test fun unknownLegacyPausePlacementRequiresReview() = runBlocking {
        val b = book()
        importer.importSession(b.syncId, "legacy", 1000, 61000, 20, 10, 20, 200, b.updatedAt)
        assertEquals("timing_conflict", receive(b, "new", 30000))
        assertEquals("page_kept", importer.importSession(b.syncId, "new", 30000, 90000, 60, 10, 20, 200, b.updatedAt, resolution = "separate"))
        assertEquals(80L, books.getById(b.id)!!.totalReadingSeconds)
    }
    @Test fun fullyCoveredSessionRetainsIdempotencyMarker() = runBlocking {
        val b = book(); receive(b)
        assertEquals("page_kept", receive(b, "covered"))
        assertEquals(0L, db.readingSessionDao().findBySyncId("covered")!!.durationSeconds)
        assertEquals("", db.readingSessionDao().findBySyncId("covered")!!.activeIntervals)
        assertEquals("duplicate", receive(b, "covered"))
        assertEquals(60L, books.getById(b.id)!!.totalReadingSeconds)
    }
    @Test fun delayedEarlierSessionKeepsLatestPageAndLastReadDate() = runBlocking {
        val b = book(); receive(b, "later", 100000, 10, 40)
        assertEquals("page_kept", receive(b, "earlier"))
        val result = books.getById(b.id)!!
        assertEquals(120L, result.totalReadingSeconds); assertEquals(160000L, result.lastReadAt)
        assertEquals(0.2f, result.readingPercent)
    }
    @Test fun changedClocksAndChangedPayloadsAreNotSilentlyAccepted() = runBlocking {
        val b = book()
        assertEquals("clock_conflict", importer.importSession(b.syncId, "clock", 1000, 61000, 60, 10, 20, 200, b.updatedAt, clockChanged = true))
        assertTrue(db.readingSessionDao().getAllForSync().isEmpty())
        receive(b)
        assertEquals("payload_conflict", receive(b, start = 2000))
        assertEquals(60L, books.getById(b.id)!!.totalReadingSeconds)
    }
    @Test fun pageResolutionNeverCountsTimeAgain() = runBlocking {
        val b = book(); books.updateOfflinePages(b.id, 200, 90)
        receive(b)
        assertEquals("page_applied", importer.importSession(b.syncId, "watch-1", 1000, 61000, 60, 10, 20, 200, b.updatedAt, resolution = "watch_page"))
        assertEquals(60L, books.getById(b.id)!!.totalReadingSeconds)
        assertEquals(0.1f, books.getById(b.id)!!.readingPercent)
    }
}
