package com.vayana.feature.library

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import com.vayana.core.database.MIGRATION_27_28
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Migration27To28Test {
    @Test
    fun onlyChangesOmittedByProgressSyncRecommendAFullSync() {
        val connection = AndroidSQLiteDriver().open(":memory:")
        connection.createTrackedTables()

        MIGRATION_27_28.migrate(connection)

        assertFalse(connection.fullSyncRequired())

        connection.execSQL("INSERT INTO books (id, title) VALUES (1, 'First title')")
        assertTrue(connection.fullSyncRequired())

        connection.clearFullSyncRecommendation()
        connection.execSQL(
            "UPDATE books SET lastLocator = 'epubcfi(/6/2)', readingPercent = 0.25, updatedAt = 10, " +
                "lastReadAt = 10, startedReadingAt = 5, totalReadingSeconds = 60, " +
                "readNextAddedAt = 7, readNextUpdatedAt = 10, deletionUpdatedAt = 10, isDeleted = 0 WHERE id = 1",
        )
        assertFalse(connection.fullSyncRequired(), "reading progress and progress-only metadata must stay quiet")

        connection.execSQL("UPDATE books SET title = 'Edited title' WHERE id = 1")
        assertTrue(connection.fullSyncRequired())

        listOf("annotations", "shelves", "book_shelf_cross_ref", "vocabulary_cards").forEachIndexed { index, table ->
            connection.clearFullSyncRecommendation()
            connection.execSQL("INSERT INTO `$table` (id) VALUES (${index + 1})")
            assertTrue(connection.fullSyncRequired(), "$table changes must recommend a full sync")
        }
    }

    private fun SQLiteConnection.createTrackedTables() {
        execSQL(
            """
            CREATE TABLE `books` (
                `id` INTEGER PRIMARY KEY NOT NULL,
                `syncId` TEXT, `title` TEXT, `author` TEXT, `series` TEXT, `seriesNumber` TEXT,
                `description` TEXT, `tagsCsv` TEXT, `coverPath` TEXT, `filePath` TEXT,
                `fileAvailability` TEXT, `format` TEXT, `fileHash` TEXT, `fileAssetId` TEXT,
                `fileAssetSha256` TEXT, `fileAssetSizeBytes` INTEGER, `fileAssetUploadedAt` INTEGER,
                `coverAssetId` TEXT, `coverAssetSha256` TEXT, `coverAssetSizeBytes` INTEGER,
                `coverAssetUploadedAt` INTEGER, `rating` INTEGER, `groupId` TEXT, `wordCount` INTEGER,
                `pageEstimate` INTEGER, `createdAt` INTEGER, `customFontSizePercent` INTEGER,
                `customLineHeight` REAL, `customFontFamily` TEXT, `customSideMarginPercent` INTEGER,
                `goodreadsUrl` TEXT, `goodreadsRating` REAL, `goodreadsRatingsCount` INTEGER,
                `originalPublicationYear` INTEGER, `customCoverPath` TEXT, `goodreadsCoverPath` TEXT,
                `physicalOwnership` TEXT, `borrowReturnAt` INTEGER, `gutenbergId` INTEGER,
                `lastLocator` TEXT, `readingPercent` REAL, `updatedAt` INTEGER, `lastReadAt` INTEGER,
                `startedReadingAt` INTEGER, `finishedReadingAt` INTEGER, `totalReadingSeconds` INTEGER,
                `readNextAddedAt` INTEGER, `readNextUpdatedAt` INTEGER, `deletionUpdatedAt` INTEGER,
                `isDeleted` INTEGER
            )
            """.trimIndent(),
        )
        execSQL("CREATE TABLE `annotations` (`id` INTEGER PRIMARY KEY NOT NULL)")
        execSQL("CREATE TABLE `shelves` (`id` INTEGER PRIMARY KEY NOT NULL)")
        execSQL("CREATE TABLE `book_shelf_cross_ref` (`id` INTEGER PRIMARY KEY NOT NULL)")
        execSQL("CREATE TABLE `vocabulary_cards` (`id` INTEGER PRIMARY KEY NOT NULL)")
    }

    private fun SQLiteConnection.fullSyncRequired(): Boolean = prepare(
        "SELECT EXISTS(SELECT 1 FROM full_sync_state WHERE id = 0 AND required = 1)",
    ).use { statement ->
        statement.step()
        statement.getLong(0) == 1L
    }

    private fun SQLiteConnection.clearFullSyncRecommendation() {
        execSQL("INSERT OR REPLACE INTO full_sync_state(id, required) VALUES (0, 0)")
    }
}
