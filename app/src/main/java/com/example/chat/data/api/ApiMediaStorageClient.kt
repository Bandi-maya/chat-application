package com.example.chat.data.api

import com.example.chat.data.remote.MediaStorageBackend
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** MediaStorageBackend over the chat backend's POST /chat/upload. The server
 *  picks the stored filename and returns an absolute public URL; uploads are
 *  served publicly, so "signed" URLs are the public URL (there is no signing
 *  plane). There is also no server-side media delete: chat message deletion
 *  governs visibility and orphaned files are reclaimed server-side, so
 *  remove() is a no-op by design. */
class ApiMediaStorageClient(
    private val baseUrl: () -> String,
    private val tenantId: () -> String,
    private val token: () -> String?,
    private val client: OkHttpClient = com.example.chat.data.api.ChatyOkHttp.shared,
) : MediaStorageBackend {

    /** path -> server URL for files uploaded this session (the server
     *  renames the file, so the URL cannot be derived from the path). */
    private val uploadedUrls = java.util.concurrent.ConcurrentHashMap<String, String>()

    override suspend fun upload(
        bucket: String,
        path: String,
        bytes: ByteArray,
        contentType: String,
        onProgress: ((transferred: Long, total: Long) -> Unit)?,
    ): Unit = withContext(Dispatchers.IO) {
        val tok = token() ?: throw IOException("Not authenticated")
        val fileName = path.substringAfterLast('/')
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody(contentType.toMediaType()))
            .build()
        val request = Request.Builder()
            .url("${baseUrl().trimEnd('/')}/chat/upload")
            .header("Authorization", "Bearer $tok")
            .header("x-tenant-id", tenantId())
            .post(body)
            .build()
        onProgress?.invoke(0L, bytes.size.toLong())
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Upload failed (${response.code})")
            val url = JSONObject(response.body?.string() ?: "{}")
                .optJSONObject("data")?.optString("url").orEmpty()
            if (url.isEmpty()) throw IOException("Upload failed: empty url in response")
            uploadedUrls[path] = url
        }
        onProgress?.invoke(bytes.size.toLong(), bytes.size.toLong())
    }

    override fun getPublicUrl(bucket: String, path: String): String =
        uploadedUrls[path]
            ?: "${baseUrl().trimEnd('/')}/uploads/${path.substringAfterLast('/')}"

    override suspend fun createSignedUrl(bucket: String, path: String, expiresInSeconds: Int): String =
        getPublicUrl(bucket, path)

    override suspend fun remove(bucket: String, paths: List<String>) {
        // No server-side media delete plane; see class doc.
    }
}
