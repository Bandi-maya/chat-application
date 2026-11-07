package com.example.chat

import com.example.chat.data.api.ApiGroupsClient
import com.example.chat.data.api.ApiHttpTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiGroupsClientTest {

    private class RecordingTransport(private val body: String) : ApiHttpTransport {
        val calls = mutableListOf<Triple<String, String, String?>>()
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            calls += Triple(method, url, body)
            return 200 to this.body
        }
    }

    private fun client(t: ApiHttpTransport) = ApiGroupsClient({ "https://api.example.com" }, { "t1" }, { "tok" }, t)

    @Test fun `add member posts to members route`() = runTest {
        val t = RecordingTransport("""{"success":true,"data":{"role":"member"},"message":"ok"}""")
        client(t).addMember("r1", "u2")
        val (m, url, body) = t.calls.single()
        assertEquals("POST", m)
        assertEquals("https://api.example.com/chat/rooms/r1/members", url)
        assertEquals("""{"user_id":"u2"}""", body)
    }

    @Test fun `set role sends patch body`() = runTest {
        val t = RecordingTransport("""{"success":true,"data":{},"message":"ok"}""")
        client(t).setRole("r1", "u2", "admin")
        val (m, url, body) = t.calls.single()
        assertEquals("PATCH", m)
        assertEquals("https://api.example.com/chat/rooms/r1/members/u2/role", url)
        assertEquals("""{"role":"admin"}""", body)
    }

    @Test fun `disappear timer null clears`() = runTest {
        val t = RecordingTransport("""{"success":true,"data":{},"message":"ok"}""")
        client(t).setDisappearTimer("r1", null)
        assertEquals("""{"seconds":null}""", t.calls.single().third)
    }

    @Test fun `404 falls back to api-v1 mount`() = runTest {
        val t = object : ApiHttpTransport {
            val urls = mutableListOf<String>()
            override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
                urls += url
                return if (urls.size == 1) 404 to "{}" else 200 to """{"success":true,"data":{},"message":"ok"}"""
            }
        }
        client(t).leave("r1")
        assertEquals(listOf(
            "https://api.example.com/chat/rooms/r1/leave",
            "https://api.example.com/api/v1/chat/rooms/r1/leave",
        ), t.urls)
    }

    @Test fun `error surfaces server detail`() = runTest {
        val t = RecordingTransport("""{"success":false,"detail":"Owner only"}""")
        val failing = object : ApiHttpTransport {
            override fun execute(method: String, url: String, headers: Map<String, String>, body: String?) =
                403 to """{"detail":"Owner only"}"""
        }
        val e = assertThrows(java.io.IOException::class.java) {
            kotlinx.coroutines.runBlocking { client(failing).setRole("r1", "u2", "admin") }
        }
        assertEquals("Owner only", e.message)
    }
}
