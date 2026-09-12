package com.vayana.core.datastore.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val snapshot: Flow<SettingsSnapshot>

    val launchReadingProgressCheckMarker: Flow<LaunchReadingProgressCheckMarker?>

    fun <T : Any> observe(setting: Setting<T>): Flow<T>

    suspend fun <T : Any> update(setting: Setting<T>, value: T)

    suspend fun updateReaderImportedFonts(fonts: List<ImportedFont>)

    suspend fun updateReaderCustomFontId(fontId: String?)

    suspend fun updateLaunchReadingProgressCheckMarker(marker: LaunchReadingProgressCheckMarker)

    suspend fun updateOnboardingCompleted(completed: Boolean)

    suspend fun reset(setting: Setting<out Any>)

    suspend fun resetAll()

    suspend fun exportToMap(includeNonExportable: Boolean = false): Map<String, String>

    suspend fun importFromMap(values: Map<String, String>)
}

data class LaunchReadingProgressCheckMarker(
    val bookId: Long,
    val syncTarget: String,
    val remoteSnapshotSha: String,
    val checkedAt: Long,
    val outcome: String = "",
)
