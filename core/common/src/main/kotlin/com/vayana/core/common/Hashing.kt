package com.vayana.core.common

import java.io.InputStream
import java.security.MessageDigest

/** SHA-256 of a stream's contents, hex-encoded. Used for import-time duplicate detection. */
object Hashing {
    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        input.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }
}
