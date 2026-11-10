package com.example.chat

import com.example.chat.data.api.AccountRecentChatSummary
import com.example.chat.data.api.ApiAccountCredentialRecord
import com.example.chat.data.api.ApiAccountRoutingCanonicalizer
import com.example.chat.data.api.ApiAuthResult
import com.example.chat.data.api.ApiMultiAccountStore
import com.example.chat.data.api.ApiRegisteredAccountMeta
import com.example.chat.data.api.ApiRoutingSnapshot
import com.example.chat.data.api.ApiUserProfile
import com.example.chat.data.api.MultiAccountRegistryCoordinator
import com.example.chat.data.api.RecentCardsLayoutState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies strict per-account isolation when multiple accounts share
 * identical conversation IDs or identical user IDs across different tenants.
 */
class MultiAccountIsolationProofTest {

    private class MemoryMultiStore : ApiMultiAccountStore {
        private var registry: List<ApiRegisteredAccountMeta> = emptyList()
        private val creds = mutableMapOf<String, ApiAccountCredentialRecord>()
        private var active: String? = null
        private val summaries = mutableMapOf<String, List<AccountRecentChatSummary>>()
        private var layout = RecentCardsLayoutState()
        private val outbox = mutableMapOf<String, String>()
        private val drafts = mutableMapOf<String, Map<String, String>>()

        override fun loadRegistry() = registry
        override fun saveRegistry(accounts: List<ApiRegisteredAccountMeta>) { registry = accounts.toList() }
        override fun loadCredentials(accountHash: String) = creds[accountHash]
        override fun saveCredentials(accountHash: String, record: ApiAccountCredentialRecord) { creds[accountHash] = record }
        override fun deleteCredentials(accountHash: String) { creds.remove(accountHash) }
        override fun loadActiveAccountKey() = active
        override fun saveActiveAccountKey(accountKey: String?) { active = accountKey }
        override fun loadRecentSummaries(accountHash: String) = summaries[accountHash].orEmpty()
        override fun saveRecentSummaries(accountHash: String, summaries: List<AccountRecentChatSummary>) {
            this.summaries[accountHash] = summaries.toList()
        }
        override fun deleteRecentSummaries(accountHash: String) { summaries.remove(accountHash) }
        override fun loadLayoutState() = layout
        override fun saveLayoutState(state: RecentCardsLayoutState) { layout = state }
        override fun loadEncryptedOutboxJson(accountHash: String) = outbox[accountHash]
        override fun saveEncryptedOutboxJson(accountHash: String, json: String?) {
            if (json == null) outbox.remove(accountHash) else outbox[accountHash] = json
        }
        override fun loadEncryptedDrafts(accountHash: String) = drafts[accountHash].orEmpty()
        override fun saveEncryptedDrafts(accountHash: String, drafts: Map<String, String>) {
            if (drafts.isEmpty()) this.drafts.remove(accountHash) else this.drafts[accountHash] = drafts.toMap()
        }
        override fun clearAccountData(accountHash: String) {
            creds.remove(accountHash)
            summaries.remove(accountHash)
            outbox.remove(accountHash)
            drafts.remove(accountHash)
        }
    }

    @Test
    fun collidingConversationIdsRemainStrictlyIsolatedByAccount() {
        val store = MemoryMultiStore()
        val coordinator = MultiAccountRegistryCoordinator(store)

        val tenantA = ApiRoutingSnapshot("org-alpha", "https://alpha.chaty.test", "", 0L)
        val tenantB = ApiRoutingSnapshot("org-beta", "https://beta.chaty.test", "", 1L)
        val sameUserId = "shared-user-42"

        val metaA = coordinator.registerAuthenticatedAccount(
            ApiAuthResult("tok-alpha", ApiUserProfile(sameUserId, "alice_a", "Alice Alpha", "AA")),
            tenantA,
            isDefaultTenant = false,
        )!!
        val metaB = coordinator.registerAuthenticatedAccount(
            ApiAuthResult("tok-beta", ApiUserProfile(sameUserId, "alice_b", "Alice Beta", "AB")),
            tenantB,
            isDefaultTenant = false,
        )!!

        assertNotEquals(metaA.accountKey, metaB.accountKey)
        assertNotEquals(metaA.accountHash, metaB.accountHash)

        coordinator.saveRecentConversations(
            metaA.accountKey,
            listOf(AccountRecentChatSummary("room-1", "Alpha Security Team", "group", unreadCount = 3)),
        )
        coordinator.saveRecentConversations(
            metaB.accountKey,
            listOf(AccountRecentChatSummary("room-1", "Beta Product Room", "direct", unreadCount = 0)),
        )

        coordinator.saveDraft(metaA.accountKey, "room-1", "Alpha confidential draft")
        coordinator.saveDraft(metaB.accountKey, "room-1", "Beta draft")

        assertEquals("Alpha Security Team", coordinator.recentConversationsFor(metaA.accountKey).single().title)
        assertEquals("Beta Product Room", coordinator.recentConversationsFor(metaB.accountKey).single().title)
        assertEquals(3, coordinator.recentConversationsFor(metaA.accountKey).single().unreadCount)
        assertEquals(0, coordinator.recentConversationsFor(metaB.accountKey).single().unreadCount)
        assertEquals("Alpha confidential draft", coordinator.draftFor(metaA.accountKey, "room-1"))
        assertEquals("Beta draft", coordinator.draftFor(metaB.accountKey, "room-1"))

        // Deleting Account A must never disturb Account B's colliding room-1 drafts or recents
        coordinator.removeAccount(metaA.accountKey)
        assertTrue(coordinator.recentConversationsFor(metaA.accountKey).isEmpty())
        assertEquals("Beta Product Room", coordinator.recentConversationsFor(metaB.accountKey).single().title)
        assertEquals("Beta draft", coordinator.draftFor(metaB.accountKey, "room-1"))
        assertTrue(ApiAccountRoutingCanonicalizer.isValidEndpointUrl("https://alpha.chaty.test"))
        assertTrue(ApiAccountRoutingCanonicalizer.isValidEndpointUrl("http://127.0.0.1:8000"))
    }
}
