package com.example.chat

import com.example.chat.data.api.ApiCallHistoryClient
import com.example.chat.data.api.ApiHttpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Call history client (research 5.4): paged reads, per-viewer deletes. */
class CallHistoryClientTest {

    private data class Call(val method: String, val url: String)

    private class FakeTransport(var handler: (Call) -> Pair<Int, String>) : ApiHttpTransport {
        val calls = mutableListOf<Call>()
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            val call = Call(method, url)
            calls.add(call)
            return handler(call)
        }
    }

    private fun client(t: FakeTransport) =
        ApiCallHistoryClient({ "http://10.0.2.2:8000" }, { "chat-application" }, t, token = { "tok" })

    private val entry = """
        {"id":"e1","call_id":"c1","direction":"incoming","other_user_id":"u9",
         "kind":"video","status":"missed","started_at":"2026-11-08T07:00:00","duration_sec":0,
         "other_user":{"id":"u9","display_name":"Maya","username":"maya.j"}}
    """.trimIndent()

    @Test
    fun historyParsesEntriesAndCursor() {
        val t = FakeTransport { call ->
            assertTrue(call.url.contains("calls/history?limit=50"))
            200 to """{"data":{"entries":[$entry],"next_cursor":"2026-11-08T07:00:00"}}"""
        }
        val page = client(t).history()
        assertEquals(1, page.entries.size)
        val e = page.entries[0]
        assertEquals("e1", e.id)
        assertEquals("incoming", e.direction)
        assertEquals("video", e.kind)
        assertEquals("missed", e.status)
        assertEquals("Maya", e.otherDisplayName)
        assertEquals("maya.j", e.otherUsername)
        assertEquals("2026-11-08T07:00:00", page.nextCursor)
    }

    @Test
    fun beforeCursorIsEncoded() {
        val t = FakeTransport { call ->
            assertTrue(call.url.contains("before=2026-11-08T07%3A00%3A00"))
            200 to """{"data":{"entries":[],"next_cursor":null}}"""
        }
        val page = client(t).history(before = "2026-11-08T07:00:00")
        assertTrue(page.entries.isEmpty())
        assertNull(page.nextCursor)
    }

    @Test
    fun planeMissingThrowsPlaneUnavailable() {
        val t = FakeTransport { 404 to """{"detail":"Not Found"}""" }
        try {
            client(t).history()
            fail("expected")
        } catch (e: ApiCallHistoryClient.PlaneUnavailable) {
            // expected
        }
        assertEquals(2, t.calls.size)
    }

    @Test
    fun deleteEntryToleratesAlreadyGone() {
        val t = FakeTransport { 404 to """{"detail":"Call log entry not found."}""" }
        client(t).deleteEntry("e1")  // must not throw
    }

    @Test
    fun clearAll() {
        val t = FakeTransport { call ->
            assertEquals("DELETE", call.method)
            assertTrue(call.url.endsWith("/calls/history/clear"))
            200 to """{"data":{"cleared":3}}"""
        }
        client(t).clear()
    }
}
