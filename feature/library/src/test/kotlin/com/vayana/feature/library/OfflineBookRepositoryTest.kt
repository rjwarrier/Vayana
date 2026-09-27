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
