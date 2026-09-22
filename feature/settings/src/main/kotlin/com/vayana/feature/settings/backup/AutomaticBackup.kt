package com.vayana.feature.settings.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.vayana.core.common.runCatchingCancellable
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AutomaticBackupFrequency(val intervalDays: Long) {
    DAILY(1),
    WEEKLY(7),
    EVERY_30_DAYS(30);

    companion object {
        fun fromStored(value: String?): AutomaticBackupFrequency =
            entries.firstOrNull { it.name == value } ?: DAILY
    }
}

data class AutomaticBackupState(
    val folderUri: String? = null,
    val folderName: String? = null,
    val keepCount: Int = 5,
    val frequency: AutomaticBackupFrequency = AutomaticBackupFrequency.DAILY,
    val lastSuccessAt: Long = 0L,
    val lastFileName: String? = null,
    val lastError: String? = null,
)

data class BackupFolderFile(
    val uri: Uri,
    val name: String,
    val modifiedAt: Long,
    val sizeBytes: Long,
)

/** Device-local schedule and SAF permission; these must not be restored onto another device. */
@Singleton
class AutomaticBackupSettings @Inject constructor(@ApplicationContext private val context: Context) {
    private val preferences = context.getSharedPreferences("automatic_backup", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<AutomaticBackupState> = _state

    fun chooseFolder(uri: Uri): Result<Unit> = runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val folder = requireNotNull(DocumentFile.fromTreeUri(context, uri)?.takeIf { it.isDirectory && it.canWrite() }) {
            "Choose a writable folder"
        }
        val editor = preferences.edit().putString("folder_uri", uri.toString()).putString("folder_name", folder.name)
            .remove("last_error")
        if (preferences.getString("folder_uri", null) != uri.toString()) {
            editor.remove("last_success_at").remove("last_file_name")
        }
        editor.apply()
        refresh()
        schedule()
    }

    fun setKeepCount(count: Int) {
        preferences.edit().putInt("keep_count", count.coerceIn(1, 10)).apply()
        refresh()
    }

    fun setFrequency(frequency: AutomaticBackupFrequency): Result<Unit> = runCatching {
        if (state.value.frequency == frequency) return@runCatching
        preferences.edit().putString("frequency", frequency.name).apply()
        refresh()
        if (state.value.folderUri != null) schedule()
    }

    fun disable() {
        WorkManager.getInstance(context).cancelUniqueWork(WorkName)
        preferences.edit().remove("folder_uri").remove("folder_name").remove("last_error").apply()
        refresh()
    }

    fun recordSuccess(fileName: String) {
        preferences.edit().putLong("last_success_at", System.currentTimeMillis())
            .putString("last_file_name", fileName).remove("last_error").apply()
        refresh()
    }

    fun recordError(message: String) {
        preferences.edit().putString("last_error", message).apply()
        refresh()
    }

    suspend fun listBackupFiles(): Result<List<BackupFolderFile>> = withContext(Dispatchers.IO) {
        val uri = state.value.folderUri?.let(Uri::parse) ?: return@withContext Result.success(emptyList())
        runCatchingCancellable {
            val folder = DocumentFile.fromTreeUri(context, uri)?.takeIf { it.isDirectory }
                ?: error("The selected backup folder is unavailable. Choose it again in Settings.")
            folder.listFiles().asSequence()
                .filter { it.isFile && isBackupFolderDisplayFile(it.name) }
                .mapNotNull { file ->
                    file.name?.let { name ->
                        BackupFolderFile(
                            uri = file.uri,
                            name = name,
                            modifiedAt = backupFileTimestamp(name, file.lastModified()),
                            sizeBytes = file.length(),
                        )
                    }
                }
                .sortedWith(compareByDescending<BackupFolderFile> { it.modifiedAt }.thenByDescending { it.name })
                .toList()
        }
    }

    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<AutomaticBackupWorker>(state.value.frequency.intervalDays, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WorkName, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun refresh() { _state.value = readState() }

    private fun readState() = AutomaticBackupState(
        folderUri = preferences.getString("folder_uri", null),
        folderName = preferences.getString("folder_name", null),
        keepCount = preferences.getInt("keep_count", 5).coerceIn(1, 10),
        frequency = AutomaticBackupFrequency.fromStored(preferences.getString("frequency", null)),
        lastSuccessAt = preferences.getLong("last_success_at", 0L),
        lastFileName = preferences.getString("last_file_name", null),
        lastError = preferences.getString("last_error", null),
    )

    companion object { private const val WorkName = "automatic-portable-backup" }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AutomaticBackupEntryPoint {
    fun backupManager(): BackupManager
    fun automaticBackupSettings(): AutomaticBackupSettings
}

class AutomaticBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, AutomaticBackupEntryPoint::class.java)
        val settings = dependencies.automaticBackupSettings()
        val state = settings.state.value
        val uri = state.folderUri?.let(Uri::parse) ?: return Result.success()
        return try {
            val folder = DocumentFile.fromTreeUri(applicationContext, uri)
                ?.takeIf { it.isDirectory && it.canWrite() }
                ?: error("The selected backup folder is unavailable. Choose it again in Settings.")
            folder.listFiles().filter { isAutomaticBackupPendingFile(it.name) }.forEach { it.delete() }
            val fileName = "vayana-auto-${System.currentTimeMillis()}.zip"
            val file = folder.createFile("application/octet-stream", "$fileName.pending")
                ?: error("Could not create a file in the backup folder")
            when (val outcome = dependencies.backupManager().createBackup(file.uri)) {
                BackupOutcome.Success -> {
                    if (!file.renameTo(fileName)) {
                        file.delete()
                        error("Could not finish the backup file in the selected folder")
                    }
                    val savedName = file.name ?: fileName
                    if (!isAutomaticBackupFile(savedName)) {
                        file.delete()
                        error("The selected folder changed the backup file name")
                    }
                    val oldFiles = folder.listFiles()
                        .filter { it.isFile && isAutomaticBackupFile(it.name) }
                        .filterNot { it.uri == file.uri }
                        .sortedWith(compareByDescending<DocumentFile> { it.name })
                        .drop(settings.state.value.keepCount - 1)
                    val pruningFailed = oldFiles.map { it.delete() }.any { !it }
                    settings.recordSuccess(savedName)
                    if (pruningFailed) {
                        settings.recordError("Some older backups could not be removed")
                    }
                    Result.success()
                }
                is BackupOutcome.Failed -> {
                    file.delete()
                    settings.recordError(outcome.message)
                    Result.failure()
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            settings.recordError(exception.message ?: "Automatic backup failed")
            Result.failure()
        }
    }
}

internal fun isAutomaticBackupFile(name: String?): Boolean =
    isAutomaticBackupName(name, ".zip")

internal fun isBackupFolderDisplayFile(name: String?): Boolean =
    name?.endsWith(".zip", ignoreCase = true) == true

internal fun isAutomaticBackupPendingFile(name: String?): Boolean =
    isAutomaticBackupName(name, ".zip.pending")

internal fun backupFileTimestamp(name: String, lastModified: Long): Long =
    lastModified.takeIf { it > 0L }
        ?: name.takeIf(::isAutomaticBackupFile)
            ?.removePrefix("vayana-auto-")?.removeSuffix(".zip")?.toLongOrNull()
        ?: 0L

private fun isAutomaticBackupName(name: String?, extension: String): Boolean =
    name != null && name.startsWith("vayana-auto-") && name.endsWith(extension) &&
        name.removePrefix("vayana-auto-").removeSuffix(extension).let { timestamp ->
            timestamp.length == 13 && timestamp.all(Char::isDigit)
        }
