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
import kotlinx.coroutines.flow.combine
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
    fun sessions(): com.vayana.core.database.repository.ReadingSessionRepository
    fun storageRoots(): com.vayana.core.filesystem.StorageRoots
}

@Singleton
class PhoneWearSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val books: BookRepository,
    private val sessions: com.vayana.core.database.repository.ReadingSessionRepository,
    private val timer: PhysicalReadingTimerController,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        // Vayana also runs on e-readers without Google Play services.
        if (!hasPlayServices(context)) return
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("wear-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PhoneWearWorker>(15, TimeUnit.MINUTES).build())
        scope.launch { books.observeAll().map(::watchBooks).distinctUntilChanged().collect { enqueue(context) } }
        // Digital reading checkpoints rewrite session rows every few seconds; the watch only shows physical sessions.
        scope.launch {
            combine(books.observeAll(), sessions.observeAll()) { allBooks, all ->
                val physicalIds = allBooks.filter { it.format == BookFormat.PHYSICAL }.map { it.id }.toSet()
                all.filter { it.bookId in physicalIds }
            }.distinctUntilChanged().collect { enqueue(context) }
        }
        scope.launch { timer.session.collect { enqueue(context) } }
        scope.launch { timer.companionStatus.collect { enqueue(context) } }
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
            deps.timer().restoreAfterBoot()
            items.filter { it.first.path?.startsWith(SharedTimerSnapshot.ROOT + "command/phone/") == true }
                .forEach { (uri, payload) ->
                    val command = runCatching { SharedTimerCommand.parse(payload) }.getOrNull() ?: return@forEach
                    if (uri.path != SharedTimerSnapshot.commandPath(command)) return@forEach
                    val status = deps.timer().applyCompanionCommand(command)
                    transport.put(SharedTimerSnapshot.receiptPath(command), deps.timer().commandReceipt(command) ?: command.receipt(status))
                }
            val allForTimer = deps.books().observeAll().first()
            items.firstOrNull { it.first.path == SharedTimerSnapshot.statePath("watch") }?.second?.let { payload ->
                val snapshot = SharedTimerSnapshot.parse(payload)
                require(snapshot.owner == "watch")
                deps.timer().receiveRemote(snapshot, allForTimer.firstOrNull { it.syncId == snapshot.book?.id }?.id ?: 0)
            }
            deps.timer().pendingCommand()?.let { command ->
                val receipt = items.firstOrNull { it.first.path == SharedTimerSnapshot.receiptPath(command) }?.second
                if (command.receiptStatus(receipt) != null) {
                    deps.timer().acknowledgeRemote(command, receipt!!)
                    if (deps.timer().pendingCommand() == null) items.firstOrNull { it.first.path == SharedTimerSnapshot.commandPath(command) }?.let { transport.delete(it.first) }
                } else transport.put(SharedTimerSnapshot.commandPath(command), command.json())
            }
            val receipts = items.filter { it.first.path?.startsWith(WearProtocol.ACK) == true }
                .associate { it.first.path!!.removePrefix(WearProtocol.ACK) to it.second }
            val incoming = items.filter { it.first.path?.startsWith(WearProtocol.SESSION) == true }
                .mapNotNull { (uri, text) -> runCatching { WearSession.parse(JSONObject(text)) }
                    .getOrNull()?.takeIf { uri.path == WearProtocol.SESSION + it.id } }
                .sortedBy { it.startedAt }
            for (session in incoming) {
                val prior = WearSyncRules.readReceipt(receipts[session.id], session.resolution, session.fingerprint())
                if (prior in WearSyncRules.delivered) continue
                val active = deps.timer().localSession()
                val status = if (active != null && session.resolution != "separate" && timerOverlaps(active, session)) "phone_timer_active"
                else deps.importer().importSession(session.book.id, session.id, session.startedAt, session.endedAt,
                    session.seconds, session.startPage, session.endPage, session.book.total, session.book.version,
                    session.activeIntervals, session.clockChanged, session.resolution)
                transport.put(WearProtocol.ACK + session.id, JSONObject().put("status", status).put("resolution", session.resolution).put("fingerprint", session.fingerprint()).toString())
                android.util.Log.i("PhoneWearSync", "Session ${session.id}: $status")
            }
            val snapshot = deps.timer().ownSnapshot()
            val timerPreferences = applicationContext.getSharedPreferences("physical_reading_timer", Context.MODE_PRIVATE)
            if (timerPreferences.getLong("publishedTimerRevision", -1) != snapshot.revision ||
                items.none { it.first.path == SharedTimerSnapshot.statePath("phone") }) {
                transport.put(SharedTimerSnapshot.statePath("phone"), snapshot.json())
                check(timerPreferences.edit().putLong("publishedTimerRevision", snapshot.revision).commit())
            }
            val allBooks = deps.books().observeAll().first()
            val books = watchBooks(allBooks)
            transport.put(WearProtocol.CATALOG, WearProtocol.catalog(books))
            val zone = java.time.ZoneId.systemDefault()
            val today = java.time.LocalDate.now(zone)
            val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val physicalIds = allBooks.filter { it.format == BookFormat.PHYSICAL }.map { it.id }.toSet()
            val logs = deps.sessions().observeAll().first().filter { it.bookId in physicalIds }
            val dailySeconds = logs.sumOf { WearDailyProgress.clippedSeconds(it.startedAt, it.endedAt,
                it.durationSeconds, it.activeIntervals, start, end) }
            val latestItems = transport.items()
            val included = logs.filter { it.endedAt >= start && it.startedAt < end }.map { it.syncId }.toSet() +
                incoming.filter { WearSyncRules.readReceipt(latestItems.firstOrNull { item -> item.first.path == WearProtocol.ACK + it.id }?.second,
                    it.resolution, it.fingerprint()) in WearSyncRules.delivered }.map { it.id }
            transport.put(WearProtocol.DAILY, WearDailyProgress(today.toString(), dailySeconds, included).json())
            val coverPaths = books.map { WearCover.path(it.id) }.toSet()
            for (book in books) {
                val cover = watchCover(book.id, allBooks.firstOrNull { it.syncId == book.id }?.coverPath
                    ?.let { deps.storageRoots().resolve(it) })
                val path = WearCover.path(book.id)
                val payload = cover.json()
                if (items.firstOrNull { it.first.path == path }?.second != payload) transport.put(path, payload)
            }
            items.filter { it.first.path?.startsWith(WearProtocol.COVER) == true && it.first.path !in coverPaths }
                .forEach { transport.delete(it.first) }
            val commandPrefix = SharedTimerSnapshot.ROOT + "command/phone/"
            val receiptPrefix = SharedTimerSnapshot.ROOT + "receipt/phone/"
            val commandIds = items.filter { it.first.path?.startsWith(commandPrefix) == true }
                .map { it.first.path!!.removePrefix(commandPrefix) }.toSet()
            items.filter { it.first.path?.startsWith(receiptPrefix) == true &&
                it.first.path!!.removePrefix(receiptPrefix) !in commandIds }.forEach { transport.delete(it.first) }
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
