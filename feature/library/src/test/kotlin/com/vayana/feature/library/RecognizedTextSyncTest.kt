package com.vayana.feature.library

import com.vayana.core.sync.asset.CloudAssetReference
import com.vayana.core.sync.asset.GitHubAssetStoreException
import com.vayana.core.sync.asset.GitHubContentsAssetStore
import com.vayana.core.sync.asset.GitHubRepository
import com.vayana.core.sync.asset.GitHubHttpClient
import com.vayana.core.sync.asset.GitHubHttpRequest
import com.vayana.core.sync.asset.GitHubHttpResponse
import com.vayana.format.ocr.*
import java.nio.file.Files
import java.io.ByteArrayOutputStream
import java.util.Base64
import org.json.JSONObject
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class RecognizedTextSyncTest {
    private val book = "b".repeat(64)
    private val secret = "sync-test-passphrase".toCharArray()

    private class Local : RecognizedTextLocal, AutoCloseable {
        val root = Files.createTempDirectory("recognized-sync-test").toFile()
        val store = OcrStore(root)
        override suspend fun capture(bookHash: String) = OcrSyncArchives.capture(store, bookHash)
        override suspend fun restore(bookHash: String, archive: OcrSyncArchive, expectedRevision: String?) {
            check(capture(bookHash)?.revision == expectedRevision)
            OcrSyncArchives.restore(store, bookHash, archive)
        }
        fun recognize(book: String, text: String, time: Long) {
            store.recordPageCount(book, 3)
            repeat(3) { page -> store.save(book, page, RecognizedPage(listOf(listOf(RecognizedWord("$text $page", 0f, 0f, 80f, 12f, 95f)))), "eng") }
            store.saveLineJoins(book)
            store.buildTextEpub(book, "Recognized book", null, "en")
            root.resolve(book).listFiles()!!.forEach { it.setLastModified(time) }
        }
        override fun close() { root.deleteRecursively() }
    }

    private class Remote : RecognizedTextRemote {
        var document: RemoteRecognizedText? = null
        val assets = mutableMapOf<String, ByteArray>()
        var downloads = 0
        var uploads = 0
        var conflict = false
        var temporaryConflicts = 0
        var refreshes = 0
        override suspend fun refresh() { refreshes++ }
        override suspend fun read(bookHash: String) = document
        override suspend fun download(reference: CloudAssetReference): ByteArray { downloads++; return assets.getValue(reference.id) }
        override suspend fun upload(reference: CloudAssetReference, encrypted: ByteArray) { uploads++; assets[reference.id] = encrypted }
        override suspend fun publish(bookHash: String, asset: RemoteRecognizedText, expectedSha: String?) {
            if (temporaryConflicts-- > 0) throw GitHubAssetStoreException("Concurrent update", 409, "")
            check(!conflict && document?.documentSha == expectedSha) { "Concurrent update" }
            document = asset.copy(documentSha = (uploads + 1).toString().padStart(40, '0'))
        }
    }

    @Test fun completedRecognitionTravelsEncryptedAndDoesNotUploadAgain() = runBlocking {
        Local().use { source -> Local().use { other ->
            source.recognize(book, "Readable Malayalam മലയാളം", 1_700_000_000_000)
            val remote = Remote()
            assertEquals(RecognizedTextSyncResult.UPLOADED, syncRecognizedText(book, secret, source, remote))
            assertFalse(remote.assets.values.single().toString(Charsets.UTF_8).contains("Readable"))
            assertEquals(RecognizedTextSyncResult.DOWNLOADED, syncRecognizedText(book, secret, other, remote))
            assertEquals(source.capture(book)!!.revision, other.capture(book)!!.revision)
            assertEquals(source.store.read(book, 1), other.store.read(book, 1))
            assertEquals(source.store.textPageMap(book), other.store.textPageMap(book))
            assertContentEquals(source.store.textEpub(book)!!.readBytes(), other.store.textEpub(book)!!.readBytes())
            assertEquals(RecognizedTextSyncResult.UNCHANGED, syncRecognizedText(book, secret, other, remote))
            assertEquals(RecognizedTextSyncResult.UNCHANGED, syncRecognizedText(book, secret, source, remote))
            assertEquals(1, remote.uploads)
            assertEquals(1, remote.downloads)
        } }
    }

    @Test fun newerCompletedRecognitionWinsAndOlderDeviceCannotReplaceIt() = runBlocking {
        Local().use { old -> Local().use { newer ->
            old.recognize(book, "Old text", 1_700_000_000_000)
            newer.recognize(book, "Improved text", 1_700_000_100_000)
            val remote = Remote()
            syncRecognizedText(book, secret, old, remote)
            assertEquals(RecognizedTextSyncResult.UPLOADED, syncRecognizedText(book, secret, newer, remote))
            assertEquals(RecognizedTextSyncResult.DOWNLOADED, syncRecognizedText(book, secret, old, remote))
            assertEquals(newer.capture(book)!!.revision, old.capture(book)!!.revision)
        } }
    }

    @Test fun badPassphraseDoesNotReplaceLocalRecognizedText() = runBlocking {
        Local().use { source -> Local().use { target ->
            source.recognize(book, "New text", 1_700_000_100_000)
            target.recognize(book, "Old text", 1_700_000_000_000)
            val remote = Remote()
            syncRecognizedText(book, secret, source, remote)
            val original = target.capture(book)!!.revision
            assertFailsWith<Exception> { syncRecognizedText(book, "wrong".toCharArray(), target, remote) }
            assertEquals(original, target.capture(book)!!.revision)
        } }
    }

    @Test fun incompleteRecognitionIsNotPublishedAndConcurrentPublishFailsSafely(): Unit = runBlocking {
        Local().use { source ->
            val remote = Remote()
            source.store.save(book, 0, RecognizedPage(emptyList()), "eng")
            assertEquals(RecognizedTextSyncResult.UNCHANGED, syncRecognizedText(book, secret, source, remote))
            assertEquals(0, remote.uploads)
            source.recognize(book, "Complete text", 1_700_000_000_000)
            remote.conflict = true
            assertFailsWith<IllegalStateException> { syncRecognizedText(book, secret, source, remote) }
            assertNull(remote.document)
            assertNotNull(source.store.textEpub(book))
        }
    }

    @Test fun conflictsRetryWithFreshReferencesAndStopAfterThreeAttempts(): Unit = runBlocking {
        Local().use { source ->
            source.recognize(book, "Complete text", 1_700_000_000_000)
            val remote = Remote().apply { temporaryConflicts = 1 }
            assertEquals(RecognizedTextSyncResult.UPLOADED, syncRecognizedTextWithRetry(book, secret, source, remote))
            assertEquals(1, remote.refreshes)
            val forever = Remote().apply { temporaryConflicts = 9 }
            assertFailsWith<GitHubAssetStoreException> { syncRecognizedTextWithRetry(book, secret, source, forever) }
            assertEquals(3, forever.uploads)
            assertEquals(2, forever.refreshes)
            assertNull(forever.document)
        }
    }

    @Test fun equalTimestampsConvergeWithoutRepeatedTransfers() = runBlocking {
        Local().use { first -> Local().use { second ->
            first.recognize(book, "First interpretation", 1_700_000_000_000)
            second.recognize(book, "Second interpretation", 1_700_000_000_000)
            val remote = Remote()
            syncRecognizedText(book, secret, first, remote)
            syncRecognizedText(book, secret, second, remote)
            syncRecognizedText(book, secret, first, remote)
            assertEquals(first.capture(book)!!.revision, second.capture(book)!!.revision)
            assertEquals(RecognizedTextSyncResult.UNCHANGED, syncRecognizedText(book, secret, first, remote))
            assertEquals(RecognizedTextSyncResult.UNCHANGED, syncRecognizedText(book, secret, second, remote))
        } }
    }

    @Test fun manyPdfLookupsShareOneDirectoryRequest() = runBlocking {
        var requests = 0
        val client = object : GitHubHttpClient {
            override fun execute(request: GitHubHttpRequest): GitHubHttpResponse {
                requests++
                assertTrue(request.url.contains("/contents/vayana/recognized-text"))
                return GitHubHttpResponse(200, "[]".toByteArray())
            }
        }
        val store = GitHubContentsAssetStore(GitHubRepository("owner", "ocr-sync-test", "main", "https://api.github.test"),
            "test-token", "test", "test@example.test", client)
        val remote = GitHubRecognizedTextRemote(store)
        repeat(20) { assertNull(remote.read(it.toString(16).padStart(64, '0'))) }
        assertEquals(1, requests)
    }

    @Test fun encryptedUploadsStreamBase64AndCleanTemporaryFiles(): Unit = runBlocking {
        val cache = Files.createTempDirectory("ocr-upload-test").toFile()
        val encrypted = ByteArray(32_001) { (it % 251).toByte() }
        var puts = 0
        val client = object : GitHubHttpClient {
            override fun execute(request: GitHubHttpRequest): GitHubHttpResponse {
                assertEquals("PUT", request.method)
                assertNull(request.body)
                val streaming = assertNotNull(request.streamingBody)
                val body = ByteArrayOutputStream().also(streaming.writeTo).toByteArray()
                assertEquals(streaming.contentLength, body.size.toLong())
                assertContentEquals(encrypted, Base64.getDecoder().decode(JSONObject(body.toString(Charsets.UTF_8)).getString("content")))
                puts++
                return GitHubHttpResponse(201, "{}".toByteArray())
            }
        }
        val store = GitHubContentsAssetStore(GitHubRepository("owner", "ocr-stream-test", "main", "https://api.github.test"),
            "test-token", "test", "test@example.test", client)
        try {
            GitHubRecognizedTextRemote(store, cache).upload(CloudAssetReference("ocrtestasset00000001", "b".repeat(64), 100, 1), encrypted)
            assertEquals(1, puts)
            assertTrue(cache.listFiles().isNullOrEmpty())
        } finally { cache.deleteRecursively() }
    }
}
