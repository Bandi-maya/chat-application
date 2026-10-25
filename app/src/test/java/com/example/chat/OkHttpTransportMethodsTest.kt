package com.example.chat

import com.example.chat.data.api.ApiAccountClient
import com.example.chat.data.api.OkHttpApiBinaryTransport
import com.example.chat.data.api.OkHttpApiTransport
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Local-server fixtures only: no live Chaty backend, account, profile, delete or subscription is touched. */
class OkHttpTransportMethodsTest {
    private class Seen(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray)

    private lateinit var server: MockWebServer
    private val seen = CopyOnWriteArrayList<Seen>()
    @Volatile private var status = 200
    @Volatile private var responseBody = "{}"
    private val base get() = server.url("/").toString().trimEnd('/')

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val hdrs = request.headers.toMultimap().entries.associate { it.key.lowercase() to it.value.joinToString(",") }
                seen.add(Seen(request.method ?: "", request.path ?: "", hdrs, request.body.readByteArray()))
                return MockResponse().setResponseCode(status).setBody(responseBody)
            }
        }
        server.start()
    }

    @After
    fun stop() { server.shutdown() }

    @Test
    fun putSendsBodyAndHeaders() {
        val r = OkHttpApiTransport().execute(
            "PUT", "$base/app-users/profile",
            mapOf("Authorization" to "Bearer t", "x-tenant-id" to "ten", "Content-Type" to "application/json"),
            """{"a":1}""",
        )
        val s = seen.single()
        assertEquals(200, r.first)
        assertEquals("PUT", s.method)
        assertEquals("/app-users/profile", s.path)
        assertEquals("Bearer t", s.headers["authorization"])
        assertEquals("ten", s.headers["x-tenant-id"])
        assertTrue(s.headers["content-type"]!!.startsWith("application/json"))
        assertEquals("""{"a":1}""", String(s.body))
    }

    @Test
    fun patchSendsBody() {
        OkHttpApiTransport().execute("PATCH", "$base/o", mapOf("x-tenant-id" to "ten"), """{"b":true}""")
        val s = seen.single()
        assertEquals("PATCH", s.method)
        assertEquals("""{"b":true}""", String(s.body))
    }

    @Test
    fun deleteWithNullBodyAndErrorStatusIsReturned() {
        status = 404
        responseBody = """{"detail":"gone"}"""
        val r = OkHttpApiTransport().execute("DELETE", "$base/chat/rooms/r/messages/m", mapOf("Authorization" to "Bearer t"), null)
        val s = seen.single()
        assertEquals("DELETE", s.method)
        assertEquals(0, s.body.size)
        assertEquals(404, r.first)
        assertEquals("""{"detail":"gone"}""", r.second)
    }

    @Test
    fun unsupportedMethodStillThrows() {
        try {
            OkHttpApiTransport().execute("TRACE", "$base/x", emptyMap(), null)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        assertTrue(seen.isEmpty())
    }

    @Test
    fun binaryTransportKeepsMultipartContentTypeAndBytes() {
        val payload = byteArrayOf(0, 1, 2, 127, -1)
        val r = OkHttpApiBinaryTransport().execute(
            "POST", "$base/app-users/upload-photo",
            mapOf("Authorization" to "Bearer t", "Content-Type" to "multipart/form-data; boundary=XYZ"),
            payload,
        )
        val s = seen.single()
        assertEquals(200, r.first)
        assertEquals("POST", s.method)
        assertEquals("multipart/form-data; boundary=XYZ", s.headers["content-type"])
        assertEquals("Bearer t", s.headers["authorization"])
        assertTrue(s.body.contentEquals(payload))
    }

    private fun account(token: String? = "tok") = ApiAccountClient(
        baseUrl = { base }, tenantId = { "ten" }, token = { token },
        transport = OkHttpApiTransport(), binaryTransport = OkHttpApiBinaryTransport(),
    )

    @Test
    fun registrationSupportPatchesOrganizationPath() {
        assertTrue(account().updateRegistrationSupport(" org1 ", false))
        val s = seen.single()
        assertEquals("PATCH", s.method)
        assertEquals("/organization/org1/registration", s.path)
        assertEquals("ten", s.headers["x-tenant-id"])
        assertEquals("Bearer tok", s.headers["authorization"])
        assertEquals("""{"allow_user_registration":false}""", String(s.body))
        status = 500
        assertEquals(false, account().updateRegistrationSupport("org1", true))
    }

    @Test
    fun uploadPhotoMultipartFieldFileAndParsesUrl() {
        responseBody = """{"data":{"avatarUrl":"https://x/a.png"}}"""
        val url = account().uploadProfilePhoto("me.png", byteArrayOf(9, 8, 7))
        val s = seen.single()
        assertEquals("https://x/a.png", url)
        assertEquals("/app-users/upload-photo", s.path)
        assertTrue(s.headers["content-type"]!!.startsWith("multipart/form-data; boundary="))
        val text = String(s.body, Charsets.ISO_8859_1)
        assertTrue(text.contains("""name="file"; filename="me.png""""))
        assertTrue(text.contains("application/octet-stream"))
    }

    @Test
    fun uploadPhotoErrorUsesServerDetailAfterFallback() {
        status = 404
        responseBody = """{"detail":"nope"}"""
        try {
            account().uploadProfilePhoto("me.png", byteArrayOf(1))
            fail("expected exception")
        } catch (e: Exception) {
            assertEquals("nope", e.message)
        }
        assertEquals(listOf("/app-users/upload-photo", "/api/v1/app-users/upload-photo"), seen.map { it.path })
    }

    @Test
    fun uploadPhotoWithoutTokenThrowsBeforeNetwork() {
        try {
            account(token = null).uploadProfilePhoto("me.png", byteArrayOf(1))
            fail("expected exception")
        } catch (e: Exception) {
            assertEquals("Not authenticated", e.message)
        }
        assertTrue(seen.isEmpty())
    }
}
