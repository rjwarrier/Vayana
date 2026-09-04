package com.vayana.dictionary.api

import kotlinx.coroutines.flow.StateFlow

interface DictionaryRepository {
    val englishPackState: StateFlow<DictionaryPackState>

    suspend fun lookupEnglish(word: String): DictionaryEntry?

    /** Installs the official Open English WordNet ZIP selected through Android's document picker. */
    suspend fun installEnglish(sourceUri: String)
}
