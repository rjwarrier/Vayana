package com.vayana.dictionary.stardict

import com.vayana.dictionary.api.PartOfSpeech
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import org.tukaani.xz.XZInputStream

/** What a StarDict dictionary says about itself in its .ifo file. */
internal data class StarDictInfo(val bookName: String, val wordCount: Int, val sameTypeSequence: String?, val offsetBits: Int, val description: String)

/**
 * StarDict dictionaries (FreeDict's releases, or a reader's own file) converted into a [SqliteDictionary]: the archive
 * (ZIP, tar.gz or tar.xz) is unpacked to [workDirectory], its .idx walked and each .dict article reduced to plain
 * senses, one per line of the article.
 */
internal object StarDictPack {
    fun build(
        archive: File,
        workDirectory: File,
        destination: File,
        wordLanguage: String,
        meaningLanguage: String,
        onProgress: (Float) -> Unit,
    ): StarDictInfo {
        workDirectory.deleteRecursively()
        require(workDirectory.mkdirs()) { "Could not prepare dictionary storage" }
        try {
            unpack(archive, workDirectory)
            val files = workDirectory.walkTopDown().filter { it.isFile }.toList()
            val ifo = files.firstOrNull { it.name.endsWith(".ifo") } ?: throw IllegalArgumentException("No .ifo file")
            val stem = ifo.name.removeSuffix(".ifo")
            val idx = files.firstOrNull { it.name == "$stem.idx" || it.name == "$stem.idx.gz" } ?: throw IllegalArgumentException("No .idx file")
            val dictFile = files.firstOrNull { it.name == "$stem.dict" }
                ?: files.firstOrNull { it.name == "$stem.dict.dz" }?.let { dz ->
                    // dictzip is gzip with a seek table; reading it straight through gives the plain .dict.
                    File(workDirectory, "$stem.dict").also { plain -> GZIPInputStream(dz.inputStream().buffered()).use { input -> copyCapped(input, plain) } }
                }
                ?: throw IllegalArgumentException("No .dict file")
            val info = parseIfo(ifo.readText())
            val index = if (idx.name.endsWith(".gz")) GZIPInputStream(idx.inputStream().buffered()).use { it.readBytes() } else idx.readBytes()
            SqlitePackWriter(destination).use { writer ->
                writer.meta(SqliteDictionary.MetaName, info.bookName)
                writer.meta(SqliteDictionary.MetaWordLanguage, wordLanguage)
                writer.meta(SqliteDictionary.MetaMeaningLanguage, meaningLanguage)
                writer.meta(SqliteDictionary.MetaAttribution, info.bookName)
                RandomAccessFile(dictFile, "r").use { dict ->
                    val total = info.wordCount.coerceAtLeast(1)
                    var done = 0
                    forEachIndexEntry(index, info.offsetBits) { word, offset, size ->
                        if (size in 1..MaxArticleBytes && offset + size <= dict.length()) {
                            val bytes = ByteArray(size)
                            dict.seek(offset)
                            dict.readFully(bytes)
                            articleEntry(word, bytes, info.sameTypeSequence)?.let(writer::add)
                        }
                        if (++done % ProgressEvery == 0) onProgress((done.toFloat() / total).coerceAtMost(1f))
                    }
                }
                writer.finish()
            }
            onProgress(1f)
            return info
        } finally {
            workDirectory.deleteRecursively()
        }
    }

    fun parseIfo(text: String): StarDictInfo {
        require(text.startsWith("StarDict's dict ifo file")) { "Not a StarDict .ifo file" }
        val fields = text.lineSequence().mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
        return StarDictInfo(
            bookName = fields["bookname"]?.take(MaxNameChars).orEmpty().ifBlank { "StarDict" },
            wordCount = fields["wordcount"]?.toIntOrNull() ?: 0,
            sameTypeSequence = fields["sametypesequence"],
            offsetBits = if (fields["idxoffsetbits"] == "64") 64 else 32,
            description = fields["description"].orEmpty(),
        )
    }

