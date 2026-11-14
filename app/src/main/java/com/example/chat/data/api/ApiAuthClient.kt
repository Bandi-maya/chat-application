package com.example.chat.data.api

import org.json.JSONObject
import java.util.Base64

/** Context-free port of the api branch's REST auth surface
 *  (lib/data/services/api_backend_service.dart auth subset +
 *  lib/data/services/username_login_service.dart). This is the service the
 *  api-branch auth screens actually call; the backend session plane backs
 *  the ChatyBackendService facade paths instead.
 *
 *  Endpoint layout (primary, with the Dart 404 fallbacks):
 *  - login:    POST {base}/auth/token            (fallback /api/v1/auth/token)
 *  - register: POST {base}/auth/register         (fallback /api/v1/auth/register)
 *  - forgot:   POST {base}/auth/forgot-password  (fallback /api/v1/auth/forgot-password)
 *  - org check: GET {base}/organization/check/<id> (fallback /tenants/config?tenant_id=<id>)
 *  Every call carries the x-tenant-id header from ApiBackendConfig. */

data class ApiUserProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarInitials: String,
    val avatarColorHex: String = "0xFF6366F1",
    val about: String = "",
    val email: String = "",
    val phone: String = "",
    val avatarUrl: String? = null,
    /** Server-side users.public_profile.banner_url (profile banner image). */
    val bannerUrl: String? = null,
    /** Server-side users.email_verified; defaults true so profiles parsed from
     *  older backends (or stored sessions) never show a false banner. */
    val emailVerified: Boolean = true,
)

data class ApiAuthResult(val token: String, val user: ApiUserProfile, val refreshToken: String? = null, val verificationMessage: String? = null)

/** Transport seam so the request/response logic is JVM-testable.
 *  Returns (statusCode, body); throws on transport failure. */
fun interface ApiHttpTransport {
    /** method is "GET" or "POST"; headers include Content-Type/x-tenant-id. */
    fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String>
}

