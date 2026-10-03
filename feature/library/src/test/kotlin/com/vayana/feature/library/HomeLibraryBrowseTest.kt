package com.vayana.feature.library

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.BookSource
import com.vayana.core.database.model.HomeLibraryDetails
import com.vayana.core.database.model.PhysicalBookOwnership
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeLibraryBrowseTest {
    private val details = HomeLibraryDetails(
        originalScriptTitle = "മഞ്ഞ്", languageCode = "ml_IN", mainGenre = "Fiction",
        subGenres = listOf("Novel"), room = "Study", bookcase = "Case 2", shelf = "Shelf 3",
    )

    @Test
    fun nativeTitleIsPreferredWithCatalogTitlePreserved() {
        val book = book()
        assertEquals("മഞ്ഞ്", book.homeLibraryDisplayTitle)
        assertEquals("Manju", book.title)
        assertNull(book.copy(source = null).homeLibraryOriginalTitle)
        assertEquals("Manju", book.copy(source = null).homeLibraryDisplayTitle)
        assertNull(book.copy(sourceMetadata = details.copy(originalScriptTitle = "Manju").toJson()).homeLibraryOriginalTitle)
    }

    @Test
    fun searchesBothScriptsAuthorGenreIsbnAndShelfAcrossWords() {
        val entry = homeLibraryBrowseEntries(listOf(book())).single()
        for (query in listOf("മഞ്ഞ്", "MANJU", "Basheer", "Study Shelf 3", "Novel", "")) {
            assertTrue(entry.matches(query, false, null, null), query)
        }
        assertFalse(entry.matches("Shelf 4", false, null, null))
        val isbnEntry = homeLibraryBrowseEntries(listOf(book().copy(
            sourceMetadata = details.copy(isbn13 = "9781234567890").toJson(),
        ))).single()
        assertTrue(isbnEntry.matches("9781234567890", false, null, null))
    }

    @Test
    fun searchNormalizesCanonicallyEquivalentUnicode() {
        val entry = homeLibraryBrowseEntries(listOf(book().copy(title = "Café"))).single()
        assertTrue(entry.matches("Cafe\u0301", false, null, null))
    }

    @Test
    fun shelfSelectionExcludesLoansLocalBooksAndReadingOrFinishedBooks() {
        val eligible = book()
        val books = listOf(eligible,
            eligible.copy(id = 2, physicalOwnership = PhysicalBookOwnership.BORROWED),
            eligible.copy(id = 3, source = null),
            eligible.copy(id = 4, startedReadingAt = 10),
            eligible.copy(id = 5, finishedReadingAt = 20),
            eligible.copy(id = 6, readingPercent = 0.5f),
            eligible.copy(id = 7, readingPercent = 1f),
        )
        assertEquals(listOf(1L), homeLibraryBrowseEntries(books)
            .filter { it.matches("", true, null, null) }.map { it.book.id })
    }

    @Test
    fun languageAndGenreFiltersCombineWithSearch() {
        val entry = homeLibraryBrowseEntries(listOf(book())).single()
        assertEquals("ml", entry.language)
        assertTrue(entry.matches("Study", true, "ml", "Novel"))
        assertFalse(entry.matches("Study", true, "ta", "Novel"))
        assertFalse(entry.matches("Study", true, "ml", "History"))
    }

    @Test
    fun languageVariantsShareOneFilterAndBlankCodesAreIgnored() {
        val books = listOf("ml", "ml_IN", "ml-IN", "ML-in", " ").mapIndexed { index, code ->
            book().copy(id = index.toLong(), sourceMetadata = details.copy(languageCode = code).toJson())
        }
        val entries = homeLibraryBrowseEntries(books)
        assertEquals(listOf("ml"), entries.mapNotNull { it.language }.distinct())
        val query = HomeLibraryBrowseQuery("")
        assertEquals(4, entries.count { it.matches(query, true, "ml", null) })
    }

    @Test
    fun restoredLanguageFiltersNormalizeOrClearAfterCatalogChanges() {
        assertEquals("ml", validHomeLibraryLanguageSelection("ml-in", listOf("ml", "ta")))
        assertNull(validHomeLibraryLanguageSelection("ml-in", listOf("ta")))
        assertNull(validHomeLibraryLanguageSelection(null, listOf("ml")))
        assertNull(validHomeLibraryLanguageSelection("ml", emptyList()))
    }

    @Test
    fun onePreparedQueryCanBeReusedAcrossLargeCatalog() {
        val entries = homeLibraryBrowseEntries((1L..2_000L).map { id ->
            book().copy(id = id, title = if (id % 2 == 0L) "Café" else "Tea")
        })
        val query = HomeLibraryBrowseQuery("  CAFE\u0301\tStudy\nShelf   3  ")
        assertEquals(listOf("café", "study", "shelf", "3"), query.tokens)
        assertEquals(1_000, entries.count { it.matches(query, true, "ml", "Fiction") })
    }

    @Test
    fun malformedMetadataFallsBackWithoutInventingNativeTitleOrLocation() {
        val book = book().copy(sourceMetadata = "not json")
        assertEquals("Manju", book.homeLibraryDisplayTitle)
        val entry = homeLibraryBrowseEntries(listOf(book)).single()
        assertEquals("", entry.location)
        assertNull(entry.language)
        assertTrue(entry.matches("Manju", true, null, null))
    }

    private fun book() = Book(
        id = 1, syncId = "home-1", title = "Manju", author = "Basheer", series = null,
        seriesNumber = null, description = null, coverPath = null, filePath = "",
        fileAvailability = BookFileAvailability.LOCAL, format = BookFormat.PHYSICAL,
        fileHash = "physical:home-1", readingPercent = 0f, rating = 0f, createdAt = 1,
        updatedAt = 1, lastReadAt = null, lastLocator = null,
        physicalOwnership = PhysicalBookOwnership.OWNED, source = BookSource.HOME_LIBRARY,
        sourceMetadata = details.toJson(),
    )
}
