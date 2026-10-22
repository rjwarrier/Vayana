package com.vayana.feature.library

import android.content.Context
import androidx.work.*
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.filesystem.StorageRoots
import com.vayana.core.resources.R
import com.vayana.core.resources.LocalizedException
import com.vayana.core.sync.asset.CloudBookAssetTransfer
import com.vayana.core.sync.asset.GitHubAssetStoreException
import com.vayana.core.resources.UiText
import com.vayana.core.sync.github.assetStore
import com.vayana.core.sync.github.gitHubSyncConfig
import com.vayana.format.ocr.BookRecognition
import com.vayana.format.ocr.OcrModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore

internal enum class PreparationState { WAITING, DOWNLOADING, RECOGNIZING, READY, FAILED, CANCELLED }

/** WorkManager persists one request per book. Inputs contain identity/options, never sync credentials. */
@Singleton
class OfflinePreparation @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val books: BookRepository,
    private val settings: SettingsRepository,
    private val roots: StorageRoots,
    private val transfer: CloudBookAssetTransfer,
) {
    // Each download currently buffers encrypted/plaintext bytes; keep the batch's peak bounded to one transfer.
    private val downloadSlot = Semaphore(1)
    private val manager get() = WorkManager.getInstance(context)
    val jobs get() = manager.getWorkInfosByTagFlow(PreparationTag)

    suspend fun enqueue(selected: List<Book>, model: OcrModel?, wifiOnly: Boolean) = withContext(Dispatchers.IO) {
        for (book in selected.filterNot { it.format.isOffline }) {
            val needsDownload = book.filePath.isBlank() || !roots.resolve(book.filePath).isFile
            manager.enqueueUniqueWork(workName(book.id), ExistingWorkPolicy.KEEP, request(book, model, wifiOnly, needsDownload)).await()
        }
    }

    suspend fun cancel(book: Book, hash: String = book.fileHash) {
        manager.cancelUniqueWork(workName(book.id)).await()
        BookRecognition.stop(context, hash)
    }

    suspend fun prepare(id: Long, syncId: String, hash: String, modelId: String?, allowDownload: Boolean = true): String? = withContext(Dispatchers.IO) {
        var book = currentBook(id, syncId, hash)
        if (book.format.isOffline) throw LocalizedException(R.string.reader_error_file_unavailable)
        val model = modelId?.let { OcrModel.byId(it) ?: throw LocalizedException(R.string.reader_error_file_unavailable) }
        if (!roots.resolve(book.filePath).isFile || book.filePath.isBlank()) {
            if (!allowDownload) throw LocalizedException(R.string.reader_error_file_missing)
            if (!downloadSlot.tryAcquire()) throw PreparationBusyException()
            try {
                val snapshot = settings.snapshot.first()
                if (!snapshot.githubSyncEnabled) throw LocalizedException(R.string.sync_detail_sync_off)
                val config = snapshot.gitHubSyncConfig() ?: throw LocalizedException(R.string.sync_detail_settings_incomplete)
                val reference = book.fileAssetReference() ?: throw LocalizedException(R.string.sync_detail_cloud_book_missing)
                val passphrase = config.passphrase.toCharArray()
                try {
                    transfer.downloadBookFile(book.id, reference, book.format.name.lowercase(), passphrase, config.assetStore()) {
                        currentCoroutineContext().ensureActive()
                        currentBook(id, syncId, hash)
                    }
                } finally { passphrase.fill('\u0000') }
                book = currentBook(id, syncId, hash)
            } finally { downloadSlot.release() }
        }
        currentCoroutineContext().ensureActive()
        if (model != null && book.format == BookFormat.PDF) {
            BookRecognition.start(context, roots.resolve(book.filePath), hash, model, 0, title = book.title)
            return@withContext hash
        }
        null
    }

    private suspend fun currentBook(id: Long, syncId: String, hash: String): Book =
        books.findActiveBySyncIdOrHash(syncId, hash)?.takeIf { it.id == id && it.syncId == syncId && it.fileHash == hash }
            ?: throw LocalizedException(R.string.reader_error_file_unavailable)

    companion object {
        internal const val PreparationTag = "vayana-offline-preparation"
        internal const val BookTagPrefix = "prepare-book:"
        internal const val ModelTagPrefix = "prepare-model:"
        internal const val SyncTagPrefix = "prepare-sync:"
        internal const val KeyId = "bookId"
        internal const val KeySync = "syncId"
        internal const val KeyHash = "hash"
        internal const val KeyModel = "model"
        internal const val KeyOcrHash = "ocrHash"
        internal const val KeyError = "errorResource"
        internal const val KeyDownload = "allowDownload"
        private fun workName(id: Long) = "vayana-prepare-$id"

        internal fun request(book: Book, model: OcrModel?, wifiOnly: Boolean, needsDownload: Boolean = true): OneTimeWorkRequest {
            val ocr = model?.takeIf { book.format == BookFormat.PDF }
            return OneTimeWorkRequestBuilder<OfflinePreparationWorker>()
                .keepResultsForAtLeast(30, TimeUnit.DAYS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf(KeyId to book.id, KeySync to book.syncId, KeyHash to book.fileHash, KeyModel to ocr?.id, KeyDownload to needsDownload))
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true)
                    .setRequiredNetworkType(if (!needsDownload) NetworkType.NOT_REQUIRED else if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                .addTag(PreparationTag).addTag("$BookTagPrefix${book.id}").addTag("$SyncTagPrefix${book.syncId}")
                .apply { if (ocr != null) {
                    addTag("$ModelTagPrefix${ocr.id}")
                    addTag(BookRecognition.modelTag(ocr))
                    addTag(BookRecognition.PreparationTag)
                    addTag(BookRecognition.preparationTag(book.fileHash))
                } }.build()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OfflinePreparationEntryPoint { fun offlinePreparation(): OfflinePreparation }

class OfflinePreparationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val preparer = EntryPointAccessors.fromApplication(applicationContext, OfflinePreparationEntryPoint::class.java).offlinePreparation()
        return try {
            val hash = preparer.prepare(inputData.getLong(OfflinePreparation.KeyId, -1),
                inputData.getString(OfflinePreparation.KeySync).orEmpty(), inputData.getString(OfflinePreparation.KeyHash).orEmpty(),
                inputData.getString(OfflinePreparation.KeyModel), inputData.getBoolean(OfflinePreparation.KeyDownload, true))
            Result.success(workDataOf(OfflinePreparation.KeyOcrHash to hash))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: PreparationBusyException) { Result.retry() }
        catch (error: GitHubAssetStoreException) {
            if (error.statusCode == 429 || error.statusCode >= 500) Result.retry()
            else Result.failure(workDataOf(OfflinePreparation.KeyError to if (error.statusCode == 404) "missingAsset" else "failed"))
        }
        catch (_: IOException) { Result.retry() }
        catch (error: LocalizedException) {
            val code = when ((error.text as? UiText.Res)?.id) {
                R.string.sync_detail_sync_off -> "syncOff"
                R.string.sync_detail_settings_incomplete -> "config"
                R.string.sync_detail_cloud_book_missing -> "missingAsset"
                R.string.reader_error_file_missing, R.string.reader_error_file_unavailable -> "missingFile"
                else -> "failed"
            }
            Result.failure(workDataOf(OfflinePreparation.KeyError to code))
        } catch (_: Exception) { Result.failure() }
    }
}

internal class PreparationBusyException : Exception()

internal fun preparationState(job: WorkInfo, recognition: List<WorkInfo>, legacy: WorkInfo? = null): PreparationState = when (job.state) {
    WorkInfo.State.CANCELLED -> PreparationState.CANCELLED
    WorkInfo.State.FAILED -> PreparationState.FAILED
    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> PreparationState.WAITING
    WorkInfo.State.RUNNING -> PreparationState.DOWNLOADING
    WorkInfo.State.SUCCEEDED -> {
        val hash = job.outputData.getString(OfflinePreparation.KeyOcrHash)
        if (hash == null) PreparationState.READY else {
            val ocr = recognition.lastOrNull { BookRecognition.bookTag(hash) in it.tags } ?: legacy
            when (ocr?.state) {
                WorkInfo.State.SUCCEEDED -> PreparationState.READY
                WorkInfo.State.FAILED -> PreparationState.FAILED
                WorkInfo.State.CANCELLED -> PreparationState.CANCELLED
                WorkInfo.State.RUNNING -> PreparationState.RECOGNIZING
                else -> PreparationState.WAITING
            }
        }
    }
}
