package com.example.chat.ui.settings.universal

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/**
 * Device-bound AES-256/GCM envelope for local Chaty backups. The key is
 * generated once in the Android Keystore and never leaves the device: a
 * backup file can only be read by this app on this device, and GCM
 * authentication rejects any file that was forged or modified. Nothing is
 * uploaded anywhere; the envelope is a plain JSON object on disk.
 */
@OptIn(ExperimentalEncodingApi::class)
object BackupCrypto {
    private const val KEY_ALIAS = "chaty_local_backup_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** Encrypts [plain] and returns the JSON envelope written to disk. */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("v", 1)
            .put("alg", "AES-256-GCM/AndroidKeyStore")
            .put("iv", Base64.Default.encode(cipher.iv))
            .put("ct", Base64.Default.encode(ciphertext))
            .toString()
    }

    /**
     * Decrypts an envelope produced by [encrypt]. Throws when the file is not
     * an encrypted Chaty backup or fails GCM authentication (wrong device,
     * forged or corrupted contents).
     */
    fun decrypt(envelope: String): String {
        val obj = JSONObject(envelope)
        val iv = obj.optString("iv").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Not an encrypted Chaty backup file.")
        val ct = obj.optString("ct").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Not an encrypted Chaty backup file.")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, Base64.Default.decode(iv)),
        )
        return String(cipher.doFinal(Base64.Default.decode(ct)), Charsets.UTF_8)
    }

    // ---- Portable passphrase envelope (v2) ----
    //
    // User-held recovery: the key is derived from a passphrase the user types
    // in-app at backup time and re-enters at restore time. Nothing about the
    // passphrase or the derived key is stored by the app; losing the
    // passphrase makes the archive unrecoverable (stated in the UI).
    // Parameters follow current Android/OWASP guidance for PBKDF2-HMAC-SHA256
    // (600k iterations, 16-byte random salt, 256-bit key, 128-bit GCM tag).

    private const val PBKDF2_ITERATIONS = 600_000
    private const val SALT_BYTES = 16

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): javax.crypto.SecretKey {
        val spec = javax.crypto.spec.PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, 256)
        try {
            val bytes = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            return javax.crypto.spec.SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** Encrypts [plain] under a passphrase-derived key (portable across devices). */
    fun encryptWithPassphrase(plain: String, passphrase: CharArray): String =
        encryptBytesWithPassphrase(plain.toByteArray(Charsets.UTF_8), passphrase)

    /** Byte-oriented variant for binary payloads (ZIP archives). */
    fun encryptBytesWithPassphrase(plain: ByteArray, passphrase: CharArray): String {
        val salt = java.security.SecureRandom().let { r -> ByteArray(SALT_BYTES).also(r::nextBytes) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plain)
        return JSONObject()
            .put("v", 2)
            .put("alg", "AES-256-GCM/PBKDF2-HMAC-SHA256-$PBKDF2_ITERATIONS")
            .put("salt", Base64.Default.encode(salt))
            .put("iv", Base64.Default.encode(cipher.iv))
            .put("ct", Base64.Default.encode(ciphertext))
            .toString()
    }

    /**
     * Decrypts a passphrase envelope produced by [encryptWithPassphrase].
     * Throws when the envelope is not a v2 passphrase backup or fails GCM
     * authentication (wrong passphrase, forged or corrupted contents).
     */
    fun decryptWithPassphrase(envelope: String, passphrase: CharArray): String =
        String(decryptBytesWithPassphrase(envelope, passphrase), Charsets.UTF_8)

    /** Byte-oriented variant for binary payloads (ZIP archives). */
    fun decryptBytesWithPassphrase(envelope: String, passphrase: CharArray): ByteArray {
        val obj = JSONObject(envelope)
        val salt = obj.optString("salt").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Not a passphrase-protected Chaty backup file.")
        val iv = obj.optString("iv").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Not a passphrase-protected Chaty backup file.")
        val ct = obj.optString("ct").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Not a passphrase-protected Chaty backup file.")
        val key = deriveKey(passphrase, Base64.Default.decode(salt))
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_BITS, Base64.Default.decode(iv)),
        )
        return cipher.doFinal(Base64.Default.decode(ct))
    }
}
