package com.vayana.format.ocr

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import java.util.UUID

/**
 * The quiet notification while a book's text is recognised: the book, pages done, and Stop. Not a foreground
 * service; recognition runs as ordinary background work and the notification only shows how far it has got.
 * Without the notification permission there is simply no notification.
 */
internal class RecognitionNotification(
    private val context: Context,
    book: String,
    private val title: String?,
    private val workId: UUID,
) {
    private val notificationId = NotificationIdBase + (book.hashCode() and 0xFFFF)
    private val manager = NotificationManagerCompat.from(context)

    fun show(done: Int, total: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel()
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_notification_text_recognition)
            .setContentTitle(context.getString(R.string.ocr_notification_title))
            .setContentText(context.getString(R.string.ocr_notification_progress, title.orEmpty(), done, total))
            .setProgress(total, done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.ocr_notification_stop), WorkManager.getInstance(context).createCancelPendingIntent(workId))
            .build()
        manager.notify(notificationId, notification)
    }

    fun cancel() = manager.cancel(notificationId)

    private fun ensureChannel() {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        if (system.getNotificationChannel(ChannelId) != null) return
        system.createNotificationChannel(
            NotificationChannel(ChannelId, context.getString(R.string.ocr_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private companion object {
        const val ChannelId = "text-recognition"
        const val NotificationIdBase = 0x0C70000
    }
}
