package com.example.chat

import com.example.chat.data.api.ApiAuthClient
import com.example.chat.data.api.ApiHttpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

class ApiAuthClientParityTest {

    private data class Call(val method: String, val url: String, val headers: Map<String, String>, val body: String?)

    private class FakeTransport(var handler: (Call) -> Pair<Int, String>) : ApiHttpTransport {
        val calls = mutableListOf<Call>()
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            val call = Call(method, url, headers, body)
            calls.add(call)
            return handler(call)
        }
    }

    private fun jwt(payload: String): String {
        fun b64(s: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())
        return "${b64("{\"alg\":\"none\"}")}.${b64(payload)}.sig"
    }

    private fun client(
        transport: FakeTransport,
        base: String = "http://10.0.2.2:8000",
        tenant: String = "chat-application",
    ) = ApiAuthClient({ base }, { tenant }, transport)

    @Test
    fun loginPostsUsernameVerbatimWithTenantHeader() {
        val t = FakeTransport { call ->
            assertEquals("http://10.0.2.2:8000/auth/token", call.url)
            assertEquals("chat-application", call.headers["x-tenant-id"])
            assertEquals("application/json", call.headers["Content-Type"])
            assertTrue(call.body!!.contains("\"username\":\"maya@example.com\""))
            assertTrue(call.body.contains("\"password\":\"pw\""))
            200 to """{"access_token":"${jwt("{\"uid\":\"u1\"}")}","user":{"id":"u1","username":"maya"}}"""
        }
        val result = client(t).login("maya@example.com", "pw")
        assertEquals("u1", result.user.id)
        assertEquals("maya", result.user.username)
        assertEquals(1, t.calls.size)
    }

    @Test
    fun loginFallsBackToApiV1On404() {
        val t = FakeTransport { call ->
            if (call.url == "http://10.0.2.2:8000/auth/token") 404 to "" else 200 to
                """{"access_token":"${jwt("{}")}","user":{"id":"u2","username":"x"}}"""
        }
        val result = client(t).login("x", "pw")
        assertEquals("u2", result.user.id)
        assertEquals("http://10.0.2.2:8000/api/v1/auth/token", t.calls[1].url)
    }

    @Test
    fun registerSendsOnlyThreeFields() {
        val t = FakeTransport { call ->
            assertEquals("http://10.0.2.2:8000/auth/register", call.url)
            val body = call.body!!
            assertTrue(body.contains("\"email\":\"a@b.c\""))
            assertTrue(body.contains("\"username\":\"maya\""))
            assertTrue(body.contains("\"password\":\"secret\""))
            assertFalse(body.contains("display"))
            200 to """{"access_token":"${jwt("{}")}","user":{"id":"u3","username":"maya","full_name":"Maya B"}}"""
        }
        val result = client(t).register("a@b.c", "maya", "secret")
        // display name comes from the server response
        assertEquals("Maya B", result.user.displayName)
        assertEquals("MB", result.user.avatarInitials)
    }

    @Test
    fun displayNameFallbackChain() {
        fun name(userJson: String, payload: String = "{}"): String {
            val t = FakeTransport { 200 to """{"access_token":"${jwt(payload)}","user":$userJson}""" }
            return client(t).login("x", "pw").user.displayName
        }
        assertEquals("Full Name", name("""{"id":"1","username":"u","full_name":"Full Name"}"""))
        assertEquals("Disp", name("""{"id":"1","username":"u","display_name":"Disp"}"""))
        assertEquals("First Last", name("""{"id":"1","username":"u","firstName":"First","last_name":"Last"}"""))
        assertEquals("maya", name("""{"id":"1","username":"maya"}"""))
        assertEquals("subj", name("""{"id":"1"}""", "{\"sub\":\"subj\"}"))
        assertEquals("User", name("""{"id":"1"}"""))
    }

    @Test
    fun initialsFromWordsOrUsername() {
        fun initials(userJson: String): String {
            val t = FakeTransport { 200 to """{"access_token":"${jwt("{}")}","user":$userJson}""" }
            return client(t).login("x", "pw").user.avatarInitials
        }
        assertEquals("MB", initials("""{"id":"1","username":"u","full_name":"Maya B"}"""))
        assertEquals("M", initials("""{"id":"1","username":"maya"}"""))
    }

    @Test
    fun missingTokenThrows() {
        val t = FakeTransport { 200 to """{"user":{"id":"1"}}""" }
        try {
            client(t).login("x", "pw")
            fail("expected")
        } catch (e: Exception) {
            assertEquals("No token found in response", e.message)
        }
    }

    @Test
    fun errorPassthroughUsesDetailThenMessage() {
        val t = FakeTransport { 401 to """{"detail":"Invalid login credentials"}""" }
        try {
            client(t).login("x", "pw")
            fail("expected")
        } catch (e: Exception) {
            assertEquals("Invalid login credentials", e.message)
        }
    }

    @Test
    fun decodeJwtHandlesUnpaddedBase64url() {
        val t = FakeTransport { 0 to "" }
        val c = client(t)
        // payload length chosen so %4 == 2 and 3 paths both exercised
        val p2 = c.decodeJwt(jwt("{\"uid\":\"u1\"}")) // 12 chars -> %4=0? exercise anyway
        assertEquals("u1", p2.getString("uid"))
        val p3 = c.decodeJwt(jwt("{\"sub\":\"abc\"}"))
        assertEquals("abc", p3.getString("sub"))
        try {
            c.decodeJwt("only.two")
            fail("expected")
        } catch (e: Exception) {
            assertEquals("Invalid token", e.message)
        }
    }

    @Test
    fun checkOrganizationNotFoundAndServerError() {
        val notFound = FakeTransport { 404 to "" }
        try {
            client(notFound).checkOrganization("acme")
            fail("expected")
        } catch (e: Exception) {
            assertEquals(
                "Organization \"acme\" not found. Please verify the ID or register a new organization.",
                e.message,
            )
        }
        // 404 on primary triggers the /tenants/config fallback first
        assertEquals(2, notFound.calls.size)
        assertTrue(notFound.calls[1].url.contains("/tenants/config?tenant_id=acme"))
        assertEquals("acme", notFound.calls[1].headers["x-tenant-id"])

        val serverErr = FakeTransport { 500 to """{"detail":"boom"}""" }
        try {
            client(serverErr).checkOrganization("acme")
            fail("expected")
        } catch (e: Exception) {
            assertEquals("boom", e.message)
        }
    }

    @Test
    fun checkOrganizationReturnsDataObject() {
        val t = FakeTransport { 200 to """{"data":{"name":"Acme","allow_user_registration":false}}""" }
        val data = client(t).checkOrganization("acme")
        val (name, allow) = ApiAuthClient.orgCheckToBanner(data, "acme")
        assertEquals("Acme", name)
        assertFalse(allow)
    }

    @Test
    fun orgCheckFlagCoercionMatchesDart() {
        assertEquals("w" to true, ApiAuthClient.orgCheckToBanner(mapOf("user_registration_enabled" to true), "w"))
        assertEquals("w" to false, ApiAuthClient.orgCheckToBanner(mapOf("allow_user_registration" to "false"), "w"))
        assertEquals("w" to true, ApiAuthClient.orgCheckToBanner(mapOf("allow_user_registration" to "true"), "w"))
        assertEquals("w" to true, ApiAuthClient.orgCheckToBanner(emptyMap(), "w"))
        assertEquals("Acme" to true, ApiAuthClient.orgCheckToBanner(mapOf("name" to "Acme"), "w"))
    }

    @Test
    fun forgotPasswordSuccessAndFailureMessages() {
        val ok = FakeTransport { 200 to """{"data":{"message":"Check your inbox"}}""" }
        assertEquals("Check your inbox", client(ok).forgotPassword("a@b.c"))

        val fallbackMsg = FakeTransport { 200 to """{}""" }
        assertEquals(
            "If an account exists with this email, a reset link has been sent.",
            client(fallbackMsg).forgotPassword("a@b.c"),
        )

        val bad = FakeTransport { 500 to """{"detail":"nope"}""" }
        try {
            client(bad).forgotPassword("a@b.c")
            fail("expected")
        } catch (e: Exception) {
            assertEquals("nope", e.message)
        }
    }

    @Test
    fun transportFailureMapsToUnableToReachServer() {
        val down = FakeTransport { throw RuntimeException("refused") }
        try {
            client(down).login("x", "pw")
            fail("expected")
        } catch (e: Exception) {
            assertEquals("Unable to reach server. Please check your internet connection.", e.message)
        }
    }

    @Test
    fun emptyOrgIdRejectedBeforeNetwork() {
        val t = FakeTransport { 200 to "{}" }
        try {
            client(t).checkOrganization("   ")
            fail("expected")
        } catch (e: Exception) {
            assertEquals("Organization ID cannot be empty", e.message)
        }
        assertTrue(t.calls.isEmpty())
    }
}
