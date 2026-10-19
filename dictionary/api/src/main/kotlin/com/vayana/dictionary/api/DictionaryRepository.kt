package com.vayana.dictionary.api

import kotlinx.coroutines.flow.StateFlow

/** Where an installed dictionary came from: the two built-in English ones, Olam, FreeDict's catalog, or a user's file. */
enum class DictionaryKind { WORDNET, GCIDE, OLAM, FREEDICT, STARDICT }

data class DictionaryStatus(
    /** Stable id: "wordnet", "gcide", "olam", "freedict-eng-deu", "stardict-<n>". */
    val id: String,
    val kind: DictionaryKind,
    /** Shown for FreeDict and imported dictionaries; built-in ones are named from string resources by [kind]. */
    val name: String?,
    /** ISO 639-3 codes: the language of the words, and of their meanings (the same for a monolingual dictionary). */
    val wordLanguage: String,
    val meaningLanguage: String,
    val state: DictionaryPackState,
    val sizeBytes: Long = 0L,
    /** 0..1 while installing. */
    val progress: Float? = null,
)

/** One dictionary FreeDict offers: [name] is its language pair ("eng-deu"), ISO 639-3 codes on both sides. */
data class FreeDictOffer(
    val name: String,
    val wordLanguage: String,
    val meaningLanguage: String,
    val headwords: Int,
    val sizeBytes: Long,
    val status: String,
    val url: String,
    val sha512: String,
)

interface DictionaryRepository {
    /** Installed while any offline dictionary is. */
    val englishPackState: StateFlow<DictionaryPackState>

    /** Built-in dictionaries (installed or not) and every installed one, in the order lookups try them. */
    val dictionaries: StateFlow<List<DictionaryStatus>>

    /**
     * Looks [word] up for a book in [bookLanguage] (ISO 639-1 or -3; null: any language, English first). The first
     * dictionary in order that defines it in that language, or else explains it in English, gives the entry; installed
     * dictionaries giving its meaning in other languages are attached as [DictionaryEntry.alsoIn].
     */
    suspend fun lookup(word: String, bookLanguage: String? = null): DictionaryEntry?

    /** Installs the official Open English WordNet ZIP selected through Android's document picker. */
    suspend fun installEnglish(sourceUri: String)

    /** Downloads GCIDE from GNU and builds its lookup file on the phone. */
    suspend fun installGcide()

    /** Downloads Olam's English–Malayalam data and builds its lookup file on the phone. */
    suspend fun installOlam()

    /** FreeDict's catalog of downloadable dictionaries (cached for a day). */
    suspend fun freeDictCatalog(): List<FreeDictOffer>

    suspend fun installFreeDict(offer: FreeDictOffer)

    /** Imports a StarDict dictionary (ZIP, tar.gz or tar.xz holding .ifo/.idx/.dict) the reader chose, with its languages. */
    suspend fun importStarDict(sourceUri: String, wordLanguage: String, meaningLanguage: String)

    suspend fun remove(id: String)

    /** Tries dictionary [id] one place earlier. */
    fun moveUp(id: String)
}
