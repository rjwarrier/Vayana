package com.vayana.core.backup

import com.vayana.core.common.DispatcherProvider
import com.vayana.core.database.dao.AnnotationDao
import com.vayana.core.database.dao.BookAliasDao
import com.vayana.core.database.dao.BookDao
import com.vayana.core.database.dao.ReadingSessionDao
import com.vayana.core.database.dao.ShelfDao
import com.vayana.core.database.dao.TombstoneDao
import com.vayana.core.database.dao.VocabularyCardDao
import com.vayana.core.database.dao.WordLookupStatDao
import com.vayana.core.database.entity.AnnotationEntity
import com.vayana.core.database.entity.BookAliasEntity
import com.vayana.core.database.entity.BookEntity
import com.vayana.core.database.entity.BookShelfCrossRefEntity
import com.vayana.core.database.entity.ReadingSessionEntity
import com.vayana.core.database.entity.ShelfEntity
import com.vayana.core.database.entity.TombstoneEntity
import com.vayana.core.database.entity.VocabularyCardEntity
import com.vayana.core.database.entity.WordLookupStatEntity
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.normalizeBorrowReturnAt
import com.vayana.core.datastore.settings.SettingsRegistry
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.filesystem.StorageRoots
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

@Singleton
class SnapshotExporter @Inject constructor(
    private val bookDao: BookDao,
    private val annotationDao: AnnotationDao,
    private val shelfDao: ShelfDao,
    private val readingSessionDao: ReadingSessionDao,
    private val bookAliasDao: BookAliasDao,
    private val tombstoneDao: TombstoneDao,
    private val vocabularyCardDao: VocabularyCardDao,
    private val wordLookupStatDao: WordLookupStatDao,
    private val settingsRepository: SettingsRepository,
    private val storageRoots: StorageRoots,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun export(): PortableSnapshot = withContext(dispatchers.io) {
        val books = bookDao.getAllForSync()
        val bookSyncIdsByLocalId = books.associate { it.id to it.syncId }
        val shelves = shelfDao.getAllForSync()
        val shelfSyncIdsByLocalId = shelves.associate { it.id to it.syncId }
        val allSettings = settingsRepository.exportToMap()
        val settings = allSettings
            .filterKeys { it in PortableSettings.allowlist }
            .toSortedMap()

        PortableSnapshot(
            formatVersion = CurrentPortableSnapshotVersion,
            exportedAt = System.currentTimeMillis(),
            deviceLabel = allSettings[SettingsRegistry.KindleDeviceName.key].orEmpty().ifBlank { SettingsRegistry.KindleDeviceName.defaultValue },
            books = books.map { it.toPortable() }.sortedBy { it.syncId },
            annotations = annotationDao.getAllForSync()
                .mapNotNull { it.toPortable(bookSyncIdsByLocalId) }
                .sortedBy { it.syncId },
            shelves = shelves.map { it.toPortable() }.sortedBy { it.syncId },
            shelfMemberships = shelfDao.getMembershipsForSync()
                .mapNotNull { it.toPortable(bookSyncIdsByLocalId, shelfSyncIdsByLocalId) }
                .sortedWith(compareBy({ it.bookSyncId }, { it.shelfSyncId })),
            readingSessions = readingSessionDao.getAllForSync()
                .mapNotNull { it.toPortable(bookSyncIdsByLocalId) }
                .sortedBy { it.syncId },
            vocabularyCards = vocabularyCardDao.getAllForSync()
                .map { it.toPortable(bookSyncIdsByLocalId) }
                .sortedBy { it.syncId },
            wordLookupCounters = wordLookupStatDao.getAllForSync()
                .map { it.toPortable() }
                .sortedWith(compareBy({ it.word.lowercase() }, { it.writerOrigin })),
            bookAliases = bookAliasDao.getAll()
                .map { it.toPortable() }
                .sortedWith(compareBy({ it.fileHash }, { it.syncId })),
            tombstones = tombstoneDao.getAll()
                .map { it.toPortable() }
                .sortedWith(compareBy({ it.entityType }, { it.deletedAt }, { it.syncId })),
            settings = settings,
        )
    }

    private fun BookEntity.toPortable(): PortableBook = PortableBook(
        syncId = syncId,
        title = title,
        author = author,
        series = series,
        seriesNumber = seriesNumber,
        description = description,
        tagsCsv = tagsCsv,
        format = format,
        fileHash = fileHash,
        fileAvailability = fileAvailability,
        fileAvailableLocally = fileAvailability == BookFileAvailability.LOCAL.name && filePath.isNotBlank() && storageRoots.resolve(filePath).isFile,
        fileAsset = fileAssetId.toPortableAsset(fileAssetSha256, fileAssetSizeBytes, fileAssetUploadedAt),
        coverAvailableLocally = coverPath?.let { storageRoots.resolve(it).isFile } ?: false,
        coverAsset = coverAssetId.toPortableAsset(coverAssetSha256, coverAssetSizeBytes, coverAssetUploadedAt),
        lastLocator = lastLocator,
        readingPercent = readingPercent,
        rating = rating,
        groupId = groupId,
        isDeleted = isDeleted,
        wordCount = wordCount,
        pageEstimate = pageEstimate,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastReadAt = lastReadAt,
        startedReadingAt = startedReadingAt,
        finishedReadingAt = finishedReadingAt,
        totalReadingSeconds = totalReadingSeconds,
        customFontSizePercent = customFontSizePercent,
        customLineHeight = customLineHeight,
        customFontFamily = customFontFamily,
        customSideMarginPercent = customSideMarginPercent,
        readNextAddedAt = readNextAddedAt,
        readNextUpdatedAt = readNextUpdatedAt,
        readNextPinned = readNextPinned,
        readingDisposition = readingDisposition,
        dispositionReason = dispositionReason,
        dispositionUpdatedAt = dispositionUpdatedAt,
        deletionUpdatedAt = deletionUpdatedAt,
        goodreadsUrl = goodreadsUrl,
        goodreadsRating = goodreadsRating,
        goodreadsRatingsCount = goodreadsRatingsCount,
        originalPublicationYear = originalPublicationYear,
        physicalOwnership = physicalOwnership,
        borrowReturnAt = normalizeBorrowReturnAt(borrowReturnAt),
        gutenbergId = gutenbergId,
    )

    private fun AnnotationEntity.toPortable(bookSyncIdsByLocalId: Map<Long, String>): PortableAnnotation? {
        val bookSyncId = bookSyncIdsByLocalId[bookId] ?: return null
        return PortableAnnotation(
            syncId = syncId,
            bookSyncId = bookSyncId,
            type = type,
            colorKey = colorKey,
            locator = locator,
            chapterTitle = chapterTitle,
            chapterHref = chapterHref,
            selectedText = selectedText,
            readerNote = readerNote,
            reviewQuestion = reviewQuestion,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isDeleted = isDeleted,
        )
    }

    private fun ShelfEntity.toPortable(): PortableShelf = PortableShelf(
        syncId = syncId,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun BookShelfCrossRefEntity.toPortable(
        bookSyncIdsByLocalId: Map<Long, String>,
        shelfSyncIdsByLocalId: Map<Long, String>,
    ): PortableShelfMembership? {
        val bookSyncId = bookSyncIdsByLocalId[bookId] ?: return null
        val shelfSyncId = shelfSyncIdsByLocalId[shelfId] ?: return null
        return PortableShelfMembership(
            bookSyncId = bookSyncId,
            shelfSyncId = shelfSyncId,
            createdAt = createdAt,
        )
    }

    private fun ReadingSessionEntity.toPortable(bookSyncIdsByLocalId: Map<Long, String>): PortableReadingSession? {
        val bookSyncId = bookSyncIdsByLocalId[bookId] ?: return null
        return PortableReadingSession(
            syncId = syncId,
            bookSyncId = bookSyncId,
            startedAt = startedAt,
            endedAt = endedAt,
            durationSeconds = durationSeconds,
            startPage = startPage,
            endPage = endPage,
        )
    }

    private fun VocabularyCardEntity.toPortable(bookSyncIdsByLocalId: Map<Long, String>): PortableVocabularyCard = PortableVocabularyCard(
        syncId = syncId,
        word = word,
        definition = definition,
        sentence = sentence,
        bookSyncId = bookId?.let(bookSyncIdsByLocalId::get),
        bookTitle = bookTitle,
        createdAt = createdAt,
        lastReviewedAt = lastReviewedAt,
        known = known,
        dueAt = dueAt,
        intervalDays = intervalDays,
        easeFactor = easeFactor,
        repetitions = repetitions,
    )

    private fun WordLookupStatEntity.toPortable(): PortableWordLookupCounter = PortableWordLookupCounter(
        word = word,
        writerOrigin = writerOrigin,
        count = count,
        lastLookedUpAt = lastLookedUpAt,
    )

    private fun BookAliasEntity.toPortable(): PortableBookAlias = PortableBookAlias(
        syncId = syncId,
        fileHash = fileHash,
        createdAt = createdAt,
    )

    private fun TombstoneEntity.toPortable(): PortableTombstone = PortableTombstone(
        syncId = syncId,
        entityType = entityType,
        deletedAt = deletedAt,
    )

    private fun String?.toPortableAsset(sha256: String?, sizeBytes: Long?, uploadedAt: Long?): PortableAsset? {
        val assetId = this?.takeIf { it.isNotBlank() } ?: return null
        val assetSha256 = sha256?.takeIf { it.isNotBlank() } ?: return null
        val assetSizeBytes = sizeBytes?.takeIf { it >= 0L } ?: return null
        val assetUploadedAt = uploadedAt?.takeIf { it > 0L } ?: return null
        return PortableAsset(
            id = assetId,
            sha256 = assetSha256,
            sizeBytes = assetSizeBytes,
            uploadedAt = assetUploadedAt,
        )
    }
}

const val CurrentPortableSnapshotVersion = 1
