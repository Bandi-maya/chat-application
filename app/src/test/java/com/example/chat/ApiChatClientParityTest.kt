package com.example.chat

import com.example.chat.data.api.ApiChatClient
import com.example.chat.data.api.ApiHttpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior parity tests for the REST chat plane (api_backend_service.dart). */
class ApiChatClientParityTest {

    private data class Call(val method: String, val url: String, val headers: Map<String, String>, val body: String?)

    private class FakeTransport(var handler: (Call) -> Pair<Int, String>) : ApiHttpTransport {
        val calls = mutableListOf<Call>()
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            val call = Call(method, url, headers, body)
            calls.add(call)
            return handler(call)
        }
    }

    private fun client(t: FakeTransport, tok: String? = "tok") =
        ApiChatClient({ "http://10.0.2.2:8000" }, { "chat-application" }, { tok }, t)

    @Test
    fun listRoomsSendsAuthAndTenantHeaders() {
        val t = FakeTransport { call ->
            assertEquals("http://10.0.2.2:8000/chat/rooms?include_tasks=false", call.url)
            assertEquals("Bearer tok", call.headers["Authorization"])
            assertEquals("chat-application", call.headers["x-tenant-id"])
            200 to """{"data":[]}"""
        }
        val result = client(t).listRooms()
        assertTrue(result is ApiChatClient.RoomsResult.Ok)
    }

    @Test
    fun listRoomsParsesConversationFields() {
        val t = FakeTransport {
            200 to """{"data":[{"uid":"r1","room_type":"direct","display_name":"Alice","created_by_id":"me","other_user":{"id":"u2"},"last_message":{"content":"hello","created_at":"2026-10-25T10:00:00Z","sender_name":"Alice"},"updated_at":"2026-10-25T10:00:00Z"}]}"""
        }
        val rooms = (client(t).listRooms() as ApiChatClient.RoomsResult.Ok).conversations
        assertEquals(1, rooms.size)
        val c = rooms[0]
        assertEquals("r1", c.id)
        assertEquals("Alice", c.title)
        assertEquals("AL", c.avatarInitials)
        assertEquals("hello", c.lastMessageText)
        assertEquals("Alice", c.lastMessageSenderId)
        assertEquals(listOf("me", "u2"), c.participantIds)
    }

    @Test
    fun listRoomsFallsBackToApiV1On404() {
        val t = FakeTransport { call ->
            if (call.url.contains("/api/v1/")) 200 to """{"data":[]}""" else 404 to ""
        }
        val result = client(t).listRooms()
        assertTrue(result is ApiChatClient.RoomsResult.Ok)
        assertEquals("http://10.0.2.2:8000/api/v1/chat/rooms?include_tasks=false", t.calls[1].url)
    }

    @Test
    fun listRooms402MapsToSubscriptionRequired() {
        val t = FakeTransport { 402 to "" }
        assertEquals(ApiChatClient.RoomsResult.SubscriptionRequired, client(t).listRooms())
    }

    @Test
    fun listRoomsNetworkErrorKeepsPriorState() {
        val t = FakeTransport { throw java.io.IOException("down") }
        assertEquals(ApiChatClient.RoomsResult.Failed, client(t).listRooms())
    }

    @Test
    fun listMessagesParsesAndNullOnFailure() {
        val t = FakeTransport { call ->
            if (call.url.endsWith("/chat/rooms/r1/messages")) {
                200 to """{"data":[{"uid":"m1","room_id":"r1","sender_id":"u2","content":"hi","created_at":"2026-10-25T10:00:00Z","sender_name":"Alice"}]}"""
            } else 500 to ""
        }
        val messages = client(t).listMessages("r1")!!
        assertEquals(1, messages.size)
        assertEquals("m1", messages[0].id)
        assertEquals("hi", messages[0].text)
        assertEquals("r1", messages[0].conversationId)
    }

    @Test
    fun listMessagesNullWhenNoToken() {
        val t = FakeTransport { 200 to """{"data":[]}""" }
        assertNull(client(t, tok = null).listMessages("r1"))
    }

    @Test
    fun sendMessagePostsContentAndConfirms() {
        val t = FakeTransport { call ->
            assertEquals("POST", call.method)
            assertTrue(call.body!!.contains("\"content\":\"hello there\""))
            assertEquals("application/json", call.headers["Content-Type"])
            200 to """{"data":{"uid":"m9","room_id":"r1","sender_id":"me","content":"hello there","created_at":"2026-10-25T10:01:00Z"}}"""
        }
        val confirmed = client(t).sendMessage("r1", "hello there")
        assertEquals("m9", confirmed!!.id)
    }

    @Test
    fun sendMessageNullOnHttpFailure() {
        val t = FakeTransport { 500 to "oops" }
        assertNull(client(t).sendMessage("r1", "hi"))
    }

    @Test
    fun listUsersFallsThroughChatUsersToAppUsersOn404() {
        val t = FakeTransport { call ->
            when {
                call.url.contains("/chat/users") -> 404 to ""
                call.url.contains("/api/v1/app-users") -> 200 to
                    """{"data":[{"uid":"u1","full_name":"Alice Rao","username":"alice","email":"a@x.com"}]}"""
                else -> 404 to ""
            }
        }
        val users = client(t).listUsers(null)!!
        assertEquals(4, t.calls.size) // chat/users -> api/v1/chat/users -> app-users -> api/v1/app-users
        assertEquals("u1", users[0].id)
        assertEquals("Alice Rao", users[0].displayName)
        assertEquals("AR", users[0].avatarInitials)
    }

    @Test
    fun listUsersSearchQueryEncoded() {
        val t = FakeTransport { call ->
            assertTrue(call.url.contains("?search=alice+rao"))
            200 to """{"data":[]}"""
        }
        client(t).listUsers("alice rao")
    }

    @Test
    fun userFromApiFallsBackToUsernameAndFirstLast() {
        val byUsername = ApiChatClient.userFromApi(mapOf("id" to "u2", "username" to "bob"))
        assertEquals("bob", byUsername.displayName)
        assertEquals("B", byUsername.avatarInitials)
        val byParts = ApiChatClient.userFromApi(mapOf("id" to "u3", "first_name" to "Cora", "last_name" to "Ray"))
        assertEquals("Cora Ray", byParts.displayName)
        val empty = ApiChatClient.userFromApi(mapOf("id" to "u4"))
        assertEquals("User", empty.displayName)
    }
}
