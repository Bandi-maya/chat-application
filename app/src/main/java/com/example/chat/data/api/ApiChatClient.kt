package com.example.chat.data.api

import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.UserProfile
import com.example.chat.domain.models.PresenceState
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** REST chat plane of the api branch's ApiBackendService
 *  (lib/data/services/api_backend_service.dart): rooms list, room messages,
 *  send, and directory users. Exact URL, header, and /api/v1 404-fallback
 *  parity. Context-free so it is JVM-testable; ApiBackendService owns state.
 *
 *  Scope honesty: this plane is server-readable content over TLS. MLS E2EE
 *  lives only on the Supabase fallback plane (ChatyBackendService); the api
 *  branch's REST chat flow does not encrypt message content. */
class ApiChatClient(
    private val baseUrl: () -> String,
    private val tenantId: () -> String,
    private val token: () -> String?,
    private val transport: ApiHttpTransport,
) {

    /** loadConversations outcome. Dart swallows network/parse errors and keeps
     *  prior state; [Failed] tells the caller to keep what it has. 402 sets
     *  the subscription-required state. */
    sealed interface RoomsResult {
        data class Ok(val conversations: List<Conversation>) : RoomsResult
        data object SubscriptionRequired : RoomsResult
        data object Failed : RoomsResult
    }

    private fun base() = baseUrl().trimEnd('/')

    private fun authHeaders(json: Boolean = false): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        token()?.let { headers["Authorization"] = "Bearer $it" }
        headers["x-tenant-id"] = tenantId()
        if (json) headers["Content-Type"] = "application/json"
        return headers
    }

    private fun getWithFallback(path: String): Pair<Int, String> {
        val response = transport.execute("GET", "${base()}/$path", authHeaders(), null)
        if (response.first == 404) {
            return transport.execute("GET", "${base()}/api/v1/$path", authHeaders(), null)
        }
        return response
    }

    /** GET /chat/rooms?include_tasks=false -> Conversation.fromApi list. */
    fun listRooms(): RoomsResult {
        if (token() == null) return RoomsResult.Failed
        return try {
            val response = getWithFallback("chat/rooms?include_tasks=false")
            when (response.first) {
                200 -> {
                    val data = JSONObject(response.second).getJSONArray("data")
                    val conversations = mutableListOf<Conversation>()
                    for (i in 0 until data.length()) {
                        conversations.add(Conversation.fromApi(deepMap(data.getJSONObject(i))))
                    }
                    RoomsResult.Ok(conversations)
                }
                402 -> RoomsResult.SubscriptionRequired
                else -> RoomsResult.Failed
            }
        } catch (_: Exception) {
            RoomsResult.Failed
        }
    }

    /** GET /chat/rooms/<id>/messages -> ChatMessage.fromApi list, or null when
     *  the load failed (Dart keeps prior messages on error). */
    fun listMessages(roomId: String): List<ChatMessage>? {
        if (token() == null) return null
        return try {
            val response = getWithFallback("chat/rooms/$roomId/messages")
            if (response.first != 200) return null
            val data = JSONObject(response.second).getJSONArray("data")
            val messages = mutableListOf<ChatMessage>()
            for (i in 0 until data.length()) {
                messages.add(ChatMessage.fromApi(deepMap(data.getJSONObject(i))))
            }
            messages
        } catch (_: Exception) {
            null
        }
    }

    /** POST /chat/rooms/<id>/messages {content} -> confirmed message on 200
     *  (body['data'] ?? body), null otherwise. The WebSocket send fallback in
     *  Dart lands with the realtime slice; without it a failed send leaves
     *  the optimistic temp message in 'sending' state, exactly like Dart
     *  with no channel. */
    /** Outcome of a send attempt, so the outbox knows whether to retry. */
    sealed interface SendResult {
        data class Ok(val message: ChatMessage) : SendResult
        /** No network / server unreachable / 5xx: keep it queued (clock) and retry later. */
        data object Offline : SendResult
        /** The server refused it (4xx, e.g. recipient deactivated): retrying will not help. */
        data class Rejected(val code: Int, val detail: String?) : SendResult
    }

    /** POST /chat/rooms/<id>/messages {content, client_id} (+ /api/v1 fallback). [clientId] lets
     *  the server drop a duplicate when a retry follows a send whose response was lost. */
    fun sendMessageResult(roomId: String, content: String, clientId: String?): SendResult {
        if (token() == null) return SendResult.Offline
        return try {
            val payload = JSONObject().put("content", content)
            if (!clientId.isNullOrEmpty()) payload.put("client_id", clientId)
            val body = payload.toString()
            var response = transport.execute(
                "POST", "${base()}/chat/rooms/$roomId/messages", authHeaders(json = true), body,
            )
            if (response.first == 404) {
                response = transport.execute(
                    "POST", "${base()}/api/v1/chat/rooms/$roomId/messages", authHeaders(json = true), body,
                )
            }
            val code = response.first
            when {
                code == 200 || code == 201 -> {
                    val json = JSONObject(response.second)
                    val raw = if (json.has("data") && !json.isNull("data")) json.get("data") else json
                    if (raw is JSONObject) SendResult.Ok(ChatMessage.fromApi(deepMap(raw))) else SendResult.Offline
                }
                // 401 = token expired: the refresh logic renews it, then the outbox retries.
                code <= 0 || code == 401 || code == 408 || code == 429 || code >= 500 -> SendResult.Offline
                else -> SendResult.Rejected(code, try {
                    JSONObject(response.second).optString("detail").ifEmpty { null }
                } catch (_: Exception) { null })
            }
        } catch (_: Exception) {
            SendResult.Offline
        }
    }

    fun sendMessage(roomId: String, content: String): ChatMessage? {
        val authToken = token() ?: return null
        return try {
            val payload = JSONObject().put("content", content).toString()
            var response = transport.execute(
                "POST", "${base()}/chat/rooms/$roomId/messages", authHeaders(json = true), payload,
            )
            if (response.first == 404) {
                response = transport.execute(
                    "POST", "${base()}/api/v1/chat/rooms/$roomId/messages", authHeaders(json = true), payload,
                )
            }
            if (response.first != 200) return null
            val body = JSONObject(response.second)
            val raw = if (body.has("data") && !body.isNull("data")) body.get("data") else body
            if (raw !is JSONObject) return null
            ChatMessage.fromApi(deepMap(raw))
        } catch (_: Exception) {
            null
        }
    }

    /** editMessage: PUT /chat/rooms/<id>/messages/<msgId> {content} (+ fallback). */
    fun editMessage(roomId: String, messageId: String, newText: String) {
        if (token() == null) return
        try {
            val payload = JSONObject().put("content", newText).toString()
            val response = transport.execute(
                "PUT", "${base()}/chat/rooms/$roomId/messages/$messageId", authHeaders(json = true), payload,
            )
            if (response.first == 404) {
                transport.execute(
                    "PUT", "${base()}/api/v1/chat/rooms/$roomId/messages/$messageId", authHeaders(json = true), payload,
                )
            }
        } catch (_: Exception) {
        }
    }

    /** deleteMessage: DELETE /chat/rooms/<id>/messages/<msgId> (+ fallback). */
    fun deleteMessage(roomId: String, messageId: String) {
        if (token() == null) return
        try {
            val response = transport.execute(
                "DELETE", "${base()}/chat/rooms/$roomId/messages/$messageId", authHeaders(), null,
            )
            if (response.first == 404) {
                transport.execute(
                    "DELETE", "${base()}/api/v1/chat/rooms/$roomId/messages/$messageId", authHeaders(), null,
                )
            }
        } catch (_: Exception) {
        }
    }

    /** clearConversationMessages: DELETE /chat/rooms/<id>/messages (+ fallback). */
    fun clearMessages(roomId: String) {
        if (token() == null) return
        try {
            val response = transport.execute(
                "DELETE", "${base()}/chat/rooms/$roomId/messages", authHeaders(), null,
            )
            if (response.first == 404) {
                transport.execute(
                    "DELETE", "${base()}/api/v1/chat/rooms/$roomId/messages", authHeaders(), null,
                )
            }
        } catch (_: Exception) {
        }
    }

    /** POST /chat/rooms/<id>/read (+ /api/v1 fallback): marks everything the
     *  server has for this user in the room as read (clears the unread count and,
     *  per the reader's server-side privacy settings, sends blue ticks). Returns
     *  the ids the server marked, or null on failure. */
    /** POST /chat/rooms/<id>/delivered (+ /api/v1 fallback): messages reached this device
     *  without the WebSocket (push). Returns true when the server accepted it. */
    fun markRoomDelivered(roomId: String): Boolean {
        if (token() == null) return false
        return try {
            var response = transport.execute("POST", "${base()}/chat/rooms/$roomId/delivered", authHeaders(json = true), "{}")
            if (response.first == 404) {
                response = transport.execute("POST", "${base()}/api/v1/chat/rooms/$roomId/delivered", authHeaders(json = true), "{}")
            }
            response.first in 200..299
        } catch (_: Exception) {
            false
        }
    }

    fun markRoomRead(roomId: String): List<String>? {
        if (token() == null) return null
        return try {
            var response = transport.execute("POST", "${base()}/chat/rooms/$roomId/read", authHeaders(json = true), "{}")
            if (response.first == 404) {
                response = transport.execute("POST", "${base()}/api/v1/chat/rooms/$roomId/read", authHeaders(json = true), "{}")
            }
            if (response.first != 200) return null
            val data = JSONObject(response.second).optJSONObject("data") ?: return emptyList()
            val ids = data.optJSONArray("message_ids") ?: return emptyList()
            (0 until ids.length()).map { ids.optString(it, "") }.filter { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    /** PATCH /chat/settings (+ /api/v1 fallback) with the privacy fields the
     *  server enforces (last seen / online audience, read receipts, typing).
     *  Returns true when the server accepted the update. */
    fun updateChatSettings(settings: Map<String, Any?>): Boolean {
        if (token() == null) return false
        val body = JSONObject()
        for ((k, v) in settings) body.put(k, v ?: JSONObject.NULL)
        return try {
            var response = transport.execute("PATCH", "${base()}/chat/settings", authHeaders(json = true), body.toString())
            if (response.first == 404) {
                response = transport.execute("PATCH", "${base()}/api/v1/chat/settings", authHeaders(json = true), body.toString())
            }
            response.first in 200..299
        } catch (_: Exception) {
            false
        }
    }

    /** Thrown for HTTP 402 on subscription-gated chat writes; Dart sets
     *  requiresSubscription and surfaces this exact message. */
    class SubscriptionRequiredException(message: String) : Exception(message)

    /** startDirectChat: POST /chat/rooms/direct (NO /api/v1 fallback in the
     *  Dart source). 200 -> Conversation.fromApi(data ?: body). 402 ->
     *  SubscriptionRequiredException with the exact enrollment message.
     *  Other statuses -> "Failed to start chat (code)". */
    fun createDirectRoom(targetUserId: String): Conversation {
        if (token() == null) throw Exception("Not authenticated")
        val response = transport.execute(
            "POST", "${base()}/chat/rooms/direct", authHeaders(json = true),
            JSONObject().put("target_user_id", targetUserId).toString(),
        )
        return when (response.first) {
            200 -> {
                val body = JSONObject(response.second)
                Conversation.fromApi(
                    deep(body.optJSONObject("data") ?: body) as Map<String, Any?>
                )
            }
            402 -> throw SubscriptionRequiredException(
                "No active subscription plan found. Please enroll in a product plan to continue."
            )
            else -> throw Exception("Failed to start chat (${response.first})")
        }
    }

    /** createGroup: POST /chat/rooms/group (+ /api/v1 fallback on 404).
     *  Network failure -> "Failed to connect to chat server: <e>". */
    fun createGroupRoom(name: String, memberUserIds: List<String>): Conversation {
        if (token() == null) throw Exception("Not authenticated")
        val body = JSONObject()
            .put("name", name.trim())
            .put("member_user_ids", JSONArray(memberUserIds))
        val response = try {
            val r = transport.execute("POST", "${base()}/chat/rooms/group", authHeaders(json = true), body.toString())
            if (r.first == 404) {
                transport.execute("POST", "${base()}/api/v1/chat/rooms/group", authHeaders(json = true), body.toString())
            } else r
        } catch (e: Exception) {
            throw Exception("Failed to connect to chat server: $e")
        }
        return when (response.first) {
            200 -> {
                val parsed = JSONObject(response.second)
                Conversation.fromApi(
                    deep(parsed.optJSONObject("data") ?: parsed) as Map<String, Any?>
                )
            }
            else -> throw Exception("Failed to create group (${response.first})")
        }
    }

    /** deleteUser: DELETE /app-users/<id> (+ fallback); errors swallowed
     *  (Dart debugPrint only). Local-state removal and the self-sign-out
     *  rule live on the owning service. */
    fun deleteUser(userId: String) {
        if (token() == null) return
        try {
            val response = transport.execute("DELETE", "${base()}/app-users/$userId", authHeaders(), null)
            if (response.first == 404) {
                transport.execute("DELETE", "${base()}/api/v1/app-users/$userId", authHeaders(), null)
            }
        } catch (_: Exception) {
        }
    }

    /** deleteConversation: DELETE /chat/rooms/<id> (+ fallback); errors
     *  swallowed. Local-state removal lives on the owning service. */
    fun deleteRoom(roomId: String) {
        if (token() == null) return
        try {
            val response = transport.execute("DELETE", "${base()}/chat/rooms/$roomId", authHeaders(), null)
            if (response.first == 404) {
                transport.execute("DELETE", "${base()}/api/v1/chat/rooms/$roomId", authHeaders(), null)
            }
        } catch (_: Exception) {
        }
    }

    /** loadUsers: GET /chat/users[?search=] with /api/v1 fallback; on network
     *  error or 404 falls back to /app-users (then /api/v1/app-users). */
    fun listUsers(query: String?): List<UserProfile>? {
        if (token() == null) return null
        val qParam = if (!query.isNullOrBlank()) "?search=${java.net.URLEncoder.encode(query.trim(), "UTF-8")}" else ""
        var response: Pair<Int, String>
        try {
            response = getWithFallback("chat/users$qParam")
        } catch (_: Exception) {
            response = try {
                transport.execute("GET", "${base()}/app-users$qParam", authHeaders(), null)
            } catch (_: Exception) {
                return null
            }
        }
        if (response.first == 404) {
            response = try {
                transport.execute("GET", "${base()}/app-users$qParam", authHeaders(), null)
            } catch (_: Exception) {
                return null
            }
            if (response.first == 404) {
                response = try {
                    transport.execute("GET", "${base()}/api/v1/app-users$qParam", authHeaders(), null)
                } catch (_: Exception) {
                    return null
                }
            }
        }
        if (response.first != 200) return null
        return try {
            val trimmed = response.second.trim()
            val list: JSONArray = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                else -> {
                    val body = JSONObject(trimmed)
                    if (body.has("data") && body.get("data") is JSONArray) body.getJSONArray("data")
                    else JSONArray()
                }
            }
            val users = mutableListOf<UserProfile>()
            for (i in 0 until list.length()) {
                users.add(userFromApi(deepMap(list.getJSONObject(i))))
            }
            users
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /** Deep org.json -> Kotlin collections (JSONObject/JSONArray unwrapped). */
        fun deepMap(obj: JSONObject): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            for (key in obj.keys()) out[key] = deep(obj.get(key))
            return out
        }

        private fun deep(value: Any?): Any? = when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> deepMap(value)
            is JSONArray -> (0 until value.length()).map { deep(value.get(it)) }
            else -> value
        }

        /** Exact port of the loadUsers per-user mapping (api_backend_service.dart). */
        fun userFromApi(json: Map<String, Any?>): UserProfile {
            val uid = (json["uid"] ?: json["id"] ?: "").toString()
            val fullName = (
                json["full_name"]
                    ?: json["displayName"]
                    ?: "${json["first_name"] ?: ""} ${json["last_name"] ?: ""}".trim()
                ).toString()
            val username = (json["username"] ?: "").toString()
            val displayName = if (fullName.isNotEmpty()) fullName else username.ifEmpty { "User" }
            val initials = if (displayName.isNotEmpty()) {
                displayName.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    .map { it[0] }.take(2).joinToString("").uppercase()
            } else "U"
            val publicProfileCamel = json["publicProfile"] as? Map<*, *>
            val publicProfileSnake = json["public_profile"] as? Map<*, *>
            val photo = (
                json["photo"]
                    ?: json["avatar_url"]
                    ?: json["avatarUrl"]
                    ?: publicProfileCamel?.get("avatarUrl")
                )?.toString()
            val about = when {
                json["about"] != null && json["about"].toString().trim().isNotEmpty() ->
                    json["about"].toString().trim()
                publicProfileSnake?.get("about") != null &&
                    publicProfileSnake["about"].toString().trim().isNotEmpty() ->
                    publicProfileSnake["about"].toString().trim()
                publicProfileCamel?.get("about") != null &&
                    publicProfileCamel["about"].toString().trim().isNotEmpty() ->
                    publicProfileCamel["about"].toString().trim()
                json["designation"] != null && json["designation"].toString().trim().isNotEmpty() ->
                    json["designation"].toString().trim()
                else -> ""
            }
            return UserProfile(
                id = uid,
                displayName = displayName,
                username = username,
                avatarInitials = initials,
                avatarColorHex = "0xFF6366F1",
                about = about,
                // backend_python /chat/users returns is_online + last_seen; older payloads have
                // neither, so those keep the previous "online now" default.
                presence = when (val online = json["is_online"]) {
                    null -> PresenceState.online
                    true, "true" -> PresenceState.online
                    else -> PresenceState.offline
                },
                lastSeenAt = (json["last_seen"] ?: json["lastSeen"])?.toString()
                    ?.let { v -> try { Instant.parse(v) } catch (_: Exception) {
                        try { java.time.OffsetDateTime.parse(v).toInstant() } catch (_: Exception) {
                            try { java.time.LocalDateTime.parse(v).toInstant(java.time.ZoneOffset.UTC) } catch (_: Exception) { null }
                        }
                    } }
                    ?: Instant.now(),
                isVerified = false,
                email = (json["email"] ?: "").toString(),
                phone = (json["mobile"] ?: json["phone"] ?: "").toString(),
                safetyNumber = "",
                avatarUrl = photo,
            )
        }
    }
}
