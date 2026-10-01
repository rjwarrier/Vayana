package com.vayana.feature.library

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import com.vayana.core.database.MIGRATION_28_29
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Migration28To29Test {
    @Test
    fun roomConflictPoliciesDoNotCrashFullSyncTracking() {
        val connection = AndroidSQLiteDriver().open(":memory:")
        connection.createFullSyncTrackedTables()
        connection.execSQL(
            "CREATE TABLE full_sync_state (id INTEGER NOT NULL, required INTEGER NOT NULL, PRIMARY KEY(id))",
        )
        connection.execSQL("INSERT INTO full_sync_state (id, required) VALUES (0, 0)")
        connection.execSQL(
            "CREATE TRIGGER full_sync_annotations_insert AFTER INSERT ON annotations BEGIN " +
                "INSERT OR REPLACE INTO full_sync_state (id, required) VALUES (0, 1); END",
        )
        connection.execSQL(
            "CREATE TRIGGER full_sync_annotations_update AFTER UPDATE ON annotations BEGIN " +
                "INSERT OR REPLACE INTO full_sync_state (id, required) VALUES (0, 1); END",
        )

        MIGRATION_28_29.migrate(connection)

        connection.execSQL("INSERT OR ABORT INTO annotations (id, colorKey) VALUES (1, 'yellow')")
        assertTrue(connection.fullSyncRequired())

        connection.execSQL("UPDATE full_sync_state SET required = 0 WHERE id = 0")
        connection.execSQL("UPDATE OR ABORT annotations SET colorKey = 'blue' WHERE id = 1")
        assertTrue(connection.fullSyncRequired())
        assertEquals("blue", connection.annotationColor(1))
    }

    private fun SQLiteConnection.fullSyncRequired(): Boolean = prepare(
        "SELECT required FROM full_sync_state WHERE id = 0",
    ).use { statement ->
        assertTrue(statement.step())
        statement.getLong(0) == 1L
    }

    private fun SQLiteConnection.annotationColor(id: Long): String = prepare(
        "SELECT colorKey FROM annotations WHERE id = ?",
    ).use { statement ->
        statement.bindLong(1, id)
        assertTrue(statement.step())
        statement.getText(0)
    }
}
