package com.example.chat

import com.example.chat.data.api.ApiAuthResult
import com.example.chat.data.api.ApiAuthSession
import com.example.chat.data.api.ApiSessionStore
import com.example.chat.data.demo.DemoMode
import com.example.chat.data.demo.DemoSampleData
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** STRICT demo-identity proof for persisted REST credentials (peer V5
 *  mixed-store blocker): a pair is demo only when BOTH sides provably match.
 *  Mixed or damaged pairs are never classified demo and never erased. */
class DemoRestSessionIdentityTest {

    private fun demoUserJson(): String = JSONObject()
        .put("id", DemoMode.USER_ID)
        .put("username", "demo")
        .put("displayName", "Demo User")
        .toString()

    private fun realUserJson(): String = JSONObject()
        .put("id", "user-123")
        .put("username", "bandi")
        .toString()

    @Test fun strictDemoPairMatches() {
        assertTrue(DemoMode.isDemoRestSession(DemoMode.TOKEN, demoUserJson()))
        // key order and whitespace must not matter: parsed, not string-matched
        val reordered = JSONObject().put("displayName", "Demo User").put("id", DemoMode.USER_ID).toString()
        assertTrue(DemoMode.isDemoRestSession(DemoMode.TOKEN, reordered))
    }

    @Test fun mixedRealTokenDemoJsonIsNeverDemo() {
        // The load-bearing case: erasing this pair would destroy a real token.
        assertFalse(DemoMode.isDemoRestSession("real-jwt", demoUserJson()))
    }

    @Test fun mixedDemoTokenRealJsonIsNeverDemo() {
        assertFalse(DemoMode.isDemoRestSession(DemoMode.TOKEN, realUserJson()))
    }

    @Test fun unprovablePairsAreNeverDemo() {
        assertFalse(DemoMode.isDemoRestSession(null, null))
        assertFalse(DemoMode.isDemoRestSession(null, demoUserJson()))
        assertFalse(DemoMode.isDemoRestSession(DemoMode.TOKEN, null))
        // damaged payload is not provably demo: preserved, never erased
        assertFalse(DemoMode.isDemoRestSession(DemoMode.TOKEN, """{"id":"demo:offline-user", broken"""))
        assertFalse(DemoMode.isDemoRestSession("real-jwt", realUserJson()))
    }

    @Test fun mixedDetectorMatrix() {
        // exactly one side demo -> quarantine; both-same -> not mixed
        assertTrue(DemoMode.isMixedRestSession("real-jwt", DemoMode.USER_ID))
        assertTrue(DemoMode.isMixedRestSession(DemoMode.TOKEN, "user-123"))
        assertFalse(DemoMode.isMixedRestSession(DemoMode.TOKEN, DemoMode.USER_ID))
        assertFalse(DemoMode.isMixedRestSession("real-jwt", "user-123"))
        assertFalse(DemoMode.isMixedRestSession(null, null))
    }

    private class MemoryStore : ApiSessionStore {
        var token: String? = null
        var userJson: String? = null
        override fun loadToken() = token
        override fun loadUserJson() = userJson
        override fun save(token: String, userJson: String) { this.token = token; this.userJson = userJson }
        override fun clear() { token = null; userJson = null }
    }

    @Test fun demoAdoptThenSignOutClearsMemoryAndPersistedStore() {
        val store = MemoryStore()
        val session = ApiAuthSession(store)
        session.adopt(ApiAuthResult(DemoMode.TOKEN, DemoSampleData.demoUser()))
        assertTrue(DemoMode.isDemoRestSession(store.loadToken(), store.loadUserJson()))
        session.signOut()
        assertNull(session.token)
        assertNull(session.currentUser)
        assertFalse(DemoMode.isDemoRestSession(store.loadToken(), store.loadUserJson()))
    }

    @Test fun clearInMemoryOnlyPreservesNonDemoPersistedBytes() {
        val store = MemoryStore()
        val session = ApiAuthSession(store)
        session.adopt(ApiAuthResult(DemoMode.TOKEN, DemoSampleData.demoUser()))
        // Simulate a mixed store: real bytes landed behind the demo session.
        store.save("real-jwt", realUserJson())
        session.clearInMemoryOnly()
        assertNull("in-memory demo session dropped", session.token)
        assertNull(session.currentUser)
        assertEquals("raw real bytes must survive", "real-jwt", store.loadToken())
        assertEquals(realUserJson(), store.loadUserJson())
    }
}
