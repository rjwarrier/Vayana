package com.vayana.format.pdf

import java.io.File
import java.io.RandomAccessFile

/** Title and author from a PDF's document information dictionary, each null when absent or unreadable. */
data class PdfInfo(val title: String?, val author: String?)

/**
 * Reads the document information dictionary (`/Info` in the trailer) without a PDF library. Only the head and tail of
 * the file are scanned, which is where the trailer and, in practice, the Info object live. Info kept in a compressed
 * object stream, or in an encrypted file, is not readable this way and yields an empty [PdfInfo]; the caller falls
 * back to the file name.
 */
object PdfInfoReader {
    private const val HeadBytes = 256 * 1024
    private const val TailBytes = 1024 * 1024
    private const val MaxFieldLength = 300

    fun read(file: File): PdfInfo {
        val text = RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            if (length <= HeadBytes + TailBytes) {
                ByteArray(length.toInt()).also { raf.readFully(it) }.latin1()
            } else {
                val head = ByteArray(HeadBytes).also { raf.readFully(it) }
                raf.seek(length - TailBytes)
                val tail = ByteArray(TailBytes).also { raf.readFully(it) }
                head.latin1() + "\n" + tail.latin1()
            }
        }
        return parse(text)
    }

    /** [text] is PDF bytes decoded as ISO-8859-1, so each char is one byte. */
    fun parse(text: String): PdfInfo {
        val empty = PdfInfo(null, null)
        // The last trailer (or cross-reference stream dictionary) is the current one after incremental updates.
        val trailerStart = maxOf(text.lastIndexOf("trailer"), text.lastIndexOf("/XRef"))
        val searchFrom = if (trailerStart >= 0) trailerStart else 0
        val infoRef = InfoRefRegex.findAll(text, searchFrom).lastOrNull() ?: InfoRefRegex.findAll(text).lastOrNull()
            ?: return empty
        if (text.indexOf("/Encrypt", searchFrom) >= 0) return empty
        val (number, generation) = infoRef.destructured
        val objectStart = Regex("(?<![0-9])$number\\s+$generation\\s+obj\\b").findAll(text).lastOrNull() ?: return empty
        val dictStart = text.indexOf("<<", objectStart.range.last)
        if (dictStart < 0) return empty
        val dictEnd = text.indexOf("endobj", dictStart).takeIf { it > 0 } ?: text.length
        val dict = text.substring(dictStart, dictEnd)
        return PdfInfo(
            title = stringField(dict, "Title")?.let(::cleanTitle),
            author = stringField(dict, "Author")?.let(::cleanAuthor),
        )
    }

    /**
     * Word processors write their own document name into the title ("Microsoft Word - Chapter 1.docx"), and many tools
     * leave a placeholder ("Untitled"). Strips the former and drops the latter, so the file name is used instead.
     */
    fun cleanTitle(title: String): String? =
        title.replace(OfficePrefixRegex, "")
            .replace(DocumentExtensionRegex, "")
            .trim()
            .takeUnless { it.isEmpty() || it.lowercase() in PlaceholderTitles }

    /** Account names that authoring tools fill in for the author ("Administrator", "Owner") aren't authors. */
    fun cleanAuthor(author: String): String? = author.trim().takeUnless { it.isEmpty() || it.lowercase() in PlaceholderAuthors }

    private fun stringField(dict: String, name: String): String? {
        val key = Regex("/$name(?![A-Za-z0-9])\\s*").find(dict) ?: return null
        val start = key.range.last + 1
        val bytes = when (dict.getOrNull(start)) {
            '(' -> literalString(dict, start + 1)
            '<' -> hexString(dict, start + 1)
            else -> null
        } ?: return null
        return decodeTextString(bytes)
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotEmpty() && it.length <= MaxFieldLength && it.none { char -> char.isISOControl() } }
    }

    private fun literalString(text: String, from: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        var depth = 1
        var i = from
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length -> {
                    val next = text[i + 1]
                    i += 2
                    when (next) {
                        'n' -> out.write('\n'.code)
                        'r' -> out.write('\r'.code)
                        't' -> out.write('\t'.code)
                        'b' -> out.write('\b'.code)
                        'f' -> out.write(0x0C)
                        '\r' -> if (text.getOrNull(i) == '\n') i++ // line continuation
                        '\n' -> Unit
                        in '0'..'7' -> {
                            var value = next - '0'
                            var digits = 1
                            while (digits < 3 && text.getOrNull(i)?.let { it in '0'..'7' } == true) {
                                value = value * 8 + (text[i] - '0')
                                i++
                                digits++
                            }
                            out.write(value and 0xFF)
                        }
                        else -> out.write(next.code and 0xFF)
                    }
                    continue
                }
                c == '(' -> depth++
                c == ')' -> if (--depth == 0) return out.toByteArray()
            }
            out.write(c.code and 0xFF)
            i++
        }
        return null
    }

    private fun hexString(text: String, from: Int): ByteArray? {
        val end = text.indexOf('>', from).takeIf { it >= 0 } ?: return null
        val digits = text.substring(from, end).filter { !it.isWhitespace() }
        if (digits.any { Character.digit(it, 16) < 0 }) return null
        val padded = if (digits.length % 2 == 0) digits else digits + "0"
        return ByteArray(padded.length / 2) { index ->
            ((Character.digit(padded[index * 2], 16) shl 4) or Character.digit(padded[index * 2 + 1], 16)).toByte()
        }
    }

    // PDF text strings are UTF-16BE with a byte-order mark, UTF-8 with one (PDF 2.0), or PDFDocEncoding, which
    // matches Latin-1 for the printable characters titles use.
    private fun decodeTextString(bytes: ByteArray): String = when {
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        else -> String(bytes, Charsets.ISO_8859_1)
    }

    private fun ByteArray.latin1(): String = String(this, Charsets.ISO_8859_1)

    private val InfoRefRegex = Regex("/Info\\s+(\\d+)\\s+(\\d+)\\s+R")
    private val OfficePrefixRegex = Regex("^\\s*Microsoft\\s+(Word|PowerPoint|Excel)\\s+-\\s+", RegexOption.IGNORE_CASE)
    private val DocumentExtensionRegex =
        Regex("\\.(docx?|pdf|rtf|odt|pages|txt|indd|qxd|pptx?|xlsx?)\\s*$", RegexOption.IGNORE_CASE)
    private val PlaceholderTitles = setOf("untitled", "untitled document", "document", "title", "no title", "unknown", "none")
    private val PlaceholderAuthors = setOf("administrator", "admin", "user", "owner", "unknown", "author", "none")
}
