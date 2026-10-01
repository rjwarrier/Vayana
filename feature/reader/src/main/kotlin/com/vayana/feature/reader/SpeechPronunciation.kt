package com.vayana.feature.reader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class SpeechPronunciation(val original: String, val spoken: String)

internal fun normalizedPronunciations(rules: List<SpeechPronunciation>): List<SpeechPronunciation> = rules
    .map { SpeechPronunciation(it.original.trim(), it.spoken.trim()) }
    .filter { it.original.isNotEmpty() && it.original.length <= 120 && it.spoken.isNotEmpty() && it.spoken.length <= 200 }
    .distinctBy { it.original.lowercase(java.util.Locale.ROOT) }
    .take(100)

/** Offsets into spoken replacements map to the entire original name; all later words retain exact offsets. */
internal class PronouncedSpeechText(
    val text: String,
    private val starts: IntArray? = null,
    private val ends: IntArray? = null,
) {
    fun sourceRange(start: Int, end: Int): Pair<Int, Int> =
        if (starts == null || ends == null) start to end else starts[start] to ends[end - 1]
}

internal fun prepareSpeechPronunciation(text: String, rules: List<SpeechPronunciation>): PronouncedSpeechText =
    SpeechPronouncer(rules).prepare(text)

/** Compile the book's rules once, rather than compiling a regex for every sentence in a chapter. */
internal class SpeechPronouncer(rules: List<SpeechPronunciation>) {
    private val ordered = normalizedPronunciations(rules).sortedByDescending { it.original.length }
    private val pattern = Regex(
        "(?<![\\p{L}\\p{N}_])(?:" +
            (ordered.map { Regex.escape(it.original) } + InterjectionPattern).joinToString("|") +
            ")(?![\\p{L}\\p{N}_])",
        RegexOption.IGNORE_CASE,
    )
    fun prepare(text: String): PronouncedSpeechText {
        val tokens = pattern.findAll(text).toList()
        val corrections = tokens.map { match ->
            match to (ordered.firstOrNull { it.original.equals(match.value, ignoreCase = true) }?.spoken
                ?: spokenInterjection(match.value))
        }.filter { (match, replacement) -> match.value != replacement }.toList()
        // Explicit book corrections own their spans, including corrections that leave a token unchanged.
        val protected = if (ordered.isEmpty()) emptyList() else tokens.filter { match ->
            ordered.any { it.original.equals(match.value, ignoreCase = true) }
        }.map { it.range }.toList()
        val numerals = romanSpeechReplacements(text).filter { (match, _) ->
            protected.none { match.range.first <= it.last && match.range.last >= it.first }
        }
        val claimed = corrections + numerals
        fun free(match: MatchResult) = claimed.none { (other, _) ->
            match.range.first <= other.range.last && match.range.last >= other.range.first
        } && protected.none { match.range.first <= it.last && match.range.last >= it.first }
        val typography = typographyReplacements(text).filter { (match, _) -> free(match) }
        val matches = (corrections.filter { (match, _) ->
            numerals.none { (numeral, _) -> match.range.first <= numeral.range.last && match.range.last >= numeral.range.first }
        } + numerals + typography).sortedBy { it.first.range.first }
        if (matches.isEmpty()) return PronouncedSpeechText(text)
        val length = text.length + matches.sumOf { (match, replacement) -> replacement.length - match.value.length }
        val spoken = StringBuilder(length)
        // Allocate exact primitive arrays once; boxed per-character lists temporarily doubled the maps.
        val starts = IntArray(length)
        val ends = IntArray(length)
        var cursor = 0
        var destination = 0
        for ((match, replacement) in matches) {
            spoken.append(text, cursor, match.range.first)
            for (index in cursor until match.range.first) {
                starts[destination] = index
                ends[destination++] = index + 1
            }
            spoken.append(replacement)
            starts.fill(match.range.first, destination, destination + replacement.length)
            ends.fill(match.range.last + 1, destination, destination + replacement.length)
            destination += replacement.length
            cursor = match.range.last + 1
        }
        spoken.append(text, cursor, text.length)
        for (index in cursor until text.length) {
            starts[destination] = index
            ends[destination++] = index + 1
        }
        return PronouncedSpeechText(spoken.toString(), starts, ends)
    }
}

/**
 * Prose punctuation and capitalisation that engines read poorly: a dash with no spaces is glued into one word, and
 * SHOUTED words are sometimes spelled out letter by letter. Both keep the highlight on the original text.
 */
private fun typographyReplacements(text: String): List<Pair<MatchResult, String>> {
    val result = mutableListOf<Pair<MatchResult, String>>()
    UnspacedDash.findAll(text).forEach { result += it to ", " }
    ShoutedWord.findAll(text).forEach { match ->
        val word = match.value
        // Keep real initialisms (FBI, USSR, NASA's letters spoken as written) - only words that read as words.
        if (word.any { it in "AEIOUY" } && word.any { it !in "IVXLCDM" } && !ConsonantRun.containsMatchIn(word)) {
            result += match to word.lowercase(java.util.Locale.ROOT)
        }
    }
    return result
}

