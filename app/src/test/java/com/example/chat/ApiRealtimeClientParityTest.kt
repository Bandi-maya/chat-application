package com.example.chat

import com.example.chat.data.api.ApiRealtimeClient
import com.example.chat.domain.models.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior parity tests for the custom-backend WebSocket plane
 *  (api_backend_service.dart:593-806, 1551-1564; backend_config.dart:80-108). */
class ApiRealtimeClientParityTest {

    // ── Fakes ────────────────────────────────────────────────────────────

    /** Virtual-time scheduler: tasks fire only when advanced, so ping and
     *  reconnect timing is asserted exactly. */
    private class FakeScheduler : ApiRealtimeClient.Scheduler {
        private data class Task(val at: Long, val seq: Int, val run: () -> Unit)

        var now = 0L
        private var seq = 0
        private val tasks = sortedSetOf<Task>(compareBy({ it.at }, { it.seq }))
        private val canceled = mutableSetOf<Task>()

        override fun schedule(delayMillis: Long, run: () -> Unit): ApiRealtimeClient.Scheduler.Cancelable {
            val task = Task(now + delayMillis, seq++, run)
            tasks.add(task)
            return ApiRealtimeClient.Scheduler.Cancelable { canceled.add(task) }
        }

        /** Run every task due at the current virtual time (including tasks
         *  those tasks schedule for "now"), then stop. */
        fun drain() {
            while (true) {
                val next = tasks.firstOrNull() ?: return
                if (next.at > now) return
                tasks.remove(next)
                if (!canceled.remove(next)) next.run()
            }
        }

        fun advanceBy(millis: Long) {
            val target = now + millis
            while (true) {
                val next = tasks.firstOrNull() ?: break
                if (next.at > target) break
                tasks.remove(next)
                now = next.at
                if (!canceled.remove(next)) next.run()
            }
            now = target
        }

        fun hasPending(): Boolean = tasks.any { it !in canceled }
    }

    private class FakeSocket(val listener: ApiRealtimeClient.SocketListener) : ApiRealtimeClient.Socket {
        val sent = mutableListOf<String>()
        var closed = false
        var failSends = false

        override fun send(text: String): Boolean {
            if (failSends || closed) return false
            sent.add(text)
            return true
        }

        override fun close() {
            closed = true
        }

        fun incoming(text: String) = listener.onMessage(this, text)
        fun fail(error: Throwable = java.io.IOException("boom")) = listener.onFailure(this, error)
        fun serverClosed() = listener.onClosed(this)
    }

    private class FakeFactory : ApiRealtimeClient.SocketFactory {
        val urls = mutableListOf<String>()
        val sockets = mutableListOf<FakeSocket>()
        var throwOnConnect = false

        override fun connect(url: String, listener: ApiRealtimeClient.SocketListener): ApiRealtimeClient.Socket {
            if (throwOnConnect) throw java.io.IOException("refused")
            urls.add(url)
            val socket = FakeSocket(listener)
            sockets.add(socket)
            return socket
        }
    }

    private class RecordingListener : ApiRealtimeClient.Listener {
        val newMessages = mutableListOf<com.example.chat.domain.models.ChatMessage>()
        val edits = mutableListOf<List<Any?>>()
        val deletes = mutableListOf<List<String>>()
        val clears = mutableListOf<String>()
        val callSignals = mutableListOf<Map<String, Any?>>()

        override fun onNewMessage(message: com.example.chat.domain.models.ChatMessage) {
            newMessages.add(message)
        }

        override fun onMessageEdited(roomId: String, messageId: String, newContent: String, editedAt: java.time.Instant) {
            edits.add(listOf(roomId, messageId, newContent, editedAt))
        }

        override fun onMessageDeleted(roomId: String, messageId: String) {
            deletes.add(listOf(roomId, messageId))
        }

        override fun onMessagesCleared(roomId: String) {
            clears.add(roomId)
        }

        override fun onCallSignal(data: Map<String, Any?>) {
            callSignals.add(data)
        }
    }

