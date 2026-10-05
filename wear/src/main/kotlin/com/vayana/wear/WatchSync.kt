package com.vayana.wear

import android.content.Context
import androidx.work.*
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.WearableListenerService
import com.vayana.core.wear.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WatchSync {
    fun enqueue(context: Context, requestBooks: Boolean = false) {
        // Supersede retry backoff. Outbox/receipts survive interruption; every pass reconciles all records.
        WorkManager.getInstance(context).enqueueUniqueWork("wear-sync", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WatchSyncWorker>().setInputData(workDataOf("request" to requestBooks))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    fun start(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("wear-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WatchSyncWorker>(15, TimeUnit.MINUTES).build())
        enqueue(context, requestBooks = true)
    }
}

class WatchListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        if (events.any { WearSyncRules.watchEvent(it.dataItem.uri.path, it.type == DataEvent.TYPE_CHANGED) }) WatchSync.enqueue(this)
    }
    override fun onCapabilityChanged(capability: CapabilityInfo) {
        if (capability.name != WearSyncRules.PHONE) return
        WatchStore(this).update { it.copy(phoneConnected = capability.nodes.isNotEmpty()) }
        WatchSync.enqueue(this, requestBooks = capability.nodes.isNotEmpty())
    }
}

class WatchSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val store = WatchStore(applicationContext)
            store.update { it.checkpoint(android.os.SystemClock.elapsedRealtime()) }
            val transport = WearTransport(applicationContext)
            val items = transport.items()
            items.filter { it.first.path?.startsWith(SharedTimerSnapshot.ROOT + "command/watch/") == true }
                .forEach { (uri, payload) ->
                    val command = runCatching { SharedTimerCommand.parse(payload) }.getOrNull() ?: return@forEach
                    if (uri.path != SharedTimerSnapshot.commandPath(command)) return@forEach
                    var status = "invalid"
                    store.update { state -> state.applySharedCommand(command, android.os.SystemClock.elapsedRealtime(), System.currentTimeMillis())
                        .also { status = it.second }.first }
                    transport.put(SharedTimerSnapshot.receiptPath(command), store.read().processedCommands[command.id]
                        ?.takeIf { command.receiptStatus(it) != null } ?: command.receipt(status))
                }
            items.firstOrNull { it.first.path == SharedTimerSnapshot.statePath("phone") }?.second?.let { payload ->
                val snapshot = SharedTimerSnapshot.parse(payload)
                require(snapshot.owner == "phone")
                store.update { it.receiveRemote(snapshot, android.os.SystemClock.elapsedRealtime(), System.currentTimeMillis(), store.bootCount()) }
            }
            store.read().timerCommands.forEach { command ->
                val receipt = items.firstOrNull { it.first.path == SharedTimerSnapshot.receiptPath(command) }?.second
                val status = command.receiptStatus(receipt)
                val revision = command.receiptRevision(receipt)
                if (status != null && (revision == null || (store.read().remoteTimer?.revision ?: -1) >= revision)) {
                    store.update { it.copy(timerCommands = it.timerCommands.filterNot { pending -> pending.id == command.id },
                        timerMessage = if (status == "applied") null else "Timer changed on phone · action was not applied") }
                    items.firstOrNull { it.first.path == SharedTimerSnapshot.commandPath(command) }?.let { transport.delete(it.first) }
                } else transport.put(SharedTimerSnapshot.commandPath(command), command.json())
            }
            items.firstOrNull { it.first.path == WearProtocol.CATALOG }?.let { (_, text) ->
                val books = WearProtocol.books(text)
                store.update { it.copy(books = books) }
            }
            items.firstOrNull { it.first.path == WearProtocol.DAILY }?.second?.let { daily ->
                WearDailyProgress.parse(daily)
                applicationContext.getSharedPreferences("reading", Context.MODE_PRIVATE).edit().putString("dailyProgress", daily).apply()
            }
            val covers = WatchCoverCache(applicationContext)
            val bookIds = store.read().books.map { it.id }.toSet()
            var coversChanged = covers.retain(bookIds)
            items.filter { it.first.path?.startsWith(WearProtocol.COVER) == true }.forEach { (uri, payload) ->
                runCatching {
                    val cover = WearCover.parse(payload)
                    if (cover.bookId in bookIds && uri.path == WearCover.path(cover.bookId)) {
                        coversChanged = covers.apply(cover) || coversChanged
                    }
                }.onFailure { android.util.Log.w("WatchSync", "Could not load book cover", it) }
            }
            if (coversChanged) {
                applicationContext.getSharedPreferences("reading", Context.MODE_PRIVATE)
                    .edit().putLong("coversRevision", System.currentTimeMillis()).apply()
                WatchTimerSurfaces.requestTile(applicationContext)
            }
            for ((uri, payload) in items.filter { it.first.path?.startsWith(WearProtocol.ACK) == true }) {
                val id = uri.path!!.removePrefix(WearProtocol.ACK)
                val entry = store.read().entries.firstOrNull { it.session.id == id } ?: continue
                val receipt = WearSyncRules.readReceipt(payload, entry.session.resolution, entry.session.fingerprint()) ?: continue
                if (receipt !in WearSyncRules.receipts) continue
                store.update { state ->
                    // The user may revise timestamps while this worker awaits a transport operation.
                    if (state.entries.firstOrNull { it.session.id == id }?.session?.fingerprint() == entry.session.fingerprint())
                        state.acknowledge(id, receipt) else state
                }
            }
            val cleanupIds = WearSyncRules.cleanupIds(store.read())
            items.filter { it.first.path?.removePrefix(WearProtocol.SESSION) in cleanupIds &&
                it.first.path?.startsWith(WearProtocol.SESSION) == true }.forEach { transport.delete(it.first) }
            // Re-publishing uses a stable path. Retries cannot create duplicate phone history.
            store.read().entries.filter { it.receipt !in WearSyncRules.delivered }.forEach {
                transport.put(WearProtocol.SESSION + it.session.id, it.session.json().toString())
            }
            val owned = store.read()
            val preferences = applicationContext.getSharedPreferences("reading", Context.MODE_PRIVATE)
            if (preferences.getLong("publishedTimerRevision", -1) != owned.timerRevision ||
                items.none { it.first.path == SharedTimerSnapshot.statePath("watch") }) {
                transport.put(SharedTimerSnapshot.statePath("watch"), SharedTimerSnapshot.create("watch", owned.timerRevision,
                    owned.active, android.os.SystemClock.elapsedRealtime(), System.currentTimeMillis(), store.epoch()).json())
                check(preferences.edit().putLong("publishedTimerRevision", owned.timerRevision).commit())
            }
            val commandPrefix = SharedTimerSnapshot.ROOT + "command/watch/"
            val receiptPrefix = SharedTimerSnapshot.ROOT + "receipt/watch/"
            val commandIds = items.filter { it.first.path?.startsWith(commandPrefix) == true }
                .map { it.first.path!!.removePrefix(commandPrefix) }.toSet()
            items.filter { it.first.path?.startsWith(receiptPrefix) == true &&
                it.first.path!!.removePrefix(receiptPrefix) !in commandIds }.forEach { transport.delete(it.first) }
            // Every pass refreshes the phone, including a pass replacing an older retry/request.
            run {
                transport.put(WearProtocol.REQUEST, java.util.UUID.randomUUID().toString())
            }
            val connected = transport.phoneConnected()
            store.update { it.copy(phoneConnected = connected, syncFailed = false) }
            android.util.Log.i("WatchSync", "Reconciled: phone=$connected pending=${store.read().entries.count { it.receipt !in WearSyncRules.delivered }}")
            Result.success()
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (error: Exception) {
            android.util.Log.w("WatchSync", "Wear sync will retry", error)
            runCatching { WatchStore(applicationContext).update { it.copy(syncFailed = true) } }
            Result.retry()
        }
    }
}