    /** The .idx records: a NUL-terminated UTF-8 word, then a big-endian offset (32 or 64 bit) and a 32-bit size. */
    fun forEachIndexEntry(index: ByteArray, offsetBits: Int, action: (String, Long, Int) -> Unit) {
        var position = 0
        val offsetBytes = offsetBits / 8
        while (position < index.size) {
            var end = position
            while (end < index.size && index[end] != 0.toByte()) end++
            if (end + 1 + offsetBytes + 4 > index.size) break
            val word = String(index, position, end - position, Charsets.UTF_8)
            var cursor = end + 1
            var offset = 0L
            repeat(offsetBytes) { offset = (offset shl 8) or (index[cursor++].toLong() and 0xFF) }
            var size = 0
            repeat(4) { size = (size shl 8) or (index[cursor++].toInt() and 0xFF) }
            action(word, offset, size)
            position = cursor
        }
    }

    /** One article as plain senses: markup lines become senses, a line naming a part of speech labels those below. */
    fun articleEntry(word: String, bytes: ByteArray, sameTypeSequence: String?): PackEntry? {
        val type = sameTypeSequence?.firstOrNull() ?: bytes.firstOrNull()?.toInt()?.toChar()
        val body = if (sameTypeSequence != null) bytes else bytes.copyOfRange(1, bytes.size)
        val text = String(body, Charsets.UTF_8).trimEnd('\u0000')
        val lines = when (type) {
            'h', 'g', 'x' -> markupLines(text)
            else -> text.lines()
        }.map { it.trim() }.filter { it.isNotEmpty() }
        var partOfSpeech = PartOfSpeech.UNKNOWN
        var firstPartOfSpeech: PartOfSpeech? = null
        val senses = mutableListOf<String>()
        for (line in lines) {
            val pos = partOfSpeechLabel(line)
            if (pos != null) {
                partOfSpeech = pos
                if (firstPartOfSpeech == null) firstPartOfSpeech = pos
                continue
            }
            // A pronunciation line (/…/) is not a meaning.
            if (line.startsWith("/") && line.endsWith("/")) continue
            if (senses.size < MaxSenses) senses += line.take(MaxSenseChars)
        }
        if (senses.isEmpty()) return null
        return PackEntry(lookupKey(word), word.take(MaxNameChars), firstPartOfSpeech ?: partOfSpeech, senses)
    }

    /**
     * HTML-ish markup to text lines: block ends break lines, tags go, entities become characters. Scanned by hand, as
     * a big dictionary has hundreds of thousands of articles and Android's regex engine is slow.
     */
    fun markupLines(markup: String): List<String> {
        val out = StringBuilder(markup.length)
        var index = 0
        while (index < markup.length) {
            val char = markup[index]
            when (char) {
                '<' -> {
                    val close = markup.indexOf('>', index)
                    if (close < 0) break
                    val tag = markup.substring(index + 1, close).trim().lowercase(Locale.ROOT)
                    val name = tag.trimStart('/').takeWhile { it.isLetterOrDigit() }
                    if (name == "br" || name in BlockTags) out.append('\n')
                    index = close + 1
                }
                '&' -> {
                    val semicolon = markup.indexOf(';', index)
                    val entity = if (semicolon in index + 2..index + 10) markup.substring(index + 1, semicolon) else null
                    val decoded = entity?.let(::decodeEntity)
                    if (decoded != null) {
                        out.append(decoded)
                        index = semicolon + 1
                    } else {
                        out.append(char)
                        index++
                    }
                }
                else -> {
                    out.append(char)
                    index++
                }
            }
        }
        return out.lines()
    }

