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
import com.vayana.feature.library.PhysicalReadingTimerNotification
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltAndroidApp
class VayanaApp : Application() {

    @Inject lateinit var phoneWearSync: com.vayana.app.wear.PhoneWearSync

    @Inject lateinit var crashReporter: CrashReporter
    @Inject lateinit var storageMaintenance: StorageMaintenance
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var continueReadingWidgetUpdater: ContinueReadingWidgetUpdater
    @Inject lateinit var appShortcuts: AppShortcuts
    @Inject lateinit var notebookExport: com.vayana.feature.notes.AutomaticNotebookExport
    @Inject lateinit var epubContentIndex: com.vayana.feature.search.EpubContentIndex
    @Inject lateinit var homeLibrarySync: HomeLibrarySync
    @Inject lateinit var readingTimeWidgetUpdater: ReadingTimeWidgetUpdater
    @Inject lateinit var physicalReadingTimerNotification: PhysicalReadingTimerNotification
    @Inject lateinit var appDataUsageHistory: com.vayana.feature.settings.AppDataUsageHistory
    @Inject lateinit var settingsRepository: com.vayana.core.datastore.settings.SettingsRepository
    @Inject lateinit var localDeviceName: com.vayana.core.database.repository.LocalDeviceName
    @Inject lateinit var sessionDeviceBackfill: com.vayana.core.sync.history.SessionDeviceBackfill
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        // Reading sessions are stamped with this device's sync name, for per-device statistics.
        applicationScope.launch {
            settingsRepository.snapshot.collect { localDeviceName.value = it.kindleDeviceName.trim().ifBlank { null } }
        }
        phoneWearSync.start()
        crashReporter.install()
        reminderScheduler.start()
        continueReadingWidgetUpdater.start()
        appShortcuts.start()
        readingTimeWidgetUpdater.start()
        physicalReadingTimerNotification.start()
        homeLibrarySync.start()
        notebookExport.start()
        applicationScope.launch { delay(StorageMaintenanceDelayMillis); epubContentIndex.start() }
        // Housekeeping waits until launch has settled, so it never competes with the first screen.
        applicationScope.launch {
            delay(StorageMaintenanceDelayMillis)
            runCatchingCancellable { storageMaintenance.runIfDue() }
        }
        // Once per sync repository: name the devices of reading sessions from before sessions recorded one.
        applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            delay(SessionDeviceBackfillDelayMillis)
            runCatchingCancellable { sessionDeviceBackfill.runIfNeeded() }
        }
        // Saves the app's daily Wi-Fi and mobile use on this device before Android ages its own history out.
        applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            delay(StorageMaintenanceDelayMillis)
            runCatchingCancellable { appDataUsageHistory.update() }
        }
    }
}

private const val StorageMaintenanceDelayMillis = 15_000L
/** After launch has settled and the launch-time sync has had its turn. */
private const val SessionDeviceBackfillDelayMillis = 60_000L
