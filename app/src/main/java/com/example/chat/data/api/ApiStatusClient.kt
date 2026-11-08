package com.example.chat.data.api

import org.json.JSONArray
import org.json.JSONObject

/** REST status plane of the live backend (app/routers/status.py): the
 *  server stores an opaque client payload per post, enforces audience and
 *  a hard 24h expiry, and tracks views. Same URL, header, and /api/v1
 *  404-fallback conventions as ApiChatClient. Context-free and JVM-testable.
 *
 *  Scope honesty: payloads on this plane are server-readable over TLS. The
 *  payload string is treated as an opaque blob by the server, so a client
 *  that wants encrypted statuses can encrypt before posting; this client
 *  does not encrypt by itself. */
class ApiStatusClient(
    private val baseUrl: () -> String,
    private val tenantId: () -> String,
    private val token: () -> String?,
    private val transport: ApiHttpTransport,
) {
    data class StatusPostEntry(
        val id: String,
        val payload: String,
        val createdAt: String?,
        val expiresAt: String?,
    )

    data class StatusFeedEntry(
        val userId: String,
        val username: String,
        val displayName: String,
        val own: Boolean,
        val posts: List<StatusPostEntry>,
    )

    data class StatusViewEntry(
        val userId: String,
        val username: String,
        val displayName: String,
        val viewedAt: String?,
    )

    private fun base() = baseUrl().trimEnd('/')

    private fun authHeaders(json: Boolean = false): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        token()?.let { headers["Authorization"] = "Bearer $it" }
        headers["x-tenant-id"] = tenantId()
        if (json) headers["Content-Type"] = "application/json"
        return headers
    }

    private fun execute(method: String, path: String, body: String? = null): Pair<Int, String> {
        val headers = authHeaders(json = body != null)
        val response = transport.execute(method, "${base()}/$path", headers, body)
        if (response.first == 404) {
            return transport.execute(method, "${base()}/api/v1/$path", headers, body)
        }
        return response
    }

    /** POST /status/post -> {id, expires_at}, or null on any non-200. */
    fun postStatus(payload: String, audienceMode: String = "all", audienceIds: List<String>? = null): Pair<String, String?>? {
        val body = JSONObject()
            .put("payload", payload)
            .put("audience_mode", audienceMode)
        if (audienceIds != null) body.put("audience_ids", JSONArray(audienceIds))
        val (code, text) = execute("POST", "status/post", body.toString())
        if (code !in 200..299) return null
        val data = runCatching { JSONObject(text).getJSONObject("data") }.getOrNull() ?: return null
        val id = data.optString("id").takeIf { it.isNotEmpty() } ?: return null
        return id to data.optString("expires_at").takeIf { it.isNotEmpty() }
    }

    /** GET /status/feed -> grouped statuses (others first, own last). */
    fun feed(): List<StatusFeedEntry>? {
        val (code, text) = execute("GET", "status/feed")
        if (code !in 200..299) return null
        val data = runCatching { JSONObject(text).getJSONObject("data") }.getOrNull() ?: return null
        val statuses = data.optJSONArray("statuses") ?: return emptyList()
        val out = mutableListOf<StatusFeedEntry>()
        for (i in 0 until statuses.length()) {
            val e = statuses.getJSONObject(i)
            val posts = mutableListOf<StatusPostEntry>()
            val arr = e.optJSONArray("posts") ?: JSONArray()
            for (j in 0 until arr.length()) {
                val p = arr.getJSONObject(j)
                posts.add(
                    StatusPostEntry(
                        id = p.optString("id"),
                        payload = p.optString("payload"),
                        createdAt = p.optString("created_at").takeIf { it.isNotEmpty() && it != "null" },
                        expiresAt = p.optString("expires_at").takeIf { it.isNotEmpty() && it != "null" },
                    ),
                )
            }
            out.add(
                StatusFeedEntry(
                    userId = e.optString("user_id"),
                    username = e.optString("username"),
                    displayName = e.optString("display_name"),
                    own = e.optBoolean("own"),
                    posts = posts,
                ),
            )
        }
        return out
    }

    /** POST /status/{id}/view. Own posts and unknown ids are not errors for
     *  the caller: viewing is best-effort publication, never blocking. */
    fun recordView(postId: String): Boolean {
        val id = ApiPathSegments.safe(postId) ?: return false
        val (code, _) = execute("POST", "status/$id/view")
        return code in 200..299
    }

    /** GET /status/{id}/views (owner only). */
    fun views(postId: String): List<StatusViewEntry>? {
        val id = ApiPathSegments.safe(postId) ?: return null
        val (code, text) = execute("GET", "status/$id/views")
        if (code !in 200..299) return null
        val data = runCatching { JSONObject(text).getJSONObject("data") }.getOrNull() ?: return null
        val viewers = data.optJSONArray("viewers") ?: return emptyList()
        val out = mutableListOf<StatusViewEntry>()
        for (i in 0 until viewers.length()) {
            val v = viewers.getJSONObject(i)
            out.add(
                StatusViewEntry(
                    userId = v.optString("user_id"),
                    username = v.optString("username"),
                    displayName = v.optString("display_name"),
                    viewedAt = v.optString("viewed_at").takeIf { it.isNotEmpty() && it != "null" },
                ),
            )
        }
        return out
    }

    /** DELETE /status/{id} (owner only server-side). */
    fun delete(postId: String): Boolean {
        val id = ApiPathSegments.safe(postId) ?: return false
        val (code, _) = execute("DELETE", "status/$id")
        return code in 200..299
    }
}
