package com.notesapp.domain.security

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Versioned portable envelope. The complete header is authenticated along with the ciphertext. */
object EncryptedArchive {
    private val magic = "NOTESENC".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 600_000
    private const val HEADER_SIZE = 8 + 4 + 4 + 16 + 12
    const val MAX_BYTES = 80 * 1024 * 1024
    private val random = SecureRandom()

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 10) { "Use a backup passphrase of at least 10 characters." }
        require(plaintext.size <= MAX_BYTES - HEADER_SIZE - 16) { "This archive is too large." }
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_SIZE).put(magic).putInt(1).putInt(ITERATIONS).put(salt).put(nonce).array()
        val key = derive(password, salt, ITERATIONS)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            header + cipher.doFinal(plaintext)
        } finally { key.fill(0) }
    }

    fun decrypt(archive: ByteArray, password: CharArray): ByteArray {
        require(archive.size in (HEADER_SIZE + 16)..MAX_BYTES) { "This is not a complete encrypted archive." }
        val input = ByteBuffer.wrap(archive)
        val found = ByteArray(8).also(input::get)
        require(found.contentEquals(magic) && input.int == 1) { "Unsupported encrypted archive format." }
        val rounds = input.int
        require(rounds in 100_000..2_000_000) { "Invalid archive key-derivation settings." }
        val salt = ByteArray(16).also(input::get)
        val nonce = ByteArray(12).also(input::get)
        val key = derive(password, salt, rounds)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(archive.copyOfRange(0, HEADER_SIZE))
            cipher.doFinal(archive, HEADER_SIZE, archive.size - HEADER_SIZE)
        } catch (_: java.security.GeneralSecurityException) { throw IllegalArgumentException("The passphrase is incorrect or the archive was modified.") }
        finally { key.fill(0) }
    }

    private fun derive(password: CharArray, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, rounds, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
}
