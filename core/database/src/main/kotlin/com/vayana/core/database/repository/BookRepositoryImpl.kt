package com.vayana.core.database.repository

import com.vayana.core.database.dao.BookDao
import com.vayana.core.database.entity.BookEntity
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
) : BookRepository {

    override fun observeAll(): Flow<List<Book>> =
        bookDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeDeleted(): Flow<List<Book>> =
        bookDao.observeDeleted().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: Long): Book? = bookDao.getById(id)?.toDomain()

    override suspend fun updateLocator(id: Long, locator: String, readingPercent: Float) {
        bookDao.updateLocator(id, locator, readingPercent, System.currentTimeMillis())
    }

    override suspend fun addReadingTime(id: Long, addedSeconds: Long) {
        bookDao.addReadingTime(id, addedSeconds, System.currentTimeMillis())
    }

    override suspend fun recordBookOpened(id: Long) {
        bookDao.recordBookOpened(id, System.currentTimeMillis())
    }

    override suspend fun updateMetadata(id: Long, title: String, author: String?, series: String?, seriesNumber: String?, description: String?) {
        bookDao.updateMetadata(id, title, author, series, seriesNumber, description, System.currentTimeMillis())
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
}

internal fun BookEntity.toDomain(): Book = Book(
    id = id,
    title = title,
    author = author,
    series = series,
    seriesNumber = seriesNumber,
    description = description,
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
