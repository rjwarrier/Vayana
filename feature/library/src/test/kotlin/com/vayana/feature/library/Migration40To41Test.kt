package com.vayana.feature.library

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import com.vayana.core.database.MIGRATION_40_41
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Migration40To41Test {
    @Test
    fun existingSessionsHaveNoListeningTime() {
        val connection = AndroidSQLiteDriver().open(":memory:")
        connection.execSQL("CREATE TABLE `reading_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `syncId` TEXT NOT NULL)")
        connection.execSQL("INSERT INTO reading_sessions (syncId) VALUES ('old')")

        MIGRATION_40_41.migrate(connection)

        val listened = connection.prepare("SELECT listenedSeconds FROM reading_sessions").use { statement ->
            statement.step()
            statement.getLong(0)
        }
        assertEquals(0L, listened)
    }
}
