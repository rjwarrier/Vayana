package com.vayana.core.database.search

/** Words typed into search, lower-cased. Punctuation - including FTS syntax like `"`, `-`, `*` - is dropped. */
fun searchTokens(text: String): List<String> =
    text.lowercase().split(NonWordChars).filter { it.isNotEmpty() }.take(MaxSearchTokens)

/**
 * FTS MATCH expression: every word must appear, each as a word prefix ("tolk" finds "Tolkien"). Tokens are
 * lower-case, so they can never be read as the upper-case AND/OR/NOT/NEAR operators.
 */
internal fun ftsPrefixMatch(tokens: List<String>): String? =
    tokens.takeIf { it.isNotEmpty() }?.joinToString(" ") { "$it*" }

/** The FTS rule applied to one field, to tell a result which of its fields matched: some word starts with a token. */
fun String?.hasWordStartingWithAny(tokens: List<String>): Boolean =
    !isNullOrBlank() && WordChars.findAll(this).any { word -> tokens.any { word.value.startsWith(it, ignoreCase = true) } }

/** The matched prefix of every word in [text] that starts with one of [tokens], for highlighting. */
fun wordPrefixMatchRanges(text: String, tokens: List<String>): List<IntRange> {
    if (tokens.isEmpty()) return emptyList()
    return WordChars.findAll(text).mapNotNull { word ->
        val matched = tokens.filter { word.value.startsWith(it, ignoreCase = true) }.maxOfOrNull { it.length }
            ?: return@mapNotNull null
        word.range.first until word.range.first + minOf(matched, word.value.length)
    }.toList()
}

// Marks (\p{M}) are part of words: Malayalam and other Indic vowel signs are combining marks, and the FTS
// unicode61 tokenizer keeps them in tokens too.
private val WordChars = Regex("""[\p{L}\p{M}\p{N}]+""")

private val NonWordChars = Regex("""[^\p{L}\p{M}\p{N}]+""")
private const val MaxSearchTokens = 8
