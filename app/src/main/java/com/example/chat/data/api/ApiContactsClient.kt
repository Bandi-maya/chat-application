package com.example.chat.data.api

import org.json.JSONObject

/** Contacts plane client (research 5.3): username + mutual-acceptance
 *  requests, the contact list, silent blocking, and Who Can Call Me
 *  (GET/PUT /privacy/call-policy). Every call falls back to /api/v1 on 404;
 *  a second 404 means the server predates the plane and callers get a
 *  fail-closed "unavailable" rather than a crash. */
class ApiContactsClient(
    private val baseUrl: () -> String,
    private val tenantId: () -> String,
    private val transport: ApiHttpTransport,
    private val token: () -> String? = { null },
) {
    data class ContactEntry(
        val userId: String,
        val username: String,
        val displayName: String,
        val about: String,
        val avatarUrl: String?,
    )

    data class ContactRequestItem(
        val requestId: String,
        val from: ContactEntry,
    )

    data class BlockedEntry(
        val userId: String,
        val username: String,
        val displayName: String,
        val blockedAt: String?,
    )

    data class CallPolicy(
        val policy: String,
        val exceptions: List<String>,
    )

    /** Plane-missing marker: both roots 404'd. Callers fail closed. */
    class PlaneUnavailable : Exception("This server doesn't have the contacts plane yet.")

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
        // A bare "Not Found" 404 on BOTH roots means the route itself is
        // missing (server predates the plane); entity 404s carry their own
        // detail ("No account with that username.", "Not blocked.", ...).
        if (response.first == 404 && isBareRouteMiss(response.second)) throw PlaneUnavailable()
        return response
    }

    private fun isBareRouteMiss(body: String): Boolean = try {
        val parsed = JSONObject(body)
        parsed.optString("detail") == "Not Found" && parsed.length() == 1
    } catch (_: Exception) {
        body.isEmpty()
    }

    private fun errorOf(response: Pair<Int, String>): String {
        var msg = "Request failed (${response.first})"
        try {
            val parsed = JSONObject(response.second)
            msg = parsed.optString("detail").ifEmpty { parsed.optString("message").ifEmpty { msg } }
        } catch (_: Exception) {
        }
        return msg
    }

    private fun parseEntry(raw: JSONObject): ContactEntry = ContactEntry(
        userId = raw.optString("id"),
        username = raw.optString("username"),
        displayName = raw.optString("display_name").ifEmpty { raw.optString("username") },
        about = raw.optString("about"),
        avatarUrl = if (raw.isNull("avatar_url")) null else raw.optString("avatar_url"),
    )

    /** POST /contacts/request. Returns "pending" or "accepted" (mutual intent
     *  auto-accepts). Silent block surfaces as the same 404 the unknown-user
     *  case gets. */
    fun sendRequest(username: String): String {
        val body = JSONObject().put("username", username.trim()).toString()
        val response = execute("POST", "contacts/request", body)
        when (response.first) {
            200 -> {
                val data = JSONObject(response.second).optJSONObject("data") ?: JSONObject()
                return data.optString("status", "pending")
            }
            404 -> throw Exception("No account with that username.")
            409 -> throw Exception("You're already contacts.")
            else -> throw Exception(errorOf(response))
        }
    }

    /** GET /contacts/requests: the incoming-requests inbox. */
    fun inbox(): List<ContactRequestItem> {
        val response = execute("GET", "contacts/requests", null)
        if (response.first != 200) throw Exception(errorOf(response))
        val data = JSONObject(response.second).optJSONArray("data") ?: return emptyList()
        val out = mutableListOf<ContactRequestItem>()
        for (i in 0 until data.length()) {
            val raw = data.getJSONObject(i)
            val from = raw.optJSONObject("from") ?: continue
            out.add(ContactRequestItem(requestId = raw.optString("request_id"), from = parseEntry(from)))
        }
        return out
    }

    /** POST /contacts/respond {request_id, action: accept|decline}. */
    fun respond(requestId: String, action: String): String {
        val body = JSONObject().put("request_id", requestId).put("action", action).toString()
        val response = execute("POST", "contacts/respond", body)
        if (response.first != 200) throw Exception(errorOf(response))
        val data = JSONObject(response.second).optJSONObject("data") ?: JSONObject()
        return data.optString("status", action)
    }

    /** GET /contacts. */
    fun contacts(): List<ContactEntry> {
        val response = execute("GET", "contacts", null)
        if (response.first != 200) throw Exception(errorOf(response))
        val data = JSONObject(response.second).optJSONArray("data") ?: return emptyList()
        val out = mutableListOf<ContactEntry>()
        for (i in 0 until data.length()) out.add(parseEntry(data.getJSONObject(i)))
        return out
    }

    /** DELETE /contacts/{user_id}: removes the pair both ways. */
    fun removeContact(userId: String) {
        val response = execute("DELETE", "contacts/$userId", null)
        if (response.first != 200) throw Exception(errorOf(response))
    }

    /** POST /block: silent - ends the contact pair and pending requests both
     *  ways, never tells the other side. */
    fun block(username: String) {
        val body = JSONObject().put("username", username.trim()).toString()
        val response = execute("POST", "block", body)
        if (response.first != 200) throw Exception(errorOf(response))
    }

    /** DELETE /block/{user_id}. */
    fun unblock(userId: String) {
        val response = execute("DELETE", "block/$userId", null)
        if (response.first != 200) throw Exception(errorOf(response))
    }

    /** GET /blocks. */
    fun blocks(): List<BlockedEntry> {
        val response = execute("GET", "blocks", null)
        if (response.first != 200) throw Exception(errorOf(response))
        val data = JSONObject(response.second).optJSONArray("data") ?: return emptyList()
        val out = mutableListOf<BlockedEntry>()
        for (i in 0 until data.length()) {
            val raw = data.getJSONObject(i)
            out.add(
                BlockedEntry(
                    userId = raw.optString("id"),
                    username = raw.optString("username"),
                    displayName = raw.optString("display_name").ifEmpty { raw.optString("username") },
                    blockedAt = if (raw.isNull("blocked_at")) null else raw.optString("blocked_at"),
                )
            )
        }
        return out
    }

    /** GET /privacy/call-policy (Who Can Call Me). */
    fun callPolicy(): CallPolicy {
        val response = execute("GET", "privacy/call-policy", null)
        if (response.first != 200) throw Exception(errorOf(response))
        val data = JSONObject(response.second).optJSONObject("data") ?: JSONObject()
        val exceptions = mutableListOf<String>()
        data.optJSONArray("exceptions")?.let { arr ->
            for (i in 0 until arr.length()) exceptions.add(arr.getString(i))
        }
        return CallPolicy(policy = data.optString("policy", "everyone"), exceptions = exceptions)
    }

    /** PUT /privacy/call-policy. Policies: everyone|contacts|contacts_except|nobody. */
    fun putCallPolicy(policy: String, exceptions: List<String>? = null): CallPolicy {
        val body = JSONObject().put("policy", policy)
        if (exceptions != null) {
            val arr = org.json.JSONArray()
            exceptions.forEach { arr.put(it) }
            body.put("exceptions", arr)
        }
        val response = execute("PUT", "privacy/call-policy", body.toString())
        if (response.first != 200) throw Exception(errorOf(response))
        return callPolicy()
    }
}
