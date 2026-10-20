package com.vayana.reader.web

import java.io.File
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ByteRangeTest {
    @Test
    fun `parses bounded and open ended ranges`() {
        assertEquals(ByteRange(10, 19), parseByteRange("bytes=10-19", 100))
        assertEquals(ByteRange(90, 99), parseByteRange("bytes=90-", 100))
        assertEquals(ByteRange(90, 99), parseByteRange("bytes=90-200", 100))
    }

    @Test
    fun `parses suffix ranges`() {
        assertEquals(ByteRange(80, 99), parseByteRange("bytes=-20", 100))
        assertEquals(ByteRange(0, 99), parseByteRange("bytes=-200", 100))
    }

    @Test
    fun `rejects malformed and unsatisfiable ranges`() {
        assertNull(parseByteRange("bytes=100-", 100))
        assertNull(parseByteRange("bytes=20-10", 100))
        assertNull(parseByteRange("bytes=0-1,4-5", 100))
        assertNull(parseByteRange("items=0-1", 100))
        assertNull(parseByteRange("bytes=-0", 100))
        assertNull(parseByteRange("bytes=0-1", 0))
    }

    @Test
    fun `a range stream yields the range once WebView skips to its start`() {
        val file = File.createTempFile("book", ".pdf").apply {
            deleteOnExit()
            writeBytes(ByteArray(100) { it.toByte() })
        }
        byteRangeStream(file, ByteRange(40, 49)).use { stream ->
            // As WebView does: skip range.start bytes, then read to the end of the stream.
            var skipped = 0L
            while (skipped < 40) skipped += stream.skip(40 - skipped).also { assertTrue(it > 0) }
            assertArrayEquals(ByteArray(10) { (40 + it).toByte() }, stream.readBytes())
        }
    }
}
