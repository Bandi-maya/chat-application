package com.example.chat.data.services

import com.example.chat.core.preferences.LockCredentialStore
import com.example.chat.data.e2ee.SecureKeyValueStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Port of the credential-storage subset of lib/data/services/local_lock_service.dart
 * used by preference migration and app/chat lock:
 * PBKDF2-HMAC-SHA256 (120,000 rounds), 16-byte random salt per credential,
 * 256-bit derived key, credentials stored only in platform secure storage,
 * constant-time comparison. Biometrics, failure-cooldown lockout, and the
 * secret-phrase path land with the app-lock slice.
 */
class LocalLockService(
    private val secureStorage: SecureKeyValueStore,
) : LockCredentialStore {

    companion object {
        private const val PREFIX = "chaty.local_lock.v2"
        private const val PIN_LENGTH_KEY = "$PREFIX.pin_length"
        private const val ITERATIONS = 120_000
        private const val BITS = 256
        private const val SALT_LENGTH = 16

        private fun normalizeMethod(method: String): String = when (method.lowercase()) {
            "pin" -> "pin"
            "pattern" -> "pattern"
            "password" -> "password"
            else -> throw IllegalArgumentException("Unsupported local credential method: $method")
        }

        private fun hashKey(method: String) = "$PREFIX.${normalizeMethod(method)}.hash"
        private fun saltKey(method: String) = "$PREFIX.${normalizeMethod(method)}.salt"

        internal fun isValidPattern(pattern: String): Boolean {
            val values = pattern.split('-').filter { it.isNotEmpty() }
            if (values.size < 4) return false
            val parsed = values.map { it.toIntOrNull() }
            if (parsed.any { it == null || it < 0 || it > 8 }) return false
            return parsed.toSet().size == parsed.size
        }

        private fun pbkdf2(secret: String, salt: ByteArray): ByteArray {
            val spec = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, BITS)
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        }

        private val b64 = java.util.Base64.getEncoder()
        private val b64dec = java.util.Base64.getDecoder()
    }

    override suspend fun hasCredential(method: String): Boolean = try {
        !secureStorage.read(hashKey(method)).isNullOrEmpty() &&
            !secureStorage.read(saltKey(method)).isNullOrEmpty()
    } catch (_: Exception) {
        false
    }

    suspend fun setPinLength(length: Int) {
        val safeLength = if (length == 6) 6 else 4
        secureStorage.write(PIN_LENGTH_KEY, safeLength.toString())
    }

    suspend fun getPinLength(): Int =
        secureStorage.read(PIN_LENGTH_KEY)?.toIntOrNull()?.let { if (it == 6) 6 else 4 } ?: 4

    /** Fail-safe overwrite rule lives in the caller (migration checks
     *  hasCredential first); this mirrors Dart setCredential exactly,
     *  including validation failures throwing so invalid legacy secrets are
     *  dropped by the migrator's catch. */
    override suspend fun setCredential(method: String, secret: String, pinLength: Int?) {
        val normalized = normalizeMethod(method)
        require(secret.isNotEmpty()) { "Credential must not be empty." }
        if (normalized == "pin") {
            val expectedLength = if (pinLength == 6) 6 else 4
            require(Regex("^\\d+$").matches(secret) && secret.length == expectedLength) {
                "PIN must contain exactly $expectedLength digits."
            }
            setPinLength(expectedLength)
        }
        if (normalized == "pattern" && !isValidPattern(secret)) {
            throw IllegalArgumentException("Pattern must connect at least 4 unique points.")
        }
        if (normalized == "password" && secret.length < 6) {
            throw IllegalArgumentException("Password must contain at least 6 characters.")
        }

        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val derived = pbkdf2(secret, salt)
        secureStorage.write(saltKey(normalized), b64.encodeToString(salt))
        secureStorage.write(hashKey(normalized), b64.encodeToString(derived))
    }

    /** Constant-time verification (rate-limit cooldown arrives with the
     *  app-lock slice). */
    suspend fun verifyCredential(method: String, secret: String): Boolean = try {
        val encodedHash = secureStorage.read(hashKey(method)) ?: return false
        val encodedSalt = secureStorage.read(saltKey(method)) ?: return false
        val salt = b64dec.decode(encodedSalt)
        val expected = b64dec.decode(encodedHash)
        MessageDigest.isEqual(expected, pbkdf2(secret, salt))
    } catch (_: Exception) {
        false
    }
}
