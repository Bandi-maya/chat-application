package com.example.chat.data.messaging

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class PendingStoreIsolationTest {
    private class Fake : SnapshotCacheStore {
        val m = HashMap<String, Any?>()
        override suspend fun readJson(userId: String, scope: String) = m["$userId|$scope"]
        override suspend fun writeJson(userId: String, scope: String, value: Any?) { m["$userId|$scope"] = value }
    }
    private fun p(id: String) = PendingSecureSend(id, "c1", "text", "hi", emptyMap(), Instant.parse("2026-01-01T00:00:00Z"))

    @Test fun accountsNeverSeeEachOthersQueue() = runBlocking {
        val s = PendingSecureSendStore(Fake())
        s.put("A", p("a1")); s.put("B", p("b1"))
        assertEquals(listOf("a1"), s.read("A").map { it.clientMessageId })
        assertEquals(listOf("b1"), s.read("B").map { it.clientMessageId })
        s.clear("A")
        assertTrue(s.read("A").isEmpty()); assertEquals(1, s.read("B").size)
    }
    @Test fun removeOnlyAffectsThatAccountAndDedupes() = runBlocking {
        val s = PendingSecureSendStore(Fake())
        s.put("A", p("x")); s.put("B", p("x")); s.put("A", p("x"))
        assertEquals(1, s.read("A").size)
        s.remove("A", "x")
        assertTrue(s.read("A").isEmpty()); assertEquals(1, s.read("B").size)
    }
    @Test fun capEnforcedPerAccount() = runBlocking {
        val s = PendingSecureSendStore(Fake())
        repeat(PendingSecureSendStore.MAX_PENDING) { s.put("A", p("i$it")) }
        assertTrue(runCatching { s.put("A", p("over")) }.isFailure)
        s.put("B", p("ok"))
        assertEquals(1, s.read("B").size)
    }
    @Test fun corruptEntriesIgnored() = runBlocking {
        val f = Fake(); f.m["A|pending_secure_sends"] = listOf("junk", mapOf("client_message_id" to "", "conversation_id" to "c"), 5)
        assertTrue(PendingSecureSendStore(f).read("A").isEmpty())
    }
}
