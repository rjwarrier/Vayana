package com.vayana.dictionary.stardict

import android.database.sqlite.SQLiteDatabase
import com.vayana.dictionary.api.DictionaryEntry
import com.vayana.dictionary.api.DictionarySense
import com.vayana.dictionary.api.PartOfSpeech
import com.vayana.dictionary.api.inflectionStems
import com.vayana.dictionary.api.lookupForms
import java.io.File
import java.util.zip.ZipFile

/**
 * GCIDE (GNU Collaborative International Dictionary of English: Webster's 1913 plus later additions, GPL) as a
 * read-only SQLite file built on the phone from GNU's own download: one row per headword entry, keyed by its
 * lower-case accent-folded spelling.
 */
internal class GcideDictionary(file: File) {
    private val database = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
    private val attribution = "GCIDE ${metaVersion() ?: ""} · GPL".replace("  ", " ")

    fun lookup(rawWord: String): DictionaryEntry? {
        val candidates = lookupForms(rawWord).flatMap { form -> listOf(form) + inflectionStems(form) }
            .map(GcideParser::key).distinct()
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
            if (senses.isNotEmpty()) return DictionaryEntry(headword ?: key, senses.distinct().take(MaxSenses), attribution)
        }
        return null
    }

    fun close() = database.close()

    private fun metaVersion(): String? = runCatching {
        database.rawQuery("SELECT value FROM meta WHERE name = 'version'", null).use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    companion object {
        /**
         * Builds the lookup file from GNU's gcide ZIP (its CIDE.A…Z files), reporting 0..1 as letters are done.
         * Returns the GCIDE version from the ZIP's folder name. Throws when the ZIP holds no CIDE files.
         */
        fun build(zip: File, destination: File, onProgress: (Float) -> Unit): String {
            destination.delete()
            ZipFile(zip).use { archive ->
                val letters = archive.entries().asSequence()
                    .filter { !it.isDirectory && LetterFileRegex.matches(it.name.substringAfterLast('/')) && it.size <= MaxLetterFileBytes }
                    .sortedBy { it.name }
                    .toList()
                require(letters.size >= MinLetterFiles) { "Not a GCIDE archive" }
                val version = letters.first().name.substringBefore('/').removePrefix("gcide-").takeIf { it.isNotBlank() && it.length <= 16 } ?: ""
                val database = SQLiteDatabase.openOrCreateDatabase(destination, null)
                try {
                    database.execSQL("CREATE TABLE entries (key TEXT NOT NULL, headword TEXT NOT NULL, pos TEXT NOT NULL, defs TEXT NOT NULL)")
                    database.execSQL("CREATE TABLE meta (name TEXT PRIMARY KEY, value TEXT)")
                    database.execSQL("INSERT INTO meta VALUES ('version', ?)", arrayOf(version))
                    val insert = database.compileStatement("INSERT INTO entries VALUES (?, ?, ?, ?)")
                    letters.forEachIndexed { index, letter ->
                        // GCIDE is mostly ASCII markup; two files carry stray Latin-1 bytes, which ISO-8859-1 always reads.
                        val text = archive.getInputStream(letter).use { String(it.readBytes(), Charsets.ISO_8859_1) }
                        database.beginTransaction()
                        try {
                            GcideParser.parse(text).forEach { entry ->
                                insert.bindString(1, entry.key)
                                insert.bindString(2, entry.headword)
                                insert.bindString(3, entry.partOfSpeech.name)
                                insert.bindString(4, entry.definitions.joinToString(DefinitionSeparator))
                                insert.executeInsert()
                            }
                            database.setTransactionSuccessful()
                        } finally {
                            database.endTransaction()
                        }
                        onProgress((index + 1f) / (letters.size + 1))
                    }
                    database.execSQL("CREATE INDEX entries_key ON entries(key)")
                    onProgress(1f)
                } finally {
                    database.close()
                }
                return version
            }
        }

        private const val DefinitionSeparator = "\u001F"
        private const val MaxEntries = 8
        private const val MaxSenses = 16
        private const val MinLetterFiles = 20
        private const val MaxLetterFileBytes = 32L * 1024 * 1024
        private val LetterFileRegex = Regex("CIDE\\.[A-Z]")
    }
}
