package com.vayana.feature.library

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import com.vayana.core.database.MIGRATION_26_27
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Migration26To27Test {
    @Test
    fun addsNullableGutenbergIdentityWithoutChangingExistingBooks() {
        val connection = AndroidSQLiteDriver().open(":memory:")
        connection.execSQL("CREATE TABLE `books` (`id` INTEGER PRIMARY KEY NOT NULL, `title` TEXT NOT NULL)")
        connection.execSQL("INSERT INTO books VALUES (1, 'Pride and Prejudice')")

        MIGRATION_26_27.migrate(connection)

        val values = connection.prepare("SELECT id, title, gutenbergId FROM books").use { statement ->
            check(statement.step())
            listOf(statement.getLong(0).toString(), statement.getText(1), statement.isNull(2).toString())
        }
        assertEquals(listOf("1", "Pride and Prejudice", "true"), values)
    }
}
