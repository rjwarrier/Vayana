package com.vayana.core.database.repository

import androidx.room.withTransaction
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.dao.BookAliasDao
import com.vayana.core.database.dao.BookDao
import com.vayana.core.database.dao.ReadingSessionDao
import com.vayana.core.database.dao.TombstoneDao
import com.vayana.core.database.entity.BookAliasEntity
import com.vayana.core.database.entity.BookEntity
import com.vayana.core.database.entity.TombstoneEntity
import com.vayana.core.database.search.ftsPrefixMatch
import com.vayana.core.database.search.searchTokens
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.OfflinePages
import com.vayana.core.database.model.PhysicalBookOwnership
import com.vayana.core.database.model.normalizeBorrowReturnAt
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import com.vayana.core.database.model.normalizedBookTagsCsv
import com.vayana.core.database.dao.PendingCloudDeletionDao
import com.vayana.core.database.dao.VocabularyCardDao
import com.vayana.core.database.entity.PendingCloudDeletionEntity

class BookRepositoryImpl @Inject constructor(
    private val database: VayanaDatabase,
    private val bookDao: BookDao,
    private val bookAliasDao: BookAliasDao,
    private val tombstoneDao: TombstoneDao,
    private val readingSessionDao: ReadingSessionDao,
    private val vocabularyCardDao: VocabularyCardDao,
    private val pendingCloudDeletionDao: PendingCloudDeletionDao,
) : BookRepository {
    private val _remoteReadingProgressApplied = MutableSharedFlow<RemoteReadingProgressApplied>(
        extraBufferCapacity = RemoteProgressEventBufferCapacity,
    )

    override val remoteReadingProgressApplied: Flow<RemoteReadingProgressApplied> =
        _remoteReadingProgressApplied.asSharedFlow()

    override suspend fun referencedCoverPaths(): Set<String> = bookDao.referencedCoverPaths().toSet()

    override fun observeAll(): Flow<List<Book>> =
        bookDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeDeleted(): Flow<List<Book>> =
        bookDao.observeDeleted().map { entities -> entities.map { it.toDomain() } }

    override suspend fun deletedBookIdsBefore(cutoff: Long): List<Long> = bookDao.deletedIdsBefore(cutoff)

    override suspend fun getById(id: Long): Book? = bookDao.getById(id)?.toDomain()

    override suspend fun findActiveBySyncIdOrHash(syncId: String, fileHash: String): Book? =
        (bookDao.findBySyncId(syncId) ?: bookDao.findByHash(fileHash))?.toDomain()

    override suspend fun updateLocator(id: Long, locator: String, readingPercent: Float, finishedThreshold: Float) {
        bookDao.updateLocator(
            id,
            locator,
            readingPercent.sanitizedReadingPercent(),
            finishedThreshold,
            System.currentTimeMillis(),
        )
    }

    override suspend fun applySyncedReadingProgress(
        syncId: String,
        fileHash: String,
        locator: String,
        readingPercent: Float,
        lastReadAt: Long?,
        remoteUpdatedAt: Long,
        startedReadingAt: Long?,
        finishedReadingAt: Long?,
        totalReadingSeconds: Long,
        syncedDeviceLabel: String?,
        syncedAt: Long?,
    ): ReadingProgressMergeResult {
        if (locator.isBlank() || remoteUpdatedAt <= 0L || readingPercent !in 0f..1f) {
            return ReadingProgressMergeResult.InvalidRemote
        }
        // The read-then-write below must be atomic: a concurrent local page-turn write between the
        // read and the write here could otherwise be silently clobbered by a stale merge decision.
        val (result, appliedEvent) = database.withTransaction {
            val book = bookDao.findBySyncId(syncId) ?: bookDao.findByHash(fileHash)
                ?: return@withTransaction ReadingProgressMergeResult.NoLocalMatch to null
            val local = book.readingProgressVersion()
            val remote = ReadingProgressVersion(
                syncId = syncId,
                fileHash = fileHash,
                locator = locator,
                readingPercent = readingPercent,
                lastReadAt = lastReadAt,
                updatedAt = remoteUpdatedAt,
            )
            if (book.syncId == syncId && book.fileHash != fileHash) {
                return@withTransaction ReadingProgressMergeResult.ConflictLocalKept(
                    local = local,
                    remote = remote,
                    reason = ReadingProgressConflictReason.INCOMPATIBLE_FILE_REVISION,
                ) to null
            }
            // A reading-stats reset counts as local progress at the moment it happened, so older remote
            // progress (from before the reset, on this or another device) can't bring the old stats back.
            val resetAt = tombstoneDao.findBySyncId(readingProgressResetTombstoneId(book.syncId))?.deletedAt ?: 0L
            val localVersion = maxOf(book.lastReadAt ?: 0L, resetAt)
            val remoteVersion = lastReadAt ?: remoteUpdatedAt
            if (localVersion > remoteVersion) {
                return@withTransaction ReadingProgressMergeResult.LocalNewer to null
            }
            if (localVersion == remoteVersion && book.lastLocator != locator) {
                return@withTransaction ReadingProgressMergeResult.ConflictLocalKept(
                    local = local,
                    remote = remote,
                    reason = ReadingProgressConflictReason.SAME_TIMESTAMP_DIFFERENT_LOCATOR,
                ) to null
            }
            if (localVersion == remoteVersion) {
                return@withTransaction ReadingProgressMergeResult.LocalNewer to null
            }
            bookDao.applySyncedReadingProgress(
                id = book.id,
                locator = locator,
                readingPercent = readingPercent,
                lastReadAt = remoteVersion,
                remoteUpdatedAt = remoteUpdatedAt,
                startedReadingAt = startedReadingAt,
                finishedReadingAt = finishedReadingAt,
                totalReadingSeconds = totalReadingSeconds,
            )
            val event = RemoteReadingProgressApplied(
                bookId = book.id,
                locator = locator,
                readingPercent = readingPercent,
                version = remoteVersion,
                syncedDeviceLabel = syncedDeviceLabel,
                syncedAt = syncedAt,
            )
            ReadingProgressMergeResult.AppliedRemote to event
        }
        appliedEvent?.let { _remoteReadingProgressApplied.emit(it) }
        return result
    }

    override suspend fun applySyncedReadNext(
        syncId: String,
        fileHash: String,
        addedAt: Long?,
        remoteUpdatedAt: Long,
    ): Boolean {
        if (syncId.isBlank() || fileHash.isBlank() || remoteUpdatedAt <= 0L || (addedAt != null && addedAt <= 0L)) {
            return false
        }
        return database.withTransaction {
            val book = bookDao.findBySyncId(syncId) ?: bookDao.findByHash(fileHash) ?: return@withTransaction false
            val local = book.readNextState()
            val remote = ReadNextState(addedAt = addedAt, updatedAt = remoteUpdatedAt)
            if (remote.version <= local.version) return@withTransaction false
            bookDao.applySyncedReadNext(book.id, remote.addedAt, remote.version)
            if (remote.addedAt != null) trimReadNextQueueKeepingUpdatedAt()
            true
        }
    }

    override suspend fun addReadingTime(id: Long, addedSeconds: Long) {
        bookDao.addReadingTime(id, addedSeconds, System.currentTimeMillis())
    }

    override suspend fun recordBookOpened(id: Long) {
        bookDao.recordBookOpened(id, System.currentTimeMillis())
    }

    override suspend fun updateMetadata(id: Long, title: String, author: String?, series: String?, seriesNumber: String?, description: String?, tagsCsv: String?) {
        bookDao.updateMetadata(id, title, author, series, seriesNumber, description, tagsCsv.normalizedBookTagsCsv(), System.currentTimeMillis())
    }

    override suspend fun updateRating(id: Long, rating: Float) {
        bookDao.updateRating(id, rating.coerceIn(0f, 5f), System.currentTimeMillis())
    }

    override suspend fun updateReadingDates(id: Long, startedAt: Long?, finishedAt: Long?) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val existing = bookDao.getById(id) ?: return@withTransaction
            // Re-saving the dates shown would still bump the version: every library observer re-queries and the
            // next sync uploads the book again, for nothing.
            if (existing.startedReadingAt == startedAt && existing.finishedReadingAt == finishedAt) return@withTransaction
            if (!BookFormat.valueOf(existing.format).isOffline) {
                bookDao.updateReadingDates(id, startedAt, finishedAt, now)
                return@withTransaction
            }
            // A book read outside the app has no reader position: a finish date puts it at the last page, and
            // clearing one takes a finished book back to the start (its page progress is kept otherwise).
            val percent = when {
                finishedAt != null -> 1f
                existing.readingPercent >= 1f -> 0f
                else -> existing.readingPercent
            }
            bookDao.update(
                existing.copy(startedReadingAt = startedAt, finishedReadingAt = finishedAt, readingPercent = percent, updatedAt = now),
            )
        }
    }

    override suspend fun updateOfflinePages(id: Long, pageCount: Int?, currentPage: Int?) {
        database.withTransaction {
            val existing = bookDao.getById(id)?.takeIf { BookFormat.valueOf(it.format).tracksPages } ?: return@withTransaction
            val pages = OfflinePages.of(pageCount, currentPage)
            val now = System.currentTimeMillis()
            val percent = pages.percent
            val updated = if (percent == null) {
                existing.copy(pageEstimate = pages.total, updatedAt = now)
            } else {
                // The page decides where the reader is: reaching the last page finishes the book, and going back
                // from it (a reread, or a mistyped page) makes it unfinished again.
                val finished = percent >= 1f
                existing.copy(
                    pageEstimate = pages.total,
                    readingPercent = percent,
                    startedReadingAt = existing.startedReadingAt ?: now.takeIf { percent > 0f },
                    finishedReadingAt = if (finished) existing.finishedReadingAt ?: now else null,
                    updatedAt = now,
                )
            }
            // Saving the pages dialog unchanged is not an edit (see updateReadingDates).
            if (updated.copy(updatedAt = existing.updatedAt) != existing) bookDao.update(updated)
        }
    }

    override suspend fun updateOfflineFormat(id: Long, format: BookFormat) {
        require(format.isOffline) { "Only books read outside the app can switch format" }
        database.withTransaction {
            val existing = bookDao.getById(id)?.takeIf { BookFormat.valueOf(it.format).isOffline } ?: return@withTransaction
            bookDao.update(
                existing.copy(
                    format = format.name,
                    physicalOwnership = if (format == BookFormat.PHYSICAL) {
                        existing.physicalOwnership ?: PhysicalBookOwnership.OWNED.name
                    } else {
                        null
                    },
                    borrowReturnAt = existing.borrowReturnAt.takeIf {
                        format == BookFormat.PHYSICAL && existing.physicalOwnership == PhysicalBookOwnership.BORROWED.name
                    },
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun updatePhysicalBookLoan(
        id: Long,
        ownership: PhysicalBookOwnership,
        borrowReturnAt: Long?,
    ) {
        bookDao.updatePhysicalBookLoan(
            id = id,
            ownership = ownership.name,
            borrowReturnAt = normalizeBorrowReturnAt(borrowReturnAt)
                .takeIf { ownership == PhysicalBookOwnership.BORROWED },
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun insertOfflineBook(
        title: String,
        author: String?,
        format: BookFormat,
        startedAt: Long?,
        finishedAt: Long?,
        pageCount: Int?,
        currentPage: Int?,
        physicalOwnership: PhysicalBookOwnership,
        borrowReturnAt: Long?,
    ): Book {
        require(format.isOffline) { "Offline books must be PHYSICAL, AUDIOBOOK or OTHER_EBOOK" }
        val pages = if (format.tracksPages) OfflinePages.of(pageCount, currentPage) else OfflinePages(null, null)
        val entity = newLocalBookEntity(
            title = title,
            author = author,
            format = format,
            filePath = "",
            // Unique per entry: two paper copies of the same title are still separate reads.
            fileHash = "${format.name.lowercase()}:${UUID.randomUUID()}",
        ).copy(
            readingPercent = if (finishedAt != null) 1f else pages.percent ?: 0f,
            pageEstimate = pages.total,
            startedReadingAt = startedAt,
            finishedReadingAt = finishedAt,
            physicalOwnership = physicalOwnership.name.takeIf { format == BookFormat.PHYSICAL },
            borrowReturnAt = normalizeBorrowReturnAt(borrowReturnAt)
                .takeIf { format == BookFormat.PHYSICAL && physicalOwnership == PhysicalBookOwnership.BORROWED },
        )
        val id = bookDao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    override suspend fun updateCover(id: Long, coverPath: String?) {
        bookDao.updateCover(id, coverPath, System.currentTimeMillis())
    }

    override suspend fun updateGoodreadsInfo(
        id: Long,
        goodreadsUrl: String?,
        rating: Float?,
        ratingsCount: Int?,
        originalPublicationYear: Int?,
    ) {
        bookDao.updateGoodreadsInfo(id, goodreadsUrl, rating, ratingsCount, originalPublicationYear)
    }

    override suspend fun updateCoverAlternates(id: Long, customCoverPath: String?, goodreadsCoverPath: String?) {
        bookDao.updateCoverAlternates(id, customCoverPath, goodreadsCoverPath)
    }

    override suspend fun insertIfNew(
        title: String,
        author: String?,
        series: String?,
        seriesNumber: String?,
        description: String?,
        tagsCsv: String?,
        coverPath: String?,
        filePath: String,
        format: BookFormat,
        fileHash: String,
    ): Book? = database.withTransaction {
        if (bookDao.findByHash(fileHash) != null) return@withTransaction null

        val entity = newLocalBookEntity(
            title = title,
            author = author,
            format = format,
            filePath = filePath,
            fileHash = fileHash,
            series = series,
            seriesNumber = seriesNumber,
            description = description,
            tagsCsv = tagsCsv.normalizedBookTagsCsv(),
            coverPath = coverPath,
        )
        val id = bookDao.insert(entity)
        entity.copy(id = id).toDomain()
    }

    override suspend fun replaceSource(
        id: Long,
        title: String,
        author: String?,
        series: String?,
        seriesNumber: String?,
        description: String?,
        coverPath: String?,
        filePath: String,
        format: BookFormat,
        fileHash: String,
    ): Boolean = database.withTransaction {
        val existing = bookDao.findByHash(fileHash)
        if (existing != null && existing.id != id) return@withTransaction false
        bookDao.replaceSource(
            id = id,
            title = title,
            author = author,
            series = series,
            seriesNumber = seriesNumber,
            description = description,
            coverPath = coverPath,
            filePath = filePath,
            format = format.name,
            fileHash = fileHash,
            updatedAt = System.currentTimeMillis(),
        )
        true
    }

    override suspend fun softDelete(id: Long) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            bookDao.getById(id)?.let { book ->
                tombstoneDao.upsert(TombstoneEntity(syncId = book.syncId, entityType = TombstoneEntityType.BOOK.value, deletedAt = now))
            }
            bookDao.softDelete(id, now)
        }
    }

    override suspend fun restore(id: Long) {
        database.withTransaction {
            bookDao.getById(id)?.let { book -> tombstoneDao.deleteBySyncId(book.syncId) }
            bookDao.restore(id, System.currentTimeMillis())
        }
    }

    override suspend fun purgeEverywhere(id: Long): PurgedBook? = database.withTransaction {
        val book = bookDao.getById(id) ?: return@withTransaction null
        val now = System.currentTimeMillis()
        // App versions that don't know book_purge still move the book to Recently deleted on this tombstone.
        tombstoneDao.upsert(TombstoneEntity(syncId = book.syncId, entityType = TombstoneEntityType.BOOK.value, deletedAt = now))
        purgeLocked(book, deletedAt = now)
    }

    override suspend fun applyPurgeTombstone(bookSyncId: String, deletedAt: Long): PurgedBook? = database.withTransaction {
        val book = bookDao.findAnyBySyncId(bookSyncId)
        if (book == null) {
            upsertPurgeTombstone(bookSyncId, deletedAt)
            null
        } else {
            purgeLocked(book, deletedAt)
        }
    }

    override suspend fun applyBookTombstone(bookSyncId: String, deletedAt: Long): String? = database.withTransaction {
        val book = bookDao.findBySyncId(bookSyncId) ?: return@withTransaction null
        book.title.takeIf { bookDao.applySyncedDeletion(bookSyncId, deletedAt) > 0 }
    }

    private suspend fun purgeLocked(book: BookEntity, deletedAt: Long): PurgedBook {
        upsertPurgeTombstone(book.syncId, deletedAt)
        // A copy of the same file imported on another device and folded in under its own sync id mustn't bring it back.
        bookAliasDao.findByFileHash(book.fileHash)?.let { alias -> upsertPurgeTombstone(alias.syncId, deletedAt) }
        val assets = listOfNotNull(
            book.fileAssetId?.takeIf { it.isNotBlank() }?.let { it to CloudAssetKind.BOOK_FILE },
            book.coverAssetId?.takeIf { it.isNotBlank() }?.let { it to CloudAssetKind.COVER },
        ).distinctBy { (assetId, _) -> assetId }
        pendingCloudDeletionDao.insertAll(
            assets.map { (assetId, kind) -> PendingCloudDeletionEntity(assetId = assetId, kind = kind.value, queuedAt = deletedAt) },
        )
        vocabularyCardDao.detachBook(book.id)
        bookAliasDao.deleteForBook(book.syncId, book.fileHash)
        bookDao.deleteById(book.id)
        return PurgedBook(
            syncId = book.syncId,
            title = book.title,
            localFilePaths = listOfNotNull(book.filePath, book.coverPath, book.customCoverPath, book.goodreadsCoverPath)
                .filter { it.isNotBlank() }
                .distinct(),
            queuedCloudAssetIds = assets.map { (assetId, _) -> assetId },
        )
    }

    private suspend fun upsertPurgeTombstone(bookSyncId: String, deletedAt: Long) {
        tombstoneDao.upsert(
            TombstoneEntity(
                syncId = bookPurgeTombstoneId(bookSyncId),
                entityType = TombstoneEntityType.BOOK_PURGE.value,
                deletedAt = deletedAt,
            ),
        )
    }

    override suspend fun markFinished(id: Long) {
        bookDao.markFinished(id, System.currentTimeMillis())
    }

    override suspend fun resetReadingStats(id: Long) {
        database.withTransaction {
            val book = bookDao.getById(id) ?: return@withTransaction
            val now = System.currentTimeMillis()
            readingSessionDao.syncIdsForBook(id).forEach { sessionSyncId ->
                tombstoneDao.upsert(TombstoneEntity(syncId = sessionSyncId, entityType = TombstoneEntityType.READING_SESSION.value, deletedAt = now))
            }
            readingSessionDao.deleteForBook(id)
            tombstoneDao.upsert(
                TombstoneEntity(
                    syncId = readingProgressResetTombstoneId(book.syncId),
                    entityType = TombstoneEntityType.READING_PROGRESS_RESET.value,
                    deletedAt = now,
                ),
            )
            bookDao.resetReadingStats(id, now)
        }
    }

    override suspend fun applyReadingStatsReset(bookSyncId: String, resetAt: Long): Int = database.withTransaction {
        val book = bookDao.findAnyBySyncId(bookSyncId) ?: return@withTransaction 0
        // Reading done on this device after the reset is newer than it, so it stays.
        if ((book.lastReadAt ?: 0L) > resetAt) return@withTransaction 0
        val removedSessions = readingSessionDao.deleteForBookStartedBefore(book.id, resetAt)
        val hasStats = book.lastLocator != null || book.readingPercent > 0f || book.startedReadingAt != null ||
            book.finishedReadingAt != null || book.totalReadingSeconds > 0L || book.lastReadAt != null
        // Already clear (e.g. this device made the reset): don't touch updatedAt again on every sync.
        if (!hasStats) return@withTransaction if (removedSessions > 0) 1 else 0
        bookDao.resetReadingStats(book.id, maxOf(book.updatedAt, resetAt))
        1
    }

    override suspend fun updateReaderPrefs(id: Long, fontSizePercent: Int?, lineHeight: Float?, fontFamily: String?, sideMarginPercent: Int?) {
        bookDao.updateReaderPrefs(id, fontSizePercent, lineHeight, fontFamily, sideMarginPercent, System.currentTimeMillis())
    }

    override suspend fun clearReaderPrefs(id: Long) {
        bookDao.clearReaderPrefs(id, System.currentTimeMillis())
    }

    override suspend fun hasAnyBooks(): Boolean = bookDao.hasAnyBooks()

    override suspend fun lastReadOpenableBookId(): Long? = bookDao.lastReadOpenableBookId()

    override suspend fun lastReadBookId(): Long? = bookDao.lastReadBookId()

    override fun observeSearchIds(text: String, limit: Int): Flow<List<Long>> {
        val match = ftsPrefixMatch(searchTokens(text)) ?: return flowOf(emptyList())
        return bookDao.observeSearchIds(match, limit)
    }

    override suspend fun setReadNext(id: Long, queued: Boolean): List<Book> =
        database.withTransaction {
            val timestamp = System.currentTimeMillis()
            bookDao.setReadNext(id, if (queued) timestamp else null, timestamp)
            if (!queued) return@withTransaction emptyList()
            overflowReadNextIds().mapNotNull { overflowId ->
                bookDao.setReadNext(overflowId, null, timestamp)
                bookDao.getById(overflowId)?.toDomain()
            }
        }

    override suspend fun attachDownloadedFile(
        id: Long,
        filePath: String,
        fileHash: String,
        assetId: String,
        assetSha256: String,
        assetSizeBytes: Long,
        assetUploadedAt: Long,
    ) {
        bookDao.attachDownloadedFile(
            id = id,
            filePath = filePath,
            fileHash = fileHash,
            assetId = assetId,
            assetSha256 = assetSha256,
            assetSizeBytes = assetSizeBytes,
            assetUploadedAt = assetUploadedAt,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun removeLocalFile(id: Long): Boolean =
        bookDao.removeLocalFile(id, System.currentTimeMillis()) > 0

    override suspend fun markFileAssetUploaded(
        id: Long,
        assetId: String,
        assetSha256: String,
        assetSizeBytes: Long,
        assetUploadedAt: Long,
    ) {
        bookDao.markFileAssetUploaded(
            id = id,
            assetId = assetId,
            assetSha256 = assetSha256,
            assetSizeBytes = assetSizeBytes,
            assetUploadedAt = assetUploadedAt,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun markCoverAssetUploaded(
        id: Long,
        assetId: String,
        assetSha256: String,
        assetSizeBytes: Long,
        assetUploadedAt: Long,
    ) {
        bookDao.markCoverAssetUploaded(
            id = id,
            assetId = assetId,
            assetSha256 = assetSha256,
            assetSizeBytes = assetSizeBytes,
            assetUploadedAt = assetUploadedAt,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun attachDownloadedCover(
        id: Long,
        coverPath: String,
        assetId: String,
        assetSha256: String,
        assetSizeBytes: Long,
        assetUploadedAt: Long,
    ) {
        bookDao.attachDownloadedCover(
            id = id,
            coverPath = coverPath,
            assetId = assetId,
            assetSha256 = assetSha256,
            assetSizeBytes = assetSizeBytes,
            assetUploadedAt = assetUploadedAt,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun mergeCloudBook(record: CloudBookRecord): CloudBookMergeResult {
        if (record.syncId.isBlank() || record.title.isBlank() || record.fileHash.isBlank()) {
            return CloudBookMergeResult.SKIPPED
        }
        return database.withTransaction {
            mergeCloudBookLocked(record).also { result ->
                // A merge can only grow the queue when the record itself is queued.
                if (result != CloudBookMergeResult.SKIPPED && record.readNextAddedAt != null) trimReadNextQueueKeepingUpdatedAt()
            }
        }
    }

    // Only setReadNext enforces the cap locally; a merged cloud record can queue more books than that.
    private suspend fun trimReadNextQueueKeepingUpdatedAt() {
        val overflowIds = overflowReadNextIds()
        if (overflowIds.isNotEmpty()) bookDao.clearReadNextKeepingUpdatedAt(overflowIds)
    }

    /** Queued books beyond the cap, oldest additions first to go. */
    private suspend fun overflowReadNextIds(): List<Long> =
        bookDao.getReadNextQueueIdsNewestFirst().drop(MaxReadNextQueueBooks)

    private suspend fun mergeCloudBookLocked(record: CloudBookRecord): CloudBookMergeResult {
        // A permanently deleted book never comes back through sync, however new the record (e.g. from a device on an
        // app version that only soft-deleted it). A re-imported copy has a new sync id and syncs normally.
        if (tombstoneDao.findBySyncId(bookPurgeTombstoneId(record.syncId)) != null) return CloudBookMergeResult.SKIPPED
        // Deletes and restores compare deletion versions, not updatedAt: reading bumps updatedAt everywhere.
        val recordDeletionVersion = record.deletionUpdatedAt ?: 0L
        val tombstone = tombstoneDao.findBySyncId(record.syncId)
        if (tombstone != null) {
            if (recordDeletionVersion <= tombstone.deletedAt) {
                // Deleted after this record's last restore: the record is stale and the book stays deleted.
                bookDao.applySyncedDeletion(record.syncId, tombstone.deletedAt)
                return CloudBookMergeResult.SKIPPED
            }
            // Restored on another device after it was deleted.
            tombstoneDao.deleteBySyncId(record.syncId)
        }
        val bySyncId = bookDao.findAnyBySyncId(record.syncId)
        val activeBySyncId = if (bySyncId?.isDeleted == true) {
            if (recordDeletionVersion <= (bySyncId.deletionUpdatedAt ?: 0L)) return CloudBookMergeResult.SKIPPED
            bookDao.restoreFromSync(bySyncId.id, recordDeletionVersion)
            bookDao.findBySyncId(record.syncId)
        } else {
            bySyncId
        }
        val existing = activeBySyncId ?: bookDao.findByHash(record.fileHash)
        if (existing != null) {
            if (existing.syncId != record.syncId) {
                bookAliasDao.upsert(BookAliasEntity(syncId = record.syncId, fileHash = record.fileHash, createdAt = minOf(existing.createdAt, record.createdAt)))
            }
            val hasSameAsset = existing.fileAssetId == record.assetId &&
                existing.fileAssetSha256 == record.assetSha256 &&
                existing.fileAssetSizeBytes == record.assetSizeBytes &&
                existing.fileAssetUploadedAt == record.assetUploadedAt
            val hasSameCoverAsset = existing.coverAssetId == record.coverAssetId &&
                existing.coverAssetSha256 == record.coverAssetSha256 &&
                existing.coverAssetSizeBytes == record.coverAssetSizeBytes &&
                existing.coverAssetUploadedAt == record.coverAssetUploadedAt
            if (existing.fileAvailability == BookFileAvailability.LOCAL.name ||
                existing.fileAvailability == BookFileAvailability.UPLOAD_PENDING.name
            ) {
                val shouldApplyRemoteMetadata = record.updatedAt > existing.updatedAt
                val readNext = existing.readNextState().mergedWith(record.readNextState())
                val applyRemoteOfflineReading = shouldApplyRemoteMetadata &&
                    record.format.isOffline && BookFormat.valueOf(existing.format).isOffline
                val base = if (applyRemoteOfflineReading) existing.withOfflineReadingFrom(record) else existing
                val merged = base.copy(
                    title = if (shouldApplyRemoteMetadata) record.title else existing.title,
                    author = if (shouldApplyRemoteMetadata) record.author else existing.author,
                    series = if (shouldApplyRemoteMetadata) record.series else existing.series,
                    seriesNumber = if (shouldApplyRemoteMetadata) record.seriesNumber else existing.seriesNumber,
                    description = if (shouldApplyRemoteMetadata) record.description else existing.description,
                    tagsCsv = if (shouldApplyRemoteMetadata) record.tagsCsv.normalizedBookTagsCsv() else existing.tagsCsv,
                    rating = if (shouldApplyRemoteMetadata) record.rating.coerceIn(0f, 5f) else existing.rating,
                    fileAssetId = if (!hasSameAsset) record.assetId else existing.fileAssetId,
                    fileAssetSha256 = if (!hasSameAsset) record.assetSha256 else existing.fileAssetSha256,
                    fileAssetSizeBytes = if (!hasSameAsset) record.assetSizeBytes else existing.fileAssetSizeBytes,
                    fileAssetUploadedAt = if (!hasSameAsset) record.assetUploadedAt else existing.fileAssetUploadedAt,
                    coverAssetId = if (!hasSameCoverAsset && record.hasCoverAsset()) record.coverAssetId else existing.coverAssetId,
                    coverAssetSha256 = if (!hasSameCoverAsset && record.hasCoverAsset()) record.coverAssetSha256 else existing.coverAssetSha256,
                    coverAssetSizeBytes = if (!hasSameCoverAsset && record.hasCoverAsset()) record.coverAssetSizeBytes else existing.coverAssetSizeBytes,
                    coverAssetUploadedAt = if (!hasSameCoverAsset && record.hasCoverAsset()) record.coverAssetUploadedAt else existing.coverAssetUploadedAt,
                    goodreadsUrl = existing.goodreadsUrl.mergeRemoteOptional(record.goodreadsUrl, shouldApplyRemoteMetadata),
                    goodreadsRating = existing.goodreadsRating.mergeRemoteOptional(record.goodreadsRating, shouldApplyRemoteMetadata),
                    goodreadsRatingsCount = existing.goodreadsRatingsCount.mergeRemoteOptional(record.goodreadsRatingsCount, shouldApplyRemoteMetadata),
                    originalPublicationYear = existing.originalPublicationYear.mergeRemoteOptional(record.originalPublicationYear, shouldApplyRemoteMetadata),
                    readNextAddedAt = readNext.addedAt,
                    readNextUpdatedAt = readNext.updatedAt,
                    updatedAt = maxOf(existing.updatedAt, record.updatedAt),
                )
                if (merged != existing) {
                    bookDao.update(merged)
                    return CloudBookMergeResult.UPDATED
                }
                return CloudBookMergeResult.SKIPPED
            }

            val progressResetAt = tombstoneDao.findBySyncId(readingProgressResetTombstoneId(existing.syncId))?.deletedAt
            bookDao.update(
                record.toCloudOnlyEntity(id = existing.id, coverPath = existing.coverPath)
                    .withCoverAlternatesFrom(existing)
                    .withMergedGoodreadsFieldsFrom(existing)
                    .withMergedReadNextFrom(existing)
                    .keepingResetProgress(existing, progressResetAt),
            )
            return CloudBookMergeResult.UPDATED
        }

        bookDao.insert(record.toCloudOnlyEntity(id = 0, coverPath = null))
        return CloudBookMergeResult.CREATED
    }
}

private fun BookEntity.readingProgressVersion(): ReadingProgressVersion =
    ReadingProgressVersion(
        syncId = syncId,
        fileHash = fileHash,
        locator = lastLocator,
        readingPercent = readingPercent.sanitizedReadingPercent(),
        lastReadAt = lastReadAt,
        updatedAt = updatedAt,
    )

internal fun BookEntity.toDomain(): Book {
    val bookFormat = BookFormat.valueOf(format)
    val ownership = physicalOwnership
        ?.let { runCatching { PhysicalBookOwnership.valueOf(it) }.getOrNull() }
        .takeIf { bookFormat == BookFormat.PHYSICAL }
    val normalizedBorrowReturnAt = borrowReturnAt
        ?.takeIf { bookFormat == BookFormat.PHYSICAL && ownership == PhysicalBookOwnership.BORROWED }
        ?.let(::normalizeBorrowReturnAt)

    return Book(
        id = id,
        syncId = syncId,
        title = title,
        author = author,
        series = series,
        seriesNumber = seriesNumber,
        description = description,
        tagsCsv = tagsCsv,
        coverPath = coverPath,
        filePath = filePath,
        fileAvailability = runCatching { BookFileAvailability.valueOf(fileAvailability) }.getOrDefault(BookFileAvailability.LOCAL),
        format = bookFormat,
        fileHash = fileHash,
        fileAssetId = fileAssetId,
        fileAssetSha256 = fileAssetSha256,
        fileAssetSizeBytes = fileAssetSizeBytes,
        fileAssetUploadedAt = fileAssetUploadedAt,
        coverAssetId = coverAssetId,
        coverAssetSha256 = coverAssetSha256,
        coverAssetSizeBytes = coverAssetSizeBytes,
        coverAssetUploadedAt = coverAssetUploadedAt,
        readingPercent = readingPercent.sanitizedReadingPercent(),
        rating = rating,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastReadAt = lastReadAt,
        lastLocator = lastLocator,
        startedReadingAt = startedReadingAt,
        finishedReadingAt = finishedReadingAt,
        totalReadingSeconds = totalReadingSeconds,
        customFontSizePercent = customFontSizePercent,
        customLineHeight = customLineHeight,
        customFontFamily = customFontFamily,
        customSideMarginPercent = customSideMarginPercent,
        readNextAddedAt = readNextAddedAt,
        readNextUpdatedAt = readNextUpdatedAt,
        goodreadsUrl = goodreadsUrl,
        goodreadsRating = goodreadsRating,
        goodreadsRatingsCount = goodreadsRatingsCount,
        originalPublicationYear = originalPublicationYear,
        customCoverPath = customCoverPath,
        goodreadsCoverPath = goodreadsCoverPath,
        // Kept when an audiobook was switched from a paper book, but an audiobook has no pages to show.
        pageCount = pageEstimate?.takeIf { it > 0 && bookFormat.tracksPages },
        physicalOwnership = ownership,
        borrowReturnAt = normalizedBorrowReturnAt,
    )
}

/** Alternate cover choices are local files, so a cloud rewrite must carry them over. */
private fun BookEntity.withCoverAlternatesFrom(existing: BookEntity): BookEntity = copy(
    customCoverPath = existing.customCoverPath,
    goodreadsCoverPath = existing.goodreadsCoverPath,
)

private fun BookEntity.withMergedGoodreadsFieldsFrom(existing: BookEntity): BookEntity = copy(
    goodreadsUrl = existing.goodreadsUrl.mergeRemoteOptional(goodreadsUrl, remoteIsNewer = updatedAt > existing.updatedAt),
    goodreadsRating = existing.goodreadsRating.mergeRemoteOptional(goodreadsRating, remoteIsNewer = updatedAt > existing.updatedAt),
    goodreadsRatingsCount = existing.goodreadsRatingsCount.mergeRemoteOptional(goodreadsRatingsCount, remoteIsNewer = updatedAt > existing.updatedAt),
    originalPublicationYear = existing.originalPublicationYear.mergeRemoteOptional(originalPublicationYear, remoteIsNewer = updatedAt > existing.updatedAt),
)

/** Cloud progress recorded before a local reading-stats reset ([resetAt]) mustn't bring the old stats back. */
private fun BookEntity.keepingResetProgress(existing: BookEntity, resetAt: Long?): BookEntity {
    if (resetAt == null || (lastReadAt ?: updatedAt) > resetAt) return this
    return copy(
        lastLocator = existing.lastLocator,
        readingPercent = existing.readingPercent,
        startedReadingAt = existing.startedReadingAt,
        finishedReadingAt = existing.finishedReadingAt,
        totalReadingSeconds = existing.totalReadingSeconds,
        lastReadAt = existing.lastReadAt,
    )
}

/**
 * "Read next" merges on its own version, not [BookEntity.updatedAt]: reading a book bumps updatedAt, and that
 * mustn't undo a queue change made on another device.
 */
internal data class ReadNextState(val addedAt: Long?, val updatedAt: Long?) {
    // Rows from before readNextUpdatedAt existed only know when they were queued.
    val version: Long get() = updatedAt ?: addedAt ?: 0L
}

internal fun ReadNextState.mergedWith(remote: ReadNextState): ReadNextState =
    if (remote.version > version) remote else this

private fun BookEntity.readNextState(): ReadNextState = ReadNextState(readNextAddedAt, readNextUpdatedAt)

private fun CloudBookRecord.readNextState(): ReadNextState = ReadNextState(readNextAddedAt, readNextUpdatedAt)

private fun BookEntity.withMergedReadNextFrom(existing: BookEntity): BookEntity {
    val readNext = existing.readNextState().mergedWith(readNextState())
    return copy(readNextAddedAt = readNext.addedAt, readNextUpdatedAt = readNext.updatedAt)
}

private fun CloudBookRecord.toCloudOnlyEntity(id: Long, coverPath: String?): BookEntity =
    BookEntity(
        id = id,
        syncId = syncId,
        title = title,
        author = author,
        series = series,
        seriesNumber = seriesNumber,
        description = description,
        tagsCsv = tagsCsv.normalizedBookTagsCsv(),
        coverPath = coverPath,
        filePath = "",
        // Nothing to download for a book read outside the app: it is complete as soon as it lands.
        fileAvailability = if (format.isOffline) BookFileAvailability.LOCAL.name else BookFileAvailability.CLOUD_ONLY.name,
        format = format.name,
        fileHash = fileHash,
        fileAssetId = assetId,
        fileAssetSha256 = assetSha256,
        fileAssetSizeBytes = assetSizeBytes,
        fileAssetUploadedAt = assetUploadedAt,
        coverAssetId = coverAssetId,
        coverAssetSha256 = coverAssetSha256,
        coverAssetSizeBytes = coverAssetSizeBytes,
        coverAssetUploadedAt = coverAssetUploadedAt,
        lastLocator = lastLocator,
        readingPercent = readingPercent.sanitizedReadingPercent(),
        rating = rating.coerceIn(0f, 5f),
        groupId = null,
        isDeleted = false,
        wordCount = wordCount,
        pageEstimate = pageEstimate,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastReadAt = lastReadAt,
        startedReadingAt = startedReadingAt,
        finishedReadingAt = finishedReadingAt,
        totalReadingSeconds = totalReadingSeconds.coerceAtLeast(0L),
        customFontSizePercent = customFontSizePercent,
        customLineHeight = customLineHeight,
        customFontFamily = customFontFamily,
        customSideMarginPercent = customSideMarginPercent,
        readNextAddedAt = readNextAddedAt,
        readNextUpdatedAt = readNextUpdatedAt,
        deletionUpdatedAt = deletionUpdatedAt,
        goodreadsUrl = goodreadsUrl,
        goodreadsRating = goodreadsRating?.coerceIn(0f, 5f),
        goodreadsRatingsCount = goodreadsRatingsCount,
        originalPublicationYear = originalPublicationYear,
        physicalOwnership = (physicalOwnership ?: PhysicalBookOwnership.OWNED).name
            .takeIf { format == BookFormat.PHYSICAL },
        borrowReturnAt = normalizeBorrowReturnAt(borrowReturnAt)
            .takeIf { format == BookFormat.PHYSICAL && physicalOwnership == PhysicalBookOwnership.BORROWED },
    )

private fun CloudBookRecord.hasCoverAsset(): Boolean =
    coverAssetId != null &&
        coverAssetSha256 != null &&
        coverAssetSizeBytes != null &&
        coverAssetUploadedAt != null

private fun <T> T?.mergeRemoteOptional(remote: T?, remoteIsNewer: Boolean): T? =
    when {
        remote == null -> this
        this == null || remoteIsNewer -> remote
        else -> this
    }

internal fun Float.sanitizedReadingPercent(): Float =
    if (isFinite()) coerceIn(0f, 1f) else 0f

private const val MaxReadNextQueueBooks = 2
private const val RemoteProgressEventBufferCapacity = 32

/** A new, unread book row with its file on this device (none for a book read outside the app). */
private fun newLocalBookEntity(
    title: String,
    author: String?,
    format: BookFormat,
    filePath: String,
    fileHash: String,
    series: String? = null,
    seriesNumber: String? = null,
    description: String? = null,
    tagsCsv: String? = null,
    coverPath: String? = null,
): BookEntity {
    val now = System.currentTimeMillis()
    return BookEntity(
        title = title,
        author = author,
        series = series,
        seriesNumber = seriesNumber,
        description = description,
        tagsCsv = tagsCsv,
        coverPath = coverPath,
        filePath = filePath,
        fileAvailability = BookFileAvailability.LOCAL.name,
        format = format.name,
        fileHash = fileHash,
        lastLocator = null,
        readingPercent = 0f,
        rating = 0f,
        groupId = null,
        isDeleted = false,
        wordCount = null,
        pageEstimate = null,
        createdAt = now,
        updatedAt = now,
        lastReadAt = null,
        startedReadingAt = null,
        finishedReadingAt = null,
        totalReadingSeconds = 0L,
    )
}

/** A book read outside the app has no reader position to sync, so its type, dates and pages ride with its metadata. */
private fun BookEntity.withOfflineReadingFrom(record: CloudBookRecord): BookEntity = copy(
    format = record.format.name,
    startedReadingAt = record.startedReadingAt,
    finishedReadingAt = record.finishedReadingAt,
    pageEstimate = record.pageEstimate,
    readingPercent = record.readingPercent.sanitizedReadingPercent(),
    physicalOwnership = when {
        record.format != BookFormat.PHYSICAL -> null
        record.physicalOwnership != null -> record.physicalOwnership.name
        else -> physicalOwnership
    },
    borrowReturnAt = when {
        record.format != BookFormat.PHYSICAL -> null
        record.physicalOwnership == PhysicalBookOwnership.BORROWED -> normalizeBorrowReturnAt(record.borrowReturnAt)
        record.physicalOwnership == PhysicalBookOwnership.OWNED -> null
        else -> borrowReturnAt
    },
)
