package com.vayana.feature.gutenberg

import java.text.Normalizer

/**
 * The library's books, keyed for spotting a Gutenberg book already in it. Gutenberg's titles and those in its EPUBs
 * agree once case, accents, punctuation and subtitles ("Frankenstein; or, the modern prometheus") are set aside;
 * where both sides name an author, one surname-length word must also be shared, so two different "Poems" don't match.
 */
class LibraryIndex(books: List<LibraryBook>) {
    private val byTitle: Map<String, List<LibraryBook>> = books.groupBy { titleKey(it.title) }

    /** The library book this Gutenberg book is, or null. */
    fun find(title: String, author: String?): Long? {
        val candidates = byTitle[titleKey(title)] ?: return null
        val words = authorWords(author)
        return candidates.firstOrNull { book ->
            val bookWords = authorWords(book.author)
            words.isEmpty() || bookWords.isEmpty() || words.any { it in bookWords }
        }?.id
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
    }
}

data class LibraryBook(val id: Long, val title: String, val author: String?)
