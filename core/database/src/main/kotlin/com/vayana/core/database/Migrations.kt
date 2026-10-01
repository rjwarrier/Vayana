package com.vayana.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.vayana.core.database.model.hasNullBookTag
import com.vayana.core.database.model.normalizedBookTagsCsv
import com.vayana.core.database.search.BookSearchIndex
import com.vayana.core.database.sync.FullSyncChangeTracker

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `annotations` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `bookId` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `colorKey` TEXT NOT NULL,
                `locator` TEXT NOT NULL,
                `chapterTitle` TEXT,
                `chapterHref` TEXT,
                `selectedText` TEXT NOT NULL,
                `readerNote` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_annotations_bookId` ON `annotations` (`bookId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_annotations_type` ON `annotations` (`type`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_annotations_colorKey` ON `annotations` (`colorKey`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_annotations_updatedAt` ON `annotations` (`updatedAt`)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `series` TEXT")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `seriesNumber` TEXT")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `startedReadingAt` INTEGER")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `finishedReadingAt` INTEGER")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `totalReadingSeconds` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `word_lookup_stats` (
                `word` TEXT NOT NULL PRIMARY KEY,
                `count` INTEGER NOT NULL,
                `lastLookedUpAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `annotations` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reading_sessions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `bookId` INTEGER NOT NULL,
                `startedAt` INTEGER NOT NULL,
                `endedAt` INTEGER NOT NULL,
                `durationSeconds` INTEGER NOT NULL,
                FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_bookId` ON `reading_sessions` (`bookId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_startedAt` ON `reading_sessions` (`startedAt`)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `customFontSizePercent` INTEGER")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `customLineHeight` REAL")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `customFontFamily` TEXT")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `customSideMarginPercent` INTEGER")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `readNextAddedAt` INTEGER")

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shelves` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `book_shelf_cross_ref` (
                `bookId` INTEGER NOT NULL,
                `shelfId` INTEGER NOT NULL,
                PRIMARY KEY(`bookId`, `shelfId`),
                FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`shelfId`) REFERENCES `shelves`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_book_shelf_cross_ref_bookId` ON `book_shelf_cross_ref` (`bookId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_book_shelf_cross_ref_shelfId` ON `book_shelf_cross_ref` (`shelfId`)")

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `vocabulary_cards` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `word` TEXT NOT NULL,
                `definition` TEXT NOT NULL,
                `sentence` TEXT,
                `bookId` INTEGER,
                `bookTitle` TEXT,
                `createdAt` INTEGER NOT NULL,
                `lastReviewedAt` INTEGER,
                `known` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_vocabulary_cards_bookId` ON `vocabulary_cards` (`bookId`)")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `syncId` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("UPDATE `books` SET `syncId` = 'book-' || lower(hex(randomblob(16))) WHERE `syncId` = ''")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_syncId` ON `books` (`syncId`)")

        connection.execSQL("ALTER TABLE `annotations` ADD COLUMN `syncId` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("UPDATE `annotations` SET `syncId` = 'annotation-' || lower(hex(randomblob(16))) WHERE `syncId` = ''")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_annotations_syncId` ON `annotations` (`syncId`)")

        connection.execSQL("ALTER TABLE `reading_sessions` ADD COLUMN `syncId` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("UPDATE `reading_sessions` SET `syncId` = 'session-' || lower(hex(randomblob(16))) WHERE `syncId` = ''")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reading_sessions_syncId` ON `reading_sessions` (`syncId`)")

        connection.execSQL("ALTER TABLE `shelves` ADD COLUMN `syncId` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("UPDATE `shelves` SET `syncId` = 'shelf-' || lower(hex(randomblob(16))) WHERE `syncId` = ''")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_shelves_syncId` ON `shelves` (`syncId`)")

        connection.execSQL("ALTER TABLE `book_shelf_cross_ref` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL(
            """
            UPDATE `book_shelf_cross_ref`
            SET `createdAt` = (
                SELECT max(`books`.`createdAt`, `shelves`.`createdAt`)
                FROM `books`, `shelves`
                WHERE `books`.`id` = `book_shelf_cross_ref`.`bookId`
                    AND `shelves`.`id` = `book_shelf_cross_ref`.`shelfId`
            )
            WHERE `createdAt` = 0
            """.trimIndent(),
        )

        connection.execSQL("ALTER TABLE `vocabulary_cards` ADD COLUMN `syncId` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("UPDATE `vocabulary_cards` SET `syncId` = 'vocabulary-' || lower(hex(randomblob(16))) WHERE `syncId` = ''")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vocabulary_cards_syncId` ON `vocabulary_cards` (`syncId`)")

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `word_lookup_stats_new` (
                `word` TEXT NOT NULL,
                `count` INTEGER NOT NULL,
                `lastLookedUpAt` INTEGER NOT NULL,
                `writerOrigin` TEXT NOT NULL DEFAULT 'legacy-local',
                PRIMARY KEY(`word`, `writerOrigin`)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO `word_lookup_stats_new` (`word`, `count`, `lastLookedUpAt`, `writerOrigin`)
            SELECT `word`, `count`, `lastLookedUpAt`, 'legacy-local' FROM `word_lookup_stats`
            """.trimIndent(),
        )
        connection.execSQL("DROP TABLE `word_lookup_stats`")
        connection.execSQL("ALTER TABLE `word_lookup_stats_new` RENAME TO `word_lookup_stats`")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `fileAvailability` TEXT NOT NULL DEFAULT 'LOCAL'")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `fileAssetId` TEXT")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `fileAssetSha256` TEXT")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `fileAssetSizeBytes` INTEGER")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `fileAssetUploadedAt` INTEGER")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `coverAssetId` TEXT")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `coverAssetSha256` TEXT")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `coverAssetSizeBytes` INTEGER")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `coverAssetUploadedAt` INTEGER")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `tagsCsv` TEXT")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `book_aliases` (
                `syncId` TEXT NOT NULL PRIMARY KEY,
                `fileHash` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_book_aliases_fileHash` ON `book_aliases` (`fileHash`)")

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tombstones` (
                `syncId` TEXT NOT NULL PRIMARY KEY,
                `entityType` TEXT NOT NULL,
                `deletedAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_tombstones_entityType` ON `tombstones` (`entityType`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_tombstones_deletedAt` ON `tombstones` (`deletedAt`)")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(connection: SQLiteConnection) {
        // findByHash/findActiveBySyncIdOrHash run on every import dedupe check and every sync
        // merge; without this index each of those was a full table scan.
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_books_fileHash` ON `books` (`fileHash`)")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(connection: SQLiteConnection) {
        // Goodreads import: local-only extras, plus both covers kept side by side so the user can switch.
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `goodreadsUrl` TEXT")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `goodreadsRating` REAL")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `goodreadsRatingsCount` INTEGER")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `originalPublicationYear` INTEGER")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `customCoverPath` TEXT")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `goodreadsCoverPath` TEXT")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(connection: SQLiteConnection) {
        // Data repair, no schema change: Android's org.json read synced JSON nulls as the text "null", which
        // then got stored (and synced onward) as a real series/tags/position/etc. Clear those back to NULL.
        val nullableSyncedText = mapOf(
            "books" to listOf(
                "author", "series", "seriesNumber", "description", "lastLocator",
                "customFontFamily", "tagsCsv", "goodreadsUrl",
            ),
            "annotations" to listOf("chapterTitle", "chapterHref", "readerNote"),
            "vocabulary_cards" to listOf("sentence", "bookTitle"),
        )
        nullableSyncedText.forEach { (table, columns) ->
            columns.forEach { column ->
                connection.execSQL("UPDATE `$table` SET `$column` = NULL WHERE `$column` = 'null'")
            }
        }
        connection.execSQL("UPDATE `annotations` SET `selectedText` = '' WHERE `selectedText` = 'null'")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `readNextUpdatedAt` INTEGER")
        connection.execSQL("UPDATE `books` SET `readNextUpdatedAt` = `readNextAddedAt` WHERE `readNextAddedAt` IS NOT NULL")
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(connection: SQLiteConnection) {
        // Full-text search. The table and trigger SQL must match what Room generates for BookFtsEntity and
        // AnnotationFtsEntity (schemas/18.json); 'rebuild' then indexes the rows that already exist.
        connection.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `books_fts` USING FTS4(`title` TEXT NOT NULL, `author` TEXT, " +
                "`series` TEXT, `seriesNumber` TEXT, `tagsCsv` TEXT, `description` TEXT, tokenize=unicode61, content=`books`)",
        )
        connection.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_books_fts_BEFORE_UPDATE BEFORE UPDATE ON `books` " +
                "BEGIN DELETE FROM `books_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        connection.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_books_fts_BEFORE_DELETE BEFORE DELETE ON `books` " +
                "BEGIN DELETE FROM `books_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        connection.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_books_fts_AFTER_UPDATE AFTER UPDATE ON `books` " +
                "BEGIN INSERT INTO `books_fts`(`docid`, `title`, `author`, `series`, `seriesNumber`, `tagsCsv`, `description`) " +
                "VALUES (NEW.`rowid`, NEW.`title`, NEW.`author`, NEW.`series`, NEW.`seriesNumber`, NEW.`tagsCsv`, NEW.`description`); END",
        )
        connection.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_books_fts_AFTER_INSERT AFTER INSERT ON `books` " +
                "BEGIN INSERT INTO `books_fts`(`docid`, `title`, `author`, `series`, `seriesNumber`, `tagsCsv`, `description`) " +
                "VALUES (NEW.`rowid`, NEW.`title`, NEW.`author`, NEW.`series`, NEW.`seriesNumber`, NEW.`tagsCsv`, NEW.`description`); END",
        )
        connection.execSQL("INSERT INTO `books_fts`(`books_fts`) VALUES('rebuild')")

        connection.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `annotations_fts` USING FTS4(`selectedText` TEXT NOT NULL, " +
                "`readerNote` TEXT, `chapterTitle` TEXT, tokenize=unicode61, content=`annotations`)",
        )
        connection.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_annotations_fts_BEFORE_UPDATE BEFORE UPDATE ON `annotations` " +
                "BEGIN DELETE FROM `annotations_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        connection.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_annotations_fts_BEFORE_DELETE BEFORE DELETE ON `annotations` " +
                "BEGIN DELETE FROM `annotations_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        connection.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_annotations_fts_AFTER_UPDATE AFTER UPDATE ON `annotations` " +
                "BEGIN INSERT INTO `annotations_fts`(`docid`, `selectedText`, `readerNote`, `chapterTitle`) " +
                "VALUES (NEW.`rowid`, NEW.`selectedText`, NEW.`readerNote`, NEW.`chapterTitle`); END",
        )
        connection.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_annotations_fts_AFTER_INSERT AFTER INSERT ON `annotations` " +
                "BEGIN INSERT INTO `annotations_fts`(`docid`, `selectedText`, `readerNote`, `chapterTitle`) " +
                "VALUES (NEW.`rowid`, NEW.`selectedText`, NEW.`readerNote`, NEW.`chapterTitle`); END",
        )
        connection.execSQL("INSERT INTO `annotations_fts`(`annotations_fts`) VALUES('rebuild')")
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(connection: SQLiteConnection) {
        // books_fts leaves Room's content-sync mode so it is re-indexed only when a book's text changes.
        listOf("BEFORE_UPDATE", "BEFORE_DELETE", "AFTER_UPDATE", "AFTER_INSERT").forEach { event ->
            connection.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_books_fts_$event")
        }
        connection.execSQL("DROP TABLE IF EXISTS `books_fts`")
        connection.execSQL(BookSearchIndex.CreateTable)
        BookSearchIndex.createTriggers(connection)
        connection.execSQL(BookSearchIndex.Backfill)
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(connection: SQLiteConnection) {
        // Cloud assets of permanently deleted books, waiting to be removed from the sync repository.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_cloud_deletions` (`assetId` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`queuedAt` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, `lastError` TEXT, PRIMARY KEY(`assetId`))",
        )
    }
}

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(connection: SQLiteConnection) {
        // Deletes and restores get their own version, so reading a book on another device can't cancel a delete.
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `deletionUpdatedAt` INTEGER")
        connection.execSQL("UPDATE `books` SET `deletionUpdatedAt` = `updatedAt` WHERE `isDeleted` = 1")
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(connection: SQLiteConnection) {
        // Spaced repetition for vocabulary cards. Existing cards start as new (due now); known cards stay known.
        connection.execSQL("ALTER TABLE `vocabulary_cards` ADD COLUMN `dueAt` INTEGER")
        connection.execSQL("ALTER TABLE `vocabulary_cards` ADD COLUMN `intervalDays` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `vocabulary_cards` ADD COLUMN `easeFactor` REAL NOT NULL DEFAULT 2.5")
        connection.execSQL("ALTER TABLE `vocabulary_cards` ADD COLUMN `repetitions` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(connection: SQLiteConnection) {
        // Spaced review of highlights, kept per device (see HighlightReviewEntity).
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `highlight_reviews` (`annotationId` INTEGER NOT NULL, `dueAt` INTEGER NOT NULL, " +
                "`intervalDays` INTEGER NOT NULL DEFAULT 0, `easeFactor` REAL NOT NULL DEFAULT 2.5, " +
                "`repetitions` INTEGER NOT NULL DEFAULT 0, `lastReviewedAt` INTEGER NOT NULL, PRIMARY KEY(`annotationId`))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_highlight_reviews_dueAt` ON `highlight_reviews` (`dueAt`)")
    }
}

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(connection: SQLiteConnection) {
        // Review schedules move from the local annotation id to the annotation's sync id; rows whose annotation is
        // gone are dropped by the join.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `highlight_reviews_new` (`annotationSyncId` TEXT NOT NULL, `dueAt` INTEGER NOT NULL, " +
                "`intervalDays` INTEGER NOT NULL DEFAULT 0, `easeFactor` REAL NOT NULL DEFAULT 2.5, " +
                "`repetitions` INTEGER NOT NULL DEFAULT 0, `lastReviewedAt` INTEGER NOT NULL, PRIMARY KEY(`annotationSyncId`))",
        )
        connection.execSQL(
            "INSERT OR IGNORE INTO `highlight_reviews_new` " +
                "(`annotationSyncId`, `dueAt`, `intervalDays`, `easeFactor`, `repetitions`, `lastReviewedAt`) " +
                "SELECT a.`syncId`, r.`dueAt`, r.`intervalDays`, r.`easeFactor`, r.`repetitions`, r.`lastReviewedAt` " +
                "FROM `highlight_reviews` r JOIN `annotations` a ON a.`id` = r.`annotationId`",
        )
        connection.execSQL("DROP TABLE `highlight_reviews`")
        connection.execSQL("ALTER TABLE `highlight_reviews_new` RENAME TO `highlight_reviews`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_highlight_reviews_dueAt` ON `highlight_reviews` (`dueAt`)")
    }
}

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(connection: SQLiteConnection) {
        // Earlier builds could store the text "null" as a tag (a JSON null read back as text, then merged with
        // Goodreads genres); rewrite just those tag lists through the normaliser, which now drops it.
        val repairs = connection.prepare("SELECT `id`, `tagsCsv` FROM `books` WHERE `tagsCsv` LIKE '%null%'").use { statement ->
            buildList {
                while (statement.step()) {
                    val tagsCsv = statement.getText(1)
                    if (hasNullBookTag(tagsCsv)) add(statement.getLong(0) to tagsCsv.normalizedBookTagsCsv())
                }
            }
        }
        repairs.forEach { (id, tagsCsv) ->
            connection.prepare("UPDATE `books` SET `tagsCsv` = ? WHERE `id` = ?").use { statement ->
                if (tagsCsv == null) statement.bindNull(1) else statement.bindText(1, tagsCsv)
                statement.bindLong(2, id)
                statement.step()
            }
        }
    }
}

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `physicalOwnership` TEXT")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `borrowReturnAt` INTEGER")
        connection.execSQL("UPDATE `books` SET `physicalOwnership` = 'OWNED' WHERE `format` = 'PHYSICAL'")
    }
}

val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(connection: SQLiteConnection) {
        // Nullable keeps every existing/local import valid. New imports are deduplicated in the repository.
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `gutenbergId` INTEGER")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_books_gutenbergId` ON `books` (`gutenbergId`)")
    }
}

val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(connection: SQLiteConnection) {
        // Start clean because an upgraded database has no trustworthy record of which existing
        // changes already reached the cloud. Subsequent full-sync-only writes are tracked exactly.
        FullSyncChangeTracker.create(connection)
    }
}

val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(connection: SQLiteConnection) {
        // Version 28's INSERT OR REPLACE trigger body inherited Room's outer OR ABORT policy and crashed whenever
        // the singleton state row already existed. Rebuild every tracker trigger with conflict-free update/insert SQL.
        FullSyncChangeTracker.recreate(connection)
    }
}

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
    MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
    MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
    MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28,
    MIGRATION_28_29,
)
