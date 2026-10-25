package com.example.chat.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Minimal GoTrue (Supabase Auth) REST client.
 * Mirrors the auth surface used by lib/data/services/backend_service.dart:
 * signUp, signInWithPassword, resetPasswordForEmail, refresh, getUser.
 */
class SupabaseAuthClient(
    private val baseUrl: String = SUPABASE_URL,
    private val anonKey: String = SUPABASE_PUBLISHABLE_KEY,
    private val httpClient: OkHttpClient = defaultHttpClient,
) {
    companion object {
        // Same project the Flutter app targets (values ship in the Flutter CI
        // as dart-defines; publishable keys are client-embeddable by design).
        const val SUPABASE_URL = "https://dntnxeanubswyswahdnj.supabase.co"
        const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_gFpVYJctaDkRRgttvnUl-A_TKu81hFF"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private val defaultHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }



    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class GoTrueUser(
        val id: String,
        val email: String? = null,
        val phone: String? = null,
        @SerialName("user_metadata") val userMetadata: JsonObject? = null,
    )

    @Serializable
    data class AuthSession(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("token_type") val tokenType: String = "bearer",
        @SerialName("expires_in") val expiresIn: Long = 3600,
        val user: GoTrueUser? = null,
    )

    @Serializable
    data class AuthResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("token_type") val tokenType: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        val user: GoTrueUser? = null,
    )

    class AuthException(message: String, val statusCode: Int? = null) : Exception(message)

    private fun endpoint(path: String) = "$baseUrl/auth/v1$path"

    private suspend fun post(path: String, body: JsonObject, bearer: String? = null): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(endpoint(path))
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
            if (bearer != null) builder.addHeader("Authorization", "Bearer $bearer")
            val request = builder.post(body.toString().toRequestBody(JSON_MEDIA)).build()
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw AuthException(extractError(text) ?: "Auth request failed (${response.code}).", response.code)
                }
                text
            }
        }

    private fun extractError(body: String): String? = runCatching {
        val obj = json.parseToJsonElement(body) as? JsonObject ?: return null
        (obj["msg"] ?: obj["error_description"] ?: obj["error"])?.toString()?.trim('"')
    }.getOrNull()

    /** Mirrors supabase signUp with emailRedirectTo chaty://login-callback/. */
    suspend fun signUp(email: String, password: String, data: JsonObject): AuthResponse {
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            put("data", data)
        }
        // gotrue uses the redirectTo query param on signup.
        val raw = post("/signup?redirect_to=chaty%3A%2F%2Flogin-callback%2F", body)
        return json.decodeFromString(AuthResponse.serializer(), raw)
    }

    /** Mirrors signInWithPassword (grant_type=password). */
    suspend fun signInWithPassword(email: String, password: String): AuthSession {
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
        }
        val raw = post("/token?grant_type=password", body)
        return json.decodeFromString(AuthSession.serializer(), raw)
    }

    /** Mirrors resetPasswordForEmail(redirectTo: chaty://reset-password/). */
    suspend fun resetPasswordForEmail(email: String) {
        val body = buildJsonObject { put("email", email) }
        post("/recover?redirect_to=chaty%3A%2F%2Freset-password%2F", body)
    }

    /** Refresh an existing session (supabase_flutter's automatic refresh). */
    suspend fun refreshSession(refreshToken: String): AuthSession {
        val body = buildJsonObject { put("refresh_token", refreshToken) }
        val raw = post("/token?grant_type=refresh_token", body)
        return json.decodeFromString(AuthSession.serializer(), raw)
    }

    suspend fun getUser(accessToken: String): GoTrueUser {
        val raw = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(endpoint("/user"))
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw AuthException("Session is no longer valid.")
                text
            }
        }
        return json.decodeFromString(GoTrueUser.serializer(), raw)
    }

    /** Mirrors verifyOTP(email:, token:, type: OtpType.recovery). */
    suspend fun verifyRecoveryOtp(email: String, token: String): AuthSession {
        val body = buildJsonObject {
            put("email", email)
            put("token", token)
            put("type", "recovery")
        }
        val raw = post("/verify", body)
        return json.decodeFromString(AuthSession.serializer(), raw)
    }

    /** Mirrors updateUser(UserAttributes(password:)). Requires a bearer token. */
    suspend fun updateUserPassword(accessToken: String, password: String): GoTrueUser {
        val body = buildJsonObject { put("password", password) }
        val raw = put("/user", body, bearer = accessToken)
        return json.decodeFromString(GoTrueUser.serializer(), raw)
    }

    /** Mirrors signOut()'s server-side logout; callers still clear the local
     *  session when this fails, matching gotrue-dart. */
    suspend fun signOutRemote(accessToken: String) {
        post("/logout", buildJsonObject {}, bearer = accessToken)
    }

    private suspend fun put(path: String, body: JsonObject, bearer: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(endpoint(path))
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $bearer")
                .put(body.toString().toRequestBody(JSON_MEDIA))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw AuthException(extractError(text) ?: "Auth request failed (${response.code}).", response.code)
                }
                text
            }
        }
}
