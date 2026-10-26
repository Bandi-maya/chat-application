package com.example.chat.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.chat.data.messaging.SnapshotCacheStore
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exact port of local_snapshot_cache_service.dart: the encrypted,
 * account-scoped snapshot cache for instant startup. AES-256-GCM with a
 * random 32-byte key kept in platform secure storage (EncryptedSharedPrefs,
 * the Android counterpart of flutter_secure_storage). Envelope byte format is
 * identical to the Dart cache ({v, n, c, m} base64 fields, 12-byte nonce,
 * 16-byte GCM tag, AAD "user:scope:v1"), so snapshots stay interchangeable.
 * Corruption is a cache miss, never app state; writes are tmp-file + rename
 * with the same fallback.
 */
class LocalSnapshotCacheService(context: Context) : SnapshotCacheStore {

    private val appContext = context.applicationContext
    private val secureStorage = EncryptedSharedPreferences.create(
        appContext,
        "chaty_snapshot_secure_store",
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private var cachedKey: ByteArray? = null
    private val keyMutex = Mutex()

    override suspend fun writeJson(userId: String, scope: String, value: Any?) {
        if (userId.isEmpty() || scope.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val key = loadOrCreateKey()
                val nonce = randomBytes(12)
                val payload = JSONObject()
                    .put("v", VERSION)
                    .put("saved_at", java.time.Instant.now().toString())
                    .put("value", toJsonValue(value))
                    .toString().toByteArray(Charsets.UTF_8)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(key, "AES"),
                    GCMParameterSpec(GCM_TAG_BITS, nonce),
                )
                cipher.updateAAD("$userId:$scope:v$VERSION".toByteArray(Charsets.UTF_8))
                val sealed = cipher.doFinal(payload)
                // javax.crypto appends the tag; the Dart envelope stores it
                // separately as "m".
                val cipherText = sealed.copyOfRange(0, sealed.size - GCM_TAG_BYTES)
                val mac = sealed.copyOfRange(sealed.size - GCM_TAG_BYTES, sealed.size)
                val envelope = JSONObject()
                    .put("v", VERSION)
                    .put("n", b64(nonce))
                    .put("c", b64(cipherText))
                    .put("m", b64(mac))
                val file = fileFor(userId, scope)
                file.parentFile?.mkdirs()
                val tmp = File("${file.path}.tmp")
                val content = envelope.toString()
                tmp.writeText(content)
                try {
                    if (file.exists()) file.delete()
                    tmp.renameTo(file)
                } catch (_: Exception) {
                    // Fallback for filesystems that fail on atomic rename.
                    file.writeText(content)
                    if (tmp.exists()) runCatching { tmp.delete() }
                }
            }
        }
    }

    override suspend fun readJson(userId: String, scope: String): Any? {
        if (userId.isEmpty() || scope.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = fileFor(userId, scope)
                if (!file.exists()) return@runCatching null
                val map = JSONObject(file.readText())
                if (map.optInt("v") != VERSION) return@runCatching null
                val nonce = unb64(map.optString("n"))
                val cipherText = unb64(map.optString("c"))
                val mac = unb64(map.optString("m"))
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(loadOrCreateKey(), "AES"),
                    GCMParameterSpec(GCM_TAG_BITS, nonce),
                )
                cipher.updateAAD("$userId:$scope:v$VERSION".toByteArray(Charsets.UTF_8))
                val clear = cipher.doFinal(cipherText + mac)
                val body = JSONObject(String(clear, Charsets.UTF_8))
                if (body.optInt("v") != VERSION) return@runCatching null
                fromJsonValue(body.get("value"))
            }.getOrNull()
        }
    }

    suspend fun deleteUser(userId: String) {
        if (userId.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val safeUser = safe(userId)
                directory().listFiles()?.forEach { entity ->
                    if (entity.isFile && entity.name.startsWith("chaty_snapshot_${safeUser}_")) {
                        runCatching { entity.delete() }
                    }
                }
            }
        }
    }

    suspend fun deleteAll() {
        withContext(Dispatchers.IO) {
            runCatching {
                directory().listFiles()?.forEach { entity ->
                    if (entity.isFile && entity.name.startsWith("chaty_snapshot_")) {
                        runCatching { entity.delete() }
                    }
                }
                keyMutex.withLock { cachedKey = null }
                secureStorage.edit().remove(KEY_NAME).apply()
            }
        }
    }

    private suspend fun loadOrCreateKey(): ByteArray = keyMutex.withLock {
        cachedKey?.let { return@withLock it }
        val existing = secureStorage.getString(KEY_NAME, null)
        if (!existing.isNullOrEmpty()) {
            runCatching {
                val bytes = android.util.Base64.decode(existing, android.util.Base64.NO_WRAP)
                if (bytes.size == 32) {
                    cachedKey = bytes
                    return@withLock bytes
                }
            }
        }
        val bytes = randomBytes(32)
        secureStorage.edit().putString(KEY_NAME, b64(bytes)).apply()
        cachedKey = bytes
        bytes
    }

    private fun fileFor(userId: String, scope: String): File =
        File(directory(), "chaty_snapshot_${safe(userId)}_${safe(scope)}.bin")

    private fun directory(): File = appContext.filesDir

    private fun safe(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_-]"), "_")

    companion object {
        private const val KEY_NAME = "chaty.snapshot_cache.key.v1"
        private const val VERSION = 1
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = 16

        private fun randomBytes(length: Int): ByteArray {
            val out = ByteArray(length)
            SecureRandom().nextBytes(out)
            return out
        }

        private fun b64(bytes: ByteArray): String =
            Base64.encodeToString(bytes, Base64.NO_WRAP)

        private fun unb64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

        fun toJsonValue(value: Any?): Any? = when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> {
                val obj = JSONObject()
                for ((k, v) in value) obj.put(k.toString(), toJsonValue(v))
                obj
            }
            is List<*> -> {
                val arr = JSONArray()
                for (v in value) arr.put(toJsonValue(v))
                arr
            }
            else -> value
        }

        fun fromJsonValue(value: Any?): Any? = when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> {
                val out = LinkedHashMap<String, Any?>()
                val keys = value.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    out[k] = fromJsonValue(value.get(k))
                }
                out
            }
            is JSONArray -> (0 until value.length()).map { fromJsonValue(value.get(it)) }
            else -> value
        }
    }
}
