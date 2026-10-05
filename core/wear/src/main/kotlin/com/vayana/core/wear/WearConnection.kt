package com.vayana.core.wear

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Foreground observation only. No timer, outbox, or database writes. */
fun companionConnection(context: Context, capability: String): Flow<Boolean> = callbackFlow {
    trySend(false) // Never present a persisted connection as current after reopening.
    if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
        close(); return@callbackFlow
    }
    val client = Wearable.getCapabilityClient(context.applicationContext)
    var revision = 0L
    val listener = CapabilityClient.OnCapabilityChangedListener { info ->
        revision++
        trySend(info.nodes.isNotEmpty())
    }
    client.addListener(listener, capability).addOnFailureListener { trySend(false) }
    val refresh = launch {
        while (true) {
            val requestedRevision = ++revision
            client.getCapability(capability, CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { if (revision == requestedRevision) trySend(it.nodes.isNotEmpty()) }
                .addOnFailureListener { if (revision == requestedRevision) trySend(false) }
            delay(10_000)
        }
    }
    awaitClose {
        refresh.cancel()
        client.removeListener(listener, capability)
    }
}.distinctUntilChanged()
