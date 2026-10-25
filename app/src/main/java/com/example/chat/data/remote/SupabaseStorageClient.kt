package com.example.chat.data.remote

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Supabase Storage REST client covering exactly what the api branch uses on
 * the status-media bucket: upload (POST object, cacheControl 3600, no
 * upsert), remove (bulk delete by prefixes), and createSignedUrl.
 */
class SupabaseStorageClient(
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val accessToken: suspend () -> String?,
    private val client: OkHttpClient = OkHttpClient(),
) {
    /** Mirrors storage.from(bucket).upload(path, file, FileOptions(cacheControl: '3600', upsert: false, contentType: mime)). */
    suspend fun upload(
        bucket: String,
        path: String,
        bytes: ByteArray,
        contentType: String,
    ): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$supabaseUrl/storage/v1/object/$bucket/$path")
            .addHeader("apikey", publishableKey)
            .addHeader("cache-control", "3600")
            .addHeader("x-upsert", "false")
            .apply {
                accessToken()?.let { addHeader("Authorization", "Bearer $it") }
            }
            .post(bytes.toRequestBody(contentType.toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Storage upload to $bucket failed with HTTP ${response.code}")
            }
        }
    }

    /** Mirrors storage.from(bucket).remove(paths) (bulk delete). */
    suspend fun remove(bucket: String, paths: List<String>): Unit = withContext(Dispatchers.IO) {
        val body = JSONObject().put("prefixes", JSONArray(paths)).toString()
        val request = Request.Builder()
            .url("$supabaseUrl/storage/v1/object/$bucket")
            .addHeader("apikey", publishableKey)
            .addHeader("Content-Type", "application/json")
            .apply {
                accessToken()?.let { addHeader("Authorization", "Bearer $it") }
            }
            .delete(body.toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Storage remove from $bucket failed with HTTP ${response.code}")
            }
        }
    }

    /** Mirrors storage.from(bucket).getPublicUrl(path) for public buckets
     *  (pure URL construction, no request). */
    fun getPublicUrl(bucket: String, path: String): String =
        "$supabaseUrl/storage/v1/object/public/$bucket/$path"

    /** Mirrors storage.from(bucket).createSignedUrl(path, expiresIn). */
    suspend fun createSignedUrl(
        bucket: String,
        path: String,
        expiresInSeconds: Int,
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put("expiresIn", expiresInSeconds).toString()
        val request = Request.Builder()
            .url("$supabaseUrl/storage/v1/object/sign/$bucket/$path")
            .addHeader("apikey", publishableKey)
            .addHeader("Content-Type", "application/json")
            .apply {
                accessToken()?.let { addHeader("Authorization", "Bearer $it") }
            }
            .post(body.toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Storage sign failed with HTTP ${response.code}")
            }
            val signedPath = JSONObject(text).optString("signedURL")
            if (signedPath.isEmpty()) throw IOException("Storage sign returned no signedURL")
            // The API returns a path relative to the project origin.
            if (signedPath.startsWith("http")) signedPath
            else "$supabaseUrl/storage/v1$signedPath"
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
