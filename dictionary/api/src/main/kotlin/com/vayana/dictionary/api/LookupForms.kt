package com.vayana.dictionary.api

import java.text.Normalizer
import java.util.Locale

/**
 * The spellings worth trying for a word as it appears in a book, most literal first: the word itself (lower case,
 * curly apostrophes straightened), then without a possessive, a contraction expanded to the word it hangs on, an
 * archaic verb ending modernised ("canst", "lendeth"), and accents and ligatures folded ("abbé", "archæology").
 * Each dictionary still applies its own inflection rules to every form.
 */
fun lookupForms(rawWord: String): List<String> {
    val word = rawWord.trim().trim(*EdgePunctuation).lowercase(Locale.ROOT).replace('’', '\'').replace('‘', '\'')
    if (word.isEmpty()) return emptyList()
    val forms = LinkedHashSet<String>()
    fun addWithFolded(form: String) {
        if (form.isBlank()) return
        forms += form
        foldAccents(form).let { if (it != form) forms += it }
    }
    addWithFolded(word)
    val bare = when {
        word.endsWith("'s") -> word.dropLast(2)
        word.endsWith("s'") -> word.dropLast(1)
        else -> word
    }
    addWithFolded(bare)
    Contractions[bare]?.let(::addWithFolded)
    ContractionSuffixes.firstOrNull { bare.endsWith(it) && bare.length > it.length }?.let { suffix ->
        addWithFolded(bare.dropLast(suffix.length))
    }
    archaicVerb(bare)?.forEach(::addWithFolded)
    return forms.toList()
}

/**
 * "é" → "e", "æ" → "ae": dictionaries often list the plain spelling only. Only marks on Latin letters (and the
 * Cyrillic stress accent) go: in Malayalam, Tamil or Hindi the combining signs are vowels, part of the word.
 */
fun foldAccents(text: String): String {
    val ligatures = text.replace("æ", "ae").replace("œ", "oe").replace("Æ", "Ae").replace("Œ", "Oe")
    val decomposed = Normalizer.normalize(ligatures, Normalizer.Form.NFD)
    val out = StringBuilder(decomposed.length)
    var base: Char? = null
    for (char in decomposed) {
        val mark = Character.getType(char) == Character.NON_SPACING_MARK.toInt()
        if (!mark) {
            base = char
            out.append(char)
            continue
        }
        val script = base?.let { Character.UnicodeScript.of(it.code) }
        val fold = script == Character.UnicodeScript.LATIN || (script == Character.UnicodeScript.CYRILLIC && char == CyrillicStress)
        if (!fold) out.append(char)
    }
    return Normalizer.normalize(out.toString(), Normalizer.Form.NFC)
}

/** Regular English inflections back to their dictionary form; each dictionary checks which candidates it has. */
fun inflectionStems(word: String): List<String> = buildList {
    if (word.length > 4 && word.endsWith("ies")) add(word.dropLast(3) + "y")
    if (word.length > 4 && word.endsWith("ied")) add(word.dropLast(3) + "y")
    listOf("ses" to "s", "xes" to "x", "zes" to "z", "ches" to "ch", "shes" to "sh")
        .forEach { (suffix, replacement) ->
            if (word.length > suffix.length + 1 && word.endsWith(suffix)) add(word.dropLast(suffix.length) + replacement)
        }
    if (word.length > 4 && word.endsWith("men")) add(word.dropLast(3) + "man")
    if (word.length > 3 && word.endsWith("es")) {
        add(word.dropLast(2))
        add(word.dropLast(1))
    }
    if (word.length > 2 && word.endsWith('s') && !word.endsWith("ss")) add(word.dropLast(1))
    if (word.length > 5 && word.endsWith("ing")) {
        add(word.dropLast(3))
        add(word.dropLast(3) + "e")
        val stem = word.dropLast(3)
        if (stem.length > 2 && stem.last() == stem[stem.lastIndex - 1]) add(stem.dropLast(1))
    }
    if (word.length > 4 && word.endsWith("ed")) {
        add(word.dropLast(2))
        add(word.dropLast(1))
        val stem = word.dropLast(2)
        if (stem.length > 2 && stem.last() == stem[stem.lastIndex - 1]) add(stem.dropLast(1))
    }
    if (word.length > 4 && word.endsWith("ier")) add(word.dropLast(3) + "y")
    if (word.length > 4 && word.endsWith("er")) {
        add(word.dropLast(2))
        add(word.dropLast(1))
    }
    if (word.length > 5 && word.endsWith("iest")) add(word.dropLast(4) + "y")
    if (word.length > 5 && word.endsWith("est")) {
        add(word.dropLast(3))
        add(word.dropLast(2))
    }
}

/** "canst" → can, "didst" → did, "lendeth" → lend/lende: the -st and -eth endings of older English. */
private fun archaicVerb(word: String): List<String>? {
    ArchaicVerbs[word]?.let { return listOf(it) }
    return when {
        word.length > 5 && word.endsWith("eth") -> word.dropLast(3).let { listOf(it, it + "e") }
        word.length > 5 && word.endsWith("est") -> word.dropLast(3).let { listOf(it, it + "e") }
        word.length > 4 && word.endsWith("st") && !word.endsWith("ist") && !word.endsWith("ast") -> listOf(word.dropLast(2))
        else -> null
    }
}

private const val CyrillicStress = '\u0301'
private val EdgePunctuation = charArrayOf('“', '”', '"', '.', ',', ';', ':', '!', '?', '(', ')', '[', ']', '—', '–')
private val ContractionSuffixes = listOf("n't", "'ll", "'re", "'ve", "'d", "'m")
private val Contractions = mapOf(
    "can't" to "can", "cannot" to "can", "won't" to "will", "shan't" to "shall", "ain't" to "be",
    "'tis" to "it", "'twas" to "it", "o'er" to "over", "e'er" to "ever", "ne'er" to "never", "e'en" to "even",
)
private val ArchaicVerbs = mapOf(
    "hast" to "have", "hath" to "have", "hadst" to "have", "art" to "be", "wast" to "be", "wert" to "be",
    "dost" to "do", "doth" to "do", "didst" to "do", "canst" to "can", "couldst" to "could", "wouldst" to "would",
    "shouldst" to "should", "shalt" to "shall", "wilt" to "will", "mayst" to "may", "mightst" to "might",
)

/** "mal" → "Malayalam" (in [locale]): dictionaries carry ISO 639-3 codes, Locale names ISO 639-1 ones best. */
fun languageDisplayName(iso3: String, locale: java.util.Locale = java.util.Locale.getDefault()): String {
    val code = java.util.Locale.getISOLanguages().firstOrNull { two ->
        runCatching { java.util.Locale(two).isO3Language }.getOrNull() == iso3
    } ?: iso3
    return java.util.Locale(code).getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }.ifBlank { iso3 }
}
