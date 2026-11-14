package com.example.chat.data.api

import org.json.JSONObject
import java.time.Instant

/** Opaque proof, not an auth session. No data-class toString can print nonce/code/token. */
class PhoneVerificationProof(val challengeId: String, val normalizedNumber: String, val token: String,
    val expiresAt: Instant, val tenantId: String, val baseUrl: String)
class PhoneVerificationChallenge(val challengeId: String, val normalizedNumber: String, val nonce: String,
    val expiresAt: Instant, val resendAt: Instant, val tenantId: String, val baseUrl: String)
class PhoneVerificationFailure(val status: Int, val errorCode: String, val retryAfterSeconds: Int?, message: String) : Exception(message)

/** Captured workspace. An API capability means the contract exists, not that SMS is enabled. */
class ApiPhoneVerificationClient(private val baseUrl: String, private val tenantId: String,
    private val transport: ApiHttpTransport, private val isCurrent: () -> Boolean,
    private val now: () -> Instant = Instant::now) {
    fun challenge(number: String): PhoneVerificationChallenge {
        require(number.matches(Regex("\\+[1-9][0-9]{7,14}"))) { "Enter an international number with country code." }
        val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val nonce = bytes.joinToString("") { "%02x".format(it) }
        val data = post("challenge", JSONObject().put("phoneNumber", number).put("clientNonce", nonce))
        val id = strictString(data, "challengeId", 128)
        val normalized = strictString(data, "normalizedNumber", 16)
        check(normalized == number) { "Phone challenge returned a different number." }
        val expires = Instant.parse(data.getString("expiresAt"))
        val resend = (data.opt("resendAfter") as? Int)?.takeIf { it in 1..300 } ?: error("Invalid resend timing.")
        check(expires.isAfter(now()) && !expires.isAfter(now().plusSeconds(360))) { "Invalid phone challenge expiry." }
        return PhoneVerificationChallenge(id, normalized, nonce, expires, now().plusSeconds(resend.toLong()), tenantId, baseUrl)
    }
    fun verify(challenge: PhoneVerificationChallenge, code: String): PhoneVerificationProof {
        require(challenge.tenantId == tenantId && challenge.baseUrl == baseUrl && challenge.expiresAt.isAfter(now())) { "Phone challenge expired or workspace changed." }
        require(code.matches(Regex("[0-9]{6}"))) { "Enter the six-digit code." }
        val data = post("verify", JSONObject().put("challengeId", challenge.challengeId).put("clientNonce", challenge.nonce).put("code", code))
        check(data.opt("verified") == true && data.optString("challengeId") == challenge.challengeId && data.optString("normalizedNumber") == challenge.normalizedNumber) { "Invalid phone verification response." }
        val token = strictString(data, "verificationToken", 4096)
        val expires = Instant.parse(data.getString("expiresAt"))
        check(expires.isAfter(now()) && !expires.isAfter(now().plusSeconds(660))) { "Invalid phone proof expiry." }
        return PhoneVerificationProof(challenge.challengeId, challenge.normalizedNumber, token, expires, tenantId, baseUrl)
    }
    private fun strictString(data: JSONObject, key: String, max: Int): String =
        (data.opt(key) as? String)?.takeIf { it.length in 1..max && it.none(Char::isISOControl) } ?: error("Invalid phone verification response.")
    private fun post(action: String, body: JSONObject): JSONObject {
        val uri = java.net.URI(baseUrl)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "Secure phone verification endpoint required." }
        check(isCurrent()) { "Workspace changed. Start phone verification again." }
        val headers = mapOf("Content-Type" to "application/json", "x-tenant-id" to tenantId)
        var response = transport.execute("POST", "${baseUrl.trimEnd('/')}/auth/phone/$action", headers, body.toString())
        check(isCurrent()) { "Workspace changed. Start phone verification again." }
        if (response.first == 404) {
            response = transport.execute("POST", "${baseUrl.trimEnd('/')}/api/v1/auth/phone/$action", headers, body.toString())
            check(isCurrent()) { "Workspace changed. Start phone verification again." }
        }
        val root = runCatching { JSONObject(response.second) }.getOrNull()
        if (response.first != 200) {
            val detail = root?.optJSONObject("detail")
            val code = detail?.optString("code").orEmpty()
            val message = when {
                response.first == 503 && code == "SMS_UNAVAILABLE" -> "SMS verification is unavailable. No SMS was sent. You can register with email only."
                response.first == 429 -> "Too many attempts. Wait before trying again."
                response.first == 410 -> "The verification code or proof expired. Request a new code."
                response.first == 409 -> "This challenge is no longer active. Request a new code."
                else -> "Phone verification failed (${response.first}). Try again or continue with email only."
            }
            throw PhoneVerificationFailure(response.first, code, (detail?.opt("retryAfter") as? Number)?.toInt()?.takeIf { it in 1..86400 }, message)
        }
        return root?.optJSONObject("data") ?: error("Invalid phone verification response.")
    }
}