    private fun decodeEntity(name: String): String? = when {
        name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)?.takeIf(Character::isValidCodePoint)?.let { String(Character.toChars(it)) }
        name.startsWith("#") -> name.drop(1).toIntOrNull()?.takeIf(Character::isValidCodePoint)?.let { String(Character.toChars(it)) }
        else -> NamedEntities[name]
    }

    private fun partOfSpeechLabel(line: String): PartOfSpeech? = when (line.lowercase(Locale.ROOT).trim().trimEnd('.')) {
        "noun", "n", "proper noun", "substantive" -> PartOfSpeech.NOUN
        "verb", "v", "vt", "vi" -> PartOfSpeech.VERB
        "adjective", "adj", "a" -> PartOfSpeech.ADJECTIVE
        "adverb", "adv" -> PartOfSpeech.ADVERB
        "conjunction", "preposition", "pronoun", "interjection", "suffix", "prefix", "article", "determiner",
        "numeral", "particle", "phrase", "abbreviation", "symbol", "contraction" -> PartOfSpeech.UNKNOWN
        else -> null
    }

    /** Unpacks the dictionary files from a ZIP, gzip or xz tarball, by content (not file name), within size limits. */
    fun unpack(archive: File, destination: File) {
        val magic = archive.inputStream().use { input -> ByteArray(6).also { input.read(it) } }
        when {
            magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte() -> ZipInputStream(archive.inputStream().buffered()).use { zip ->
                var entries = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(++entries <= MaxArchiveEntries) { "Too many files" }
                    if (!entry.isDirectory) keep(entry.name, zip, destination)
                }
            }
            magic[0] == 0x1F.toByte() && magic[1] == 0x8B.toByte() -> untar(GZIPInputStream(archive.inputStream().buffered()), destination)
            magic[0] == 0xFD.toByte() && magic[1] == '7'.code.toByte() -> untar(XZInputStream(archive.inputStream().buffered()), destination)
            else -> throw IllegalArgumentException("Not a ZIP, tar.gz or tar.xz archive")
        }
    }

    /** A minimal ustar reader: 512-byte headers, octal sizes, data padded to 512 bytes. */
    private fun untar(stream: InputStream, destination: File) {
        DataInputStream(BufferedInputStream(stream)).use { input ->
            val header = ByteArray(512)
            var entries = 0
            while (true) {
                try {
                    input.readFully(header)
                } catch (_: EOFException) {
                    break
                }
                if (header.all { it == 0.toByte() }) break
                require(++entries <= MaxArchiveEntries) { "Too many files" }
                val name = String(header, 0, 100, Charsets.UTF_8).substringBefore('\u0000')
                val prefix = String(header, 345, 155, Charsets.UTF_8).substringBefore('\u0000')
                val size = String(header, 124, 12, Charsets.US_ASCII).trim('\u0000', ' ').toLongOrNull(8) ?: 0L
                val type = header[156].toInt().toChar()
                val padded = (size + 511) / 512 * 512
                val body = BoundedStream(input, size)
                if (type == '0' || type == '\u0000') keep(if (prefix.isNotEmpty()) "$prefix/$name" else name, body, destination)
                body.skipRest()
                input.skipFully(padded - size)
            }
        }
    }

    private fun keep(path: String, input: InputStream, destination: File) {
        val name = path.substringAfterLast('/')
        if (KeptSuffixes.none { name.endsWith(it) } || name.startsWith(".")) return
        copyCapped(input, File(destination, name))
    }

    private fun copyCapped(input: InputStream, file: File) {
        var total = 0L
        FileOutputStream(file).buffered().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MaxUnpackedBytes) { "Dictionary file is too large" }
                output.write(buffer, 0, count)
            }
        }
    }

    private fun DataInputStream.skipFully(count: Long) {
        var left = count
        while (left > 0) {
            val skipped = skip(left)
            if (skipped <= 0) {
                if (read() < 0) return
                left--
            } else {
                left -= skipped
            }
        }
    }

    /** At most [limit] bytes of [input]; [skipRest] moves past what wasn't read. */
    private class BoundedStream(private val input: InputStream, private var limit: Long) : InputStream() {
        override fun read(): Int = if (limit <= 0) -1 else input.read().also { if (it >= 0) limit-- }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (limit <= 0) return -1
            val count = input.read(buffer, offset, minOf(length.toLong(), limit).toInt())
            if (count > 0) limit -= count
            return count
        }
        fun skipRest() {
            val sink = ByteArray(8192)
            while (limit > 0 && read(sink, 0, sink.size) > 0) Unit
        }
        override fun close() = Unit
    }

    private val BlockTags = setOf("div", "p", "li", "tr", "h1", "h2", "h3", "h4", "h5", "h6", "dd", "dt", "ol", "ul")
    private val KeptSuffixes = listOf(".ifo", ".idx", ".idx.gz", ".dict", ".dict.dz")
    private val NamedEntities = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "mdash" to "—", "ndash" to "–", "hellip" to "…", "laquo" to "«", "raquo" to "»")
    private const val MaxArchiveEntries = 64
    private const val MaxUnpackedBytes = 512L * 1024 * 1024
    private const val MaxArticleBytes = 256 * 1024
    private const val MaxSenses = 12
    private const val MaxSenseChars = 600
    private const val MaxNameChars = 200
    private const val ProgressEvery = 2_000
}
