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
            LibraryBook(4, "Les Misérables", null),
        ),
    )

    @Test
    fun matchesDespiteCaseSubtitlesAndAuthorOrder() {
        assertEquals(1L, index.find("Frankenstein; or, the modern prometheus", "Mary Wollstonecraft Shelley"))
        assertEquals(2L, index.find("Pride and Prejudice", "Jane Austen"))
    }

    @Test
    fun accentsAndMissingAuthorsStillMatch() {
        assertEquals(4L, index.find("Les Miserables", "Victor Hugo"))
        assertEquals(2L, index.find("Pride and prejudice", null))
    }

    @Test
    fun sameTitleByAnotherAuthorIsNotAMatch() {
        assertNull(index.find("Poems", "William Blake"))
        assertNull(index.find("Moby Dick", "Herman Melville"))
    }
}
