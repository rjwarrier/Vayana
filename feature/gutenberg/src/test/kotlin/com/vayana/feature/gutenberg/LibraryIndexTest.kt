package com.vayana.feature.gutenberg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibraryIndexTest {
    private val index = LibraryIndex(
        listOf(
            LibraryBook(1, "Frankenstein; Or, The Modern Prometheus", "Mary Wollstonecraft Shelley"),
            LibraryBook(2, "Pride and Prejudice", "Austen, Jane"),
            LibraryBook(3, "Poems", "Emily Dickinson"),
            LibraryBook(4, "Les Misérables", null, gutenbergId = 135),
        ),
    )

    @Test
    fun matchesDespiteCaseSubtitlesAndAuthorOrder() {
        assertEquals(1L, index.find(0, "Frankenstein; or, the modern prometheus", "Mary Wollstonecraft Shelley"))
        assertEquals(2L, index.find(0, "Pride and Prejudice", "Jane Austen"))
    }

    @Test
    fun accentsAndMissingAuthorsStillMatch() {
        assertEquals(4L, index.find(0, "Les Miserables", "Victor Hugo"))
        assertEquals(2L, index.find(0, "Pride and prejudice", null))
    }

    @Test
    fun sameTitleByAnotherAuthorIsNotAMatch() {
        assertNull(index.find(0, "Poems", "William Blake"))
        assertNull(index.find(0, "Moby Dick", "Herman Melville"))
    }

    @Test
    fun stableGutenbergIdentityWinsOverMetadataDifferences() {
        assertEquals(4L, index.find(135, "A completely different title", "Another author"))
    }
}
