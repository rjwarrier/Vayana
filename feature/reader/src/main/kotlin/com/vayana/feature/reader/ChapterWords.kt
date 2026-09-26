package com.vayana.feature.reader

/** A word from the chapter on screen with its first dictionary sense; [saved] when it's already a vocabulary card. */
data class ChapterWord(val word: String, val definition: String, val saved: Boolean)

sealed interface ChapterWordsState {
    data object Idle : ChapterWordsState
    data object Loading : ChapterWordsState
    data object DictionaryRequired : ChapterWordsState
    data class Ready(val words: List<ChapterWord>) : ChapterWordsState
}

/**
 * The chapter's likeliest unfamiliar words, without a word-frequency list to lean on: words of at least
 * [MinUnusualWordLength] letters that the chapter uses at most [MaxUnusualWordOccurrences] times, that appear in
 * lower case at least once (so names and other proper nouns drop out) and aren't in [knownWords].
 * Rarest first, then longest. [counts] maps each word as written to how often it appears.
 */
internal fun unusualWordCandidates(
    counts: Map<String, Int>,
    knownWords: Set<String>,
    limit: Int = MaxUnusualWordCandidates,
): List<String> {
    val byNormalizedWord = HashMap<String, CandidateCounts>(counts.size)
    for ((variant, count) in counts) {
        val word = variant.lowercase()
        val candidate = byNormalizedWord.getOrPut(word, ::CandidateCounts)
        candidate.occurrences += count
        if (variant == word) candidate.hasLowercaseVariant = true
    }

    return byNormalizedWord.asSequence()
        .filter { (word, candidate) ->
            word.length >= MinUnusualWordLength &&
                word.all(Char::isLetter) &&
                candidate.hasLowercaseVariant &&
                word !in knownWords &&
                candidate.occurrences <= MaxUnusualWordOccurrences
        }
        .map { (word, candidate) -> word to candidate.occurrences }
        .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenByDescending { it.first.length }.thenBy { it.first })
        .take(limit)
        .map { it.first }
        .toList()
}

private class CandidateCounts(
    var occurrences: Int = 0,
    var hasLowercaseVariant: Boolean = false,
)

internal const val MinUnusualWordLength = 8
private const val MaxUnusualWordOccurrences = 2
private const val MaxUnusualWordCandidates = 60
