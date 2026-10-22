package com.vayana.format.ocr

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
    fun `readable Text view is available when the default locale formats non-Latin digits`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            val store = OcrStore(dir)
            store.save(book, 0, RecognizedPage(listOf(listOf(RecognizedWord("Readable", 0f, 0f, 50f, 8f, 90f)))), "eng")
            store.buildTextEpub(book, "Readable scan", null, "en")
            assertTrue(store.textEpub(book)?.isFile == true)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `empty recognition never publishes Text view and readable recognition can replace it`() {
        val store = OcrStore(dir)
        store.save(book, 0, RecognizedPage(emptyList()), "eng")
        store.saveLineJoins(book)
        store.buildTextEpub(book, "Blank scan", null, "en")
        assertNull(store.textEpub(book))
        assertTrue(store.textPageMap(book).isEmpty())

        assertTrue(store.pageFile(book, 0)?.delete() == true)
        store.save(book, 0, RecognizedPage(listOf(listOf(RecognizedWord("Readable", 0f, 0f, 50f, 8f, 90f)))), "eng")
        store.buildTextEpub(book, "Readable scan", null, "en")
        assertTrue(store.textEpub(book)?.isFile == true)
        assertEquals(listOf("OEBPS/c001.xhtml"), store.textPageMap(book))

        assertTrue(store.pageFile(book, 0)?.delete() == true)
        store.save(book, 0, RecognizedPage(emptyList()), "eng")
        store.buildTextEpub(book, "Blank again", null, "en")
        assertNull(store.textEpub(book))
        assertTrue(store.textPageMap(book).isEmpty())
    }

    @Test
    fun `an empty EPUB created by an older version is not available as Text view`() {
        val store = OcrStore(dir)
        val bookDir = File(dir, book).apply { mkdirs() }
        writeTextEpub(TextBook(emptyList()), "Old blank scan", null, "en",
            File(bookDir, "text-v5.epub"), File(bookDir, "text-v5-pages.json"), 1)
        assertNull(store.textEpub(book))
    }

    @Test
    fun `page markers without readable words do not make Text view available`() {
        val store = OcrStore(dir)
        store.save(book, 0, RecognizedPage(listOf(listOf(RecognizedWord("1", 0f, 0f, 10f, 8f, 90f)))), "eng")
        store.buildTextEpub(book, "Page number only", null, "en")
        assertNull(store.textEpub(book))
    }

    @Test
    fun `old style chillus become atomic and stray joiners go`() {
        // ന + virama + ZWJ, ര + virama + ZWJ, ല + virama + ZWJ, as Tesseract writes them
        assertEquals("സെൻട്രൽ റെയിൽ", normalizeRecognizedText("സെന്‍ട്രല്‍ റെയില്‍"))
        assertEquals("കൾ", normalizeRecognizedText("കള്‍"))
        assertEquals("എക്സ്പ്രസ്സ്", normalizeRecognizedText("എക്‌സ്പ്രസ്സ്"))
        // a virama without a joiner is a real conjunct break and stays
        assertEquals("വന്ന്", normalizeRecognizedText("വന്ന്"))
        // Malayalam digits become the 0-9 books print
        assertEquals("2018 ജനുവരി 23", normalizeRecognizedText("൨൦൧൮ ജനുവരി ൨൩"))
    }

    @Test
    fun `a saved page reads back as it was`() {
        val store = OcrStore(dir)
        val page = RecognizedPage(listOf(
            listOf(RecognizedWord("ചെന്നൈ", 50.4f, 50.88f, 75.12f, 60.48f, 91f), RecognizedWord("എന്ന", 80f, 51f, 99.5f, 60f, 87f)),
            listOf(RecognizedWord("2018", 50f, 70f, 70f, 80f, 64f)),
        ), joinedLines = setOf(0))
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

    @Test
    fun `old abandoned model partials are removed without touching recent downloads or models`() {
        val models = File(dir, "tessdata").apply { mkdirs() }
        val abandoned = File(models, "eng.traineddata.123.part").apply {
            writeText("partial")
            assertTrue(setLastModified(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L))
        }
        val downloading = File(models, "eng.traineddata.456.part").apply { writeText("active") }
        val installed = File(models, "eng.traineddata").apply {
            writeText("verified model")
            assertTrue(setLastModified(abandoned.lastModified()))
        }

        OcrModelFiles(dir)

        assertFalse(abandoned.exists())
        assertEquals("active", downloading.readText())
        assertEquals("verified model", installed.readText())
    }

    @Test
    fun `two books can install the same new model without sharing partial files`() {
        val target = File(dir, "tessdata/eng.traineddata")
        val bytes = "model".toByteArray()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val opened = CountDownLatch(1)
        val release = CountDownLatch(1)
        val delayed = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                opened.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return super.read(buffer, offset, length)
            }
        }
        val workers = Executors.newFixedThreadPool(2)
        try {
            val first = workers.submit { installVerified(delayed, "eng", bytes.size.toLong(), sha256, target) }
            assertTrue(opened.await(5, TimeUnit.SECONDS))
            try {
                workers.submit { installVerified(bytes.inputStream(), "eng", bytes.size.toLong(), sha256, target) }
                    .get(5, TimeUnit.SECONDS)
            } finally {
                release.countDown()
            }
            first.get(5, TimeUnit.SECONDS)
            assertTrue(target.readBytes().contentEquals(bytes))
            assertEquals(listOf(target.name), target.parentFile.list()!!.toList())
        } finally {
            release.countDown()
            workers.shutdownNow()
        }
    }
}
