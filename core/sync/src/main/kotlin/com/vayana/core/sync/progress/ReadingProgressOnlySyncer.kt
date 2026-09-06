package com.vayana.core.sync.progress

import com.vayana.core.backup.PortableReadingProgressPatch
import com.vayana.core.backup.patchPortableReadingProgressOnly
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.sync.asset.GitHubAssetStoreException
import com.vayana.core.sync.github.assetStore
import com.vayana.core.sync.github.gitHubSyncConfig
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

enum class ReadingProgressOnlySyncStatus {
    PUSHED,
    NO_CHANGES,
    SYNC_DISABLED,
    CONFIG_INCOMPLETE,
    CLOUD_MISSING,
    FAILED,
}

data class ReadingProgressOnlySyncResult(
    val status: ReadingProgressOnlySyncStatus,
    val pushed: Int = 0,
    val failureMessage: String? = null,
)

@Singleton
class ReadingProgressOnlySyncer @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val bookRepository: BookRepository,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun syncLocalProgressToCloud(): ReadingProgressOnlySyncResult = withContext(dispatchers.io) {
        val settings = settingsRepository.snapshot.first()
        if (!settings.githubSyncEnabled) {
            return@withContext ReadingProgressOnlySyncResult(ReadingProgressOnlySyncStatus.SYNC_DISABLED)
        }
        val syncConfig = settings.gitHubSyncConfig()
            ?: return@withContext ReadingProgressOnlySyncResult(ReadingProgressOnlySyncStatus.CONFIG_INCOMPLETE)
        val store = runCatchingCancellable { syncConfig.assetStore() }
            .getOrElse { throwable ->
                return@withContext ReadingProgressOnlySyncResult(
                    status = ReadingProgressOnlySyncStatus.CONFIG_INCOMPLETE,
                    failureMessage = throwable.message,
                )
            }
        val patches = bookRepository.observeAll().first().map { book ->
            PortableReadingProgressPatch(
                fileHash = book.fileHash,
                lastLocator = book.lastLocator,
                readingPercent = book.readingPercent,
                lastReadAt = book.lastReadAt,
                startedReadingAt = book.startedReadingAt,
                finishedReadingAt = book.finishedReadingAt,
                totalReadingSeconds = book.totalReadingSeconds,
            )
        }

        var lastFailure: Throwable? = null
        repeat(MaxProgressOnlySyncAttempts) {
            val remoteSnapshot = runCatchingCancellable { store.getSyncDocumentWithSha(SnapshotLatestPath) }
                .getOrElse { throwable ->
                    return@withContext if (throwable.isMissingRemoteSnapshot()) {
                        ReadingProgressOnlySyncResult(
                            status = ReadingProgressOnlySyncStatus.CLOUD_MISSING,
                            failureMessage = throwable.syncFailureMessage(),
                        )
                    } else {
                        ReadingProgressOnlySyncResult(
                            status = ReadingProgressOnlySyncStatus.FAILED,
                            failureMessage = throwable.syncFailureMessage(),
                        )
                    }
                }
            val attempt = runCatchingCancellable {
                val patchResult = patchPortableReadingProgressOnly(
                    jsonText = remoteSnapshot.bytes.toString(Charsets.UTF_8),
                    patches = patches,
                    exportedAt = System.currentTimeMillis(),
                )
                if (patchResult.patched > 0) {
                    store.putSyncDocumentIfUnchanged(
                        path = SnapshotLatestPath,
                        bytes = patchResult.jsonText.toByteArray(Charsets.UTF_8),
                        expectedSha = remoteSnapshot.sha,
                    )
                    ReadingProgressOnlySyncResult(
                        status = ReadingProgressOnlySyncStatus.PUSHED,
                        pushed = patchResult.patched,
                    )
                } else {
                    ReadingProgressOnlySyncResult(ReadingProgressOnlySyncStatus.NO_CHANGES)
                }
            }
            attempt.onSuccess { result -> return@withContext result }
            val throwable = attempt.exceptionOrNull()
            lastFailure = throwable
            if (throwable?.isGitHubConflict() != true) {
                return@withContext ReadingProgressOnlySyncResult(
                    status = ReadingProgressOnlySyncStatus.FAILED,
                    failureMessage = throwable?.syncFailureMessage(),
                )
            }
        }

        ReadingProgressOnlySyncResult(
            status = ReadingProgressOnlySyncStatus.FAILED,
            failureMessage = lastFailure?.syncFailureMessage(),
        )
    }
}

private fun Throwable.syncFailureMessage(): String =
    when (this) {
        is GitHubAssetStoreException -> buildString {
            append(message ?: "GitHub request failed")
            responseBody.takeIf { it.isNotBlank() }?.let { body ->
                append(": ")
                append(body.take(MaxSyncFailureBodyChars))
            }
        }
        else -> message ?: "GitHub sync failed"
    }.take(MaxSyncFailureMessageChars)

private fun Throwable.isMissingRemoteSnapshot(): Boolean =
    this is GitHubAssetStoreException &&
        (statusCode == HttpURLConnection.HTTP_NOT_FOUND ||
            (statusCode == HttpURLConnection.HTTP_CONFLICT &&
                responseBody.contains("Git Repository is empty", ignoreCase = true)))

private fun Throwable.isGitHubConflict(): Boolean =
    this is GitHubAssetStoreException && statusCode == HttpURLConnection.HTTP_CONFLICT

private const val SnapshotLatestPath = "vayana/snapshot-latest.json"
private const val MaxProgressOnlySyncAttempts = 2
private const val MaxSyncFailureBodyChars = 320
private const val MaxSyncFailureMessageChars = 400
