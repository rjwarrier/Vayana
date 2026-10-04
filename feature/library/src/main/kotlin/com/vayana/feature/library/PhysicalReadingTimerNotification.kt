package com.vayana.feature.library

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vayana.core.common.ApplicationScope
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.diagnostics.DiagnosticCategory
import com.vayana.core.diagnostics.DiagnosticsLogStore
import com.vayana.core.resources.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps the durable timer's foreground service aligned with its active state. */
@Singleton
class PhysicalReadingTimerNotification @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val controller: PhysicalReadingTimerController,
    private val diagnostics: DiagnosticsLogStore,
    private val dispatchers: DispatcherProvider,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            controller.session.collect { timer ->
                try {
                    if (timer.activeForNotification() != null) {
                        PhysicalReadingTimerService.show(context)
                    } else {
                        PhysicalReadingTimerService.stop(context)
                    }
                } catch (error: Exception) {
                    recordTimerFailure(
                        diagnostics,
                        dispatchers,
                        NotificationCoordinatorSource,
                        "Could not update the reading timer notification",
                        error,
                    )
                }
            }
        }
    }

    /** Restarts the foreground service after Android grants notification permission. */
    fun refresh() {
        if (controller.session.value.activeForNotification() != null) {
            PhysicalReadingTimerService.show(context)
        }
    }
}

/** Owns the active reading timer notification so Android process cleanup cannot remove its controls. */
@AndroidEntryPoint
class PhysicalReadingTimerService : Service() {
    @Inject lateinit var controller: PhysicalReadingTimerController
    @Inject lateinit var diagnostics: DiagnosticsLogStore
    @Inject lateinit var dispatchers: DispatcherProvider

    private lateinit var serviceScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        createChannel()
        serviceScope = CoroutineScope(SupervisorJob() + dispatchers.main)
        serviceScope.launch { controller.session.collect(::render) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        controller.session.value.activeForNotification()?.let(::render)
        when (intent?.action) {
            ActionPause -> perform(NotificationPauseSource) { controller.pause() }
            ActionResume -> perform(NotificationResumeSource) { controller.resume() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun perform(source: String, action: suspend () -> Unit) {
        serviceScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                recordTimerFailure(
                    diagnostics,
                    dispatchers,
                    source,
                    "Reading timer notification action failed",
                    error,
                )
            }
        }
    }

    private fun render(timer: PhysicalTimerSession?) {
        val activeTimer = timer.activeForNotification()
        if (activeTimer == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        try {
            startForeground(NotificationId, buildNotification(activeTimer))
        } catch (error: Exception) {
            serviceScope.launch {
                recordTimerFailure(
                    diagnostics,
                    dispatchers,
                    NotificationServiceSource,
                    "Reading timer foreground service failed",
                    error,
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                ChannelId,
                getString(R.string.physical_timer_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.physical_timer_notification_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(timer: PhysicalTimerSession): Notification {
        val running = timer.phase == PhysicalTimerPhase.RUNNING
        val elapsedMillis = timer.elapsedMillis(SystemClock.elapsedRealtime())
        val primaryAction = if (running) {
            serviceAction(ActionPause, R.drawable.ic_notification_physical_pause, R.string.physical_timer_pause, PauseRequestCode)
        } else {
            serviceAction(ActionResume, R.drawable.ic_notification_physical_play, R.string.physical_timer_resume, ResumeRequestCode)
        }
        val contentText = if (running) {
            getString(R.string.physical_timer_notification_running, timer.startPage)
        } else {
            getString(R.string.physical_timer_notification_paused, formatTimerClock(elapsedMillis / 1_000))
        }
        return NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(R.drawable.ic_notification_physical_timer)
            .setContentTitle(timer.bookTitle)
            .setContentText(contentText)
            .setContentIntent(appPendingIntent(OpenRequestCode))
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setShowWhen(running)
            .setWhen(System.currentTimeMillis() - elapsedMillis)
            .setUsesChronometer(running)
            .addAction(primaryAction)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_notification_physical_stop,
                    getString(R.string.physical_timer_stop),
                    appPendingIntent(StopRequestCode, ActionFinish),
                ),
            )
            .build()
    }

    private fun serviceAction(action: String, icon: Int, label: Int, requestCode: Int): NotificationCompat.Action {
        val pending = PendingIntent.getService(
            this,
            requestCode,
            Intent(this, PhysicalReadingTimerService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action(icon, getString(label), pending)
    }

    private fun appPendingIntent(requestCode: Int, action: String? = null): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            intent.action = action
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

    companion object {
        fun show(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PhysicalReadingTimerService::class.java).setAction(ActionShow),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PhysicalReadingTimerService::class.java))
        }

        fun isFinishRequest(intent: Intent): Boolean = intent.action == ActionFinish
    }
}

/** Restores an interrupted running timer as paused and brings back its controls after reboot. */
@AndroidEntryPoint
class PhysicalReadingTimerBootReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: PhysicalReadingTimerController
    @Inject lateinit var diagnostics: DiagnosticsLogStore
    @Inject lateinit var dispatchers: DispatcherProvider
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        scope.launch {
            try {
                controller.restoreAfterBoot()
                if (controller.session.value.activeForNotification() != null) {
                    PhysicalReadingTimerService.show(context)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                recordTimerFailure(
                    diagnostics,
                    dispatchers,
                    BootReceiverSource,
                    "Could not restore the reading timer after reboot",
                    error,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private fun PhysicalTimerSession?.activeForNotification(): PhysicalTimerSession? = takeIf {
    it?.phase == PhysicalTimerPhase.RUNNING || it?.phase == PhysicalTimerPhase.PAUSED
}

private suspend fun recordTimerFailure(
    diagnostics: DiagnosticsLogStore,
    dispatchers: DispatcherProvider,
    source: String,
    message: String,
    error: Throwable,
) {
    withContext(dispatchers.io) {
        runCatching {
            diagnostics.record(DiagnosticCategory.CRASH, source, message, error.stackTraceToString())
        }
    }
}

private const val ChannelId = "physical_reading_timer_v2"
private const val NotificationId = 52_001
private const val OpenRequestCode = 52_002
private const val PauseRequestCode = 52_003
private const val ResumeRequestCode = 52_004
private const val StopRequestCode = 52_005
private const val ActionShow = "com.vayana.feature.library.action.SHOW_PHYSICAL_TIMER"
private const val ActionPause = "com.vayana.feature.library.action.PAUSE_PHYSICAL_TIMER"
private const val ActionResume = "com.vayana.feature.library.action.RESUME_PHYSICAL_TIMER"
private const val ActionFinish = "com.vayana.feature.library.action.FINISH_PHYSICAL_TIMER"
private const val NotificationCoordinatorSource = "PhysicalReadingTimerNotification"
private const val NotificationServiceSource = "PhysicalReadingTimerService"
private const val NotificationPauseSource = "PhysicalReadingTimerPause"
private const val NotificationResumeSource = "PhysicalReadingTimerResume"
private const val BootReceiverSource = "PhysicalReadingTimerBootReceiver"
