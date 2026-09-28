package com.vayana.feature.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vayana.core.common.ApplicationScope
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.ReadingSessionRepository
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.resources.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the two daily reminders scheduled to match Settings: the reading nudge at the chosen hour (when turned on),
 * and borrowed-book reminders in the morning. Each is a daily periodic job; WorkManager may run it a little late
 * (the phone batches background work), which suits a reminder.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            settingsRepository.snapshot
                .map { Triple(it.readingReminderEnabled, it.readingReminderHour, it.borrowRemindersEnabled) }
                .distinctUntilChanged()
                .collect { (readingEnabled, readingHour, borrowEnabled) ->
                    schedule(ReadingWork, readingEnabled, readingHour, ReadingReminderWorker::class.java)
                    schedule(BorrowWork, borrowEnabled, BorrowReminderHour, BorrowReminderWorker::class.java)
                }
        }
    }

    private fun schedule(name: String, enabled: Boolean, hour: Int, worker: Class<out CoroutineWorker>) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(name)
            return
        }
        val request = PeriodicWorkRequest.Builder(worker, 1, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(hour).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        // UPDATE keeps an already-scheduled job but moves it when the hour changes.
        workManager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun delayUntil(hour: Int): Duration {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        var next = now.with(LocalTime.of(hour, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    private companion object {
        const val ReadingWork = "reminders.reading"
        const val BorrowWork = "reminders.borrowed"
        const val BorrowReminderHour = 9
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface RemindersEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun readingSessionRepository(): ReadingSessionRepository
    fun bookRepository(): BookRepository
}

/** Nudges on a day the reading goal isn't met yet; says nothing on days it is. */
class ReadingReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, RemindersEntryPoint::class.java)
        val settings = dependencies.settingsRepository().snapshot.first()
        if (!settings.readingReminderEnabled) return Result.success()
        val sessions = dependencies.readingSessionRepository().observeAll().first()
        val nudge = readingNudge(sessions, settings.dailyReadingGoalMinutes, System.currentTimeMillis(), ZoneId.systemDefault())
            ?: return Result.success()
        val resources = applicationContext.resources
        val body = if (nudge.streakDays > 0) {
            resources.getQuantityString(
                R.plurals.reminder_reading_body_streak, nudge.streakDays,
                nudge.minutesToday, nudge.goalMinutes, nudge.streakDays,
            )
        } else {
            resources.getQuantityString(R.plurals.reminder_reading_body, nudge.goalMinutes, nudge.minutesToday, nudge.goalMinutes)
        }
        ReminderNotifications.post(applicationContext, ReadingNotificationId, resources.getString(R.string.reminder_reading_title), body)
        return Result.success()
    }

    private companion object {
        const val ReadingNotificationId = 4101
    }
}

/** Reminds of each borrowed book three days and one day before it's due back, and on the day. */
class BorrowReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, RemindersEntryPoint::class.java)
        if (!dependencies.settingsRepository().snapshot.first().borrowRemindersEnabled) return Result.success()
        val books = dependencies.bookRepository().observeAll().first()
        val resources = applicationContext.resources
        borrowReminders(books, System.currentTimeMillis(), ZoneId.systemDefault()).forEach { reminder ->
            val due = if (reminder.daysRemaining == 0) {
                resources.getString(R.string.reminder_borrow_due_today)
            } else {
                resources.getQuantityString(R.plurals.reminder_borrow_due_days, reminder.daysRemaining, reminder.daysRemaining)
            }
            val body = reminder.pagesPerDay?.let { pages ->
                resources.getString(
                    R.string.reminder_borrow_body_with_pages, due,
                    resources.getQuantityString(R.plurals.reminder_borrow_pages_per_day, pages, pages),
                )
            } ?: due
            ReminderNotifications.post(
                applicationContext,
                BorrowNotificationIdBase + (reminder.bookId % BorrowNotificationIdSpan).toInt(),
                resources.getString(R.string.reminder_borrow_title, reminder.title),
                body,
            )
        }
        return Result.success()
    }

    private companion object {
        const val BorrowNotificationIdBase = 4200
        const val BorrowNotificationIdSpan = 10_000L
    }
}

internal object ReminderNotifications {
    private const val ChannelId = "reminders"

    /** Posts [title]/[body], opening the app when tapped. Quietly skipped if notifications aren't allowed. */
    fun post(context: Context, id: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(ChannelId, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
