package com.vayana.core.database.search

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Keeps `books_fts` in step with `books`. Room's content-sync triggers re-indexed a book on every `books` update,
 * including each reading-position save; these fire only when the searchable text actually changes.
 */
internal object BookSearchIndex {
    private const val Columns = "`title`, `author`, `series`, `seriesNumber`, `tagsCsv`, `description`"
    private const val NewValues = "NEW.`title`, NEW.`author`, NEW.`series`, NEW.`seriesNumber`, NEW.`tagsCsv`, NEW.`description`"
    private const val TextChanged = "OLD.`title` IS NOT NEW.`title` OR OLD.`author` IS NOT NEW.`author` OR " +
        "OLD.`series` IS NOT NEW.`series` OR OLD.`seriesNumber` IS NOT NEW.`seriesNumber` OR " +
        "OLD.`tagsCsv` IS NOT NEW.`tagsCsv` OR OLD.`description` IS NOT NEW.`description`"

    /** Must match Room's generated `createSql` for BookFtsEntity (schemas/19.json). */
    const val CreateTable = "CREATE VIRTUAL TABLE IF NOT EXISTS `books_fts` USING FTS4(`title` TEXT NOT NULL, " +
        "`author` TEXT, `series` TEXT, `seriesNumber` TEXT, `tagsCsv` TEXT, `description` TEXT, tokenize=unicode61)"

    /** Indexes every existing book into an empty `books_fts`. */
    const val Backfill = "INSERT INTO `books_fts`(`docid`, $Columns) SELECT `id`, $Columns FROM `books`"

    private val triggers = listOf(
        "CREATE TRIGGER IF NOT EXISTS `books_fts_after_insert` AFTER INSERT ON `books` BEGIN " +
            "INSERT INTO `books_fts`(`docid`, $Columns) VALUES (NEW.`id`, $NewValues); END",
        "CREATE TRIGGER IF NOT EXISTS `books_fts_after_update` AFTER UPDATE ON `books` WHEN $TextChanged BEGIN " +
            "DELETE FROM `books_fts` WHERE `docid` = OLD.`id`; " +
            "INSERT INTO `books_fts`(`docid`, $Columns) VALUES (NEW.`id`, $NewValues); END",
        "CREATE TRIGGER IF NOT EXISTS `books_fts_after_delete` AFTER DELETE ON `books` BEGIN " +
            "DELETE FROM `books_fts` WHERE `docid` = OLD.`id`; END",
    )

    fun createTriggers(connection: SQLiteConnection) {
        triggers.forEach { sql -> connection.execSQL(sql) }
    }
}

/** On a fresh install Room creates `books_fts` from the entity but knows nothing of its triggers. */
internal object BookSearchIndexCallback : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        BookSearchIndex.createTriggers(connection)
    }
}
