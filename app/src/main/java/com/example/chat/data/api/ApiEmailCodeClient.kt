package com.example.chat.data.api

import org.json.JSONObject

/** Prepared email-code auth contract. Captured route/tenant, no password or bearer
 *  fallback; unavailable deployment is an error, never a fabricated session.
 *  Server patch must deploy before this client can authenticate real users. */
class ApiEmailCodeClient(
    private val baseUrl: String,
    private val tenantId: String,
    private val transport: ApiHttpTransport,
    private val isCurrent: () -> Boolean = { true },
) {
    data class Challenge(val id: String, val email: String, val resendAfterSeconds: Int, val expiresInSeconds: Int)

    private fun request(path: String, payload: JSONObject, deviceHeaders: Map<String, String> = emptyMap()): Pair<Int, String> {
        check(isCurrent()) { "Account or workspace changed. Start sign-in again." }
        val uri = java.net.URI(baseUrl)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "Secure sign-in endpoint required." }
        val headers = mapOf("Content-Type" to "application/json", "x-tenant-id" to tenantId) +
            deviceHeaders.filterKeys { it in setOf("x-device-id", "x-device-public-key", "x-device-challenge-id", "x-device-proof") }
        val body = payload.toString()
        var response = transport.execute("POST", "${baseUrl.trimEnd('/')}/$path", headers, body)
        check(isCurrent()) { "Account or workspace changed. Start sign-in again." }
        if (response.first == 404) {
            response = transport.execute("POST", "${baseUrl.trimEnd('/')}/api/v1/$path", headers, body)
            check(isCurrent()) { "Account or workspace changed. Start sign-in again." }
        }
        return response
    }

    fun requestCode(email: String): Challenge {
        val normalized = email.trim().lowercase(java.util.Locale.ROOT)
        require(normalized.length <= 254 && normalized.contains('@')) { "Enter a valid email address." }
        val response = request("auth/email-code/request", JSONObject().put("email", normalized))
        if (response.first != 200) throw responseError(response)
        val root = JSONObject(response.second)
        val data = root.optJSONObject("data") ?: throw IllegalStateException("Invalid code-request response.")
        val id = data.optString("challenge_id")
        require(runCatching { java.util.UUID.fromString(id).toString() == id.lowercase(java.util.Locale.ROOT) }.getOrDefault(false)) { "Invalid code challenge." }
        val resend = data.optInt("resend_after_seconds", -1)
        val expires = data.optInt("expires_in_seconds", -1)
        check(resend in 1..3600 && expires in 30..3600) { "Invalid code-request expiry." }
        return Challenge(id, normalized, resend, expires)
    }

    fun verifyCode(challenge: Challenge, code: String, deviceHeaders: Map<String, String> = emptyMap()): ApiAuthResult {
        require(code.length == 6 && code.all { it in '0'..'9' }) { "Enter the six-digit code." }
        val response = request("auth/email-code/verify", JSONObject()
            .put("challenge_id", challenge.id).put("code", code), deviceHeaders)
        if (response.first != 200) throw responseError(response)
        val result = ApiAuthClient({ baseUrl }, { tenantId }, transport).parseAuthResponse(response.first, response.second)
        check(result.token.isNotBlank() && result.user.id.isNotBlank() && !result.refreshToken.isNullOrBlank()) {
            "The server did not return a complete sign-in session."
        }
        check(result.user.email.equals(challenge.email, ignoreCase = true)) { "The code signed in a different email. Start again." }
        return result
    }

    private fun responseError(response: Pair<Int, String>): Exception {
        val message = runCatching { JSONObject(response.second).optString("detail") }.getOrNull()
            ?.takeIf { it.isNotBlank() && it.length < 250 }
        return IllegalStateException(message ?: "Email-code sign-in is unavailable (${response.first}).")
    }
}