    private class Rig(token: String? = "tok", url: String? = "ws://10.0.2.2:8000/chat/ws/u1") {
        val scheduler = FakeScheduler()
        val factory = FakeFactory()
        val listener = RecordingListener()
        var tokenValue = token
        var urlValue = url
        val client = ApiRealtimeClient({ urlValue }, { tokenValue }, factory, scheduler)

        init {
            client.listener = listener
        }

        fun connect(): Rig {
            client.connect()
            scheduler.drain()
            return this
        }

        val socket: FakeSocket get() = factory.sockets.last()
    }

    @Test fun scopeReplacementRejectsOldSocketAndConnectsNewSocket() {
        val scheduler = FakeScheduler()
        val factory = FakeFactory()
        var current: Any = Any()
        val client = ApiRealtimeClient({ "wss://example.invalid/same" }, { "same-token" }, factory, scheduler,
            connectionScopeIdentity = { current },
            connectionScopeGuard = { expected, action -> if (expected === current) { action(); true } else false })
        client.connect(); scheduler.drain()
        val old = factory.sockets.single()
        current = Any() // Same token/url is still a different authentication generation.
        assertFalse(client.sendCallSignal("new-recipient", mapOf("call_id" to "secret-new-call")))
        assertTrue(old.sent.none { it.contains("secret-new-call") })
        scheduler.drain()
        assertTrue(old.closed)
        assertEquals(2, factory.sockets.size)
        assertTrue(client.sendCallSignal("new-recipient", mapOf("call_id" to "new-call")))
        assertTrue(factory.sockets.last().sent.any { it.contains("new-call") })
    }

    // ── URL resolution (backend_config.dart getWebSocketUrl) ─────────────

    @Test
    fun urlDefaultsToWsSchemeOnRestBase() {
        assertEquals(
            "ws://10.0.2.2:8000/chat/ws/u1",
            ApiRealtimeClient.resolveWebSocketUrl("u1", null, "", "http://10.0.2.2:8000"),
        )
    }

    @Test
    fun urlUpgradesHttpsToWss() {
        assertEquals(
            "wss://chat.example.com/chat/ws/u1",
            ApiRealtimeClient.resolveWebSocketUrl("u1", null, "", "https://chat.example.com"),
        )
    }

    @Test
    fun urlStripsApiVersionSuffixAndTrailingSlashes() {
        assertEquals(
            "wss://chat.example.com/chat/ws/u1",
            ApiRealtimeClient.resolveWebSocketUrl("u1", null, "", "https://chat.example.com/api/v1/"),
        )
    }

    @Test
    fun urlTrimsUserId() {
        assertEquals(
            "ws://10.0.2.2:8000/chat/ws/u1",
            ApiRealtimeClient.resolveWebSocketUrl("  u1  ", null, "", "http://10.0.2.2:8000"),
        )
    }

    @Test
    fun urlCustomAppendsStandardPath() {
        assertEquals(
            "wss://ws.example.com/live/chat/ws/u1",
            ApiRealtimeClient.resolveWebSocketUrl("u1", " wss://ws.example.com/live/ ", "", "http://x"),
        )
    }

    @Test
    fun urlCustomSubstitutesBothPlaceholders() {
        assertEquals(
            "wss://ws.example.com/chat/ws/u1",
            ApiRealtimeClient.resolveWebSocketUrl("u1", "wss://ws.example.com/chat/ws/{userId}", "", "http://x"),
        )
        assertEquals(
            "wss://ws.example.com/stream/u1",
            ApiRealtimeClient.resolveWebSocketUrl("u1", "wss://ws.example.com/stream/\$userId", "", "http://x"),
        )
    }

    @Test
    fun urlCustomSchoolPathIsIgnoredAndFallsThrough() {
        assertEquals(
            "wss://env.example.com/chat/ws/u1",
            ApiRealtimeClient.resolveWebSocketUrl(
                "u1", "wss://school.example.com/ws/school", "wss://env.example.com", "http://x",
            ),
        )
    }

