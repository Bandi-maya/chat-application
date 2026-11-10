package com.example.chat.data.services

import com.example.chat.core.preferences.LockCredentialStore
import com.example.chat.data.e2ee.SecureKeyValueStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Biometric / device-credential seam (Dart: local_auth LocalAuthentication).
 *  The Android adapter (BiometricPrompt) is wired by the integration owner;
 *  every method must swallow platform errors and return false/empty, like the
 *  Dart try/catch around PlatformException. */
interface LockBiometrics {
    suspend fun canCheckBiometrics(): Boolean
    suspend fun availableBiometrics(): List<String>
    suspend fun isDeviceSupported(): Boolean
    /** True only when Android device PIN/pattern/password is configured and available. */
    suspend fun isDeviceCredentialSupported(): Boolean = isDeviceSupported()
    /** biometricOnly=true, stickyAuth, sensitiveTransaction. */
    suspend fun authenticateBiometricOnly(reason: String): Boolean
    /** biometricOnly=false (device PIN/pattern/password allowed). */
    suspend fun authenticateWithDeviceCredential(reason: String): Boolean
}

/**
 * Exact port of lib/data/services/local_lock_service.dart (full file).
 * PBKDF2-HMAC-SHA256 (120,000 rounds), 16-byte random salt per credential,
 * 256-bit derived key, credentials only in platform secure storage,
 * constant-time comparison, failed-attempt cooldown (5 -> 30s, 10 -> 60s,
 * 15 -> 5min), secret phrase, biometric/device-credential via [biometrics].
 * Replaces the earlier credential-only subset (same class and members).
 */
