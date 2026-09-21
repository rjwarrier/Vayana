package com.vayana.feature.library

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import com.vayana.core.database.MIGRATION_24_25
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Only tag lists carrying the stray "null" tag are rewritten; everything else is left exactly as stored. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Migration24To25Test {
    @Test
    fun strayNullTagsAreRemoved() {
        val connection = AndroidSQLiteDriver().open(":memory:")
        connection.execSQL("CREATE TABLE `books` (`id` INTEGER PRIMARY KEY NOT NULL, `tagsCsv` TEXT)")
        connection.execSQL(
            "INSERT INTO books VALUES (1, 'null,Mystery,Science Fiction'), (2, 'null'), (3, 'Fiction, NULL'), " +
                "(4, 'Nullification,classic'), (5, NULL), (6, 'Fantasy')",
        )

        MIGRATION_24_25.migrate(connection)

        assertEquals(
            listOf("1|Mystery, Science Fiction", "2|<null>", "3|Fiction", "4|Nullification,classic", "5|<null>", "6|Fantasy"),
            connection.rows("SELECT id, tagsCsv FROM books ORDER BY id"),
        )
    }

    private fun SQLiteConnection.rows(sql: String): List<String> = prepare(sql).use { statement ->
        buildList {
            while (statement.step()) {
                add((0 until statement.getColumnCount()).joinToString("|") { if (statement.isNull(it)) "<null>" else statement.getText(it) })
            }
        }
    }
}
