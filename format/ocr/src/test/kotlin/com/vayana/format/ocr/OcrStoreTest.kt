package com.vayana.format.ocr

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OcrStoreTest {
    private val dir = Files.createTempDirectory("ocr").toFile()
    private val book = "c".repeat(64)

    @Test
    fun `old style chillus become atomic and stray joiners go`() {
        // ന + virama + ZWJ, ര + virama + ZWJ, ല + virama + ZWJ, as Tesseract writes them
        assertEquals("സെൻട്രൽ റെയിൽ", normalizeRecognizedText("സെന്‍ട്രല്‍ റെയില്‍"))
        assertEquals("കൾ", normalizeRecognizedText("കള്‍"))
        assertEquals("എക്സ്പ്രസ്സ്", normalizeRecognizedText("എക്‌സ്പ്രസ്സ്"))
        // a virama without a joiner is a real conjunct break and stays
        assertEquals("വന്ന്", normalizeRecognizedText("വന്ന്"))
    }

    @Test
    fun `a saved page reads back as it was`() {
        val store = OcrStore(dir)
        val page = RecognizedPage(listOf(
            listOf(RecognizedWord("ചെന്നൈ", 50.4f, 50.88f, 75.12f, 60.48f, 91f), RecognizedWord("എന്ന", 80f, 51f, 99.5f, 60f, 87f)),
            listOf(RecognizedWord("2018", 50f, 70f, 70f, 80f, 64f)),
        ))
        store.save(book, 10, page, OcrModel.MalayalamFast.id)
        assertEquals(page, store.read(book, 10))
        assertEquals(setOf(10), store.savedPages(book))
        assertNull(store.read(book, 11))
    }

    @Test
    fun `keys and pages that could leave the store are refused`() {
        val store = OcrStore(dir)
        store.save("../$book", 1, RecognizedPage(emptyList()), "m")
        store.save(book, -1, RecognizedPage(emptyList()), "m")
        assertEquals(0, dir.walk().count { it.isFile })
    }

    @Test
    fun `recognition starts at the page being read and works outwards, skipping saved pages`() {
        assertEquals(listOf(5, 6, 4, 7, 3, 8, 9), pagesToRecognize((3..9).toList(), saved = emptySet(), startPage = 5))
        assertEquals(listOf(6, 7, 3), pagesToRecognize((3..7).toList(), saved = setOf(4, 5), startPage = 5))
        assertEquals(listOf(0, 1, 2), pagesToRecognize(listOf(2, 0, 1), saved = emptySet(), startPage = 0))
    }

    @Test
    fun `a model is installed only when its size and SHA-256 match`() {
        val target = File(dir, "tessdata/mal.traineddata")
        val bytes = "model".toByteArray()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        assertThrows<IOException> { installVerified("other".byteInputStream(), "mal", 5, sha256, target) }
        assertThrows<IOException> { installVerified("model!".byteInputStream(), "mal", 5, sha256, target) }
        assertFalse(target.exists())
        assertEquals(listOf<String>(), target.parentFile.list()!!.toList())

        installVerified(bytes.inputStream(), "mal", 5, sha256, target)
        assertTrue(target.readBytes().contentEquals(bytes))
    }
}
