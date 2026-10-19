package com.vayana.feature.library

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import com.vayana.core.database.MIGRATION_39_40
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Migration39To40Test {
    @Test
    fun existingSessionsKeepNoDeviceAndNewOnesCanNameOne() {
        val connection = AndroidSQLiteDriver().open(":memory:")
        connection.execSQL("CREATE TABLE `reading_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `syncId` TEXT NOT NULL)")
        connection.execSQL("INSERT INTO reading_sessions (syncId) VALUES ('old')")

        MIGRATION_39_40.migrate(connection)
        connection.execSQL("INSERT INTO reading_sessions (syncId, deviceName) VALUES ('new', 'Pixel')")

        val devices = connection.prepare("SELECT syncId, deviceName FROM reading_sessions ORDER BY id").use { statement ->
            buildList { while (statement.step()) add(statement.getText(0) to if (statement.isNull(1)) null else statement.getText(1)) }
        }
        assertEquals(listOf("old" to null, "new" to "Pixel"), devices)
    }
}
