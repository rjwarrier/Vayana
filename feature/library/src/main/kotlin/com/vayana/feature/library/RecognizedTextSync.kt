package com.vayana.feature.library

import android.content.Context
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.common.Hashing
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.filesystem.StorageRoots
import com.vayana.core.sync.asset.CloudAssetCipher
import com.vayana.core.sync.asset.CloudAssetLayout
import com.vayana.core.sync.asset.CloudAssetReference
import com.vayana.core.sync.asset.GitHubAssetStoreException
import com.vayana.core.sync.asset.GitHubContentsAssetStore
import com.vayana.format.ocr.BookRecognition
import com.vayana.format.ocr.OcrSyncArchive
import com.vayana.format.ocr.OcrStore
import com.vayana.format.ocr.OcrSyncArchives
import com.vayana.format.ocr.RecognitionBusyException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class RemoteRecognizedText(
    val reference: CloudAssetReference,
    val revision: String,
    val updatedAt: Long,
    val documentSha: String,
)

internal interface RecognizedTextRemote {
    suspend fun refresh() = Unit
    suspend fun read(bookHash: String): RemoteRecognizedText?
    suspend fun download(reference: CloudAssetReference): ByteArray
    suspend fun upload(reference: CloudAssetReference, encrypted: ByteArray)
    suspend fun publish(bookHash: String, asset: RemoteRecognizedText, expectedSha: String?)
}

internal interface RecognizedTextLocal {
    suspend fun capture(bookHash: String): OcrSyncArchive?
    suspend fun restore(bookHash: String, archive: OcrSyncArchive, expectedRevision: String?)
}

internal enum class RecognizedTextSyncResult { UNCHANGED, UPLOADED, DOWNLOADED }

internal suspend fun syncRecognizedTextWithRetry(
    bookHash: String, passphrase: CharArray, local: RecognizedTextLocal, remote: RecognizedTextRemote,
): RecognizedTextSyncResult {
    repeat(3) { attempt ->
        try { return syncRecognizedText(bookHash, passphrase, local, remote) }
        catch (conflict: GitHubAssetStoreException) {
            if (conflict.statusCode != 409 || attempt == 2) throw conflict
            remote.refresh()
        }
    }
    error("Unreachable")
}

/** Kept separate from snapshots so older clients cannot erase OCR references on their next sync. */
internal suspend fun syncRecognizedText(
    bookHash: String, passphrase: CharArray, local: RecognizedTextLocal, remote: RecognizedTextRemote,
): RecognizedTextSyncResult {
    require(bookHash.matches(Regex("[0-9a-f]{64}")) && passphrase.isNotEmpty())
    val captured = local.capture(bookHash)
    val cloud = remote.read(bookHash)
    if (captured?.revision == cloud?.revision) return RecognizedTextSyncResult.UNCHANGED
    val localWins = captured != null && (cloud == null || captured.updatedAt > cloud.updatedAt ||
        (captured.updatedAt == cloud.updatedAt && captured.revision > cloud.revision))
    val cipher = CloudAssetCipher()
    if (localWins) {
        checkNotNull(captured)
        val reference = CloudAssetReference(UUID.randomUUID().toString().replace("-", ""),
            captured.sha256, captured.bytes.size.toLong(), System.currentTimeMillis())
        remote.upload(reference, cipher.encrypt(captured.bytes, passphrase, reference.ocrAad(bookHash)))
        // The reference is published only after the encrypted bytes exist; concurrent publishers cannot overwrite it.
        remote.publish(bookHash, RemoteRecognizedText(reference, captured.revision, captured.updatedAt, ""), cloud?.documentSha)
        return RecognizedTextSyncResult.UPLOADED
    }
    checkNotNull(cloud)
    val encrypted = remote.download(cloud.reference)
    require(encrypted.size <= MaxOcrAssetBytes + 1024) { "Recognized text download is too large" }
    val bytes = cipher.decrypt(encrypted, passphrase, cloud.reference.ocrAad(bookHash))
    require(bytes.size.toLong() == cloud.reference.sizeBytes && Hashing.sha256(bytes) == cloud.reference.sha256) {
        "Recognized text download did not match its reference"
    }
    local.restore(bookHash, OcrSyncArchive(bytes, cloud.reference.sha256, cloud.updatedAt, cloud.revision), captured?.revision)
    return RecognizedTextSyncResult.DOWNLOADED
}

private fun CloudAssetReference.ocrAad(bookHash: String) =
    "vayana.ocr.v1:$bookHash:$id:$sha256:$sizeBytes".toByteArray()

