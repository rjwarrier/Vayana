package com.vayana.feature.library

import androidx.room.Room
import com.vayana.core.database.VayanaDatabase
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
class PhysicalSessionDeletionTest {
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

    @Test fun deletingOneSessionSubtractsOnlyItsTimeAndPreservesProgressAndOtherLogs() = runBlocking {
        val book = books.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null, 194, 128)
        books.recordPhysicalReadingSession(book.id, "first", 1000, 241000, 240, 128, 132, 194)
        books.recordPhysicalReadingSession(book.id, "second", 242000, 362000, 120, 132, 140, 194)
        val before = books.getById(book.id)!!
        val other = db.readingSessionDao().findBySyncId("second")!!
        assertTrue(sessions.deletePhysicalSession(book.id, "first"))
        assertNull(db.readingSessionDao().findBySyncId("first"))
        val after = books.getById(book.id)!!
        assertEquals(120L, after.totalReadingSeconds)
        assertEquals(before.readingPercent, after.readingPercent)
        assertEquals(before.startedReadingAt, after.startedReadingAt)
        assertEquals(before.finishedReadingAt, after.finishedReadingAt)
        assertEquals(before.lastReadAt, after.lastReadAt)
        assertEquals(other, db.readingSessionDao().findBySyncId("second"))
        assertEquals(TombstoneEntityType.READING_SESSION.value, db.tombstoneDao().findBySyncId("first")!!.entityType)
        assertFalse(sessions.deletePhysicalSession(book.id, "first"))
        assertEquals(120L, books.getById(book.id)!!.totalReadingSeconds)
    }
    @Test fun deletedSessionCannotBeRestoredByCloudOrWatchRetry() = runBlocking {
        val book = books.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null, 100, 0)
        books.recordPhysicalReadingSession(book.id, "deleted", 1000, 61000, 60, 0, 20, 100)
        assertTrue(sessions.deletePhysicalSession(book.id, "deleted"))
        assertEquals(0L, books.getById(book.id)!!.totalReadingSeconds)
        assertEquals(ReadingSessionMergeResult.SKIPPED, sessions.mergeCloudSession(
            CloudReadingSessionRecord("deleted", book.syncId, 1000, 61000, 60, 0, 20)))
        val companion = CompanionReadingRepository(db, db.bookDao(), db.readingSessionDao(), db.tombstoneDao())
        assertEquals("reset", companion.importSession(book.syncId, "deleted", 1000, 61000, 60, 0, 20, 100, book.updatedAt))
        assertTrue(db.readingSessionDao().getAllForSync().isEmpty())
    }
    @Test fun wrongBookCannotDeleteAnotherBooksSession() = runBlocking {
        val a = books.insertOfflineBook("A", null, BookFormat.PHYSICAL, null, null)
        val b = books.insertOfflineBook("B", null, BookFormat.PHYSICAL, null, null)
        books.recordPhysicalReadingSession(a.id, "a-session", 1000, 61000, 60, 1, 2, 100)
        assertFailsWith<IllegalArgumentException> { sessions.deletePhysicalSession(b.id, "a-session") }
        assertNotNull(db.readingSessionDao().findBySyncId("a-session"))
        assertNull(db.tombstoneDao().findBySyncId("a-session"))
        assertEquals(60L, books.getById(a.id)!!.totalReadingSeconds)
    }
}
