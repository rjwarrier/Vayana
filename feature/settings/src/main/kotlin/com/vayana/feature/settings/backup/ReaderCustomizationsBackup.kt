package com.vayana.feature.settings.backup

import android.content.Context
import com.vayana.core.datastore.settings.ImportedFont
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.resources.R
import com.vayana.core.resources.failUnless
import java.io.File
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** Optional in format-1 archives; format 2 includes font metadata/files and book-keyed speech corrections. */
internal data class ReaderCustomizationsBackup(
    val fonts: List<ImportedFont>,
    val selectedFontId: String?,
    val pronunciations: Map<String, String>,
) {
    fun toJson(): String = JSONObject().apply {
        put("fonts", JSONArray().apply {
            fonts.forEach { font ->
                put(JSONObject().put("id", font.id).put("displayName", font.displayName).put("fileName", font.fileName))
            }
        })
        put("selectedFontId", selectedFontId ?: JSONObject.NULL)
        put("pronunciations", JSONObject(pronunciations))
    }.toString()

    fun validateFonts(directory: File) {
        val root = directory.canonicalFile
        failUnless(fonts.map { it.id }.distinct().size == fonts.size, R.string.settings_error_backup_archive_contains_invalid_entry)
        for (font in fonts) {
            val file = File(root, font.fileName).canonicalFile
            failUnless(
                font.id.isNotBlank() && font.displayName.isNotBlank() &&
                    font.fileName.isNotBlank() && '/' !in font.fileName && '\\' !in font.fileName &&
                    file.parentFile == root && file.isFile && file.length() > 0,
                R.string.settings_error_backup_missing_file, "fonts", font.fileName,
            )
        }
        failUnless(selectedFontId == null || fonts.any { it.id == selectedFontId }, R.string.settings_error_backup_archive_contains_invalid_entry)
    }

    suspend fun restore(context: Context, settings: SettingsRepository) {
        settings.updateReaderImportedFonts(fonts)
        settings.updateReaderCustomFontId(selectedFontId)
        val editor = context.getSharedPreferences(ReaderPronunciationsPreferences, Context.MODE_PRIVATE).edit().clear()
        pronunciations.forEach { (bookKey, rules) -> editor.putString(bookKey, rules) }
        // The restore restarts the process immediately: await durable preferences instead of apply().
        failUnless(editor.commit(), R.string.settings_error_restore_failed)
    }

    companion object {
        suspend fun capture(context: Context, settings: SettingsRepository): ReaderCustomizationsBackup {
            val snapshot = settings.snapshot.first()
            val pronunciations = context.getSharedPreferences(ReaderPronunciationsPreferences, Context.MODE_PRIVATE)
                .all.mapValues { (_, value) -> value as String }
            return ReaderCustomizationsBackup(snapshot.readerImportedFonts, snapshot.readerCustomFontId, pronunciations)
        }

        fun parse(text: String): ReaderCustomizationsBackup {
            val json = JSONObject(text)
            val fonts = json.getJSONArray("fonts")
            val pronunciations = json.getJSONObject("pronunciations")
            return ReaderCustomizationsBackup(
                fonts = List(fonts.length()) { index ->
                    val font = fonts.getJSONObject(index)
                    ImportedFont(font.getString("id"), font.getString("displayName"), font.getString("fileName"))
                },
                selectedFontId = if (json.isNull("selectedFontId")) null else json.getString("selectedFontId"),
                pronunciations = pronunciations.keys().asSequence().associateWith { key ->
                    val rules = pronunciations.getString(key)
                    val array = JSONArray(rules)
                    repeat(array.length()) { index ->
                        val rule = array.getJSONObject(index)
                        rule.getString("original")
                        rule.getString("spoken")
                    }
                    rules
                },
            )
        }
    }
}

internal const val ReaderCustomizationsEntry = "reader-customizations.json"
// Owned by SpeechPronunciationStore in :feature:reader; only this named store is portable.
private const val ReaderPronunciationsPreferences = "reader_pronunciations"
