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

private val Context.vayanaSettingsDataStore by preferencesDataStore(name = "vayana_settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {

    private val dataStore = context.vayanaSettingsDataStore

    override val snapshot: Flow<SettingsSnapshot> = dataStore.data.map { preferences -> preferences.toSnapshot() }

    override fun <T : Any> observe(setting: Setting<T>): Flow<T> =
        dataStore.data.map { preferences -> preferences.read(setting) }

    override suspend fun <T : Any> update(setting: Setting<T>, value: T) {
        dataStore.edit { preferences -> preferences.write(setting, value) }
    }

    override suspend fun reset(setting: Setting<out Any>) {
        dataStore.edit { preferences -> preferences.remove(setting.preferencesKey()) }
    }

    override suspend fun resetAll() {
        dataStore.edit { preferences -> preferences.clear() }
    }

    override suspend fun exportToMap(): Map<String, String> {
        val preferences = dataStore.data.first()
        return SettingsRegistry.all.associate { setting -> setting.key to preferences.read(setting).serializeSettingValue() }
    }

    override suspend fun importFromMap(values: Map<String, String>) {
        dataStore.edit { preferences ->
            SettingsRegistry.all.forEach { setting ->
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

private fun Preferences.toSnapshot(): SettingsSnapshot = SettingsSnapshot(
    themeMode = read(SettingsRegistry.ThemeMode),
    displayProfile = read(SettingsRegistry.DisplayProfile),
    darkVariant = read(SettingsRegistry.DarkVariant),
    motionSetting = read(SettingsRegistry.Motion),
    readerFontSizePercent = read(SettingsRegistry.ReaderFontSize),
    readerLineHeight = read(SettingsRegistry.ReaderLineHeight),
    readerFontFamily = read(SettingsRegistry.ReaderFontFamily),
    readerSideMarginPercent = read(SettingsRegistry.ReaderSideMargin),
    readerUsePublisherStyles = read(SettingsRegistry.ReaderPublisherStyles),
    readerTapZoneMode = read(SettingsRegistry.ReaderTapZoneMode),
    readerVolumeKeys = read(SettingsRegistry.ReaderVolumeKeys),
    readerKeepAwake = read(SettingsRegistry.ReaderKeepAwake),
)

@Suppress("UNCHECKED_CAST")
private fun <T : Any> Preferences.read(setting: Setting<T>): T = when (setting) {
    is BooleanSetting -> this[booleanPreferencesKey(setting.key)] ?: setting.defaultValue
    is IntSetting -> this[intPreferencesKey(setting.key)] ?: setting.defaultValue
    is FloatSetting -> this[floatPreferencesKey(setting.key)] ?: setting.defaultValue
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
        is ChoiceSetting<*> -> preferences[stringPreferencesKey(setting.key)] = (value as Enum<*>).name
    }
}

private fun MutablePreferences.writeFromString(setting: Setting<out Any>, encoded: String) {
    when (setting) {
        is BooleanSetting -> this[booleanPreferencesKey(setting.key)] = encoded.toBooleanStrictOrNull() ?: setting.defaultValue
        is IntSetting -> this[intPreferencesKey(setting.key)] = (encoded.toIntOrNull() ?: setting.defaultValue).coerceIn(setting.range)
        is FloatSetting -> this[floatPreferencesKey(setting.key)] = (encoded.toFloatOrNull() ?: setting.defaultValue).coerceIn(setting.range.start, setting.range.endInclusive)
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
    is ChoiceSetting<*> -> stringPreferencesKey(key)
}

private fun Any.serializeSettingValue(): String = if (this is Enum<*>) name else toString()

private class MutablePreferencesWriter(val preferences: MutablePreferences)

private fun <T : Any> MutablePreferences.write(setting: Setting<T>, value: T) =
    MutablePreferencesWriter(this).write(setting, value)
