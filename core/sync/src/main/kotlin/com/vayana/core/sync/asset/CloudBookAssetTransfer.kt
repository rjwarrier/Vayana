package com.vayana.core.sync.asset

import com.vayana.core.common.DispatcherProvider
import com.vayana.core.common.Hashing
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.filesystem.StorageRoots
import java.io.File
import java.io.Closeable
import java.security.DigestInputStream
import java.security.MessageDigest
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

data class PreparedCloudAsset(
    val reference: CloudAssetReference,
    val path: String,
    val encryptedFile: File,
) : Closeable {
    override fun close() { encryptedFile.delete() }
}

enum class CloudBookFileDownloadPhase {
    ENCRYPTED_BYTES_DOWNLOADED,
    PLAINTEXT_DECRYPTED,
}

@Singleton
class CloudBookAssetTransfer @Inject constructor(
    private val bookRepository: BookRepository,
    private val storageRoots: StorageRoots,
    private val stager: CloudAssetStager,
    private val dispatchers: DispatcherProvider,
    @param:ApplicationContext private val context: Context,
) {
    private val cipher = CloudAssetCipher()

    suspend fun uploadBookFile(bookId: Long, passphrase: CharArray, store: CloudAssetStore): CloudAssetReference =
        withContext(dispatchers.io) {
            require(passphrase.isNotEmpty()) { "Cloud asset passphrase is required" }
            prepareBookFile(bookId, passphrase).use { prepared ->
                store.putFile(prepared.path, prepared.encryptedFile)
                bookRepository.markFileAssetUploaded(
                    id = bookId,
                    assetId = prepared.reference.id,
                    assetSha256 = prepared.reference.sha256,
                    assetSizeBytes = prepared.reference.sizeBytes,
                    assetUploadedAt = prepared.reference.uploadedAt,
                )
                prepared.reference
            }
        }

    suspend fun uploadCoverImage(bookId: Long, passphrase: CharArray, store: CloudAssetStore): CloudAssetReference =
        withContext(dispatchers.io) {
            require(passphrase.isNotEmpty()) { "Cloud asset passphrase is required" }
            prepareCoverImage(bookId, passphrase).use { prepared ->
                store.putFile(prepared.path, prepared.encryptedFile)
                bookRepository.markCoverAssetUploaded(
                    id = bookId,
                    assetId = prepared.reference.id,
                    assetSha256 = prepared.reference.sha256,
                    assetSizeBytes = prepared.reference.sizeBytes,
                    assetUploadedAt = prepared.reference.uploadedAt,
                )
                prepared.reference
            }
        }

    suspend fun downloadBookFile(
        bookId: Long,
        reference: CloudAssetReference,
        extension: String,
        passphrase: CharArray,
        store: CloudAssetStore,
        onProgress: suspend (CloudBookFileDownloadPhase) -> Unit = {},
    ): StagedCloudAsset = withContext(dispatchers.io) {
        require(passphrase.isNotEmpty()) { "Cloud asset passphrase is required" }
        val encryptedBytes = store.get(CloudAssetLayout.pathFor(reference.id))
        onProgress(CloudBookFileDownloadPhase.ENCRYPTED_BYTES_DOWNLOADED)
        val plaintext = cipher.decrypt(encryptedBytes, passphrase, reference.aad())
        onProgress(CloudBookFileDownloadPhase.PLAINTEXT_DECRYPTED)
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
        check(!book.format.isOffline) { "Books read outside the app do not have downloadable assets" }
        check(book.fileAvailability == BookFileAvailability.LOCAL) { "Only local book files can be uploaded" }
        check(book.filePath.isNotBlank()) { "Book file path is empty" }
        val file = storageRoots.resolve(book.filePath)
        check(file.isFile) { "Book file is missing from this device" }
        check(file.length() <= MaxBookAssetBytes) { "Book file is too large for GitHub asset sync" }
        prepareFile(file, passphrase)
    }

    suspend fun prepareCoverImage(bookId: Long, passphrase: CharArray): PreparedCloudAsset = withContext(dispatchers.io) {
        require(passphrase.isNotEmpty()) { "Cloud asset passphrase is required" }
        val book = bookRepository.getById(bookId) ?: error("Book not found")
        val coverPath = book.coverPath?.takeIf { it.isNotBlank() } ?: error("Book cover path is empty")
        val file = storageRoots.resolve(coverPath)
        check(file.isFile) { "Book cover is missing from this device" }
        check(file.length() <= MaxCoverAssetBytes) { "Book cover is too large for GitHub asset sync" }
        prepareFile(file, passphrase)
    }

    private fun prepareFile(file: File, passphrase: CharArray): PreparedCloudAsset {
        val size = file.length()
        val plaintextSha256 = Hashing.sha256(file.inputStream())
        val reference = CloudAssetReference(
            id = UUID.randomUUID().toString().replace("-", ""),
            sha256 = plaintextSha256,
            sizeBytes = size,
            uploadedAt = System.currentTimeMillis(),
        )
        val encrypted = File.createTempFile("vayana-upload-", ".bin", context.cacheDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(file.inputStream(), digest).use { input ->
                encrypted.outputStream().buffered().use { output ->
                    cipher.encrypt(input, output, passphrase, reference.aad())
                }
            }
            // A replacement between hashing and encryption must never produce an invalid asset reference.
            check(digest.digest().joinToString("") { "%02x".format(it) } == plaintextSha256 && file.length() == size) {
                "Book or cover changed while preparing its upload"
            }
            return PreparedCloudAsset(reference, CloudAssetLayout.pathFor(reference.id), encrypted)
        } catch (failure: Throwable) {
            encrypted.delete()
            throw failure
        }
    }
}

private fun CloudAssetReference.aad(): ByteArray =
    "vayana.asset.v1:$id:$sha256:$sizeBytes".toByteArray(Charsets.UTF_8)

private const val MaxBookAssetBytes = 60L * 1024L * 1024L
private const val MaxCoverAssetBytes = 5L * 1024L * 1024L
