package com.vayana.wear

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.vayana.core.wear.*

object WatchGoals {
    fun today(context: Context, state: WatchState): Long {
        val progress = context.getSharedPreferences("reading", 0).getString("dailyProgress", null)
            ?.let { runCatching { WearDailyProgress.parse(it) }.getOrNull() }
            ?: WearDailyProgress("1970-01-01", 0, emptySet())
        return progress.total(state, System.currentTimeMillis())
    }
    fun schedule(context: Context, state: WatchState) {
        val settings = context.getSharedPreferences("watch-settings", 0)
        val minutes = settings.getInt("sessionTarget", 0)
        val active = state.displayActive(SystemClock.elapsedRealtime(), WatchStore(context).bootCount())
        val intent = PendingIntent.getBroadcast(context, 201, Intent(context, ReadingReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(intent)
        if (minutes <= 0 || active?.timer?.phase != PhysicalTimerPhase.RUNNING ||
            settings.getString("reminded", null) == "${active.timer.syncId}:$minutes") return
        val remaining = (minutes * 60_000L - active.timer.elapsedMillis(SystemClock.elapsedRealtime())).coerceAtLeast(1000)
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + remaining, intent)
    }
}

class ReadingReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = WatchStore(context)
        val state = store.read().recover(store.bootCount(), SystemClock.elapsedRealtime())
        val active = state.displayActive(SystemClock.elapsedRealtime(), WatchStore(context).bootCount()) ?: return
        val settings = context.getSharedPreferences("watch-settings", 0)
        val minutes = settings.getInt("sessionTarget", 0)
        val token = "${active.timer.syncId}:$minutes"
        if (minutes <= 0 || active.timer.phase != PhysicalTimerPhase.RUNNING || settings.getString("reminded", null) == token) return
        if (active.timer.elapsedMillis(SystemClock.elapsedRealtime()) < minutes * 60_000L) { WatchGoals.schedule(context, state); return }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("reading-goal", "Reading goal", NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null, null); enableVibration(true) })
        val tap = PendingIntent.getActivity(context, 201, Intent(context, WatchActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (android.os.Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            manager.notify(201, NotificationCompat.Builder(context, "reading-goal").setSmallIcon(R.drawable.ic_reading_timer)
                .setContentTitle("Reading target reached").setContentText("$minutes minutes · ${active.book.title}")
                .setContentIntent(tap).setAutoCancel(true).setSilent(!settings.getBoolean("haptics", true)).build())
            settings.edit().putString("reminded", token).commit()
        }
    }
}
