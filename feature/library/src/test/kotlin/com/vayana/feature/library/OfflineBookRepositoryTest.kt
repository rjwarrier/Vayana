package com.vayana.feature.library

import androidx.room.Room
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.PhysicalBookOwnership
import com.vayana.core.database.repository.BookRepositoryImpl
import com.vayana.core.database.repository.CloudBookMergeResult
import com.vayana.core.database.repository.CloudBookRecord
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Books read outside the app: created without a file, dated by hand, and synced as metadata only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class OfflineBookRepositoryTest {
    private lateinit var database: VayanaDatabase
    private lateinit var repository: BookRepositoryImpl

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), VayanaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BookRepositoryImpl(
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
    fun physicalTimerQueriesKeepSummaryCompleteAndSamplesBoundedAndBookSpecific() = runBlocking {
        val book = repository.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null)
        val other = repository.insertOfflineBook("Other", null, BookFormat.PHYSICAL, null, null)
        val sessions = com.vayana.core.database.repository.ReadingSessionRepositoryImpl(
            database, database.readingSessionDao(), database.bookDao(), database.bookAliasDao(), database.tombstoneDao())
        assertEquals(0L, sessions.observePhysicalSummary(book.id).first().sessionCount)
        assertEquals(0L, sessions.observePhysicalSummary(book.id).first().averageSeconds)
        val dao = database.readingSessionDao()
        for (index in 1..100) dao.insert(com.vayana.core.database.entity.ReadingSessionEntity(
            syncId = "paper-$index", bookId = book.id, startedAt = 1000L + index, endedAt = 200000L,
            durationSeconds = index.toLong(), startPage = 0, endPage = if (index > 90) 0 else 10))
        dao.insert(com.vayana.core.database.entity.ReadingSessionEntity(syncId = "untimed-pages", bookId = book.id,
            startedAt = 9999L, endedAt = 200000L, durationSeconds = 999L))
        dao.insert(com.vayana.core.database.entity.ReadingSessionEntity(syncId = "other", bookId = other.id,
            startedAt = 9999L, endedAt = 200000L, durationSeconds = 999L, startPage = 0, endPage = 10))
        val summary = sessions.observePhysicalSummary(book.id).first()
        assertEquals(100L, summary.sessionCount)
        assertEquals(5050L, summary.totalSeconds)
        assertEquals(50L, summary.averageSeconds)
        assertEquals(listOf("paper-100", "paper-99", "paper-98"),
            sessions.observeRecentPhysicalSessions(book.id, 3).first().map { it.syncId })
        assertEquals(listOf("paper-90", "paper-89", "paper-88", "paper-87", "paper-86"),
            sessions.observeRecentPhysicalSessions(book.id, 5, forwardOnly = true).first().map { it.syncId })
        assertEquals(101, sessions.observeForBook(book.id).first().size)
        sessions.deleteBySyncId("paper-100")
        assertEquals(99L, sessions.observePhysicalSummary(book.id).first().sessionCount)
        assertEquals("paper-99", sessions.observeRecentPhysicalSessions(book.id, 3).first().first().syncId)
    }

    @Test
    fun markingPhysicalBookReadingRecordsRecencyWithoutInventingTimeOrPages() = runBlocking {
        val book = repository.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null)
        repository.updateOfflinePages(book.id, 200, 0)
        repository.markPhysicalBookReading(book.id)
        val reading = repository.getById(book.id)!!
        assertEquals(BookReadingState.READING, reading.readingState())
        assertTrue(reading.startedReadingAt != null)
        assertTrue(reading.lastReadAt != null)
        assertEquals(0L, reading.totalReadingSeconds)
        assertEquals(0f, reading.readingPercent)
        assertTrue(database.readingSessionDao().getAllForSync().isEmpty())
        repository.recordPhysicalReadingSession(book.id, "logged", 1000, 61000, 60, 0, 20, 200)
        repository.markPhysicalBookReading(book.id)
        assertEquals(60L, repository.getById(book.id)!!.totalReadingSeconds)
        assertEquals(0.1f, repository.getById(book.id)!!.readingPercent)
    }

    @Test
    fun physicalHomeShowsMarkedAndLoggedBooksWithNewestFirstAndActiveTimerPinned() = runBlocking {
        val first = repository.insertOfflineBook("First", null, BookFormat.PHYSICAL, null, null)
        val second = repository.insertOfflineBook("Second", null, BookFormat.PHYSICAL, null, null)
        val unread = repository.insertOfflineBook("Unread", null, BookFormat.PHYSICAL, null, null)
        val finished = repository.insertOfflineBook("Finished", null, BookFormat.PHYSICAL, 1000, 2000)
        val audio = repository.insertOfflineBook("Audio", null, BookFormat.AUDIOBOOK, 1000, null)
        val books = listOf(first.copy(startedReadingAt = 1000, lastReadAt = 2000),
            second.copy(startedReadingAt = 2000, lastReadAt = 3000), unread, finished, audio)
        assertEquals(listOf(second.id, first.id), physicalReadingHomeBooks(books, null).map { it.id })
        assertEquals(listOf(first.id, second.id), physicalReadingHomeBooks(books, first.id).map { it.id })
        assertEquals(3, physicalReadingHomeBooks(books + (10..16).map {
            first.copy(id = it.toLong(), startedReadingAt = 1000)
        }, null).size)
    }

    @Test
    fun startingTimerMarksBookAndInvalidBookCannotLeaveAnActiveTimer() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("physical_reading_timer", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val timer = PhysicalReadingTimerController(context, repository)
        kotlin.test.assertFailsWith<IllegalArgumentException> { timer.start(Long.MAX_VALUE, "Missing", 0, 100) }
        assertNull(timer.session.value)
        val book = repository.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null)
        timer.start(book.id, book.title, 0, 100)
        assertEquals(BookReadingState.READING, repository.getById(book.id)!!.readingState())
        assertTrue(repository.getById(book.id)!!.lastReadAt != null)
        assertEquals(0L, repository.getById(book.id)!!.totalReadingSeconds)
        timer.discard()
    }

    @Test
    fun physicalTimerSaveIsAtomicAndIdempotentAndUpdatesProgress() = runBlocking {
        val book = repository.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null)
        repository.updateOfflinePages(book.id, 200, 40)
        val priorLastRead = repository.getById(book.id)!!.lastReadAt ?: 0L
        repeat(2) { repository.recordPhysicalReadingSession(book.id, "physical-session", 1000, 601000, 600, 40, 50, 200) }
        val saved = repository.getById(book.id)!!
        assertEquals(600L, saved.totalReadingSeconds)
        assertEquals(0.25f, saved.readingPercent)
        assertEquals(maxOf(priorLastRead, 601000L), saved.lastReadAt)
        val logs = database.readingSessionDao().getAllForSync()
        assertEquals(1, logs.size)
        assertEquals(40, logs.single().startPage)
        assertEquals(50, logs.single().endPage)
        repository.recordPhysicalReadingSession(book.id, "finish-session", 602000, 1202000, 600, 50, 200, 200)
        assertEquals(1202000L, repository.getById(book.id)!!.finishedReadingAt)
    }

    @Test
    fun rejectedPageRollsBackSessionAndReadingTime() = runBlocking {
        val book = repository.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null)
        repository.updateOfflinePages(book.id, 100, 10)
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            repository.recordPhysicalReadingSession(book.id, "bad-session", 1000, 61000, 60, 10, 101, null)
        }
        assertEquals(0L, repository.getById(book.id)!!.totalReadingSeconds)
        assertTrue(database.readingSessionDao().getAllForSync().isEmpty())
    }

    @Test
    fun timerSurvivesControllerRecreationAndPausedTimeIsNotLogged() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("physical_reading_timer", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val book = repository.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null)
        val timer = PhysicalReadingTimerController(context, repository)
        timer.start(book.id, book.title, 10, 100)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(60))
        timer.pause()
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(600))
        val restored = PhysicalReadingTimerController(context, repository)
        assertEquals(PhysicalTimerPhase.PAUSED, restored.session.value!!.phase)
        restored.stop()
        val retry = PhysicalReadingTimerController(context, repository)
        restored.save(20, 100)
        retry.save(20, 100)
        assertEquals(60L, repository.getById(book.id)!!.totalReadingSeconds)
        assertEquals(1, database.readingSessionDao().getAllForSync().size)
        assertNull(PhysicalReadingTimerController(context, repository).session.value)
    }

    @Test
    fun rebootRecoveryPersistsPausedTimerWithCurrentBootCount() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("physical_reading_timer", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val book = repository.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null)
        PhysicalReadingTimerController(context, repository).start(book.id, book.title, 10, 100)
        val stored = org.json.JSONObject(preferences.getString("session", null)!!)
            .put("bootCount", -1)
            .put("phase", PhysicalTimerPhase.RUNNING.name)
        preferences.edit().putString("session", stored.toString()).commit()

        val restored = PhysicalReadingTimerController(context, repository)
        assertEquals(PhysicalTimerPhase.PAUSED, restored.session.value!!.phase)
        restored.restoreAfterBoot()

        val persisted = org.json.JSONObject(preferences.getString("session", null)!!)
        assertEquals(PhysicalTimerPhase.PAUSED.name, persisted.getString("phase"))
        assertEquals(restored.session.value!!.bootCount, persisted.getInt("bootCount"))
    }

    @Test
    fun repeatedTimerControlsPreserveStoredStateAndDuration() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("physical_reading_timer", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val book = repository.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null)
        val timer = PhysicalReadingTimerController(context, repository)
        timer.start(book.id, book.title, 10, 100)
        val running = preferences.getString("session", null)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(10))
        timer.resume()
        assertEquals(running, preferences.getString("session", null))
        timer.pause()
        val paused = preferences.getString("session", null)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(100))
        timer.pause()
        assertEquals(paused, preferences.getString("session", null))
        timer.stop()
        val stopped = preferences.getString("session", null)
        timer.stop()
        assertEquals(stopped, preferences.getString("session", null))
        timer.save(20, 100)
        assertEquals(10L, repository.getById(book.id)!!.totalReadingSeconds)
    }

    @Test
    fun invalidStartDoesNotCreateTimerOrReplaceActiveSession() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("physical_reading_timer", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val book = repository.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null)
        val timer = PhysicalReadingTimerController(context, repository)
        kotlin.test.assertFailsWith<IllegalArgumentException> { timer.start(book.id, book.title, 101, 100) }
        assertNull(timer.session.value)
        timer.start(book.id, book.title, 10, 100)
        val active = timer.session.value
        kotlin.test.assertFailsWith<IllegalStateException> { timer.start(book.id, book.title, 20, 100) }
        assertEquals(active, timer.session.value)
        timer.discard()
    }

    @Test
    fun backdatedManualSessionAddsTimeWithoutRegressingProgressOrLastRead() = runBlocking {
        val book = repository.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null)
        repository.recordPhysicalReadingSession(book.id, "recent", 500000, 560000, 60, 80, 100, 100)
        repeat(2) {
            repository.recordPhysicalReadingSession(book.id, "manual-old", 1000, 61000, 60, 10, 20, 100, updateProgress = false)
        }
        val saved = repository.getById(book.id)!!
        assertEquals(120L, saved.totalReadingSeconds)
        assertEquals(1f, saved.readingPercent)
        assertEquals(560000L, saved.lastReadAt)
        assertEquals(560000L, saved.finishedReadingAt)
        assertEquals(1000L, saved.startedReadingAt)
        assertEquals(2, database.readingSessionDao().getAllForSync().size)
    }

    @Test
    fun manualSessionCanUpdateCurrentPagesAndRejectsInvalidStartingPage() = runBlocking {
        val book = repository.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null)
        repository.updateOfflinePages(book.id, 100, 10)
        repository.recordPhysicalReadingSession(book.id, "manual", 1000, 61000, 60, 10, 30, 100, updateProgress = true)
        assertEquals(0.3f, repository.getById(book.id)!!.readingPercent)
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            repository.recordPhysicalReadingSession(book.id, "bad-start", 62000, 122000, 60, 101, 30, null)
        }
        assertEquals(60L, repository.getById(book.id)!!.totalReadingSeconds)
        assertEquals(1, database.readingSessionDao().getAllForSync().size)
    }

    @Test
    fun projectedNoteMembershipMatchesFullAnnotationsIncludingWhitespaceAndDeletion() = runBlocking {
        val notes = com.vayana.core.database.repository.AnnotationRepositoryImpl(database, database.annotationDao(),
            database.bookDao(), database.bookAliasDao(), database.tombstoneDao())
        val types = com.vayana.core.database.model.AnnotationType.entries
        val blanks = listOf<String?>(null, "", " \t\n\u00a0\u2003\u202f\u3000", "personal note")
        val locators = listOf("quote:1", "goodreads-quote:1", "Quote:1", "epubcfi(/6/2)")
        for (type in types) for (note in blanks) for (locator in locators) {
            val book = repository.insertOfflineBook("Case", null, BookFormat.PHYSICAL, null, null)
            notes.create(book.id, type, "popular", locator, null, null, "quote", note)
        }
        val entries = notes.observeAll().first()
        entries.take(5).forEach { notes.softDelete(it.id) }
        val expected = notes.observeAll().first().filter {
            !com.vayana.core.database.model.isCommunityQuoteLocator(it.locator) ||
                it.type != com.vayana.core.database.model.AnnotationType.UNDERLINE || it.colorKey != "popular"
        }.filter { it.type != com.vayana.core.database.model.AnnotationType.BOOKMARK || !it.readerNote.isNullOrBlank() }
            .map { it.bookId }.toSet()
        assertEquals(expected, notes.observePersonalNotesBookIds().first())
        val first = entries.last()
        notes.create(first.bookId, com.vayana.core.database.model.AnnotationType.NOTE, "journal", "cfi", null, null, "", "entry")
        assertEquals(expected + first.bookId, notes.observePersonalNotesBookIds().first())
    }

    @Test
    fun journalNotesPersistWithoutSelectedTextAndKeepStableSyncIdentity() = runBlocking {
        val book = repository.insertOfflineBook("Dune", null, BookFormat.PHYSICAL, null, null)
        val notes = com.vayana.core.database.repository.AnnotationRepositoryImpl(database, database.annotationDao(),
            database.bookDao(), database.bookAliasDao(), database.tombstoneDao())
        val entry = notes.create(book.id, com.vayana.core.database.model.AnnotationType.NOTE, "journal",
            "epubcfi(/6/2)", "Chapter one", "chapter.xhtml", "", "Stopped after the revelation. #reflection")
        assertEquals(entry, notes.observeForBook(book.id).first().single())
        assertTrue(entry.syncId.isNotBlank())
        val synced = database.annotationDao().getAllForSync().single()
        assertEquals("journal", synced.colorKey)
        assertEquals(entry.readerNote, synced.readerNote)
        assertEquals("", synced.selectedText)
        notes.update(entry.copy(readerNote = "Revised reflection"))
        assertEquals(entry.syncId, notes.getById(entry.id)!!.syncId)
        notes.softDelete(entry.id)
        assertTrue(notes.observeForBook(book.id).first().isEmpty())
        assertTrue(database.annotationDao().getAllForSync().single().isDeleted)
    }

    @Test
    fun additiveTagsPreserveMetadataAndAreIdempotent() = runBlocking {
        val book = repository.insertOfflineBook("Dune", "Frank Herbert", BookFormat.PHYSICAL, 100, null)
        assertTrue(repository.addTags(book.id, "Favorite, Fiction"))
        assertTrue(repository.addTags(book.id, "fiction, Space"))
        val updated = repository.getById(book.id)!!
        assertEquals("Favorite, Fiction, Space", updated.tagsCsv)
        assertEquals(book.title, updated.title)
        assertEquals(book.author, updated.author)
        assertEquals(book.startedReadingAt, updated.startedReadingAt)
        assertEquals(book.readingPercent, updated.readingPercent)
        kotlin.test.assertFalse(repository.addTags(book.id, "SPACE, favorite"))
        assertEquals(updated.updatedAt, repository.getById(book.id)!!.updatedAt)
    }

    @Test
    fun insertedOfflineBookIsLocalFileLessAndFinishedWhenGivenAFinishDate() = runBlocking {
        val book = repository.insertOfflineBook("Piranesi", "Susanna Clarke", BookFormat.AUDIOBOOK, startedAt = 100, finishedAt = 200)

        assertEquals(BookFormat.AUDIOBOOK, book.format)
        assertEquals(BookFileAvailability.LOCAL, book.fileAvailability)
        assertEquals("", book.filePath)
        assertTrue(book.fileHash.startsWith("audiobook:"))
        assertEquals(1f, book.readingPercent)
        assertEquals(200L, book.finishedReadingAt)
    }

    @Test
    fun ebookReadInAnotherAppIsAnOfflineBook() = runBlocking {
        val book = repository.insertOfflineBook("Project Hail Mary", null, BookFormat.OTHER_EBOOK, startedAt = null, finishedAt = 5)

        assertTrue(book.format.isOffline)
        assertTrue(book.fileHash.startsWith("other_ebook:"))
        assertEquals(listOf(BookFormat.PHYSICAL, BookFormat.AUDIOBOOK, BookFormat.OTHER_EBOOK), BookFormat.Offline)
    }

    @Test
    fun clearingTheFinishDateOfAnOfflineBookUnfinishesIt() = runBlocking {
        val book = repository.insertOfflineBook("Dune", null, BookFormat.PHYSICAL, startedAt = 100, finishedAt = 200)

        repository.updateReadingDates(book.id, startedAt = 100, finishedAt = null)

        val updated = repository.observeAll().first().single()
        assertNull(updated.finishedReadingAt)
        assertEquals(0f, updated.readingPercent)
    }

    @Test
    fun switchingFormatKeepsTheBookAndBumpsItsVersion() = runBlocking {
        val book = repository.insertOfflineBook("Dune", null, BookFormat.PHYSICAL, startedAt = null, finishedAt = null)

        repository.updateOfflineFormat(book.id, BookFormat.AUDIOBOOK)

        val updated = repository.observeAll().first().single()
        assertEquals(BookFormat.AUDIOBOOK, updated.format)
        assertTrue(updated.updatedAt >= book.updatedAt)
    }

    @Test
    fun pagesDriveProgressAndReachingTheLastPageFinishesTheBook() = runBlocking {
        val book = repository.insertOfflineBook("Dune", null, BookFormat.PHYSICAL, null, null, pageCount = 400, currentPage = 100)
        assertEquals(400, book.pageCount)
        assertEquals(100, book.currentPage())
        assertEquals(0.25f, book.readingPercent)

        repository.updateOfflinePages(book.id, pageCount = 400, currentPage = 400)
        val finished = repository.observeAll().first().single()
        assertEquals(1f, finished.readingPercent)
        assertTrue(finished.finishedReadingAt != null)
        assertTrue(finished.startedReadingAt != null)

        repository.updateOfflinePages(book.id, pageCount = 400, currentPage = 399)
        val reopened = repository.observeAll().first().single()
        assertNull(reopened.finishedReadingAt)
        assertEquals(399, reopened.currentPage())
    }

    @Test
    fun audiobooksHaveNoPages() = runBlocking {
        val audiobook = repository.insertOfflineBook("Dune", null, BookFormat.AUDIOBOOK, null, null, pageCount = 400, currentPage = 100)
        assertNull(audiobook.pageCount)
        assertEquals(0f, audiobook.readingPercent)

        val paper = repository.insertOfflineBook("Emma", null, BookFormat.PHYSICAL, null, null, pageCount = 300, currentPage = 30)
        repository.updateOfflineFormat(paper.id, BookFormat.AUDIOBOOK)
        repository.updateOfflinePages(paper.id, pageCount = 300, currentPage = 300)

        val switched = repository.observeAll().first().single { it.id == paper.id }
        assertNull(switched.pageCount)
        assertNull(switched.finishedReadingAt)
    }

    @Test
    fun currentPageWithoutATotalIsIgnored() = runBlocking {
        val book = repository.insertOfflineBook("Dune", null, BookFormat.PHYSICAL, null, null, pageCount = null, currentPage = 50)

        assertNull(book.pageCount)
        assertEquals(0f, book.readingPercent)
    }

    @Test
    fun physicalBookStoresBorrowedStatusAndReturnDate() = runBlocking {
        val zone = ZoneId.systemDefault()
        val sunday = LocalDate.of(2026, 9, 27).atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
        val saturday = LocalDate.of(2026, 9, 26).atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
        val book = repository.insertOfflineBook(
            title = "The Library Book",
            author = null,
            format = BookFormat.PHYSICAL,
            startedAt = null,
            finishedAt = null,
            physicalOwnership = PhysicalBookOwnership.BORROWED,
            borrowReturnAt = sunday,
        )

        assertEquals(PhysicalBookOwnership.BORROWED, book.physicalOwnership)
        assertEquals(saturday, book.borrowReturnAt)

        repository.updatePhysicalBookLoan(book.id, PhysicalBookOwnership.OWNED, 9_000L)
        val owned = repository.observeAll().first().single()
        assertEquals(PhysicalBookOwnership.OWNED, owned.physicalOwnership)
        assertNull(owned.borrowReturnAt)
    }

    @Test
    fun pageChangesUpdatePhysicalRecencyButMetadataAndUnchangedSavesDoNot() = runBlocking {
        val book = repository.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, 1000, null, pageCount = 100, currentPage = 10)
        val dao = database.bookDao()
        dao.update(dao.getById(book.id)!!.copy(lastReadAt = 1000, updatedAt = 1000))
        repository.updateOfflinePages(book.id, 200, 10)
        assertEquals(1000L, repository.getById(book.id)!!.lastReadAt)
        repository.updateOfflinePages(book.id, 200, 20)
        val read = repository.getById(book.id)!!
        assertTrue(read.lastReadAt!! > 1000)
        assertEquals(0L, read.totalReadingSeconds)
        assertEquals(book.startedReadingAt, read.startedReadingAt)
        repository.updateOfflinePages(book.id, 200, 20)
        assertEquals(read, repository.getById(book.id))
    }

    @Test
    fun markingReadingRejectsOtherFormatsAndPreservesHistoryDuringRereading() = runBlocking {
        val audio = repository.insertOfflineBook("Audio", null, BookFormat.AUDIOBOOK, null, null)
        kotlin.test.assertFailsWith<IllegalArgumentException> { repository.markPhysicalBookReading(audio.id) }
        assertNull(repository.getById(audio.id)!!.lastReadAt)
        val book = repository.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null)
        repository.recordPhysicalReadingSession(book.id, "complete", 1000, 61000, 60, 0, 100, 100)
        repository.markPhysicalBookReading(book.id)
        val reading = repository.getById(book.id)!!
        assertEquals(BookReadingState.READING, reading.readingState())
        assertNull(reading.finishedReadingAt)
        assertEquals(0f, reading.readingPercent)
        assertEquals(60L, reading.totalReadingSeconds)
        assertEquals(1, database.readingSessionDao().getAllForSync().size)
    }

    @Test
    fun savingUnchangedValuesKeepsTheVersion() = runBlocking {
        val book = repository.insertOfflineBook(
            title = "Dune",
            author = null,
            format = BookFormat.PHYSICAL,
            startedAt = 100,
            finishedAt = null,
            pageCount = 400,
            currentPage = 100,
            physicalOwnership = PhysicalBookOwnership.BORROWED,
            borrowReturnAt = null,
        )
        val bookDao = database.bookDao()
        bookDao.update(bookDao.getById(book.id)!!.copy(updatedAt = 1L))

        repository.updateOfflinePages(book.id, pageCount = 400, currentPage = 100)
        repository.updateReadingDates(book.id, startedAt = 100, finishedAt = null)
        repository.updatePhysicalBookLoan(book.id, PhysicalBookOwnership.BORROWED, borrowReturnAt = null)
        assertEquals(1L, repository.observeAll().first().single().updatedAt)

        repository.updatePhysicalBookLoan(book.id, PhysicalBookOwnership.OWNED, borrowReturnAt = null)
        assertTrue(repository.observeAll().first().single().updatedAt > 1L)
    }

    @Test
    fun nonPhysicalBookDiscardsPhysicalLoanDetails() = runBlocking {
        val book = repository.insertOfflineBook(
            title = "Listened",
            author = null,
            format = BookFormat.AUDIOBOOK,
            startedAt = null,
            finishedAt = null,
            physicalOwnership = PhysicalBookOwnership.BORROWED,
            borrowReturnAt = 5_000L,
        )

        assertNull(book.physicalOwnership)
        assertNull(book.borrowReturnAt)
    }

    @Test
    fun pageInputRejectsACurrentPagePastTheEnd() {
        assertTrue(offlinePagesInput("300", "301").currentTooHigh)
        assertEquals(OfflinePagesInput(total = 300, current = 12, currentTooHigh = false), offlinePagesInput("300", "12"))
        assertEquals(OfflinePagesInput(total = null, current = null, currentTooHigh = false), offlinePagesInput("0", "12"))
    }

    @Test
    fun syncedOfflineBookArrivesReadyWithoutADownload() = runBlocking {
        assertEquals(CloudBookMergeResult.CREATED, repository.mergeCloudBook(offlineRecord(updatedAt = 10, finishedAt = 300)))

        val book = repository.observeAll().first().single()
        assertEquals(BookFileAvailability.LOCAL, book.fileAvailability)
        assertEquals(BookFormat.PHYSICAL, book.format)
        assertEquals(300L, book.finishedReadingAt)
        assertNull(book.fileAssetId)
    }

    @Test
    fun newerSyncedOfflineBookUpdatesDatesAndFormat() = runBlocking {
        repository.mergeCloudBook(offlineRecord(updatedAt = 10, finishedAt = null))

        val result = repository.mergeCloudBook(
            offlineRecord(updatedAt = 20, finishedAt = 400, format = BookFormat.OTHER_EBOOK, readingPercent = 1f, pageCount = 320),
        )

        assertEquals(CloudBookMergeResult.UPDATED, result)
        val book = repository.observeAll().first().single()
        assertEquals(BookFormat.OTHER_EBOOK, book.format)
        assertEquals(320, book.pageCount)
        assertEquals(400L, book.finishedReadingAt)
        assertEquals(1f, book.readingPercent)
    }

    @Test
    fun newerSyncedPhysicalBookUpdatesLoanDetails() = runBlocking {
        repository.mergeCloudBook(offlineRecord(updatedAt = 10, finishedAt = null))

        repository.mergeCloudBook(
            offlineRecord(
                updatedAt = 20,
                finishedAt = null,
                physicalOwnership = PhysicalBookOwnership.BORROWED,
                borrowReturnAt = 8_000L,
            ),
        )

        val book = repository.observeAll().first().single()
        assertEquals(PhysicalBookOwnership.BORROWED, book.physicalOwnership)
        assertEquals(8_000L, book.borrowReturnAt)
    }

    @Test
    fun olderSyncedOfflineBookLeavesLocalDatesAlone() = runBlocking {
        repository.mergeCloudBook(offlineRecord(updatedAt = 20, finishedAt = 400))

        repository.mergeCloudBook(offlineRecord(updatedAt = 10, finishedAt = null))

        assertEquals(400L, repository.observeAll().first().single().finishedReadingAt)
    }

    private fun offlineRecord(
        updatedAt: Long,
        finishedAt: Long?,
        format: BookFormat = BookFormat.PHYSICAL,
        readingPercent: Float = if (finishedAt != null) 1f else 0f,
        pageCount: Int? = null,
        physicalOwnership: PhysicalBookOwnership? = null,
        borrowReturnAt: Long? = null,
    ) = CloudBookRecord(
        syncId = "offline-a",
        title = "Paper Book",
        author = null,
        series = null,
        seriesNumber = null,
        description = null,
        tagsCsv = null,
        format = format,
        fileHash = "physical:a",
        assetId = null,
        assetSha256 = null,
        assetSizeBytes = null,
        assetUploadedAt = null,
        coverAssetId = null,
        coverAssetSha256 = null,
        coverAssetSizeBytes = null,
        coverAssetUploadedAt = null,
        lastLocator = null,
        readingPercent = readingPercent,
        rating = 0f,
        wordCount = null,
        pageEstimate = pageCount,
        createdAt = 1,
        updatedAt = updatedAt,
        lastReadAt = null,
        startedReadingAt = 100,
        finishedReadingAt = finishedAt,
        totalReadingSeconds = 0,
        customFontSizePercent = null,
        customLineHeight = null,
        customFontFamily = null,
        customSideMarginPercent = null,
        readNextAddedAt = null,
        readNextUpdatedAt = null,
        goodreadsUrl = null,
        goodreadsRating = null,
        goodreadsRatingsCount = null,
        originalPublicationYear = null,
        physicalOwnership = physicalOwnership,
        borrowReturnAt = borrowReturnAt,
    )
}
