package com.example.chat

import com.example.chat.data.api.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Production helper used by room/task loaders, with delayed synthetic responses. */
class ScopedApiPublicationTest {
    private class Store : ApiSessionStore {
        override fun loadToken(): String? = null
        override fun loadUserJson(): String? = null
        override fun save(token: String, userJson: String) {}
        override fun clear() {}
    }
    private fun user(id: String) = ApiAuthResult("synthetic-$id", ApiUserProfile(id, id, id, id))
    private fun staleResult(change: (ApiAuthSession) -> Unit, result: String) = runBlocking {
        val lock = Any()
        val session = ApiAuthSession(Store(), lock)
        session.adopt(user("A"))
        val owner = ApiAccountScopeOwner(lock, session::snapshot, { ApiRoutingSnapshot("synthetic", "https://invalid.example", "", 0) })
        val expected = owner.snapshot()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var published = "B-current"
        val operation = async {
            publishScopedApiResult(expected, owner::withScope, fetch = {
                started.complete(Unit); release.await(); result
            }, publish = { published = it })
        }
        started.await(); change(session); release.complete(Unit)
        assertFalse(operation.await())
        assertEquals("B-current", published)
    }
    @Test fun delayedRoomSuccessCannotPublishAfterSwitch() = staleResult({ it.adopt(user("B")) }, "A-rooms")
    @Test fun delayedSubscriptionCannotPublishAfterSameAccountRelogin() = staleResult({ it.signOut(); it.adopt(user("A")) }, "A-subscription-required")
    @Test fun delayedTasksCannotPublishAfterSwitchAndReturnAba() = staleResult({ it.adopt(user("B")); it.adopt(user("A")) }, "A-tasks")
    @Test fun currentScopePublishesExactlyOnce() = runBlocking {
        val lock = Any(); val session = ApiAuthSession(Store(), lock); session.adopt(user("A"))
        val owner = ApiAccountScopeOwner(lock, session::snapshot, { ApiRoutingSnapshot("synthetic", "https://invalid.example", "", 0) })
        var calls = 0
        assertTrue(publishScopedApiResult(owner.snapshot(), owner::withScope, { listOf("synthetic-room") }) {
            assertEquals(listOf("synthetic-room"), it); calls++
        })
        assertEquals(1, calls)
    }
}
