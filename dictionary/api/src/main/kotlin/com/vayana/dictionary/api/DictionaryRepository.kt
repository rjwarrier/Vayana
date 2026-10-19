package com.vayana.dictionary.api

import kotlinx.coroutines.flow.StateFlow

/** The offline dictionaries Vayana can install. */
enum class DictionaryPack { WORDNET, GCIDE }

data class DictionaryPackStatus(
    val pack: DictionaryPack,
    val state: DictionaryPackState,
    val sizeBytes: Long = 0L,
    /** 0..1 while installing (GCIDE: downloading, then building its lookup file). */
    val progress: Float? = null,
)

interface DictionaryRepository {
    /** Installed while any offline dictionary is; lookups then go through every installed one. */
    val englishPackState: StateFlow<DictionaryPackState>

    /** Every dictionary, in the order lookups try them. */
    val packs: StateFlow<List<DictionaryPackStatus>>

    /** The first installed dictionary, in [packs] order, that has the word. */
    suspend fun lookupEnglish(word: String): DictionaryEntry?

    /** Installs the official Open English WordNet ZIP selected through Android's document picker. */
    suspend fun installEnglish(sourceUri: String)

    /** Downloads GCIDE from GNU and builds its lookup file on the phone. */
    suspend fun installGcide()

    suspend fun remove(pack: DictionaryPack)

    /** Tries [pack] one place earlier. */
    fun moveUp(pack: DictionaryPack)
}
