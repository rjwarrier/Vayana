package com.vayana.feature.library

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.vayana.core.database.ALL_MIGRATIONS
import com.vayana.core.database.VayanaDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PhysicalTimerMigrationTest {
    @Test
    fun upgradePreservesExistingSessionsAndRoomValidatesSchema() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "physical-timer-migration.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema = JSONObject(java.io.File(System.getProperty("vayana.test.schemas"), "31.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            old.execSQL("INSERT INTO books (id,syncId,title,filePath,fileAvailability,format,fileHash,readingPercent,rating,isDeleted,createdAt,updatedAt,totalReadingSeconds) VALUES (1,'book','Paper','','LOCAL','PHYSICAL','paper',0,0,0,1000,61000,60)")
            old.execSQL("INSERT INTO reading_sessions (id,syncId,bookId,startedAt,endedAt,durationSeconds) VALUES (7,'old-session',1,1000,61000,60)")
            old.version = 31
        }
        val db = Room.databaseBuilder(context, VayanaDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries().build()
        try {
            val log = db.readingSessionDao().getAllForSync().single()
            assertEquals(7L, log.id)
            assertEquals(60L, log.durationSeconds)
            assertNull(log.startPage)
            assertNull(log.endPage)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
