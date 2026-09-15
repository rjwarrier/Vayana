package com.vayana.core.common

/**
 * Tags on highlights live in the note text as hashtags ("#idea", "#to-do"), so they sync, search and export
 * with the note without a schema of their own.
 */
object HighlightTags {
    private val TagRegex = Regex("""(?<![\p{L}\p{N}_#])#([\p{L}\p{N}][\p{L}\p{N}_-]*)""")

    /** Distinct tags in [note], lower-cased, in the order they first appear. */
    fun parse(note: String?): List<String> {
        if (note.isNullOrBlank() || '#' !in note) return emptyList()
        return TagRegex.findAll(note).map { it.groupValues[1].lowercase() }.distinct().toList()
    }

    /** [note] with `#tag` appended, unless it already carries that tag. */
    fun add(note: String, tag: String): String {
        if (tag.lowercase() in parse(note)) return note
        val separator = if (note.isBlank() || note.last().isWhitespace()) "" else " "
        return "$note$separator#$tag"
    }
}

/** Letters and digits only, lower-cased: two quotes differing just in quote marks, dashes or spacing are the same quote. */
fun quoteMatchKey(text: String): String =
    buildString(text.length) {
        for (char in text) if (char.isLetterOrDigit()) append(char.lowercaseChar())
    }