    @Test
    fun urlEnvUsedWhenNoCustom() {
        assertEquals(
            "wss://env.example.com/chat/ws/u1",
            ApiRealtimeClient.resolveWebSocketUrl("u1", null, "wss://env.example.com/", "http://x"),
        )
    }

    @Test
    fun urlEnvSchoolPathIgnoredFallsToRestBase() {
        assertEquals(
            "ws://x/chat/ws/u1",
            ApiRealtimeClient.resolveWebSocketUrl("u1", null, "wss://s.example.com/ws/school", "http://x"),
        )
    }

    // ── Connect lifecycle ────────────────────────────────────────────────

    @Test
    fun connectIsNoOpWithoutToken() {
        val rig = Rig(token = null).connect()
        assertTrue(rig.factory.urls.isEmpty())
        assertFalse(rig.client.isConnected)
    }

    @Test
    fun connectIsNoOpWithoutUrl() {
        val rig = Rig(url = null).connect()
        assertTrue(rig.factory.urls.isEmpty())
        assertFalse(rig.client.isConnected)
    }

    @Test
    fun connectOpensSocketAndIsConnected() {
        val rig = Rig().connect()
        assertEquals(listOf("ws://10.0.2.2:8000/chat/ws/u1"), rig.factory.urls)
        assertTrue(rig.client.isConnected)
    }

    @Test
    fun connectWhileConnectedDoesNotReconnect() {
        val rig = Rig().connect()
        rig.client.connect()
        rig.scheduler.drain()
        assertEquals(1, rig.factory.sockets.size)
    }

    @Test
    fun connectFailureSchedulesReconnectAfter4Seconds() {
        val rig = Rig()
        rig.factory.throwOnConnect = true
        rig.connect()
        assertFalse(rig.client.isConnected)
        rig.factory.throwOnConnect = false
        rig.scheduler.advanceBy(3_999)
        assertEquals(0, rig.factory.sockets.size)
        rig.scheduler.advanceBy(1)
        assertEquals(1, rig.factory.sockets.size)
        assertTrue(rig.client.isConnected)
    }

    // ── Ping heartbeat ───────────────────────────────────────────────────

    @Test
    fun pingGoesOutEvery25Seconds() {
        val rig = Rig().connect()
        rig.scheduler.advanceBy(24_999)
        assertTrue(rig.socket.sent.isEmpty())
        rig.scheduler.advanceBy(1)
        assertEquals(listOf("""{"type":"ping"}"""), rig.socket.sent)
        rig.scheduler.advanceBy(25_000)
        assertEquals(2, rig.socket.sent.size)
    }

    @Test
    fun failedPingCleansUpAndReconnectsIn4Seconds() {
        val rig = Rig().connect()
        rig.scheduler.advanceBy(20_000)
        rig.socket.failSends = true
        rig.scheduler.advanceBy(5_000)
        assertFalse(rig.client.isConnected)
        assertTrue(rig.socket.closed)
        // Reconnect lands 4s after the failed ping at t=25s -> t=29s.
        rig.scheduler.advanceBy(3_999)
        assertEquals(1, rig.factory.sockets.size)
        rig.scheduler.advanceBy(1)
        assertEquals(2, rig.factory.sockets.size)
        assertTrue(rig.client.isConnected)
    }

    // ── Reconnect on drop ────────────────────────────────────────────────

    @Test
    fun socketFailureTriggersCleanupAndReconnect() {
        val rig = Rig().connect()
        rig.socket.fail()
        rig.scheduler.drain()
        assertFalse(rig.client.isConnected)
        rig.scheduler.advanceBy(4_000)
        assertEquals(2, rig.factory.sockets.size)
    }

    @Test
    fun serverCloseTriggersReconnect() {
        val rig = Rig().connect()
        rig.socket.serverClosed()
        rig.scheduler.drain()
        assertFalse(rig.client.isConnected)
        rig.scheduler.advanceBy(4_000)
        assertEquals(2, rig.factory.sockets.size)
    }

