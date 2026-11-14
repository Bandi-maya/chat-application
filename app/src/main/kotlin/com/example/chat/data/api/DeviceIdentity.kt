package com.example.chat.data.api

import org.bouncycastle.util.encoders.Base64
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
 * Keystore-backed) and never leaves the device. The server decides whether legacy sessions may continue. A new login must obtain a fresh challenge; reinstall creates a new installation, not a physical-device guarantee.
 */
class DeviceIdentityManager(
    private val secureStore: SecureKeyValueStore,
) {
    private val mutex = Mutex()
    @Volatile private var cached: DeviceIdentity? = null

    class DeviceIdentity(
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
            val privateBytes = Base64.decode(storedPrivate)
            val publicBytes = Base64.decode(storedPublic)
            check(privateBytes.size == 32 && publicBytes.size == 32 &&
                java.security.MessageDigest.isEqual(Ed25519PrivateKeyParameters(privateBytes, 0).generatePublicKey().encoded, publicBytes) &&
                runCatching { UUID.fromString(storedId).toString() == storedId }.getOrDefault(false)) {
                "Saved installation identity is invalid. It cannot be silently replaced."
            }
            DeviceIdentity(storedId, storedPublic, privateBytes)
        } else {
            check(storedId == null && storedPrivate == null && storedPublic == null) {
                "Saved installation identity is incomplete. It cannot be silently replaced."
            }
            val privateKey = Ed25519PrivateKeyParameters(SecureRandom())
            val publicKey = privateKey.generatePublicKey().encoded
            val created = DeviceIdentity(
                deviceId = UUID.randomUUID().toString(),
                publicKeyBase64 = Base64.toBase64String(publicKey),
                privateKeyBytes = privateKey.encoded,
            )
            secureStore.writeAll(
                mapOf(
                    KEY_DEVICE_ID to created.deviceId,
                    KEY_PRIVATE to Base64.toBase64String(privateKey.encoded),
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

    /** Fresh one-use proof for every login/register attempt; shared identity across retained accounts. */
    suspend fun challengeHeaders(baseUrl: String, tenantId: String, transport: ApiHttpTransport,
        isCurrent: () -> Boolean): Map<String, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val identity = getOrCreate()
        val uri = java.net.URI(baseUrl)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "Secure device verification endpoint required." }
        check(isCurrent()) { "Account changed. Start sign-in again." }
        val body = org.json.JSONObject().put("installationId", identity.deviceId).put("publicKey", identity.publicKeyBase64).toString()
        val headers = mapOf("Content-Type" to "application/json")
        var response = transport.execute("POST", "${baseUrl.trimEnd('/')}/auth/device/challenge", headers, body)
        check(isCurrent()) { "Account changed. Start sign-in again." }
        if (response.first == 404) {
            response = transport.execute("POST", "${baseUrl.trimEnd('/')}/api/v1/auth/device/challenge", headers, body)
            check(isCurrent()) { "Account changed. Start sign-in again." }
        }
        check(response.first == 200) { "Installation verification unavailable (${response.first}). Try again later." }
        val data = org.json.JSONObject(response.second).optJSONObject("data") ?: error("Invalid installation challenge.")
        val challengeId = data.getString("challengeId")
        val message = data.getString("challengeMessage")
        val expires = java.time.Instant.parse(data.getString("expiresAt"))
        val now = java.time.Instant.now()
        check(challengeId.length in 1..128 && message.length in 1..4096 && data.optInt("accountLimit") == 2 &&
            expires.isAfter(now) && !expires.isAfter(now.plusSeconds(90))) { "Invalid or expired installation challenge." }
        check(isCurrent()) { "Account changed. Start sign-in again." }
        mapOf(HEADER_DEVICE_ID to identity.deviceId, HEADER_DEVICE_PUBLIC_KEY to identity.publicKeyBase64,
            HEADER_DEVICE_CHALLENGE_ID to challengeId, HEADER_DEVICE_PROOF to Base64.toBase64String(identity.sign(message.toByteArray(Charsets.UTF_8))))
    }

    /** Headers for refresh: prove possession of the device key by signing the
     *  exact refresh token being presented. */
    suspend fun proofHeaders(refreshToken: String): Map<String, String> {
        val identity = getOrCreate()
        val signature = identity.sign(refreshToken.toByteArray(Charsets.UTF_8))
        return mapOf(
            HEADER_DEVICE_ID to identity.deviceId,
            HEADER_DEVICE_PROOF to Base64.toBase64String(signature),
        )
    }

    companion object {
        const val HEADER_DEVICE_ID = "x-device-id"
        const val HEADER_DEVICE_PUBLIC_KEY = "x-device-public-key"
        const val HEADER_DEVICE_CHALLENGE_ID = "x-device-challenge-id"
        const val HEADER_DEVICE_PROOF = "x-device-proof"
        private const val KEY_DEVICE_ID = "chaty.device.id.v1"
        private const val KEY_PRIVATE = "chaty.device.ed25519.private.v1"
        private const val KEY_PUBLIC = "chaty.device.ed25519.public.v1"
    }
}
