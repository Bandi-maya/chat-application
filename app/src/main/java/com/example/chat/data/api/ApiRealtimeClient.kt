package com.example.chat.data.api

import com.example.chat.core.util.parseIsoOrNow
import com.example.chat.domain.models.ChatMessage
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** Realtime plane of the api branch's ApiBackendService
 *  (lib/data/services/api_backend_service.dart:593-806, 1551-1564): custom
 *  backend WebSocket connect, 25s ping heartbeat, 4s reconnect, the
 *  new_message / message_edited / message_deleted / messages_cleared /
 *  call_signal / pong events, the send_message WebSocket fallback, and
 *  outgoing call signals. URL resolution is an exact port of
 *  ApiBackendConfig.getWebSocketUrl (lib/data/services/backend_config.dart:80).
 *
 *  Context-free and JVM-testable: the socket and the clock are injected seams,
 *  exactly like ApiChatClient's transport seam. ApiBackendService owns all
 *  chat state; this client only parses wire events and reports them through
 *  [Listener] (Dart applies the optimistic-replace / conversation-bump state
 *  changes in the service, so those stay with the service owner).
 *
 *  Threading mirrors Dart's event loop: every state transition and listener
 *  dispatch runs on the injected [Scheduler]; socket callbacks from any
 *  thread are posted onto it. Public send methods are fire-and-forget from
 *  any thread, like Dart's sink.add.
 */