class ApiAuthClient(
    private val baseUrl: () -> String,
    private val tenantId: () -> String,
    private val transport: ApiHttpTransport,
    private val token: () -> String? = { null },
) {
    /** ApiBackendService.login — identifier is sent verbatim as `username`. */
    fun login(identifier: String, password: String, extraHeaders: Map<String, String> = emptyMap()): ApiAuthResult {
        val body = JSONObject().put("username", identifier).put("password", password).toString()
        val response = postWithFallback("auth/token", body, extraHeaders = extraHeaders)
        return parseAuthResponse(response.first, response.second)
    }

    /** ApiBackendService.register — no displayName is sent; the display name
     *  comes back from the server response (see parseAuthResponse). */
    fun register(email: String, username: String, password: String, extraHeaders: Map<String, String> = emptyMap(), phoneProof: PhoneVerificationProof? = null): ApiAuthResult {
        val body = JSONObject()
            .put("email", email)
            .put("username", username)
            .put("password", password)
            .apply {
                phoneProof?.let { proof ->
                    require(proof.tenantId == tenantId() && proof.baseUrl.trimEnd('/') == baseUrl().trimEnd('/') && proof.expiresAt.isAfter(java.time.Instant.now())) { "Phone verification expired or workspace changed." }
                    put("phoneChallengeId", proof.challengeId); put("phoneVerificationToken", proof.token)
                }
            }
            .toString()
        val response = postWithFallback("auth/register", body, extraHeaders = extraHeaders)
        val result = parseAuthResponse(response.first, response.second)
        val notice = runCatching { JSONObject(response.second).optString("message").takeIf { it.isNotBlank() && it != "null" } }.getOrNull()
        return result.copy(verificationMessage = notice)
    }

    /** ApiBackendService.updateProfile: PUT /app-users/profile (+ /api/v1
     *  fallback). Payload includes only provided fields plus the nested
     *  publicProfile block, matching Dart exactly. Returns (success, detail). */
    fun updateProfile(
        displayName: String? = null,
        about: String? = null,
        username: String? = null,
        avatarUrl: String? = null,
        phone: String? = null,
        avatarColorHex: String? = null,
        avatarInitials: String? = null,
        bannerUrl: String? = null,
    ): Pair<Boolean, String> {
        val tok = token() ?: throw Exception("Not authenticated")
        return putProfile(baseUrl(), tenantId(), tok, displayName, about, username, avatarUrl,
            phone, avatarColorHex, avatarInitials, bannerUrl) { }
    }

    /** Account-bound form: route, tenant and token come ONLY from [expected] (captured once, never re-read on the
     *  404 retry). [isCurrent] (authSnapshot() === expected) is checked before each request and before the result is
     *  released; a failed check throws [ApiScopeChangedException] and no retry is sent. HTTP runs outside any lock.
     *  A blank token or blank userId is rejected before any request. The caller publishes to the local profile inside
     *  a short withScope(expected) callback. */
    fun updateProfile(
        expected: ApiAccountScope,
        isCurrent: () -> Boolean,
        displayName: String? = null,
        about: String? = null,
        username: String? = null,
        avatarUrl: String? = null,
        phone: String? = null,
        avatarColorHex: String? = null,
        avatarInitials: String? = null,
        bannerUrl: String? = null,
    ): Pair<Boolean, String> {
        val tok = expected.token?.takeIf { it.isNotBlank() && !expected.userId.isNullOrBlank() }
            ?: throw Exception("Not authenticated")
        if (expected.baseUrl.isBlank()) throw Exception("Not authenticated")
        return putProfile(expected.baseUrl, expected.tenantId, tok, displayName, about, username, avatarUrl,
            phone, avatarColorHex, avatarInitials, bannerUrl) {
            if (!isCurrent()) throw ApiScopeChangedException()
        }
    }

    private fun putProfile(
        base: String, tenant: String, authToken: String,
        displayName: String?, about: String?, username: String?, avatarUrl: String?,
        phone: String?, avatarColorHex: String?, avatarInitials: String?, bannerUrl: String?,
        check: () -> Unit,
    ): Pair<Boolean, String> {
        val headers = mapOf(
            "Authorization" to "Bearer $authToken",
            "Content-Type" to "application/json",
            "x-tenant-id" to tenant,
        )

        // Preferred plane (research 5.1): PUT /profile/me persists username
        // changes; the legacy /app-users/profile drops them. Old servers 404
        // the new route, so fall back in that case only.
        val newPayload = JSONObject()
        if (displayName != null) newPayload.put("display_name", displayName.trim())
        if (about != null) newPayload.put("about", about.trim())
        if (avatarUrl != null) newPayload.put("avatar", avatarUrl.trim())
        if (username != null) newPayload.put("username", username.trim())
        if (phone != null) newPayload.put("phone", phone.trim())
        if (avatarColorHex != null) newPayload.put("avatar_color_hex", avatarColorHex.trim())
        if (avatarInitials != null) newPayload.put("avatar_initials", avatarInitials.trim())
        if (bannerUrl != null) newPayload.put("banner_url", bannerUrl.trim())

        val legacyPayload = JSONObject()
        if (displayName != null) legacyPayload.put("full_name", displayName.trim())
        if (about != null) legacyPayload.put("about", about.trim())
        if (username != null) legacyPayload.put("username", username.trim())
        // Legacy server copies top-level avatarUrl into the directory's users.avatar_url.
        if (avatarUrl != null) legacyPayload.put("avatarUrl", avatarUrl.trim())
        val publicProfile = JSONObject()
        if (about != null) publicProfile.put("about", about.trim())
        if (avatarUrl != null) publicProfile.put("avatarUrl", avatarUrl.trim())
        if (avatarColorHex != null) publicProfile.put("avatar_color_hex", avatarColorHex.trim())
        if (avatarInitials != null) publicProfile.put("avatar_initials", avatarInitials.trim())
        if (bannerUrl != null) publicProfile.put("banner_url", bannerUrl.trim())
        legacyPayload.put("publicProfile", publicProfile)

        check()
        var response = try {
            transport.execute("PUT", "$base/profile/me", headers, newPayload.toString())
        } catch (e: Exception) {
            throw Exception("Unable to reach server to update profile: $e")
        }
        check()   // switch while in flight: no retry under a stale account
        if (response.first == 404) {
            response = try {
                transport.execute("PUT", "$base/api/v1/profile/me", headers, newPayload.toString())
            } catch (e: Exception) {
                throw Exception("Unable to reach server to update profile: $e")
            }
            check()
        }
        val finalResponse = if (response.first == 404) {
            // Server predates the profile plane: legacy route, which silently
            // drops username (the gap the new plane closes).
            val legacy = try {
                transport.execute("PUT", "$base/app-users/profile", headers, legacyPayload.toString())
            } catch (e: Exception) {
                throw Exception("Unable to reach server to update profile: $e")
            }
            check()
            if (legacy.first == 404) {
                val legacyV1 = try {
                    transport.execute("PUT", "$base/api/v1/app-users/profile", headers, legacyPayload.toString())
                } catch (e: Exception) {
                    throw Exception("Unable to reach server to update profile: $e")
                }
                check()
                legacyV1
            } else legacy
        } else response
        check()   // never release a result into a replaced account

        if (finalResponse.first == 200) return true to ""
        var detail = "Failed to update profile (${finalResponse.first})"
        if (finalResponse.first == 409) detail = "That username is taken."
        try {
            val body = JSONObject(finalResponse.second)
            val d = if (body.has("detail") && !body.isNull("detail")) body.get("detail").toString()
                else if (body.has("message") && !body.isNull("message")) body.get("message").toString()
                else null
            if (d != null) detail = d
        } catch (_: Exception) {
        }
        throw Exception(detail)
    }

    /** ApiBackendService.registerOrganization: POST /organization/admin/create
     *  (404 fallback /api/v1/organization/admin/create). On success the Dart
     *  side persists the org blob and applies the tenant — the caller
     *  (ApiBackendService) owns those side effects. */
    fun registerOrganization(
        tenantId: String,
        organizationName: String,
        adminUsername: String,
        adminEmail: String,
        adminPassword: String,
        adminFullName: String,
        tenantType: String = "chat",
        allowUserRegistration: Boolean = true,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Pair<ApiAuthResult, Map<String, Any?>?> {
        val payload = JSONObject()
            .put("tenant_id", tenantId.trim())
            .put("name", organizationName.trim())
            .put("admin_username", adminUsername.trim())
            .put("admin_email", adminEmail.trim())
            .put("admin_password", adminPassword)
            .put("admin_full_name", adminFullName.trim())
            .put("tenant_type", tenantType)
            .put("allow_user_registration", allowUserRegistration)
            .toString()
        val response = postWithFallbackNoTenant("organization/admin/create", payload, extraHeaders)
        var org: Map<String, Any?>? = null
        if (response.first == 200) {
            try {
                val body = JSONObject(response.second)
                val data = if (body.has("data")) body.get("data") else body
                if (data is JSONObject && data.has("org") && data.get("org") is JSONObject) {
                    org = jsonToMap(data.getJSONObject("org"))
                }
            } catch (_: Exception) {
            }
        }
        return parseAuthResponse(response.first, response.second) to org
    }

    /** The organization endpoints do NOT carry the x-tenant-id header. */
    private fun postWithFallbackNoTenant(path: String, body: String, extraHeaders: Map<String, String> = emptyMap()): Pair<Int, String> {
        val headers = mapOf("Content-Type" to "application/json") + extraHeaders
        val response = transport.execute("POST", "${baseUrl()}/$path", headers, body)
        if (response.first == 404) {
            return transport.execute("POST", "${baseUrl()}/api/v1/$path", headers, body)
        }
        return response
    }

    /** Outcome of POST /auth/refresh. [Rejected] means the refresh token is
     *  expired/revoked (the user must sign in again); [Unavailable] is a
     *  network/server problem worth retrying later. [Ok.refreshToken] is the
     *  rotated refresh token when the server rotates (single-use tokens);
     *  the caller MUST persist it - replaying a consumed token revokes the
     *  whole session server-side. Null means the server did not rotate. */
    sealed interface RefreshResult {
        data class Ok(val accessToken: String, val refreshToken: String? = null) : RefreshResult
        data object Rejected : RefreshResult
        data object Unavailable : RefreshResult
    }

    /** POST /auth/refresh {refreshToken} -> new access token (data.token) plus
     *  the rotated refresh token (data.refreshToken / data.refresh_token). */
    fun refresh(refreshToken: String, extraHeaders: Map<String, String> = emptyMap()): RefreshResult {
        val body = JSONObject().put("refreshToken", refreshToken).toString()
        val response = try {
            postWithFallback("auth/refresh", body, extraHeaders = extraHeaders)
        } catch (_: Exception) {
            return RefreshResult.Unavailable
        }
        return when (response.first) {
            200 -> try {
                val parsed = JSONObject(response.second)
                val data = parsed.optJSONObject("data") ?: parsed
                val token = data.optString("token", "").ifEmpty { data.optString("access_token", "") }
                if (token.isEmpty()) return RefreshResult.Unavailable
                val rotated = data.optString("refreshToken", "").ifEmpty { data.optString("refresh_token", "") }
                RefreshResult.Ok(token, rotated.ifBlank { null })
            } catch (_: Exception) {
                RefreshResult.Unavailable
            }
            401, 403 -> RefreshResult.Rejected
            else -> RefreshResult.Unavailable
        }
    }

    /** Seconds until the JWT expires, or null when the token has no readable exp. */
    fun secondsUntilExpiry(token: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Long? = try {
        val exp = decodeJwt(token).optLong("exp", 0L)
        if (exp <= 0L) null else exp - nowEpochSeconds
    } catch (_: Exception) {
        null
    }

    /** ApiBackendService.forgotPassword — returns the server message. */
    fun forgotPassword(email: String): String {
        val cleanEmail = email.trim()
        val cleanTenant = tenantId().trim()
        val body = JSONObject().put("email", cleanEmail).toString()
        val response: Pair<Int, String> = try {
            postWithFallback("auth/forgot-password", body, tenant = cleanTenant)
        } catch (e: Exception) {
            throw Exception("Unable to reach server. Please check your internet connection.")
        }
        if (response.first == 200) {
            return try {
                val parsed = JSONObject(response.second)
                val data = if (parsed.has("data")) parsed.get("data") else parsed
                (if (data is JSONObject && data.has("message") && !data.isNull("message"))
                    data.get("message").toString()
                else null)
                    ?: "If an account exists with this email, a reset link has been sent."
            } catch (_: Exception) {
                "If an account exists with this email, a reset link has been sent."
            }
        }
        var errorMsg = "Failed to request password reset (${response.first})"
        try {
            val parsed = JSONObject(response.second)
            errorMsg = parsed.optString("detail").ifEmpty { parsed.optString("message").ifEmpty { errorMsg } }
        } catch (_: Exception) {
        }
        throw Exception(errorMsg)
    }

    /** Username availability verdict (report 5.1: yes/no only, no taken
     *  suggestions). [reason] carries the inline rule error when valid=false. */
    data class UsernameAvailability(val valid: Boolean, val available: Boolean, val reason: String? = null)

    /** GET /profile/username-availability?u= (+ /api/v1 fallback). */
    fun usernameAvailability(username: String): UsernameAvailability {
        val tok = token() ?: throw Exception("Not authenticated")
        val base = baseUrl()
        val headers = mapOf(
            "Authorization" to "Bearer $tok",
            "Content-Type" to "application/json",
            "x-tenant-id" to tenantId(),
        )
        val encoded = java.net.URLEncoder.encode(username.trim(), "UTF-8")
        var response = try {
            transport.execute("GET", "$base/profile/username-availability?u=$encoded", headers, null)
        } catch (e: Exception) {
            throw Exception("Unable to reach server. Please check your internet connection.")
        }
        if (response.first == 404) {
            response = try {
                transport.execute("GET", "$base/api/v1/profile/username-availability?u=$encoded", headers, null)
            } catch (e: Exception) {
                throw Exception("Unable to reach server. Please check your internet connection.")
            }
        }
        if (response.first == 404) {
            // Server predates the profile plane: no live check, let the save
            // attempt surface any conflict instead.
            return UsernameAvailability(valid = true, available = true)
        }
        if (response.first != 200) {
            var errorMsg = "Request failed (${response.first})"
            try {
                val parsed = JSONObject(response.second)
                errorMsg = parsed.optString("detail").ifEmpty { parsed.optString("message").ifEmpty { errorMsg } }
            } catch (_: Exception) {
            }
            throw Exception(errorMsg)
        }
        val parsed = JSONObject(response.second)
        val data = parsed.optJSONObject("data") ?: parsed
        return UsernameAvailability(
            valid = data.optBoolean("valid", true),
            available = data.optBoolean("available", false),
            reason = if (data.has("reason") && !data.isNull("reason")) data.get("reason").toString() else null,
        )
    }

    /** GET /profile/{username} (+ /api/v1 fallback): exact-match public-safe
     *  profile for the contacts plane. 404 surfaces "No account with that
     *  username."; servers predating the profile plane get the same message. */
    fun profileByUsername(username: String): Map<String, Any?> {
        val tok = token() ?: throw Exception("Not authenticated")
        val base = baseUrl()
        val headers = mapOf(
            "Authorization" to "Bearer $tok",
            "Content-Type" to "application/json",
            "x-tenant-id" to tenantId(),
        )
        val encoded = java.net.URLEncoder.encode(username.trim(), "UTF-8")
        var response = try {
            transport.execute("GET", "$base/profile/$encoded", headers, null)
        } catch (e: Exception) {
            throw Exception("Unable to reach server. Please check your internet connection.")
        }
        if (response.first == 404) {
            response = try {
                transport.execute("GET", "$base/api/v1/profile/$encoded", headers, null)
            } catch (e: Exception) {
                throw Exception("Unable to reach server. Please check your internet connection.")
            }
        }
        if (response.first != 200) {
            var errorMsg = "No account with that username."
            if (response.first != 404) {
                errorMsg = "Request failed (${response.first})"
                try {
                    val parsed = JSONObject(response.second)
                    errorMsg = parsed.optString("detail").ifEmpty { parsed.optString("message").ifEmpty { errorMsg } }
                } catch (_: Exception) {
                }
            }
            throw Exception(errorMsg)
        }
        val parsed = JSONObject(response.second)
        val data = parsed.optJSONObject("data") ?: parsed
        return jsonToMap(data)
    }

    /** POST auth/email/verification/send: asks the server to email a fresh
     *  6-digit code. Enumeration-safe — the server answers the same whether
     *  or not the address is registered, and the generic message passes through. */
    fun sendEmailVerification(email: String): String = postOtpMail(
        "auth/email/verification/send",
        JSONObject().put("email", email.trim()).toString(),
        fallbackMessage = "If an account exists with this email, verification was requested. Delivery is not yet confirmed.",
    )

    /** POST auth/email/verification/confirm: marks the address verified when
     *  the code matches. Wrong codes carry "N attempts remaining" in detail. */
    fun confirmEmailVerification(email: String, code: String): String = postOtpMail(
        "auth/email/verification/confirm",
        JSONObject().put("email", email.trim()).put("code", code.trim()).toString(),
        fallbackMessage = "Email verified.",
    )

    /** POST auth/reset-password: the code from the forgot-password email sets
     *  the new password; the server revokes every existing session. */
    fun resetPasswordWithCode(email: String, code: String, newPassword: String): String = postOtpMail(
        "auth/reset-password",
        JSONObject().put("email", email.trim()).put("code", code.trim())
            .put("new_password", newPassword).toString(),
        fallbackMessage = "Password updated.",
    )

    /** Shared POST + message/detail handling for the email-OTP endpoints
     *  (same envelope as forgotPassword: data.message on 200, detail on error). */
    private fun postOtpMail(path: String, body: String, fallbackMessage: String): String {
        val response: Pair<Int, String> = try {
            postWithFallback(path, body, tenant = tenantId().trim())
        } catch (e: Exception) {
            throw Exception("Unable to reach server. Please check your internet connection.")
        }
        if (response.first == 200) {
            return try {
                val parsed = JSONObject(response.second)
                val data = if (parsed.has("data")) parsed.get("data") else parsed
                (if (data is JSONObject && data.has("message") && !data.isNull("message"))
                    data.get("message").toString()
                else null) ?: parsed.optString("message").takeIf { it.isNotBlank() && it != "null" } ?: fallbackMessage
            } catch (_: Exception) {
                fallbackMessage
            }
        }
        var errorMsg = "Request failed (${response.first})"
        try {
            val parsed = JSONObject(response.second)
            errorMsg = parsed.optString("detail").ifEmpty { parsed.optString("message").ifEmpty { errorMsg } }
        } catch (_: Exception) {
        }
        throw Exception(errorMsg)
    }

    /** ApiBackendService.checkOrganization. */
    fun checkOrganization(workspace: String): Map<String, Any?> {
        val clean = workspace.trim()
        if (clean.isEmpty()) throw Exception("Organization ID cannot be empty")

        val primary = "${baseUrl()}/organization/check/$clean"
        val fallback = "${baseUrl()}/tenants/config?tenant_id=$clean"
        var response: Pair<Int, String>? = null
        try {
            response = transport.execute("GET", primary, emptyMap(), null)
            if (response.first == 404) {
                response = transport.execute("GET", fallback, mapOf("x-tenant-id" to clean), null)
            }
        } catch (e: Exception) {
            response = try {
                transport.execute("GET", fallback, mapOf("x-tenant-id" to clean), null)
            } catch (_: Exception) {
                throw Exception("Unable to reach server to check organization: $e")
            }
        }

        return when (response.first) {
            200 -> {
                val parsed = JSONObject(response.second)
                val data = if (parsed.has("data")) parsed.get("data") else parsed
                if (data is JSONObject) jsonToMap(data) else emptyMap()
            }
            404 -> throw Exception(
                "Organization \"$clean\" not found. Please verify the ID or register a new organization.",
            )
            else -> {
                var detail = "Organization check failed (${response.first})"
                try {
                    val parsed = JSONObject(response.second)
                    detail = parsed.optString("detail").ifEmpty { parsed.optString("message").ifEmpty { detail } }
                } catch (_: Exception) {
                }
                throw Exception(detail)
            }
        }
    }

    companion object {
        /** RegisterScreen's flag/name extraction from the check payload. */
        fun orgCheckToBanner(data: Map<String, Any?>, workspace: String): Pair<String, Boolean> {
            val flag = data["allow_user_registration"] ?: data["user_registration_enabled"]
            val allowReg = when (flag) {
            is Boolean -> flag
            null -> true
            else -> flag.toString().lowercase() != "false"
            }
            val name = (data["name"] ?: workspace).toString()
            return name to allowReg
        }
    }


    /** Transport failure text: name the actual cause class so a server outage is not
     *  misreported as the user's internet. The configured endpoint is not changed here. */
    private fun serverUnreachable(e: Exception): Exception {
        val cause = when (e) {
            is java.net.SocketTimeoutException -> "server did not answer in time"
            is java.net.ConnectException -> "connection refused by the server"
            is java.net.UnknownHostException -> "server address not found"
            else -> e.javaClass.simpleName
        }
        return Exception("Unable to reach the Chaty server ($cause). Check your internet connection; if it works, the server itself is not responding - try again later.")
    }

    private fun postWithFallback(
        path: String,
        body: String,
        tenant: String = tenantId(),
        extraHeaders: Map<String, String> = emptyMap(),
    ): Pair<Int, String> {
        val headers = mapOf("Content-Type" to "application/json", "x-tenant-id" to tenant) + extraHeaders
        val response = try {
            transport.execute("POST", "${baseUrl()}/$path", headers, body)
        } catch (e: Exception) {
            throw serverUnreachable(e)
        }
        if (response.first == 404) {
            return try {
                transport.execute("POST", "${baseUrl()}/api/v1/$path", headers, body)
            } catch (e: Exception) {
                throw serverUnreachable(e)
            }
        }
        return response
    }

    // ------------------------------------------------------------------
    // _parseAuthResponse + _decodeJwt (exact ports)
    // ------------------------------------------------------------------

    fun parseAuthResponse(statusCode: Int, responseBody: String): ApiAuthResult {
        if (statusCode == 200) {
            val body = JSONObject(responseBody)
            val data = if (body.has("data")) body.get("data") else body
            val dataObj = data as? JSONObject ?: throw Exception("No token found in response")
            val tokenRaw = if (dataObj.has("access_token") && !dataObj.isNull("access_token")) dataObj.get("access_token")
                else if (dataObj.has("token") && !dataObj.isNull("token")) dataObj.get("token") else null
            val token = tokenRaw?.toString() ?: throw Exception("No token found in response")

            val payload = decodeJwt(token)
            val userObj = if (dataObj.has("user") && dataObj.get("user") is JSONObject)
                dataObj.getJSONObject("user") else JSONObject()

            val userId = firstString(userObj, "id", "uid") ?: payload.str("uid") ?: ""
            val username = firstString(userObj, "username") ?: payload.str("sub") ?: ""

            var extractedName = (firstString(userObj, "full_name", "display_name", "name") ?: "").trim()
            if (extractedName.isEmpty()) {
                val fn = (firstString(userObj, "firstName", "first_name") ?: "").trim()
                val ln = (firstString(userObj, "lastName", "last_name") ?: "").trim()
                extractedName = "$fn $ln".trim()
            }
            if (extractedName.isEmpty()) {
                extractedName = if (username.isNotEmpty()) username else (payload.str("sub") ?: "User")
            }

            val derivedInitials = if (extractedName.isNotEmpty())
                extractedName.split(Regex("\\s+"))
                    .map { if (it.isNotEmpty()) it[0].toString() else "" }
                    .take(2).joinToString("").uppercase()
            else if (username.isNotEmpty()) username.substring(0, 1).uppercase()
            else "U"
            // Server-side overrides (users.public_profile) win over derived.
            val initials = firstString(userObj, "avatar_initials")?.trim()?.takeIf { it.isNotEmpty() }
                ?: derivedInitials
            val colorHex = firstString(userObj, "avatar_color_hex", "avatarColorHex")?.trim()
                ?.takeIf { it.isNotEmpty() } ?: "0xFF6366F1"
            val banner = firstString(userObj, "banner_url")
                ?: userObj.optJSONObject("public_profile")?.optString("banner_url")?.takeIf { it.isNotEmpty() }

            val photo = firstString(userObj, "photo", "avatar_url", "avatarUrl")
                ?: (userObj.optJSONObject("public_profile")?.optString("avatarUrl"))

            var extractedAbout = ""
            val about = firstString(userObj, "about")?.trim()
            if (!about.isNullOrEmpty()) {
                extractedAbout = about
            } else {
                val pp = userObj.optJSONObject("public_profile")?.optString("about")?.trim()
                val pP = userObj.optJSONObject("publicProfile")?.optString("about")?.trim()
                val designation = firstString(userObj, "designation")?.trim()
                when {
                    !pp.isNullOrEmpty() -> extractedAbout = pp
                    !pP.isNullOrEmpty() -> extractedAbout = pP
                    !designation.isNullOrEmpty() -> extractedAbout = designation
                }
            }

            val user = ApiUserProfile(
                id = userId,
                displayName = extractedName,
                username = username,
                avatarInitials = initials,
                avatarColorHex = colorHex,
                about = extractedAbout,
                email = firstString(userObj, "email") ?: "",
                phone = firstString(userObj, "mobile", "phone") ?: "",
                avatarUrl = photo,
                bannerUrl = banner,
                emailVerified = userObj.optBoolean("email_verified", true),
            )
            val refresh = if (dataObj.has("refresh_token") && !dataObj.isNull("refresh_token")) dataObj.get("refresh_token").toString()
                else if (dataObj.has("refreshToken") && !dataObj.isNull("refreshToken")) dataObj.get("refreshToken").toString() else null
            return ApiAuthResult(token = token, user = user, refreshToken = refresh?.ifBlank { null })
        }

        // Error path mirrors the Dart else branch: detail/message passthrough.
        var detail = "Authentication failed ($statusCode)"
        try {
            val parsed = JSONObject(responseBody)
            detail = parsed.optString("detail").ifEmpty { parsed.optString("message").ifEmpty { detail } }
        } catch (_: Exception) {
        }
        val error = runCatching { JSONObject(responseBody).optJSONObject("detail") }.getOrNull()
        val code = error?.optString("code")
        detail = when (code) {
            "DEVICE_ACCOUNT_LIMIT" -> "This installation already has two accounts. Remove one before adding another."
            "DEVICE_PROOF_REQUIRED" -> "Installation verification is required. Start sign-in again."
            else -> error?.optString("message")?.takeIf { it.isNotBlank() } ?: detail
        }
        throw Exception(detail)
    }

    fun decodeJwt(token: String): JSONObject {
        val parts = token.split(".")
        if (parts.size != 3) throw Exception("Invalid token")
        var payload = parts[1].replace('-', '+').replace('_', '/')
        when (payload.length % 4) {
            2 -> payload += "=="
            3 -> payload += "="
            0 -> Unit
            else -> throw Exception("Illegal base64url string!")
        }
        val decodedBytes = try {
            android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
        } catch (_: Throwable) {
            Base64.getDecoder().decode(payload)
        }
        return JSONObject(String(decodedBytes, Charsets.UTF_8))
    }

    private fun JSONObject.str(key: String): String? =
        if (has(key) && !isNull(key)) get(key).toString() else null

    private fun firstString(obj: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) return obj.get(k).toString()
        }
        return null
    }

    private fun jsonToMap(obj: JSONObject): Map<String, Any?> {
        val out = mutableMapOf<String, Any?>()
        for (key in obj.keys()) {
            out[key] = if (obj.isNull(key)) null else obj.get(key)
        }
        return out
    }
}
