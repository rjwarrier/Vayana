package com.vayana.dictionary.online

import com.vayana.dictionary.api.DictionaryEntry
import com.vayana.dictionary.api.DictionarySense
import com.vayana.dictionary.api.PartOfSpeech
import org.json.JSONArray
import org.json.JSONObject

/** Turns Wikimedia REST responses into dictionary entries. Pure, so it is tested without the network. */
internal object WikimediaParser {
    /**
     * `page/definition/{word}` on Wiktionary: senses grouped by language, then part of speech. English senses only
     * when there are any; otherwise the other languages', each marked with its language ("(Latin) whisper").
     */
    fun wiktionary(headword: String, json: String, pageUrl: String): DictionaryEntry? {
        val root = JSONObject(json)
        val english = root.optJSONArray(EnglishKey)
        val usages = if (english != null && english.length() > 0) {
            english.objects().map { it to null }
        } else {
            root.keys().asSequence().toList().flatMap { key ->
                root.optJSONArray(key)?.objects().orEmpty().map { it to it.optString("language").ifBlank { null } }
            }
        }
        val senses = usages
            .flatMap { (usage, language) ->
                val partOfSpeech = partOfSpeech(usage.optString("partOfSpeech"))
                usage.optJSONArray("definitions")?.objects().orEmpty().mapNotNull { definition ->
                    val text = definition.optString("definition").htmlToText().ifBlank { return@mapNotNull null }
                    DictionarySense(
                        partOfSpeech = partOfSpeech,
                        definition = if (language == null) text else "($language) $text",
                        examples = examples(definition),
                    )
                }
            }
            // Wiktionary lists symbols and abbreviations first ("ran": the RAN symbol); the words' own senses lead.
            .sortedBy { it.partOfSpeech == PartOfSpeech.UNKNOWN }
            .take(MaxSenses)
        if (senses.isEmpty()) return null
        return DictionaryEntry(headword = headword, senses = senses, attribution = WiktionaryAttribution, sourceUrl = pageUrl)
    }

    /**
     * `page/summary/{title}` on Wikipedia: the short description (when there is one) then the article's opening
     * paragraph. A disambiguation page's paragraph just says the title "may refer to" several things; its link leads on.
     */
    fun wikipedia(json: String): DictionaryEntry? {
        val root = JSONObject(json)
        val extract = root.optString("extract").trim().ifBlank { return null }
        val title = root.optString("title").ifBlank { return null }
        val description = root.optString("description").trim()
        val pageUrl = root.optJSONObject("content_urls")?.optJSONObject("mobile")?.optString("page")?.ifBlank { null }
        val senses = listOfNotNull(
            description.takeIf { it.isNotEmpty() }?.let { DictionarySense(PartOfSpeech.UNKNOWN, it.replaceFirstChar(Char::uppercaseChar)) },
            DictionarySense(PartOfSpeech.UNKNOWN, extract),
        )
        return DictionaryEntry(headword = title, senses = senses, attribution = WikipediaAttribution, sourceUrl = pageUrl)
    }

    /** The page titles an `action=opensearch` response lists: `[query, [titles], [descriptions], [urls]]`. */
    fun openSearchTitles(json: String): List<String> = JSONArray(json).optJSONArray(1)?.strings().orEmpty()

    private fun examples(definition: JSONObject): List<String> {
        val parsed = definition.optJSONArray("parsedExamples")?.objects().orEmpty().map { it.optString("example") }
        val plain = definition.optJSONArray("examples")?.strings().orEmpty()
        return (parsed + plain).map { it.htmlToText() }.filter { it.isNotBlank() }.distinct()
    }

    private fun partOfSpeech(label: String): PartOfSpeech = when (label.lowercase()) {
        "noun" -> PartOfSpeech.NOUN
        "verb" -> PartOfSpeech.VERB
        "adjective" -> PartOfSpeech.ADJECTIVE
        "adverb" -> PartOfSpeech.ADVERB
        else -> PartOfSpeech.UNKNOWN
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { optString(it, null) }

    private const val EnglishKey = "en"
    private const val MaxSenses = 6
    private const val WiktionaryAttribution = "Wiktionary · CC BY-SA 4.0"
    private const val WikipediaAttribution = "Wikipedia · CC BY-SA 4.0"
}

/** Plain text of a small HTML fragment: tags dropped, the common entities decoded, whitespace collapsed. */
internal fun String.htmlToText(): String =
    replace(TagRegex, "")
        .replace(EntityRegex) { match ->
            val name = match.groupValues[1]
            when {
                name.startsWith("#x", ignoreCase = true) -> name.drop(2).toIntOrNull(16)?.let(::codePointString)
                name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePointString)
                else -> NamedEntities[name]
            } ?: match.value
        }
        .replace(WhitespaceRegex, " ")
        .trim()

private fun codePointString(codePoint: Int): String? =
    codePoint.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) }

private val TagRegex = Regex("<[^>]*>")
private val EntityRegex = Regex("&(#[xX]?[0-9a-fA-F]+|[a-zA-Z]+);")
private val WhitespaceRegex = Regex("\\s+")
private val NamedEntities = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")
