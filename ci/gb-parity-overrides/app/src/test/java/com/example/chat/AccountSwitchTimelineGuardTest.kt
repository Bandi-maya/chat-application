package com.example.chat

import com.example.chat.data.messaging.ConversationsRepository
import com.example.chat.data.messaging.SupabaseRpc
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic account-scope guard proofs: no real accounts, no network.
 *  [FakeScopeOwner] reproduces the production ApiAccountScopeOwner semaphore
 *  contract: ALL mutations and all guarded check+write pairs share one lock,
 *  so no generation change can interpose between check and write.
 *
 *  Effective-source adaptation: the candidate-era hydration entry points
 *  (applyHydratedConversationIfCurrent/applyHydratedMessagesIfCurrent/
 *  clearForAccountChange) are not part of the effective production source;
 *  the restore-listed branch ConversationsRepository exposes the same
 *  protection through the scope-guarded load paths, and the production
 *  account-switch wipe is the SessionLifecycleService pattern of direct
 *  store clears. Every original stale-account regression assertion below is
 *  preserved and exercised through those real production behaviors. */
class AccountSwitchTimelineGuardTest {

    private class FakeScopeOwner {
        private val lock = Any()
        private var current: Any? = null
        fun adopt(scope: Any?) = synchronized(lock) { current = scope }
        fun snapshot(): Any? = synchronized(lock) { current }
        fun withScope(expected: Any?, action: () -> Unit): Boolean = synchronized(lock) {
            if (expected == null || current !== expected) false else { action(); true }
        }
    }

    private class FakeRpc : SupabaseRpc {
        @Volatile var messagesResponse: List<Map<String, Any?>> = emptyList()
        @Volatile var conversationsResponse: List<Map<String, Any?>> = emptyList()
        @Volatile var messagesDelay: CompletableDeferred<Unit>? = null
        @Volatile var conversationsDelay: CompletableDeferred<Unit>? = null
        @Volatile var messagesCalls = 0
        val messagesEntered = CompletableDeferred<Unit>()
        val secondMessagesEntered = CompletableDeferred<Unit>()
        val conversationsEntered = CompletableDeferred<Unit>()
        override suspend fun rpc(function: String, params: Map<String, Any?>): Any? = when (function) {
            "get_conversation_messages" -> { messagesCalls++; messagesEntered.complete(Unit); if (messagesCalls >= 2) secondMessagesEntered.complete(Unit); messagesDelay?.await(); messagesResponse }
            "get_my_conversations" -> { conversationsEntered.complete(Unit); conversationsDelay?.await(); conversationsResponse }
            else -> emptyList<Any?>()
        }
    }

    private class RecordingCache : com.example.chat.data.messaging.SnapshotCacheStore {
        data class Write(val userId: String, val scope: String, val value: Any?)
        val writes = mutableListOf<Write>()
        override suspend fun readJson(userId: String, scope: String): Any? = null
        override suspend fun writeJson(userId: String, scope: String, value: Any?) {
            writes.add(Write(userId, scope, value))
        }
    }

    private fun row(id: String, chat: String, sender: String, body: String): Map<String, Any?> =
        mapOf("id" to id, "conversation_id" to chat, "sender_id" to sender,
            "body" to body, "type" to "text", "created_at" to "2026-10-27T00:00:00Z")

    private fun repo(rpc: FakeRpc, owner: FakeScopeOwner, user: String? = "u",
        cache: RecordingCache? = null) = ConversationsRepository(
        rpc = rpc, mls = { null }, currentUserId = { user },
        snapshotCache = cache,
        snapshotScope = cache?.let { CoroutineScope(Dispatchers.Unconfined) },
        scopeSnapshot = { owner.snapshot() },
        withScope = { expected, action -> owner.withScope(expected, action) },
    )

    @Test fun delayedOldAccountMessagesDroppedAfterSwitch() = runBlocking {
        val owner = FakeScopeOwner(); owner.adopt(Any()) // account A
        val rpc = FakeRpc().apply {
            messagesResponse = listOf(row("a1", "c1", "peer", "old account secret"))
            messagesDelay = CompletableDeferred()
        }
        val repo = repo(rpc, owner)
        val job = async { repo.loadMessages("c1") }
        rpc.messagesEntered.await()
        owner.adopt(Any()) // switch to B mid-flight (mutation under owner lock)
        rpc.messagesDelay!!.complete(Unit)
        job.await()
        assertTrue(repo.messagesByChatId.isEmpty())
    }