private val UnspacedDash = Regex("(?<=[\\p{L}.,!?'’\"”])—(?=[\\p{L}'‘\"“])")
private val ShoutedWord = Regex("(?<![\\p{L}\\p{N}_'’])\\p{Lu}{4,}(?![\\p{L}\\p{N}_'’])")
private val ConsonantRun = Regex("[^AEIOUY]{3,}")

/** Convert clear numeral labels, not pronoun I, initials, ordinary words, or arbitrary capitalized acronyms. */
private fun romanSpeechReplacements(text: String): List<Pair<MatchResult, String>> =
    RomanToken.findAll(text).mapNotNull { match ->
        val roman = java.text.Normalizer.normalize(match.value, java.text.Normalizer.Form.NFKC)
            .uppercase(java.util.Locale.ROOT)
        if (!ValidRoman.matches(roman)) return@mapNotNull null
        val labelled = RomanLabel.containsMatchIn(text.substring((match.range.first - 80).coerceAtLeast(0), match.range.first))
        val unicode = match.value.any { it in '\u2160'..'\u217f' }
        val standalone = text.trim().trimEnd('.', ':', ')') == match.value &&
            match.value.length > 1 && match.value == match.value.uppercase(java.util.Locale.ROOT)
        if (!labelled && !unicode && !standalone) return@mapNotNull null
        var number = 0
        var previous = 0
        for (character in roman.reversed()) {
            val value = when (character) {
                'I' -> 1; 'V' -> 5; 'X' -> 10; 'L' -> 50; 'C' -> 100; 'D' -> 500; else -> 1000
            }
            number += if (value < previous) -value else value
            previous = value
        }
        match to number.toString()
    }.toList()

private val RomanToken = Regex("(?<![\\p{L}\\p{N}_])(?:[IVXLCDMivxlcdm]+|[\\u2160-\\u217f]+)(?![\\p{L}\\p{N}_])")
private val ValidRoman = Regex("(?=.)M{0,3}(?:CM|CD|D?C{0,3})(?:XC|XL|L?X{0,3})(?:IX|IV|V?I{0,3})")
private val RomanLabel = Regex("\\b(?:chapter|ch\\.?|part|book|volume|vol\\.?|section|sec\\.?|act|scene|appendix|figure|fig\\.?|table|episode)\\s+$", RegexOption.IGNORE_CASE)

// Use conventional short interjections, rather than unrelated literal words such as "hum" and "mum".
// Android also receives a text hint for these tokens; book corrections still override this default.
// Do not infer emotion, alter uppercase acronyms, or treat the measurement unit "mm" as a murmur.
private const val InterjectionPattern = "h+m{2,}|m{3,}|u+h{2,}|a+h{2,}|o+h{2,}|s+h{2,}"
private val ThinkingMurmur = Regex("h+m{2,}", RegexOption.IGNORE_CASE)
private val ClosedMurmur = Regex("m{3,}", RegexOption.IGNORE_CASE)
private val Hesitation = Regex("u+h{2,}", RegexOption.IGNORE_CASE)
private val Sigh = Regex("a+h{2,}", RegexOption.IGNORE_CASE)
private val Surprise = Regex("o+h{2,}", RegexOption.IGNORE_CASE)
private val Hush = Regex("s+h{2,}", RegexOption.IGNORE_CASE)

private fun spokenInterjection(word: String): String {
    if (word == word.uppercase(java.util.Locale.ROOT)) return word
    return when {
        ThinkingMurmur.matches(word) -> "hmm"
        ClosedMurmur.matches(word) -> "mmm"
        Hesitation.matches(word) -> "uh"
        Sigh.matches(word) -> "ah"
        Surprise.matches(word) -> "oh"
        Hush.matches(word) -> "shh"
        else -> word
    }
}

/** Local pronunciation corrections keyed by the book's stable sync identity, independently of voice settings. */
internal class SpeechPronunciationStore(context: Context) {
    private val preferences = context.getSharedPreferences("reader_pronunciations", Context.MODE_PRIVATE)

    fun load(bookKey: String): List<SpeechPronunciation> = runCatching {
        val array = JSONArray(preferences.getString(bookKey, "[]"))
        normalizedPronunciations((0 until array.length()).map { index ->
            val rule = array.getJSONObject(index)
            SpeechPronunciation(rule.getString("original"), rule.getString("spoken"))
        })
    }.getOrDefault(emptyList())

    fun save(bookKey: String, rules: List<SpeechPronunciation>) {
        val array = JSONArray()
        normalizedPronunciations(rules).forEach { rule ->
            array.put(JSONObject().put("original", rule.original).put("spoken", rule.spoken))
        }
        preferences.edit().putString(bookKey, array.toString()).apply()
    }
}
