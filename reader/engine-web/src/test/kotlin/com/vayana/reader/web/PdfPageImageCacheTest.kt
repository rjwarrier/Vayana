package com.vayana.reader.web

import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PdfPageImageCacheTest {
    private val dir = Files.createTempDirectory("pdf-pages").toFile()
    private val book = "a".repeat(64)

    @Test
    fun `a saved page reads back`() {
        val cache = PdfPageImageCache(dir)
        cache.save(book, "12-1008x1632-0-own.webp", byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), cache.read(book, "12-1008x1632-0-own.webp")?.readBytes())
        assertNull(cache.read(book, "13-1008x1632-0-own.webp"))
    }

    @Test
    fun `a page saved at a slightly different size serves, in the same colours only`() {
        val cache = PdfPageImageCache(dir)
        cache.save(book, "5-1137x1840-0-000000-EDEDED.webp", byteArrayOf(1))
        cache.save(book, "5-1098x1777-0-000000-EDEDED.webp", byteArrayOf(2))
        cache.save(book, "5-1100x1780-0-own.webp", byteArrayOf(3))
        assertEquals(byteArrayOf(2).toList(), cache.read(book, "5-1100x1780-0-000000-EDEDED.webp")?.readBytes()?.toList())
        assertNull(cache.read(book, "5-1500x2428-0-000000-EDEDED.webp"))
        assertNull(cache.read(book, "6-1100x1780-0-000000-EDEDED.webp"))
    }

    @Test
    fun `names that could leave the store are refused`() {
        val cache = PdfPageImageCache(dir)
        cache.save("../$book", "1-10x10-0-own.webp", byteArrayOf(1))
        cache.save(book, "../1-10x10-0-own.webp", byteArrayOf(1))
        cache.save(book, "1-10x10-0-own.png", byteArrayOf(1))
        assertEquals(0, dir.walk().count { it.isFile })
        assertNull(cache.read(book, "../../etc/passwd"))
    }

    @Test
    fun `trimming drops the least recently used pages first`() {
        val cache = PdfPageImageCache(dir, maxBytes = 25)
        for (page in 1..3) cache.save(book, "$page-10x10-0-own.webp", ByteArray(10))
        val folder = File(dir, book)
        File(folder, "1-10x10-0-own.webp").setLastModified(1_000)
        File(folder, "2-10x10-0-own.webp").setLastModified(3_000)
        File(folder, "3-10x10-0-own.webp").setLastModified(2_000)
        cache.trim()
        assertEquals(setOf("2-10x10-0-own.webp", "3-10x10-0-own.webp"), folder.list()!!.toSet())
    }
}