    @Test fun staleLoadConversationsDoesNotClobberNewAccountHydrate() = runBlocking {
        val owner = FakeScopeOwner(); owner.adopt(Any())
        val rpc = FakeRpc().apply {
            conversationsResponse = listOf(mapOf("conversation_id" to "a1", "kind" to "direct", "title" to "Old"))
            conversationsDelay = CompletableDeferred()
        }
        val repo = repo(rpc, owner)
        val job = async { repo.loadConversations(loadMembers = false) }
        rpc.conversationsEntered.await()
        owner.adopt(Any()) // switch account mid-flight
        // Production hydration under the new account: a fresh scope-guarded
        // load wholesale-replaces the store (branch loadConversations clears
        // then putAll inside the owner-lock guard).
        rpc.conversationsDelay!!.complete(Unit)
        rpc.conversationsResponse = listOf(mapOf("conversation_id" to "b1", "kind" to "direct", "title" to "New"))
        repo.loadConversations(loadMembers = false)
        job.await()
        assertEquals(setOf("b1"), repo.conversationsById.keys)
    }

    @Test fun sameChatIdAcrossAccountsKeepsOnlyCurrentRows() = runBlocking {
        val owner = FakeScopeOwner(); val scopeA = Any(); owner.adopt(scopeA)
        val rpc = FakeRpc()
        val repo = repo(rpc, owner)
        rpc.messagesResponse = listOf(row("a1", "c1", "peer", "A row"))
        repo.loadMessages("c1")
        assertEquals(listOf("a1"), repo.messagesByChatId["c1"]!!.map { it.id })
        // An A-scoped completion started BEFORE the switch loses its race even
        // though it finishes after B's hydrate.
        val gate = CompletableDeferred<Unit>(); rpc.messagesDelay = gate
        val stale = async { repo.loadMessages("c1") } // captures scopeA here
        var spins = 0
        while (rpc.messagesCalls < 2 && spins++ < 1000) kotlinx.coroutines.delay(2)
        assertEquals(2, rpc.messagesCalls)
        owner.adopt(Any())
        // Production account-switch wipe (SessionLifecycleService pattern:
        // direct store clears at the transition).
        repo.conversationsById.clear()
        repo.messagesByChatId.clear()
        repo.usersById.clear()
        gate.complete(Unit)
        // Production hydration under the new account.
        rpc.messagesResponse = listOf(row("b1", "c1", "peer", "B row"))
        repo.loadMessages("c1")
        stale.await()
        assertEquals(listOf("b1"), repo.messagesByChatId["c1"]!!.map { it.id })
    }

    @Test fun accountSwitchWipeDropsStaleHydration() = runBlocking {
        val owner = FakeScopeOwner(); val scopeA = Any(); owner.adopt(scopeA)
        val rpc = FakeRpc().apply {
            conversationsResponse = listOf(mapOf("conversation_id" to "c1", "kind" to "direct", "title" to "T"))
            messagesResponse = listOf(row("m1", "c1", "peer", "x"))
        }
        val repo = repo(rpc, owner)
        repo.loadConversations(loadMembers = false)
        repo.loadMessages("c1")
        assertTrue(repo.conversationsById.isNotEmpty())
        assertTrue(repo.messagesByChatId.isNotEmpty())
        // A completion captured under the OLD account that finishes after the
        // switch must never republish into wiped stores.
        val gate = CompletableDeferred<Unit>(); rpc.messagesDelay = gate
        val stale = async { repo.loadMessages("c1") } // captures scopeA
        // Start the suspended second load before changing the owner identity.
        rpc.secondMessagesEntered.await()
        assertEquals(2, rpc.messagesCalls)
        // Switch: production wipe is the direct-clear pattern.
        owner.adopt(Any())
        repo.conversationsById.clear()
        repo.messagesByChatId.clear()
        repo.usersById.clear()
        gate.complete(Unit)
        stale.await()
        assertTrue(repo.conversationsById.isEmpty())
        assertTrue(repo.messagesByChatId.isEmpty())
        assertTrue(repo.usersById.isEmpty())
    }

