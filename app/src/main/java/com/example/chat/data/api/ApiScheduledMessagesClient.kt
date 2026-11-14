package com.example.chat.data.api

import com.example.chat.data.services.ServerScheduledMessage
import com.example.chat.ui.automation.ScheduledMessagesPort
import java.time.Instant
import java.time.ZoneId
import org.json.JSONObject

/** Local server contract: plaintext scheduling only. Captured scope, no live routing lambdas. */
class ApiScheduledMessagesClient(
    private val owner: ApiAccountScope,
    private val transport: ApiHttpTransport,
    private val guarded: (ApiAccountScope, () -> Unit) -> Boolean,
    private val retryId: (requestFingerprint: String) -> String,
) : ScheduledMessagesPort {
    override fun publish(block: () -> Unit): Boolean = guarded(owner, block)
    private fun call(method: String, suffix: String = "", payload: JSONObject? = null): JSONObject {
        check(publish {}) { "Account changed. Open scheduled messages again." }
        check(ApiServerCapabilities.supports(owner, transport, "scheduled_messages_v1")) { "Scheduled sending is unavailable on this server. Nothing was changed." }
        check(publish {}) { "Account changed. Open scheduled messages again." }
        val headers = mapOf("Authorization" to "Bearer ${owner.token ?: error("Not authenticated")}",
            "x-tenant-id" to owner.tenantId, "Content-Type" to "application/json")
        val response = transport.execute(method, "${owner.baseUrl.trimEnd('/')}/chat/scheduled-messages$suffix", headers, payload?.toString())
        check(publish {}) { "Account changed. Reopen this account to check the schedule." }
        if (response.first !in 200..299) {
            val reason = when (response.first) {
                404 -> "Scheduled sending is unavailable on this server or the schedule no longer exists."
                409 -> "The server could not change this schedule. Refresh to check its current state."
                else -> "Could not update scheduled messages (${response.first}). Retry keeps the same request ID."
            }
            throw IllegalStateException(reason)
        }
        return JSONObject(response.second)
    }
    override suspend fun create(roomId: String, body: String, at: Instant): String {
        require(body.isNotBlank() && body.length <= 10_000) { "Scheduled messages must contain 1 to 10,000 characters." }
        // Never persist the body here. Only a SHA-256 fingerprint and random retry ID are retained.
        val values = listOf(owner.userId.orEmpty(), owner.tenantId, owner.baseUrl, roomId, body, at.toString())
        val framed = values.joinToString("") { "${it.length}:$it" }
        val fingerprint = java.security.MessageDigest.getInstance("SHA-256").digest(framed.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val id = retryId(fingerprint)
        val data = call("POST", payload = JSONObject().put("room_id", roomId).put("client_id", id)
            .put("content", body).put("send_at", at.toString())).getJSONObject("data")
        return data.getString("id").also { check(it.isNotBlank()) { "Invalid schedule response" } }
    }
    private fun segment(id: String): String = ApiPathSegments.safe(id) ?: error("Invalid schedule ID")
    override suspend fun list(): List<ServerScheduledMessage> {
        val rows = call("GET").getJSONArray("data")
        return (0 until rows.length()).map { row(rows.getJSONObject(it)) }
    }
    override suspend fun get(id: String): ServerScheduledMessage = row(call("GET", "/${segment(id)}").getJSONObject("data"))
    override suspend fun cancel(id: String) {
        val data = call("DELETE", "/${segment(id)}").getJSONObject("data")
        check(data.optString("id") == id && data.optString("state") == "cancelled") {
            "Cancellation was not confirmed. Refresh to check the schedule."
        }
    }
    private fun row(json: JSONObject): ServerScheduledMessage = ServerScheduledMessage(
        id = json.getString("id"), conversationId = json.getString("room_id"),
        body = json.optString("content").takeUnless { it == "null" }.orEmpty(),
        scheduledAt = Instant.parse(json.getString("send_at")).atZone(ZoneId.systemDefault()),
        state = json.getString("state"), executedAt = null,
        lastError = json.optString("failure_code").takeUnless { it.isBlank() || it == "null" },
    )
}
