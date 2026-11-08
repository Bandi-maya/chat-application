package com.example.chat.data.api

import org.json.JSONObject

/** Call history client (research 5.4): the Calls tab reads GET
 *  /calls/history instead of the dead Supabase service. Deletes are
 *  per-viewer: swipe-to-delete one entry, "Clear call log" for all. */
class ApiCallHistoryClient(
    private val baseUrl: () -> String,
    private val tenantId: () -> String,
    private val transport: ApiHttpTransport,
    private val token: () -> String? = { null },
) {
    data class CallEntry(
        val id: String,
        val callId: String,
        /** outgoing | incoming (derived per viewer by the server). */
        val direction: String,
        val otherUserId: String,
        val otherUsername: String?,
        val otherDisplayName: String?,
        /** audio | video */
        val kind: String,
        /** answered | missed | declined | failed */
        val status: String,
        val startedAt: String?,
        val durationSec: Int,
    )

    data class CallHistoryPage(
        val entries: List<CallEntry>,
        val nextCursor: String?,
    )

    class PlaneUnavailable : Exception("This server doesn't have call history yet.")

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
        var response = try {
            transport.execute(method, "$base/$path", headers(), body)
        } catch (e: Exception) {
            throw Exception("Unable to reach server. Please check your internet connection.")
        }
        if (response.first == 404) {
            response = try {
                transport.execute(method, "$base/api/v1/$path", headers(), body)
            } catch (e: Exception) {
                throw Exception("Unable to reach server. Please check your internet connection.")
            }
        }
        // A bare "Not Found" 404 on BOTH roots means the route is missing
        // (server predates the plane); a 404 with a specific detail is an
        // entity miss (e.g. deleting an already-gone entry).
        if (response.first == 404 && isBareRouteMiss(response.second)) throw PlaneUnavailable()
        return response
    }

    private fun isBareRouteMiss(body: String): Boolean = try {
        val parsed = JSONObject(body)
        parsed.optString("detail") == "Not Found" && parsed.length() == 1
    } catch (_: Exception) {
        body.isEmpty()
    }

    /** GET /calls/history?limit=&before= - paged, both directions. */
    fun history(limit: Int = 50, before: String? = null): CallHistoryPage {
        var path = "calls/history?limit=$limit"
        if (before != null) path += "&before=" + java.net.URLEncoder.encode(before, "UTF-8")
        val response = execute("GET", path, null)
        if (response.first != 200) {
            var msg = "Request failed (${response.first})"
            try {
                val parsed = JSONObject(response.second)
                msg = parsed.optString("detail").ifEmpty { parsed.optString("message").ifEmpty { msg } }
            } catch (_: Exception) {
            }
            throw Exception(msg)
        }
        val data = JSONObject(response.second).optJSONObject("data") ?: return CallHistoryPage(emptyList(), null)
        val arr = data.optJSONArray("entries")
        val entries = mutableListOf<CallEntry>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val raw = arr.getJSONObject(i)
                val other = raw.optJSONObject("other_user")
                entries.add(
                    CallEntry(
                        id = raw.optString("id"),
                        callId = raw.optString("call_id"),
                        direction = raw.optString("direction", "incoming"),
                        otherUserId = raw.optString("other_user_id"),
                        otherUsername = other?.let { if (it.isNull("username")) null else it.optString("username") },
                        otherDisplayName = other?.let { if (it.isNull("display_name")) null else it.optString("display_name") },
                        kind = raw.optString("kind", "audio"),
                        status = raw.optString("status", "missed"),
                        startedAt = if (raw.isNull("started_at")) null else raw.optString("started_at"),
                        durationSec = raw.optInt("duration_sec", 0),
                    )
                )
            }
        }
        return CallHistoryPage(
            entries = entries,
            nextCursor = if (data.isNull("next_cursor")) null else data.optString("next_cursor"),
        )
    }

    /** DELETE /calls/history/{id}: per-viewer - hides it for you only.
     *  A specific-detail 404 means the entry is already gone: that's fine. */
    fun deleteEntry(entryId: String) {
        val response = execute("DELETE", "calls/history/$entryId", null)
        if (response.first != 200 && response.first != 404) {
            var msg = "Request failed (${response.first})"
            try {
                val parsed = JSONObject(response.second)
                msg = parsed.optString("detail").ifEmpty { parsed.optString("message").ifEmpty { msg } }
            } catch (_: Exception) {
            }
            throw Exception(msg)
        }
    }

    /** DELETE /calls/history/clear: the whole log, for you only. */
    fun clear() {
        val response = execute("DELETE", "calls/history/clear", null)
        if (response.first != 200) {
            var msg = "Request failed (${response.first})"
            try {
                val parsed = JSONObject(response.second)
                msg = parsed.optString("detail").ifEmpty { parsed.optString("message").ifEmpty { msg } }
            } catch (_: Exception) {
            }
            throw Exception(msg)
        }
    }
}
