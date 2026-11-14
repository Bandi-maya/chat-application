package com.example.chat

import com.example.chat.data.api.AccountRecentChatSummary
import com.example.chat.data.api.ApiAccountCredentialRecord
import com.example.chat.data.api.ApiAccountRoutingCanonicalizer
import com.example.chat.data.api.ApiAuthClient
import com.example.chat.data.api.ApiAuthResult
import com.example.chat.data.api.ApiMultiAccountStore
import com.example.chat.data.api.ApiRegisteredAccountMeta
import com.example.chat.data.api.ApiRoutingSnapshot
import com.example.chat.data.api.ApiSessionStore
import com.example.chat.data.api.ApiUserProfile
import com.example.chat.data.api.JwtExpiryDecoder
import com.example.chat.data.api.MultiAccountRegistryCoordinator
import com.example.chat.data.api.RecentCardsLayoutState
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedMultiAccountRegistryTest {

    private class FakeMultiAccountStore : ApiMultiAccountStore {
        var registry: List<ApiRegisteredAccountMeta> = emptyList()
        val credentials = mutableMapOf<String, ApiAccountCredentialRecord>()
        var activeKey: String? = null
        val summaries = mutableMapOf<String, List<AccountRecentChatSummary>>()
        var layoutState = RecentCardsLayoutState()
        val outbox = mutableMapOf<String, String>()
        val drafts = mutableMapOf<String, Map<String, String>>()

        override fun loadRegistry() = registry
        override fun saveRegistry(accounts: List<ApiRegisteredAccountMeta>) { registry = accounts.toList() }
        override fun loadCredentials(accountHash: String) = credentials[accountHash]
        override fun saveCredentials(accountHash: String, record: ApiAccountCredentialRecord) { credentials[accountHash] = record }
        override fun deleteCredentials(accountHash: String) { credentials.remove(accountHash) }
        override fun loadActiveAccountKey() = activeKey
        override fun saveActiveAccountKey(accountKey: String?) { activeKey = accountKey }
        override fun loadRecentSummaries(accountHash: String) = summaries[accountHash].orEmpty()
        override fun saveRecentSummaries(accountHash: String, summaries: List<AccountRecentChatSummary>) {
            this.summaries[accountHash] = summaries.toList()
        }
        override fun deleteRecentSummaries(accountHash: String) { summaries.remove(accountHash) }
        override fun loadLayoutState() = layoutState
        override fun saveLayoutState(state: RecentCardsLayoutState) { layoutState = state }
        override fun loadEncryptedOutboxJson(accountHash: String) = outbox[accountHash]
        override fun saveEncryptedOutboxJson(accountHash: String, json: String?) {
            if (json == null) outbox.remove(accountHash) else outbox[accountHash] = json
        }
        override fun loadEncryptedDrafts(accountHash: String) = drafts[accountHash].orEmpty()
        override fun saveEncryptedDrafts(accountHash: String, drafts: Map<String, String>) {
            if (drafts.isEmpty()) this.drafts.remove(accountHash) else this.drafts[accountHash] = drafts.toMap()
        }
        override fun clearAccountData(accountHash: String) {
            credentials.remove(accountHash)
            summaries.remove(accountHash)
            outbox.remove(accountHash)
            drafts.remove(accountHash)
        }
    }

    private class FakeLegacyStore : ApiSessionStore {
        var token: String? = null
        var userJson: String? = null
        var refresh: String? = null
        var clearedFlag: Boolean = false
        var clearCalls: Int = 0

        override fun loadToken() = token
        override fun loadUserJson() = userJson
        override fun save(token: String, userJson: String) {
            this.token = token
            this.userJson = userJson
            this.clearedFlag = false
        }
        override fun loadRefreshToken() = refresh
        override fun saveRefreshToken(token: String?) { refresh = token }
        override fun wasExplicitlyCleared() = clearedFlag
        override fun clear() {
            token = null
            userJson = null
            refresh = null
            clearedFlag = true
            clearCalls++
        }
    }

    private fun jwtWithExp(expSeconds: Long): String {
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"HS256"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"u1","exp":$expSeconds}""".toByteArray())
        return "$header.$payload.sig"
    }

    private fun user(id: String, name: String = "User $id") = ApiUserProfile(
        id = id, username = "u_$id", displayName = name, avatarInitials = id.take(2).uppercase(),
    )

    @Test
    fun defaultTwoAccountCapAllowsAnyMixAndNeverRemovesExistingSessions() {
        val multi = FakeMultiAccountStore()
        val coordinator = MultiAccountRegistryCoordinator(multi)
        val personal = ApiRoutingSnapshot("personal", "https://api.example.com", "wss://api.example.com/ws", 0L)
        val organization = personal.copy(tenantId = "organization")
        val first = coordinator.registerAuthenticatedAccount(ApiAuthResult("token1", user("u1")), personal, true)
        val second = coordinator.registerAuthenticatedAccount(ApiAuthResult("token2", user("u2")), personal, true)
        assertNotNull(first); assertNotNull(second)
        val active = multi.activeKey
        assertNull(coordinator.registerAuthenticatedAccount(ApiAuthResult("token3", user("u3")), organization, false))
        assertEquals(2, multi.registry.size)
        assertEquals(active, multi.activeKey)
        assertEquals(2, multi.credentials.size)
        assertNotNull(coordinator.registerAuthenticatedAccount(ApiAuthResult("renewed", user("u1")), personal, true))
        assertEquals(2, multi.registry.size)
    }

    @Test
    fun sameUserIdAcrossTenantsProducesDistinctAccountKeysAndHashes() {
        val k1 = ApiAccountRoutingCanonicalizer.accountKey("Acme ", "HTTPS://API.Chaty.app:443/", "user-1")
        val k2 = ApiAccountRoutingCanonicalizer.accountKey("beta", "https://api.chaty.app", "user-1")
        val k1Alias = ApiAccountRoutingCanonicalizer.accountKey("acme", "https://api.chaty.app", "user-1")
        assertNotNull(k1)
        assertNotNull(k2)
        assertEquals(k1, k1Alias)
        assertNotEquals(k1, k2)
        assertNotEquals(
            ApiAccountRoutingCanonicalizer.accountHash(k1!!),
            ApiAccountRoutingCanonicalizer.accountHash(k2!!),
        )
    }

    @Test
    fun migratesLegacySingleAccountWithoutClearingLegacyStoreOrLoggingOut() {
        val multi = FakeMultiAccountStore()
        val legacy = FakeLegacyStore().apply {
            save("legacy-tok", com.example.chat.data.api.apiUserToJson(user("u1")))
            saveRefreshToken("legacy-ref")
        }
        val coordinator = MultiAccountRegistryCoordinator(multi, maxAccounts = 5)
        val routing = ApiRoutingSnapshot("tenant-a", "https://api.example.com", "wss://api.example.com/ws", 0L)
        val migrated = coordinator.migrateLegacyIfNeeded(legacy, routing, isDefaultTenant = false)
        assertNotNull(migrated)
        assertEquals("u1", migrated!!.userId)
        assertEquals("tenant-a", migrated.tenantId)
        assertEquals(0, legacy.clearCalls)
        assertEquals("legacy-tok", legacy.token)
        assertEquals(1, multi.registry.size)
        assertEquals(migrated.accountKey, multi.activeKey)
        assertEquals("legacy-tok", multi.loadCredentials(migrated.accountHash)?.accessToken)
        assertEquals("legacy-ref", multi.loadCredentials(migrated.accountHash)?.refreshToken)
    }

    @Test
    fun respectsExplicitlyClearedTombstoneAndRejectsDemoSessions() {
        val multi = FakeMultiAccountStore()
        val legacy = FakeLegacyStore().apply {
            token = "stale-tok"
            userJson = com.example.chat.data.api.apiUserToJson(user("u1"))
            clearedFlag = true
        }
        val coordinator = MultiAccountRegistryCoordinator(multi)
        val routing = ApiRoutingSnapshot("t1", "https://api.example.com", "", 0L)
        assertNull(coordinator.migrateLegacyIfNeeded(legacy, routing, isDefaultTenant = true))

        val demoAuth = ApiAuthResult(
            token = com.example.chat.data.demo.DemoMode.TOKEN,
            user = user(com.example.chat.data.demo.DemoMode.USER_ID),
        )
        assertNull(coordinator.registerAuthenticatedAccount(demoAuth, routing, isDefaultTenant = true))
        assertTrue(multi.registry.isEmpty())
    }

    @Test
    fun expiredAccountMarksOnlyItselfExpiredOnRejectedRefresh() = runBlocking {
        val multi = FakeMultiAccountStore()
        val coordinator = MultiAccountRegistryCoordinator(multi)
        val routing = ApiRoutingSnapshot("t1", "https://api.example.com", "", 0L)
        val pastJwt = jwtWithExp((System.currentTimeMillis() / 1000L) - 60L)
        val validJwt = jwtWithExp((System.currentTimeMillis() / 1000L) + 3600L)
        val metaA = coordinator.registerAuthenticatedAccount(
            ApiAuthResult(validJwt, user("a"), refreshToken = "ref-a"), routing, true,
        )!!
        val metaB = coordinator.registerAuthenticatedAccount(
            ApiAuthResult(pastJwt, user("b"), refreshToken = "ref-b"), routing, true,
        )!!

        assertEquals(((System.currentTimeMillis() / 1000L) + 3600L) * 1000L, JwtExpiryDecoder.decodeExpiryEpochMs(validJwt))

        val refreshed = coordinator.refreshAccountIfNeeded(metaB.accountKey) { _, _, _ ->
            ApiAuthClient.RefreshResult.Rejected
        }
        assertFalse(refreshed)
        val updatedRegistry = multi.loadRegistry()
        assertFalse(updatedRegistry.first { it.accountKey == metaA.accountKey }.sessionExpired)
        assertTrue(updatedRegistry.first { it.accountKey == metaB.accountKey }.sessionExpired)
    }

    @Test
    fun signOutInactiveRemovesOnlyTargetAndSignOutActiveSelectsFallback() {
        val multi = FakeMultiAccountStore()
        val coordinator = MultiAccountRegistryCoordinator(multi)
        val routing = ApiRoutingSnapshot("t1", "https://api.example.com", "", 0L)
        val a = coordinator.registerAuthenticatedAccount(ApiAuthResult("tok-a", user("a")), routing, true)!!
        val b = coordinator.registerAuthenticatedAccount(ApiAuthResult("tok-b", user("b")), routing, true)!!

        // Currently active = b. Removing a leaves b intact.
        val nextAfterInactive = coordinator.removeAccount(a.accountKey)
        assertEquals(b.accountKey, multi.activeKey)
        assertEquals(b.accountKey, nextAfterInactive?.accountKey)
        assertNull(multi.loadCredentials(a.accountHash))
        assertNotNull(multi.loadCredentials(b.accountHash))

        // Add c, active becomes c. Removing c falls back to b.
        val c = coordinator.registerAuthenticatedAccount(ApiAuthResult("tok-c", user("c")), routing, true)!!
        assertEquals(c.accountKey, multi.activeKey)
        val fallback = coordinator.removeAccount(c.accountKey)
        assertEquals(b.accountKey, fallback?.accountKey)
        assertNull(multi.loadCredentials(c.accountHash))

        // Removing final account b yields null (full sign-out).
        val afterLast = coordinator.removeAccount(b.accountKey)
        assertNull(afterLast)
        assertTrue(multi.registry.isEmpty())
        assertNull(multi.activeKey)
    }

    @Test
    fun layoutOrderAndPerAccountNavigationContextPersistAcrossRestarts() {
        val multi = FakeMultiAccountStore()
        val c1 = MultiAccountRegistryCoordinator(multi)
        val routing = ApiRoutingSnapshot("t1", "https://api.example.com", "", 0L)
        val a = c1.registerAuthenticatedAccount(ApiAuthResult("ta", user("a")), routing, true)!!
        val b = c1.registerAuthenticatedAccount(ApiAuthResult("tb", user("b")), routing, true)!!

        c1.updateRecentCardOrder(listOf(b.accountKey, a.accountKey))
        c1.setAccountNavigationState(a.accountKey, selectedConversationId = "shared-room-1", navContext = "unread")
        c1.setAccountNavigationState(b.accountKey, selectedConversationId = "shared-room-1", navContext = "groups")

        // New coordinator instance reading same persisted store
        val c2 = MultiAccountRegistryCoordinator(multi)
        assertEquals(listOf(b.accountKey, a.accountKey), c2.orderedAccountKeys())
        assertEquals("shared-room-1", c2.selectedConversationFor(a.accountKey))
        assertEquals("unread", c2.navContextFor(a.accountKey))
        assertEquals("groups", c2.navContextFor(b.accountKey))
    }
}
