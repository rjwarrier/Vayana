package com.vayana.dictionary.api

data class DictionaryEntry(
    val headword: String,
    val senses: List<DictionarySense>,
    val attribution: String,
    /** The entry's page on the web, for entries looked up online. */
    val sourceUrl: String? = null,
    /** ISO 639-3 code of the language the senses are written in, when it differs from the word's own. */
    val meaningLanguage: String? = null,
    /** The same word in other installed dictionaries that give its meaning in another language (Malayalam, German…). */
    val alsoIn: List<DictionaryEntry> = emptyList(),
)

data class DictionarySense(
    val partOfSpeech: PartOfSpeech,
    val definition: String,
    val examples: List<String> = emptyList(),
    /** Other words sharing this sense's synset (WordNet's own synonym grouping). */
    val synonyms: List<String> = emptyList(),
)

enum class PartOfSpeech {
    NOUN,
    VERB,
    ADJECTIVE,
    ADVERB,
    UNKNOWN,
}

sealed interface DictionaryPackState {
    data object NotInstalled : DictionaryPackState
    data object Installing : DictionaryPackState
    data object Installed : DictionaryPackState
    /** The install failed; why is reported by the exception [DictionaryRepository.installEnglish] throws. */
    data object Failed : DictionaryPackState
}
