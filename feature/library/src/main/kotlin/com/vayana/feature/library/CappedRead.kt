package com.vayana.feature.library

import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val ReadBufferBytes = 16 * 1024
private const val MaxInitialBufferBytes = 256 * 1024

/**
 * Reads the stream, or returns null as soon as it holds more than [maxBytes]. [sizeHint], a declared Content-Length
 * (or -1), presizes the buffer so a typical download is not copied while it grows.
 */
internal fun InputStream.readAtMost(maxBytes: Int, sizeHint: Long): ByteArray? {
    val initialCapacity = sizeHint.takeIf { it > 0L }?.coerceAtMost(MaxInitialBufferBytes.toLong())?.toInt() ?: ReadBufferBytes
    val out = ByteArrayOutputStream(initialCapacity)
    val buffer = ByteArray(ReadBufferBytes)
    while (true) {
        val read = read(buffer)
        if (read < 0) return out.toByteArray()
        out.write(buffer, 0, read)
        if (out.size() > maxBytes) return null
    }
}
