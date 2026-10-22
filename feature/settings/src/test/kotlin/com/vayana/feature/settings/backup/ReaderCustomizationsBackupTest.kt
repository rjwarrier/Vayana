package com.vayana.feature.settings.backup

import android.content.Context
import android.net.Uri
import com.vayana.core.common.DefaultDispatcherProvider
import com.vayana.core.database.di.DatabaseModule
import com.vayana.core.datastore.settings.DataStoreSettingsRepository
import com.vayana.core.datastore.settings.ImportedFont
import com.vayana.core.filesystem.StorageRoots
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ReaderCustomizationsBackupTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun backupIncludesImportedFontBytesMetadataSelectionAndBookPronunciations() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val database = DatabaseModule.provideVayanaDatabase(context)
        val settings = DataStoreSettingsRepository(context)
        val roots = StorageRoots(context)
        val font = ImportedFont("custom-font", "My font", "custom-font.ttf")
        val rules = """[{"original":"Vayana","spoken":"Vaayana"}]"""
        try {
            database.openHelper.writableDatabase
            File(roots.fontsDir, font.fileName).writeText("font bytes")
            settings.updateReaderImportedFonts(listOf(font))
            settings.updateReaderCustomFontId(font.id)
            context.getSharedPreferences("reader_pronunciations", Context.MODE_PRIVATE)
                .edit().putString("stable-book-sync-id", rules).commit()
            val archive = File(temp.root, "backup.zip")
            val manager = BackupManager(context, database, settings, roots, DefaultDispatcherProvider())

            assertEquals(BackupOutcome.Success, manager.createBackup(Uri.fromFile(archive)))

            ZipFile(archive).use { zip ->
                val fontEntry = assertNotNull(zip.getEntry("fonts/${font.fileName}"))
                assertEquals("font bytes", zip.getInputStream(fontEntry).bufferedReader().use { it.readText() })
                val entry = assertNotNull(zip.getEntry("reader-customizations.json"))
                val json = JSONObject(zip.getInputStream(entry).bufferedReader().use { it.readText() })
                assertEquals(font.id, json.getJSONArray("fonts").getJSONObject(0).getString("id"))
                assertEquals(font.id, json.getString("selectedFontId"))
                assertEquals(rules, json.getJSONObject("pronunciations").getString("stable-book-sync-id"))

                val staging = temp.newFolder("staging")
                zip.entries().asSequence().filterNot { it.isDirectory }.forEach { item ->
                    val file = File(staging, item.name)
                    file.parentFile?.mkdirs()
                    zip.getInputStream(item).use { input -> file.outputStream().use { input.copyTo(it) } }
                }
                val restored = ReaderCustomizationsBackup.parse(json.toString())
                restored.validateFonts(File(staging, "fonts"))

                // Apply exactly the metadata/preferences path used after the manager swaps the staged files.
                val oldFont = ImportedFont("previous-font", "Previous font", "previous.ttf")
                settings.updateReaderImportedFonts(listOf(oldFont))
                settings.updateReaderCustomFontId(oldFont.id)
                context.getSharedPreferences("reader_pronunciations", Context.MODE_PRIVATE)
                    .edit().clear().putString("unrelated-old-book", "[]").commit()
                val beforeRestore = ReaderCustomizationsBackup.capture(context, settings)
                restored.restore(context, settings)
                assertEquals(listOf(font), settings.snapshot.first().readerImportedFonts)
                assertEquals(font.id, settings.snapshot.first().readerCustomFontId)
                val preferences = context.getSharedPreferences("reader_pronunciations", Context.MODE_PRIVATE)
                assertEquals(rules, preferences.getString("stable-book-sync-id", null))
                assertNull(preferences.getString("unrelated-old-book", null))

                // The rollback snapshot restores the previous customizations, including preference removals.
                beforeRestore.restore(context, settings)
                assertEquals(listOf(oldFont), settings.snapshot.first().readerImportedFonts)
                assertEquals(oldFont.id, settings.snapshot.first().readerCustomFontId)
                assertEquals(mapOf("unrelated-old-book" to "[]"), preferences.all)
            }
            val inspection = manager.inspectBackup(Uri.fromFile(archive)) as InspectOutcome.Success
            assertTrue(inspection.inspection.isCompatible)
            assertEquals(1, inspection.inspection.importedFontCount)
            assertEquals(1, inspection.inspection.pronunciationBookCount)

            val legacy = copyArchive(archive, "legacy.zip", 1) { it == ReaderCustomizationsEntry || it.startsWith("fonts/") }
            val legacyInspection = (manager.inspectBackup(Uri.fromFile(legacy)) as InspectOutcome.Success).inspection
            assertTrue(legacyInspection.isCompatible)
            assertNull(legacyInspection.importedFontCount)

            val beforeInvalidRestore = ReaderCustomizationsBackup.capture(context, settings)
            val missingFont = copyArchive(archive, "missing-font.zip", 2) { it.startsWith("fonts/") }
            assertTrue(manager.restoreBackup(Uri.fromFile(missingFont)) is RestoreOutcome.Failed)
            assertTrue(database.openHelper.writableDatabase.isOpen)
            assertEquals("font bytes", File(roots.fontsDir, font.fileName).readText())
            assertEquals(beforeInvalidRestore, ReaderCustomizationsBackup.capture(context, settings))

            val missingMetadata = copyArchive(archive, "missing-metadata.zip", 2) { it == ReaderCustomizationsEntry }
            assertTrue(manager.restoreBackup(Uri.fromFile(missingMetadata)) is RestoreOutcome.Failed)
            assertTrue(manager.inspectBackup(Uri.fromFile(missingMetadata)) is InspectOutcome.Failed)
            assertTrue(database.openHelper.writableDatabase.isOpen)
        } finally {
            database.close()
        }
    }

    private fun copyArchive(source: File, name: String, version: Int, omit: (String) -> Boolean): File = File(temp.root, name).also { target ->
        ZipOutputStream(target.outputStream()).use { output ->
            ZipFile(source).use { zip ->
                zip.entries().asSequence().filterNot { omit(it.name) }.forEach { entry ->
                    output.putNextEntry(ZipEntry(entry.name))
                    zip.getInputStream(entry).use { input ->
                        if (entry.name == "manifest.json") {
                            val json = JSONObject(input.bufferedReader().readText()).put("backupFormatVersion", version)
                            output.write(json.toString().toByteArray())
                        } else input.copyTo(output)
                    }
                    output.closeEntry()
                }
            }
        }
    }

    @Test
    fun invalidFontPathsMissingFilesAndSelectionsAreRejectedBeforeRestore() {
        val directory = temp.newFolder()
        File(directory, "font.ttf").writeText("font")
        val font = ImportedFont("font", "My font", "font.ttf")
        val valid = ReaderCustomizationsBackup(listOf(font), font.id, emptyMap())
        valid.validateFonts(directory)
        listOf("../outside.ttf", "nested/font.ttf", "..\\outside.ttf", "", "missing.ttf").forEach { path ->
            assertFails(path) { valid.copy(fonts = listOf(font.copy(fileName = path))).validateFonts(directory) }
        }
        assertFails { valid.copy(selectedFontId = "missing-id").validateFonts(directory) }
        assertFails { valid.copy(fonts = listOf(font, font)).validateFonts(directory) }
        File(directory, "font.ttf").writeBytes(byteArrayOf())
        assertFails { valid.validateFonts(directory) }
    }

    @Test
    fun emptyCustomizationPayloadRoundTripsAndMalformedPronunciationsAreRejected() {
        val empty = ReaderCustomizationsBackup(emptyList(), null, emptyMap())
        assertEquals(empty, ReaderCustomizationsBackup.parse(empty.toJson()))
        empty.validateFonts(temp.newFolder())
        val malformed = JSONObject(empty.toJson()).put("pronunciations", JSONObject().put("book", "not JSON"))
        assertFails { ReaderCustomizationsBackup.parse(malformed.toString()) }
    }
}
