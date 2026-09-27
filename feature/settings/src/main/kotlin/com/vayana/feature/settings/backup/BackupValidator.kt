package com.vayana.feature.settings.backup

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
        check(db.version in 1..DATABASE_VERSION && db.version == declaredVersion) {
            "Backup database version does not match its manifest"
        }
        db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext()) {
                "Backup database is damaged"
            }
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
            check(!cursor.moveToFirst()) { "Backup database contains broken references" }
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
    check(file.path.startsWith(root.path + File.separator) && file.isFile && file.length() > 0L) {
        "Backup is missing a valid $directory file: $relativePath"
    }
}
