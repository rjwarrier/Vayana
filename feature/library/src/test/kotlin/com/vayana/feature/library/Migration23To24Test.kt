package com.vayana.feature.library

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import com.vayana.core.database.MIGRATION_23_24
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The schedule table moves from annotation ids to sync ids without losing what the reader has already reviewed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Migration23To24Test {
    @Test
    fun schedulesFollowTheirAnnotationsSyncIdAndOrphansAreDropped() {
        val connection = AndroidSQLiteDriver().open(":memory:")
        connection.execSQL("CREATE TABLE `annotations` (`id` INTEGER PRIMARY KEY NOT NULL, `syncId` TEXT NOT NULL)")
        connection.execSQL(
            "CREATE TABLE `highlight_reviews` (`annotationId` INTEGER NOT NULL, `dueAt` INTEGER NOT NULL, " +
                "`intervalDays` INTEGER NOT NULL DEFAULT 0, `easeFactor` REAL NOT NULL DEFAULT 2.5, " +
                "`repetitions` INTEGER NOT NULL DEFAULT 0, `lastReviewedAt` INTEGER NOT NULL, PRIMARY KEY(`annotationId`))",
        )
        connection.execSQL("CREATE INDEX `index_highlight_reviews_dueAt` ON `highlight_reviews` (`dueAt`)")
        connection.execSQL("INSERT INTO annotations VALUES (1, 'annotation-a'), (2, 'annotation-b')")
        connection.execSQL("INSERT INTO highlight_reviews VALUES (1, 1000, 3, 2.5, 2, 500), (2, 2000, 1, 2.3, 1, 600), (99, 3000, 9, 2.5, 4, 700)")

        MIGRATION_23_24.migrate(connection)

        assertEquals(
            listOf("annotation-a|1000|3|2.5|2|500", "annotation-b|2000|1|2.3|1|600"),
            connection.rows("SELECT annotationSyncId, dueAt, intervalDays, easeFactor, repetitions, lastReviewedAt FROM highlight_reviews ORDER BY annotationSyncId"),
        )
        assertEquals(listOf("index_highlight_reviews_dueAt"), connection.rows("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'highlight_reviews' AND name NOT LIKE 'sqlite_autoindex%'"))
        assertEquals(listOf("highlight_reviews"), connection.rows("SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'highlight_reviews%'"))
    }

    private fun SQLiteConnection.rows(sql: String): List<String> = prepare(sql).use { statement ->
        buildList {
            while (statement.step()) {
                add((0 until statement.getColumnCount()).joinToString("|") { statement.getText(it) })
            }
        }
    }
}
