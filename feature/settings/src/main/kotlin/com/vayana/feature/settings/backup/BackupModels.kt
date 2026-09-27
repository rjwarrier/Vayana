package com.vayana.feature.settings.backup

import com.vayana.core.resources.UiText

data class BackupManifest(
    val appVersion: String,
    val backupFormatVersion: Int,
    val databaseVersion: Int,
    val createdAt: Long,
)

sealed interface BackupOutcome {
    data object Success : BackupOutcome
    data class Failed(val message: UiText) : BackupOutcome
}

sealed interface RestoreOutcome {
    data object Success : RestoreOutcome
    data class Incompatible(val message: UiText) : RestoreOutcome
    data class Failed(val message: UiText) : RestoreOutcome
}

data class BackupInspection(
    val manifest: BackupManifest,
    val bookCount: Int,
    val annotationCount: Int,
    val settingsCount: Int,
    val totalBytes: Long,
    val isNewerFormat: Boolean,
    val isNewerDatabase: Boolean,
) {
    val isCompatible: Boolean get() = !isNewerFormat && !isNewerDatabase
}

sealed interface InspectOutcome {
    data class Success(val inspection: BackupInspection) : InspectOutcome
    data class Failed(val message: UiText) : InspectOutcome
}