class ApiRealtimeClient(
    /** Resolves the ws(s) URL for the current session, or null when there is
     *  no usable session/user id (Dart _connectWebSocket returns early when
     *  token/currentUser id is missing; the service also applies the JWT uid
     *  fallback before resolving). Evaluated on every connect attempt. */
    private val webSocketUrl: () -> String?,
    private val token: () -> String?,
    private val socketFactory: SocketFactory = OkHttpSocketFactory(),
    private val scheduler: Scheduler = ExecutorScheduler(),
    /** null keeps the exact fixed 4s reconnect; non-null adds capped exponential backoff with jitter. */
    private val backoff: com.example.chat.data.network.BackoffPolicy? = null,
    private val connectionScopeIdentity: () -> Any? = { null },
    private val connectionScopeGuard: (Any?, () -> Unit) -> Boolean = { _, action -> action(); true },
) {

    /** Minimal socket seam so the client is JVM-testable without OkHttp. */
    interface Socket {
        /** Queues a text frame; false when it could not be queued (closed). */
        fun send(text: String): Boolean
        fun close()
    }

    interface SocketFactory {
        fun connect(url: String, listener: SocketListener): Socket
    }

    /** Socket callbacks. May arrive on any thread; the client re-posts them
     *  onto the scheduler. */
    interface SocketListener {
        fun onMessage(socket: Socket, text: String)
        fun onFailure(socket: Socket, error: Throwable)
        fun onClosed(socket: Socket)
    }

    /** One-shot delayed task seam (Dart Timer / Timer.periodic). */
    interface Scheduler {
        fun schedule(delayMillis: Long, task: () -> Unit): Cancelable
        fun interface Cancelable {
            fun cancel()
        }
    }

    /** Wire events, delivered on the scheduler thread. State application
     *  (optimistic replace, conversation bump, preview recompute) is the
     *  service owner's job, matching the Dart split. */
    interface Listener {
        /** new_message: payload['data'] ?? payload['message'] parsed with
         *  ChatMessage.fromApi. */
        fun onNewMessage(message: ChatMessage) {}
        /** message_edited: uid ?? id, room_id, content, editedAt =
         *  DateTime.tryParse(updated_at) ?? DateTime.now(). */
        fun onMessageEdited(roomId: String, messageId: String, newContent: String, editedAt: Instant) {}
        /** message_deleted: uid ?? id, room_id. */
        fun onMessageDeleted(roomId: String, messageId: String) {}
        /** messages_cleared: room_id (dropped when empty, like Dart). */
        fun onMessagesCleared(roomId: String) {}
        /** call_signal: payload['data'] ?? payload, only when it is a Map. */
        fun onCallSignal(data: Map<String, Any?>) {}
        fun onScopedCallSignal(data: Map<String, Any?>, capturedScope: Any?) { onCallSignal(data) }
    }

    @Volatile
    var listener: Listener? = null

    /** Dart: bool get isConnected => _channel != null. */
    val isConnected: Boolean get() = socket != null

    @Volatile
    private var socket: Socket? = null

    // Everything below is touched only on the scheduler thread.
    private var socketScope: Any? = null
    private var socketToken: String? = null
    private var socketUrl: String? = null
    private var connecting = false
    private var pingCancel: Scheduler.Cancelable? = null
    private var reconnectCancel: Scheduler.Cancelable? = null
    private var reconnectAttempt = 0

    /** Network regained: skip the pending wait and reconnect now (no-op while
     *  connected/connecting or signed out). Safe to call from any thread. */
    fun reconnectNow() = post {
        if (socket != null && (socketToken != token() || socketUrl != webSocketUrl() || socketScope !== connectionScopeIdentity())) cleanupLocked()
        if (socket != null || connecting || token() == null) return@post
        reconnectCancel?.cancel()
        reconnectCancel = null
        reconnectAttempt = 0
        connect()
    }

    private fun post(task: () -> Unit) {
        scheduler.schedule(0, task)
    }

    /** Dart _connectWebSocket: no-op without a session or while
     *  connecting/connected; cancels any pending reconnect; on immediate
     *  failure cleans up and schedules the 4s reconnect. */
    fun connect() = post {
        if (socket != null && (socketToken != token() || socketUrl != webSocketUrl() || socketScope !== connectionScopeIdentity())) cleanupLocked()
        if (connecting || socket != null) return@post
        if (token() == null) return@post
        val url = webSocketUrl()?.takeIf { it.isNotBlank() } ?: return@post
        reconnectCancel?.cancel()
        reconnectCancel = null
        connecting = true
        socketToken = token(); socketUrl = url; socketScope = connectionScopeIdentity()
        val expectedScope = socketScope
        var registered: Socket? = null
        val created = try {
            if (!connectionScopeGuard(expectedScope) {
                if (socketToken == token() && socketUrl == webSocketUrl()) {
                    registered = socketFactory.connect(url, socketListener)
                    socket = registered
                }
            } || registered == null) {
                connecting = false
                return@post
            }
            registered!!
        } catch (_: Exception) {
            connecting = false
            scheduleReconnectLocked()
            return@post
        }
        socket = created
        connecting = false
        schedulePingLocked()
    }

    /** Dart signOut path: cancel the reconnect timer and clean up without
     *  scheduling another reconnect. */
    fun disconnect() = post {
        reconnectCancel?.cancel()
        reconnectCancel = null
        cleanupLocked()
    }

    /** Dart sendMessage step 2 (api_backend_service.dart:1551): the WebSocket
     *  fallback used only when the HTTP POST did not confirm. Fire-and-forget;
     *  Dart swallows errors. Returns whether a frame was queued (informational
     *  for logging/tests). */
    fun sendMessageFallback(roomId: String, content: String): Boolean {
        val s = socket ?: return false
        return try {
            s.send(
                JSONObject()
                    .put("type", "send_message")
                    .put("room_id", roomId)
                    .put("content", content)
                    .toString(),
            )
        } catch (_: Exception) {
            false
        }
    }

    /** Dart sendCallSignal (api_backend_service.dart:807): sends
     *  {type: call_signal, target_user_id, data}; no-op when disconnected. */
    fun sendCallSignal(targetUserId: String, data: Map<String, Any?>): Boolean {
        val s = socket ?: return false
        val expectedScope = socketScope
        var queued = false
        val accepted = connectionScopeGuard(expectedScope) {
            if (s === socket && socketToken == token() && socketUrl == webSocketUrl() && expectedScope === connectionScopeIdentity()) {
                queued = try {
                    s.send(JSONObject().put("type", "call_signal").put("target_user_id", targetUserId).put("data", toJson(data)).toString())
                } catch (_: Exception) { false }
            }
        }
        if (!accepted || !queued) connect()
        return queued
    }

    // ── Internals (scheduler thread only) ────────────────────────────────

    private val socketListener = object : SocketListener {
        override fun onMessage(socket: Socket, text: String) = post {
            if (socket !== this@ApiRealtimeClient.socket) return@post
            if (socketToken != token() || socketUrl != webSocketUrl() || socketScope !== connectionScopeIdentity()) return@post
            reconnectAttempt = 0
            handleMessage(text)
        }

        override fun onFailure(socket: Socket, error: Throwable) = post {
            if (socket !== this@ApiRealtimeClient.socket) return@post
            cleanupLocked()
            scheduleReconnectLocked()
        }

        override fun onClosed(socket: Socket) = post {
            if (socket !== this@ApiRealtimeClient.socket) return@post
            cleanupLocked()
            scheduleReconnectLocked()
        }
    }

    /** Dart _cleanupWebSocket. */
    private fun cleanupLocked() {
        pingCancel?.cancel()
        pingCancel = null
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        socketToken = null; socketUrl = null
        connecting = false
    }

    /** Dart _scheduleReconnect: 4s later, reconnect only when a session
     *  remains and the channel is still down. */
    private fun scheduleReconnectLocked() {
        if (token() == null) return
        reconnectCancel?.cancel()
        val delayMs = backoff?.delayMs(reconnectAttempt++) ?: RECONNECT_DELAY_MS
        reconnectCancel = scheduler.schedule(delayMs) {
            reconnectCancel = null
            if (token() != null && socket == null) connect()
        }
    }

    /** Dart Timer.periodic(25s) ping heartbeat, as a self-rescheduling task.
     *  Stops when the channel or session is gone; a failed ping cleans up and
     *  schedules the reconnect, exactly like Dart. */
    private fun schedulePingLocked() {
        pingCancel?.cancel()
        pingCancel = scheduler.schedule(PING_INTERVAL_MS) { pingTick() }
    }

    private fun pingTick() {
        val s = socket
        if (s == null || token() == null) {
            pingCancel = null
            return
        }
        val ok = try {
            s.send("""{"type":"ping"}""")
        } catch (_: Exception) {
            false
        }
        if (!ok) {
            pingCancel = null
            cleanupLocked()
            scheduleReconnectLocked()
            return
        }
        schedulePingLocked()
    }

    /** Dart _channel.stream.listen onMessage: parse errors swallowed, pong
     *  ignored, every other event dispatched by its 'type'. */
    private fun handleMessage(text: String) {
        try {
            val payload = JSONObject(text)
            when (payload.optString("type", "")) {
                "pong" -> Unit
                "call_signal" -> {
                    val raw = if (payload.has("data") && !payload.isNull("data")) {
                        payload.get("data")
                    } else {
                        payload
                    }
                    (raw as? JSONObject)?.let {
                        val signal = ApiChatClient.deepMap(it).toMutableMap()
                        val outer = payload.optString("sender_id", "")
                        val inner = signal["sender_id"]?.toString().orEmpty()
                        if (outer.isNotEmpty() && inner.isNotEmpty() && outer != inner) return
                        if (inner.isEmpty() && outer.isNotEmpty()) signal["sender_id"] = outer
                        listener?.onScopedCallSignal(signal, socketScope)
                    }
                }
                "new_message" -> {
                    val raw = dataOrMessage(payload) ?: return
                    listener?.onNewMessage(ChatMessage.fromApi(ApiChatClient.deepMap(raw)))
                }
                "message_edited" -> {
                    val raw = dataOrMessage(payload) ?: return
                    val msgId = str(raw, "uid") ?: str(raw, "id") ?: ""
                    val roomId = str(raw, "room_id") ?: ""
                    val newContent = str(raw, "content") ?: ""
                    val editedAt = parseIsoOrNow(str(raw, "updated_at"))
                    listener?.onMessageEdited(roomId, msgId, newContent, editedAt)
                }
                "message_deleted" -> {
                    val raw = dataOrMessage(payload) ?: return
                    val msgId = str(raw, "uid") ?: str(raw, "id") ?: ""
                    val roomId = str(raw, "room_id") ?: ""
                    listener?.onMessageDeleted(roomId, msgId)
                }
                "messages_cleared" -> {
                    val raw = dataOrMessage(payload)
                    val roomId = raw?.let { str(it, "room_id") } ?: ""
                    if (roomId.isNotEmpty()) listener?.onMessagesCleared(roomId)
                }
            }
        } catch (_: Exception) {
            // Dart: debugPrint + swallow; the channel stays up.
        }
    }

    /** Dart: payload['data'] ?? payload['message'], null-tolerant; anything
     *  that is not a JSON object is dropped (Dart would throw inside
     *  ChatMessage.fromApi / index assignment and swallow). */
    private fun dataOrMessage(payload: JSONObject): JSONObject? {
        val raw = if (payload.has("data") && !payload.isNull("data")) {
            payload.get("data")
        } else if (payload.has("message") && !payload.isNull("message")) {
            payload.get("message")
        } else {
            null
        }
        return raw as? JSONObject
    }

    companion object {
        const val PING_INTERVAL_MS = 25_000L
        const val RECONNECT_DELAY_MS = 4_000L

        /** Dart map access: missing key or JSON null both read as null. */
        private fun str(obj: JSONObject, key: String): String? {
            if (!obj.has(key) || obj.isNull(key)) return null
            return obj.get(key).toString()
        }

        /** jsonEncode-equivalent for call-signal payloads: maps and lists
         *  become JSON containers, null stays an explicit JSON null. */
        private fun toJson(value: Any?): Any = when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> {
                val out = JSONObject()
                for ((k, v) in value) if (k != null) out.put(k.toString(), toJson(v))
                out
            }
            is List<*> -> {
                val out = JSONArray()
                for (v in value) out.put(toJson(v))
                out
            }
            is JSONObject, is JSONArray -> value
            else -> value
        }

        /** Exact port of ApiBackendConfig.getWebSocketUrl
         *  (lib/data/services/backend_config.dart:80-108). Precedence:
         *  custom override -> WEBSOCKET_URL env -> REST base with ws scheme.
         *  A value containing '/ws/school' is ignored at each level.
         *  '{userId}' / '$userId' placeholders are substituted; otherwise the
         *  standard '/chat/ws/<userId>' path is appended. */
        fun resolveWebSocketUrl(
            userId: String,
            customWebSocketUrl: String?,
            envWebSocketUrl: String = "",
            restApiBaseUrl: String,
        ): String {
            val cleanUserId = userId.trim()
            if (!customWebSocketUrl.isNullOrEmpty() && !customWebSocketUrl.contains("/ws/school")) {
                val ws = customWebSocketUrl.trim()
                if (ws.contains("{userId}") || ws.contains("\$userId")) {
                    return ws
                        .replace("{userId}", cleanUserId)
                        .replace("\$userId", cleanUserId)
                }
                return "${ws.replace(Regex("/+$"), "")}/chat/ws/$cleanUserId"
            }
            if (envWebSocketUrl.isNotEmpty() && !envWebSocketUrl.contains("/ws/school")) {
                if (envWebSocketUrl.contains("{userId}") || envWebSocketUrl.contains("\$userId")) {
                    return envWebSocketUrl
                        .replace("{userId}", cleanUserId)
                        .replace("\$userId", cleanUserId)
                }
                return "${envWebSocketUrl.replace(Regex("/+$"), "")}/chat/ws/$cleanUserId"
            }
            val base = restApiBaseUrl
                .replaceFirst("https://", "wss://")
                .replaceFirst("http://", "ws://")
                .replace(Regex("/api/v[0-9]+/?$"), "")
                .replace(Regex("/+$"), "")
            return "$base/chat/ws/$cleanUserId"
        }
    }

    /** Production scheduler: a single daemon thread, so all client state is
     *  confined to one thread like Dart's event loop. */
    class ExecutorScheduler : Scheduler {
        private val executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "ApiRealtimeClient").apply { isDaemon = true }
        }

        override fun schedule(delayMillis: Long, task: () -> Unit): Scheduler.Cancelable {
            val future = executor.schedule(
                { task() },
                delayMillis,
                java.util.concurrent.TimeUnit.MILLISECONDS,
            )
            return Scheduler.Cancelable { future.cancel(false) }
        }

        fun shutdown() {
            executor.shutdown()
        }
    }

    /** Production socket factory backed by OkHttp's WebSocket. OkHttp queues
     *  frames until the socket opens, matching Dart's WebSocketChannel
     *  behavior of accepting sink.add before the connection completes. */
    class OkHttpSocketFactory : SocketFactory {
        private val client = okhttp3.OkHttpClient()

        override fun connect(url: String, listener: SocketListener): Socket {
            lateinit var webSocket: okhttp3.WebSocket
            val socket = object : Socket {
                override fun send(text: String): Boolean = webSocket.send(text)
                override fun close() {
                    webSocket.close(1000, null)
                }
            }
            webSocket = client.newWebSocket(
                okhttp3.Request.Builder().url(url).build(),
                object : okhttp3.WebSocketListener() {
                    override fun onMessage(ws: okhttp3.WebSocket, text: String) =
                        listener.onMessage(socket, text)

                    override fun onFailure(ws: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) =
                        listener.onFailure(socket, t)

                    override fun onClosed(ws: okhttp3.WebSocket, code: Int, reason: String) =
                        listener.onClosed(socket)

                    override fun onClosing(ws: okhttp3.WebSocket, code: Int, reason: String) {
                        ws.close(1000, null)
                    }
                },
            )
            return socket
        }
    }
}
