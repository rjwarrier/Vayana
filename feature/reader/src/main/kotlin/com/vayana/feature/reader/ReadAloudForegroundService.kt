package com.vayana.feature.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.IBinder
import kotlinx.coroutines.flow.MutableSharedFlow

/** Keeps read-aloud alive while the reader activity is paused or the screen is locked. */
class ReadAloudForegroundService : Service() {
    private var playing = false

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationChannelId,
                getString(R.string.read_aloud_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionUpdate -> playing = intent.getBooleanExtra(ExtraPlaying, false)
            ActionToggle -> ReadAloudNotificationCommands.requestToggle()
        }
        val playbackAction = readAloudNotificationPlaybackAction(playing)
        val toggleIntent = Intent(this, ReadAloudForegroundService::class.java).setAction(ActionToggle)
        val togglePendingIntent = PendingIntent.getService(
            this,
            ToggleRequestCode,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, NotificationChannelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.read_aloud_notification_title))
            .setContentText(getString(R.string.read_aloud_notification_text))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(
                        this,
                        if (playbackAction == ReadAloudNotificationPlaybackAction.PAUSE) {
                            android.R.drawable.ic_media_pause
                        } else {
                            android.R.drawable.ic_media_play
                        },
                    ),
                    getString(
                        if (playbackAction == ReadAloudNotificationPlaybackAction.PAUSE) {
                            R.string.read_aloud_notification_pause
                        } else {
                            R.string.read_aloud_notification_play
                        },
                    ),
                    togglePendingIntent,
                ).build(),
            )
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0))
            .setOnlyAlertOnce(true)
            .build()
        startForeground(NotificationId, notification)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun show(context: Context, playing: Boolean) {
            val intent = Intent(context, ReadAloudForegroundService::class.java)
                .setAction(ActionUpdate)
                .putExtra(ExtraPlaying, playing)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ReadAloudForegroundService::class.java))
        }

        private const val NotificationChannelId = "read_aloud"
        private const val NotificationId = 4_201
        private const val ToggleRequestCode = 4_202
        private const val ActionUpdate = "com.vayana.feature.reader.action.UPDATE_READ_ALOUD"
        private const val ActionToggle = "com.vayana.feature.reader.action.TOGGLE_READ_ALOUD"
        private const val ExtraPlaying = "playing"
    }
}

internal enum class ReadAloudNotificationPlaybackAction { PLAY, PAUSE }

internal fun readAloudNotificationPlaybackAction(playing: Boolean): ReadAloudNotificationPlaybackAction =
    if (playing) ReadAloudNotificationPlaybackAction.PAUSE else ReadAloudNotificationPlaybackAction.PLAY

internal object ReadAloudNotificationCommands {
    val toggles = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun requestToggle() {
        toggles.tryEmit(Unit)
    }
}
