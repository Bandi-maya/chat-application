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
                presence = PresenceState.online,
                lastSeenAt = Instant.now(),
                isVerified = false,
                email = (json["email"] ?: "").toString(),
                phone = (json["mobile"] ?: json["phone"] ?: "").toString(),
                safetyNumber = "",
                avatarUrl = photo,
            )
        }
    }
}
