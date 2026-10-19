package com.vayana.dictionary.stardict

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import com.vayana.dictionary.api.DictionaryEntry
import com.vayana.dictionary.api.DictionarySense
import com.vayana.dictionary.api.PartOfSpeech
import com.vayana.dictionary.api.foldAccents
import com.vayana.dictionary.api.inflectionStems
import com.vayana.dictionary.api.lookupForms
import java.io.Closeable
import java.io.File
import java.util.Locale

/** One headword's senses, as stored: [key] is the lower-case, accent-folded spelling lookups match on. */
internal data class PackEntry(val key: String, val headword: String, val partOfSpeech: PartOfSpeech, val definitions: List<String>)

internal fun lookupKey(word: String): String = foldAccents(word).lowercase(Locale.ROOT).trim()

/**
 * Every dictionary Vayana builds on the phone (GCIDE, Olam, FreeDict and imported StarDict files) is the same
 * read-only SQLite file: one row per headword entry keyed by [lookupKey], plus a `meta` table naming it, its two
 * languages and its attribution.
 */
internal class SqliteDictionary(file: File, private val fallbackAttribution: String? = null) {
    private val database = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
    private val meta: Map<String, String> = runCatching {
        database.rawQuery("SELECT name, value FROM meta", null).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1).orEmpty()) }
        }
    }.getOrDefault(emptyMap())

    val name: String? get() = meta[MetaName]
    val wordLanguage: String get() = meta[MetaWordLanguage] ?: English
    val meaningLanguage: String get() = meta[MetaMeaningLanguage] ?: wordLanguage
    private val attribution: String get() = meta[MetaAttribution] ?: fallbackAttribution ?: name.orEmpty()

    fun lookup(rawWord: String): DictionaryEntry? {
        // English inflection rules only help English words; other languages are matched as written (and folded).
        val candidates = lookupForms(rawWord)
            .flatMap { form -> if (wordLanguage == English) listOf(form) + inflectionStems(form) else listOf(form) }
            .map(::lookupKey).distinct()
        for (key in candidates) {
            val senses = mutableListOf<DictionarySense>()
            var headword: String? = null
            database.rawQuery("SELECT headword, pos, defs FROM entries WHERE key = ? ORDER BY rowid LIMIT $MaxEntries", arrayOf(key)).use { cursor ->
                while (cursor.moveToNext()) {
                    headword = headword ?: cursor.getString(0)
                    val pos = runCatching { PartOfSpeech.valueOf(cursor.getString(1)) }.getOrDefault(PartOfSpeech.UNKNOWN)
                    cursor.getString(2).split(DefinitionSeparator).forEach { senses += DictionarySense(pos, it) }
                }
            }
            if (senses.isNotEmpty()) {
                return DictionaryEntry(
                    headword = headword ?: key,
                    senses = senses.distinct().take(MaxSenses),
                    attribution = attribution,
                    meaningLanguage = meaningLanguage.takeIf { it != wordLanguage },
                )
            }
        }
        return null
    }

    fun close() = database.close()

    companion object {
        const val English = "eng"
        const val MetaName = "name"
        const val MetaVersion = "version"
        const val MetaWordLanguage = "word_language"
        const val MetaMeaningLanguage = "meaning_language"
        const val MetaAttribution = "attribution"
        const val DefinitionSeparator = "\u001F"
        private const val MaxEntries = 8
        private const val MaxSenses = 16
    }
}

/** Writes a [SqliteDictionary] file: entries in batches, the key index once at the end. */
internal class SqlitePackWriter(destination: File) : Closeable {
    private val database: SQLiteDatabase
    private val insert: SQLiteStatement
    private var pending = 0

    init {
        destination.delete()
        database = SQLiteDatabase.openOrCreateDatabase(destination, null)
        database.execSQL("CREATE TABLE entries (key TEXT NOT NULL, headword TEXT NOT NULL, pos TEXT NOT NULL, defs TEXT NOT NULL)")
        database.execSQL("CREATE TABLE meta (name TEXT PRIMARY KEY, value TEXT)")
        insert = database.compileStatement("INSERT INTO entries VALUES (?, ?, ?, ?)")
        database.beginTransaction()
    }

    fun meta(name: String, value: String) {
        database.execSQL("INSERT OR REPLACE INTO meta VALUES (?, ?)", arrayOf(name, value))
    }

    fun add(entry: PackEntry) {
        if (entry.key.isBlank() || entry.definitions.isEmpty()) return
        insert.bindString(1, entry.key)
        insert.bindString(2, entry.headword)
        insert.bindString(3, entry.partOfSpeech.name)
        insert.bindString(4, entry.definitions.joinToString(SqliteDictionary.DefinitionSeparator))
        insert.executeInsert()
        if (++pending >= BatchSize) {
            database.setTransactionSuccessful()
            database.endTransaction()
            database.beginTransaction()
            pending = 0
        }
    }

    /** Commits, indexes and closes; without it the file is left incomplete (and is deleted by the caller). */
    fun finish() {
        database.setTransactionSuccessful()
        database.endTransaction()
        database.execSQL("CREATE INDEX entries_key ON entries(key)")
        database.close()
    }

    override fun close() {
        if (database.isOpen) {
            if (database.inTransaction()) database.endTransaction()
            database.close()
        }
    }

    private companion object {
        const val BatchSize = 5_000
    }
}
