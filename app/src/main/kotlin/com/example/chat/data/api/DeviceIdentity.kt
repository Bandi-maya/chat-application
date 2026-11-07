package com.example.chat.data.api

import android.util.Base64
import com.example.chat.data.e2ee.SecureKeyValueStore
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * Persistent device identity for device-bound sessions (refresh tokens are
 * pinned to this device's Ed25519 key server-side):
 *
 *  - Login / register send `x-device-id` + `x-device-public-key` so the server
 *    binds the new session to this device (or flags it as a new device needing
 *    confirmation).
 *  - Every refresh sends `x-device-id` + `x-device-proof`, the proof being an
 *    Ed25519 signature over the raw refresh token. A stolen refresh token is
 *    therefore unusable without this device's private key.
 *
 * The key material lives in [SecureKeyValueStore] (EncryptedSharedPreferences /
 * Keystore-backed) and never leaves the device. Legacy sessions simply omit the
 * headers and keep working.
 */
class DeviceIdentityManager(
    private val secureStore: SecureKeyValueStore,
) {
    private val mutex = Mutex()
    @Volatile private var cached: DeviceIdentity? = null

    data class DeviceIdentity(
        val deviceId: String,
        val publicKeyBase64: String,
        private val privateKeyBytes: ByteArray,
    ) {
        /** Raw Ed25519 signature over [message]. */
        fun sign(message: ByteArray): ByteArray {
            val privateKey = Ed25519PrivateKeyParameters(privateKeyBytes, 0)
            val signer = Ed25519Signer()
            signer.init(true, privateKey)
            signer.update(message, 0, message.size)
            return signer.generateSignature()
        }
    }

    /** Loads the stored identity or creates and persists a fresh one. */
    suspend fun getOrCreate(): DeviceIdentity = cached ?: mutex.withLock {
        cached?.let { return it }
        val storedId = secureStore.read(KEY_DEVICE_ID)
        val storedPrivate = secureStore.read(KEY_PRIVATE)
        val storedPublic = secureStore.read(KEY_PUBLIC)
        val identity = if (storedId != null && storedPrivate != null && storedPublic != null) {
            DeviceIdentity(
                deviceId = storedId,
                publicKeyBase64 = storedPublic,
                privateKeyBytes = Base64.decode(storedPrivate, Base64.DEFAULT),
            )
        } else {
            if (storedId != null || storedPrivate != null || storedPublic != null) {
                // Incomplete identity material: rotate rather than risk a
                // mismatched key pair being presented to the server.
                secureStore.deleteAll(setOf(KEY_DEVICE_ID, KEY_PRIVATE, KEY_PUBLIC))
            }
            val privateKey = Ed25519PrivateKeyParameters(SecureRandom())
            val publicKey = privateKey.generatePublicKey().encoded
            val created = DeviceIdentity(
                deviceId = UUID.randomUUID().toString(),
                publicKeyBase64 = Base64.encodeToString(publicKey, Base64.NO_WRAP),
                privateKeyBytes = privateKey.encoded,
            )
            secureStore.writeAll(
                mapOf(
                    KEY_DEVICE_ID to created.deviceId,
                    KEY_PRIVATE to Base64.encodeToString(created.privateKeyBytes, Base64.NO_WRAP),
                    KEY_PUBLIC to created.publicKeyBase64,
                ),
            )
            created
        }
        cached = identity
        identity
    }

    /** Headers for login/register: bind the new session to this device. */
    suspend fun identityHeaders(): Map<String, String> {
        val identity = getOrCreate()
        return mapOf(
            HEADER_DEVICE_ID to identity.deviceId,
            HEADER_DEVICE_PUBLIC_KEY to identity.publicKeyBase64,
        )
    }

    /** Headers for refresh: prove possession of the device key by signing the
     *  exact refresh token being presented. */
    suspend fun proofHeaders(refreshToken: String): Map<String, String> {
        val identity = getOrCreate()
        val signature = identity.sign(refreshToken.toByteArray(Charsets.UTF_8))
        return mapOf(
            HEADER_DEVICE_ID to identity.deviceId,
            HEADER_DEVICE_PROOF to Base64.encodeToString(signature, Base64.NO_WRAP),
        )
    }

    companion object {
        const val HEADER_DEVICE_ID = "x-device-id"
        const val HEADER_DEVICE_PUBLIC_KEY = "x-device-public-key"
        const val HEADER_DEVICE_PROOF = "x-device-proof"
        private const val KEY_DEVICE_ID = "chaty.device.id.v1"
        private const val KEY_PRIVATE = "chaty.device.ed25519.private.v1"
        private const val KEY_PUBLIC = "chaty.device.ed25519.public.v1"
    }
}
