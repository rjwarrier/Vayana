package com.vayana.feature.library

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.vayana.core.database.ALL_MIGRATIONS
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.sync.FullSyncChangeTrackerCallback
import kotlinx.coroutines.runBlocking
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
class VocabularyIndexMigrationTest {
    @Test
    fun upgradePreservesCardsSchedulesIdsAndUsesIndexes() = verifyUpgrade(keepCard = true)

    @Test
    fun emptyUpgradePreservesDeletedIdHighWaterMark() = verifyUpgrade(keepCard = false)

    private fun verifyUpgrade(keepCard: Boolean) = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "vocabulary-migration-$keepCard.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema = JSONObject(java.io.File(System.getProperty("vayana.test.schemas"), "30.json").readText())
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
            old.execSQL("INSERT INTO vocabulary_cards (id,syncId,word,definition,sentence,bookId,bookTitle,createdAt,lastReviewedAt,known,dueAt,intervalDays,easeFactor,repetitions) VALUES (7,'card','MiXeD','meaning','context',NULL,'Book',100,200,0,300,4,2.1,5)")
            old.execSQL("INSERT INTO vocabulary_cards (id,syncId,word,definition,createdAt,known) VALUES (99,'deleted','gone','gone',0,0)")
            old.execSQL("DELETE FROM vocabulary_cards WHERE id = 99")
            if (!keepCard) old.execSQL("DELETE FROM vocabulary_cards")
            old.execSQL("INSERT INTO full_sync_state VALUES (0,0)")
            old.version = 30
        }
        val db = Room.databaseBuilder(context, VayanaDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .addCallback(FullSyncChangeTrackerCallback)
            .allowMainThreadQueries().build()
        try {
            // Opening through Room validates the migrated schema against the generated entity schema.
            val sql = db.openHelper.writableDatabase
            val cards = db.vocabularyCardDao().getAllForSync()
            assertEquals(if (keepCard) 1 else 0, cards.size)
            if (keepCard) {
                val card = cards.single()
                assertEquals(7L, card.id)
                assertEquals("MiXeD", card.word)
                assertEquals("meaning", card.definition)
                assertEquals("context", card.sentence)
                assertEquals("Book", card.bookTitle)
                assertEquals(100L, card.createdAt)
                assertEquals(200L, card.lastReviewedAt)
                assertEquals(300L, card.dueAt)
                assertEquals(4, card.intervalDays)
                assertEquals(2.1f, card.easeFactor)
                assertEquals(5, card.repetitions)
                assertEquals(card, db.vocabularyCardDao().findByWord("mixed"))
                assertEquals(listOf(card), db.vocabularyCardDao().getForReview(300, 5))
            }
            sql.query("SELECT required FROM full_sync_state WHERE id=0").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            fun plan(query: String): String = sql.query("EXPLAIN QUERY PLAN $query").use { cursor ->
                buildString { while (cursor.moveToNext()) append(cursor.getString(3)) }
            }
            assertTrue(plan("SELECT * FROM vocabulary_cards WHERE word='mixed' COLLATE NOCASE ORDER BY known DESC LIMIT 1").contains("index_vocabulary_cards_word_known"))
            assertTrue(plan("SELECT COUNT(*) FROM vocabulary_cards WHERE known=0 AND (dueAt IS NULL OR dueAt<=300)").contains("index_vocabulary_cards_known_dueAt_createdAt"))
            sql.execSQL("INSERT INTO vocabulary_cards (syncId,word,definition,createdAt,known) VALUES ('new','new','new',0,0)")
            sql.query("SELECT id FROM vocabulary_cards WHERE syncId='new'").use { it.moveToFirst(); assertEquals(100L, it.getLong(0)) }
            sql.query("SELECT required FROM full_sync_state WHERE id=0").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
