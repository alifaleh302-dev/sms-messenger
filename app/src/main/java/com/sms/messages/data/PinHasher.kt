package com.sms.messages.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Hashes the app-lock PIN with a per-install salt.
 *
 * A PIN is only four to six digits, so it can always be brute-forced by someone who has
 * the file. The point here is narrower: the stored value must not be the PIN itself, so
 * reading the settings backup does not immediately hand over the code. The salt stops one
 * precomputed table from covering every install at once.
 *
 * Format: `base64(salt):base64(sha256(salt || pin))`.
 */
object PinHasher {

    private const val SALT_BYTES = 16
    private const val ITERATIONS = 20_000

    fun hash(pin: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val digest = stretch(pin, salt)
        return "${encode(salt)}:${encode(digest)}"
    }

    /** True when [pin] reproduces [stored]. Malformed stored values fail closed. */
    fun verify(pin: String, stored: String): Boolean {
        val parts = stored.split(':')
        if (parts.size != 2) return false
        val salt = runCatching { decode(parts[0]) }.getOrNull() ?: return false
        val expected = runCatching { decode(parts[1]) }.getOrNull() ?: return false
        val actual = stretch(pin, salt)
        return MessageDigest.isEqual(expected, actual)
    }

    /**
     * Repeats the digest so a guess costs something. Not a substitute for a real password
     * KDF, but the only secret here is a short PIN and this keeps the check cheap on device.
     */
    private fun stretch(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        var current = salt + pin.toByteArray(Charsets.UTF_8)
        repeat(ITERATIONS) {
            digest.reset()
            current = digest.digest(current)
        }
        return current
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP)
}