    @Test fun sameUserGenerationChangeDropsInFlightButPreservesPublished() = runBlocking {
        val owner = FakeScopeOwner(); owner.adopt(Any())
        val rpc = FakeRpc().apply {
            messagesResponse = listOf(row("m1", "c1", "peer", "published"))
        }
        val repo = repo(rpc, owner)
        repo.loadMessages("c1")
        assertEquals(1, repo.messagesByChatId["c1"]!!.size)
        // Same user, new generation (renewal/tenant/route): NO wipe is issued,
        // published rows stay, and anything captured under the old generation
        // is dropped.
        val gate = CompletableDeferred<Unit>(); rpc.messagesDelay = gate
        val stale = async { repo.loadMessages("c2") } // captures the old generation
        var spins = 0
        while (rpc.messagesCalls < 2 && spins++ < 1000) kotlinx.coroutines.delay(2)
        assertEquals(2, rpc.messagesCalls)
        owner.adopt(Any())
        gate.complete(Unit)
        stale.await()
        assertEquals(1, repo.messagesByChatId["c1"]!!.size)
        assertTrue(repo.messagesByChatId["c2"] == null)
        assertTrue(repo.conversationsById.isEmpty())
    }

    @Test fun nullScopeCaptureFailsClosedAfterLogin() = runBlocking {
        val owner = FakeScopeOwner() // logged out: current == null
        val rpc = FakeRpc().apply {
            messagesResponse = listOf(row("x1", "c1", "peer", "pre-login"))
            messagesDelay = CompletableDeferred()
        }
        val repo = repo(rpc, owner, user = null)
        val job = async { repo.loadMessages("c1") } // captures null
        rpc.messagesEntered.await()
        owner.adopt(Any()) // login to B while the unauthenticated load is in flight
        rpc.messagesDelay!!.complete(Unit)
        job.await()
        assertTrue("a null-captured completion must never publish after login",
            repo.messagesByChatId.isEmpty())
    }

    @Test fun logoutDuringPendingLoadDropsWrite() = runBlocking {
        val owner = FakeScopeOwner(); owner.adopt(Any())
        val rpc = FakeRpc().apply {
            messagesResponse = listOf(row("a1", "c1", "peer", "secret"))
            messagesDelay = CompletableDeferred()
        }
        val repo = repo(rpc, owner)
        val job = async { repo.loadMessages("c1") }
        rpc.messagesEntered.await()
        owner.adopt(null) // logout mid-flight
        rpc.messagesDelay!!.complete(Unit)
        job.await()
        assertTrue(repo.messagesByChatId.isEmpty())
    }

    @Test fun persistNeverRunsForStaleCompletion() = runBlocking {
        val owner = FakeScopeOwner(); owner.adopt(Any())
        val cache = RecordingCache()
        val rpc = FakeRpc().apply {
            messagesResponse = listOf(row("a1", "c1", "peer", "stale"))
            messagesDelay = CompletableDeferred()
        }
        val repo = repo(rpc, owner, cache = cache)
        val job = async { repo.loadMessages("c1") }
        rpc.messagesEntered.await()
        owner.adopt(Any())
        rpc.messagesDelay!!.complete(Unit)
        job.await()
        assertTrue(cache.writes.isEmpty())
    }

    @Test fun persistUsesGuardCapturedUserIdAndRows() = runBlocking {
        val owner = FakeScopeOwner(); owner.adopt(Any())
        val cache = RecordingCache()
        val rpc = FakeRpc().apply {
            conversationsResponse = listOf(mapOf("conversation_id" to "c1", "kind" to "direct", "title" to "T"))
        }
        val repo = repo(rpc, owner, user = "userA", cache = cache)
        repo.loadConversations(loadMembers = false)
        assertEquals(1, cache.writes.size)
        assertEquals("userA", cache.writes[0].userId)
        assertEquals("conversations", cache.writes[0].scope)
    }

    @Test fun rapidSwitchBtoADropsBCompletion() = runBlocking {
        val owner = FakeScopeOwner(); owner.adopt(Any()) // B active
        val rpc = FakeRpc().apply {
            messagesResponse = listOf(row("b1", "c1", "peer", "B row"))
            messagesDelay = CompletableDeferred()
        }
        val repo = repo(rpc, owner)
        val job = async { repo.loadMessages("c1") }
        rpc.messagesEntered.await()
        owner.adopt(Any()) // rapid switch back to A
        rpc.messagesDelay!!.complete(Unit)
        job.await()
        assertTrue(repo.messagesByChatId.isEmpty())
    }

    @Test fun legacyConstructorWithoutSeamsRemainsUnguarded() = runBlocking {
        val rpc = FakeRpc().apply {
            messagesResponse = listOf(row("m1", "c1", "peer", "hello"))
        }
        val repo = ConversationsRepository(rpc = rpc, mls = { null }, currentUserId = { "u" })
        repo.loadMessages("c1")
        assertEquals(1, repo.messagesByChatId["c1"]!!.size)
    }
}
