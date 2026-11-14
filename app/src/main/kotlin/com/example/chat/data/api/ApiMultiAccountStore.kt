package com.example.chat.data.api

/** Encrypted multi-account persistence boundary. Implementations store credentials and metadata
 *  exclusively inside Android Keystore-backed EncryptedSharedPreferences, never plaintext prefs. */
interface ApiMultiAccountStore {
    fun loadRegistry(): List<ApiRegisteredAccountMeta>
    fun saveRegistry(accounts: List<ApiRegisteredAccountMeta>)
    fun loadCredentials(accountHash: String): ApiAccountCredentialRecord?
    fun saveCredentials(accountHash: String, record: ApiAccountCredentialRecord)
    fun deleteCredentials(accountHash: String)
    fun loadActiveAccountKey(): String?
    fun saveActiveAccountKey(accountKey: String?)
    fun loadRecentSummaries(accountHash: String): List<AccountRecentChatSummary>
    fun saveRecentSummaries(accountHash: String, summaries: List<AccountRecentChatSummary>)
    fun deleteRecentSummaries(accountHash: String)
    fun loadLayoutState(): RecentCardsLayoutState
    fun saveLayoutState(state: RecentCardsLayoutState)
    fun loadEncryptedOutboxJson(accountHash: String): String?
    fun saveEncryptedOutboxJson(accountHash: String, json: String?)

    /** Strict outbox read for the quarantine contract: null ONLY means no
     *  outbox was ever saved (legitimate-empty); a present-but-unreadable
     *  value must throw - reading a corrupt store as "empty" would fail the
     *  quarantine open on restart. The lenient API cannot tell the two
     *  apart, so this default THROWS: only a store with a checked contract
     *  (EncryptedMultiAccountStore) may serve strict reads. */
    fun loadEncryptedOutboxJsonStrict(accountHash: String): String? =
        throw UnsupportedOperationException(
            "Strict outbox reads require a store with a checked read contract (EncryptedMultiAccountStore).")

    /** Strict outbox write for the quarantine contract: returns only when
     *  the value is durably stored; throws otherwise. A read-back check
     *  cannot prove disk durability (only readability), so this default
     *  THROWS: only a store with a checked commit
     *  (EncryptedMultiAccountStore) may serve strict writes. */
    fun saveEncryptedOutboxJsonStrict(accountHash: String, json: String?) {
        throw UnsupportedOperationException(
            "Strict outbox writes require a store with a checked commit (EncryptedMultiAccountStore).")
    }
    fun loadEncryptedDrafts(accountHash: String): Map<String, String>
    fun saveEncryptedDrafts(accountHash: String, drafts: Map<String, String>)
    fun clearAccountData(accountHash: String)
}
