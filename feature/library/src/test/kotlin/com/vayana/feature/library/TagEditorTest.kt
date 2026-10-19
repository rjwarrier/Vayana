package com.vayana.feature.library

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TagEditorTest {
    @Test
    fun typedTagsAddEachCommaSeparatedTagOnceWhateverItsCase() {
        assertEquals(listOf("Fantasy", "sea", "Magic"), listOf("Fantasy").withTypedTags(" sea,  fantasy ,Magic, "))
    }

    @Test
    fun csvDropsWholeTagsPastTheLengthLimit() {
        val tags = List(40) { "tag-number-$it-padded-out" }
        val csv = tags.toTagsCsv()
        assertTrue(csv.length <= MaxBookTagsInputChars)
        assertTrue(csv.split(", ").all { it in tags })
    }

    @Test
    fun suggestionsSkipTagsAlreadyOnTheBookAndFollowUse() {
        val library = listOf(book(1, "Fiction, Sea"), book(2, "fiction, Magic"), book(3, "Fiction"))
        val byUse = library.tagsByUse()
        assertEquals("Fiction", byUse.first())
        assertEquals(listOf("Magic", "Sea"), byUse.suggestedTags(tags = listOf("FICTION"), input = ""))
        assertEquals(listOf("Magic"), byUse.suggestedTags(tags = emptyList(), input = "ag"))
    }

    private fun book(id: Long, tags: String) = Book(
        id = id, syncId = "$id", title = "Book $id", author = null, series = null, seriesNumber = null,
        description = null, coverPath = null, filePath = "", fileAvailability = BookFileAvailability.LOCAL,
        format = BookFormat.EPUB, fileHash = "$id", readingPercent = 0f, rating = 0f, createdAt = id, updatedAt = id,
        lastReadAt = null, lastLocator = null, tagsCsv = tags,
    )
}
