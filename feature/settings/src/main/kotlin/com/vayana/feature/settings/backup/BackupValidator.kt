package com.vayana.feature.settings.backup

import com.vayana.core.resources.failUnless
import com.vayana.core.resources.uiText
import com.vayana.core.resources.LocalizedException
import com.vayana.core.resources.UiText
import com.vayana.core.resources.R
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import com.vayana.core.database.ALL_MIGRATIONS
import com.vayana.core.database.DATABASE_VERSION
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.model.BookFormat
import java.io.File

/** Validates and migrates only the staged copy, before any live data is closed or moved. */
internal fun validateStagedBackup(context: Context, stagingDir: File, declaredVersion: Int) {
    val stagedDb = File(stagingDir, "database/vayana.db")
    SQLiteDatabase.openDatabase(stagedDb.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
        failUnless(
            db.version in 1..DATABASE_VERSION && db.version == declaredVersion,
            R.string.settings_error_backup_database_version_does_not_match,
        )
        db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            failUnless(
                cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext(),
                R.string.settings_error_backup_database_damaged,
            )
        }
        // Force Room to inspect the actual schema, even if an invalid database contains
        // a copied identity hash. Older versions are migrated with the app's migrations.
        db.execSQL("DROP TABLE IF EXISTS room_master_table")
    }

    val validationDb = Room.databaseBuilder(context, VayanaDatabase::class.java, stagedDb.absolutePath)
        .addMigrations(*ALL_MIGRATIONS)
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .build()
    try {
        val db = validationDb.openHelper.writableDatabase
        db.query("PRAGMA foreign_key_check").use { cursor ->
            failUnless(!cursor.moveToFirst(), R.string.settings_error_backup_database_contains_broken_references)
        }
        db.query("SELECT filePath, coverPath, format, fileAvailability FROM books WHERE isDeleted = 0").use { cursor ->
            while (cursor.moveToNext()) {
                val format = BookFormat.valueOf(cursor.getString(2))
                val fileAvailability = cursor.getString(3)
                if (!format.isOffline && fileAvailability == "LOCAL") {
                    requireStagedFile(stagingDir, cursor.getString(0), "books")
                }
                if (!cursor.isNull(1)) requireStagedFile(stagingDir, cursor.getString(1), "covers")
            }
        }
    } finally {
        // TRUNCATE and close leave the migrated database self-contained for copyTo.
        validationDb.close()
    }
}

private fun requireStagedFile(stagingDir: File, relativePath: String, directory: String) {
    val root = File(stagingDir, directory).canonicalFile
    val file = File(stagingDir, relativePath).canonicalFile
    failUnless(
        file.path.startsWith(root.path + File.separator) && file.isFile && file.length() > 0L,
        R.string.settings_error_backup_missing_file,
        directory,
        relativePath,
    )
}
