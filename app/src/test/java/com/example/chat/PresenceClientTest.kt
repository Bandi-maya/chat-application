package com.example.chat

import com.example.chat.data.api.ApiHttpTransport
import com.example.chat.data.api.ApiPresenceClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Presence plane client (research 5.2): three privacy-filtered shapes,
 *  fail-closed Unavailable, privacy settings round-trip. */
class PresenceClientTest {

    private class FakeTransport(var handler: (String, String) -> Pair<Int, String>) : ApiHttpTransport {
        val calls = mutableListOf<Pair<String, String>>()
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            calls.add(method to url)
            return handler(method, url)
        }
    }

    private fun client(t: FakeTransport) =
        ApiPresenceClient({ "http://10.0.2.2:8000" }, { "chat-application" }, t, token = { "tok" })

    @Test
    fun onlineShape() {
        val t = FakeTransport { _, _ -> 200 to """{"data":{"user_id":"u1","online":true}}""" }
        assertEquals(ApiPresenceClient.PresenceView.Online, client(t).presence("u1"))
    }

    @Test
    fun lastSeenShape() {
        val t = FakeTransport { _, _ -> 200 to """{"data":{"user_id":"u1","last_seen_at":"2026-11-08T07:00:00"}}""" }
        val view = client(t).presence("u1")
        assertTrue(view is ApiPresenceClient.PresenceView.LastSeen)
        assertEquals("2026-11-08T07:00:00", (view as ApiPresenceClient.PresenceView.LastSeen).at)
    }

    @Test
    fun approxShape() {
        val t = FakeTransport { _, _ -> 200 to """{"data":{"user_id":"u1","approx":"within_week"}}""" }
        assertEquals(ApiPresenceClient.PresenceView.Approx("within_week"), client(t).presence("u1"))
    }

    @Test
    fun unknownUserOrOldServerIsUnavailableFailClosed() {
        val t = FakeTransport { _, url ->
            if (url.contains("/api/v1/")) 404 to """{"detail":"Not Found"}""" else 404 to """{"detail":"User not found."}"""
        }
        assertEquals(ApiPresenceClient.PresenceView.Unavailable, client(t).presence("ghost"))
    }

    @Test
    fun heartbeatPostsOnlineTrue() {
        var seenBody: String? = null
        val c = ApiPresenceClient({ "http://10.0.2.2:8000" }, { "chat-application" }, object : ApiHttpTransport {
            override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
                seenBody = body
                return 200 to """{"data":{"online":true}}"""
            }
        }, token = { "tok" })
        assertTrue(c.heartbeat(true))
        assertTrue(seenBody!!.contains("\"online\":true"))
    }

    @Test
    fun heartbeatFailClosedOnError() {
        val t500 = FakeTransport { _, _ -> 500 to "oops" }
        assertTrue(!client(t500).heartbeat(true))
        val throwing = FakeTransport { _, _ -> throw Exception("no net") }
        assertTrue(!client(throwing).heartbeat(false))
    }

    @Test
    fun apiV1FallbackUsed() {
        val t = FakeTransport { _, url ->
            if (url.contains("/api/v1/presence/")) 200 to """{"data":{"user_id":"u1","online":true}}"""
            else 404 to """{"detail":"Not Found"}"""
        }
        assertEquals(ApiPresenceClient.PresenceView.Online, client(t).presence("u1"))
        assertTrue(t.calls[1].second.contains("/api/v1/"))
    }

    @Test
    fun privacyRoundTrip() {
        var stored = """{"last_seen_policy":"contacts","online_policy":"same_as_last_seen","last_seen_exceptions":[]}"""
        val t = FakeTransport { method, _ ->
            if (method == "PUT") {
                stored = """{"last_seen_policy":"nobody","online_policy":"same_as_last_seen","last_seen_exceptions":[]}"""
                200 to """{"data":$stored}"""
            } else 200 to """{"data":$stored}"""
        }
        val c = client(t)
        assertEquals("contacts", c.privacy()!!.lastSeenPolicy)
        assertEquals("nobody", c.putPrivacy(lastSeenPolicy = "nobody")!!.lastSeenPolicy)
    }
}
