package com.vayana.core.datastore.settings

import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

enum class SmartShelfStatus { ALL, UNREAD, READING, FINISHED }

data class SmartShelf(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val query: String = "",
    val author: String = "",
    val tag: String = "",
    val status: SmartShelfStatus = SmartShelfStatus.ALL,
    val dormantDays: Int = 0,
    val withNotes: Boolean = false,
)

data class ReadingPreset(
    val id: String = UUID.randomUUID().toString(), val name: String,
    val fontSizePercent: Int, val lineHeight: Float, val fontFamily: ReaderFontFamily,
    val sideMarginPercent: Int, val theme: ReaderTheme, val bolderText: Boolean,
    val textAlign: ReaderTextAlign, val hyphenation: ReaderHyphenation, val usePublisherStyles: Boolean,
    val customFontId: String? = null,
) {
    companion object {
        fun capture(name: String, settings: SettingsSnapshot) = ReadingPreset(
            name = name.trim().take(80), fontSizePercent = settings.readerFontSizePercent,
            lineHeight = settings.readerLineHeight, fontFamily = settings.readerFontFamily,
            sideMarginPercent = settings.readerSideMarginPercent, theme = settings.readerTheme,
            bolderText = settings.readerBolderText, textAlign = settings.readerTextAlign,
            hyphenation = settings.readerHyphenation, usePublisherStyles = settings.readerUsePublisherStyles,
            customFontId = settings.readerCustomFontId,
        )
    }
}

/** Registered structured settings participate in settings backups and portable snapshot exports. */
@Singleton
class ReadingToolsRepository @Inject constructor(private val settings: SettingsRepository) {
    val smartShelves = settings.observe(SettingsRegistry.SmartShelves).map(::decodeSmartShelves).distinctUntilChanged()
    val presets = settings.observe(SettingsRegistry.ReadingPresets).map(::decodeReadingPresets).distinctUntilChanged()
    val plans = settings.observe(SettingsRegistry.ReadingPlans).map(::decodeReadingPlans).distinctUntilChanged()

    suspend fun saveShelf(shelf: SmartShelf) {
        require(shelf.name.isNotBlank())
        settings.updateAtomic(SettingsRegistry.SmartShelves) { encoded ->
            val others = decodeSmartShelves(encoded).filterNot { it.id == shelf.id }
            require(others.size < 50)
            encodeSmartShelves(others + shelf.copy(name = shelf.name.trim().take(80), query = shelf.query.trim().take(500),
                author = shelf.author.trim().take(160), tag = shelf.tag.trim().take(160), dormantDays = shelf.dormantDays.coerceIn(0, 3650)))
        }
    }

    suspend fun deleteShelf(id: String) = settings.updateAtomic(SettingsRegistry.SmartShelves) {
        encodeSmartShelves(decodeSmartShelves(it).filterNot { shelf -> shelf.id == id })
    }

    suspend fun savePreset(preset: ReadingPreset) {
        require(preset.name.isNotBlank())
        settings.updateAtomic(SettingsRegistry.ReadingPresets) {
            val others = decodeReadingPresets(it).filterNot { old -> old.id == preset.id }
            require(others.size < 50)
            encodeReadingPresets(others + preset.copy(name = preset.name.trim().take(80)))
        }
    }

    suspend fun deletePreset(id: String) = settings.updateAtomic(SettingsRegistry.ReadingPresets) {
        encodeReadingPresets(decodeReadingPresets(it).filterNot { preset -> preset.id == id })
    }

    suspend fun setFinishBy(bookSyncId: String, date: LocalDate?) {
        require(bookSyncId.isNotBlank())
        settings.updateAtomic(SettingsRegistry.ReadingPlans) {
            val plans = decodeReadingPlans(it).toMutableMap()
            if (date == null) plans.remove(bookSyncId) else plans[bookSyncId] = date
            JSONObject().apply { plans.forEach { (id, day) -> put(id, day.toString()) } }.toString()
        }
    }
}

