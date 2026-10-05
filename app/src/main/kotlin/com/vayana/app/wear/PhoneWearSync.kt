package com.vayana.app.wear

import android.content.Context
import androidx.work.*
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.WearableListenerService
import com.vayana.core.common.ApplicationScope
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.Book
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.CompanionReadingRepository
import com.vayana.core.wear.*
import com.vayana.feature.library.PhysicalReadingTimerController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PhoneWearDependencies {
    fun books(): BookRepository
    fun importer(): CompanionReadingRepository
    fun timer(): PhysicalReadingTimerController
}

@Singleton
class PhoneWearSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val books: BookRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        // Vayana also runs on e-readers without Google Play services.
        if (!hasPlayServices(context)) return
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("wear-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PhoneWearWorker>(15, TimeUnit.MINUTES).build())
        scope.launch { books.observeAll().map(::watchBooks).distinctUntilChanged().collect { enqueue(context) } }
    }
    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("wear-sync", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PhoneWearWorker>().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        }
    }
}

class PhoneWearListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        if (events.any { WearSyncRules.phoneEvent(it.dataItem.uri.path, it.type == DataEvent.TYPE_CHANGED) }) PhoneWearSync.enqueue(this)
    }
    override fun onCapabilityChanged(capability: CapabilityInfo) {
        if (capability.name == WearSyncRules.WATCH && capability.nodes.isNotEmpty()) PhoneWearSync.enqueue(this)
    }
}

class PhoneWearWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (!hasPlayServices(applicationContext)) return@withContext Result.success()
            val deps = EntryPointAccessors.fromApplication(applicationContext, PhoneWearDependencies::class.java)
            val transport = WearTransport(applicationContext)
            val items = transport.items()
            val receipts = items.filter { it.first.path?.startsWith(WearProtocol.ACK) == true }
                .associate { it.first.path!!.removePrefix(WearProtocol.ACK) to it.second }
            val incoming = items.filter { it.first.path?.startsWith(WearProtocol.SESSION) == true }
                .mapNotNull { (uri, text) -> runCatching { WearSession.parse(JSONObject(text)) }
                    .getOrNull()?.takeIf { uri.path == WearProtocol.SESSION + it.id } }
                .sortedBy { it.startedAt }
            for (session in incoming) {
                val prior = WearSyncRules.readReceipt(receipts[session.id], session.resolution, session.fingerprint())
                if (prior in WearSyncRules.delivered) continue
                val active = deps.timer().session.value
                val status = if (active != null && session.resolution != "separate" && timerOverlaps(active, session)) "phone_timer_active"
                else deps.importer().importSession(session.book.id, session.id, session.startedAt, session.endedAt,
                    session.seconds, session.startPage, session.endPage, session.book.total, session.book.version,
                    session.activeIntervals, session.clockChanged, session.resolution)
                transport.put(WearProtocol.ACK + session.id, JSONObject().put("status", status).put("resolution", session.resolution).put("fingerprint", session.fingerprint()).toString())
                android.util.Log.i("PhoneWearSync", "Session ${session.id}: $status")
            }
            val books = watchBooks(deps.books().observeAll().first())
            transport.put(WearProtocol.CATALOG, WearProtocol.catalog(books))
            // The watch removes delivered source items only after durably recording the acknowledgement.
            val sourceIds = incoming.map { it.id }.toSet()
            items.filter { it.first.path?.startsWith(WearProtocol.ACK) == true &&
                it.first.path!!.removePrefix(WearProtocol.ACK) !in sourceIds }.forEach { transport.delete(it.first) }
            Result.success()
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (error: Exception) {
            android.util.Log.w("PhoneWearSync", "Wear sync will retry", error)
            Result.retry()
        }
    }
}

internal fun watchBooks(books: List<Book>): List<WearBook> = books.filter { it.format == BookFormat.PHYSICAL &&
    it.finishedReadingAt == null && it.readingPercent < 1f && it.readingDisposition == "ACTIVE" &&
    (it.startedReadingAt != null || it.readingPercent > 0f || it.lastReadAt != null || it.totalReadingSeconds > 0) }
    .sortedByDescending { it.lastReadAt ?: it.createdAt }.take(50)
    .map { WearBook(it.syncId, it.title.take(200),
        it.pageCount?.let { count -> (it.readingPercent * count).roundToInt().coerceIn(0, count) } ?: 0,
        it.pageCount, it.updatedAt) }

private fun hasPlayServices(context: Context) = com.google.android.gms.common.GoogleApiAvailability.getInstance()
    .isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS

private fun timerOverlaps(timer: PhysicalTimerSession, session: WearSession): Boolean {
    val current = timer.checkpoint(android.os.SystemClock.elapsedRealtime())
    val a = current.activeIntervals?.let(com.vayana.core.common.ReadingIntervals::decode)
        ?: listOf(com.vayana.core.common.ReadingInterval(timer.startedAt, maxOf(timer.startedAt + 1, timer.endedAt.takeIf { it > 0 } ?: System.currentTimeMillis())))
    val b = session.activeIntervals?.let(com.vayana.core.common.ReadingIntervals::decode)
        ?: listOf(com.vayana.core.common.ReadingInterval(session.startedAt, maxOf(session.startedAt + 1, session.endedAt)))
    return a.any { left -> b.any { right -> left.start < right.end && left.end > right.start } }
}
