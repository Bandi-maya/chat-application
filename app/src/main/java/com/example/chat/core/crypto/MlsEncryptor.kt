package com.example.chat.core.crypto

/**
 * Seam for the MLS E2EE layer (Flutter: MlsE2eeService backed by the
 * repo-owned third_party/openmls_android Rust fork over Flutter Rust Bridge).
 *
 * An exact Kotlin port requires Android bindings for that same OpenMLS fork
 * (JNI/UniFFI or a Rust android-library build). Until that lands, no
 * implementation is registered and message transport fails closed with
 * `IllegalStateException("Encrypted message transport is unavailable.")`,
 * exactly like the Flutter build does when MlsE2eeService is absent.
 */
interface MlsEncryptor {
    /** Null when device identity is unavailable (transport must then fail). */
    val currentDeviceId: String?

    /** Mirrors MlsE2eeService.isReady (group storage opened and usable). */
    val isReady: Boolean
        get() = false

    /** Mirrors MlsE2eeService.encryptPayload: returns base64 group id, the
     *  local epoch (must match the server epoch or throw), and base64
     *  ciphertext for the given JSON payload. */
    suspend fun encryptPayload(conversationId: String, payload: Map<String, Any?>): MlsEncryptedPayload

    /** Mirrors MlsE2eeService.decryptPayload for incoming row hydration. */
    suspend fun decryptPayload(conversationId: String, ciphertext: String): Map<String, Any?>

    /** Mirrors MlsE2eeService.encryptEditedPayload for the edit path:
     *  same payload shape as encryptPayload, plus the edited flag. */
    suspend fun encryptEditedPayload(conversationId: String, payload: Map<String, Any?>): MlsEncryptedPayload
}

data class MlsEncryptedPayload(
    val groupId: String,
    val epoch: Int,
    val ciphertext: String,
)
