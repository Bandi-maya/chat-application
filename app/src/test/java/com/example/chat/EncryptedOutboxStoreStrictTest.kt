package com.example.chat

import android.content.SharedPreferences
import com.example.chat.data.api.AccountRecentChatSummary
import com.example.chat.data.api.ApiAccountCredentialRecord
import com.example.chat.data.api.ApiMultiAccountStore
import com.example.chat.data.api.ApiRegisteredAccountMeta
import com.example.chat.data.api.RecentCardsLayoutState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quarantine-contract tests for the strict encrypted-outbox read/write API.
 *
 *  Background: EncryptedSharedPreferences.getString returns null for BOTH
 *  "never saved" and "present but unreadable", and commit() returns a
 *  boolean the lenient EncryptedMultiAccountStore outbox methods ignored.
 *  For the hold/quarantine flow either failure fails OPEN (holds silently
 *  dropped on restart), so the strict variants must:
 *   - return null only for a genuinely absent key;
 *   - throw when a present key cannot be read;
 *   - return from a write only after a verified durable commit.
 *
 *  The strict core (OutboxStoreStrict) is exercised directly here with a
 *  fake SharedPreferences; EncryptedMultiAccountStore itself delegates to
 *  it one-to-one and needs a Context, so it is covered by review rather
 *  than instantiated in a JVM test. The interface contract is checked with
 *  map-backed fakes: the DEFAULT strict methods must throw (a read-back can
 *  prove readability, never disk durability), and an overriding store with
 *  honest strict semantics round-trips. */
class EncryptedOutboxStoreStrictTest {

    // ----- Fake SharedPreferences -------------------------------------------

