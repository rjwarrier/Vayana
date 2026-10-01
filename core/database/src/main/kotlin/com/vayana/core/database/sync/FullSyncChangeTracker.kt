package com.vayana.core.database.sync

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Tracks changes omitted by reading-progress-only sync. Keeping this at the database boundary means imports,
 * reader annotations, shelf edits and sync merges cannot accidentally bypass the recommendation state.
 */
object FullSyncChangeTracker {
    const val CreateTable = "CREATE TABLE IF NOT EXISTS `full_sync_state` " +
        "(`id` INTEGER NOT NULL, `required` INTEGER NOT NULL, PRIMARY KEY(`id`))"

    // Room writes entities with INSERT/UPDATE OR ABORT. SQLite propagates that outer conflict policy into trigger
    // statements, so INSERT OR REPLACE here becomes ABORT and crashes once the singleton row already exists.
    // Updating first and conditionally inserting avoids a conflict under every outer write policy and Android SQLite.
    private const val MarkRequired =
        "UPDATE `full_sync_state` SET `required` = 1 WHERE `id` = 0; " +
            "INSERT INTO `full_sync_state` (`id`, `required`) SELECT 0, 1 " +
            "WHERE NOT EXISTS (SELECT 1 FROM `full_sync_state` WHERE `id` = 0);"

    private val BookMetadataChanged = listOf(
        "syncId", "title", "author", "series", "seriesNumber", "description", "tagsCsv",
        "coverPath", "filePath", "fileAvailability", "format", "fileHash",
        "fileAssetId", "fileAssetSha256", "fileAssetSizeBytes", "fileAssetUploadedAt",
        "coverAssetId", "coverAssetSha256", "coverAssetSizeBytes", "coverAssetUploadedAt",
        "rating", "groupId", "wordCount", "pageEstimate", "createdAt",
        "customFontSizePercent", "customLineHeight", "customFontFamily", "customSideMarginPercent",
        "goodreadsUrl", "goodreadsRating", "goodreadsRatingsCount", "originalPublicationYear",
        "customCoverPath", "goodreadsCoverPath", "physicalOwnership", "borrowReturnAt", "gutenbergId",
    ).joinToString(" OR ") { column -> "OLD.`$column` IS NOT NEW.`$column`" }

    private val triggers = listOf(
        "CREATE TRIGGER IF NOT EXISTS `full_sync_books_insert` AFTER INSERT ON `books` BEGIN $MarkRequired END",
        "CREATE TRIGGER IF NOT EXISTS `full_sync_books_metadata_update` AFTER UPDATE ON `books` " +
            "WHEN $BookMetadataChanged BEGIN $MarkRequired END",
        trackedTableTrigger("annotations", "insert"),
        trackedTableTrigger("annotations", "update"),
        trackedTableTrigger("annotations", "delete"),
        trackedTableTrigger("shelves", "insert"),
        trackedTableTrigger("shelves", "update"),
        trackedTableTrigger("shelves", "delete"),
        trackedTableTrigger("book_shelf_cross_ref", "insert"),
        trackedTableTrigger("book_shelf_cross_ref", "update"),
        trackedTableTrigger("book_shelf_cross_ref", "delete"),
        trackedTableTrigger("vocabulary_cards", "insert"),
        trackedTableTrigger("vocabulary_cards", "update"),
        trackedTableTrigger("vocabulary_cards", "delete"),
    )

    fun create(connection: SQLiteConnection) {
        connection.execSQL(CreateTable)
        triggers.forEach(connection::execSQL)
    }

    fun recreate(connection: SQLiteConnection) {
        triggerNames.forEach { name -> connection.execSQL("DROP TRIGGER IF EXISTS `$name`") }
        create(connection)
    }

    private val triggerNames = buildList {
        add("full_sync_books_insert")
        add("full_sync_books_metadata_update")
        listOf("annotations", "shelves", "book_shelf_cross_ref", "vocabulary_cards").forEach { table ->
            listOf("insert", "update", "delete").forEach { operation ->
                add("full_sync_${table}_${operation}")
            }
        }
    }

    private fun trackedTableTrigger(table: String, operation: String): String =
        "CREATE TRIGGER IF NOT EXISTS `full_sync_${table}_${operation}` AFTER ${operation.uppercase()} " +
            "ON `$table` BEGIN $MarkRequired END"
}

/** Room creates tables on a fresh install, while this callback installs their non-Room triggers. */
object FullSyncChangeTrackerCallback : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        FullSyncChangeTracker.create(connection)
    }
}
