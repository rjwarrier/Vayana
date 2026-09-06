package com.vayana.core.database.repository

import com.vayana.core.database.dao.BookDao
import com.vayana.core.database.entity.BookEntity
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
) : BookRepository {
    private val _remoteReadingProgressApplied = MutableSharedFlow<RemoteReadingProgressApplied>(
        extraBufferCapacity = RemoteProgressEventBufferCapacity,
    )

    override val remoteReadingProgressApplied: Flow<RemoteReadingProgressApplied> =
        _remoteReadingProgressApplied.asSharedFlow()

    override fun observeAll(): Flow<List<Book>> =
        bookDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeDeleted(): Flow<List<Book>> =
        bookDao.observeDeleted().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: Long): Book? = bookDao.getById(id)?.toDomain()

    override suspend fun findActiveBySyncIdOrHash(syncId: String, fileHash: String): Book? =
        (bookDao.findBySyncId(syncId) ?: bookDao.findByHash(fileHash))?.toDomain()

    override suspend fun updateLocator(id: Long, locator: String, readingPercent: Float) {
        bookDao.updateLocator(id, locator, readingPercent, System.currentTimeMillis())
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
    ): ReadingProgressMergeResult {
        if (locator.isBlank() || remoteUpdatedAt <= 0L || readingPercent !in 0f..1f) {
            return ReadingProgressMergeResult.InvalidRemote
        }
        val book = bookDao.findBySyncId(syncId) ?: bookDao.findByHash(fileHash)
            ?: return ReadingProgressMergeResult.NoLocalMatch
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
            return ReadingProgressMergeResult.ConflictLocalKept(
                local = local,
                remote = remote,
                reason = ReadingProgressConflictReason.INCOMPATIBLE_FILE_REVISION,
            )
        }
        val localVersion = book.lastReadAt ?: 0L
        val remoteVersion = lastReadAt ?: remoteUpdatedAt
        if (localVersion > remoteVersion) {
            return ReadingProgressMergeResult.LocalNewer
        }
        if (localVersion == remoteVersion && book.lastLocator != locator) {
            return ReadingProgressMergeResult.ConflictLocalKept(
                local = local,
                remote = remote,
                reason = ReadingProgressConflictReason.SAME_TIMESTAMP_DIFFERENT_LOCATOR,
            )
        }
        if (localVersion == remoteVersion) {
            return ReadingProgressMergeResult.LocalNewer
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
        _remoteReadingProgressApplied.emit(
            RemoteReadingProgressApplied(
                bookId = book.id,
                locator = locator,
                readingPercent = readingPercent,
                version = remoteVersion,
            ),
        )
        return ReadingProgressMergeResult.AppliedRemote
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

    override suspend fun updateCover(id: Long, coverPath: String?) {
        bookDao.updateCover(id, coverPath, System.currentTimeMillis())
    }

    override suspend fun insertIfNew(
        title: String,
        author: String?,
        series: String?,
        seriesNumber: String?,
        description: String?,
        coverPath: String?,
        filePath: String,
        format: BookFormat,
        fileHash: String,
    ): Book? {
        if (bookDao.findByHash(fileHash) != null) return null

        val now = System.currentTimeMillis()
        val entity = BookEntity(
            title = title,
            author = author,
            series = series,
            seriesNumber = seriesNumber,
            description = description,
            tagsCsv = null,
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
        val id = bookDao.insert(entity)
        return entity.copy(id = id).toDomain()
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
    ): Boolean {
        val existing = bookDao.findByHash(fileHash)
        if (existing != null && existing.id != id) return false
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
        return true
    }

    override suspend fun softDelete(id: Long) {
        bookDao.softDelete(id, System.currentTimeMillis())
    }

    override suspend fun restore(id: Long) {
        bookDao.restore(id, System.currentTimeMillis())
    }

    override suspend fun purge(id: Long) {
        bookDao.purge(id)
    }

    override suspend fun markFinished(id: Long) {
        bookDao.markFinished(id, System.currentTimeMillis())
    }

    override suspend fun updateReaderPrefs(id: Long, fontSizePercent: Int?, lineHeight: Float?, fontFamily: String?, sideMarginPercent: Int?) {
        bookDao.updateReaderPrefs(id, fontSizePercent, lineHeight, fontFamily, sideMarginPercent, System.currentTimeMillis())
    }

    override suspend fun clearReaderPrefs(id: Long) {
        bookDao.clearReaderPrefs(id, System.currentTimeMillis())
    }

    override fun observeReadNextQueue(): Flow<List<Book>> =
        bookDao.observeReadNextQueue().map { entities -> entities.map { it.toDomain() } }

    override suspend fun setReadNext(id: Long, queued: Boolean) {
        bookDao.setReadNext(id, if (queued) System.currentTimeMillis() else null, System.currentTimeMillis())
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
        val existing = bookDao.findBySyncId(record.syncId) ?: bookDao.findByHash(record.fileHash)
        if (existing != null) {
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
                val merged = existing.copy(
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
                    updatedAt = maxOf(existing.updatedAt, record.updatedAt),
                )
                if (merged != existing) {
                    bookDao.update(merged)
                    return CloudBookMergeResult.UPDATED
                }
                return CloudBookMergeResult.SKIPPED
            }

            bookDao.update(record.toCloudOnlyEntity(id = existing.id, coverPath = existing.coverPath))
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
        readingPercent = readingPercent,
        lastReadAt = lastReadAt,
        updatedAt = updatedAt,
    )

internal fun BookEntity.toDomain(): Book = Book(
    id = id,
    title = title,
    author = author,
    series = series,
    seriesNumber = seriesNumber,
    description = description,
    tagsCsv = tagsCsv,
    coverPath = coverPath,
    filePath = filePath,
    fileAvailability = runCatching { BookFileAvailability.valueOf(fileAvailability) }.getOrDefault(BookFileAvailability.LOCAL),
    format = BookFormat.valueOf(format),
    fileHash = fileHash,
    fileAssetId = fileAssetId,
    fileAssetSha256 = fileAssetSha256,
    fileAssetSizeBytes = fileAssetSizeBytes,
    fileAssetUploadedAt = fileAssetUploadedAt,
    coverAssetId = coverAssetId,
    coverAssetSha256 = coverAssetSha256,
    coverAssetSizeBytes = coverAssetSizeBytes,
    coverAssetUploadedAt = coverAssetUploadedAt,
    readingPercent = readingPercent,
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
)

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
        fileAvailability = BookFileAvailability.CLOUD_ONLY.name,
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
        readingPercent = readingPercent.coerceIn(0f, 1f),
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
)

private fun CloudBookRecord.hasCoverAsset(): Boolean =
    coverAssetId != null &&
        coverAssetSha256 != null &&
        coverAssetSizeBytes != null &&
        coverAssetUploadedAt != null

private fun String?.normalizedBookTagsCsv(): String? =
    this?.split(",")
        ?.map { it.normalizedBookTag() }
        ?.filter { it.isNotEmpty() }
        ?.distinctBy { it.lowercase() }
        ?.take(MaxBookTags)
        ?.joinToString(", ")
        ?.take(MaxBookTagsCsvChars)
        ?.trimEnd(',', ' ')
        ?.ifBlank { null }

private fun String.normalizedBookTag(): String =
    map { if (Character.isISOControl(it)) ' ' else it }
        .joinToString("")
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(MaxBookTagChars)
        .trim()

private const val MaxBookTags = 32
private const val MaxBookTagChars = 40
private const val MaxBookTagsCsvChars = 1_024
private const val RemoteProgressEventBufferCapacity = 32
