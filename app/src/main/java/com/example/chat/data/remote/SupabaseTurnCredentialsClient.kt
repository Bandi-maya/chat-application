package com.example.chat.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Loads short-lived ICE credentials from the repository's authenticated
 * Supabase Edge Function: POST /functions/v1/turn-credentials.
 *
 * The function authenticates the current Supabase user and returns STUN
 * addresses plus ephemeral TURN credentials when relay infrastructure is
 * configured. No TURN hostname, username, password, or shared secret is
 * embedded in the Android client.
 */
class SupabaseTurnCredentialsClient(
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val accessToken: suspend () -> String?,
    private val httpClient: OkHttpClient = defaultHttpClient,
) {
    data class Credentials(
        val iceServers: List<Map<String, Any?>>,
        val relayConfigured: Boolean,
        val expiresAtEpochSeconds: Long?,
    )

    suspend fun load(): Credentials = withContext(Dispatchers.IO) {
        require(supabaseUrl.startsWith("https://")) { "Supabase URL must use HTTPS." }
        require(publishableKey.isNotBlank()) { "Supabase publishable key is missing." }
        val bearer = accessToken()?.takeIf { it.isNotBlank() }
            ?: throw SecurityException("Sign in to load secure ICE credentials.")

        val request = Request.Builder()
            .url("${supabaseUrl.trimEnd('/')}/functions/v1/turn-credentials")
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer $bearer")
            .header("Accept", "application/json")
            .post("{}".toRequestBody(JSON))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("TURN credential service returned HTTP ${response.code}.")
            }
            val body = response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw IOException("TURN credential service returned an empty response.")
            val json = JSONObject(body)
            val rawServers = json.optJSONArray("ice_servers")
                ?: throw IOException("TURN credential response has no ICE server list.")
            val servers = parseServers(rawServers)
            if (servers.isEmpty()) throw IOException("TURN credential service returned no usable ICE servers.")

            val hasTurn = servers.any { entry ->
                val urls = entry["urls"]
                when (urls) {
                    is String -> isTurnUrl(urls)
                    is List<*> -> urls.any { it?.toString()?.let(::isTurnUrl) == true }
                    else -> false
                }
            }
            val relayConfigured = json.optBoolean("relay_configured", hasTurn)
            if (relayConfigured && !hasTurn) {
                throw IOException("TURN relay is marked configured but no TURN URL was returned.")
            }
            Credentials(
                iceServers = servers,
                relayConfigured = relayConfigured && hasTurn,
                expiresAtEpochSeconds = json.optLong("expires_at").takeIf { it > 0L },
            )
        }
    }

    private fun parseServers(raw: JSONArray): List<Map<String, Any?>> {
        if (raw.length() > MAX_ICE_SERVERS) throw IOException("Too many ICE servers in response.")
        val result = ArrayList<Map<String, Any?>>(raw.length())
        for (index in 0 until raw.length()) {
            val item = raw.optJSONObject(index) ?: continue
            val rawUrls = item.opt("urls")
            val urls: Any = when (rawUrls) {
                is String -> rawUrls.also { validateIceUrl(it) }
                is JSONArray -> {
                    val array = rawUrls
                    val list = ArrayList<String>(array.length())
                    for (urlIndex in 0 until array.length()) {
                        val url = array.optString(urlIndex)
                        if (url.isNotBlank()) {
                            validateIceUrl(url)
                            list += url
                        }
                    }
                    if (list.isEmpty()) continue
                    list
                }
                else -> continue
            }
            val entry = linkedMapOf<String, Any?>("urls" to urls)
            item.optString("username").takeIf { it.isNotBlank() }?.let { entry["username"] = it }
            item.optString("credential").takeIf { it.isNotBlank() }?.let { entry["credential"] = it }
            result += entry
        }
        return result
    }

    private fun validateIceUrl(url: String) {
        if (!url.startsWith("stun:", ignoreCase = true) &&
            !url.startsWith("stuns:", ignoreCase = true) &&
            !url.startsWith("turn:", ignoreCase = true) &&
            !url.startsWith("turns:", ignoreCase = true)
        ) {
            throw IOException("TURN credential service returned an unsupported ICE URL.")
        }
    }

    private fun isTurnUrl(url: String): Boolean =
        url.startsWith("turn:", ignoreCase = true) || url.startsWith("turns:", ignoreCase = true)

    companion object {
        private const val MAX_ICE_SERVERS = 16
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val defaultHttpClient: OkHttpClient = OkHttpClient.Builder()
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }
}
