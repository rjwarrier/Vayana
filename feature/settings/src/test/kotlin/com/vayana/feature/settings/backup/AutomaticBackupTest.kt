package com.vayana.feature.settings.backup

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutomaticBackupTest {
    @Test
    fun frequencyChoicesUseStableDayIntervalsAndDefaultToDailyForExistingUsers() {
        assertEquals(1L, AutomaticBackupFrequency.DAILY.intervalDays)
        assertEquals(7L, AutomaticBackupFrequency.WEEKLY.intervalDays)
        assertEquals(30L, AutomaticBackupFrequency.EVERY_30_DAYS.intervalDays)
        assertEquals(AutomaticBackupFrequency.DAILY, AutomaticBackupFrequency.fromStored(null))
        assertEquals(AutomaticBackupFrequency.WEEKLY, AutomaticBackupFrequency.fromStored("WEEKLY"))
        assertEquals(AutomaticBackupFrequency.DAILY, AutomaticBackupFrequency.fromStored("UNKNOWN"))
    }

    @Test
    fun rotationRecognizesOnlyTimestampedAutomaticArchives() {
        assertTrue(isAutomaticBackupFile("vayana-auto-1790060400000.zip"))
        assertFalse(isAutomaticBackupFile("vayana-backup-2026-09-22.zip"))
        assertFalse(isAutomaticBackupFile("vayana-auto-.zip"))
        assertFalse(isAutomaticBackupFile("vayana-auto-1790060400000.txt"))
        assertFalse(isAutomaticBackupFile("vayana-auto-1790060400000.zip.old"))
        assertFalse(isAutomaticBackupFile("vayana-auto-1790060400000.zip.pending"))
        assertTrue(isAutomaticBackupPendingFile("vayana-auto-1790060400000.zip.pending"))
    }

    @Test
    fun fileDateUsesProviderTimestampThenAutomaticFilenameAsFallback() {
        assertEquals(1_790_060_500_000L, backupFileTimestamp("vayana-auto-1790060400000.zip", 1_790_060_500_000L))
        assertEquals(1_790_060_400_000L, backupFileTimestamp("vayana-auto-1790060400000.zip", 0L))
        assertEquals(0L, backupFileTimestamp("other.zip", 0L))
    }

    @Test
    fun folderListIncludesCompletedZipFilesAndExcludesPendingWrites() {
        assertTrue(isBackupFolderDisplayFile("vayana-auto-1790060400000.zip"))
        assertTrue(isBackupFolderDisplayFile("my-manual-backup.ZIP"))
        assertFalse(isBackupFolderDisplayFile("vayana-auto-1790060400000.zip.pending"))
        assertFalse(isBackupFolderDisplayFile("notes.txt"))
    }
}
