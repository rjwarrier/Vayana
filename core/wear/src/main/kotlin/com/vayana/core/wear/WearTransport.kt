package com.vayana.core.wear

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.CapabilityClient
import java.util.concurrent.TimeUnit

/** Blocking calls are restricted to worker IO threads. DataClient persists outgoing items offline. */
class WearTransport(context: Context) {
    private val client = Wearable.getDataClient(context)
    private val capabilities = Wearable.getCapabilityClient(context)
    fun phoneConnected(): Boolean = Tasks.await(capabilities.getCapability(WearSyncRules.PHONE,
        CapabilityClient.FILTER_REACHABLE), 15, TimeUnit.SECONDS).nodes.isNotEmpty()
    fun put(path: String, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size < 100_000)
        Tasks.await(client.putDataItem(PutDataRequest.create(path).setData(bytes).setUrgent()), 30, TimeUnit.SECONDS)
    }
    fun items(): List<Pair<Uri, String>> {
        val buffer = Tasks.await(client.dataItems, 30, TimeUnit.SECONDS)
        return try {
            buffer.filter { it.uri.path?.startsWith(WearProtocol.ROOT) == true }
                .mapNotNull { item -> item.data?.let { item.uri to it.toString(Charsets.UTF_8) } }
        } finally { buffer.release() }
    }
    fun delete(uri: Uri) { Tasks.await(client.deleteDataItems(uri), 30, TimeUnit.SECONDS) }
}
