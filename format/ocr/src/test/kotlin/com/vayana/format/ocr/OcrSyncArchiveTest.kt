package com.vayana.format.ocr

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OcrSyncArchiveTest {
    @Test fun `sync skips a busy recognition writer without waiting`(): Unit = runBlocking {
        val held = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writer = launch { RecognitionAccess.book(book) { held.complete(Unit); release.await() } }
        held.await()
        try {
            assertThrows<RecognitionBusyException> { runBlocking { RecognitionAccess.idleBook(book) { fail<Unit>("Must not access a busy book") } } }
        } finally { release.complete(Unit); writer.join() }
    }
    private val book = "b".repeat(64)
    private fun recognized(store: OcrStore) {
        store.recordPageCount(book, 3)
        repeat(3) { page -> store.save(book, page, RecognizedPage(listOf(listOf(RecognizedWord("മലയാളം $page", 0f, 0f, 40f, 12f, 91f)))), "mal-fast") }
        store.saveLineJoins(book)
        store.buildTextEpub(book, "Ram C/O Anandi", null, "ml")
    }
    private fun withStores(action: (OcrStore, OcrStore) -> Unit) {
        val source = OcrStore(Files.createTempDirectory("ocr-source").toFile())
        val target = OcrStore(Files.createTempDirectory("ocr-target").toFile())
        try { recognized(source); action(source, target) } finally { source.root.deleteRecursively(); target.root.deleteRecursively() }
    }

    @Test fun `complete archives round trip text boxes joins and text view`() = withStores { source, target ->
        val archive = OcrSyncArchives.capture(source, book)!!
        OcrSyncArchives.restore(target, book, archive)
        assertEquals(archive.revision, OcrSyncArchives.capture(target, book)!!.revision)
        assertEquals(source.read(book, 1), target.read(book, 1))
        assertEquals(source.searchPages(book).toList(), target.searchPages(book).toList())
        assertEquals(source.textPageMap(book), target.textPageMap(book))
        assertArrayEquals(source.textEpub(book)!!.readBytes(), target.textEpub(book)!!.readBytes())
        // Keep the received ZIP itself in cache; verifying/restoring it must not re-encode the archive.
        assertArrayEquals(archive.bytes, File(target.root, "$book/.sync-v1.zip").readBytes())
        assertArrayEquals(archive.bytes, OcrSyncArchives.capture(target, book)!!.bytes)
        assertTrue(target.root.listFiles()!!.all { it.name == book })
    }

    @Test fun `unchanged captures use cache and changed recognition invalidates it`() = withStores { source, _ ->
        val first = OcrSyncArchives.capture(source, book)!!
        val cached = File(source.root, "$book/.sync-v1.zip")
        cached.setLastModified(1_700_000_000_000)
        val again = OcrSyncArchives.capture(source, book)!!
        assertEquals(1_700_000_000_000, cached.lastModified())
        assertEquals(first.revision, again.revision)
        source.save(book, 1, RecognizedPage(listOf(listOf(RecognizedWord("Improved", 0f, 0f, 40f, 12f, 99f)))), "eng")
        assertNull(OcrSyncArchives.capture(source, book))
        source.saveLineJoins(book); source.buildTextEpub(book, "Updated", null, "en")
        assertNotEquals(first.revision, OcrSyncArchives.capture(source, book)!!.revision)
    }

    @Test fun `corrupt archive cache is rebuilt`() = withStores { source, _ ->
        val first = OcrSyncArchives.capture(source, book)!!
        File(source.root, "$book/.sync-v1.zip").writeText("corrupt")
        val recovered = OcrSyncArchives.capture(source, book)!!
        assertEquals(first.revision, recovered.revision)
        assertEquals(first.sha256, recovered.sha256)
    }

    @Test fun `incomplete pages never publish text as complete`() = withStores { source, _ ->
        source.pageFile(book, 1)!!.delete()
        assertNull(OcrSyncArchives.capture(source, book))
    }

    @Test fun `a contiguous partial run is not mistaken for the whole pdf`() = withStores { source, _ ->
        source.pageFile(book, 2)!!.delete()
        source.saveLineJoins(book)
        source.buildTextEpub(book, "First two pages only", null, "ml")
        assertEquals(2, source.textPageMap(book).size)
        assertEquals(3, source.pageCount(book))
        assertNull(OcrSyncArchives.capture(source, book))
    }

    @Test fun `page maps must refer to chapters in the text epub`() = withStores { source, _ ->
        File(source.root, "$book/$TextPageMapName").writeText("[\"OEBPS/missing.xhtml\",\"OEBPS/missing.xhtml\",\"OEBPS/missing.xhtml\"]")
        assertThrows<IllegalArgumentException> { OcrSyncArchives.capture(source, book) }
    }

    @Test fun `invalid downloads never replace existing recognized text`() = withStores { source, target ->
        recognized(target)
        val before = OcrSyncArchives.capture(target, book)!!.revision
        val archive = OcrSyncArchives.capture(source, book)!!
        assertThrows<IllegalArgumentException> { OcrSyncArchives.restore(target, book, archive.copy(bytes = "broken".toByteArray())) }
        assertThrows<IllegalArgumentException> { OcrSyncArchives.restore(target, "c".repeat(64), archive) }
        assertThrows<IllegalArgumentException> { OcrSyncArchives.restore(target, book, archive.copy(revision = "0".repeat(64))) }
        assertEquals(before, OcrSyncArchives.capture(target, book)!!.revision)
    }

    @Test fun `unsafe archive entries and decompression bombs are rejected`() = withStores { _, target ->
        fun archive(name: String, blocks: Int): OcrSyncArchive {
            val bytes = ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(name))
                val block = ByteArray(1024 * 1024)
                repeat(blocks) { zip.write(block) }
                zip.closeEntry()
            } }.toByteArray()
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            return OcrSyncArchive(bytes, hash, 1, "0".repeat(64))
        }
        assertThrows<IllegalArgumentException> { OcrSyncArchives.restore(target, book, archive("../escape", 1)) }
        assertThrows<IllegalArgumentException> { OcrSyncArchives.restore(target, book, archive("0.json", 33)) }
        assertFalse(File(target.root.parentFile, "escape").exists())
    }

    @Test fun `cancellation leaves the existing book intact`() = withStores { source, target ->
        recognized(target)
        val before = OcrSyncArchives.capture(target, book)!!.revision
        val archive = OcrSyncArchives.capture(source, book)!!
        var checks = 0
        assertThrows<CancellationException> { OcrSyncArchives.restore(target, book, archive) { if (++checks == 4) throw CancellationException() } }
        assertEquals(before, OcrSyncArchives.capture(target, book)!!.revision)
    }

    @Test fun `opening the store recovers a process death between directory moves`() = withStores { _, target ->
        recognized(target)
        val original = OcrSyncArchives.capture(target, book)!!.revision
        val stage = File(target.root.parentFile, ".${target.root.name}-sync-${UUID.randomUUID()}").apply { mkdirs() }
        Files.move(File(target.root, book).toPath(), File(stage, "previous").toPath())
        File(target.root, ".restore-$book.json").writeText(JSONObject().put("staging", stage.name).toString())
        val recovered = OcrStore(target.root)
        assertEquals(original, OcrSyncArchives.capture(recovered, book)!!.revision)
        assertFalse(stage.exists())
        assertFalse(File(target.root, ".restore-$book.json").exists())
    }

    @Test fun `recovery keeps a new installation that already reached its destination`() = withStores { source, target ->
        recognized(target)
        val expected = OcrSyncArchives.capture(source, book)!!.revision
        val stage = File(target.root.parentFile, ".${target.root.name}-sync-${UUID.randomUUID()}").apply { mkdirs() }
        Files.move(File(target.root, book).toPath(), File(stage, "previous").toPath())
        File(source.root, book).copyRecursively(File(target.root, book))
        File(target.root, ".restore-$book.json").writeText(JSONObject().put("staging", stage.name).toString())
        assertEquals(expected, OcrSyncArchives.capture(OcrStore(target.root), book)!!.revision)
        assertFalse(stage.exists())
    }
}
