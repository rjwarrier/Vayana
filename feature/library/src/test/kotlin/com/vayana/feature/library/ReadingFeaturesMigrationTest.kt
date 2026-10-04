package com.vayana.feature.library

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.vayana.core.database.ALL_MIGRATIONS
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.sync.FullSyncChangeTrackerCallback
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ReadingFeaturesMigrationTest {
    @Test
    fun version32UpgradesAndNewContentIndexWorks() = verifyUpgrade(keepCard = true)

    @Test
    fun repeatedUpgradeFixtureRemainsValid() = verifyUpgrade(keepCard = false)

    private fun verifyUpgrade(keepCard: Boolean) = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "reading-features-migration-$keepCard.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema = JSONObject(java.io.File(System.getProperty("vayana.test.schemas"), "32.json").readText())
            .getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            old.execSQL("INSERT INTO full_sync_state VALUES (0,0)")
            old.version = 32
        }
        val db = Room.databaseBuilder(context, VayanaDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .addCallback(FullSyncChangeTrackerCallback)
            .allowMainThreadQueries().build()
        try {
            // Opening through Room validates the migrated schema against the generated entity schema.
            val sql = db.openHelper.writableDatabase
            sql.query("SELECT readNextPinned, readingDisposition, dispositionReason, dispositionUpdatedAt FROM books LIMIT 0").close()
            sql.query("SELECT reviewQuestion FROM annotations LIMIT 0").close()
            sql.query("SELECT * FROM epub_passages_fts LIMIT 0").close()
            sql.execSQL("INSERT INTO books (syncId,title,filePath,format,fileHash,lastLocator,readingPercent,rating,groupId,isDeleted,wordCount,pageEstimate,createdAt,updatedAt,lastReadAt,author,series,seriesNumber,description,coverPath,fileAvailability,totalReadingSeconds) VALUES ('test','Book','book.epub','EPUB','hash',NULL,0,0,NULL,0,NULL,NULL,100,100,NULL,NULL,NULL,NULL,NULL,NULL,'LOCAL',0)")
            val id = db.bookDao().findBySyncId("test")!!.id
            sql.execSQL("UPDATE full_sync_state SET required=0 WHERE id=0")
            sql.execSQL("UPDATE books SET readingDisposition='PAUSED', dispositionUpdatedAt=200 WHERE id=$id")
            sql.query("SELECT required FROM full_sync_state WHERE id=0").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            val dao = db.epubPassageDao()
            dao.replace(id, listOf(com.vayana.core.database.entity.EpubPassageEntity(bookId=id, fileHash="hash", chapterHref="a", chapterTitle="A", text="migration moonlight")))
            assertEquals(1, dao.search("moon*",10).first().size)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
