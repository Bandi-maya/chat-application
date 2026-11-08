package com.example.chat

import com.example.chat.data.api.ApiAuthClient
import com.example.chat.data.api.ApiHttpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Profile plane client (research 5.1): PUT /profile/me preference with
 *  legacy fallback, username availability, exact-match lookup. */
class ProfileClientTest {

    private data class Call(val method: String, val url: String, val headers: Map<String, String>, val body: String?)

    private class FakeTransport(var handler: (Call) -> Pair<Int, String>) : ApiHttpTransport {
        val calls = mutableListOf<Call>()
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            val call = Call(method, url, headers, body)
            calls.add(call)
            return handler(call)
        }
    }

    private fun client(t: FakeTransport) =
        ApiAuthClient({ "http://10.0.2.2:8000" }, { "chat-application" }, t, token = { "tok" })

    @Test
    fun updatePrefersProfileMeWithNewFieldNames() {
        val t = FakeTransport { call ->
            assertEquals("PUT", call.method)
            assertEquals("http://10.0.2.2:8000/profile/me", call.url)
            assertTrue(call.body!!.contains("\"display_name\":\"Maya\""))
            assertTrue(call.body.contains("\"about\":\"hi\""))
            assertTrue(call.body.contains("\"avatar\":\"https://x/a.jpg\""))
            assertTrue(call.body.contains("\"username\":\"maya.j\""))
            200 to """{"data":{"username":"maya.j"}}"""
        }
        val (ok, _) = client(t).updateProfile(
            displayName = "Maya", about = "hi", username = "maya.j", avatarUrl = "https://x/a.jpg",
        )
        assertTrue(ok)
        assertEquals(1, t.calls.size)
    }

    @Test
    fun updateFallsBackThroughApiV1ThenLegacyRoute() {
        val t = FakeTransport { call ->
            when {
                call.url.endsWith("/profile/me") -> 404 to ""
                call.url.endsWith("/api/v1/profile/me") -> 404 to ""
                call.url.endsWith("/app-users/profile") -> 200 to """{"data":{}}"""
                else -> 500 to ""
            }
        }
        val (ok, _) = client(t).updateProfile(displayName = "Maya", username = "maya.j")
        assertTrue(ok)
        assertEquals(3, t.calls.size)
        // Legacy payload keeps the old field names.
        assertTrue(t.calls[2].body!!.contains("\"full_name\":\"Maya\""))
    }

    @Test
    fun updateSurfaces409AsTaken() {
        val t = FakeTransport { 409 to """{"detail":"That username is taken."}""" }
        try {
            client(t).updateProfile(username = "taken.name")
            fail("expected failure")
        } catch (e: Exception) {
            assertEquals("That username is taken.", e.message)
        }
    }

    @Test
    fun availabilityParsesVerdictAndReason() {
        val t = FakeTransport { call ->
            assertTrue(call.url.startsWith("http://10.0.2.2:8000/profile/username-availability?u="))
            200 to """{"data":{"available":false,"valid":false,"reason":"Username must contain at least one letter."}}"""
        }
        val r = client(t).usernameAvailability("12345")
        assertFalse(r.valid)
        assertFalse(r.available)
        assertEquals("Username must contain at least one letter.", r.reason)
    }

    @Test
    fun availabilityOnOldServerIsPermissiveAndSaveDecides() {
        val t = FakeTransport { 404 to "" }
        val r = client(t).usernameAvailability("anything")
        assertTrue(r.valid)
        assertTrue(r.available)
        assertNull(r.reason)
    }

    @Test
    fun profileLookupReturnsPublicFields() {
        val t = FakeTransport { call ->
            assertEquals("http://10.0.2.2:8000/profile/bandi.maya", call.url)
            200 to """{"data":{"id":"u1","username":"bandi.maya","display_name":"Bandi","about":"hi","avatar_url":null}}"""
        }
        val p = client(t).profileByUsername("bandi.maya")
        assertEquals("u1", p["id"])
        assertEquals("Bandi", p["display_name"])
    }

    @Test
    fun profileLookup404SaysNoAccount() {
        val t = FakeTransport { 404 to """{"detail":"No account with that username."}""" }
        try {
            client(t).profileByUsername("ghost")
            fail("expected failure")
        } catch (e: Exception) {
            assertEquals("No account with that username.", e.message)
        }
    }
}