    @Test
    fun noReconnectAfterDisconnect() {
        val rig = Rig().connect()
        rig.client.disconnect()
        rig.scheduler.drain()
        assertFalse(rig.client.isConnected)
        rig.scheduler.advanceBy(60_000)
        assertEquals(1, rig.factory.sockets.size)
    }

    @Test
    fun noReconnectWhenTokenGone() {
        val rig = Rig().connect()
        rig.tokenValue = null
        rig.socket.fail()
        rig.scheduler.drain()
        rig.scheduler.advanceBy(60_000)
        assertEquals(1, rig.factory.sockets.size)
    }

    @Test
    fun staleSocketCallbacksAreIgnoredAfterReconnect() {
        val rig = Rig().connect()
        val old = rig.socket
        old.fail()
        rig.scheduler.drain()
        rig.scheduler.advanceBy(4_000)
        val current = rig.socket
        assertTrue(current !== old)
        old.incoming("""{"type":"new_message","data":{"uid":"m9","room_id":"r1","sender_id":"u2","content":"late","created_at":"2026-10-25T10:00:00Z"}}""")
        rig.scheduler.drain()
        assertTrue(rig.listener.newMessages.isEmpty())
    }

    // ── Events ───────────────────────────────────────────────────────────

    @Test
    fun pongIsIgnored() {
        val rig = Rig().connect()
        rig.socket.incoming("""{"type":"pong"}""")
        rig.scheduler.drain()
        assertTrue(rig.client.isConnected)
        assertEquals(0, rig.factory.sockets.size - 1)
    }

    @Test
    fun newMessageParsesFromDataKey() {
        val rig = Rig().connect()
        rig.socket.incoming(
            """{"type":"new_message","data":{"uid":"m1","room_id":"r1","sender_id":"u2","content":"hello","created_at":"2026-10-25T10:00:00Z"}}""",
        )
        rig.scheduler.drain()
        assertEquals(1, rig.listener.newMessages.size)
        val m = rig.listener.newMessages[0]
        assertEquals("m1", m.id)
        assertEquals("r1", m.conversationId)
        assertEquals("u2", m.senderId)
        assertEquals("hello", m.text)
        assertEquals(MessageType.text, m.type)
    }

    @Test
    fun newMessageParsesFromMessageKeyFallback() {
        val rig = Rig().connect()
        rig.socket.incoming(
            """{"type":"new_message","message":{"uid":"m2","room_id":"r2","sender_id":"u3","content":"hi","created_at":"2026-10-25T10:00:00Z"}}""",
        )
        rig.scheduler.drain()
        assertEquals("m2", rig.listener.newMessages[0].id)
    }

    @Test
    fun messageEditedExtractsIdsContentAndTime() {
        val rig = Rig().connect()
        rig.socket.incoming(
            """{"type":"message_edited","data":{"uid":"m1","room_id":"r1","content":"edited","updated_at":"2026-10-25T11:00:00Z"}}""",
        )
        rig.scheduler.drain()
        assertEquals(listOf("r1", "m1", "edited", java.time.Instant.parse("2026-10-25T11:00:00Z")), rig.listener.edits[0])
    }

    @Test
    fun messageEditedFallsBackToIdKey() {
        val rig = Rig().connect()
        rig.socket.incoming(
            """{"type":"message_edited","data":{"id":"m7","room_id":"r9","content":"x","updated_at":"bad"}}""",
        )
        rig.scheduler.drain()
        assertEquals("m7", rig.listener.edits[0][1])
        assertNotNull(rig.listener.edits[0][3]) // Dart: tryParse ?? now
    }

    @Test
    fun messageDeletedExtractsIds() {
        val rig = Rig().connect()
        rig.socket.incoming("""{"type":"message_deleted","data":{"uid":"m1","room_id":"r1"}}""")
        rig.scheduler.drain()
        assertEquals(listOf("r1", "m1"), rig.listener.deletes[0])
    }

