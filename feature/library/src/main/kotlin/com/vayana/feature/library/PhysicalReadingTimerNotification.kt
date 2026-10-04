package com.vayana.feature.library

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vayana.core.common.ApplicationScope
import com.vayana.core.resources.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Mirrors the durable physical-reading timer into one quiet, lock-screen-visible notification. */
@Singleton
class PhysicalReadingTimerNotification @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val controller: PhysicalReadingTimerController,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        createChannel()
        scope.launch {
            controller.session.collectLatest { timer ->
                if (timer?.phase == PhysicalTimerPhase.RUNNING || timer?.phase == PhysicalTimerPhase.PAUSED) {
                    publish(timer)
                } else {
                    NotificationManagerCompat.from(context).cancel(NotificationId)
                }
            }
        }
    }

    /** Re-publishes after Android grants notification permission without requiring a timer state change. */
    fun refresh() {
        val timer = controller.session.value
        if (timer?.phase == PhysicalTimerPhase.RUNNING || timer?.phase == PhysicalTimerPhase.PAUSED) publish(timer)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                ChannelId,
                context.getString(R.string.physical_timer_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.physical_timer_notification_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
    }

    private fun publish(timer: PhysicalTimerSession) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return

        val running = timer.phase == PhysicalTimerPhase.RUNNING
        val elapsedMillis = timer.elapsedMillis(SystemClock.elapsedRealtime())
        val primaryAction = if (running) {
            action(ActionPause, R.drawable.ic_notification_physical_pause, R.string.physical_timer_pause, PauseRequestCode)
        } else {
            action(ActionResume, R.drawable.ic_notification_physical_play, R.string.physical_timer_resume, ResumeRequestCode)
        }
        val contentText = if (running) {
            context.getString(R.string.physical_timer_notification_running, timer.startPage)
        } else {
            context.getString(R.string.physical_timer_notification_paused, formatTimerClock(elapsedMillis / 1_000))
        }
        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            PendingIntent.getActivity(
                context,
                OpenRequestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_notification_physical_timer)
            .setContentTitle(timer.bookTitle)
            .setContentText(contentText)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(running)
            .setWhen(System.currentTimeMillis() - elapsedMillis)
            .setUsesChronometer(running)
            .addAction(primaryAction)
            .addAction(action(ActionStop, R.drawable.ic_notification_physical_stop, R.string.physical_timer_stop, StopRequestCode))
            .build()
        notifications.notify(NotificationId, notification)
    }

    private fun action(action: String, icon: Int, label: Int, requestCode: Int): NotificationCompat.Action {
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, PhysicalReadingTimerNotificationReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action(icon, context.getString(label), pending)
    }

    private companion object {
        // A new ID is required because Android preserves the importance of an existing channel.
        const val ChannelId = "physical_reading_timer_v2"
        const val NotificationId = 52_001
        const val OpenRequestCode = 52_002
        const val PauseRequestCode = 52_003
        const val ResumeRequestCode = 52_004
        const val StopRequestCode = 52_005
    }
}

@AndroidEntryPoint
class PhysicalReadingTimerNotificationReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: PhysicalReadingTimerController
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ActionPause -> controller.pause()
                    ActionResume -> controller.resume()
                    ActionStop -> controller.stop()
                }
            } catch (_: Exception) {
                // The persisted timer remains recoverable; a notification action must never crash the app process.
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private const val ActionPause = "com.vayana.feature.library.action.PAUSE_PHYSICAL_TIMER"
private const val ActionResume = "com.vayana.feature.library.action.RESUME_PHYSICAL_TIMER"
private const val ActionStop = "com.vayana.feature.library.action.STOP_PHYSICAL_TIMER"
