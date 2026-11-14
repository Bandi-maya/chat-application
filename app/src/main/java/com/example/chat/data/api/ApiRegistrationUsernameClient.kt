package com.example.chat.data.api

import org.json.JSONObject

/** Public registration preflight only. No auth-profile endpoint fallback,
 *  no Available result for missing/malformed/unavailable backend response. */
class ApiRegistrationUsernameClient(
    private val baseUrl: String,
    private val tenantId: String,
    private val transport: ApiHttpTransport,
    private val isCurrent: () -> Boolean = { true },
) {
    fun check(username: String): ApiAuthClient.UsernameAvailability {
        val clean = username.trim()
        require(clean.length in 3..30) { "Username must have 3 to 30 characters." }
        val encoded = java.net.URLEncoder.encode(clean, "UTF-8")
        val headers = mapOf("Content-Type" to "application/json", "x-tenant-id" to tenantId)
        kotlin.check(isCurrent()) { "Workspace changed; check the username again." }
        var response = transport.execute("GET", "${baseUrl.trimEnd('/')}/auth/username-availability?u=$encoded", headers, null)
        kotlin.check(isCurrent()) { "Workspace changed; check the username again." }
        if (response.first == 404) {
            response = transport.execute("GET", "${baseUrl.trimEnd('/')}/api/v1/auth/username-availability?u=$encoded", headers, null)
            kotlin.check(isCurrent()) { "Workspace changed; check the username again." }
        }
        kotlin.check(response.first == 200) { "Could not check username (${response.first})." }
        val data = JSONObject(response.second).optJSONObject("data") ?: error("Invalid username-check response.")
        kotlin.check(data.opt("valid") is Boolean && data.opt("available") is Boolean) { "Invalid username-check response." }
        return ApiAuthClient.UsernameAvailability(data.getBoolean("valid"), data.getBoolean("available"),
            data.optString("reason").takeIf { it.isNotBlank() && it != "null" })
    }
}
