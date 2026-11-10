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
    fun loadEncryptedDrafts(accountHash: String): Map<String, String>
    fun saveEncryptedDrafts(accountHash: String, drafts: Map<String, String>)
    fun clearAccountData(accountHash: String)
}
