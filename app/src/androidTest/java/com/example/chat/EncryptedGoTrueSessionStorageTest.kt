package com.example.chat

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.data.auth.EncryptedGoTrueSessionStorage
import com.example.chat.data.auth.sessionDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedGoTrueSessionStorageTest {
    @Test
    fun signOutTombstonePreventsStaleLegacySessionResurrection() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = EncryptedGoTrueSessionStorage(context)
        val access = stringPreferencesKey("access_token")
        val refresh = stringPreferencesKey("refresh_token")
        val expiry = longPreferencesKey("expires_at_epoch_ms")
        val user = stringPreferencesKey("user_id")

        storage.clear()
        try {
            // Simulate a crash between encrypted sign-out and legacy DataStore cleanup.
            context.sessionDataStore.edit { prefs ->
                prefs[access] = "stale-access-token"
                prefs[refresh] = "stale-refresh-token"
                prefs[expiry] = System.currentTimeMillis() + 60_000
                prefs[user] = "stale-user"
            }

            assertNull("a signed-out session must not be restored", storage.load())
            val legacy = context.sessionDataStore.data.first()
            assertNull(legacy[access])
            assertNull(legacy[refresh])
            assertNull(legacy[expiry])
            assertNull(legacy[user])
        } finally {
            storage.clear()
        }
    }
}
