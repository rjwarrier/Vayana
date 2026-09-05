package com.vayana.core.sync.asset

import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CloudAssetCipher(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun encrypt(plaintext: ByteArray, passphrase: CharArray, aad: ByteArray): ByteArray {
        require(passphrase.isNotEmpty()) { "Cloud asset passphrase is required" }
        val salt = ByteArray(SaltBytes).also(secureRandom::nextBytes)
        val nonce = ByteArray(NonceBytes).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GcmTagBits, nonce))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArray(Magic.size + 1 + salt.size + nonce.size + ciphertext.size).also { envelope ->
            var offset = 0
            Magic.copyInto(envelope, offset)
            offset += Magic.size
            envelope[offset++] = EnvelopeVersion
            salt.copyInto(envelope, offset)
            offset += salt.size
            nonce.copyInto(envelope, offset)
            offset += nonce.size
            ciphertext.copyInto(envelope, offset)
        }
    }

    fun decrypt(envelope: ByteArray, passphrase: CharArray, aad: ByteArray): ByteArray {
        require(passphrase.isNotEmpty()) { "Cloud asset passphrase is required" }
        require(envelope.size > Magic.size + 1 + SaltBytes + NonceBytes + GcmTagBytes) { "Cloud asset envelope is too small" }
        require(envelope.copyOfRange(0, Magic.size).contentEquals(Magic)) { "Cloud asset envelope has an invalid header" }
        require(envelope[Magic.size] == EnvelopeVersion) { "Unsupported cloud asset envelope version" }
        var offset = Magic.size + 1
        val salt = envelope.copyOfRange(offset, offset + SaltBytes)
        offset += SaltBytes
        val nonce = envelope.copyOfRange(offset, offset + NonceBytes)
        offset += NonceBytes
        val ciphertext = envelope.copyOfRange(offset, envelope.size)
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GcmTagBits, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, KdfIterations, KeyBits)
        val encoded = SecretKeyFactory.getInstance(KdfAlgorithm).generateSecret(spec).encoded
        return try {
            SecretKeySpec(encoded, KeyAlgorithm)
        } finally {
            spec.clearPassword()
            Arrays.fill(encoded, 0)
        }
    }

    private companion object {
        val Magic = byteArrayOf(0x56, 0x41, 0x59, 0x41, 0x4e, 0x41, 0x41, 0x31)
        const val EnvelopeVersion: Byte = 1
        const val Transformation = "AES/GCM/NoPadding"
        const val KeyAlgorithm = "AES"
        const val KdfAlgorithm = "PBKDF2WithHmacSHA256"
        const val KeyBits = 256
        const val KdfIterations = 210_000
        const val SaltBytes = 16
        const val NonceBytes = 12
        const val GcmTagBits = 128
        const val GcmTagBytes = GcmTagBits / 8
    }
}
