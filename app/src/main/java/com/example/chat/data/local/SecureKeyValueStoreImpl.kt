package com.example.chat.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.chat.data.e2ee.SecureKeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keystore-backed secret store. Every mutation waits for a durable encrypted
 * preference commit and throws when Android reports that the write failed.
 */
class SecureKeyValueStoreImpl(context: Context) : SecureKeyValueStore {

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        com.example.chat.core.security.SecureStrings.secureStoreFileName,
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(key, null)
    }

    override suspend fun write(key: String, value: String): Unit = withContext(Dispatchers.IO) {
        check(prefs.edit().putString(key, value).commit()) { "Unable to persist encrypted secure value." }
    }

    override suspend fun writeAll(values: Map<String, String>): Unit = withContext(Dispatchers.IO) {
        val editor = prefs.edit()
        values.forEach { (key, value) -> editor.putString(key, value) }
        check(editor.commit()) { "Unable to persist encrypted secure values." }
    }

    override suspend fun delete(key: String): Unit = withContext(Dispatchers.IO) {
        check(prefs.edit().remove(key).commit()) { "Unable to delete encrypted secure value." }
    }

    override suspend fun deleteAll(keys: Set<String>): Unit = withContext(Dispatchers.IO) {
        val editor = prefs.edit()
        keys.forEach(editor::remove)
        check(editor.commit()) { "Unable to delete encrypted secure values." }
    }

    override suspend fun deletePrefix(prefix: String): Unit = withContext(Dispatchers.IO) {
        if (prefix.isEmpty()) return@withContext
        val matching = prefs.all.keys.filter { it.startsWith(prefix) }.toSet()
        if (matching.isEmpty()) return@withContext
        val editor = prefs.edit()
        matching.forEach(editor::remove)
        check(editor.commit()) { "Unable to delete scoped encrypted secure values." }
    }

    /** FlutterSecureStorage().deleteAll(): erase every secret in the store. */
    suspend fun deleteAll(): Unit = withContext(Dispatchers.IO) {
        check(prefs.edit().clear().commit()) { "Unable to clear encrypted secure storage." }
    }
}
