package com.example.chat.data.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android Keystore-backed multi-account registry and per-account encrypted secret store.
 * Every credential, recent summary, outbox queue, and draft map for an account lives inside
 * EncryptedSharedPreferences (AES256-SIV keys + AES256-GCM values) under that account's
 * SHA-256 hash namespace. Never stores tokens in plaintext SharedPreferences or logs them.
 */
class EncryptedMultiAccountStore(
    context: Context,
    prefsOverride: SharedPreferences? = null,
) : ApiMultiAccountStore {

    private val appContext = context.applicationContext
    private val lock = Any()

    @Volatile
    private var cachedPrefs: SharedPreferences? = prefsOverride

    private fun securePrefs(): SharedPreferences = synchronized(lock) {
        cachedPrefs?.let { return it }
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ).also { cachedPrefs = it }
    }

    override fun loadRegistry(): List<ApiRegisteredAccountMeta> = synchronized(lock) {
        val raw = runCatching { securePrefs().getString(KEY_REGISTRY, null) }.getOrNull() ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    ApiRegisteredAccountMeta.fromJson(o)?.let { add(it) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun saveRegistry(accounts: List<ApiRegisteredAccountMeta>) {
        synchronized(lock) {
            val arr = JSONArray()
            accounts.forEach { arr.put(it.toJson()) }
            check(securePrefs().edit().putString(KEY_REGISTRY, arr.toString()).commit()) {
                "Failed to persist multi-account registry securely."
            }
        }
    }

    override fun loadCredentials(accountHash: String): ApiAccountCredentialRecord? = synchronized(lock) {
        if (accountHash.isBlank()) return null
        val raw = runCatching { securePrefs().getString(credKey(accountHash), null) }.getOrNull() ?: return null
        return ApiAccountCredentialRecord.fromJson(raw)
    }

    override fun saveCredentials(accountHash: String, record: ApiAccountCredentialRecord) {
        synchronized(lock) {
            require(accountHash.isNotBlank()) { "accountHash must not be blank" }
            check(securePrefs().edit().putString(credKey(accountHash), record.toJson().toString()).commit()) {
                "Failed to persist encrypted account credentials."
            }
        }
    }

    override fun deleteCredentials(accountHash: String) {
        synchronized(lock) {
            if (accountHash.isBlank()) return
            runCatching { securePrefs().edit().remove(credKey(accountHash)).commit() }
        }
    }

    override fun loadActiveAccountKey(): String? = synchronized(lock) {
        runCatching { securePrefs().getString(KEY_ACTIVE_ACCOUNT, null)?.ifBlank { null } }.getOrNull()
    }

    override fun saveActiveAccountKey(accountKey: String?) {
        synchronized(lock) {
            val ed = securePrefs().edit()
            if (accountKey.isNullOrBlank()) ed.remove(KEY_ACTIVE_ACCOUNT) else ed.putString(KEY_ACTIVE_ACCOUNT, accountKey)
            check(ed.commit()) { "Failed to persist active account selection." }
        }
    }

    override fun loadRecentSummaries(accountHash: String): List<AccountRecentChatSummary> = synchronized(lock) {
        if (accountHash.isBlank()) return emptyList()
        val raw = runCatching { securePrefs().getString(recentKey(accountHash), null) }.getOrNull() ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    AccountRecentChatSummary.fromJson(o)?.let { add(it) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun saveRecentSummaries(accountHash: String, summaries: List<AccountRecentChatSummary>) {
        synchronized(lock) {
            if (accountHash.isBlank()) return
            val arr = JSONArray()
            summaries.forEach { arr.put(it.toJson()) }
            runCatching { securePrefs().edit().putString(recentKey(accountHash), arr.toString()).commit() }
        }
    }

    override fun deleteRecentSummaries(accountHash: String) {
        synchronized(lock) {
            if (accountHash.isBlank()) return
            runCatching { securePrefs().edit().remove(recentKey(accountHash)).commit() }
        }
    }

    override fun loadLayoutState(): RecentCardsLayoutState = synchronized(lock) {
        val raw = runCatching { securePrefs().getString(KEY_LAYOUT_STATE, null) }.getOrNull()
        RecentCardsLayoutState.fromJson(raw)
    }

    override fun saveLayoutState(state: RecentCardsLayoutState) {
        synchronized(lock) {
            runCatching { securePrefs().edit().putString(KEY_LAYOUT_STATE, state.toJson().toString()).commit() }
        }
    }

    override fun loadEncryptedOutboxJson(accountHash: String): String? = synchronized(lock) {
        if (accountHash.isBlank()) return null
        runCatching { securePrefs().getString(outboxKey(accountHash), null) }.getOrNull()
    }

    override fun saveEncryptedOutboxJson(accountHash: String, json: String?) {
        synchronized(lock) {
            if (accountHash.isBlank()) return
            val ed = securePrefs().edit()
            if (json.isNullOrBlank()) ed.remove(outboxKey(accountHash)) else ed.putString(outboxKey(accountHash), json)
            runCatching { ed.commit() }
        }
    }

    override fun loadEncryptedDrafts(accountHash: String): Map<String, String> = synchronized(lock) {
        if (accountHash.isBlank()) return emptyMap()
        val raw = runCatching { securePrefs().getString(draftsKey(accountHash), null) }.getOrNull() ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            buildMap {
                o.keys().forEach { k ->
                    val v = o.optString(k, "")
                    if (k.isNotBlank() && v.isNotBlank()) put(k, v)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    override fun saveEncryptedDrafts(accountHash: String, drafts: Map<String, String>) {
        synchronized(lock) {
            if (accountHash.isBlank()) return
            val ed = securePrefs().edit()
            val clean = drafts.filter { it.key.isNotBlank() && it.value.isNotBlank() }
            if (clean.isEmpty()) {
                ed.remove(draftsKey(accountHash))
            } else {
                val o = JSONObject()
                clean.forEach { (k, v) -> o.put(k, v) }
                ed.putString(draftsKey(accountHash), o.toString())
            }
            runCatching { ed.commit() }
        }
    }

    override fun clearAccountData(accountHash: String) {
        synchronized(lock) {
            if (accountHash.isBlank()) return
            runCatching {
                securePrefs().edit()
                    .remove(credKey(accountHash))
                    .remove(recentKey(accountHash))
                    .remove(outboxKey(accountHash))
                    .remove(draftsKey(accountHash))
                    .commit()
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "chaty_multi_account_secure"
        private const val KEY_REGISTRY = "multi_account_registry_v1"
        private const val KEY_ACTIVE_ACCOUNT = "active_account_key_v1"
        private const val KEY_LAYOUT_STATE = "recent_cards_layout_v1"

        private fun credKey(hash: String) = "cred_v1_$hash"
        private fun recentKey(hash: String) = "recent_summary_v1_$hash"
        private fun outboxKey(hash: String) = "outbox_v1_$hash"
        private fun draftsKey(hash: String) = "drafts_v1_$hash"
    }
}
