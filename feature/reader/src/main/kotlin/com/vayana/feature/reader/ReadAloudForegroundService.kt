package com.vayana.feature.reader

import com.vayana.core.common.AppLanguage
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Keeps read-aloud alive while the reader activity is paused or the screen is locked, and exposes it as media: a
 * [MediaSession] so the lock screen, headset and Bluetooth buttons and the notification can play, pause and skip.
 */
class ReadAloudForegroundService : Service() {
    private var playing = false
    private var bookTitle: String? = null
    private var progressPercent: Int? = null
    private var mediaSession: MediaSession? = null

    // Notification text in the language chosen in Settings (before Android 13 the system does not apply it).
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

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
        mediaSession = MediaSession(this, MediaSessionTag).apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() = ReadAloudNotificationCommands.send(ReadAloudCommand.PLAY)

                    override fun onPause() = ReadAloudNotificationCommands.send(ReadAloudCommand.PAUSE)

                    override fun onSkipToNext() = ReadAloudNotificationCommands.send(ReadAloudCommand.NEXT)

                    override fun onSkipToPrevious() = ReadAloudNotificationCommands.send(ReadAloudCommand.PREVIOUS)

                    override fun onStop() = ReadAloudNotificationCommands.send(ReadAloudCommand.STOP)
                },
            )
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionUpdate -> {
                playing = intent.getBooleanExtra(ExtraPlaying, false)
                bookTitle = intent.getStringExtra(ExtraBookTitle)
                progressPercent = intent
                    .takeIf { it.hasExtra(ExtraProgressPercent) }
                    ?.getIntExtra(ExtraProgressPercent, 0)
                    ?.coerceIn(0, 100)
            }
            ActionCommand -> intent.getStringExtra(ExtraCommand)
                ?.let { name -> ReadAloudCommand.entries.firstOrNull { it.name == name } }
                ?.let(ReadAloudNotificationCommands::send)
        }
        publishSessionState()
        startForeground(NotificationId, buildNotification())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaSession?.run {
            isActive = false
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun displayTitle(): String =
        bookTitle?.takeIf(String::isNotBlank) ?: getString(R.string.read_aloud_notification_title)

    private fun publishSessionState() {
        val session = mediaSession ?: return
        val title = displayTitle()
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, getString(R.string.read_aloud_notification_title))
                .build(),
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(SessionActions)
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    if (playing) 1f else 0f,
                )
                .build(),
        )
    }

    private fun buildNotification(): Notification {
        val playPause = when (readAloudNotificationPlaybackAction(playing)) {
            ReadAloudNotificationPlaybackAction.PAUSE ->
                action(ReadAloudCommand.PAUSE, R.drawable.ic_notification_pause, R.string.read_aloud_notification_pause)
            ReadAloudNotificationPlaybackAction.PLAY ->
                action(ReadAloudCommand.PLAY, R.drawable.ic_notification_play, R.string.read_aloud_notification_play)
        }
        val style = Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2)
        mediaSession?.let { style.setMediaSession(it.sessionToken) }
        val builder = Notification.Builder(this, NotificationChannelId)
            .setSmallIcon(R.drawable.ic_notification_read_aloud)
            .setContentTitle(displayTitle())
            .setContentText(
                progressPercent?.let { getString(R.string.read_aloud_notification_progress, it) }
                    ?: getString(R.string.read_aloud_notification_text),
            )
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(action(ReadAloudCommand.PREVIOUS, R.drawable.ic_notification_previous, R.string.read_aloud_notification_previous))
            .addAction(playPause)
            .addAction(action(ReadAloudCommand.NEXT, R.drawable.ic_notification_next, R.string.read_aloud_notification_next))
            .setStyle(style)
            .setOnlyAlertOnce(true)
        progressPercent?.let { builder.setProgress(100, it, false) }
        return builder.build()
    }

    private fun action(command: ReadAloudCommand, @DrawableRes icon: Int, @StringRes label: Int): Notification.Action {
        val intent = Intent(this, ReadAloudForegroundService::class.java)
            .setAction(ActionCommand)
            .putExtra(ExtraCommand, command.name)
        val pending = PendingIntent.getService(
            this,
            CommandRequestCodeBase + command.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), getString(label), pending).build()
    }

    companion object {
        fun show(context: Context, playing: Boolean, bookTitle: String?, progressPercent: Int?) {
            val intent = Intent(context, ReadAloudForegroundService::class.java)
                .setAction(ActionUpdate)
                .putExtra(ExtraPlaying, playing)
                .putExtra(ExtraBookTitle, bookTitle)
            progressPercent?.let { intent.putExtra(ExtraProgressPercent, it) }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ReadAloudForegroundService::class.java))
        }

        private const val NotificationChannelId = "read_aloud"
        private const val NotificationId = 4_201
        private const val CommandRequestCodeBase = 4_210
        private const val MediaSessionTag = "VayanaReadAloud"
        private const val ActionUpdate = "com.vayana.feature.reader.action.UPDATE_READ_ALOUD"
        private const val ActionCommand = "com.vayana.feature.reader.action.READ_ALOUD_COMMAND"
        private const val ExtraPlaying = "playing"
        private const val ExtraBookTitle = "book_title"
        private const val ExtraProgressPercent = "progress_percent"
        private const val ExtraCommand = "command"
        private const val SessionActions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_STOP
    }
}

internal enum class ReadAloudNotificationPlaybackAction { PLAY, PAUSE }

internal fun readAloudNotificationPlaybackAction(playing: Boolean): ReadAloudNotificationPlaybackAction =
    if (playing) ReadAloudNotificationPlaybackAction.PAUSE else ReadAloudNotificationPlaybackAction.PLAY

/** What the notification, lock screen or a headset asks read-aloud to do. */
internal enum class ReadAloudCommand { PLAY, PAUSE, NEXT, PREVIOUS, STOP }

internal object ReadAloudNotificationCommands {
    val commands = MutableSharedFlow<ReadAloudCommand>(extraBufferCapacity = 4)

    fun send(command: ReadAloudCommand) {
        commands.tryEmit(command)
    }
}
