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

    /** Recent global searches, newest first. Local to this device. */
    val recentSearches: Flow<List<String>>

    /** Replaces the recent searches with [transform] applied to the current list, atomically. */
    suspend fun updateRecentSearches(transform: (List<String>) -> List<String>)

    /** Gutenberg-only browsing history and download preference. Local to this device and never exported. */
    val recentGutenbergSearches: Flow<List<String>>
    val recentGutenbergBooks: Flow<List<GutenbergRecentBook>>
    val preferredGutenbergEdition: Flow<String>

    suspend fun recordGutenbergSearch(query: String)
    suspend fun recordGutenbergBook(book: GutenbergRecentBook)
    suspend fun updatePreferredGutenbergEdition(edition: String)
    suspend fun clearGutenbergHistory()

    suspend fun reset(setting: Setting<out Any>)

    suspend fun resetAll()

    suspend fun exportToMap(includeNonExportable: Boolean = false): Map<String, String>

    suspend fun importFromMap(values: Map<String, String>)
}

data class GutenbergRecentBook(
    val id: Long,
    val title: String,
    val author: String?,
)

data class LaunchReadingProgressCheckMarker(
    val bookId: Long,
    val syncTarget: String,
    val remoteSnapshotSha: String,
    val checkedAt: Long,
    val outcome: String = "",
)
