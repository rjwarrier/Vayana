package com.vayana.dictionary.api

/** Where a word the offline dictionary lacks can be looked up on the web. */
enum class OnlineDictionarySource { WIKTIONARY, WIKIPEDIA }

/**
 * Looks words up on Wikimedia sites. Only ever on the reader's request: each lookup sends the word over the network,
 * unlike the offline dictionary.
 */
interface OnlineDictionary {
    /** [word]'s entry on [source], or null when it has none. Throws when the site can't be reached. */
    suspend fun lookup(word: String, source: OnlineDictionarySource): DictionaryEntry?
}
