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
        val matches = pattern.findAll(text).map { match ->
            match to (ordered.firstOrNull { it.original.equals(match.value, ignoreCase = true) }?.spoken
                ?: spokenInterjection(match.value))
        }.filter { (match, replacement) -> match.value != replacement }.toList()
        if (matches.isEmpty()) return PronouncedSpeechText(text)
        val spoken = StringBuilder()
        val starts = mutableListOf<Int>()
        val ends = mutableListOf<Int>()
        var cursor = 0
        for ((match, replacement) in matches) {
            for (index in cursor until match.range.first) {
                spoken.append(text[index])
                starts += index
                ends += index + 1
            }
            spoken.append(replacement)
            repeat(replacement.length) { starts += match.range.first; ends += match.range.last + 1 }
            cursor = match.range.last + 1
        }
        for (index in cursor until text.length) {
            spoken.append(text[index])
            starts += index
            ends += index + 1
        }
        return PronouncedSpeechText(spoken.toString(), starts.toIntArray(), ends.toIntArray())
    }
}

// Familiar phonetic approximations avoid engines spelling consonant-only murmurs letter by letter.
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
        ThinkingMurmur.matches(word) -> "hum"
        ClosedMurmur.matches(word) -> "mum"
        Hesitation.matches(word) -> "uh"
        Sigh.matches(word) -> "ah"
        Surprise.matches(word) -> "oh"
        Hush.matches(word) -> "shush"
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
