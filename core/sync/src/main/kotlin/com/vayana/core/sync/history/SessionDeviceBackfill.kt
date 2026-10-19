package com.vayana.core.sync.history

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.vayana.core.backup.parsePortableReadingSessions
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.database.repository.ReadingSessionRepository
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.diagnostics.DiagnosticCategory
import com.vayana.core.diagnostics.DiagnosticsLogStore
import com.vayana.core.sync.asset.GitHubAssetStoreException
import com.vayana.core.sync.asset.GitHubSnapshotCommit
import com.vayana.core.sync.asset.GraphQlTimeoutStatusCodes
import com.vayana.core.sync.github.assetStore
import com.vayana.core.sync.github.gitHubSyncConfig
import com.vayana.core.sync.github.isGitHubSyncReady
import com.vayana.core.sync.isOfflineSyncFailure
import com.vayana.core.sync.snapshot.unzipSnapshotJson
import com.vayana.core.sync.usage.SyncDataUsageLog
import com.vayana.core.sync.usage.SyncKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first

/**
 * Once per sync repository, names the device that recorded each reading session from before sessions carried one,
 * by reading the repository's commit history (see [creditSessionsToDevices]). Waits for an unmetered network: it
 * reads a few megabytes. A run that fails is tried again a day later; one that finishes is never repeated.
 */
@Singleton
class SessionDeviceBackfill @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val readingSessionRepository: ReadingSessionRepository,
    private val diagnosticsLogStore: DiagnosticsLogStore,
    private val syncDataUsageLog: SyncDataUsageLog,
) {
    private val marker = File(context.noBackupFilesDir, "session-device-backfill")

    suspend fun runIfNeeded() {
        val settings = settingsRepository.snapshot.first()
        if (!settings.isGitHubSyncReady()) return
        val store = settings.gitHubSyncConfig()?.assetStore() ?: return
        val state = runCatching { marker.readText() }.getOrNull()?.split('\n')
        if (state?.getOrNull(0) == store.cacheScope &&
            (state.getOrNull(1) == Done || (state.getOrNull(1)?.toLongOrNull() ?: 0L) > System.currentTimeMillis() - RetryAfterMillis)
        ) return
        context.getSystemService(ConnectivityManager::class.java)?.awaitUnmeteredNetwork()

        val startedAt = System.nanoTime()
        val result = runCatchingCancellable {
            val commits = ArrayList<GitHubSnapshotCommit>()
            var cursor: String? = null
            var pageSize = HistoryPageSize
            while (true) {
                val page = try {
                    store.snapshotHistoryPage(cursor, pageSize)
                } catch (timeout: GitHubAssetStoreException) {
                    // Commits from the days of many slice folders make heavy pages: ask for fewer, from the same place.
                    if (timeout.statusCode !in GraphQlTimeoutStatusCodes || pageSize <= MinHistoryPageSize) throw timeout
                    pageSize /= 2
                    continue
                }
                commits += page.commits
                cursor = page.nextCursor ?: break
            }
            val versions = sessionsVersions(commits.asReversed())
            var read = 0
            val devices = creditSessionsToDevices(versions) { version ->
                read++
                val bytes = store.getBlob(version.blobSha)
                // A document this build can't read names nobody; the stretch around it is still credited.
                runCatching {
                    val json = if (version.zipped) unzipSnapshotJson(bytes) else bytes.toString(Charsets.UTF_8)
                    parsePortableReadingSessions(json).mapTo(HashSet()) { it.syncId }
                }.getOrDefault(emptySet())
            }
            val named = readingSessionRepository.fillDeviceNames(devices)
            "commits=${commits.size}, versions=${versions.size}, read=$read, credited=${devices.size}, named=$named, " +
                "devices=${devices.values.groupingBy { it }.eachCount()}"
        }
        syncDataUsageLog.record(SyncKind.HISTORY, store.transferStats())
        val millis = (System.nanoTime() - startedAt) / 1_000_000
        result.onSuccess { detail ->
            marker.writeText("${store.cacheScope}\n$Done")
            diagnosticsLogStore.record(DiagnosticCategory.SYNC_LOG, Source, "Session devices from sync history in $millis ms",
                "$detail, ${store.requestStats()}")
        }.onFailure { failure ->
            // Losing the network mid-run isn't this run's fault: the next launch tries again.
            if (!isOfflineSyncFailure(failure.message)) marker.writeText("${store.cacheScope}\n${System.currentTimeMillis()}")
            diagnosticsLogStore.record(DiagnosticCategory.SYNC, Source, "Session devices from sync history failed after $millis ms",
                "${failure.javaClass.simpleName}: ${failure.message}, ${store.requestStats()}")
        }
    }
}

/** Suspends until the default network is validated and unmetered (Wi-Fi, usually): at once if it already is. */
private suspend fun ConnectivityManager.awaitUnmeteredNetwork() {
    callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
            }
            override fun onLost(network: Network) { trySend(false) }
        }
        registerDefaultNetworkCallback(callback)
        awaitClose { unregisterNetworkCallback(callback) }
    }.first { it }
}

private const val Done = "done"
private const val Source = "SessionDeviceBackfill"
private const val RetryAfterMillis = 24L * 60 * 60 * 1000
/** GraphQL times out (504) on 100 commits with their slice folders; 40 usually answers in a few seconds. */
private const val HistoryPageSize = 40
private const val MinHistoryPageSize = 5
