package com.vayana.core.sync.asset

import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class CloudAssetCipherTest {
    private val cipher = CloudAssetCipher()

    @Test
    fun roundTripsWithMatchingPassphraseAndMetadata() {
        val plaintext = "book bytes".toByteArray()
        val aad = "vayana.asset.v1:test".toByteArray()
        val encrypted = cipher.encrypt(plaintext, "secret".toCharArray(), aad)

        val decrypted = cipher.decrypt(encrypted, "secret".toCharArray(), aad)

        assertContentEquals(plaintext, decrypted)
        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("book bytes"))
    }

    @Test
    fun roundTripsLargeAssetWithoutChangingEnvelopeSize() {
        val plaintext = ByteArray(4 * 1024 * 1024) { index -> (index % 251).toByte() }
        val encrypted = cipher.encrypt(plaintext, "secret".toCharArray(), "large-book".toByteArray())

        assertEquals(plaintext.size + 8 + 1 + 16 + 12 + 16, encrypted.size)
        assertContentEquals(
            plaintext,
            cipher.decrypt(encrypted, "secret".toCharArray(), "large-book".toByteArray()),
        )
    }

    @Test
    fun rejectsWrongPassphrase() {
        val encrypted = cipher.encrypt("book bytes".toByteArray(), "secret".toCharArray(), byteArrayOf())

        assertFailsWith<AEADBadTagException> {
            cipher.decrypt(encrypted, "wrong".toCharArray(), byteArrayOf())
        }
    }

    @Test
    fun rejectsWrongAuthenticatedMetadata() {
        val encrypted = cipher.encrypt("book bytes".toByteArray(), "secret".toCharArray(), "one".toByteArray())

        assertFailsWith<AEADBadTagException> {
            cipher.decrypt(encrypted, "secret".toCharArray(), "two".toByteArray())
        }
    }

    @Test
    fun rejectsEmptyPassphrase() {
        assertFailsWith<IllegalArgumentException> {
            cipher.encrypt("book bytes".toByteArray(), CharArray(0), byteArrayOf())
        }
    }
}
