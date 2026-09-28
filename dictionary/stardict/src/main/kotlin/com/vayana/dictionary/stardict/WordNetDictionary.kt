package com.vayana.dictionary.stardict

import com.vayana.dictionary.api.DictionaryEntry
import com.vayana.dictionary.api.DictionarySense
import com.vayana.dictionary.api.PartOfSpeech
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.LinkedHashMap
import java.util.Locale

internal class WordNetDictionary(private val directory: File) {
    private val exceptions: Map<String, List<String>> by lazy { loadExceptions() }

    /** The parts of speech whose index and data files are present, mapped once; lookups then touch no file system. */
    private val parts: List<WordNetPart> by lazy {
        PartFiles.mapNotNull { (suffix, partOfSpeech) ->
            val indexFile = File(directory, "index.$suffix")
            val dataFile = File(directory, "data.$suffix")
            if (!indexFile.isFile || !dataFile.isFile) return@mapNotNull null
            WordNetPart(partOfSpeech, WordNetIndex(indexFile.mapReadOnly()), dataFile.mapReadOnly())
        }
    }
    private val resultCache = object : LinkedHashMap<String, DictionaryEntry?>(ResultCacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DictionaryEntry?>?): Boolean =
            size > ResultCacheSize
    }

    fun lookup(rawWord: String): DictionaryEntry? {
        val query = normalizeLookupWord(rawWord) ?: return null
        synchronized(resultCache) {
            if (resultCache.containsKey(query)) return resultCache[query]
        }
        val candidates = buildList {
            add(query)
            addAll(exceptions[query].orEmpty())
            addAll(simpleStems(query))
        }.distinct()

        for (candidate in candidates) {
            val candidateBytes = candidate.toByteArray(Charsets.UTF_8)
            val senses = buildList {
                parts.forEach { part -> addAll(readSenses(candidateBytes, part)) }
            }.distinctBy { Triple(it.partOfSpeech, it.definition, it.examples) }
            if (senses.isNotEmpty()) {
                val entry = DictionaryEntry(
                    headword = candidate.replace('_', ' '),
                    senses = senses,
                    attribution = "Open English WordNet 2025 · CC BY 4.0",
                )
                synchronized(resultCache) { resultCache[query] = entry }
                return entry
            }
        }
        synchronized(resultCache) { resultCache[query] = null }
        return null
    }

    private fun readSenses(word: ByteArray, part: WordNetPart): List<DictionarySense> {
        val line = part.index.find(word) ?: return emptyList()
        val fields = line.split(WhitespaceRegex)
        if (fields.size < 7) return emptyList()
        val pointerCount = fields.getOrNull(3)?.toIntOrNull() ?: return emptyList()
        val offsetsStart = 6 + pointerCount
        if (offsetsStart >= fields.size) return emptyList()

        val data = part.data
        return fields.drop(offsetsStart).mapNotNull { offsetText ->
            val offset = offsetText.toLongOrNull() ?: return@mapNotNull null
            if (offset < 0 || offset >= data.limit()) return@mapNotNull null
            parseDataLine(data.lineAt(offset.toInt()), part.partOfSpeech)
        }
    }

    private class WordNetPart(val partOfSpeech: PartOfSpeech, val index: WordNetIndex, val data: ByteBuffer)

    /**
     * Binary search straight over the mapped, sorted index file (as WordNet's own `bin_search` does), so nothing is
     * read or kept in memory up front: each lookup touches the ~20 lines it compares.
     */
    private class WordNetIndex(private val bytes: ByteBuffer) {
        // The license at the top is indented; entries start after it. Trailing blank lines are not entries either.
        private val entriesEnd: Int = run {
            var end = bytes.limit()
            while (end > 0 && bytes[end - 1].isWhitespaceByte()) end--
            end
        }
        private val entriesStart: Int = run {
            var start = 0
            while (start < entriesEnd && bytes[start].isWhitespaceByte()) start = lineEnd(start) + 1
            start
        }

        /** The index line whose lemma is [target] (UTF-8), or null. */
        fun find(target: ByteArray): String? {
            // low and high are always line starts (or the end of the entries).
            var low = entriesStart
            var high = entriesEnd
            while (low < high) {
                var start = (low + high) ushr 1
                while (start > low && bytes[start - 1] != NewLineByte) start--
                val end = lineEnd(start)
                val comparison = compareLemma(start, end, target)
                when {
                    comparison < 0 -> low = end + 1
                    comparison > 0 -> high = start
                    else -> return bytes.decode(start, end)
                }
            }
            return null
        }

        private fun lineEnd(start: Int): Int {
            var end = start
            while (end < entriesEnd && bytes[end] != NewLineByte) end++
            return end
        }

        /** The line's lemma (up to its first space) against [target], as unsigned UTF-8 bytes, i.e. by code point. */
        private fun compareLemma(start: Int, end: Int, target: ByteArray): Int {
            var position = start
            var index = 0
            while (position < end) {
                val byte = bytes[position]
                if (byte == SpaceByte || byte == CarriageReturnByte) break
                if (index == target.size) return 1
                val difference = (byte.toInt() and 0xFF) - (target[index].toInt() and 0xFF)
                if (difference != 0) return difference
                position++
                index++
            }
            return if (index == target.size) 0 else -1
        }
    }

    private fun parseDataLine(line: String?, fallbackPartOfSpeech: PartOfSpeech): DictionarySense? {
        if (line == null) return null
        val separator = line.indexOf('|')
        if (separator < 0 || separator == line.lastIndex) return null
        val gloss = line.substring(separator + 1).trim()
        val examples = ExampleRegex.findAll(gloss).map { it.groupValues[1] }.toList()
        val definition = gloss.substringBefore("; \"").trim().trimEnd(';')
        if (definition.isBlank()) return null

        val metadata = line.substring(0, separator).trim().split(WhitespaceRegex)
        val partOfSpeech = when (metadata.getOrNull(2)) {
            "n" -> PartOfSpeech.NOUN
            "v" -> PartOfSpeech.VERB
            "a", "s" -> PartOfSpeech.ADJECTIVE
            "r" -> PartOfSpeech.ADVERB
            else -> fallbackPartOfSpeech
        }
        return DictionarySense(partOfSpeech, definition, examples, synonyms = parseSynsetWords(metadata))
    }

    /** The other words sharing this synset - WordNet's own synonym grouping (w_cnt is hex, per the WNDB format). */
    private fun parseSynsetWords(metadata: List<String>): List<String> {
        val wordCount = metadata.getOrNull(3)?.toIntOrNull(radix = 16) ?: return emptyList()
        return (0 until wordCount).mapNotNull { i -> metadata.getOrNull(4 + i * 2)?.replace('_', ' ') }
    }

    private fun loadExceptions(): Map<String, List<String>> = buildMap {
        PartFiles.keys.forEach { suffix ->
            File(directory, "$suffix.exc").takeIf(File::isFile)?.forEachLine { line ->
                val fields = line.trim().split(WhitespaceRegex)
                if (fields.size > 1) {
                    val existing = get(fields.first()).orEmpty()
                    put(fields.first(), (existing + fields.drop(1)).distinct())
                }
            }
        }
    }

    private fun simpleStems(word: String): List<String> = buildList {
        if (word.length > 4 && word.endsWith("ies")) add(word.dropLast(3) + "y")
        if (word.length > 4 && word.endsWith("ied")) add(word.dropLast(3) + "y")
        listOf("ses" to "s", "xes" to "x", "zes" to "z", "ches" to "ch", "shes" to "sh")
            .forEach { (suffix, replacement) ->
                if (word.length > suffix.length + 1 && word.endsWith(suffix)) {
                    add(word.dropLast(suffix.length) + replacement)
                }
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

    companion object {
        private val PartFiles = linkedMapOf(
            "noun" to PartOfSpeech.NOUN,
            "verb" to PartOfSpeech.VERB,
            "adj" to PartOfSpeech.ADJECTIVE,
            "adv" to PartOfSpeech.ADVERB,
        )
        private val WhitespaceRegex = Regex("\\s+")
        private val ExampleRegex = Regex("\"([^\"]+)\"")
        private val LookupWordRegex = Regex("^[\\p{L}]+(?:['’\\-][\\p{L}]+)*$")
        private const val NewLineByte: Byte = 10
        private const val CarriageReturnByte: Byte = 13
        private const val SpaceByte: Byte = 32
        private const val ResultCacheSize = 64
        private const val MaxDataLineBytes = 64 * 1024

        private fun Byte.isWhitespaceByte(): Boolean = toInt().toChar().isWhitespace()

        /** Read-only map of [this]; the mapping outlives the channel and is released with the buffer. */
        private fun File.mapReadOnly(): ByteBuffer =
            RandomAccessFile(this, "r").use { file -> file.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length()) }

        /** The line starting at [offset] without its line break, or null past [MaxDataLineBytes] (as a corrupt file). */
        private fun ByteBuffer.lineAt(offset: Int): String? {
            val limit = minOf(limit(), offset + MaxDataLineBytes)
            var end = offset
            while (end < limit && this[end] != NewLineByte) end++
            if (end == limit && end < limit()) return null
            return decode(offset, end).trimEnd('\r')
        }

        // Absolute reads only (on a duplicate for the bulk copy): lookups may run on several threads at once.
        private fun ByteBuffer.decode(start: Int, end: Int): String {
            val line = ByteArray(end - start)
            duplicate().apply { position(start) }.get(line)
            return String(line, Charsets.UTF_8)
        }

        internal fun normalizeLookupWord(rawWord: String): String? {
            val trimmed = rawWord.trim().trim('“', '”', '‘', '’', '\'', '"', '.', ',', ';', ':', '!', '?', '(', ')', '[', ']')
            if (!LookupWordRegex.matches(trimmed)) return null
            return trimmed.lowercase(Locale.ROOT).replace('’', '\'').replace(' ', '_')
        }
    }
}
