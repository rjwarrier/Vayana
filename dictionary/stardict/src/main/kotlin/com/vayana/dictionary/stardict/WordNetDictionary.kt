package com.vayana.dictionary.stardict

import com.vayana.dictionary.api.DictionaryEntry
import com.vayana.dictionary.api.DictionarySense
import com.vayana.dictionary.api.PartOfSpeech
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.LinkedHashMap
import java.util.Locale

internal class WordNetDictionary(private val directory: File) {
    private val exceptions: Map<String, List<String>> by lazy { loadExceptions() }
    private val indexes = mutableMapOf<String, WordNetIndex>()
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
            val senses = buildList {
                PartFiles.forEach { (suffix, partOfSpeech) ->
                    addAll(readSenses(candidate, suffix, partOfSpeech))
                }
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

    private fun readSenses(word: String, suffix: String, partOfSpeech: PartOfSpeech): List<DictionarySense> {
        val indexFile = File(directory, "index.$suffix")
        val dataFile = File(directory, "data.$suffix")
        if (!indexFile.isFile || !dataFile.isFile) return emptyList()
        val line = indexFor(indexFile).find(word) ?: return emptyList()
        val fields = line.split(WhitespaceRegex)
        if (fields.size < 7) return emptyList()
        val pointerCount = fields.getOrNull(3)?.toIntOrNull() ?: return emptyList()
        val offsetsStart = 6 + pointerCount
        if (offsetsStart >= fields.size) return emptyList()

        return RandomAccessFile(dataFile, "r").use { data ->
            fields.drop(offsetsStart).mapNotNull { offsetText ->
                val offset = offsetText.toLongOrNull() ?: return@mapNotNull null
                if (offset < 0 || offset >= data.length()) return@mapNotNull null
                data.seek(offset)
                parseDataLine(data.readUtf8Line(), partOfSpeech)
            }
        }
    }

    private fun indexFor(file: File): WordNetIndex = synchronized(indexes) {
        indexes.getOrPut(file.name) { WordNetIndex(file.readBytes()) }
    }

    private class WordNetIndex(private val bytes: ByteArray) {
        private val lineOffsets: IntArray = buildList {
            var start = 0
            while (start < bytes.size) {
                var end = start
                while (end < bytes.size && bytes[end] != NewLineByte) end++
                if (end > start && bytes[start].toInt().toChar().isWhitespace().not()) add(start)
                start = end + 1
            }
        }.toIntArray()

        fun find(target: String): String? {
            var low = 0
            var high = lineOffsets.size - 1
            while (low <= high) {
                val middle = (low + high) ushr 1
                val line = lineAt(middle)
                val comparison = line.substringBefore(' ').compareTo(target)
                when {
                    comparison < 0 -> low = middle + 1
                    comparison > 0 -> high = middle - 1
                    else -> return line
                }
            }
            return null
        }

        private fun lineAt(index: Int): String {
            val start = lineOffsets[index]
            var end = start
            while (end < bytes.size && bytes[end] != NewLineByte && bytes[end] != CarriageReturnByte) end++
            return String(bytes, start, end - start, Charsets.UTF_8)
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
        return DictionarySense(partOfSpeech, definition, examples)
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

    private fun RandomAccessFile.readUtf8Line(): String? {
        val bytes = ByteArrayOutputStream()
        while (bytes.size() <= MaxDataLineBytes) {
            when (val next = read()) {
                -1 -> return bytes.takeIf { it.size() > 0 }?.toString(Charsets.UTF_8.name())
                NewLineByte.toInt() -> return bytes.toString(Charsets.UTF_8.name()).trimEnd('\r')
                else -> bytes.write(next)
            }
        }
        return null
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
        private const val ResultCacheSize = 64
        private const val MaxDataLineBytes = 64 * 1024

        internal fun normalizeLookupWord(rawWord: String): String? {
            val trimmed = rawWord.trim().trim('“', '”', '‘', '’', '\'', '"', '.', ',', ';', ':', '!', '?', '(', ')', '[', ']')
            if (!LookupWordRegex.matches(trimmed)) return null
            return trimmed.lowercase(Locale.ROOT).replace('’', '\'').replace(' ', '_')
        }
    }
}
