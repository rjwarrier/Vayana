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
            items.firstOrNull { it.first.path == WearProtocol.CATALOG }?.let { (_, text) ->
                val books = WearProtocol.books(text)
                store.update { it.copy(books = books) }
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
