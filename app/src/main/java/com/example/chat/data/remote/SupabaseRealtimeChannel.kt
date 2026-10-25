package com.example.chat.data.remote

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/** Mirrors supabase_dart's RealtimeSubscribeStatus (the values the api
 *  branch's subscribe callback switches on). */
enum class RealtimeSubscribeStatus { subscribed, timedOut, closed, channelError }

/** One postgres_changes registration, as passed to onPostgresChanges. */
data class PostgresChangeSubscription(
    val table: String,
    val schema: String = "public",
    val event: String = "*",
)

/** Decoded postgres_changes payload. [eventType] is INSERT / UPDATE / DELETE;
 *  [record] is newRecord, [oldRecord] oldRecord (delete payloads carry the
 *  row in oldRecord, like supabase_dart). */
data class PostgresChange(
    val eventType: String,
    val table: String,
    val schema: String,
    val record: Map<String, Any?>,
    val oldRecord: Map<String, Any?>,
)

/** A live channel; [close] mirrors _client.removeChannel(channel). */
interface RealtimeChannel {
    fun close()
}

/**
 * Minimal Phoenix-protocol (vsn 1.0.0) Supabase Realtime websocket channel
 * over OkHttp, covering exactly what the api branch uses: one
 * "realtime:<topic>" channel with N table-scoped postgres_changes
 * registrations, join-time access_token for RLS, 25s heartbeats, and
 * exponential-backoff reconnect (supabase_flutter reconnects internally the
 * same way; statuses surface as subscribed / timedOut / closed /
 * channelError, which the service maps onto ConnectionHealthService).
 *
 * Each postgres_changes payload is delivered once per matching registration;
 * because every registration here is table-scoped with no column filter,
 * routing by payload table is equivalent to supabase_dart's per-callback
 * dispatch.
 */