class LocalLockService(
    private val secureStorage: SecureKeyValueStore,
    private val biometrics: LockBiometrics? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
) : LockCredentialStore {

    private val attemptMutex = Mutex()
    @Volatile
    private var accountNamespace: String = ""

    fun setAccountNamespace(namespaceHash: String) {
        accountNamespace = namespaceHash.trim()
    }

    private fun scopedPrefix(): String =
        if (accountNamespace.isEmpty()) PREFIX else "$PREFIX.$accountNamespace"

    private fun pinLengthKey() = "${scopedPrefix()}.pin_length"
    private fun secretPhraseHashKey() = "${scopedPrefix()}.secret_phrase.hash"
    private fun secretPhraseSaltKey() = "${scopedPrefix()}.secret_phrase.salt"
    private fun failedAttemptsKey() = "${scopedPrefix()}.failed_attempts"
    private fun lockoutUntilKey() = "${scopedPrefix()}.lockout_until"
    private fun scopedHashKey(method: String) = "${scopedPrefix()}.${normalizeMethod(method)}.hash"
    private fun scopedSaltKey(method: String) = "${scopedPrefix()}.${normalizeMethod(method)}.salt"

    companion object {
        private const val PREFIX = "chaty.local_lock.v2"
        private const val PIN_LENGTH_KEY = "$PREFIX.pin_length"
        private const val SECRET_PHRASE_HASH_KEY = "$PREFIX.secret_phrase.hash"
        private const val SECRET_PHRASE_SALT_KEY = "$PREFIX.secret_phrase.salt"
        private const val FAILED_ATTEMPTS_KEY = "$PREFIX.failed_attempts"
        private const val LOCKOUT_UNTIL_KEY = "$PREFIX.lockout_until"
        private const val ITERATIONS = 120_000

        fun normalizeSecretPhrase(phrase: String): String =
            phrase.trim().replace(Regex("\\s+"), " ").lowercase()
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
            val chars = secret.toCharArray()
            val spec = PBEKeySpec(chars, salt, ITERATIONS, BITS)
            return try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
                chars.fill('\u0000')
            }
        }

        private val b64 = java.util.Base64.getEncoder()
        private val b64dec = java.util.Base64.getDecoder()
    }

    override suspend fun hasCredential(method: String): Boolean = try {
        !secureStorage.read(scopedHashKey(method)).isNullOrEmpty() &&
            !secureStorage.read(scopedSaltKey(method)).isNullOrEmpty()
    } catch (_: Exception) {
        false
    }

    suspend fun setPinLength(length: Int) {
        val safeLength = if (length == 6) 6 else 4
        secureStorage.write(pinLengthKey(), safeLength.toString())
    }

    suspend fun getPinLength(): Int =
        secureStorage.read(pinLengthKey())?.toIntOrNull()?.let { if (it == 6) 6 else 4 } ?: 4

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
        }
        if (normalized == "pattern" && !isValidPattern(secret)) {
            throw IllegalArgumentException("Pattern must connect at least 4 unique points.")
        }
        if (normalized == "password" && secret.length < 6) {
            throw IllegalArgumentException("Password must contain at least 6 characters.")
        }

        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val derived = pbkdf2(secret, salt)
        val values = linkedMapOf(
            scopedSaltKey(normalized) to b64.encodeToString(salt),
            scopedHashKey(normalized) to b64.encodeToString(derived),
        )
        if (normalized == "pin") values[pinLengthKey()] = (if (pinLength == 6) 6 else 4).toString()
        try {
            secureStorage.writeAll(values)
        } finally {
            salt.fill(0)
            derived.fill(0)
        }
    }

    /** Constant-time verification with failed-attempt cooldown. */
    suspend fun verifyCredential(method: String, secret: String): Boolean = attemptMutex.withLock {
        if (getRemainingCooldownSeconds() > 0) return@withLock false
        val normalized = normalizeMethod(method)
        try {
            val encodedHash = secureStorage.read(scopedHashKey(normalized))
            val encodedSalt = secureStorage.read(scopedSaltKey(normalized))
            if (encodedHash.isNullOrBlank() || encodedSalt.isNullOrBlank()) return@withLock false
            val salt = b64dec.decode(encodedSalt)
            val expected = b64dec.decode(encodedHash)
            val candidate = pbkdf2(secret, salt)
            val isMatch = try {
                MessageDigest.isEqual(expected, candidate)
            } finally {
                salt.fill(0)
                expected.fill(0)
                candidate.fill(0)
            }
            if (isMatch) resetFailedAttemptsLocked() else recordFailedAttemptLocked()
            isMatch
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    suspend fun hasSecretPhrase(): Boolean = try {
        !secureStorage.read(secretPhraseHashKey()).isNullOrEmpty() &&
            !secureStorage.read(secretPhraseSaltKey()).isNullOrEmpty()
    } catch (_: Exception) {
        false
    }

    suspend fun setSecretPhrase(rawPhrase: String) {
        val normalized = normalizeSecretPhrase(rawPhrase)
        require(normalized.isNotEmpty()) { "Secret phrase cannot be empty." }
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val bytes = pbkdf2(normalized, salt)
        try {
            secureStorage.writeAll(
                mapOf(
                    secretPhraseSaltKey() to b64.encodeToString(salt),
                    secretPhraseHashKey() to b64.encodeToString(bytes),
                ),
            )
        } finally {
            salt.fill(0)
            bytes.fill(0)
        }
    }

    suspend fun verifySecretPhrase(rawQuery: String): Boolean = attemptMutex.withLock {
        val normalized = normalizeSecretPhrase(rawQuery)
        if (normalized.isEmpty() || getRemainingCooldownSeconds() > 0) return@withLock false
        try {
            val encodedHash = secureStorage.read(secretPhraseHashKey())
            val encodedSalt = secureStorage.read(secretPhraseSaltKey())
            if (encodedHash.isNullOrBlank() || encodedSalt.isNullOrBlank()) return@withLock false
            val salt = b64dec.decode(encodedSalt)
            val expected = b64dec.decode(encodedHash)
            val candidate = pbkdf2(normalized, salt)
            val matches = try {
                MessageDigest.isEqual(expected, candidate)
            } finally {
                salt.fill(0)
                expected.fill(0)
                candidate.fill(0)
            }
            if (matches) resetFailedAttemptsLocked() else recordFailedAttemptLocked()
            matches
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    suspend fun clearSecretPhrase() {
        secureStorage.deleteAll(setOf(secretPhraseHashKey(), secretPhraseSaltKey()))
    }

    suspend fun getFailedAttempts(): Int = try {
        secureStorage.read(failedAttemptsKey())?.toIntOrNull() ?: 0
    } catch (_: Exception) {
        0
    }

    suspend fun getRemainingCooldownSeconds(): Int = try {
        val lockoutTime = secureStorage.read(lockoutUntilKey())?.toLongOrNull() ?: 0L
        val current = now()
        if (lockoutTime > current) Math.ceil((lockoutTime - current) / 1000.0).toInt() else 0
    } catch (_: Exception) {
        0
    }

    suspend fun recordFailedAttempt() = attemptMutex.withLock { recordFailedAttemptLocked() }

    private suspend fun recordFailedAttemptLocked() {
        val attempts = getFailedAttempts() + 1
        val values = linkedMapOf(failedAttemptsKey() to attempts.toString())
        val lockoutMs = when {
            attempts >= 15 -> 5 * 60_000L
            attempts >= 10 -> 60_000L
            attempts >= 5 -> 30_000L
            else -> null
        }
        if (lockoutMs != null) values[lockoutUntilKey()] = (now() + lockoutMs).toString()
        secureStorage.writeAll(values)
    }

    suspend fun resetFailedAttempts() = attemptMutex.withLock { resetFailedAttemptsLocked() }

    private suspend fun resetFailedAttemptsLocked() {
        secureStorage.deleteAll(setOf(failedAttemptsKey(), lockoutUntilKey()))
    }

    suspend fun canUseBiometrics(): Boolean = try {
        val b = biometrics
        b != null && b.canCheckBiometrics() && b.availableBiometrics().isNotEmpty()
    } catch (_: Exception) {
        false
    }

    suspend fun availableBiometrics(): List<String> = try {
        biometrics?.availableBiometrics() ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    suspend fun authenticateBiometric(reason: String = "Authenticate to unlock Chaty"): Boolean = try {
        if (!canUseBiometrics()) false else biometrics!!.authenticateBiometricOnly(reason)
    } catch (_: Exception) {
        false
    }

    suspend fun canUseDeviceCredential(): Boolean = try {
        biometrics?.isDeviceCredentialSupported() == true
    } catch (_: Exception) {
        false
    }

    suspend fun authenticateDeviceCredential(reason: String = "Use your device lock to unlock Chaty"): Boolean = try {
        val b = biometrics
        if (!canUseDeviceCredential() || b == null) false else b.authenticateWithDeviceCredential(reason)
    } catch (_: Exception) {
        false
    }

    suspend fun clearCredential(method: String) {
        val normalized = normalizeMethod(method)
        secureStorage.deleteAll(setOf(scopedHashKey(normalized), scopedSaltKey(normalized)))
    }

    suspend fun clearAll() {
        clearCredential("pin")
        clearCredential("pattern")
        clearCredential("password")
        clearSecretPhrase()
        resetFailedAttempts()
        secureStorage.delete(pinLengthKey())
    }
}
