package com.vayana.format.ocr

import java.nio.file.Files
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OcrSearchTest {
    @Test
    fun `only finalized pages are searchable and their revision survives restarting the store`() {
        val root = Files.createTempDirectory("ocr-search").toFile()
        val hash = "a".repeat(64)
        val store = OcrStore(root)
        val words = RecognizedPage(listOf(listOf(RecognizedWord("മലയാളം", 0f, 0f, 40f, 8f, 90f))))
        store.save(hash, 7, words, "mal-fast")
        assertNull(store.searchRevision(hash))
        assertTrue(store.searchPages(hash).none())
        store.saveLineJoins(hash)
        val firstRevision = store.searchRevision(hash)
        assertNotNull(firstRevision)
        assertEquals(firstRevision, OcrStore(root).searchRevision(hash))
        assertEquals(listOf(RecognizedTextPage(7, "മലയാളം")), store.searchPages(hash).toList())

        store.save(hash, 8, words, "mal-fast")
        assertNull(store.searchRevision(hash))
        store.saveLineJoins(hash)
        assertNotEquals(firstRevision, store.searchRevision(hash))
        store.delete(hash)
        assertNull(store.searchRevision(hash))
        assertTrue(store.searchPages(hash).none())
    }

    @Test
    fun `joined line ends are searchable as whole words and empty pages are skipped`() {
        val root = Files.createTempDirectory("ocr-search-joins").toFile()
        val hash = "b".repeat(64)
        val store = OcrStore(root)
        val word = { text: String, top: Float -> RecognizedWord(text, 0f, top, 40f, top + 8f, 90f) }
        store.save(hash, 0, RecognizedPage(listOf(listOf(word("കിലോമീറ്ററു", 0f)), listOf(word("കൾ", 12f)))), "mal-fast")
        store.save(hash, 1, RecognizedPage(emptyList()), "mal-fast")
        store.saveLineJoins(hash)
        assertEquals(listOf(RecognizedTextPage(0, "കിലോമീറ്ററുകൾ")), store.searchPages(hash).toList())
    }
}
