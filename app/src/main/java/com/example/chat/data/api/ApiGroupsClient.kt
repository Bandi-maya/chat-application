package com.example.chat.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Groups + status + disappearing-timer wire client (matrices 9/10/17):
 *  member management, roles, invite links, status posts, per-chat timer.
 *  Same conventions as ApiMlsRpcClient: APIResponse envelope unwrap, dual
 *  /api/v1 fallback, server detail surfaced, JVM-testable transport seam. */
class ApiGroupsClient(
    private val baseUrl: () -> String,
    private val tenantId: () -> String,
    private val token: suspend () -> String?,
    private val transport: ApiHttpTransport,
) {
    private suspend fun call(method: String, path: String, body: JSONObject?): JSONObject =
        withContext(Dispatchers.IO) {
            val headers = mutableMapOf("x-tenant-id" to tenantId())
            token()?.let { headers["Authorization"] = "Bearer $it" }
            body?.let { headers["Content-Type"] = "application/json" }
            val url = "${baseUrl().trimEnd('/')}/$path"
            var (code, text) = transport.execute(method, url, headers, body?.toString())
            if (code == 404) {
                val retry = transport.execute(method, "${baseUrl().trimEnd('/')}/api/v1/$path", headers, body?.toString())
                code = retry.first; text = retry.second
            }
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(text).optString("detail") }.getOrDefault("")
                throw java.io.IOException(detail.ifEmpty { "Request failed (HTTP $code)" })
            }
            JSONObject(text)
        }

    private fun data(resp: JSONObject): JSONObject =
        if (resp.opt("data") is JSONObject) resp.getJSONObject("data") else JSONObject()

    // ── members ────────────────────────────────────────────────────────

    suspend fun listMembers(roomId: String): JSONObject =
        data(call("GET", "chat/rooms/$roomId/members", null))

    suspend fun addMember(roomId: String, userId: String): JSONObject =
        data(call("POST", "chat/rooms/$roomId/members", JSONObject().put("user_id", userId)))

    suspend fun removeMember(roomId: String, userId: String): JSONObject =
        data(call("DELETE", "chat/rooms/$roomId/members/$userId", null))

    suspend fun leave(roomId: String): JSONObject =
        data(call("POST", "chat/rooms/$roomId/leave", JSONObject()))

    suspend fun setRole(roomId: String, userId: String, role: String): JSONObject =
        data(call("PATCH", "chat/rooms/$roomId/members/$userId/role", JSONObject().put("role", role)))

    suspend fun approveJoin(roomId: String, userId: String): JSONObject =
        data(call("POST", "chat/rooms/$roomId/members/$userId/approve", JSONObject()))

    // ── settings + invites ─────────────────────────────────────────────

    suspend fun updateSettings(roomId: String, joinRequiresApproval: Boolean? = null,
                               permissions: JSONObject? = null): JSONObject {
        val body = JSONObject()
        joinRequiresApproval?.let { body.put("join_requires_approval", it) }
        permissions?.let { body.put("permissions", it) }
        return data(call("PATCH", "chat/rooms/$roomId/settings", body))
    }

    suspend fun createInvite(roomId: String, maxUses: Int? = null): JSONObject {
        val body = JSONObject()
        maxUses?.let { body.put("max_uses", it) }
        return data(call("POST", "chat/rooms/$roomId/invites", body))
    }

    suspend fun revokeInvite(roomId: String, token: String): JSONObject =
        data(call("DELETE", "chat/rooms/$roomId/invites/$token", null))

    suspend fun joinViaInvite(token: String): JSONObject =
        data(call("POST", "chat/invites/$token/join", JSONObject()))

    // ── disappearing timer (matrix 17) ─────────────────────────────────

    suspend fun setDisappearTimer(roomId: String, seconds: Int?): JSONObject =
        data(call("PATCH", "chat/rooms/$roomId/disappear",
            JSONObject().put("seconds", seconds ?: JSONObject.NULL)))

    // ── status (matrix 10) ─────────────────────────────────────────────

    suspend fun postStatus(payload: String, audienceMode: String = "all",
                           audienceIds: List<String>? = null): JSONObject {
        val body = JSONObject().put("payload", payload).put("audience_mode", audienceMode)
        audienceIds?.let { body.put("audience_ids", JSONArray(it)) }
        return data(call("POST", "status/post", body))
    }

    suspend fun statusFeed(): JSONObject =
        data(call("GET", "status/feed", null))

    suspend fun recordStatusView(postId: String): JSONObject =
        data(call("POST", "status/$postId/view", JSONObject()))

    suspend fun statusViews(postId: String): JSONObject =
        data(call("GET", "status/$postId/views", null))

    suspend fun deleteStatus(postId: String): JSONObject =
        data(call("DELETE", "status/$postId", null))
}
