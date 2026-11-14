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
    fun rejectsUnsafePathIdentifiersBeforeCallingTransport() {
        val t = FakeTransport { 200 to """{"data":[]}""" }
        assertNull(client(t).listMessages("../admin"))
        assertNull(client(t).sendMessage("room?next=/admin", "hello"))
        assertEquals(
            ApiChatClient.SendResult.Rejected(400, "Invalid conversation identifier."),
            client(t).sendMessageResult("room#fragment", "hello", null),
        )
        client(t).editMessage("room/other", "message", "edited")
        client(t).deleteMessage("room", "../message")
        client(t).deleteUser("..")
        assertTrue("unsafe path IDs must be rejected before network I/O", t.calls.isEmpty())
    }

    @Test
    fun getChatSettingsExtrasParsesBlobAndUpdatedAt() {
        val t = FakeTransport { call ->
            assertEquals("GET", call.method)
            assertEquals("http://10.0.2.2:8000/chat/settings", call.url)
            assertEquals("Bearer tok", call.headers["Authorization"])
            200 to "{\"data\":{\"extras\":{\"theme\":{\"mode\":\"dark\"},\"font\":{\"scale\":1.25}},\"updated_at\":\"2026-11-08T15:30:00\",\"who_can_see_last_seen\":\"contacts\"}}"
        }
        val (extras, updatedAt) = client(t).getChatSettingsExtras()!!
        assertEquals("dark", ((extras!!["theme"] as Map<*, *>)["mode"]))
        assertEquals(1.25, ((extras["font"] as Map<*, *>)["scale"] as Number).toDouble(), 0.0001)
        assertEquals("2026-11-08T15:30:00", updatedAt)
    }

    @Test
    fun getChatSettingsExtrasHandlesMissingBlobAndFailures() {
        // Row exists but no blob pushed yet.
        var t = FakeTransport { 200 to "{\"data\":{\"extras\":null,\"updated_at\":null}}" }
        val (extras, updatedAt) = client(t).getChatSettingsExtras()!!
        assertNull(extras)
        assertNull(updatedAt)
        // Transport failure -> null (caller treats local as authoritative).
        t = FakeTransport { 500 to "boom" }
        assertNull(client(t).getChatSettingsExtras())
        // Signed out -> no network call.
        t = FakeTransport { throw AssertionError("must not call transport") }
        assertNull(client(t, tok = null).getChatSettingsExtras())
    }

    @Test
    fun updateChatSettingsCarriesExtrasBlob() {
        val t = FakeTransport { call ->
            assertEquals("PATCH", call.method)
            val body = org.json.JSONObject(call.body)
            val extras = body.getJSONObject("extras")
            assertEquals("light", extras.getJSONObject("theme").getString("mode"))
            200 to "{\"data\":{}}"
        }
        assertTrue(client(t).updateChatSettings(mapOf("extras" to mapOf("theme" to mapOf("mode" to "light")))))
    }

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
            assertTrue(call.url.contains("?q=alice+rao"))
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
    @Test fun userMediaSurvivesSnakePublicProfile() {
        val user = ApiChatClient.userFromApi(mapOf("id" to "peer", "display_name" to "Peer Name",
            "public_profile" to mapOf("avatarUrl" to "https://example.invalid/a.jpg", "banner_url" to "https://example.invalid/b.jpg")))
        assertEquals("Peer Name", user.displayName)
        assertEquals("https://example.invalid/a.jpg", user.avatarUrl)
        assertEquals("https://example.invalid/b.jpg", user.bannerUrl)
    }
    @Test fun queuedRetryAndRouteFallbackKeepSameClientIdentityAndPayload() {
        val t = FakeTransport { call -> if (call.url.contains("/api/v1/")) 503 to "{}" else 404 to "{}" }
        val c = client(t)
        repeat(2) { assertEquals(ApiChatClient.SendResult.Offline, c.sendMessageResult("room", "same payload", "temp_stable-key")) }
        assertEquals(4, t.calls.size)
        assertEquals(1, t.calls.map { it.body }.distinct().size)
        t.calls.forEach { assertEquals("temp_stable-key", org.json.JSONObject(it.body).getString("client_id")) }
    }

    @Test fun malformedCommitResponseKeepsQueuedMessageUnconfirmed() {
        val missingId = FakeTransport { 200 to """{"data":{"content":"hello"}}""" }
        assertEquals(ApiChatClient.SendResult.Offline, client(missingId).sendMessageResult("room", "hello", "temp_key"))
        val wrongRoom = FakeTransport { 200 to """{"data":{"id":"message","room_id":"other-room"}}""" }
        assertEquals(ApiChatClient.SendResult.Offline, client(wrongRoom).sendMessageResult("room", "hello", "temp_key"))
    }

}
