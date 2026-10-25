package com.example.chat.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.chat.data.e2ee.SecureKeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** EncryptedSharedPreferences-backed secure store (Android counterpart of
 *  flutter_secure_storage). Keys/values are AES-256-GCM/S2V encrypted with a
 *  Keystore-held master key, matching the Flutter app's security boundary:
 *  MLS db key, device id and signer material live only here. */
class SecureKeyValueStoreImpl(context: Context) : SecureKeyValueStore {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "chaty_secure_store",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(key, null)
    }

    override suspend fun write(key: String, value: String): Unit = withContext(Dispatchers.IO) {
        prefs.edit().putString(key, value).apply()
    }

    override suspend fun delete(key: String): Unit = withContext(Dispatchers.IO) {
        prefs.edit().remove(key).apply()
    }

    /** FlutterSecureStorage().deleteAll(): erase every secret in the store. */
    suspend fun deleteAll(): Unit = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }
}