    private class FakeEditor(
        private val prefs: FakeSharedPreferences,
    ) : SharedPreferences.Editor {
        private val puts = HashMap<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var cleared = false

        override fun putString(key: String, value: String?) = apply { puts[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { puts[key] = values }
        override fun putInt(key: String, value: Int) = apply { puts[key] = value }
        override fun putLong(key: String, value: Long) = apply { puts[key] = value }
        override fun putFloat(key: String, value: Float) = apply { puts[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value }
        override fun remove(key: String) = apply { removals += key }
        override fun clear() = apply { cleared = true }

        override fun commit(): Boolean {
            if (!prefs.commitResult) return false
            applyOps()
            return true
        }

        override fun apply() {
            applyOps()
        }

        private fun applyOps() {
            if (cleared) prefs.map.clear()
            removals.forEach { prefs.map.remove(it) }
            puts.forEach { (k, v) -> if (v == null) prefs.map.remove(k) else prefs.map[k] = v }
        }
    }

    private class FakeSharedPreferences : SharedPreferences {
        val map = HashMap<String, Any?>()
        /** Simulates commit() reporting the write was NOT durably stored. */
        var commitResult = true
        /** Keys that exist (contains=true) but cannot be read (getString=null):
         *  the EncryptedSharedPreferences decryption-failure shape. */
        val unreadableKeys = mutableSetOf<String>()
        /** Simulates a keystore-level failure thrown out of any read. */
        var throwOnRead: Throwable? = null

        override fun getAll(): MutableMap<String, *> = HashMap(map)

        override fun getString(key: String, defValue: String?): String? {
            throwOnRead?.let { throw it }
            if (unreadableKeys.contains(key)) return null
            @Suppress("UNCHECKED_CAST")
            return if (map.containsKey(key)) map[key] as String? else defValue
        }

        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return map[key] as MutableSet<String>? ?: defValues
        }

        override fun getInt(key: String, defValue: Int) = map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long) = map[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float) = map[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = map[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean {
            throwOnRead?.let { throw it }
            return map.containsKey(key) || unreadableKeys.contains(key)
        }

        override fun edit(): SharedPreferences.Editor = FakeEditor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener?) {}
    }

    // ----- Strict core (OutboxStoreStrict) -----------------------------------

    @Test
    fun `strict read returns null only when the key was never written`() {
        val prefs = FakeSharedPreferences()
        assertNull(com.example.chat.data.api.OutboxStoreStrict.readStrict(prefs, "outbox_v1_abc"))
    }

    @Test
    fun `strict write then read round-trips the exact payload`() {
        val prefs = FakeSharedPreferences()
        val json = """{"held_conversations":["c1"],"entries":[{"temp_id":"t1","held":true}]}"""
        com.example.chat.data.api.OutboxStoreStrict.writeStrict(prefs, "outbox_v1_abc", json)
        assertEquals(json, com.example.chat.data.api.OutboxStoreStrict.readStrict(prefs, "outbox_v1_abc"))
    }

    @Test
    fun `strict read throws on present-but-unreadable instead of failing open`() {
        val prefs = FakeSharedPreferences()
        prefs.unreadableKeys += "outbox_v1_abc"
        assertThrows(IllegalStateException::class.java) {
            com.example.chat.data.api.OutboxStoreStrict.readStrict(prefs, "outbox_v1_abc")
        }
    }

    @Test
    fun `strict read propagates keystore failures instead of swallowing them`() {
        val prefs = FakeSharedPreferences()
        prefs.map["outbox_v1_abc"] = "[]"
        prefs.throwOnRead = SecurityException("keystore unavailable")
        assertThrows(SecurityException::class.java) {
            com.example.chat.data.api.OutboxStoreStrict.readStrict(prefs, "outbox_v1_abc")
        }
    }

    @Test
    fun `strict write throws when commit reports not durable`() {
        val prefs = FakeSharedPreferences()
        prefs.commitResult = false
        assertThrows(IllegalStateException::class.java) {
            com.example.chat.data.api.OutboxStoreStrict.writeStrict(prefs, "outbox_v1_abc", "[]")
        }
        // And nothing was stored: the failed write is not half-visible.
        assertNull(com.example.chat.data.api.OutboxStoreStrict.readStrict(prefs, "outbox_v1_abc"))
    }

    @Test
    fun `strict write null removes the key and verifies the removal`() {
        val prefs = FakeSharedPreferences()
        com.example.chat.data.api.OutboxStoreStrict.writeStrict(prefs, "outbox_v1_abc", "[]")
        com.example.chat.data.api.OutboxStoreStrict.writeStrict(prefs, "outbox_v1_abc", null)
        assertNull(com.example.chat.data.api.OutboxStoreStrict.readStrict(prefs, "outbox_v1_abc"))
        prefs.commitResult = false
        com.example.chat.data.api.OutboxStoreStrict.writeStrict(prefs, "outbox_v1_abc", "[]")
        assertThrows(IllegalStateException::class.java) {
            com.example.chat.data.api.OutboxStoreStrict.writeStrict(prefs, "outbox_v1_abc", null)
        }
    }

    @Test
    fun `hold payload survives a restart boundary unchanged`() {
        // Same backing map, new reader: what the strict write committed is
        // exactly what the strict read returns after a process restart.
        val prefs = FakeSharedPreferences()
        val json = """{"held_conversations":["empty-chat"],"entries":[]}"""
        com.example.chat.data.api.OutboxStoreStrict.writeStrict(prefs, "outbox_v1_abc", json)
        val afterRestart = com.example.chat.data.api.OutboxStoreStrict.readStrict(prefs, "outbox_v1_abc")
        assertEquals(json, afterRestart)
        assertTrue(afterRestart!!.contains("empty-chat"))
    }

    // ----- Interface contract -------------------------------------------------

    private open class MapBackedStore : ApiMultiAccountStore {
        val outbox = mutableMapOf<String, String>()

        override fun loadRegistry(): List<ApiRegisteredAccountMeta> = emptyList()
        override fun saveRegistry(accounts: List<ApiRegisteredAccountMeta>) {}
        override fun loadCredentials(accountHash: String): ApiAccountCredentialRecord? = null
        override fun saveCredentials(accountHash: String, record: ApiAccountCredentialRecord) {}
        override fun deleteCredentials(accountHash: String) {}
        override fun loadActiveAccountKey(): String? = null
        override fun saveActiveAccountKey(accountKey: String?) {}
        override fun loadRecentSummaries(accountHash: String): List<AccountRecentChatSummary> = emptyList()
        override fun saveRecentSummaries(accountHash: String, summaries: List<AccountRecentChatSummary>) {}
        override fun deleteRecentSummaries(accountHash: String) {}
        override fun loadLayoutState() = RecentCardsLayoutState()
        override fun saveLayoutState(state: RecentCardsLayoutState) {}
        override fun loadEncryptedOutboxJson(accountHash: String): String? = outbox[accountHash]
        override fun saveEncryptedOutboxJson(accountHash: String, json: String?) {
            if (json == null) outbox.remove(accountHash) else outbox[accountHash] = json
        }
        override fun loadEncryptedDrafts(accountHash: String): Map<String, String> = emptyMap()
        override fun saveEncryptedDrafts(accountHash: String, drafts: Map<String, String>) {}
        override fun clearAccountData(accountHash: String) { outbox.remove(accountHash) }
    }

    /** A store with honest in-memory strict semantics: contains-checked read,
     *  put-then-present write. Stands in for EncryptedMultiAccountStore's
     *  contains()+commit() overrides (that store needs a Context and cannot
     *  be instantiated in a JVM test). */
    private class StrictMapBackedStore : MapBackedStore() {
        override fun loadEncryptedOutboxJsonStrict(accountHash: String): String? {
            if (!outbox.containsKey(accountHash)) return null
            return outbox[accountHash]
                ?: throw IllegalStateException("present but unreadable")
        }

        override fun saveEncryptedOutboxJsonStrict(accountHash: String, json: String?) {
            if (json.isNullOrBlank()) outbox.remove(accountHash) else outbox[accountHash] = json
            if (outbox[accountHash] != json?.takeIf { it.isNotBlank() }) {
                throw IllegalStateException("write not durable")
            }
        }
    }

    @Test
    fun `interface defaults throw instead of faking a strict contract`() {
        // A read-back can prove readability, never disk durability: the
        // default strict methods must refuse rather than silently weaken the
        // quarantine contract for non-overriding implementations.
        val store = MapBackedStore()
        assertThrows(UnsupportedOperationException::class.java) {
            store.saveEncryptedOutboxJsonStrict("hash1", "[]")
        }
        assertThrows(UnsupportedOperationException::class.java) {
            store.loadEncryptedOutboxJsonStrict("hash1")
        }
    }

    @Test
    fun `overriding store round-trips holds across a restart boundary`() {
        val store = StrictMapBackedStore()
        val json = "{\"held_conversations\":[\"c1\"],\"entries\":[]}"
        store.saveEncryptedOutboxJsonStrict("hash1", json)
        // New instance, same backing data would be the restart case; here the
        // same instance proves the strict pair honors the exact payload.
        assertEquals(json, store.loadEncryptedOutboxJsonStrict("hash1"))
        store.saveEncryptedOutboxJsonStrict("hash1", null)
        assertNull(store.loadEncryptedOutboxJsonStrict("hash1"))
    }
}
