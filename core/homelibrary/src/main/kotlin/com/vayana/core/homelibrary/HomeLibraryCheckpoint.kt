package com.vayana.core.homelibrary

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Where the last successful sync got to. Device-local: it is not a setting, and is never backed up or synced. */
data class HomeLibraryCheckpoint(
    /** The highest `updated_at` seen (tombstones included); null until a sync has completed. */
    val lastSyncUpdatedAt: Long? = null,
    /** When the last sync (or up-to-date check) succeeded. */
    val lastSyncedAt: Long? = null,
    /** Home Library's `/info` as of the last sync, to skip a query when nothing changed. */
    val infoBookCount: Int? = null,
    val infoMaxUpdatedAt: Long? = null,
)

interface HomeLibraryCheckpointStore {
    val checkpoint: Flow<HomeLibraryCheckpoint>

    suspend fun read(): HomeLibraryCheckpoint

    suspend fun save(checkpoint: HomeLibraryCheckpoint)
}

private val Context.homeLibraryCheckpointDataStore: DataStore<Preferences> by preferencesDataStore(name = "home_library_sync")

@Singleton
class DataStoreHomeLibraryCheckpointStore @Inject constructor(
    @ApplicationContext context: Context,
) : HomeLibraryCheckpointStore {
    private val dataStore = context.homeLibraryCheckpointDataStore

    override val checkpoint: Flow<HomeLibraryCheckpoint> = dataStore.data.map { it.toCheckpoint() }

    override suspend fun read(): HomeLibraryCheckpoint = checkpoint.first()

    override suspend fun save(checkpoint: HomeLibraryCheckpoint) {
        dataStore.edit { preferences ->
            preferences.set(LastSyncUpdatedAt, checkpoint.lastSyncUpdatedAt)
            preferences.set(LastSyncedAt, checkpoint.lastSyncedAt)
            preferences.set(InfoBookCount, checkpoint.infoBookCount)
            preferences.set(InfoMaxUpdatedAt, checkpoint.infoMaxUpdatedAt)
        }
    }

    private fun Preferences.toCheckpoint() = HomeLibraryCheckpoint(
        lastSyncUpdatedAt = this[LastSyncUpdatedAt],
        lastSyncedAt = this[LastSyncedAt],
        infoBookCount = this[InfoBookCount],
        infoMaxUpdatedAt = this[InfoMaxUpdatedAt],
    )

    private fun <T> androidx.datastore.preferences.core.MutablePreferences.set(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }

    private companion object {
        val LastSyncUpdatedAt = longPreferencesKey("last_sync_updated_at")
        val LastSyncedAt = longPreferencesKey("last_synced_at")
        val InfoBookCount = intPreferencesKey("info_book_count")
        val InfoMaxUpdatedAt = longPreferencesKey("info_max_updated_at")
    }
}
