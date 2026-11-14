package com.example.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class HashedPassword(
    val salt: String,
    val hash: String
)

object PasswordCrypto {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun hashPassword(password: String): HashedPassword {
        require(password.isNotEmpty()) { "Password must not be empty" }
        val saltBytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(saltBytes)
        val saltHex = saltBytes.toHexString()
        val hashHex = deriveKeyHex(password, saltBytes)
        return HashedPassword(salt = saltHex, hash = hashHex)
    }

    fun verifyPassword(password: String, saltHex: String, expectedHashHex: String): Boolean {
        if (password.isEmpty() || saltHex.isBlank() || expectedHashHex.isBlank()) return false
        return try {
            val saltBytes = saltHex.hexToByteArray()
            val actualHash = deriveKeyHex(password, saltBytes)
            MessageDigest.isEqual(
                actualHash.toByteArray(Charsets.UTF_8),
                expectedHashHex.toByteArray(Charsets.UTF_8)
            )
        } catch (_: Exception) {
            false
        }
    }

    private fun deriveKeyHex(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            val bytes = factory.generateSecret(spec).encoded
            bytes.toHexString()
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Invalid hex length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
