package com.example.chat

import com.example.chat.data.api.ApiAccountClient
import com.example.chat.data.api.ApiBinaryTransport
import com.example.chat.data.api.ApiHttpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Behavior parity tests for the REST account plane
 *  (api_backend_service.dart updateOrganizationRegistrationSupport +
 *  uploadProfilePhoto). */
class ApiAccountClientParityTest {

    private data class Call(val method: String, val url: String, val headers: Map<String, String>, val body: String?)
    private data class BinaryCall(val method: String, val url: String, val headers: Map<String, String>, val body: ByteArray)

    private class FakeTransport(var handler: (Call) -> Pair<Int, String>) : ApiHttpTransport {
        val calls = mutableListOf<Call>()
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            val call = Call(method, url, headers, body)
            calls.add(call)
            return handler(call)
        }
    }

    private class FakeBinaryTransport(var handler: (BinaryCall) -> Pair<Int, String>) : ApiBinaryTransport {
        val calls = mutableListOf<BinaryCall>()
        override fun execute(method: String, url: String, headers: Map<String, String>, body: ByteArray): Pair<Int, String> {
            val call = BinaryCall(method, url, headers, body)
            calls.add(call)
            return handler(call)
        }
    }

    private fun client(t: FakeTransport, b: FakeBinaryTransport, tok: String? = "tok") =
        ApiAccountClient({ "http://10.0.2.2:8000" }, { "chat-application" }, { tok }, t, b)

    @Test
    fun registrationPatchSendsExactRequest() {
        val t = FakeTransport { call ->
            assertEquals("PATCH", call.method)
            assertEquals("http://10.0.2.2:8000/organization/acme/registration", call.url)
            assertEquals("Bearer tok", call.headers["Authorization"])
            assertEquals("chat-application", call.headers["x-tenant-id"])
            assertEquals("""{"allow_user_registration":false}""", call.body)
            200 to ""
        }
        assertTrue(client(t, FakeBinaryTransport { 500 to "" }).updateRegistrationSupport(" acme ", false))
    }

    @Test
    fun registrationPatchHasNoFallbackAndMapsNon200ToFalse() {
        val t = FakeTransport { 404 to """{"detail":"nope"}""" }
        assertFalse(client(t, FakeBinaryTransport { 500 to "" }).updateRegistrationSupport("acme", true))
        assertEquals(1, t.calls.size)
    }

    @Test
    fun uploadPostsMultipartWithExactHeadersAndFilename() {
        val b = FakeBinaryTransport { call ->
            assertEquals("POST", call.method)
            assertEquals("http://10.0.2.2:8000/app-users/upload-photo", call.url)
            assertEquals("Bearer tok", call.headers["Authorization"])
            assertEquals("chat-application", call.headers["x-tenant-id"])
            assertTrue(call.headers["Content-Type"]!!.startsWith("multipart/form-data; boundary="))
            val body = String(call.body, Charsets.UTF_8)
            assertTrue(body.contains("name=\"file\"; filename=\"avatar.jpg\""))
            assertTrue(body.contains("Content-Type: application/octet-stream"))
            200 to """{"data":{"avatarUrl":"https://cdn.example/u1.jpg"}}"""
        }
        val url = client(FakeTransport { 500 to "" }, b).uploadProfilePhoto("avatar.jpg", byteArrayOf(1, 2, 3))
        assertEquals("https://cdn.example/u1.jpg", url)
        assertEquals(1, b.calls.size)
    }

    @Test
    fun uploadFallsBackToApiV1On404() {
        val b = FakeBinaryTransport { call ->
            if (call.url.contains("/api/v1/")) 200 to """{"data":{"avatarUrl":"https://cdn.example/u2.jpg"}}"""
            else 404 to ""
        }
        val url = client(FakeTransport { 500 to "" }, b).uploadProfilePhoto("a.jpg", byteArrayOf(1))
        assertEquals("https://cdn.example/u2.jpg", url)
        assertEquals(2, b.calls.size)
        assertEquals("http://10.0.2.2:8000/api/v1/app-users/upload-photo", b.calls[1].url)
    }

    @Test
    fun uploadReadsBareUrlField() {
        val b = FakeBinaryTransport { 200 to """{"url":"https://cdn.example/u3.jpg"}""" }
        assertEquals("https://cdn.example/u3.jpg",
            client(FakeTransport { 500 to "" }, b).uploadProfilePhoto("a.jpg", byteArrayOf(1)))
    }

    @Test
    fun uploadMapsServerDetailToError() {
        val b = FakeBinaryTransport { 500 to """{"detail":"file too large"}""" }
        try {
            client(FakeTransport { 500 to "" }, b).uploadProfilePhoto("a.jpg", byteArrayOf(1))
            fail("expected exception")
        } catch (e: Exception) {
            assertEquals("file too large", e.message)
        }
    }

    @Test
    fun uploadGenericErrorOnNonJsonBody() {
        val b = FakeBinaryTransport { 502 to """<html>bad gateway</html>""" }
        try {
            client(FakeTransport { 500 to "" }, b).uploadProfilePhoto("a.jpg", byteArrayOf(1))
            fail("expected exception")
        } catch (e: Exception) {
            assertEquals("Failed to upload profile photo (502)", e.message)
        }
    }

    @Test
    fun uploadRequiresToken() {
        val b = FakeBinaryTransport { 200 to "{}" }
        try {
            client(FakeTransport { 500 to "" }, b, tok = null).uploadProfilePhoto("a.jpg", byteArrayOf(1))
            fail("expected exception")
        } catch (e: Exception) {
            assertEquals("Not authenticated", e.message)
        }
        assertEquals(0, b.calls.size)
    }

    @Test
    fun uploadWrapsTransportFailureWithoutFallback() {
        val b = FakeBinaryTransport { throw RuntimeException("connection refused") }
        try {
            client(FakeTransport { 500 to "" }, b).uploadProfilePhoto("a.jpg", byteArrayOf(1))
            fail("expected exception")
        } catch (e: Exception) {
            assertTrue(e.message!!.startsWith("Unable to reach server to upload photo:"))
        }
        assertEquals(1, b.calls.size)
    }
}
