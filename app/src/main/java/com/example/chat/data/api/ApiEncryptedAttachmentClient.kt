package com.example.chat.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Client for the backend's encrypted attachment transient queue
 *  (the /e2ee/attachments endpoints). The file bytes handed to [upload] must already be
 *  the client-side ciphertext - this client never sees plaintext or keys.
 *  Download returns the raw ciphertext bytes; [confirmDelivered] tells the
 *  server the blob can be deleted (deleted after delivery, by design). */
class ApiEncryptedAttachmentClient(
    private val baseUrl: () -> String,
    private val tenantId: () -> String,
    private val token: suspend () -> String?,
    private val client: OkHttpClient = com.example.chat.data.api.ChatyOkHttp.shared,
) {
    data class UploadResult(val attachmentId: String, val sizeBytes: Long)

    private suspend fun requestBuilder(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .addHeader("x-tenant-id", tenantId())
        token()?.let { builder.addHeader("Authorization", "Bearer $it") }
        return builder
    }

    private fun base() = baseUrl().trimEnd('/')

    /** Uploads [ciphertext] for [conversationId]; returns the attachment id.
     *  Throws with the server's detail on 413 (file too large) or 429
     *  (per-user pending quota full). */
    suspend fun upload(conversationId: String, ciphertext: ByteArray): UploadResult =
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("conversation_id", conversationId)
                .addFormDataPart(
                    "file", "blob.bin",
                    ciphertext.toRequestBody("application/octet-stream".toMediaType()),
                )
                .build()
            val request = requestBuilder("${base()}/e2ee/attachments/upload").post(body).build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.code == 404) {
                    // dual-mount fallback
                    val retry = requestBuilder("${base()}/api/v1/e2ee/attachments/upload").post(body).build()
                    client.newCall(retry).execute().use { r2 ->
                        return@withContext parseUpload(r2.code, r2.body?.string().orEmpty())
                    }
                }
                parseUpload(response.code, text)
            }
        }

    private fun parseUpload(code: Int, text: String): UploadResult {
        if (code !in 200..299) {
            val detail = runCatching {
                val parsed = JSONObject(text)
                if (parsed.has("detail")) parsed.optString("detail")
                else parsed.optJSONObject("error")?.optString("message").orEmpty()
            }.getOrDefault("")
            throw IOException(if (detail.isNotEmpty()) detail else "Attachment upload failed (HTTP $code)")
        }
        val data = JSONObject(text).getJSONObject("data")
        return UploadResult(
            attachmentId = data.getString("attachment_id"),
            sizeBytes = data.getLong("size_bytes"),
        )
    }

    /** Downloads the ciphertext. Throws FileNotFoundException when the blob
     *  was already delivered and deleted. */
    suspend fun download(attachmentId: String): ByteArray = withContext(Dispatchers.IO) {
        val request = requestBuilder("${base()}/e2ee/attachments/$attachmentId").get().build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) {
                val retry = requestBuilder("${base()}/api/v1/e2ee/attachments/$attachmentId").get().build()
                client.newCall(retry).execute().use { r2 ->
                    if (r2.code == 404) throw java.io.FileNotFoundException("Attachment delivered or expired.")
                    if (r2.code !in 200..299) throw IOException("Attachment download failed (HTTP ${r2.code})")
                    return@withContext r2.body!!.bytes()
                }
            }
            if (response.code == 404) throw java.io.FileNotFoundException("Attachment delivered or expired.")
            if (response.code !in 200..299) throw IOException("Attachment download failed (HTTP ${response.code})")
            response.body!!.bytes()
        }
    }

    /** Confirms receipt; the server deletes the blob once every other
     *  participant has confirmed. Idempotent. */
    suspend fun confirmDelivered(attachmentId: String): Unit = withContext(Dispatchers.IO) {
        val request = requestBuilder("${base()}/e2ee/attachments/$attachmentId/delivered")
            .post("".toRequestBody(null))
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) {
                val retry = requestBuilder("${base()}/api/v1/e2ee/attachments/$attachmentId/delivered")
                    .post("".toRequestBody(null)).build()
                client.newCall(retry).execute().use { r2 ->
                    if (r2.code !in 200..299 && r2.code != 404) {
                        throw IOException("Delivery confirmation failed (HTTP ${r2.code})")
                    }
                    return@withContext
                }
            }
            if (response.code !in 200..299 && response.code != 404) {
                throw IOException("Delivery confirmation failed (HTTP ${response.code})")
            }
        }
    }
}
