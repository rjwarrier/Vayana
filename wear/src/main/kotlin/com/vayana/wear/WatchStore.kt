package com.vayana.wear

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.vayana.core.wear.WatchState

class WatchStore(context: Context) {
    private val preferences = context.getSharedPreferences("reading", Context.MODE_PRIVATE)
    private val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    fun read(): WatchState = synchronized(lock) {
        // A malformed snapshot must surface an error, never silently clear an outbox.
        preferences.getString("state", null)?.let(WatchState::parse) ?: WatchState()
    }
    fun update(change: (WatchState) -> WatchState): WatchState = synchronized(lock) {
        val previous = read()
        val next = change(previous.recover(boot, SystemClock.elapsedRealtime()))
        if (next == previous) return@synchronized previous
        check(preferences.edit().putString("state", next.json()).commit()) { "Could not save reading data" }
        next
    }
    fun bootCount() = boot
    companion object { private val lock = Any() }
}