internal class GitHubRecognizedTextRemote(private val store: GitHubContentsAssetStore, private val cacheDir: File? = null) : RecognizedTextRemote {
    private var references: Map<String, String>? = null
    override suspend fun refresh() { references = null }
    override suspend fun read(bookHash: String): RemoteRecognizedText? {
        val listing = references ?: store.listSyncDocumentShas("vayana/recognized-text").also { references = it }
        val sha = listing[path(bookHash)] ?: return null
        val document = store.getSyncDocumentWithKnownSha(path(bookHash), sha, maxBytes = 16 * 1024)
        val json = JSONObject(document.bytes.toString(Charsets.UTF_8))
        require(json.getInt("version") == 1 && json.getString("bookHash") == bookHash)
        val revision = json.getString("revision")
        require(revision.matches(Regex("[0-9a-f]{64}")))
        val asset = json.getJSONObject("asset")
        val reference = CloudAssetReference(asset.getString("id"), asset.getString("sha256"), asset.getLong("sizeBytes"), asset.getLong("uploadedAt"))
        require(reference.sizeBytes in 1..MaxOcrAssetBytes)
        val updatedAt = json.getLong("updatedAt")
        require(updatedAt > 0)
        return RemoteRecognizedText(reference, revision, updatedAt, document.sha)
    }
    override suspend fun download(reference: CloudAssetReference) = store.get(CloudAssetLayout.pathFor(reference.id), (reference.sizeBytes + 1024).toInt())
    override suspend fun upload(reference: CloudAssetReference, encrypted: ByteArray) {
        val file = File.createTempFile("ocr-upload-", ".bin", requireNotNull(cacheDir))
        try {
            file.writeBytes(encrypted)
            store.putFile(CloudAssetLayout.pathFor(reference.id), file)
        } finally { file.delete() }
    }
    override suspend fun publish(bookHash: String, asset: RemoteRecognizedText, expectedSha: String?) {
        val ref = asset.reference
        val json = JSONObject().put("version", 1).put("bookHash", bookHash).put("revision", asset.revision).put("updatedAt", asset.updatedAt)
            .put("asset", JSONObject().put("id", ref.id).put("sha256", ref.sha256).put("sizeBytes", ref.sizeBytes).put("uploadedAt", ref.uploadedAt))
        store.putSyncDocumentIfUnchanged(path(bookHash), json.toString().toByteArray(), expectedSha)
    }
    private fun path(bookHash: String): String {
        require(bookHash.matches(Regex("[0-9a-f]{64}")))
        return "vayana/recognized-text/$bookHash.json"
    }
}

data class RecognizedTextSyncSummary(val uploaded: Int = 0, val downloaded: Int = 0, val failed: Int = 0, val failure: Throwable? = null, val busy: Int = 0)

@Singleton
class RecognizedTextSync @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val roots: StorageRoots,
) {
    /** Debug/diagnostics round trip in an isolated cache; never replaces the library's local OCR. */
    suspend fun verifyRemote(bookHash: String, passphrase: CharArray, store: GitHubContentsAssetStore): Int = withContext(dispatchers.io) {
        val root = File(context.cacheDir, "ocr-sync-check-${UUID.randomUUID()}")
        val ocr = OcrStore(root)
        val coroutine = currentCoroutineContext()
        try {
            val local = object : RecognizedTextLocal {
                override suspend fun capture(bookHash: String): OcrSyncArchive? = null
                override suspend fun restore(bookHash: String, archive: OcrSyncArchive, expectedRevision: String?) {
                    OcrSyncArchives.restore(ocr, bookHash, archive) { coroutine.ensureActive() }
                }
            }
            check(syncRecognizedText(bookHash, passphrase, local, GitHubRecognizedTextRemote(store, context.cacheDir)) == RecognizedTextSyncResult.DOWNLOADED)
            check(ocr.searchPages(bookHash).any()) { "Downloaded OCR has no searchable text" }
            ocr.savedPages(bookHash).size
        } finally { root.deleteRecursively() }
    }

    suspend fun sync(books: List<Book>, passphrase: CharArray, store: GitHubContentsAssetStore): RecognizedTextSyncSummary = withContext(dispatchers.io) {
        val coroutine = currentCoroutineContext()
        val sourceFiles = books.associate { it.fileHash to roots.resolve(it.filePath) }
        val local = object : RecognizedTextLocal {
            override suspend fun capture(bookHash: String) = BookRecognition.captureForSync(context, bookHash, sourceFiles[bookHash]) { coroutine.ensureActive() }
            override suspend fun restore(bookHash: String, archive: OcrSyncArchive, expectedRevision: String?) =
                BookRecognition.restoreFromSync(context, bookHash, archive, expectedRevision) { coroutine.ensureActive() }
        }
        val remote = GitHubRecognizedTextRemote(store, context.cacheDir)
        var uploaded = 0
        var downloaded = 0
        var failed = 0
        var failure: Throwable? = null
        var busy = 0
        for (book in books.filter { it.format == BookFormat.PDF }.distinctBy { it.fileHash }) {
            coroutine.ensureActive()
            try {
                when (syncRecognizedTextWithRetry(book.fileHash, passphrase, local, remote)) {
                    RecognizedTextSyncResult.UPLOADED -> uploaded++
                    RecognizedTextSyncResult.DOWNLOADED -> downloaded++
                    else -> Unit
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: RecognitionBusyException) { busy++ }
            catch (error: Exception) {
                failed++; failure = error
                if (error is GitHubAssetStoreException && error.statusCode in setOf(401, 403, 429)) break
            }
        }
        RecognizedTextSyncSummary(uploaded, downloaded, failed, failure, busy)
    }
}

private const val MaxOcrAssetBytes = 32L * 1024 * 1024
