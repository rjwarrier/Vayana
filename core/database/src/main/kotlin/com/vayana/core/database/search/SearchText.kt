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

/** The FTS rule applied to one field, to tell a result which of its fields matched. */
fun String?.hasWordStartingWith(token: String): Boolean =
    !isNullOrBlank() && split(NonWordChars).any { it.startsWith(token, ignoreCase = true) }

// Marks (\p{M}) are part of words: Malayalam and other Indic vowel signs are combining marks, and the FTS
// unicode61 tokenizer keeps them in tokens too.
private val NonWordChars = Regex("""[^\p{L}\p{M}\p{N}]+""")
private const val MaxSearchTokens = 8
