package com.vayana.dictionary.stardict

import com.vayana.dictionary.api.PartOfSpeech
import java.text.Normalizer
import java.util.Locale

/**
 * Reads GCIDE's CIDE.A…Z files: paragraphs (`<p>…</p>`) where one starting with `<ent>` opens an entry (several
 * `<ent>`s are spellings of one entry) and later paragraphs add numbered senses to it. Keeps the headwords, the
 * first part of speech and each `<def>`'s text; etymology, pronunciation and citations are dropped. Entities are
 * GCIDE's own `<name/` form.
 *
 * Plain index scanning, no regular expressions: Android's regex engine took minutes per letter file on the phone.
 */
internal object GcideParser {
    fun parse(text: String): List<PackEntry> {
        val entries = mutableListOf<PackEntry>()
        var headwords = emptyList<String>()
        var partOfSpeech = PartOfSpeech.UNKNOWN
        var definitions = mutableListOf<String>()
        fun flush() {
            if (definitions.isNotEmpty()) headwords.forEach { headword ->
                entries += PackEntry(lookupKey(headword), headword, partOfSpeech, definitions.toList())
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

    /**
     * Builds the GCIDE lookup file from GNU's gcide ZIP (its CIDE.A…Z files), reporting 0..1 as letters are done.
     * Throws IllegalArgumentException when the ZIP holds no CIDE files.
     */
    fun build(zip: java.io.File, destination: java.io.File, onProgress: (Float) -> Unit) {
        java.util.zip.ZipFile(zip).use { archive ->
            val letters = archive.entries().asSequence()
                .filter { !it.isDirectory && it.name.substringAfterLast('/').let { n -> n.length == 6 && n.startsWith("CIDE.") } && it.size <= MaxLetterFileBytes }
                .sortedBy { it.name }
                .toList()
            require(letters.size >= MinLetterFiles) { "Not a GCIDE archive" }
            val version = letters.first().name.substringBefore('/').removePrefix("gcide-").takeIf { it.isNotBlank() && it.length <= 16 }.orEmpty()
            SqlitePackWriter(destination).use { writer ->
                writer.meta(SqliteDictionary.MetaName, "GCIDE")
                writer.meta(SqliteDictionary.MetaVersion, version)
                writer.meta(SqliteDictionary.MetaWordLanguage, SqliteDictionary.English)
                writer.meta(SqliteDictionary.MetaAttribution, "GCIDE $version · GPL".replace("  ", " "))
                letters.forEachIndexed { index, letter ->
                    // Mostly ASCII markup; two files carry stray Latin-1 bytes, which ISO-8859-1 always reads.
                    val text = archive.getInputStream(letter).use { String(it.readBytes(), Charsets.ISO_8859_1) }
                    parse(text).forEach(writer::add)
                    onProgress((index + 1f) / (letters.size + 1))
                }
                writer.finish()
            }
            onProgress(1f)
        }
    }

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
    private const val MinLetterFiles = 20
    private const val MaxLetterFileBytes = 32L * 1024 * 1024
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
