package com.vayana.feature.gutenberg

import java.text.Normalizer
import java.util.LinkedHashMap

/**
 * The library's books, keyed for spotting a Gutenberg book already in it. Gutenberg's titles and those in its EPUBs
 * agree once case, accents, punctuation and subtitles ("Frankenstein; or, the modern prometheus") are set aside;
 * where both sides name an author, one surname-length word must also be shared, so two different "Poems" don't match.
 */
class LibraryIndex(books: List<LibraryBook>) {
    private data class IndexedBook(val id: Long, val authorWords: Set<String>)
    private data class Lookup(val title: String, val author: String?)

    private val byTitle: Map<String, List<IndexedBook>> = books
        .groupBy({ titleKey(it.title) }, { IndexedBook(it.id, authorWords(it.author)) })
    private val byGutenbergId: Map<Long, Long> = books.mapNotNull { book -> book.gutenbergId?.let { it to book.id } }.toMap()
    private val matches = object : LinkedHashMap<Lookup, Long?>(MatchCacheEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Lookup, Long?>?) = size > MatchCacheEntries
    }

    /** The library book this Gutenberg book is, or null. */
    fun find(gutenbergId: Long, title: String, author: String?): Long? {
        byGutenbergId[gutenbergId]?.let { return it }
        val lookup = Lookup(title, author)
        synchronized(matches) { if (matches.containsKey(lookup)) return matches[lookup] }
        val candidates = byTitle[titleKey(title)]
        val match = candidates?.let {
            val words = authorWords(author)
            it.firstOrNull { book ->
                words.isEmpty() || book.authorWords.isEmpty() || words.any { word -> word in book.authorWords }
            }?.id
        }
        synchronized(matches) { matches[lookup] = match }
        return match
    }

    companion object {
        val Empty = LibraryIndex(emptyList())

        internal fun titleKey(title: String): String {
            val main = title.split(';', ':', '—', '(').first()
            return Normalizer.normalize(main, Normalizer.Form.NFD)
                .replace(MarksRegex, "")
                .lowercase()
                .filter { it.isLetterOrDigit() }
        }

        private fun authorWords(author: String?): Set<String> =
            author.orEmpty()
                .let { Normalizer.normalize(it, Normalizer.Form.NFD).replace(MarksRegex, "").lowercase() }
                .split(WordSeparatorRegex)
                .filter { it.length >= MinAuthorWordLength }
                .toSet()

        private val MarksRegex = Regex("\\p{M}+")
        private val WordSeparatorRegex = Regex("[^\\p{L}\\p{N}]+")
        private const val MinAuthorWordLength = 3
        private const val MatchCacheEntries = 256
    }
}

data class LibraryBook(val id: Long, val title: String, val author: String?, val gutenbergId: Long? = null)
