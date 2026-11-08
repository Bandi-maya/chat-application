package com.example.chat

import com.example.chat.data.api.ApiContactsClient
import com.example.chat.data.api.ApiHttpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Contacts plane client (research 5.3): requests, inbox, blocking,
 *  Who Can Call Me. */
class ContactsClientTest {

    private data class Call(val method: String, val url: String, val body: String?)

    private class FakeTransport(var handler: (Call) -> Pair<Int, String>) : ApiHttpTransport {
        val calls = mutableListOf<Call>()
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            val call = Call(method, url, body)
            calls.add(call)
            return handler(call)
        }
    }

    private fun client(t: FakeTransport) =
        ApiContactsClient({ "http://10.0.2.2:8000" }, { "chat-application" }, t, token = { "tok" })

    private val routeMiss = 404 to """{"detail":"Not Found"}"""

    @Test
    fun sendRequestReturnsPending() {
        val t = FakeTransport { call ->
            assertEquals("POST", call.method)
            assertEquals("http://10.0.2.2:8000/contacts/request", call.url)
            assertTrue(call.body!!.contains("\"username\":\"maya.j\""))
            200 to """{"data":{"status":"pending"}}"""
        }
        assertEquals("pending", client(t).sendRequest("maya.j"))
    }

    @Test
    fun sendRequestMutualAutoAccepts() {
        val t = FakeTransport { 200 to """{"data":{"status":"accepted"}}""" }
        assertEquals("accepted", client(t).sendRequest("maya.j"))
    }

    @Test
    fun sendRequestUnknownUserSurfacesFriendly404() {
        val t = FakeTransport { 404 to """{"detail":"No account with that username."}""" }
        try {
            client(t).sendRequest("ghost")
            fail("expected")
        } catch (e: Exception) {
            assertEquals("No account with that username.", e.message)
        }
    }

    @Test
    fun planeMissingOnBothRootsThrowsPlaneUnavailable() {
        val t = FakeTransport { routeMiss }
        try {
            client(t).contacts()
            fail("expected")
        } catch (e: ApiContactsClient.PlaneUnavailable) {
            // expected
        }
        assertEquals(2, t.calls.size)  // root + /api/v1 fallback
        assertTrue(t.calls[1].url.contains("/api/v1/contacts"))
    }

    @Test
    fun apiV1FallbackServesWhenRootMisses() {
        val t = FakeTransport { call ->
            if (call.url.contains("/api/v1/")) 200 to """{"data":[]}}""" else routeMiss
        }
        assertTrue(client(t).contacts().isEmpty())
        assertEquals(2, t.calls.size)
    }

    @Test
    fun inboxParsesRequesters() {
        val t = FakeTransport {
            200 to """{"data":[{"request_id":"r1","from":{"id":"u1","username":"maya.j","display_name":"Maya","about":"","avatar_url":null}}]}"""
        }
        val inbox = client(t).inbox()
        assertEquals(1, inbox.size)
        assertEquals("r1", inbox[0].requestId)
        assertEquals("maya.j", inbox[0].from.username)
        assertEquals("Maya", inbox[0].from.displayName)
    }

    @Test
    fun respondSendsAction() {
        val t = FakeTransport { call ->
            assertTrue(call.body!!.contains("\"action\":\"accept\""))
            assertTrue(call.body.contains("\"request_id\":\"r1\""))
            200 to """{"data":{"status":"accepted"}}"""
        }
        assertEquals("accepted", client(t).respond("r1", "accept"))
    }

    @Test
    fun blocksRoundTrip() {
        var blocked = false
        val t = FakeTransport { call ->
            when {
                call.method == "POST" && call.url.endsWith("/block") -> {
                    assertTrue(call.body!!.contains("\"username\":\"maya.j\""))
                    blocked = true
                    200 to """{"data":{"blocked":true}}"""
                }
                call.method == "GET" && call.url.endsWith("/blocks") ->
                    200 to if (blocked) """{"data":[{"id":"u1","username":"maya.j","display_name":"Maya","blocked_at":"2026-11-08T07:00:00"}]}""" else """{"data":[]}"""
                call.method == "DELETE" && call.url.endsWith("/block/u1") -> {
                    blocked = false
                    200 to """{"data":{"blocked":false}}"""
                }
                else -> 500 to ""
            }
        }
        val c = client(t)
        c.block("maya.j")
        assertEquals(1, c.blocks().size)
        assertEquals("u1", c.blocks()[0].userId)
        c.unblock("u1")
        assertTrue(c.blocks().isEmpty())
    }

    @Test
    fun callPolicyGetAndPut() {
        var policy = "everyone"
        val t = FakeTransport { call ->
            when {
                call.method == "GET" -> 200 to """{"data":{"policy":"$policy","exceptions":[]}}"""
                call.method == "PUT" -> {
                    assertTrue(call.body!!.contains("\"policy\":\"contacts\""))
                    policy = "contacts"
                    200 to """{"data":{"policy":"contacts","exceptions":[]}}"""
                }
                else -> 500 to ""
            }
        }
        val c = client(t)
        assertEquals("everyone", c.callPolicy().policy)
        assertEquals("contacts", c.putCallPolicy("contacts").policy)
        assertEquals("contacts", c.callPolicy().policy)
    }
}
