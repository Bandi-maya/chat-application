package com.example.chat.data.api

import java.io.File
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okio.buffer

/** Media upload plane of the api branch's ApiBackendService.uploadMedia
 *  (lib/data/services/api_backend_service.dart:1569-1606): POST a single
 *  multipart 'file' part to {restApiBaseUrl}/chat/upload with Bearer auth and
 *  the x-tenant-id header, retry once against /api/v1/chat/upload when the
 *  primary path 404s, decode the 200 body as {data: {...}} (falling back to
 *  the bare body), and surface the server's detail/message on any other
 *  status. The service layer (chat_media_service.dart:80-166) owns picking,
 *  compression and attachment construction; this client is only the wire
 *  call, so it stays context-free and JVM-testable through the injected
 *  [Transport] seam exactly like ApiRealtimeClient's socket seam.
 */
class ApiMediaUploadClient(
    private val restApiBaseUrl: () -> String,
    private val tenantId: () -> String,
    private val token: () -> String?,
    private val transport: Transport = OkHttpTransport(),
) {

    /** Minimal transport seam so the client is JVM-testable without OkHttp. */
    interface Transport {
        /** POSTs [fileBytes] as the multipart 'file' part and returns the
         *  status code plus decoded body text. */
        fun upload(
            url: String,
            authorization: String,
            tenantId: String,
            filename: String,
            fileBytes: ByteArray,
        ): Response
    }

    /** Optional progress-capable seam; existing JVM fakes remain source-compatible. */
    interface ProgressTransport : Transport {
        fun uploadWithProgress(
            url: String,
            authorization: String,
            tenantId: String,
            filename: String,
            fileBytes: ByteArray,
            onProgress: (transferred: Long, total: Long) -> Unit,
        ): Response
    }

    /** File-backed multipart path: never materializes a voice note/video in RAM. */
    interface FileProgressTransport : Transport {
        fun uploadFileWithProgress(
            url: String,
            authorization: String,
            tenantId: String,
            filename: String,
            file: File,
            onProgress: (transferred: Long, total: Long) -> Unit,
        ): Response
    }

    data class Response(val statusCode: Int, val body: String)

    class UploadException(message: String) : Exception(message)

    /** Exact port of ApiBackendService.uploadMedia using a streaming file body. */
    fun uploadMedia(file: File): Map<String, Any> = uploadMedia(file) { _, _ -> }

    fun uploadMedia(file: File, onProgress: (transferred: Long, total: Long) -> Unit): Map<String, Any> {
        val bearer = token() ?: throw UploadException("Not authenticated")
        return uploadFile(file, bearer, restApiBaseUrl(), tenantId(), onProgress)
    }

    private fun uploadFile(
        file: File,
        bearer: String,
        baseUrl: String,
        requestTenantId: String,
        onProgress: (transferred: Long, total: Long) -> Unit,
        checkScope: () -> Unit = {},
    ): Map<String, Any> {
        if (!file.isFile || !file.canRead() || file.length() <= 0L) {
            throw UploadException("Selected media file is unavailable or empty.")
        }
        if (bearer.isBlank()) throw UploadException("Not authenticated")
        val base = baseUrl.trimEnd('/')
        val fileTransport = transport as? FileProgressTransport
            ?: throw UploadException("Streaming upload is unavailable; refusing to load the full file into memory.")
        val safeFilename = safeMultipartFilename(file.name)
        val response = try {
            checkScope()
            val primary = fileTransport.uploadFileWithProgress(
                "$base/chat/upload", "Bearer $bearer", requestTenantId, safeFilename, file, onProgress,
            )
            checkScope()
            if (primary.statusCode == 404) {
                fileTransport.uploadFileWithProgress(
                    "$base/api/v1/chat/upload", "Bearer $bearer", requestTenantId, safeFilename, file, onProgress,
                )
            } else primary
        } catch (error: ApiScopeChangedException) {
            throw error
        } catch (error: UploadException) {
            throw error
        } catch (error: Exception) {
            throw UploadException("Failed to stream media to custom backend: $error")
        }
        checkScope()
        return decodeResponse(response)
    }

    private fun safeMultipartFilename(value: String): String {
        val sanitized = value.map { ch ->
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '.' || ch == '_' || ch == '-') ch else '_'
        }.joinToString("").trim('.', '_')
        if (sanitized.isBlank()) return "upload.bin"
        if (sanitized.length <= 120) return sanitized
        val dot = sanitized.lastIndexOf('.')
        return if (dot in 1 until sanitized.length) {
            val ext = sanitized.substring(dot).take(15)
            val name = sanitized.substring(0, dot).take(120 - ext.length)
            "$name$ext"
        } else {
            sanitized.take(120)
        }
    }

    /** Account-scoped file upload uses the captured account credentials, never live getters. */
    fun uploadMedia(
        expectedScope: ApiAccountScope,
        isScopeCurrent: () -> Boolean,
        file: File,
        onProgress: (transferred: Long, total: Long) -> Unit,
    ): Map<String, Any> {
        if (!isScopeCurrent()) throw ApiScopeChangedException()
        val bearer = expectedScope.token?.takeIf { it.isNotBlank() && !expectedScope.userId.isNullOrBlank() }
            ?: throw UploadException("Not authenticated")
        val result = uploadFile(file, bearer, expectedScope.baseUrl, expectedScope.tenantId, onProgress) {
            if (!isScopeCurrent()) throw ApiScopeChangedException()
        }
        if (!isScopeCurrent()) throw ApiScopeChangedException()
        return result
    }

    /** Byte-array form for callers that already hold the payload. */
    fun uploadMedia(
        filename: String,
        bytes: ByteArray,
        onProgress: ((transferred: Long, total: Long) -> Unit)? = null,
    ): Map<String, Any> {
        val bearer = token() ?: throw UploadException("Not authenticated")
        return uploadBytes(filename, bytes, bearer, restApiBaseUrl(), tenantId(), onProgress)
    }

    private fun uploadBytes(
        filename: String,
        bytes: ByteArray,
        bearer: String,
        baseUrl: String,
        requestTenantId: String,
        onProgress: ((transferred: Long, total: Long) -> Unit)?,
        checkScope: () -> Unit = {},
    ): Map<String, Any> {
        if (bearer.isBlank()) throw UploadException("Not authenticated")
        if (bytes.isEmpty()) throw UploadException("Selected media file is unavailable or empty.")
        val base = baseUrl.trimEnd('/')
        val safeFilename = safeMultipartFilename(filename)
        val response: Response = try {
            fun send(url: String): Response =
                if (onProgress != null && transport is ProgressTransport) {
                    transport.uploadWithProgress(url, "Bearer $bearer", requestTenantId, safeFilename, bytes, onProgress)
                } else {
                    transport.upload(url, "Bearer $bearer", requestTenantId, safeFilename, bytes)
                }
            checkScope()
            val primary = send("$base/chat/upload")
            checkScope()
            if (primary.statusCode == 404) send("$base/api/v1/chat/upload") else primary
        } catch (error: ApiScopeChangedException) {
            throw error
        } catch (error: UploadException) {
            throw error
        } catch (error: Exception) {
            throw UploadException("Failed to connect to upload server: $error")
        }
        checkScope()
        return decodeResponse(response)
    }

    /**
     * Account-scoped byte upload uses the captured account credentials and
     * refuses to return an attachment reference if the account changed.
     */
    fun uploadMedia(
        expectedScope: ApiAccountScope,
        isScopeCurrent: () -> Boolean,
        filename: String,
        bytes: ByteArray,
        onProgress: ((transferred: Long, total: Long) -> Unit)? = null,
    ): Map<String, Any> {
        if (!isScopeCurrent()) throw ApiScopeChangedException()
        val bearer = expectedScope.token?.takeIf { it.isNotBlank() && !expectedScope.userId.isNullOrBlank() }
            ?: throw UploadException("Not authenticated")
        val result = uploadBytes(filename, bytes, bearer, expectedScope.baseUrl, expectedScope.tenantId, onProgress) {
            if (!isScopeCurrent()) throw ApiScopeChangedException()
        }
        if (!isScopeCurrent()) throw ApiScopeChangedException()
        return result
    }

    /**
     * Parses both supported server envelopes: {data:{...}} and the legacy
     * top-level object. Never returns a partial success object for malformed
     * or non-2xx responses.
     */
    private fun decodeResponse(response: Response): Map<String, Any> {
        val root = runCatching { org.json.JSONObject(response.body) }.getOrNull()
        if (response.statusCode !in 200..299) {
            val detail = root?.let { json ->
                json.optString("detail").takeIf { it.isNotBlank() }
                    ?: json.optString("message").takeIf { it.isNotBlank() }
                    ?: json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: json.optString("error").takeIf { it.isNotBlank() }
            }
            throw UploadException(detail ?: "Failed to upload media (${response.statusCode})")
        }
        val payload = root?.let { json ->
            if (json.has("data") && !json.isNull("data")) {
                json.optJSONObject("data")
            } else json
        } ?: throw UploadException("Upload response did not contain a media object.")

        return ApiChatClient.deepMap(payload)
            .mapNotNull { (key, value) -> value?.let { key to it } }
            .toMap()
    }

    /** Device transport over OkHttp, mirroring http.MultipartRequest: one
     *  'file' part, Bearer + x-tenant-id headers, no extra fields. */
    class OkHttpTransport : ProgressTransport, FileProgressTransport {
        private val client = okhttp3.OkHttpClient()

        override fun upload(
            url: String,
            authorization: String,
            tenantId: String,
            filename: String,
            fileBytes: ByteArray,
        ): Response = uploadWithProgress(url, authorization, tenantId, filename, fileBytes) { _, _ -> }

        override fun uploadFileWithProgress(
            url: String,
            authorization: String,
            tenantId: String,
            filename: String,
            file: File,
            onProgress: (Long, Long) -> Unit,
        ): Response {
            val mime = when (filename.substringAfterLast('.', "").lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "mp4" -> "video/mp4"
                "m4a" -> "audio/mp4"
                "mp3" -> "audio/mpeg"
                "pdf" -> "application/pdf"
                else -> "application/octet-stream"
            }
            val fileBody = file.asRequestBody(mime.toMediaType())
            val multipart = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file", filename, fileBody)
                .build()
            val total = multipart.contentLength()
            val body = object : okhttp3.RequestBody() {
                override fun contentType() = multipart.contentType()
                override fun contentLength() = total
                override fun writeTo(sink: okio.BufferedSink) {
                    var transferred = 0L
                    val forwarding = object : okio.ForwardingSink(sink) {
                        override fun write(source: okio.Buffer, byteCount: Long) {
                            super.write(source, byteCount)
                            transferred += byteCount
                            onProgress(transferred, total)
                        }
                    }
                    val buffered = forwarding.buffer()
                    multipart.writeTo(buffered)
                    buffered.flush()
                }
            }
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Authorization", authorization)
                .header("x-tenant-id", tenantId)
                .post(body)
                .build()
            client.newCall(request).execute().use { resp ->
                return Response(resp.code, resp.body?.string() ?: "")
            }
        }

        override fun uploadWithProgress(
            url: String,
            authorization: String,
            tenantId: String,
            filename: String,
            fileBytes: ByteArray,
            onProgress: (Long, Long) -> Unit,
        ): Response {
            val mime = when (filename.substringAfterLast('.', "").lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "mp4" -> "video/mp4"
                "m4a" -> "audio/mp4"
                "mp3" -> "audio/mpeg"
                "pdf" -> "application/pdf"
                else -> "application/octet-stream"
            }
            val multipart = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file", filename, okhttp3.RequestBody.create(mime.toMediaType(), fileBytes))
                .build()
            val total = multipart.contentLength()
            val body = object : okhttp3.RequestBody() {
                override fun contentType() = multipart.contentType()
                override fun contentLength() = total
                override fun writeTo(sink: okio.BufferedSink) {
                    var transferred = 0L
                    val forwarding = object : okio.ForwardingSink(sink) {
                        override fun write(source: okio.Buffer, byteCount: Long) {
                            super.write(source, byteCount)
                            transferred += byteCount
                            onProgress(transferred, total)
                        }
                    }
                    val buffered = forwarding.buffer()
                    multipart.writeTo(buffered)
                    buffered.flush()
                }
            }
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Authorization", authorization)
                .header("x-tenant-id", tenantId)
                .post(body)
                .build()
            client.newCall(request).execute().use { resp ->
                return Response(resp.code, resp.body?.string() ?: "")
            }
        }
    }
}
