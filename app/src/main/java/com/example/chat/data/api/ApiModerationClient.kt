package com.example.chat.data.api

import org.json.JSONObject

/** Pending owner-operated backend contract. No optimistic success or endpoint fallback. */
class ApiModerationClient(private val transport: ApiHttpTransport) {
    fun report(scope: ApiAccountScope, current: () -> Boolean, messageId: String, reason: String, details: String): String {
        val id = ApiPathSegments.safe(messageId) ?: error("Invalid message identifier.")
        require(reason in REASONS) { "Choose a report reason." }
        require(details.length <= 1000) { "Report details must be at most 1000 characters." }
        val data = request(scope, current, "POST", "/moderation/reports",
            JSONObject().put("message_id", id).put("reason", reason).put("details", details).toString())
        check(data.optString("status") == "received" && data.optString("report_id").isNotBlank()) { "Server did not confirm the report." }
        return data.getString("report_id")
    }
    fun block(scope: ApiAccountScope, current: () -> Boolean, userId: String, blocked: Boolean) {
        val id = ApiPathSegments.safe(userId) ?: error("Invalid user identifier.")
        require(id != scope.userId) { "You cannot block yourself." }
        val data = request(scope, current, "PUT", "/moderation/blocks/$id", JSONObject().put("blocked", blocked).toString())
        check(data.optString("user_id") == id && data.has("blocked") && data.getBoolean("blocked") == blocked) { "Server did not confirm the block change." }
    }
    private fun request(scope: ApiAccountScope, current: () -> Boolean, method: String, path: String, body: String): JSONObject {
        check(!scope.token.isNullOrBlank() && !scope.userId.isNullOrBlank()) { "Sign in before using safety actions." }
        check(current()) { "Account changed. Open this chat again." }
        val response = transport.execute(method, scope.baseUrl.trimEnd('/') + path,
            mapOf("Authorization" to "Bearer ${scope.token}", "x-tenant-id" to scope.tenantId, "Content-Type" to "application/json"), body)
        check(current()) { "Account changed. Result was not applied." }
        if (response.first == 404 || response.first == 501 || response.first == 503) error("Safety service is not available yet. Nothing was confirmed; try again later.")
        check(response.first in 200..201) { "Safety request failed (${response.first}). Nothing was confirmed." }
        return JSONObject(response.second).getJSONObject("data")
    }
    companion object { val REASONS = setOf("spam", "harassment", "hate", "sexual_content", "violence", "other") }
}
