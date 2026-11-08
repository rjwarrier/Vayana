package com.vayana.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.SystemClock
import androidx.work.*
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.sync.SyncOperationCoordinator
import com.vayana.core.sync.github.assetStore
import com.vayana.core.sync.github.gitHubSyncConfig
import com.vayana.feature.library.RecognizedTextSync
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/** Shell-only, debug-only targeted OCR sync and isolated cloud download verification. */
class OcrSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hash = intent.getStringExtra("bookHash")?.takeIf { it.matches(Regex("[0-9a-f]{64}")) } ?: return
        statusFile(context).writeText(JSONObject().put("state", "queued").toString())
        WorkManager.getInstance(context).enqueueUniqueWork("debug-ocr-sync-$hash", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<OcrSyncWorker>().setInputData(workDataOf("bookHash" to hash))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OcrSyncEntryPoint {
    fun books(): BookRepository
    fun settings(): SettingsRepository
    fun recognizedTextSync(): RecognizedTextSync
    fun coordinator(): SyncOperationCoordinator
}

class OcrSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val status = statusFile(applicationContext)
        status.writeText(JSONObject().put("state", "running").toString())
        return try {
            val entry = EntryPointAccessors.fromApplication(applicationContext, OcrSyncEntryPoint::class.java)
            entry.coordinator().run {
                val hash = requireNotNull(inputData.getString("bookHash"))
                val book = entry.books().observeAll().first().first { it.fileHash == hash }
                val settings = entry.settings().snapshot.first()
                check(settings.githubSyncEnabled) { "Sync is disabled" }
                val config = requireNotNull(settings.gitHubSyncConfig()) { "Sync configuration is incomplete" }
                val passphrase = config.passphrase.toCharArray()
                try {
                    val store = config.assetStore()
                    val summary = entry.recognizedTextSync().sync(listOf(book), passphrase, store)
                    summary.failure?.let { throw it }
                    val pages = entry.recognizedTextSync().verifyRemote(hash, passphrase, store)
                    val repeatStarted = SystemClock.elapsedRealtime()
                    val beforeRepeat = store.transferStats()
                    val repeat = entry.recognizedTextSync().sync(listOf(book), passphrase, store)
                    repeat.failure?.let { throw it }
                    check(repeat.uploaded == 0 && repeat.downloaded == 0 && repeat.busy == 0)
                    val repeatMs = SystemClock.elapsedRealtime() - repeatStarted
                    val afterRepeat = store.transferStats()
                    status.writeText(JSONObject().put("state", "complete").put("uploaded", summary.uploaded)
                        .put("downloaded", summary.downloaded).put("verifiedPages", pages)
                        .put("repeatMs", repeatMs).put("repeatRequests", afterRepeat.requests - beforeRepeat.requests)
                        .put("repeatUpBytes", afterRepeat.upBytes - beforeRepeat.upBytes)
                        .put("repeatDownBytes", afterRepeat.downBytes - beforeRepeat.downBytes).toString())
                    Log.i("OcrSync", "Cloud round trip verified: uploaded=${summary.uploaded}, downloaded=${summary.downloaded}, pages=$pages")
                } finally { passphrase.fill('\u0000') }
            }
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            status.writeText(JSONObject().put("state", "failed").put("error", "${error.javaClass.simpleName}: ${error.message}").toString())
            Log.e("OcrSync", "Targeted sync failed", error)
            Result.failure()
        }
    }
}

private fun statusFile(context: Context) = File(context.filesDir, "diagnostics/ocr-sync-status.json").also { it.parentFile?.mkdirs() }
