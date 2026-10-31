package com.example.chat.data.remote

import java.io.IOException
import android.util.Base64
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
        onProgress: ((transferred: Long, total: Long) -> Unit)? = null,
    ): Unit = withContext(Dispatchers.IO) {
        val bearer = accessToken()
        if (bytes.size >= RESUMABLE_THRESHOLD_BYTES &&
            uploadTus(bucket, path, bytes, contentType, bearer, onProgress)
        ) return@withContext

        val mediaType = contentType.toMediaType()
        val rawBody = bytes.toRequestBody(mediaType)
        val body = if (onProgress == null) rawBody else object : okhttp3.RequestBody() {
            override fun contentType() = rawBody.contentType()
            override fun contentLength() = rawBody.contentLength()
            override fun writeTo(sink: okio.BufferedSink) {
                var transferred = 0L
                val forwarding = object : okio.ForwardingSink(sink) {
                    override fun write(source: okio.Buffer, byteCount: Long) {
                        super.write(source, byteCount)
                        transferred += byteCount
                        onProgress.invoke(transferred, rawBody.contentLength())
                    }
                }
                rawBody.writeTo(okio.buffer(forwarding))
            }
        }
        val request = Request.Builder()
            .url("$supabaseUrl/storage/v1/object/$bucket/$path")
            .addHeader("apikey", publishableKey)
            .addHeader("cache-control", "3600")
            .addHeader("x-upsert", "false")
            .apply {
                bearer?.let { addHeader("Authorization", "Bearer $it") }
            }
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Storage upload to $bucket failed with HTTP ${response.code}")
            }
        }
    }

    /**
     * Supabase Storage's TUS endpoint for large media. Chunks are acknowledged by
     * Upload-Offset; on a dropped connection we HEAD the upload resource and resume
     * from the server's confirmed offset rather than restarting from byte zero.
     * Returns false only when the server does not expose TUS (404/405), allowing the
     * existing single-request upload path to remain compatible.
     */
    private fun uploadTus(
        bucket: String,
        path: String,
        bytes: ByteArray,
        contentType: String,
        bearer: String?,
        onProgress: ((Long, Long) -> Unit)?,
    ): Boolean {
        val origin = supabaseUrl.toHttpUrlOrNull() ?: throw IOException("Invalid storage origin")
        val metadata = listOf(
            "bucketName" to bucket,
            "objectName" to path,
            "contentType" to contentType,
            "cacheControl" to "3600",
        ).joinToString(",") { (key, value) ->
            "$key " + Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
        val create = Request.Builder()
            .url("$supabaseUrl/storage/v1/upload/resumable")
            .addHeader("apikey", publishableKey)
            .addHeader("Tus-Resumable", TUS_VERSION)
            .addHeader("Upload-Length", bytes.size.toString())
            .addHeader("Upload-Metadata", metadata)
            .addHeader("x-upsert", "false")
            .apply { bearer?.let { addHeader("Authorization", "Bearer $it") } }
            .post(ByteArray(0).toRequestBody(null))
            .build()

        val location = client.newCall(create).execute().use { response ->
            if (response.code == 404 || response.code == 405) return false
            if (response.code != 201) {
                throw IOException("Resumable upload initialization failed with HTTP ${response.code}")
            }
            response.header("Location") ?: throw IOException("Resumable upload did not return a location")
        }
        val uploadUrl = origin.resolve(location) ?: throw IOException("Invalid resumable upload location")
        if (uploadUrl.scheme != "https" || uploadUrl.host != origin.host || uploadUrl.port != origin.port) {
            throw IOException("Resumable upload location is outside the storage origin")
        }

        var offset = 0L
        var failures = 0
        onProgress?.invoke(0L, bytes.size.toLong())
        while (offset < bytes.size) {
            val end = minOf(bytes.size.toLong(), offset + TUS_CHUNK_BYTES)
            val chunk = bytes.copyOfRange(offset.toInt(), end.toInt())
            val patch = Request.Builder()
                .url(uploadUrl)
                .addHeader("apikey", publishableKey)
                .addHeader("Tus-Resumable", TUS_VERSION)
                .addHeader("Upload-Offset", offset.toString())
                .addHeader("Content-Type", "application/offset+octet-stream")
                .apply { bearer?.let { addHeader("Authorization", "Bearer $it") } }
                .patch(chunk.toRequestBody("application/offset+octet-stream".toMediaType()))
                .build()
            try {
                client.newCall(patch).execute().use { response ->
                    when (response.code) {
                        204 -> {
                            val acknowledged = response.header("Upload-Offset")?.toLongOrNull()
                                ?: throw IOException("TUS response omitted Upload-Offset")
                            if (acknowledged <= offset || acknowledged > bytes.size) {
                                throw IOException("TUS server returned an invalid upload offset")
                            }
                            offset = acknowledged
                            failures = 0
                            onProgress?.invoke(offset, bytes.size.toLong())
                        }
                        409 -> {
                            offset = queryTusOffset(uploadUrl, bearer, origin)
                            onProgress?.invoke(offset, bytes.size.toLong())
                        }
                        else -> throw IOException("Resumable upload chunk failed with HTTP ${response.code}")
                    }
                }
            } catch (error: IOException) {
                failures++
                if (failures > MAX_TUS_RETRIES) throw error
                offset = queryTusOffset(uploadUrl, bearer, origin)
                onProgress?.invoke(offset, bytes.size.toLong())
                try { Thread.sleep((250L shl (failures - 1)).coerceAtMost(2_000L)) } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Resumable upload interrupted")
                }
            }
        }
        return true
    }

    private fun queryTusOffset(uploadUrl: HttpUrl, bearer: String?, origin: HttpUrl): Long {
        if (uploadUrl.scheme != "https" || uploadUrl.host != origin.host || uploadUrl.port != origin.port) {
            throw IOException("Invalid resumable upload origin")
        }
        val head = Request.Builder()
            .url(uploadUrl)
            .addHeader("apikey", publishableKey)
            .addHeader("Tus-Resumable", TUS_VERSION)
            .apply { bearer?.let { addHeader("Authorization", "Bearer $it") } }
            .head()
            .build()
        return client.newCall(head).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unable to resume upload (HTTP ${response.code})")
            val offset = response.header("Upload-Offset")?.toLongOrNull()
                ?: throw IOException("Resume response omitted Upload-Offset")
            if (offset < 0L) throw IOException("Invalid resume offset")
            offset
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
        const val TUS_VERSION = "1.0.0"
        const val RESUMABLE_THRESHOLD_BYTES = 5 * 1024 * 1024
        const val TUS_CHUNK_BYTES = 512L * 1024L
        const val MAX_TUS_RETRIES = 4
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