internal fun decodeSmartShelves(encoded: String): List<SmartShelf> = jsonItems(encoded).mapNotNull { json ->
    runCatching {
        SmartShelf(id = json.getString("id").take(160), name = json.getString("name").take(80),
            query = json.optString("query").take(500), author = json.optString("author").take(160),
            tag = json.optString("tag").take(160), status = SmartShelfStatus.valueOf(json.optString("status", "ALL")),
            dormantDays = json.optInt("dormantDays").coerceIn(0, 3650), withNotes = json.optBoolean("withNotes"))
            .takeIf { it.id.isNotBlank() && it.name.isNotBlank() }
    }.getOrNull()
}.distinctBy { it.id }.take(50)

internal fun encodeSmartShelves(shelves: List<SmartShelf>): String = JSONArray().apply {
    shelves.forEach { shelf -> put(JSONObject().put("id", shelf.id).put("name", shelf.name).put("query", shelf.query)
        .put("author", shelf.author).put("tag", shelf.tag).put("status", shelf.status.name)
        .put("dormantDays", shelf.dormantDays).put("withNotes", shelf.withNotes)) }
}.toString()

internal fun decodeReadingPresets(encoded: String): List<ReadingPreset> = jsonItems(encoded).mapNotNull { json ->
    runCatching {
        ReadingPreset(id = json.getString("id").take(160), name = json.getString("name").take(80),
            fontSizePercent = json.getInt("fontSize").coerceIn(SettingsRegistry.ReaderFontSize.range),
            lineHeight = json.getDouble("lineHeight").toFloat().let { require(it.isFinite()); it.coerceIn(SettingsRegistry.ReaderLineHeight.range) },
            fontFamily = ReaderFontFamily.valueOf(json.getString("fontFamily")),
            sideMarginPercent = json.getInt("margin").coerceIn(SettingsRegistry.ReaderSideMargin.range),
            theme = ReaderTheme.valueOf(json.getString("theme")), bolderText = json.optBoolean("bold"),
            textAlign = ReaderTextAlign.valueOf(json.optString("align", "BOOK")),
            hyphenation = ReaderHyphenation.valueOf(json.optString("hyphenation", "BOOK")),
            usePublisherStyles = json.optBoolean("publisher", true),
            customFontId = json.optString("customFontId").take(160).ifBlank { null })
            .takeIf { it.id.isNotBlank() && it.name.isNotBlank() }
    }.getOrNull()
}.distinctBy { it.id }.take(50)

internal fun encodeReadingPresets(presets: List<ReadingPreset>): String = JSONArray().apply {
    presets.forEach { preset -> put(JSONObject().put("id", preset.id).put("name", preset.name)
        .put("fontSize", preset.fontSizePercent).put("lineHeight", preset.lineHeight.toDouble())
        .put("fontFamily", preset.fontFamily.name).put("margin", preset.sideMarginPercent)
        .put("theme", preset.theme.name).put("bold", preset.bolderText).put("align", preset.textAlign.name)
        .put("hyphenation", preset.hyphenation.name).put("publisher", preset.usePublisherStyles)
        .put("customFontId", preset.customFontId.orEmpty())) }
}.toString()

internal fun decodeReadingPlans(encoded: String): Map<String, LocalDate> = runCatching {
    val json = JSONObject(encoded)
    buildMap {
        json.keys().forEach { id ->
            if (id.isNotBlank() && id.length <= 160) runCatching { LocalDate.parse(json.getString(id)) }
                .getOrNull()?.let { put(id, it) }
        }
    }
}.getOrDefault(emptyMap())

private fun jsonItems(encoded: String): List<JSONObject> = runCatching {
    val array = JSONArray(encoded)
    (0 until minOf(array.length(), 100)).mapNotNull { array.optJSONObject(it) }
}.getOrDefault(emptyList())
