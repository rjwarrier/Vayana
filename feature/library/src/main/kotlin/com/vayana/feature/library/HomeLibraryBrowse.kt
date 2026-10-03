package com.vayana.feature.library

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.HomeLibraryDetails
import com.vayana.core.database.model.PhysicalBookOwnership
import java.text.Normalizer
import java.util.Locale

internal val Book.homeLibraryOriginalTitle: String?
    get() = if (isHomeLibrary) HomeLibraryDetails.fromJson(sourceMetadata).originalScriptTitle?.trim()
        ?.takeIf { it.isNotEmpty() && it != title } else null

internal val Book.homeLibraryDisplayTitle: String
    get() = homeLibraryOriginalTitle ?: title

internal data class HomeLibraryBrowseEntry(val book: Book, val details: HomeLibraryDetails) {
    val language: String? = details.languageCode?.homeLibraryLanguageCode()
    val genres: List<String> = (listOfNotNull(details.mainGenre) + details.subGenres)
        .map(String::trim).filter(String::isNotEmpty).distinct()
    val location: String = details.location.joinToString(" · ")
    private val searchable = listOfNotNull(book.title, details.originalScriptTitle, book.author,
        book.series, book.tagsCsv, details.subtitle, details.languageCode, details.isbn13,
        details.isbn10, location).plus(genres).joinToString(" ").searchNormalized()

    fun matches(query: String, fromShelves: Boolean, language: String?, genre: String?): Boolean {
        return matches(HomeLibraryBrowseQuery(query), fromShelves, language, genre)
    }

    fun matches(query: HomeLibraryBrowseQuery, fromShelves: Boolean, language: String?, genre: String?): Boolean {
        if (fromShelves && (!book.isHomeLibrary || book.readingState() != BookReadingState.NOT_STARTED ||
                (book.format == BookFormat.PHYSICAL && book.physicalOwnership == PhysicalBookOwnership.BORROWED))) return false
        if (language != null && this.language != language) return false
        if (genre != null && genre !in genres) return false
        return query.tokens.all { it in searchable }
    }
}

/** Prepare once per query change, rather than normalizing and allocating tokens for every book. */
internal class HomeLibraryBrowseQuery(query: String) {
    val tokens: List<String> = query.searchNormalized().split(QueryWhitespace).filter(String::isNotEmpty)
}

internal fun validHomeLibraryLanguageSelection(selected: String?, choices: List<String>): String? =
    selected?.homeLibraryLanguageCode()?.takeIf { it in choices }

private fun String.homeLibraryLanguageCode(): String? {
    val tag = trim().replace('_', '-').lowercase(Locale.ROOT).takeIf { it.isNotEmpty() } ?: return null
    return Locale.forLanguageTag(tag).language.ifBlank { tag }
}

internal fun homeLibraryBrowseEntries(books: List<Book>): List<HomeLibraryBrowseEntry> = books.map {
    HomeLibraryBrowseEntry(it, if (it.isHomeLibrary) HomeLibraryDetails.fromJson(it.sourceMetadata) else HomeLibraryDetails())
}

private fun String.searchNormalized(): String = Normalizer.normalize(this, Normalizer.Form.NFC).lowercase(Locale.ROOT)

internal fun homeLibraryLanguageLabel(code: String): String = Locale.forLanguageTag(code.replace('_', '-'))
    .getDisplayLanguage(Locale.getDefault()).takeIf { it.isNotBlank() } ?: code

private val QueryWhitespace = Regex("\\s+")
