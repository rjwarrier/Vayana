package com.vayana.format.ocr

/**
 * Which line ends of a recognised book fall inside a word. Malayalam books break words at the end of a line without
 * a hyphen (കിലോമീറ്ററു / കൾക്കു), and recognised text would otherwise be searched, looked up and read aloud in
 * halves. Returns, per page, the indexes of the lines whose last word continues as the next line's first word.
 *
 * The book itself is the word list: a word in the middle of a line is whole, so it is
 * - joined when the next line starts with a mark or a doubled consonant (no word starts with ം, ി, ക്ക, ങ്ങ ...),
 *   or with a case ending that begins with യ (യുടെ, യും, ...),
 * - joined when the two halves together appear in the book as a whole word,
 * - joined when neither half ever appears as a whole word, unless the first ends as words do (a chillu, ം or ്),
 * - never joined when the next line starts with a vowel letter, punctuation or a digit, or the line ends with them.
 *
 * Checked on a 324-page scanned novel: about 85–90% of line ends decided right in three samples of 40, against
 * ~60% with no joining; most misses are compounds printed either way (എല്ലാം കൊണ്ടും / എല്ലാംകൊണ്ടും).
 * ponytail: a heuristic from one book; a Malayalam word list would do better if one is ever bundled.
 */
fun lineJoins(pages: Map<Int, RecognizedPage>): Map<Int, Set<Int>> {
    val wholeWords = wholeWordCounts(pages)
    return pages.mapValues { (_, page) ->
        page.lines.indices.filter { index ->
            val next = page.lines.getOrNull(index + 1) ?: return@filter false
            val end = page.lines[index].lastOrNull()?.text ?: return@filter false
            val start = next.firstOrNull()?.text ?: return@filter false
            continuesOnNextLine(end, start, wholeWords)
        }.toSet()
    }
}

/** How often each word appears in the middle of a line in [pages]: there it is certainly a whole word. */
internal fun wholeWordCounts(pages: Map<Int, RecognizedPage>): Map<String, Int> {
    val wholeWords = HashMap<String, Int>()
    for (page in pages.values) for (line in page.lines) {
        for (word in line.drop(1).dropLast(1)) bare(word.text).takeIf { it.isNotEmpty() }?.let { wholeWords.merge(it, 1, Int::plus) }
    }
    return wholeWords
}

internal fun continuesOnNextLine(end: String, start: String, wholeWords: Map<String, Int>): Boolean {
    if (EndsLikeSentence.containsMatchIn(end) || StartsLikeSentence.containsMatchIn(start)) return false
    val first = bare(end)
    val second = bare(start)
    if (first.isEmpty() || second.isEmpty()) return false
    // the rules below are Malayalam spelling; other scripts' line ends are left as they are
    if (first.none { it in MalayalamLetters } || second.none { it in MalayalamLetters }) return false
    val lead = second[0]
    if (lead in IndependentVowels) return false
    if (lead in DependentMarks) return true
    if (second.length > 2 && second[1] == Virama && (second[2] == lead || lead in NasalsThatNeverStartWords)) return true
    if (YaEndings.any { second == it || second.startsWith(it) && second.length <= it.length + 3 }) return true
    if ((wholeWords[first + second] ?: 0) > 0) return true
    return (wholeWords[first] ?: 0) == 0 && (wholeWords[second] ?: 0) == 0 && first.last() !in WordEndings
}

private fun bare(word: String) = word.trim { it in Surrounding }

private const val Virama = '്'
private val MalayalamLetters = 'ഀ'..'ൿ'
private const val Surrounding = "‘’“”\"'()[].,!?;:…"
private val IndependentVowels = 'അ'..'ഔ'
private val DependentMarks = ('ാ'..'്').toSet() + setOf('ം', 'ഃ', 'ൗ')
private const val NasalsThatNeverStartWords = "ങഞണ"
private const val WordEndings = "ൺൻർൽൾൿം്"
private val YaEndings = listOf("യുടെ", "യൂടെ", "യും", "യെ", "യിൽ", "യിലെ", "യിലേക്ക്", "യോട്", "യോടെ", "യായി", "യാണ്", "യുമായി", "യൊക്കെ", "യ്ക്ക്")
private val EndsLikeSentence = Regex("""[.,!?;:'"’”)\]…\-—–0-9]$""")
private val StartsLikeSentence = Regex("""^[‘“"'(\[0-9]""")
