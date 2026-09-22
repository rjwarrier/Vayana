package com.vayana.feature.settings.backup

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutomaticBackupTest {
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
}