class SupabaseRealtimeChannel(
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val accessToken: suspend () -> String?,
    private val topic: String,
    private val subscriptions: List<PostgresChangeSubscription>,
    private val onChange: (PostgresChange) -> Unit,
    private val onStatus: (RealtimeSubscribeStatus) -> Unit,
    private val client: OkHttpClient = OkHttpClient(),
) : RealtimeChannel {

    private val lock = Any()
    private val refCounter = AtomicLong(0)
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "chaty-realtime").apply { isDaemon = true }
    }

    private var socket: WebSocket? = null
    private var closedIntentionally = false
    private var joinRef: String? = null
    private var reconnectAttempts = 0
    private var lastPongAt = 0L

    fun start() {
        scheduler.execute { connect() }
        scheduler.scheduleWithFixedDelay(
            { heartbeat() }, HEARTBEAT_MS, HEARTBEAT_MS, TimeUnit.MILLISECONDS,
        )
    }

    override fun close() {
        synchronized(lock) { closedIntentionally = true }
        scheduler.execute {
            synchronized(lock) {
                val s = socket
                socket = null
                s?.close(1000, "channel removed")
            }
            onStatus(RealtimeSubscribeStatus.closed)
            scheduler.shutdown()
        }
    }

    private fun nextRef(): String = refCounter.incrementAndGet().toString()

    private fun connect() {
        val wsUrl = supabaseUrl
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .trimEnd('/') +
            "/realtime/v1/websocket?apikey=$publishableKey&vsn=1.0.0"
        val request = Request.Builder().url(wsUrl).build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                scheduler.execute { sendJoin() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scheduler.execute { handleMessage(text) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scheduler.execute { handleSocketDown() }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scheduler.execute { handleSocketDown() }
            }
        }
        synchronized(lock) {
            if (closedIntentionally) return
            socket = client.newWebSocket(request, listener)
        }
    }

    private fun sendJoin() {
        val changes = JSONArray()
        for (sub in subscriptions) {
            changes.put(
                JSONObject()
                    .put("event", sub.event)
                    .put("schema", sub.schema)
                    .put("table", sub.table)
            )
        }
        val config = JSONObject()
            .put(
                "config",
                JSONObject()
                    .put("postgres_changes", changes)
                    .put("broadcast", JSONObject().put("ack_self", false).put("self", false))
                    .put("presence", JSONObject().put("key", "")),
            )
        val token = runCatching {
            kotlinx.coroutines.runBlocking { accessToken() }
        }.getOrNull()
        if (token != null) config.put("access_token", token)
        val ref = nextRef()
        synchronized(lock) { joinRef = ref }
        send(topic, "phx_join", config, ref)
    }

    private fun send(topicName: String, event: String, payload: JSONObject, ref: String?) {
        val frame = JSONObject()
            .put("topic", topicName)
            .put("event", event)
            .put("payload", payload)
            .put("ref", ref ?: JSONObject.NULL)
        val text = frame.toString()
        val s = synchronized(lock) { socket }
        s?.send(text)
    }

    private fun handleMessage(text: String) {
        val frame = runCatching { JSONObject(text) }.getOrNull() ?: return
        val event = frame.optString("event")
        val payload = frame.optJSONObject("payload") ?: JSONObject()
        when (event) {
            "phx_reply" -> {
                val status = payload.optString("status")
                val ref = if (frame.isNull("ref")) null else frame.optString("ref")
                if (ref != null && ref == synchronized(lock) { joinRef }) {
                    if (status == "ok") {
                        synchronized(lock) { reconnectAttempts = 0 }
                        lastPongAt = System.currentTimeMillis()
                        onStatus(RealtimeSubscribeStatus.subscribed)
                    } else {
                        onStatus(RealtimeSubscribeStatus.channelError)
                        scheduleReconnect()
                    }
                } else {
                    // Heartbeat / access_token replies.
                    lastPongAt = System.currentTimeMillis()
                }
            }
            "postgres_changes" -> {
                val data = payload.optJSONObject("data") ?: return
                val change = PostgresChange(
                    eventType = data.optString("type"),
                    table = data.optString("table"),
                    schema = data.optString("schema"),
                    record = data.optJSONObject("record")?.let { PostgrestRpcClient.jsonToMap(it) }
                        ?: emptyMap(),
                    oldRecord = data.optJSONObject("old_record")?.let { PostgrestRpcClient.jsonToMap(it) }
                        ?: emptyMap(),
                )
                onChange(change)
            }
            "system" -> {
                if (payload.optString("status") == "ok") {
                    lastPongAt = System.currentTimeMillis()
                }
            }
        }
    }

    private fun heartbeat() {
        val (intentional, hasSocket) = synchronized(lock) { closedIntentionally to (socket != null) }
        if (intentional) return
        if (!hasSocket) return
        send("phoenix", "heartbeat", JSONObject(), nextRef())
        if (lastPongAt > 0 && System.currentTimeMillis() - lastPongAt > HEARTBEAT_TIMEOUT_MS) {
            // Server stopped answering: recycle the socket to force reconnect,
            // same effect as supabase_flutter's heartbeat timeout.
            val s = synchronized(lock) { socket }
            s?.cancel()
        }
    }

    private fun handleSocketDown() {
        val intentional = synchronized(lock) {
            val was = closedIntentionally
            socket = null
            was
        }
        if (intentional) return
        onStatus(RealtimeSubscribeStatus.timedOut)
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        val attempts = synchronized(lock) { ++reconnectAttempts }
        val delayMs = (BASE_BACKOFF_MS shl (attempts - 1).coerceAtMost(5))
            .coerceAtMost(MAX_BACKOFF_MS)
        scheduler.schedule({ connect() }, delayMs, TimeUnit.MILLISECONDS)
    }

    companion object {
        private const val HEARTBEAT_MS = 25_000L
        private const val HEARTBEAT_TIMEOUT_MS = 60_000L
        private const val BASE_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
    }
}
