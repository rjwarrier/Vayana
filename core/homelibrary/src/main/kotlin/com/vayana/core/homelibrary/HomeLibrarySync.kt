package com.vayana.core.homelibrary

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vayana.core.common.ApplicationScope
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.diagnostics.DiagnosticCategory
import com.vayana.core.diagnostics.DiagnosticsLogStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the user sees of the mirror: one calm line, never an error dialog. */
sealed interface HomeLibraryStatus {
    /** The "Sync with Home Library" setting is off. */
    data object Disabled : HomeLibraryStatus

    /** Nothing has been tried yet in this process. */
    data object Idle : HomeLibraryStatus
    data object Syncing : HomeLibraryStatus
    data object Connected : HomeLibraryStatus

    /** Home Library is not installed, or refused the query. */
    data object NotConnected : HomeLibraryStatus
    data object SharingOff : HomeLibraryStatus
    data object UnsupportedSchema : HomeLibraryStatus

    /** The last run threw; the next trigger retries. */
    data object Failed : HomeLibraryStatus
}

/**
 * Runs Home Library syncs and decides when: at launch and whenever the app comes to the foreground, when Home
 * Library signals a change (debounced), and from a periodic background job as a fallback. All runs share one lock, so
 * a trigger arriving mid-sync waits and then finds nothing left to do.
 */
@Singleton
class HomeLibrarySync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: HomeLibrarySyncEngine,
    private val covers: HomeLibraryCovers,
    private val settings: SettingsRepository,
    private val checkpoints: HomeLibraryCheckpointStore,
    private val logStore: DiagnosticsLogStore,
    private val dispatchers: DispatcherProvider,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val lock = Mutex()
    private val mutableStatus = MutableStateFlow<HomeLibraryStatus>(HomeLibraryStatus.Idle)
    @Volatile private var lastAttemptAt = 0L

    val status: StateFlow<HomeLibraryStatus> = mutableStatus

    /** When the last sync (or up-to-date check) succeeded; null if never. */
    val lastSyncedAt: Flow<Long?> = checkpoints.checkpoint.map { it.lastSyncedAt }.distinctUntilChanged()

    /** Starts following the setting: watching Home Library and scheduling the fallback only while it is on. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch {
            settings.snapshot.map { it.homeLibrarySyncEnabled }.distinctUntilChanged().collectLatest { enabled ->
                if (!enabled) {
                    WorkManager.getInstance(context).cancelUniqueWork(WorkName)
                    mutableStatus.value = HomeLibraryStatus.Disabled
                    return@collectLatest
                }
                if (mutableStatus.value == HomeLibraryStatus.Disabled) mutableStatus.value = HomeLibraryStatus.Idle
                schedulePeriodic()
                syncNow()
                homeLibraryChanges(context).collect { syncNow() }
            }
        }
    }

    /** Called when the app comes to the foreground; the cheap `/info` check inside the run skips needless queries. */
    fun onForeground() {
        if (System.currentTimeMillis() - lastAttemptAt < ForegroundThrottleMillis) return
        scope.launch { syncNow() }
    }

    /**
     * One sync run; false only when the setting is off. [force] skips the "nothing changed" shortcut. With
     * [skipIfSyncedWithin] a run is skipped when one finished that recently (the periodic fallback, since the change
     * observer and foreground checks normally keep the mirror fresh).
     */
    suspend fun syncNow(force: Boolean = false, skipIfSyncedWithin: Long = 0L): Boolean = lock.withLock {
        if (!settings.snapshot.first().homeLibrarySyncEnabled) return@withLock false
        if (skipIfSyncedWithin > 0L) {
            val last = checkpoints.read().lastSyncedAt
            if (last != null && System.currentTimeMillis() - last < skipIfSyncedWithin) return@withLock true
        }
        lastAttemptAt = System.currentTimeMillis()
        mutableStatus.value = HomeLibraryStatus.Syncing
        val result = runCatchingCancellable { withContext(dispatchers.io) { engine.sync(force) } }
            .getOrElse { error ->
                logStore.record(DiagnosticCategory.SYNC, LogSource, "Home Library sync failed", error.stackTraceToString())
                mutableStatus.value = HomeLibraryStatus.Failed
                return@withLock true
            }
        mutableStatus.value = result.toStatus()
        if (mutableStatus.value == HomeLibraryStatus.Connected) {
            runCatchingCancellable { covers.refresh() }
                .onFailure { logStore.record(DiagnosticCategory.SYNC, LogSource, "Home Library cover fetch failed", it.stackTraceToString()) }
        }
        true
    }

    private fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<HomeLibrarySyncWorker>(PeriodicHours, TimeUnit.HOURS).build()
        // No network constraint: the catalog is on this phone. KEEP leaves an already-scheduled job alone.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WorkName, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun HomeLibrarySyncResult.toStatus(): HomeLibraryStatus = when (this) {
        is HomeLibrarySyncResult.Synced, HomeLibrarySyncResult.UpToDate, HomeLibrarySyncResult.NoChanges -> HomeLibraryStatus.Connected
        HomeLibrarySyncResult.NotConnected -> HomeLibraryStatus.NotConnected
        HomeLibrarySyncResult.SharingOff -> HomeLibraryStatus.SharingOff
        is HomeLibrarySyncResult.UnsupportedSchema -> HomeLibraryStatus.UnsupportedSchema
    }

    private companion object {
        const val WorkName = "home_library.sync"
        const val LogSource = "HomeLibrarySync"
        const val PeriodicHours = 6L
        const val ForegroundThrottleMillis = 60_000L
    }
}

/**
 * Emits once whenever Home Library says its catalog changed (any `/books` URI, descendants included), for as long as it
 * is collected. A burst of change notifications (an import touches many rows) settles for [debounceMillis] first, so
 * it costs one sync.
 */
@OptIn(FlowPreview::class)
fun homeLibraryChanges(context: Context, debounceMillis: Long = ChangeDebounceMillis): Flow<Unit> = callbackFlow {
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            trySend(Unit)
        }
    }
    // Home Library not installed on this device: registering throws SecurityException, which killed the app on launch.
    val registered = runCatching {
        context.contentResolver.registerContentObserver(HomeLibraryContract.booksUri, true, observer)
    }.isSuccess
    awaitClose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
}.conflate().debounce(debounceMillis)

// An import in Home Library touches many rows over several seconds; let it settle so it costs one sync.
private const val ChangeDebounceMillis = 3_000L

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface HomeLibrarySyncEntryPoint {
    fun homeLibrarySync(): HomeLibrarySync
}

private val FreshForMillis = TimeUnit.HOURS.toMillis(1)

/** The fallback for a missed change signal (Home Library was killed, or the app was not running). */
class HomeLibrarySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, HomeLibrarySyncEntryPoint::class.java)
            .homeLibrarySync()
            .syncNow(skipIfSyncedWithin = FreshForMillis)
        // Every outcome (not connected, failed) is already recorded in the status; WorkManager retrying adds nothing.
        return Result.success()
    }
}
