package com.example.chat.data.remote

import com.example.chat.data.messaging.SupabaseRpc
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** PostgREST RPC client over OkHttp. Mirrors supabase_dart's .rpc(): POST
 *  {url}/rest/v1/rpc/{fn} with apikey + bearer token, params as JSON object;
 *  scalars/rows/maps returned as plain Kotlin values (null on void). */
class PostgrestRpcClient(
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val accessToken: suspend () -> String?,
    private val client: OkHttpClient = OkHttpClient(),
) : SupabaseRpc {

    override suspend fun rpc(function: String, params: Map<String, Any?>): Any? =
        withContext(Dispatchers.IO) {
            val body = JSONObject(params).toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/rpc/$function")
                .addHeader("apikey", publishableKey)
                .addHeader("Content-Type", "application/json")
                .apply {
                    accessToken()?.let { addHeader("Authorization", "Bearer $it") }
                }
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // PostgREST errors are JSON {message, details, hint, code};
                    // surface message so callers keep their Dart error strings.
                    val message = runCatching { JSONObject(text).optString("message") }
                        .getOrDefault("")
                    throw IOException(
                        if (message.isNotEmpty()) message
                        else "RPC $function failed with HTTP ${response.code}"
                    )
                }
                if (text.isBlank() || text == "null") return@withContext null
                val trimmed = text.trim()
                when {
                    trimmed.startsWith("{") -> jsonToMap(JSONObject(trimmed))
                    trimmed.startsWith("[") -> jsonToList(JSONArray(trimmed))
                    trimmed == "true" -> true
                    trimmed == "false" -> false
                    trimmed.toLongOrNull() != null -> trimmed.toLong()
                    trimmed.toDoubleOrNull() != null -> trimmed.toDouble()
                    else -> trimmed.removeSurrounding("\"")
                }
            }
        }

    companion object {
        fun jsonToMap(obj: JSONObject): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = jsonValue(obj.get(k))
            }
            return out
        }

        fun jsonToList(arr: JSONArray): List<Any?> =
            (0 until arr.length()).map { jsonValue(arr.get(it)) }

        private fun jsonValue(v: Any?): Any? = when (v) {
            null, JSONObject.NULL -> null
            is JSONObject -> jsonToMap(v)
            is JSONArray -> jsonToList(v)
            else -> v
        }
    }
}
