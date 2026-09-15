package com.vayana.core.common

/** One highlight from a Kindle "My Clippings.txt", with the note typed at its end, if any. */
data class KindleHighlight(
    val text: String,
    val note: String?,
    val location: Int?,
)

data class KindleBookClippings(
    val title: String,
    val author: String?,
    val highlights: List<KindleHighlight>,
    /** Notes that don't sit at the end of any highlight. */
    val looseNotes: List<String>,
)

/**
 * Reads the "My Clippings.txt" file every Kindle keeps. Entries are separated by a line of `=`; each one is the
 * book line ("Title (Author)"), a metadata line ("- Your Highlight on page 3 | Location 40-42 | Added on ..."), a
 * blank line and the text. Bookmarks are dropped. A highlight re-saved after being extended appears twice, so a
 * highlight contained in a later one of the same book is dropped too.
 */
object KindleClippingsParser {
    private val SeparatorRegex = Regex("""^=+\s*$""")
    private val AuthorRegex = Regex("""^(.*)\(([^()]*)\)\s*$""")
    private val LocationRegex = Regex("""(?:location|loc\.|position|posición|emplacement)\s*(\d+)(?:\s*-\s*(\d+))?""", RegexOption.IGNORE_CASE)
    private val NoteRegex = Regex("""\b(note|notiz|nota)\b""", RegexOption.IGNORE_CASE)
    private val BookmarkRegex = Regex("""\b(bookmark|lesezeichen|signet|marcador|segnalibro)\b""", RegexOption.IGNORE_CASE)

    fun parse(rawText: String): List<KindleBookClippings> {
        val entries = rawText.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
            .split('\n')
            .fold(mutableListOf(mutableListOf<String>())) { groups, line ->
                if (SeparatorRegex.matches(line)) groups.add(mutableListOf()) else groups.last().add(line)
                groups
            }
            .mapNotNull(::parseEntry)

        return entries.groupBy { it.title to it.author }.map { (book, bookEntries) ->
            val highlights = bookEntries.filter { it.kind == Kind.HIGHLIGHT }.dropExtended()
            val notes = bookEntries.filter { it.kind == Kind.NOTE }
            val attached = mutableSetOf<Entry>()
            val withNotes = highlights.map { highlight ->
                val note = notes.firstOrNull { note -> note !in attached && note.isAtEndOf(highlight) }
                note?.let(attached::add)
                KindleHighlight(text = highlight.text, note = note?.text, location = highlight.start)
            }
            KindleBookClippings(
                title = book.first,
                author = book.second,
                highlights = withNotes,
                looseNotes = notes.filterNot { it in attached }.map { it.text },
            )
        }
    }

    private fun parseEntry(lines: List<String>): Entry? {
        val content = lines.dropWhile { it.isBlank() }
        if (content.size < 2) return null
        val bookLine = content[0].trim().removePrefix("﻿")
        val metadata = content[1].trim()
        if (!metadata.startsWith("-")) return null
        val text = content.drop(2).joinToString("\n").trim()
        val kind = when {
            BookmarkRegex.containsMatchIn(metadata) -> return null
            NoteRegex.containsMatchIn(metadata) -> Kind.NOTE
            else -> Kind.HIGHLIGHT
        }
        if (text.isBlank()) return null
        val authorMatch = AuthorRegex.matchEntire(bookLine)
        val title = (authorMatch?.groupValues?.get(1) ?: bookLine).trim()
        if (title.isEmpty()) return null
        val location = LocationRegex.find(metadata)
        val start = location?.groupValues?.get(1)?.toIntOrNull()
        val end = location?.groupValues?.get(2)?.toIntOrNull()?.let { tail -> expandLocationEnd(start, tail) } ?: start
        return Entry(
            title = title,
            author = authorMatch?.groupValues?.get(2)?.trim()?.ifEmpty { null },
            kind = kind,
            start = start,
            end = end,
            text = text,
        )
    }

    /** Kindle writes some ranges shortened ("1234-56"); widen the end to the start's magnitude. */
    private fun expandLocationEnd(start: Int?, end: Int): Int {
        if (start == null || end >= start) return end
        val startText = start.toString()
        val endText = end.toString()
        return (startText.dropLast(endText.length) + endText).toIntOrNull()?.takeIf { it >= start } ?: end
    }

    private fun List<Entry>.dropExtended(): List<Entry> {
        val keys = map { quoteMatchKey(it.text) }
        return filterIndexed { index, _ -> (index + 1 until size).none { later -> keys[index] in keys[later] } }
    }

    private fun Entry.isAtEndOf(highlight: Entry): Boolean {
        val noteAt = start ?: return false
        val from = highlight.start ?: return false
        return noteAt in from..(highlight.end ?: from)
    }

    private enum class Kind { HIGHLIGHT, NOTE }

    private data class Entry(
        val title: String,
        val author: String?,
        val kind: Kind,
        val start: Int?,
        val end: Int?,
        val text: String,
    )
}
