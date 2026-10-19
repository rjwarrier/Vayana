package com.vayana.dictionary.stardict

import com.vayana.dictionary.api.PartOfSpeech
import com.vayana.dictionary.api.foldAccents
import java.text.Normalizer
import java.util.Locale

/** One GCIDE headword's senses, as stored: [key] is the lower-case, accent-folded spelling lookups match on. */
internal data class GcideEntry(val key: String, val headword: String, val partOfSpeech: PartOfSpeech, val definitions: List<String>)

/**
 * Reads GCIDE's CIDE.A…Z files: paragraphs (`<p>…</p>`) where one starting with `<ent>` opens an entry (several
 * `<ent>`s are spellings of one entry) and later paragraphs add numbered senses to it. Keeps the headwords, the
 * first part of speech and each `<def>`'s text; etymology, pronunciation and citations are dropped. Entities are
 * GCIDE's own `<name/` form.
 *
 * Plain index scanning, no regular expressions: Android's regex engine took minutes per letter file on the phone.
 */
internal object GcideParser {
    fun parse(text: String): List<GcideEntry> {
        val entries = mutableListOf<GcideEntry>()
        var headwords = emptyList<String>()
        var partOfSpeech = PartOfSpeech.UNKNOWN
        var definitions = mutableListOf<String>()
        fun flush() {
            if (definitions.isNotEmpty()) headwords.forEach { headword ->
                entries += GcideEntry(key(headword), headword, partOfSpeech, definitions.toList())
            }
        }
        var position = 0
        while (true) {
            val start = text.indexOf("<p>", position)
            if (start < 0) break
            val end = text.indexOf("</p>", start + 3).let { if (it < 0) text.length else it }
            position = end + 4
            val spellings = elements(text, start, end, "ent").map(::clean).filter { it.isNotBlank() }
            if (spellings.isNotEmpty()) {
                flush()
                headwords = spellings.distinct()
                partOfSpeech = elements(text, start, end, "pos", limit = 1).firstOrNull()?.let { partOfSpeechOf(clean(it)) }
                    ?: PartOfSpeech.UNKNOWN
                definitions = mutableListOf()
            }
            if (headwords.isNotEmpty() && definitions.size < MaxSensesPerEntry) {
                elements(text, start, end, "def").map(::clean).filter { it.isNotBlank() }
                    .forEach { if (definitions.size < MaxSensesPerEntry) definitions += it }
            }
        }
        flush()
        return entries
    }

    fun key(headword: String): String = foldAccents(headword).lowercase(Locale.ROOT).trim()

    /** The raw contents of each `<tag>…</tag>` between [from] and [to]. */
    private fun elements(text: String, from: Int, to: Int, tag: String, limit: Int = Int.MAX_VALUE): List<String> {
        val open = "<$tag>"
        val close = "</$tag>"
        val found = mutableListOf<String>()
        var position = from
        while (found.size < limit) {
            val start = text.indexOf(open, position)
            if (start < 0 || start >= to) break
            val end = text.indexOf(close, start + open.length)
            if (end < 0 || end > to) break
            found += text.substring(start + open.length, end)
            position = end + close.length
        }
        return found
    }

    /** Markup out, GCIDE entities to characters, accent marks (" ` *) dropped, whitespace collapsed. */
    fun clean(markup: String): String {
        val out = StringBuilder(markup.length)
        var index = 0
        while (index < markup.length) {
            val char = markup[index]
            when {
                char == '<' -> {
                    var nameEnd = index + 1
                    while (nameEnd < markup.length && markup[nameEnd].isLetterOrDigit()) nameEnd++
                    val isEntity = nameEnd > index + 1 && nameEnd < markup.length && markup[nameEnd] == '/' &&
                        (nameEnd + 1 >= markup.length || markup[nameEnd + 1] != '>')
                    if (isEntity) {
                        val name = markup.substring(index + 1, nameEnd)
                        out.append(Entities[name] ?: entityLetter(name))
                        index = nameEnd + 1
                    } else {
                        val close = markup.indexOf('>', index)
                        index = if (close < 0) markup.length else close + 1
                    }
                }
                char == '"' || char == '`' || char == '*' -> index++
                char.isWhitespace() -> {
                    if (out.isNotEmpty() && out.last() != ' ') out.append(' ')
                    index++
                }
                else -> {
                    out.append(char)
                    index++
                }
            }
        }
        return out.toString().replace("||", "").trim()
    }

    /** `<eacute/` → é, `<oum/` → ö, `<amac/` → a: a base letter with its mark, or the bare letter for rarer marks. */
    private fun entityLetter(name: String): String {
        val base = name.firstOrNull()?.takeIf { it.isLetter() } ?: return ""
        val suffix = name.substring(1)
        AccentMarks[suffix]?.let { mark -> return Normalizer.normalize("$base$mark", Normalizer.Form.NFC) }
        return if (MarkSuffixes.any { suffix.isNotEmpty() && suffix.endsWith(it) }) base.toString() else ""
    }

    private fun partOfSpeechOf(pos: String): PartOfSpeech {
        val p = pos.lowercase(Locale.ROOT).trim()
        return when {
            p.startsWith("n.") || p.startsWith("n ") || p == "n" -> PartOfSpeech.NOUN
            p.startsWith("v.") -> PartOfSpeech.VERB
            p.startsWith("adv.") -> PartOfSpeech.ADVERB
            p.startsWith("a.") || p.startsWith("adj.") -> PartOfSpeech.ADJECTIVE
            else -> PartOfSpeech.UNKNOWN
        }
    }

    private const val MaxSensesPerEntry = 12
    private val AccentMarks = mapOf("acute" to "́", "grave" to "̀", "cir" to "̂", "circ" to "̂",
        "um" to "̈", "uml" to "̈", "til" to "̃", "tilde" to "̃", "ced" to "̧", "ring" to "̊")
    private val MarkSuffixes = listOf("mac", "cr", "dot", "dd", "sl", "ac", "br")
    private val Entities = mapOf(
        "ae" to "æ", "AE" to "Æ", "oe" to "œ", "OE" to "Œ", "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’",
        "deg" to "°", "sect" to "§", "prime" to "′", "bprime" to "″", "frac12" to "½", "frac13" to "⅓", "frac14" to "¼",
        "frac34" to "¾", "frac23" to "⅔", "pound" to "£", "para" to "¶", "divide" to "÷", "times" to "×", "root" to "√",
        "sharp" to "♯", "flat" to "♭", "thorn" to "þ", "edh" to "ð", "br" to " ", "mdash" to "—", "ndash" to "–",
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "DELTA" to "Δ", "epsilon" to "ε", "eta" to "η",
        "theta" to "θ", "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "pi" to "π", "rho" to "ρ",
        "sigma" to "σ", "tau" to "τ", "phi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω", "UPSILON" to "Υ",
        "cced" to "ç", "ntil" to "ñ", "or" to "or",
    )
}
