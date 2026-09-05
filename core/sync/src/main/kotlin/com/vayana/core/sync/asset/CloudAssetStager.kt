package com.vayana.core.sync.asset

import com.vayana.core.common.DispatcherProvider
import com.vayana.core.common.Hashing
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.filesystem.StorageRoots
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

@Singleton
class CloudAssetStager @Inject constructor(
    private val bookRepository: BookRepository,
    private val storageRoots: StorageRoots,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun stageDownloadedBook(
        bookId: Long,
        reference: CloudAssetReference,
        plaintextBytes: ByteArray,
        extension: String,
    ): StagedCloudAsset = withContext(dispatchers.io) {
        val normalizedExtension = extension.trim().lowercase().takeIf { it.matches(FileExtensionRegex) } ?: "epub"
        val actualSha256 = Hashing.sha256(plaintextBytes)
        check(actualSha256 == reference.sha256) { "Downloaded book did not match its expected hash" }
        check(plaintextBytes.size.toLong() == reference.sizeBytes) { "Downloaded book size did not match its expected size" }

        val destination = File(storageRoots.booksDir, "${UUID.randomUUID()}.$normalizedExtension")
        try {
            destination.writeBytes(plaintextBytes)
            val relativePath = storageRoots.relativize(destination)
            bookRepository.attachDownloadedFile(
                id = bookId,
                filePath = relativePath,
                fileHash = actualSha256,
                assetId = reference.id,
                assetSha256 = reference.sha256,
                assetSizeBytes = reference.sizeBytes,
                assetUploadedAt = reference.uploadedAt,
            )
            StagedCloudAsset(
                reference = reference,
                relativePath = relativePath,
                plaintextSha256 = actualSha256,
            )
        } catch (throwable: Throwable) {
            destination.delete()
            throw throwable
        }
    }

    suspend fun stageDownloadedCover(
        bookId: Long,
        reference: CloudAssetReference,
        plaintextBytes: ByteArray,
    ): StagedCloudAsset = withContext(dispatchers.io) {
        val actualSha256 = Hashing.sha256(plaintextBytes)
        check(actualSha256 == reference.sha256) { "Downloaded cover did not match its expected hash" }
        check(plaintextBytes.size.toLong() == reference.sizeBytes) { "Downloaded cover size did not match its expected size" }

        val destination = File(storageRoots.coversDir, "${UUID.randomUUID()}.jpg")
        try {
            destination.writeBytes(plaintextBytes)
            val relativePath = storageRoots.relativize(destination)
            bookRepository.attachDownloadedCover(
                id = bookId,
                coverPath = relativePath,
                assetId = reference.id,
                assetSha256 = reference.sha256,
                assetSizeBytes = reference.sizeBytes,
                assetUploadedAt = reference.uploadedAt,
            )
            StagedCloudAsset(
                reference = reference,
                relativePath = relativePath,
                plaintextSha256 = actualSha256,
            )
        } catch (throwable: Throwable) {
            destination.delete()
            throw throwable
        }
    }
}

private val FileExtensionRegex = Regex("^[a-z0-9]{1,8}$")
