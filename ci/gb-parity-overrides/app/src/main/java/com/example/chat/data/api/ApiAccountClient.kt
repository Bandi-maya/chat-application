package com.example.chat.data.api

import org.json.JSONException
import org.json.JSONObject

/** Binary transport seam for multipart uploads (the text ApiHttpTransport
 *  cannot carry photo bytes). Implemented by the integrator over OkHttp. */
fun interface ApiBinaryTransport {
    /** Returns (statusCode, body); throws on transport failure. */
    fun execute(method: String, url: String, headers: Map<String, String>, body: ByteArray): Pair<Int, String>
}

/** Context-free port of the api branch's REST account surface used by
 *  account_settings_screen.dart:
 *  - ApiBackendService.updateOrganizationRegistrationSupport
 *  - ApiBackendService.uploadProfilePhoto
 *
 *  Endpoint layout (exactly the Dart source):
 *  - registration: PATCH {base}/organization/<id>/registration (NO fallback)
 *  - photo:        POST  {base}/app-users/upload-photo (404 -> /api/v1/...)
 *  Both carry x-tenant-id and, when a session token exists, Bearer auth. */
class ApiAccountClient(
    private val baseUrl: () -> String,
    private val tenantId: () -> String,
    private val token: () -> String?,
    private val transport: ApiHttpTransport,
    private val binaryTransport: ApiBinaryTransport,
) {

    /** Dart updateOrganizationRegistrationSupport: returns statusCode == 200,
     *  no /api/v1 fallback, no error-body parsing. */
    fun updateRegistrationSupport(
        organizationIdOrTenantId: String,
        allowUserRegistration: Boolean,
    ): Boolean {
        val clean = organizationIdOrTenantId.trim()
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "x-tenant-id" to tenantId(),
        )
        token()?.let { headers["Authorization"] = "Bearer $it" }
        val body = JSONObject().put("allow_user_registration", allowUserRegistration).toString()
        val response = transport.execute(
            "PATCH", "${baseUrl()}/organization/$clean/registration", headers, body,
        )
        return response.first == 200
    }

    /** Dart uploadProfilePhoto: multipart POST of field `file`; 404 retries
     *  against /api/v1; 200 returns (data.avatarUrl ?: data.url ?: ''); the
     *  current-user persistence stays with the ApiBackendService facade. */
    fun uploadProfilePhoto(filename: String, bytes: ByteArray): String {
        // Capture base/tenant/token ONCE: primary and the /api/v1 retry use only these. A blank token is not a session.
        val tok = token()?.takeIf { it.isNotBlank() } ?: throw Exception("Not authenticated")
        val base = baseUrl(); val tenant = tenantId()
        val unchanged = { baseUrl() == base && tenantId() == tenant && token() == tok }
        return upload(base, tenant, tok, filename, bytes) { if (!unchanged()) throw ApiScopeChangedException() }
    }

    /** Account-bound form: routing, tenant and token come ONLY from [expected]; [isCurrent] (authSnapshot() === expected)
     *  is checked before each request and again before the URL is returned. HTTP runs outside any lock. The caller
     *  publishes the result to the local profile inside a short withScope(expected) callback. */
    fun uploadProfilePhoto(expected: ApiAccountScope, isCurrent: () -> Boolean, filename: String, bytes: ByteArray): String {
        val tok = expected.token?.takeIf { it.isNotBlank() && !expected.userId.isNullOrBlank() } ?: throw Exception("Not authenticated")
        return upload(expected.baseUrl, expected.tenantId, tok, filename, bytes) { if (!isCurrent()) throw ApiScopeChangedException() }
    }

    private fun upload(base: String, tenant: String, tok: String, filename: String, bytes: ByteArray, check: () -> Unit): String {
        val headers = mapOf(
            "Authorization" to "Bearer $tok",
            "x-tenant-id" to tenant,
            "Content-Type" to "multipart/form-data; boundary=$BOUNDARY",
        )
        val body = buildMultipartBody(safeMultipartFilename(filename), bytes)

        val response: Pair<Int, String> = try {
            check()
            val primary = binaryTransport.execute("POST", "$base/app-users/upload-photo", headers, body)
            check()   // switch while in flight: no retry under a stale account
            if (primary.first == 404) {
                binaryTransport.execute("POST", "$base/api/v1/app-users/upload-photo", headers, body)
            } else {
                primary
            }
        } catch (e: ApiScopeChangedException) {
            throw e
        } catch (e: Exception) {
            throw Exception("Unable to reach server to upload photo: $e")
        }
        check()   // never release a result into a replaced account

        if (response.first == 200) {
            val parsed = JSONObject(response.second)
            val data = if (parsed.has("data")) parsed.get("data") else parsed
            if (data is JSONObject) {
                return (if (data.has("avatarUrl") && !data.isNull("avatarUrl")) data.get("avatarUrl")
                    else if (data.has("url") && !data.isNull("url")) data.get("url")
                    else "").toString()
            }
            return ""
        }

        var detail = "Failed to upload profile photo (${response.first})"
        try {
            val parsed = JSONObject(response.second)
            detail = parsed.optString("detail").ifEmpty { parsed.optString("message").ifEmpty { detail } }
        } catch (_: Exception) {
        }
        throw Exception(detail)
    }

    private fun safeMultipartFilename(value: String): String = value.map { ch ->
        if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '.' || ch == '_' || ch == '-') ch else '_'
    }.joinToString("").trim('.', '_').take(120).ifBlank { "upload.bin" }

    private fun buildMultipartBody(filename: String, bytes: ByteArray): ByteArray {
        // Dart http.MultipartFile('file', ...) without an explicit content
        // type sends application/octet-stream.
        val head = "--$BOUNDARY\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n"
        val tail = "\r\n--$BOUNDARY--\r\n"
        return head.toByteArray(Charsets.UTF_8) + bytes + tail.toByteArray(Charsets.UTF_8)
    }

    companion object {
        /** Fixed boundary; servers key off the header, not the value. */
        const val BOUNDARY = "----ChatyAccountBoundary7MA4YWxkTrZu0gW"
    }
}
