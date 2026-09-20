package com.vayana.core.datastore.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vayana.core.designsystem.theme.DarkVariant
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.MotionSetting
import com.vayana.core.designsystem.theme.ThemeMode
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.vayanaSettingsDataStore by preferencesDataStore(name = "vayana_settings")
private val ReaderImportedFontsKey = stringPreferencesKey("reader.imported_fonts")
private val ReaderCustomFontIdKey = stringPreferencesKey("reader.custom_font_id")
private val LaunchReadingProgressCheckMarkerKey = stringPreferencesKey("sync.launch_reading_progress_check_marker")
private val OnboardingCompletedKey = booleanPreferencesKey("onboarding.completed")
private val RecentSearchesKey = stringPreferencesKey("search.recent")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {

    private val dataStore = context.vayanaSettingsDataStore

    override val snapshot: Flow<SettingsSnapshot> = dataStore.data.map { preferences -> preferences.toSnapshot() }

    override val launchReadingProgressCheckMarker: Flow<LaunchReadingProgressCheckMarker?> =
        dataStore.data.map { preferences -> preferences.readLaunchReadingProgressCheckMarker() }

    override fun <T : Any> observe(setting: Setting<T>): Flow<T> =
        dataStore.data.map { preferences -> preferences.read(setting) }

    override suspend fun <T : Any> update(setting: Setting<T>, value: T) {
        dataStore.edit { preferences -> preferences.write(setting, value) }
    }

    override suspend fun updateReaderImportedFonts(fonts: List<ImportedFont>) {
        dataStore.edit { preferences ->
            preferences[ReaderImportedFontsKey] = fonts.serializeImportedFonts()
            val selectedId = preferences[ReaderCustomFontIdKey]
            if (selectedId != null && fonts.none { it.id == selectedId }) {
                preferences.remove(ReaderCustomFontIdKey)
            }
        }
    }

    override suspend fun updateReaderCustomFontId(fontId: String?) {
        dataStore.edit { preferences ->
            if (fontId.isNullOrBlank()) {
                preferences.remove(ReaderCustomFontIdKey)
            } else {
                preferences[ReaderCustomFontIdKey] = fontId
            }
        }
    }

    override suspend fun updateLaunchReadingProgressCheckMarker(marker: LaunchReadingProgressCheckMarker) {
        dataStore.edit { preferences ->
            preferences[LaunchReadingProgressCheckMarkerKey] = marker.serialize()
        }
    }

    override suspend fun updateOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences -> preferences[OnboardingCompletedKey] = completed }
    }

    override val recentSearches: Flow<List<String>> = dataStore.data.map { preferences -> preferences.readRecentSearches() }

    override suspend fun updateRecentSearches(transform: (List<String>) -> List<String>) {
        dataStore.edit { preferences ->
            val searches = transform(preferences.readRecentSearches())
            if (searches.isEmpty()) {
                preferences.remove(RecentSearchesKey)
            } else {
                preferences[RecentSearchesKey] = searches.joinToString("\n")
            }
        }
    }

    override suspend fun reset(setting: Setting<out Any>) {
        dataStore.edit { preferences -> preferences.remove(setting.preferencesKey()) }
    }

    override suspend fun resetAll() {
        dataStore.edit { preferences -> preferences.clear() }
    }

    override suspend fun exportToMap(includeNonExportable: Boolean): Map<String, String> {
        val preferences = dataStore.data.first()
        return SettingsRegistry.persisted
            .filter { setting -> includeNonExportable || setting.isExportable }
            .associate { setting -> setting.key to preferences.read(setting).serializeSettingValue() }
    }

    override suspend fun importFromMap(values: Map<String, String>) {
        dataStore.edit { preferences ->
            SettingsRegistry.persisted.forEach { setting ->
                values[setting.key]?.let { encoded -> preferences.writeFromString(setting, encoded) }
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    abstract fun bindSettingsRepository(repository: DataStoreSettingsRepository): SettingsRepository
}

private fun Preferences.toSnapshot(): SettingsSnapshot {
    val importedFonts = readImportedFonts()
    return SettingsSnapshot(
        onboardingCompleted = this[OnboardingCompletedKey] ?: false,
        themeMode = read(SettingsRegistry.ThemeMode),
        displayProfile = read(SettingsRegistry.DisplayProfile),
        darkVariant = read(SettingsRegistry.DarkVariant),
        motionSetting = read(SettingsRegistry.Motion),
        navigationMode = read(SettingsRegistry.NavigationMode),
        readerFontSizePercent = read(SettingsRegistry.ReaderFontSize),
        readerLineHeight = read(SettingsRegistry.ReaderLineHeight),
        readerFontFamily = read(SettingsRegistry.ReaderFontFamily),
        readerImportedFonts = importedFonts,
        readerCustomFontId = this[ReaderCustomFontIdKey]?.takeIf { selectedId ->
            importedFonts.any { it.id == selectedId }
        },
        readerTheme = read(SettingsRegistry.ReaderTheme),
        readerSideMarginPercent = read(SettingsRegistry.ReaderSideMargin),
        readerHeaderGapDp = read(SettingsRegistry.ReaderHeaderGap),
        readerFooterGapDp = read(SettingsRegistry.ReaderFooterGap),
        readerUsePublisherStyles = read(SettingsRegistry.ReaderPublisherStyles),
        readerTapZoneMode = read(SettingsRegistry.ReaderTapZoneMode),
        readerVolumeKeys = read(SettingsRegistry.ReaderVolumeKeys),
        readerKeepAwake = read(SettingsRegistry.ReaderKeepAwake),
        readerShowHeaders = read(SettingsRegistry.ReaderShowHeaders),
        readerShowFooter = read(SettingsRegistry.ReaderShowFooter),
        readerAutoMarkSelection = read(SettingsRegistry.ReaderAutoMarkSelection),
        readerBionicReading = read(SettingsRegistry.ReaderBionicReading),
        readerBolderText = read(SettingsRegistry.ReaderBolderText),
        readerTextAlign = read(SettingsRegistry.ReaderTextAlign),
        readerHyphenation = read(SettingsRegistry.ReaderHyphenation),
        readerFullScreen = read(SettingsRegistry.ReaderFullScreen),
        readerPageTurnAnimation = read(SettingsRegistry.ReaderPageTurnAnimation),
        einkRefreshEveryPages = read(SettingsRegistry.EinkRefreshEveryPages),
        einkAudioFeaturesEnabled = read(SettingsRegistry.EinkAudioFeatures),
        dailyReadingGoalMinutes = read(SettingsRegistry.DailyReadingGoalMinutes),
        yearlyBooksGoal = read(SettingsRegistry.YearlyBooksGoal),
        defaultCoverSource = read(SettingsRegistry.DefaultCoverSource),
        finishedPercent = read(SettingsRegistry.FinishedPercent),
        landscapeTwoColumnLayout = read(SettingsRegistry.LandscapeTwoColumnLayout),
        kindleDeviceName = read(SettingsRegistry.KindleDeviceName),
        githubSyncEnabled = read(SettingsRegistry.GithubSyncEnabled),
        githubOwner = read(SettingsRegistry.GithubOwner),
        githubRepository = read(SettingsRegistry.GithubRepository),
        githubBranch = read(SettingsRegistry.GithubBranch),
        githubToken = read(SettingsRegistry.GithubToken),
        githubSyncPassphrase = read(SettingsRegistry.GithubSyncPassphrase),
        readingAutoSyncEveryPages = read(SettingsRegistry.ReadingAutoSyncEveryPages),
        dynamicColor = read(SettingsRegistry.DynamicColor),
        dateFormatStyle = read(SettingsRegistry.DateFormat),
        startScreen = read(SettingsRegistry.StartScreen),
        recentlyDeletedRetention = read(SettingsRegistry.RecentlyDeletedRetention),
        weekStart = read(SettingsRegistry.WeekStart),
        readAloudRate = read(SettingsRegistry.ReadAloudRate),
        readAloudPitch = read(SettingsRegistry.ReadAloudPitch),
        readAloudVoiceName = read(SettingsRegistry.ReadAloudVoiceName),
        readAloudEngine = read(SettingsRegistry.ReadAloudEngine),
        readerBrightnessPercent = read(SettingsRegistry.ReaderBrightness),
        readerWarmLightPercent = read(SettingsRegistry.ReaderWarmLight),
        readerEdgeSwipeLight = read(SettingsRegistry.ReaderEdgeSwipeLight),
    )
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> Preferences.read(setting: Setting<T>): T = when (setting) {
    is BooleanSetting -> this[booleanPreferencesKey(setting.key)] ?: setting.defaultValue
    is IntSetting -> this[intPreferencesKey(setting.key)] ?: setting.defaultValue
    is FloatSetting -> this[floatPreferencesKey(setting.key)] ?: setting.defaultValue
    is StringSetting -> this[stringPreferencesKey(setting.key)] ?: setting.defaultValue
    is ChoiceSetting<*> -> {
        val encoded = this[stringPreferencesKey(setting.key)]
        setting.options.firstOrNull { (it.value as Enum<*>).name == encoded }?.value ?: setting.defaultValue
    }
} as T

@Suppress("UNCHECKED_CAST")
private fun <T : Any> MutablePreferencesWriter.write(setting: Setting<T>, value: T) {
    when (setting) {
        is BooleanSetting -> preferences[booleanPreferencesKey(setting.key)] = value as Boolean
        is IntSetting -> preferences[intPreferencesKey(setting.key)] = (value as Int).coerceIn(setting.range)
        is FloatSetting -> preferences[floatPreferencesKey(setting.key)] = (value as Float).coerceIn(setting.range.start, setting.range.endInclusive)
        is StringSetting -> preferences[stringPreferencesKey(setting.key)] = (value as String).take(setting.maxLength)
        is ChoiceSetting<*> -> preferences[stringPreferencesKey(setting.key)] = (value as Enum<*>).name
    }
}

private fun MutablePreferences.writeFromString(setting: Setting<out Any>, encoded: String) {
    when (setting) {
        is BooleanSetting -> this[booleanPreferencesKey(setting.key)] = encoded.toBooleanStrictOrNull() ?: setting.defaultValue
        is IntSetting -> this[intPreferencesKey(setting.key)] = (encoded.toIntOrNull() ?: setting.defaultValue).coerceIn(setting.range)
        is FloatSetting -> this[floatPreferencesKey(setting.key)] = (encoded.toFloatOrNull() ?: setting.defaultValue).coerceIn(setting.range.start, setting.range.endInclusive)
        is StringSetting -> this[stringPreferencesKey(setting.key)] = encoded.take(setting.maxLength)
        is ChoiceSetting<*> -> {
            val value = setting.options.firstOrNull { (it.value as Enum<*>).name == encoded }?.value ?: setting.defaultValue
            this[stringPreferencesKey(setting.key)] = (value as Enum<*>).name
        }
    }
}

private fun Setting<out Any>.preferencesKey(): Preferences.Key<*> = when (this) {
    is BooleanSetting -> booleanPreferencesKey(key)
    is IntSetting -> intPreferencesKey(key)
    is FloatSetting -> floatPreferencesKey(key)
    is StringSetting -> stringPreferencesKey(key)
    is ChoiceSetting<*> -> stringPreferencesKey(key)
}

private fun Any.serializeSettingValue(): String = if (this is Enum<*>) name else toString()

private val Setting<out Any>.isExportable: Boolean
    get() = this !is StringSetting || exportable

private class MutablePreferencesWriter(val preferences: MutablePreferences)

private fun <T : Any> MutablePreferences.write(setting: Setting<T>, value: T) =
    MutablePreferencesWriter(this).write(setting, value)

private fun Preferences.readImportedFonts(): List<ImportedFont> {
    val encoded = this[ReaderImportedFontsKey].orEmpty()
    if (encoded.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(encoded)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val displayName = item.optString("displayName").takeIf { it.isNotBlank() } ?: continue
                val fileName = item.optString("fileName").takeIf { it.isNotBlank() } ?: continue
                add(ImportedFont(id = id, displayName = displayName, fileName = fileName))
            }
        }
    }.getOrDefault(emptyList())
}

private fun List<ImportedFont>.serializeImportedFonts(): String {
    val array = JSONArray()
    forEach { font ->
        array.put(
            JSONObject()
                .put("id", font.id)
                .put("displayName", font.displayName)
                .put("fileName", font.fileName),
        )
    }
    return array.toString()
}

private fun Preferences.readLaunchReadingProgressCheckMarker(): LaunchReadingProgressCheckMarker? {
    val encoded = this[LaunchReadingProgressCheckMarkerKey].orEmpty()
    if (encoded.isBlank()) return null
    return runCatching {
        val json = JSONObject(encoded)
        val bookId = json.optLong("bookId", 0L).takeIf { it > 0L } ?: return@runCatching null
        val syncTarget = json.optString("syncTarget").takeIf { it.isNotBlank() } ?: return@runCatching null
        val remoteSnapshotSha = json.optString("remoteSnapshotSha").takeIf { it.isNotBlank() } ?: return@runCatching null
        val checkedAt = json.optLong("checkedAt", 0L).takeIf { it > 0L } ?: return@runCatching null
        LaunchReadingProgressCheckMarker(
            bookId = bookId,
            syncTarget = syncTarget,
            remoteSnapshotSha = remoteSnapshotSha,
            checkedAt = checkedAt,
            outcome = json.optString("outcome"),
        )
    }.getOrNull()
}

private fun LaunchReadingProgressCheckMarker.serialize(): String =
    JSONObject()
        .put("bookId", bookId)
        .put("syncTarget", syncTarget)
        .put("remoteSnapshotSha", remoteSnapshotSha)
        .put("checkedAt", checkedAt)
        .put("outcome", outcome)
        .toString()

private fun Preferences.readRecentSearches(): List<String> =
    this[RecentSearchesKey]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
