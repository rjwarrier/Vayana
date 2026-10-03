package com.vayana.feature.library

import androidx.room.Room
import com.vayana.core.common.DefaultDispatcherProvider
import com.vayana.core.common.Hashing
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.repository.BookRepositoryImpl
import com.vayana.core.filesystem.StorageRoots
import com.vayana.core.sync.asset.CloudAssetCipher
import com.vayana.core.sync.asset.CloudAssetStager
import com.vayana.core.sync.asset.CloudAssetStore
import com.vayana.core.sync.asset.CloudBookAssetTransfer
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CloudUploadFileTest {
    @Test
    fun fileUploadCleansTemporaryFilesAndOnlyMarksSuccessfulUploads() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, VayanaDatabase::class.java).allowMainThreadQueries().build()
        val roots = StorageRoots(context)
        val source = File.createTempFile("upload-book", ".epub", roots.booksDir)
        try {
            val plaintext = ByteArray(16_385) { (it % 251).toByte() }
            source.writeBytes(plaintext)
            val repo = BookRepositoryImpl(db, db.bookDao(), db.bookAliasDao(), db.tombstoneDao(), db.readingSessionDao(), db.vocabularyCardDao(), db.pendingCloudDeletionDao())
            val book = assertNotNull(repo.insertIfNew("Book", null, null, null, null, coverPath = null,
                filePath = roots.relativize(source), format = BookFormat.EPUB, fileHash = Hashing.sha256(plaintext)))
            val dispatchers = DefaultDispatcherProvider()
            val transfer = CloudBookAssetTransfer(repo, roots, CloudAssetStager(repo, roots, dispatchers), dispatchers, context)
            var uploadedFile: File? = null
            var encrypted = byteArrayOf()
            var fail = true
            val store = object : CloudAssetStore {
                override suspend fun put(path: String, bytes: ByteArray) = error("Must use file upload")
                override suspend fun get(path: String): ByteArray = error("Not used")
                override suspend fun delete(path: String) = error("Not used")
                override suspend fun putFile(path: String, file: File) {
                    uploadedFile = file
                    assertTrue(file.isFile)
                    if (fail) throw IOException("Upload failed")
                    encrypted = file.readBytes()
                }
            }
            assertFailsWith<IOException> { transfer.uploadBookFile(book.id, "secret".toCharArray(), store) }
            assertFalse(assertNotNull(uploadedFile).exists())
            assertNull(repo.getById(book.id)?.fileAssetId)
            fail = false
            val reference = transfer.uploadBookFile(book.id, "secret".toCharArray(), store)
            assertFalse(assertNotNull(uploadedFile).exists())
            assertEquals(reference.id, repo.getById(book.id)?.fileAssetId)
            val aad = "vayana.asset.v1:${reference.id}:${reference.sha256}:${reference.sizeBytes}".toByteArray()
            assertContentEquals(plaintext, CloudAssetCipher().decrypt(encrypted, "secret".toCharArray(), aad))
        } finally {
            source.delete()
            db.close()
        }
    }
}
