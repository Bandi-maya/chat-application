package com.example.chat.data.api

import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/** Opaque account/config snapshot. Never log this object or its token. */
class CallSignalScope(val accountId: String, val baseUrl: String, val token: String, val generation: Long)

/** Inactive until wired by owner. Synchronous IO only; no scheduler, login or network retry.
 * Routes match Flutter api@cac25f1. A successful GET does not prove server dequeue semantics. */
class ApiCallSignalClient(
    private val currentScope: () -> CallSignalScope?,
    private val transport: ApiHttpTransport,
) {
    sealed interface PollResult {
        data class Signals(val signals: List<Map<String, Any?>>) : PollResult
        data object Unavailable : PollResult
        data object Unauthorized : PollResult
        data object Stale : PollResult
        data object InFlight : PollResult
        data object Failed : PollResult
    }
    enum class SendResult { ACCEPTED, UNAUTHORIZED, UNAVAILABLE, STALE, FAILED }
    private val polling = AtomicBoolean(false)
    private var unavailableScope: CallSignalScope? = null
    private fun valid(scope: CallSignalScope): Boolean = currentScope() === scope
    private fun base(scope: CallSignalScope): String? = runCatching {
        val uri = URI(scope.baseUrl)
        // Never send credentials through unencrypted or user-info/fragment URLs.
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) null
        else scope.baseUrl.trimEnd('/')
    }.getOrNull()
    private fun request(scope: CallSignalScope, method: String, path: String, body: String?): Pair<Int, String>? {
        val base = base(scope) ?: return null
        if (!valid(scope)) return null
        val headers = mapOf("Authorization" to "Bearer ${scope.token}", "Content-Type" to "application/json")
        val first = transport.execute(method, "$base/$path", headers, body)
        if (!valid(scope)) return null
        return if (first.first == 404) {
            transport.execute(method, "$base/api/v1/$path", headers, body).takeIf { valid(scope) }
        } else first
    }
    fun send(targetUserId: String, signal: Map<String, Any?>, roomId: String? = null): SendResult {
        val expected = currentScope() ?: return SendResult.UNAUTHORIZED
        return send(expected, targetUserId, signal, roomId)
    }
    fun send(scope: CallSignalScope, targetUserId: String, signal: Map<String, Any?>, roomId: String? = null): SendResult {
        if (!valid(scope)) return SendResult.STALE
        if (targetUserId.isBlank() || (signal["call_id"] as? String).isNullOrBlank() || signal["signal_type"] !is String) return SendResult.FAILED
        return try {
            val body = JSONObject().put("target_user_id", targetUserId).put("data", JSONObject(signal))
            if (!roomId.isNullOrBlank()) body.put("room_id", roomId)
            val response = request(scope, "POST", "chat/call/signal", body.toString()) ?: return SendResult.STALE
            when (response.first) { in 200..299 -> SendResult.ACCEPTED; 401,403 -> SendResult.UNAUTHORIZED; 404,405 -> SendResult.UNAVAILABLE; else -> SendResult.FAILED }
        } catch (_: Exception) { if (valid(scope)) SendResult.FAILED else SendResult.STALE }
    }
    fun poll(): PollResult {
        val expected = currentScope() ?: return PollResult.Unauthorized
        return poll(expected)
    }
    fun poll(scope: CallSignalScope): PollResult {
        if (!valid(scope)) return PollResult.Stale
        if (unavailableScope === scope) return PollResult.Unavailable
        if (!polling.compareAndSet(false, true)) return PollResult.InFlight
        try {
            val response = request(scope, "GET", "chat/call/signals", null) ?: return PollResult.Stale
            if (response.first == 401 || response.first == 403) return PollResult.Unauthorized
            if (response.first == 404 || response.first == 405) { unavailableScope = scope; return PollResult.Unavailable }
            if (response.first != 200) return PollResult.Failed
            val items = JSONObject(response.second).optJSONArray("data") ?: return PollResult.Failed
            val result = mutableListOf<Map<String, Any?>>()
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val data = item.optJSONObject("data") ?: continue
                val outer = item.optString("sender_id", "")
                val inner = data.optString("sender_id", "")
                if (outer.isNotEmpty() && inner.isNotEmpty() && outer != inner) continue
                if (inner.isEmpty() && outer.isNotEmpty()) data.put("sender_id", outer)
                if (data.optString("sender_id").isBlank() || data.optString("call_id").isBlank()) continue
                result.add(jsonMap(data))
            }
            return if (valid(scope)) PollResult.Signals(result) else PollResult.Stale
        } catch (_: Exception) { return if (valid(scope)) PollResult.Failed else PollResult.Stale }
        finally { polling.set(false) }
    }
    private fun jsonMap(value: JSONObject): Map<String, Any?> = value.keys().asSequence().associateWith { key ->
        when (val item = value.get(key)) { JSONObject.NULL -> null; is JSONObject -> jsonMap(item); else -> item }
    }
}
