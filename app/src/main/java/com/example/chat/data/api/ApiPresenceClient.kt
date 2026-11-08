package com.example.chat.data.api

import org.json.JSONObject

/** Presence plane client (research 5.2). The server decides what a viewer
 *  may see - this client just renders it, fail-closed: if the server says
 *  nothing (old server, unknown user), the UI shows nothing. */
class ApiPresenceClient(
    private val baseUrl: () -> String,
    private val tenantId: () -> String,
    private val transport: ApiHttpTransport,
    private val token: () -> String? = { null },
) {
    /** Exactly the server's three privacy-filtered shapes, plus Unavailable
     *  for servers that predate the plane (404 on both roots). */
    sealed interface PresenceView {
        data object Online : PresenceView
        data class LastSeen(val at: String?) : PresenceView
        /** Telegram-style bucket: recently | within_week | within_month | long_ago. */
        data class Approx(val bucket: String) : PresenceView
        data object Unavailable : PresenceView
    }

    data class PresencePrivacy(
        val lastSeenPolicy: String,
        val onlinePolicy: String,
        val lastSeenExceptions: List<String>,
    )

    private fun headers(): Map<String, String> {
        val tok = token() ?: throw Exception("Not authenticated")
        return mapOf(
            "Authorization" to "Bearer $tok",
            "Content-Type" to "application/json",
            "x-tenant-id" to tenantId(),
        )
    }

    private fun execute(method: String, path: String, body: String?): Pair<Int, String> {
        val base = baseUrl()
        val response = try {
            transport.execute(method, "$base/$path", headers(), body)
        } catch (e: Exception) {
            throw Exception("Unable to reach server. Please check your internet connection.")
        }
        if (response.first == 404) {
            return try {
                transport.execute(method, "$base/api/v1/$path", headers(), body)
            } catch (e: Exception) {
                throw Exception("Unable to reach server. Please check your internet connection.")
            }
        }
        return response
    }

    /** GET /presence/{user_id}. Fail-closed: 404 (old server OR unknown
     *  user) -> Unavailable, and the header shows nothing. */
    fun presence(userId: String): PresenceView {
        val response = execute("GET", "presence/$userId", null)
        if (response.first == 404) return PresenceView.Unavailable
        if (response.first != 200) return PresenceView.Unavailable
        val data = try {
            JSONObject(response.second).optJSONObject("data") ?: return PresenceView.Unavailable
        } catch (_: Exception) {
            return PresenceView.Unavailable
        }
        return when {
            data.optBoolean("online", false) -> PresenceView.Online
            data.has("last_seen_at") -> PresenceView.LastSeen(
                if (data.isNull("last_seen_at")) null else data.optString("last_seen_at")
            )
            data.has("approx") -> PresenceView.Approx(data.optString("approx", "long_ago"))
            else -> PresenceView.Unavailable
        }
    }

    /** POST /presence/heartbeat - REST presence transport. The websocket is
     *  primary, but proxies that block Upgrade must not leave the client
     *  invisible; the server only believes online for 90s, so the app
     *  heartbeats while foregrounded. Fail-closed: false on any error. */
    fun heartbeat(online: Boolean = true): Boolean {
        val body = JSONObject().put("online", online).toString()
        val response = try {
            execute("POST", "presence/heartbeat", body)
        } catch (_: Exception) {
            return false
        }
        return response.first == 200
    }

    /** GET /privacy/presence. */
    fun privacy(): PresencePrivacy? {
        val response = execute("GET", "privacy/presence", null)
        if (response.first != 200) return null
        val data = JSONObject(response.second).optJSONObject("data") ?: return null
        val exceptions = mutableListOf<String>()
        data.optJSONArray("last_seen_exceptions")?.let { arr ->
            for (i in 0 until arr.length()) exceptions.add(arr.getString(i))
        }
        return PresencePrivacy(
            lastSeenPolicy = data.optString("last_seen_policy", "contacts"),
            onlinePolicy = data.optString("online_policy", "same_as_last_seen"),
            lastSeenExceptions = exceptions,
        )
    }

    /** PUT /privacy/presence. Policies: everyone|contacts|contacts_except|
     *  nobody; online: everyone|same_as_last_seen. Null fields stay unchanged. */
    fun putPrivacy(
        lastSeenPolicy: String? = null,
        onlinePolicy: String? = null,
        lastSeenExceptions: List<String>? = null,
    ): PresencePrivacy? {
        val body = JSONObject()
        if (lastSeenPolicy != null) body.put("last_seen_policy", lastSeenPolicy)
        if (onlinePolicy != null) body.put("online_policy", onlinePolicy)
        if (lastSeenExceptions != null) {
            val arr = org.json.JSONArray()
            lastSeenExceptions.forEach { arr.put(it) }
            body.put("last_seen_exceptions", arr)
        }
        val response = execute("PUT", "privacy/presence", body.toString())
        if (response.first != 200) {
            var msg = "Request failed (${response.first})"
            try {
                val parsed = JSONObject(response.second)
                msg = parsed.optString("detail").ifEmpty { parsed.optString("message").ifEmpty { msg } }
            } catch (_: Exception) {
            }
            throw Exception(msg)
        }
        return privacy()
    }
}
