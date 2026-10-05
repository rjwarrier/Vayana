package com.vayana.wear

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.vayana.core.wear.*

class WatchStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("reading", Context.MODE_PRIVATE)
    private val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    fun read(): WatchState = synchronized(lock) {
        // A malformed snapshot must surface an error, never silently clear an outbox.
        preferences.getString("state", null)?.let(WatchState::parse) ?: WatchState()
    }
    fun update(change: (WatchState) -> WatchState): WatchState = synchronized(lock) {
        val previous = read()
        var next = change(previous.recover(boot, SystemClock.elapsedRealtime()))
        if (WatchTimerSurface.from(previous.active) != WatchTimerSurface.from(next.active) || previous.active?.timer?.syncId != next.active?.timer?.syncId)
            next = next.copy(timerRevision = previous.timerRevision + 1)
        if (next == previous) return@synchronized previous
        check(preferences.edit().putString("state", next.json()).commit()) { "Could not save reading data" }
        if (com.vayana.core.wear.WatchTimerSurface.from(previous.active) != com.vayana.core.wear.WatchTimerSurface.from(next.active) || previous.books != next.books || previous.remoteTimer != next.remoteTimer || previous.timerCommands != next.timerCommands) {
            runCatching { WatchTimerSurfaces.refresh(context, next) }
                .onFailure { android.util.Log.w("WatchStore", "Could not update timer surfaces", it) }
        }
        next
    }
    fun epoch(): String = synchronized(lock) {
        preferences.getString("timerEpoch", null) ?: java.util.UUID.randomUUID().toString().also {
            check(preferences.edit().putString("timerEpoch", it).commit())
        }
    }
    fun bootCount() = boot
    companion object { private val lock = Any() }
}
