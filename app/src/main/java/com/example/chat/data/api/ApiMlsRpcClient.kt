package com.example.chat.data.api

import com.example.chat.data.messaging.SupabaseRpc
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** SupabaseRpc implementation backed by the Chaty backend's /e2ee routes.
 *
 *  D1 (Supabase retirement): the MLS key services and the encrypted message
 *  pipeline used to call Supabase RPCs; this client maps those exact
 *  function names to the replacement HTTP endpoints and unwraps the
 *  APIResponse envelope, so MlsE2eeService, SecureMessageTransport and the
 *  Rust MLS engine need no changes. Only the allowlisted encrypted-plane
 *  functions are accepted - any other name fails closed instead of silently
 *  leaving the encrypted plane.
 *
 *  Error text keeps the server's detail/message so the service's string
 *  matching (group-race catch-up, setup-pending detection) keeps working. */
class ApiMlsRpcClient(
    private val baseUrl: () -> String,
    private val tenantId: () -> String,
    private val token: suspend () -> String?,
    private val transport: ApiHttpTransport,
) : SupabaseRpc {

    private fun endpointFor(function: String): String = when (function) {
        "register_mls_device_v1" -> "e2ee/devices/register"
        "claim_mls_conversation_key_packages_v1" -> "e2ee/key-packages/claim"
        "publish_mls_group_v1" -> "e2ee/groups/publish"
        "ack_mls_welcome_v1" -> "e2ee/welcomes/ack"
        "publish_mls_membership_update_v1" -> "e2ee/groups/membership"
        "get_mls_conversation_state_v1" -> "e2ee/groups/state"
        "send_mls_message_v1" -> "e2ee/messages/send"
        "edit_mls_message_v1" -> "e2ee/messages/edit"
        "delete_chat_message" -> "e2ee/messages/delete"
        // D1: the reads the retired Supabase PostgREST client served.
        "get_conversation_messages" -> "e2ee/messages/list"
        "get_my_conversations" -> "e2ee/conversations/list"
        else -> throw IllegalArgumentException("Not an encrypted-plane backend function: $function")
    }

    /** p_* parameter names become plain JSON fields (p_device_id -> device_id). */
    private fun bodyFor(params: Map<String, Any?>): String {
        val body = JSONObject()
        for ((key, value) in params) {
            val field = key.removePrefix("p_")
            body.put(field, toJson(value))
        }
        return body.toString()
    }

    private fun toJson(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> {
            val obj = JSONObject()
            for ((k, v) in value) obj.put(k.toString(), toJson(v))
            obj
        }
        is List<*> -> {
            val arr = JSONArray()
            for (item in value) arr.put(toJson(item))
            arr
        }
        is Boolean, is Number, is String -> value
        else -> value.toString()
    }

    override suspend fun rpc(function: String, params: Map<String, Any?>): Any? =
        withContext(Dispatchers.IO) {
            if (function == "search_profiles") return@withContext searchProfiles(params)
            // ConversationsRepository's legacy rpc names that map onto the
            // REST chat plane (the methods endpointFor's e2ee table cannot
            // express: non-POST verbs, ids in the path).
            when (function) {
                "delete_conversation" -> return@withContext deleteConversation(params)
                "mark_conversation_read" -> return@withContext markConversationRead(params)
                "get_conversation_members" -> return@withContext conversationMembers(params)
            }
            val path = endpointFor(function)
            val headers = mutableMapOf(
                "Content-Type" to "application/json",
                "x-tenant-id" to tenantId(),
            )
            token()?.let { headers["Authorization"] = "Bearer $it" }

            val base = baseUrl().trimEnd('/')
            var response = transport.execute("POST", "$base/$path", headers, bodyFor(params))
            if (response.first == 404) {
                // Same dual-mount fallback convention as the other API clients.
                response = transport.execute("POST", "$base/api/v1/$path", headers, bodyFor(params))
            }

            val text = response.second
            if (response.first !in 200..299) {
                val message = runCatching {
                    val parsed = JSONObject(text)
                    val err = parsed.optJSONObject("error")
                    when {
                        parsed.has("detail") -> parsed.optString("detail")
                        err != null -> err.optString("message")
                        else -> parsed.optString("message")
                    }
                }.getOrDefault("")
                throw IOException(
                    if (message.isNotEmpty()) message
                    else "MLS backend call $function failed with HTTP ${response.first}"
                )
            }
            if (text.isBlank()) return@withContext null
            val parsed = JSONObject(text)
            val data = if (parsed.has("data")) parsed.get("data") else parsed
            fromJson(data)
        }

    /** GET /app-users?q= — the backend's profile search (replaces the
     *  Supabase search_profiles RPC under D1). Same row shape the repository
     *  hydrates with profileFromRow. */
    private suspend fun headers(): MutableMap<String, String> {
        val headers = mutableMapOf("Content-Type" to "application/json", "x-tenant-id" to tenantId())
        token()?.let { headers["Authorization"] = "Bearer $it" }
        return headers
    }

    private suspend fun executeWithFallback(method: String, path: String, body: String?): Pair<Int, String> {
        val base = baseUrl().trimEnd('/')
        var response = transport.execute(method, "$base/$path", headers(), body)
        if (response.first == 404) {
            response = transport.execute(method, "$base/api/v1/$path", headers(), body)
        }
        if (response.first !in 200..299) {
            throw IllegalStateException("$method $path failed (${response.first})")
        }
        return response
    }

    /** delete_conversation -> DELETE /chat/rooms/{id}. */
    private suspend fun deleteConversation(params: Map<String, Any?>): Any? {
        val id = ApiPathSegments.safe(params["p_conversation_id"]?.toString() ?: "")
            ?: throw IllegalArgumentException("Invalid conversation identifier.")
        executeWithFallback("DELETE", "chat/rooms/$id", null)
        return null
    }

    /** mark_conversation_read -> POST /chat/rooms/{id}/read. */
    private suspend fun markConversationRead(params: Map<String, Any?>): Any? {
        val id = ApiPathSegments.safe(params["p_conversation_id"]?.toString() ?: "")
            ?: throw IllegalArgumentException("Invalid conversation identifier.")
        executeWithFallback("POST", "chat/rooms/$id/read", "{}")
        return null
    }

    /** get_conversation_members -> GET /chat/rooms/{id}/members; rows are
     *  shaped for ConversationsRepository.profileFromRow (id/display_name/
     *  username). */
    private suspend fun conversationMembers(params: Map<String, Any?>): Any? {
        val id = ApiPathSegments.safe(params["p_conversation_id"]?.toString() ?: "")
            ?: throw IllegalArgumentException("Invalid conversation identifier.")
        val response = executeWithFallback("GET", "chat/rooms/$id/members", null)
        val data = JSONObject(response.second).optJSONObject("data") ?: return emptyList<Map<String, Any?>>()
        val members = data.optJSONArray("members") ?: return emptyList<Map<String, Any?>>()
        val rows = mutableListOf<Map<String, Any?>>()
        for (i in 0 until members.length()) {
            val m = members.optJSONObject(i) ?: continue
            rows.add(mapOf(
                "id" to m.optString("user_id"),
                "username" to m.optString("username"),
                "display_name" to m.optString("display_name"),
                "role" to m.optString("role"),
                "status" to m.optString("status"),
            ))
        }
        return rows
    }

    private suspend fun searchProfiles(params: Map<String, Any?>): Any? {
        val query = params["p_query"]?.toString()?.trim().orEmpty()
        val headers = mutableMapOf("x-tenant-id" to tenantId())
        token()?.let { headers["Authorization"] = "Bearer $it" }
        val base = baseUrl().trimEnd('/')
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        var response = transport.execute("GET", "$base/app-users?q=$encoded", headers, null)
        if (response.first == 404) {
            response = transport.execute("GET", "$base/api/v1/app-users?q=$encoded", headers, null)
        }
        if (response.first !in 200..299) {
            throw IOException("Profile search failed with HTTP ${response.first}")
        }
        if (response.second.isBlank()) return emptyList<Any?>()
        val parsed = JSONObject(response.second)
        val data = if (parsed.has("data")) parsed.get("data") else return emptyList<Any?>()
        return fromJson(data)
    }

    private fun fromJson(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> {
            val out = mutableMapOf<String, Any?>()
            for (key in value.keys()) out[key] = fromJson(value.get(key))
            out
        }
        is JSONArray -> (0 until value.length()).map { fromJson(value.get(it)) }
        else -> value
    }
}
