package com.vayana.feature.library

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import com.vayana.core.database.MIGRATION_27_28
import com.vayana.core.database.MIGRATION_29_30
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Migration29To30Test {
    private fun upgradedConnection(): SQLiteConnection {
        val connection = AndroidSQLiteDriver().open(":memory:")
        connection.createFullSyncTrackedTables()
        // The tracker as versions 28 and 29 left it: no knowledge of `books.source`.
        MIGRATION_27_28.migrate(connection)
        connection.execSQL("INSERT INTO books (id, title) VALUES (1, 'Existing book')")
        MIGRATION_29_30.migrate(connection)
        connection.clearFullSyncRecommendation()
        return connection
    }

    @Test
    fun existingBooksKeepNoSourceAndNoSyncUuid() {
        val connection = upgradedConnection()

        connection.prepare("SELECT syncUuid, source, sourceUpdatedAt, sourceHasCover, sourceMetadata FROM books WHERE id = 1").use { statement ->
            assertTrue(statement.step())
            assertTrue(statement.isNull(0))
            assertTrue(statement.isNull(1))
            assertTrue(statement.isNull(2))
            assertEquals(0L, statement.getLong(3))
            assertTrue(statement.isNull(4))
        }
    }

    @Test
    fun syncUuidIsUniqueButManyBooksMayHaveNone() {
        val connection = upgradedConnection()

        connection.execSQL("INSERT INTO books (id, title) VALUES (2, 'No uuid')")
        connection.execSQL("INSERT INTO books (id, title, syncUuid, source) VALUES (3, 'A', 'u1', 'home_library')")

        assertFailsWith<Exception> {
            connection.execSQL("INSERT INTO books (id, title, syncUuid, source) VALUES (4, 'B', 'u1', 'home_library')")
        }
    }

    @Test
    fun mirroredBooksNeverRecommendAFullSyncButOwnBooksStillDo() {
        val connection = upgradedConnection()

        connection.execSQL("INSERT INTO books (id, title, syncUuid, source) VALUES (5, 'Mirrored', 'u5', 'home_library')")
        connection.execSQL("UPDATE books SET title = 'Retitled' WHERE id = 5")
        assertFalse(connection.fullSyncRequired())

        connection.execSQL("INSERT INTO books (id, title) VALUES (6, 'Own')")
        assertTrue(connection.fullSyncRequired())

        connection.clearFullSyncRecommendation()
        connection.execSQL("UPDATE books SET title = 'Own, edited' WHERE id = 6")
        assertTrue(connection.fullSyncRequired())
    }

    @Test
    fun neverSetSourceStaysNullForOwnRows() {
        val connection = upgradedConnection()
        connection.execSQL("INSERT INTO books (id, title) VALUES (7, 'Own')")

        connection.prepare("SELECT source FROM books WHERE id = 7").use { statement ->
            assertTrue(statement.step())
            assertNull(statement.getTextOrNull(0))
        }
    }

    private fun SQLiteConnection.fullSyncRequired(): Boolean = prepare(
        "SELECT EXISTS(SELECT 1 FROM full_sync_state WHERE id = 0 AND required = 1)",
    ).use { statement ->
        statement.step()
        statement.getLong(0) == 1L
    }

    private fun SQLiteConnection.clearFullSyncRecommendation() {
        execSQL("UPDATE full_sync_state SET required = 0 WHERE id = 0")
    }

    private fun androidx.sqlite.SQLiteStatement.getTextOrNull(index: Int): String? = if (isNull(index)) null else getText(index)
}
