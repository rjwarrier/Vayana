package com.vayana.feature.settings.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.vayana.core.database.DATABASE_VERSION
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BackupValidatorTest {
    @get:Rule val temp = TemporaryFolder()
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun acceptsCurrentDatabaseAndBookFiles() {
        val root = backup()
        validateStagedBackup(context, root, DATABASE_VERSION)
        open(root).use { assertEquals(DATABASE_VERSION, it.version) }
    }

    @Test
    fun migratesEverySupportedVersionBeforeTheSwap() {
        for (version in 1 until DATABASE_VERSION) {
            val root = backup(version)
            validateStagedBackup(context, root, version)
            open(root).use { db ->
                assertEquals(DATABASE_VERSION, db.version)
                db.rawQuery("SELECT * FROM reading_sessions", null).use { assertEquals(0, it.count) }
            }
        }
    }

    @Test
    fun rejectsCorruptDatabase() {
        val root = backup()
        File(root, "database/vayana.db").writeText("not a sqlite database")
        assertFails { validateStagedBackup(context, root, DATABASE_VERSION) }
    }

    @Test
    fun rejectsWrongSchemaEvenWithValidRoomIdentity() {
        val root = backup()
        open(root).use { it.execSQL("DROP TABLE annotations") }
        assertFails { validateStagedBackup(context, root, DATABASE_VERSION) }
    }

    @Test
    fun rejectsManifestVersionMismatch() {
        val root = backup()
        assertFails { validateStagedBackup(context, root, 1) }
    }

    @Test
    fun rejectsMissingBookAndCoverFiles() {
        val missingBook = backup()
        File(missingBook, "books/book.epub").delete()
        assertFails { validateStagedBackup(context, missingBook, DATABASE_VERSION) }
        val missingCover = backup()
        open(missingCover).use { it.execSQL("UPDATE books SET coverPath = 'covers/missing.jpg'") }
        assertFails { validateStagedBackup(context, missingCover, DATABASE_VERSION) }
    }

    @Test
    fun rejectsPathsOutsideTheStagedBookDirectory() {
        val root = backup()
        File(root, "outside.epub").writeText("book")
        open(root).use { it.execSQL("UPDATE books SET filePath = 'books/../outside.epub'") }
        assertFails { validateStagedBackup(context, root, DATABASE_VERSION) }
        assertTrue(File(root, "outside.epub").isFile)
    }

    @Test
    fun physicalBooksDoNotRequireAnEpub() {
        val root = backup()
        open(root).use { it.execSQL("UPDATE books SET format = 'PHYSICAL', filePath = ''") }
        File(root, "books/book.epub").delete()
        validateStagedBackup(context, root, DATABASE_VERSION)
    }

    @Test
    fun rejectsBrokenForeignKeys() {
        val root = backup()
        open(root).use {
            it.execSQL("INSERT INTO reading_sessions (bookId, startedAt, endedAt, durationSeconds) VALUES (999, 0, 1000, 1)")
        }
        assertFails { validateStagedBackup(context, root, DATABASE_VERSION) }
    }

    private fun backup(version: Int = DATABASE_VERSION): File {
        val root = temp.newFolder()
        File(root, "database").mkdirs()
        File(root, "books").mkdirs()
        File(root, "books/book.epub").writeText("book bytes")
        val schema = JSONObject(File(System.getProperty("vayana.test.schemas"), "$version.json").readText())
            .getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(File(root, "database/vayana.db"), null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = version
            val timeColumn = if (version >= 5) ", totalReadingSeconds" else ""
            val timeValue = if (version >= 5) ", 0" else ""
            db.execSQL("""
                INSERT INTO books (id, title, filePath, format, fileHash, readingPercent, rating, isDeleted, createdAt, updatedAt$timeColumn)
                VALUES (1, 'Test book', 'books/book.epub', 'EPUB', 'hash', 0, 0, 0, 0, 0$timeValue)
            """.trimIndent())
        }
        return root
    }

    private fun open(root: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(File(root, "database/vayana.db").path, null, SQLiteDatabase.OPEN_READWRITE)
}
