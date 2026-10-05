package com.vayana.wear

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import androidx.wear.tiles.TileService
import com.vayana.core.wear.*
import com.vayana.core.wear.WatchTimerSurface

object WatchTimerSurfaces {
    private const val CHANNEL = "reading-timer"
    private const val ID = 101
    fun refresh(context: Context, state: WatchState) {
        requestTile(context)
        ReadingComplicationService.refresh(context)
        WatchGoals.schedule(context, state)
        val manager = context.getSystemService(NotificationManager::class.java)
        val timer = WatchTimerSurface.from(state.displayActive(SystemClock.elapsedRealtime(), WatchStore(context).bootCount()))
        if (timer == null) { manager.cancel(ID); return }
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Reading timer", NotificationManager.IMPORTANCE_LOW)
            .apply { setSound(null, null); enableVibration(false) })
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        manager.notify(ID, notification(context, timer))
    }

    internal fun notification(context: Context, timer: WatchTimerSurface): android.app.Notification {
        val intent = PendingIntent.getActivity(context, 0, Intent(context, WatchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_reading_timer).setContentTitle(timer.title)
            .setContentText(if (timer.running) "Reading · page ${timer.page}" else "Paused · page ${timer.page}")
            .setContentIntent(intent).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        val status = if (timer.running) {
            builder.setWhen(System.currentTimeMillis() - (SystemClock.elapsedRealtime() - timer.time))
                .setUsesChronometer(true)
            Status.Builder().addTemplate("Reading #time#").addPart("time", Status.StopwatchPart(timer.time)).build()
        } else Status.Builder().addTemplate("#phase#").addPart("phase", Status.TextPart("Paused")).build()
        OngoingActivity.Builder(context, ID, builder).setStaticIcon(R.drawable.ic_reading_timer)
            .setTouchIntent(intent).setStatus(status).build().apply(context)
        return builder.build()
    }
    fun requestTile(context: Context) {
        TileService.getUpdater(context).requestUpdate(ReadingTileService::class.java)
    }
}
