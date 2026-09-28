package com.vayana.dictionary.api

/** Where a word the offline dictionary lacks can be looked up on the web. */
enum class OnlineDictionarySource { WIKTIONARY, WIKIPEDIA }

/**
 * Looks words up on Wikimedia sites. Only ever on the reader's request: each lookup sends the word over the network,
 * unlike the offline dictionary.
 */
interface OnlineDictionary {
    /**
     * [word]'s entry on [source], or null when it has none. Throws when the site can't be reached. [language] is the
     * book's (a primary subtag like "ml"): Wikipedia is searched in that language first, and Wiktionary's entry for a
     * word in that language comes first.
     */
    suspend fun lookup(word: String, source: OnlineDictionarySource, language: String? = null): DictionaryEntry?
}
