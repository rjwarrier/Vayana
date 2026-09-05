package com.vayana.core.sync.asset

import com.vayana.core.common.DispatcherProvider
import com.vayana.core.common.Hashing
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.filesystem.StorageRoots
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

data class PreparedCloudAsset(
    val reference: CloudAssetReference,
    val path: String,
    val encryptedBytes: ByteArray,
)

@Singleton
class CloudBookAssetTransfer @Inject constructor(
    private val bookRepository: BookRepository,
    private val storageRoots: StorageRoots,
    private val stager: CloudAssetStager,
    private val dispatchers: DispatcherProvider,
) {
    private val cipher = CloudAssetCipher()

    suspend fun uploadBookFile(bookId: Long, passphrase: CharArray, store: CloudAssetStore): CloudAssetReference =
        withContext(dispatchers.io) {
            require(passphrase.isNotEmpty()) { "Cloud asset passphrase is required" }
            val prepared = prepareBookFile(bookId, passphrase)
            store.put(prepared.path, prepared.encryptedBytes)
            bookRepository.markFileAssetUploaded(
                id = bookId,
                assetId = prepared.reference.id,
                assetSha256 = prepared.reference.sha256,
                assetSizeBytes = prepared.reference.sizeBytes,
                assetUploadedAt = prepared.reference.uploadedAt,
            )
            prepared.reference
        }

    suspend fun uploadCoverImage(bookId: Long, passphrase: CharArray, store: CloudAssetStore): CloudAssetReference =
        withContext(dispatchers.io) {
            require(passphrase.isNotEmpty()) { "Cloud asset passphrase is required" }
            val prepared = prepareCoverImage(bookId, passphrase)
            store.put(prepared.path, prepared.encryptedBytes)
            bookRepository.markCoverAssetUploaded(
                id = bookId,
                assetId = prepared.reference.id,
                assetSha256 = prepared.reference.sha256,
                assetSizeBytes = prepared.reference.sizeBytes,
                assetUploadedAt = prepared.reference.uploadedAt,
            )
            prepared.reference
        }

    suspend fun downloadBookFile(
        bookId: Long,
        reference: CloudAssetReference,
        extension: String,
        passphrase: CharArray,
        store: CloudAssetStore,
    ): StagedCloudAsset = withContext(dispatchers.io) {
        require(passphrase.isNotEmpty()) { "Cloud asset passphrase is required" }
        val encryptedBytes = store.get(CloudAssetLayout.pathFor(reference.id))
        val plaintext = cipher.decrypt(encryptedBytes, passphrase, reference.aad())
        stager.stageDownloadedBook(bookId, reference, plaintext, extension)
    }

    suspend fun downloadCoverImage(
        bookId: Long,
        reference: CloudAssetReference,
        passphrase: CharArray,
        store: CloudAssetStore,
    ): StagedCloudAsset = withContext(dispatchers.io) {
        require(passphrase.isNotEmpty()) { "Cloud asset passphrase is required" }
        val encryptedBytes = store.get(CloudAssetLayout.pathFor(reference.id))
        val plaintext = cipher.decrypt(encryptedBytes, passphrase, reference.aad())
        stager.stageDownloadedCover(bookId, reference, plaintext)
    }

    suspend fun prepareBookFile(bookId: Long, passphrase: CharArray): PreparedCloudAsset = withContext(dispatchers.io) {
        require(passphrase.isNotEmpty()) { "Cloud asset passphrase is required" }
        val book = bookRepository.getById(bookId) ?: error("Book not found")
        check(book.format != BookFormat.PHYSICAL) { "Physical books do not have downloadable assets" }
        check(book.fileAvailability == BookFileAvailability.LOCAL) { "Only local book files can be uploaded" }
        check(book.filePath.isNotBlank()) { "Book file path is empty" }
        val file = storageRoots.resolve(book.filePath)
        check(file.isFile) { "Book file is missing from this device" }
        check(file.length() <= MaxBookAssetBytes) { "Book file is too large for GitHub asset sync" }
        val plaintext = file.readBytes()
        val plaintextSha256 = Hashing.sha256(plaintext)
        val reference = CloudAssetReference(
            id = UUID.randomUUID().toString().replace("-", ""),
            sha256 = plaintextSha256,
            sizeBytes = plaintext.size.toLong(),
            uploadedAt = System.currentTimeMillis(),
        )
        PreparedCloudAsset(
            reference = reference,
            path = CloudAssetLayout.pathFor(reference.id),
            encryptedBytes = cipher.encrypt(plaintext, passphrase, reference.aad()),
        )
    }

    suspend fun prepareCoverImage(bookId: Long, passphrase: CharArray): PreparedCloudAsset = withContext(dispatchers.io) {
        require(passphrase.isNotEmpty()) { "Cloud asset passphrase is required" }
        val book = bookRepository.getById(bookId) ?: error("Book not found")
        val coverPath = book.coverPath?.takeIf { it.isNotBlank() } ?: error("Book cover path is empty")
        val file = storageRoots.resolve(coverPath)
        check(file.isFile) { "Book cover is missing from this device" }
        check(file.length() <= MaxCoverAssetBytes) { "Book cover is too large for GitHub asset sync" }
        val plaintext = file.readBytes()
        val plaintextSha256 = Hashing.sha256(plaintext)
        val reference = CloudAssetReference(
            id = UUID.randomUUID().toString().replace("-", ""),
            sha256 = plaintextSha256,
            sizeBytes = plaintext.size.toLong(),
            uploadedAt = System.currentTimeMillis(),
        )
        PreparedCloudAsset(
            reference = reference,
            path = CloudAssetLayout.pathFor(reference.id),
            encryptedBytes = cipher.encrypt(plaintext, passphrase, reference.aad()),
        )
    }
}

private fun CloudAssetReference.aad(): ByteArray =
    "vayana.asset.v1:$id:$sha256:$sizeBytes".toByteArray(Charsets.UTF_8)

private const val MaxBookAssetBytes = 60L * 1024L * 1024L
private const val MaxCoverAssetBytes = 5L * 1024L * 1024L
