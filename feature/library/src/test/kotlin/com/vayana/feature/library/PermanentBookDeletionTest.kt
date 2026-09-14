package com.vayana.feature.library

import androidx.room.Room
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.entity.AnnotationEntity
import com.vayana.core.database.entity.BookAliasEntity
import com.vayana.core.database.entity.BookEntity
import com.vayana.core.database.entity.BookShelfCrossRefEntity
import com.vayana.core.database.entity.ReadingSessionEntity
import com.vayana.core.database.entity.ShelfEntity
import com.vayana.core.database.entity.VocabularyCardEntity
import com.vayana.core.database.repository.BookRepositoryImpl
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.repository.CloudBookMergeResult
import com.vayana.core.database.repository.CloudBookRecord

/** Repository-level permanent deletion against a real (in-memory) Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PermanentBookDeletionTest {
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
    fun purgeEverywhereRemovesTheBookAndEverythingThatHangsOffIt() = runBlocking {
        val bookId = database.bookDao().insert(book(syncId = "book-a", fileHash = "hash-a"))
        val annotationId = database.annotationDao().insert(
            AnnotationEntity(
                bookId = bookId, type = "HIGHLIGHT", colorKey = "yellow", locator = "epubcfi(/6/2)", chapterTitle = null,
                chapterHref = null, selectedText = "A line", readerNote = null, createdAt = 1, updatedAt = 1,
            ),
        )
        database.readingSessionDao().insert(ReadingSessionEntity(bookId = bookId, startedAt = 1, endedAt = 61, durationSeconds = 60))
        val shelfId = database.shelfDao().insert(ShelfEntity(name = "Favourites", createdAt = 1, updatedAt = 1))
        database.shelfDao().addBookToShelf(BookShelfCrossRefEntity(bookId = bookId, shelfId = shelfId))
        val cardId = database.vocabularyCardDao().insert(
            VocabularyCardEntity(
                word = "ephemeral", definition = "short-lived", sentence = null, bookId = bookId, bookTitle = "Book A",
                createdAt = 1, lastReviewedAt = null, known = false,
            ),
        )
        database.bookAliasDao().upsert(BookAliasEntity(syncId = "book-a-elsewhere", fileHash = "hash-a", createdAt = 1))

        val purged = repository.purgeEverywhere(bookId)

        assertEquals(listOf("books/a.epub", "covers/a.jpg", "covers/a-custom.jpg"), purged?.localFilePaths)
        assertEquals(listOf("fileasset00000001", "coverasset0000001"), purged?.queuedCloudAssetIds)
        assertNull(database.bookDao().getById(bookId))
        assertNull(database.annotationDao().getById(annotationId))
        assertEquals(emptyList(), database.readingSessionDao().syncIdsForBook(bookId))
        assertEquals(0, count("SELECT COUNT(*) FROM book_shelf_cross_ref"))
        assertEquals("ephemeral", database.vocabularyCardDao().getById(cardId)?.word)
        assertNull(database.vocabularyCardDao().getById(cardId)?.bookId)
        assertNull(database.bookAliasDao().findByFileHash("hash-a"))
        assertEquals("book", database.tombstoneDao().findBySyncId("book-a")?.entityType)
        assertEquals("book_purge", database.tombstoneDao().findBySyncId("purge:book-a")?.entityType)
        assertEquals("book_purge", database.tombstoneDao().findBySyncId("purge:book-a-elsewhere")?.entityType)
        assertEquals(
            setOf("fileasset00000001" to "book_file", "coverasset0000001" to "cover"),
            database.pendingCloudDeletionDao().getBatch(10).map { it.assetId to it.kind }.toSet(),
        )
    }

    @Test
    fun syncedPurgeAppliesOverNewerLocalEditsAndIsRemembered() = runBlocking {
        val bookId = database.bookDao().insert(
            book(syncId = "book-b", fileHash = "hash-b", updatedAt = 5_000, isDeleted = true, withAssets = false),
        )

        val purged = repository.applyPurgeTombstone("book-b", deletedAt = 1_000)

        assertEquals("book-b", purged?.syncId)
        assertEquals(emptyList(), purged?.queuedCloudAssetIds)
        assertNull(database.bookDao().getById(bookId))
        assertNull(repository.applyPurgeTombstone("book-b", deletedAt = 1_000))
        assertEquals(1_000L, database.tombstoneDao().findBySyncId("purge:book-b")?.deletedAt)
        assertNull(database.tombstoneDao().findBySyncId("book-b"))
    }

    @Test
    fun syncCannotBringBackAPermanentlyDeletedBook() = runBlocking {
        val bookId = database.bookDao().insert(book(syncId = "book-c", fileHash = "hash-c"))
        repository.purgeEverywhere(bookId)

        val result = repository.mergeCloudBook(cloudRecord(syncId = "book-c", fileHash = "hash-c", updatedAt = 9_000_000_000_000))

        assertEquals(CloudBookMergeResult.SKIPPED, result)
        assertNull(database.bookDao().findAnyBySyncId("book-c"))
    }

    @Test
    fun aReimportedCopyOfAPurgedFileSyncsAsANewBook() = runBlocking {
        val bookId = database.bookDao().insert(book(syncId = "book-d", fileHash = "hash-d"))
        repository.purgeEverywhere(bookId)

        val result = repository.mergeCloudBook(cloudRecord(syncId = "book-d-reimported", fileHash = "hash-d", updatedAt = 10))

        assertEquals(CloudBookMergeResult.CREATED, result)
    }

    @Test
    fun syncedDeleteAppliesEvenIfTheBookWasReadHereAfterwards() = runBlocking {
        val bookId = database.bookDao().insert(book(syncId = "book-e", fileHash = "hash-e", updatedAt = 5_000))

        val title = repository.applyBookTombstone("book-e", deletedAt = 1_000)

        assertEquals("Book A", title)
        val deleted = database.bookDao().getById(bookId)
        assertEquals(true, deleted?.isDeleted)
        assertEquals(1_000L, deleted?.deletionUpdatedAt)
    }

    @Test
    fun syncedDeleteDoesNotUndoALaterRestore() = runBlocking {
        val bookId = database.bookDao().insert(book(syncId = "book-f", fileHash = "hash-f", deletionUpdatedAt = 2_000))

        assertNull(repository.applyBookTombstone("book-f", deletedAt = 1_000))
        assertEquals(false, database.bookDao().getById(bookId)?.isDeleted)
    }

    @Test
    fun staleRecordNeitherRestoresNorDuplicatesADeletedBook() = runBlocking {
        val bookId = database.bookDao().insert(book(syncId = "book-g", fileHash = "hash-g"))
        repository.softDelete(bookId)

        val result = repository.mergeCloudBook(cloudRecord(syncId = "book-g", fileHash = "hash-g", updatedAt = 9_000_000_000_000))

        assertEquals(CloudBookMergeResult.SKIPPED, result)
        assertEquals(true, database.bookDao().getById(bookId)?.isDeleted)
        assertEquals(1, count("SELECT COUNT(*) FROM books"))
    }

    @Test
    fun restoreOnAnotherDeviceBringsTheBookBack() = runBlocking {
        val bookId = database.bookDao().insert(book(syncId = "book-h", fileHash = "hash-h"))
        repository.softDelete(bookId)
        val restoredAt = System.currentTimeMillis() + 60_000

        val result = repository.mergeCloudBook(
            cloudRecord(syncId = "book-h", fileHash = "hash-h", updatedAt = restoredAt, deletionUpdatedAt = restoredAt),
        )

        assertEquals(CloudBookMergeResult.UPDATED, result)
        assertEquals(false, database.bookDao().getById(bookId)?.isDeleted)
        assertNull(database.tombstoneDao().findBySyncId("book-h"))
    }

    @Test
    fun purgingAMissingBookDoesNothing() = runBlocking {
        assertNull(repository.purgeEverywhere(42))
        assertEquals(0, count("SELECT COUNT(*) FROM tombstones"))
    }

    private fun cloudRecord(syncId: String, fileHash: String, updatedAt: Long, deletionUpdatedAt: Long? = null) = CloudBookRecord(
        syncId = syncId,
        title = "Book A",
        author = null,
        series = null,
        seriesNumber = null,
        description = null,
        tagsCsv = null,
        format = BookFormat.EPUB,
        fileHash = fileHash,
        assetId = "fileasset00000009",
        assetSha256 = "0".repeat(64),
        assetSizeBytes = 10,
        assetUploadedAt = 1,
        coverAssetId = null,
        coverAssetSha256 = null,
        coverAssetSizeBytes = null,
        coverAssetUploadedAt = null,
        lastLocator = null,
        readingPercent = 0f,
        rating = 0f,
        wordCount = null,
        pageEstimate = null,
        createdAt = 1,
        updatedAt = updatedAt,
        lastReadAt = null,
        startedReadingAt = null,
        finishedReadingAt = null,
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
        deletionUpdatedAt = deletionUpdatedAt,
    )

    private fun count(sql: String): Int = database.query(sql, null).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

    private fun book(
        syncId: String,
        fileHash: String,
        updatedAt: Long = 1,
        isDeleted: Boolean = false,
        withAssets: Boolean = true,
        deletionUpdatedAt: Long? = null,
    ) = BookEntity(
        syncId = syncId,
        title = "Book A",
        author = null,
        series = null,
        seriesNumber = null,
        description = null,
        coverPath = "covers/a.jpg",
        filePath = "books/a.epub",
        format = "EPUB",
        fileHash = fileHash,
        fileAssetId = "fileasset00000001".takeIf { withAssets },
        coverAssetId = "coverasset0000001".takeIf { withAssets },
        lastLocator = null,
        readingPercent = 0.5f,
        rating = 0f,
        groupId = null,
        isDeleted = isDeleted,
        wordCount = null,
        pageEstimate = null,
        createdAt = 1,
        updatedAt = updatedAt,
        lastReadAt = null,
        customCoverPath = "covers/a-custom.jpg",
        goodreadsCoverPath = "covers/a.jpg",
        deletionUpdatedAt = deletionUpdatedAt,
    )
}
