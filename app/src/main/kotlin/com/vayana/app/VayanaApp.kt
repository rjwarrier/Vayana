package com.vayana.app

import android.app.Application
import android.content.Context
import com.vayana.core.common.AppLanguage
import com.vayana.core.common.ApplicationScope
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.diagnostics.CrashReporter
import com.vayana.core.filesystem.StorageMaintenance
import com.vayana.core.homelibrary.HomeLibrarySync
import com.vayana.app.widget.AppShortcuts
import com.vayana.app.widget.ContinueReadingWidgetUpdater
import com.vayana.app.widget.ReadingTimeWidgetUpdater
import com.vayana.feature.reminders.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltAndroidApp
class VayanaApp : Application() {

    @Inject lateinit var crashReporter: CrashReporter
    @Inject lateinit var storageMaintenance: StorageMaintenance
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var continueReadingWidgetUpdater: ContinueReadingWidgetUpdater
    @Inject lateinit var appShortcuts: AppShortcuts
    @Inject lateinit var homeLibrarySync: HomeLibrarySync
    @Inject lateinit var readingTimeWidgetUpdater: ReadingTimeWidgetUpdater
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        crashReporter.install()
        reminderScheduler.start()
        continueReadingWidgetUpdater.start()
        appShortcuts.start()
        readingTimeWidgetUpdater.start()
        homeLibrarySync.start()
        // Housekeeping waits until launch has settled, so it never competes with the first screen.
        applicationScope.launch {
            delay(StorageMaintenanceDelayMillis)
            runCatchingCancellable { storageMaintenance.runIfDue() }
        }
    }
}

private const val StorageMaintenanceDelayMillis = 15_000L
