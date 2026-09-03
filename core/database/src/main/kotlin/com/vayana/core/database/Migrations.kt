package com.vayana.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

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

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
