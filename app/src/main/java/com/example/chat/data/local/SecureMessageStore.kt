package com.example.chat.data.local

import android.content.Context
import androidx.room.Room
import com.example.chat.data.e2ee.SecureKeyValueStore
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Owns per-account Room+SQLCipher database handles (matrices 11/18). Each account namespace
 * gets its own database file and its own 32-byte passphrase in Keystore-backed
 * SecureKeyValueStore - never shared across accounts, never in prefs, never logged.
 */
class SecureMessageStore(
    private val context: Context,
    private val secureStore: SecureKeyValueStore,
    private val activeNamespaceProvider: () -> String? = { null },
) {
    private val dbs = ConcurrentHashMap<String, SecureMessageDatabase>()
    private val createLock = Any()

    private fun normalizeNamespace(explicit: String?): String {
        val raw = (explicit ?: activeNamespaceProvider()).orEmpty().trim()
        if (raw.isEmpty()) return DEFAULT_NAMESPACE
        return raw.replace(Regex("[^A-Za-z0-9_-]"), "_")
    }

    private fun passphraseKeyFor(namespace: String): String =
        if (namespace == DEFAULT_NAMESPACE) KEY_DB_PASSPHRASE
        else "${KEY_DB_PASSPHRASE}_$namespace"

    private fun dbFileNameFor(namespace: String): String =
        if (namespace == DEFAULT_NAMESPACE) "chaty_messages.db"
        else "chaty_messages_$namespace.db"

    suspend fun database(namespaceKey: String? = null): SecureMessageDatabase {
        val ns = normalizeNamespace(namespaceKey)
        dbs[ns]?.takeIf { it.isOpen }?.let { return it }

        val keyName = passphraseKeyFor(ns)
        val passphrase = secureStore.read(keyName) ?: run {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            val hex = bytes.joinToString("") { "%02x".format(it) }
            secureStore.write(keyName, hex)
            hex
        }
        return synchronized(createLock) {
            dbs[ns]?.takeIf { it.isOpen } ?: Room.databaseBuilder(
                context.applicationContext,
                SecureMessageDatabase::class.java,
                dbFileNameFor(ns),
            )
                .openHelperFactory(SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8)))
                .build()
                .also { dbs[ns] = it }
        }
    }

    /** Closes and detaches the DB handle for [namespaceKey] (call asynchronously outside scopeLock). */
    fun closeNamespace(namespaceKey: String) {
        val ns = normalizeNamespace(namespaceKey)
        val existing = synchronized(createLock) { dbs.remove(ns) } ?: return
        runCatching {
            if (existing.isOpen) existing.close()
        }
    }

    /** Closes and deletes the DB file and passphrase for a removed/signed-out account namespace. */
    suspend fun purgeNamespace(namespaceKey: String) {
        val ns = normalizeNamespace(namespaceKey)
        closeNamespace(ns)
        if (ns != DEFAULT_NAMESPACE) {
            runCatching { context.applicationContext.deleteDatabase(dbFileNameFor(ns)) }
            runCatching { secureStore.delete(passphraseKeyFor(ns)) }
        }
    }

    companion object {
        private const val DEFAULT_NAMESPACE = "__default__"
        private const val KEY_DB_PASSPHRASE = "secure_message_db_passphrase_v1"
    }
}