    @Test
    fun messagesClearedReportsRoom() {
        val rig = Rig().connect()
        rig.socket.incoming("""{"type":"messages_cleared","data":{"room_id":"r1"}}""")
        rig.scheduler.drain()
        assertEquals(listOf("r1"), rig.listener.clears)
    }

    @Test
    fun messagesClearedWithoutRoomIsDropped() {
        val rig = Rig().connect()
        rig.socket.incoming("""{"type":"messages_cleared","data":{}}""")
        rig.socket.incoming("""{"type":"messages_cleared"}""")
        rig.scheduler.drain()
        assertTrue(rig.listener.clears.isEmpty())
    }

    @Test
    fun callSignalPrefersDataKey() {
        val rig = Rig().connect()
        rig.socket.incoming("""{"type":"call_signal","data":{"sdp":"offer","from":"u2"}}""")
        rig.scheduler.drain()
        assertEquals(mapOf("sdp" to "offer", "from" to "u2"), rig.listener.callSignals[0])
    }

    @Test
    fun callSignalWithoutDataUsesWholePayload() {
        val rig = Rig().connect()
        rig.socket.incoming("""{"type":"call_signal","sdp":"answer"}""")
        rig.scheduler.drain()
        assertEquals("answer", rig.listener.callSignals[0]["sdp"])
        assertEquals("call_signal", rig.listener.callSignals[0]["type"])
    }

    @Test
    fun callSignalNonMapDataIsDropped() {
        val rig = Rig().connect()
        rig.socket.incoming("""{"type":"call_signal","data":"nope"}""")
        rig.scheduler.drain()
        assertTrue(rig.listener.callSignals.isEmpty())
    }

    @Test
    fun malformedFrameIsSwallowedAndConnectionStaysUp() {
        val rig = Rig().connect()
        rig.socket.incoming("not json")
        rig.socket.incoming("""{"type":"new_message","data":"also not a map"}""")
        rig.scheduler.drain()
        assertTrue(rig.client.isConnected)
        assertTrue(rig.listener.newMessages.isEmpty())
    }

    // ── Send fallback + call signal ──────────────────────────────────────

    @Test
    fun sendMessageFallbackSendsExactPayload() {
        val rig = Rig().connect()
        assertTrue(rig.client.sendMessageFallback("r1", "hello world"))
        val sent = org.json.JSONObject(rig.socket.sent[0])
        assertEquals("send_message", sent.getString("type"))
        assertEquals("r1", sent.getString("room_id"))
        assertEquals("hello world", sent.getString("content"))
    }

    @Test
    fun sendMessageFallbackIsNoOpWhenDisconnected() {
        val rig = Rig()
        assertFalse(rig.client.sendMessageFallback("r1", "x"))
    }

    @Test
    fun sendMessageFallbackSwallowsErrors() {
        val rig = Rig().connect()
        rig.socket.failSends = true
        assertFalse(rig.client.sendMessageFallback("r1", "x"))
        assertTrue(rig.client.isConnected) // send failure alone does not tear down
    }

    @Test
    fun sendCallSignalSendsExactPayload() {
        val rig = Rig().connect()
        val data = mapOf<String, Any?>("sdp" to "offer", "candidates" to listOf("a", "b"), "extra" to null)
        assertTrue(rig.client.sendCallSignal("u2", data))
        val sent = org.json.JSONObject(rig.socket.sent[0])
        assertEquals("call_signal", sent.getString("type"))
        assertEquals("u2", sent.getString("target_user_id"))
        val payload = sent.getJSONObject("data")
        assertEquals("offer", payload.getString("sdp"))
        assertEquals("a", payload.getJSONArray("candidates").getString(0))
        assertTrue(payload.has("extra") && payload.isNull("extra"))
    }

    @Test
    fun sendCallSignalIsNoOpWhenDisconnected() {
        val rig = Rig()
        assertFalse(rig.client.sendCallSignal("u2", emptyMap()))
    }
}
