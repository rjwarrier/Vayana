package com.vayana.dictionary.api

data class DictionaryEntry(
    val headword: String,
    val senses: List<DictionarySense>,
    val attribution: String,
)

data class DictionarySense(
    val partOfSpeech: PartOfSpeech,
    val definition: String,
    val examples: List<String> = emptyList(),
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
    data class Failed(val message: String) : DictionaryPackState
}
