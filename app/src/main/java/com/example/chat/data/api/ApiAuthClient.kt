package com.example.chat.data.api

import org.json.JSONObject
import java.util.Base64

/** Context-free port of the api branch's REST auth surface
 *  (lib/data/services/api_backend_service.dart auth subset +
 *  lib/data/services/username_login_service.dart). This is the service the
 *  api-branch auth screens actually call; the Supabase AuthRepository backs
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
)

data class ApiAuthResult(val token: String, val user: ApiUserProfile)

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
    fun login(identifier: String, password: String): ApiAuthResult {
        val body = JSONObject().put("username", identifier).put("password", password).toString()
        val response = postWithFallback("auth/token", body)
        return parseAuthResponse(response.first, response.second)
    }

    /** ApiBackendService.register — no displayName is sent; the display name
     *  comes back from the server response (see parseAuthResponse). */
    fun register(email: String, username: String, password: String): ApiAuthResult {
        val body = JSONObject()
            .put("email", email)
            .put("username", username)
            .put("password", password)
            .toString()
        val response = postWithFallback("auth/register", body)
        return parseAuthResponse(response.first, response.second)
    }

    /** ApiBackendService.updateProfile: PUT /app-users/profile (+ /api/v1
     *  fallback). Payload includes only provided fields plus the nested
     *  publicProfile block, matching Dart exactly. Returns (success, detail). */
    fun updateProfile(
        displayName: String? = null,
        about: String? = null,
        username: String? = null,
        avatarUrl: String? = null,
    ): Pair<Boolean, String> {
        val authToken = token() ?: throw Exception("Not authenticated")
        val payload = JSONObject()
        if (displayName != null) payload.put("full_name", displayName.trim())
        if (about != null) payload.put("about", about.trim())
        if (username != null) payload.put("username", username.trim())
        val publicProfile = JSONObject()
        if (about != null) publicProfile.put("about", about.trim())
        if (avatarUrl != null) publicProfile.put("avatarUrl", avatarUrl.trim())
        payload.put("publicProfile", publicProfile)

        val headers = mapOf(
            "Authorization" to "Bearer $authToken",
            "Content-Type" to "application/json",
            "x-tenant-id" to tenantId(),
        )
        val response = try {
            transport.execute("PUT", "${baseUrl()}/app-users/profile", headers, payload.toString())
        } catch (e: Exception) {
            throw Exception("Unable to reach server to update profile: $e")
        }
        val finalResponse = if (response.first == 404) {
            try {
                transport.execute("PUT", "${baseUrl()}/api/v1/app-users/profile", headers, payload.toString())
            } catch (e: Exception) {
                throw Exception("Unable to reach server to update profile: $e")
            }
        } else response

        if (finalResponse.first == 200) return true to ""
        var detail = "Failed to update profile (${finalResponse.first})"
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
        val response = postWithFallbackNoTenant("organization/admin/create", payload)
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
    private fun postWithFallbackNoTenant(path: String, body: String): Pair<Int, String> {
        val headers = mapOf("Content-Type" to "application/json")
        val response = transport.execute("POST", "${baseUrl()}/$path", headers, body)
        if (response.first == 404) {
            return transport.execute("POST", "${baseUrl()}/api/v1/$path", headers, body)
        }
        return response
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


    private fun postWithFallback(
        path: String,
        body: String,
        tenant: String = tenantId(),
    ): Pair<Int, String> {
        val headers = mapOf("Content-Type" to "application/json", "x-tenant-id" to tenant)
        val response = try {
            transport.execute("POST", "${baseUrl()}/$path", headers, body)
        } catch (e: Exception) {
            throw Exception("Unable to reach server. Please check your internet connection.")
        }
        if (response.first == 404) {
            return try {
                transport.execute("POST", "${baseUrl()}/api/v1/$path", headers, body)
            } catch (e: Exception) {
                throw Exception("Unable to reach server. Please check your internet connection.")
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

            val initials = if (extractedName.isNotEmpty())
                extractedName.split(Regex("\\s+"))
                    .map { if (it.isNotEmpty()) it[0].toString() else "" }
                    .take(2).joinToString("").uppercase()
            else if (username.isNotEmpty()) username.substring(0, 1).uppercase()
            else "U"

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
                about = extractedAbout,
                email = firstString(userObj, "email") ?: "",
                phone = firstString(userObj, "mobile", "phone") ?: "",
                avatarUrl = photo,
            )
            return ApiAuthResult(token = token, user = user)
        }

        // Error path mirrors the Dart else branch: detail/message passthrough.
        var detail = "Authentication failed ($statusCode)"
        try {
            val parsed = JSONObject(responseBody)
            detail = parsed.optString("detail").ifEmpty { parsed.optString("message").ifEmpty { detail } }
        } catch (_: Exception) {
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
        val decoded = Base64.getDecoder().decode(payload)
        return JSONObject(String(decoded, Charsets.UTF_8))
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
