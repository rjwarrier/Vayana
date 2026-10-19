package com.vayana.dictionary.stardict

import com.vayana.dictionary.api.FreeDictOffer
import com.vayana.dictionary.api.PartOfSpeech
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import org.json.JSONArray

/**
 * Olam's English–Malayalam dataset (ODbL): a tab-separated file in olam.in's enml.tar.gz, one row per English
 * word and Malayalam meaning (`from_content`, `types` such as `{n}`, `to_content`). Rows for one word become one
 * entry per part of speech.
 */
internal object OlamPack {
    fun build(archive: File, destination: File, onProgress: (Float) -> Unit) {
        val rows = GZIPInputStream(archive.inputStream().buffered()).use(::tsvFromTar)
        val grouped = LinkedHashMap<Pair<String, PartOfSpeech>, Pair<String, MutableList<String>>>()
        rows.lineSequence().drop(1).forEach { line ->
            val columns = line.split('\t')
            if (columns.size < 3) return@forEach
            val word = columns[0].trim()
            val meaning = columns[2].trim()
            if (word.isEmpty() || meaning.isEmpty() || word.length > 120) return@forEach
            val key = lookupKey(word) to partOfSpeech(columns[1])
            grouped.getOrPut(key) { word to mutableListOf() }.second.let { if (it.size < 12 && meaning !in it) it += meaning }
        }
        require(grouped.size >= MinEntries) { "Not an Olam dataset" }
        SqlitePackWriter(destination).use { writer ->
            writer.meta(SqliteDictionary.MetaName, "Olam")
            writer.meta(SqliteDictionary.MetaWordLanguage, SqliteDictionary.English)
            writer.meta(SqliteDictionary.MetaMeaningLanguage, "mal")
            writer.meta(SqliteDictionary.MetaAttribution, "Olam · ODbL")
            var done = 0
            grouped.forEach { (key, value) ->
                writer.add(PackEntry(key.first, value.first, key.second, value.second))
                if (++done % 20_000 == 0) onProgress(done.toFloat() / grouped.size)
            }
            writer.finish()
        }
        onProgress(1f)
    }

    /** The data file is the tarball's one large member: read the first regular file. */
    private fun tsvFromTar(input: InputStream): String {
        val header = ByteArray(512)
        while (true) {
            var read = 0
            while (read < 512) {
                val count = input.read(header, read, 512 - read)
                if (count < 0) throw IllegalArgumentException("Not an Olam dataset")
                read += count
            }
            if (header.all { it == 0.toByte() }) throw IllegalArgumentException("Not an Olam dataset")
            val size = String(header, 124, 12, Charsets.US_ASCII).trim(Char(0), ' ').toLongOrNull(8) ?: 0L
            val type = header[156].toInt()
            val padded = (size + 511) / 512 * 512
            if ((type == '0'.code || type == 0) && size > 0) {
                require(size <= MaxDataBytes) { "Olam data is too large" }
                val bytes = ByteArray(size.toInt())
                var offset = 0
                while (offset < bytes.size) {
                    val count = input.read(bytes, offset, bytes.size - offset)
                    if (count < 0) break
                    offset += count
                }
                return String(bytes, 0, offset, Charsets.UTF_8)
            }
            var left = padded
            while (left > 0) left -= input.skip(left).takeIf { it > 0 } ?: (if (input.read() < 0) left else 1)
        }
    }

    private fun partOfSpeech(type: String): PartOfSpeech = when (type.trim().trim('{', '}')) {
        "n", "propn" -> PartOfSpeech.NOUN
        "v", "phrv" -> PartOfSpeech.VERB
        "a", "adj" -> PartOfSpeech.ADJECTIVE
        "adv" -> PartOfSpeech.ADVERB
        else -> PartOfSpeech.UNKNOWN
    }

    private const val MinEntries = 1_000
    private const val MaxDataBytes = 128L * 1024 * 1024
}

/** FreeDict's freedict-database.json: each dictionary with its StarDict release (URL, size, SHA-512). */
internal object FreeDictCatalog {
    fun parse(json: String): List<FreeDictOffer> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").takeIf { NameRegex.matches(it) } ?: return@mapNotNull null
            val releases = item.optJSONArray("releases") ?: return@mapNotNull null
            val stardict = (0 until releases.length()).mapNotNull { releases.optJSONObject(it) }
                .firstOrNull { it.optString("platform") == "stardict" } ?: return@mapNotNull null
            val url = stardict.optString("URL").takeIf { it.startsWith(DownloadPrefix) } ?: return@mapNotNull null
            FreeDictOffer(
                name = name,
                wordLanguage = name.substringBefore('-'),
                meaningLanguage = name.substringAfter('-'),
                headwords = item.optString("headwords").toIntOrNull() ?: 0,
                sizeBytes = stardict.optString("size").toLongOrNull() ?: 0L,
                status = item.optString("status"),
                url = url,
                sha512 = stardict.optString("checksum").lowercase(),
            )
        }
    }

    const val DownloadPrefix = "https://download.freedict.org/dictionaries/"
    private val NameRegex = Regex("^[a-z]{3}-[a-z]{3}$")
}
