package com.example.chat.data.local

import android.content.Context
import androidx.room.Room
import com.example.chat.data.e2ee.SecureKeyValueStore
import java.security.SecureRandom
import net.zetetic.database.sqlcipher.SupportFactory

/** Owns the Room+SQLCipher database handle (matrices 11/18). The 32-byte
 *  passphrase is generated on first use and stored in the Keystore-backed
 *  SecureKeyValueStore - never in prefs, never logged. */
class SecureMessageStore(
    private val context: Context,
    private val secureStore: SecureKeyValueStore,
) {
    @Volatile
    private var db: SecureMessageDatabase? = null

    suspend fun database(): SecureMessageDatabase {
        db?.let { return it }
        val passphrase = secureStore.read(KEY_DB_PASSPHRASE) ?: run {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            val hex = bytes.joinToString("") { "%02x".format(it) }
            secureStore.write(KEY_DB_PASSPHRASE, hex)
            hex
        }
        return synchronized(this) {
            db ?: Room.databaseBuilder(
                context.applicationContext,
                SecureMessageDatabase::class.java,
                "chaty_messages.db",
            )
                .openHelperFactory(SupportFactory(passphrase.toByteArray(Charsets.UTF_8)))
                .build()
                .also { db = it }
        }
    }

    companion object {
        private const val KEY_DB_PASSPHRASE = "secure_message_db_passphrase_v1"
    }
}
