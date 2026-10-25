package com.example.chat.data.api

import java.io.File
import org.json.JSONObject

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

    data class Response(val statusCode: Int, val body: String)

    class UploadException(message: String) : Exception(message)

    /** Exact port of ApiBackendService.uploadMedia. Returns the server's
     *  data map (url, path, size, mime keys as the backend supplies them). */
    fun uploadMedia(file: File): Map<String, Any> =
        uploadMedia(file.name, file.readBytes())

    /** Byte-array form for callers that already hold the payload
     *  (ChatMediaService's post-compression bytes; the wire body is the
     *  same multipart part either way). */
    fun uploadMedia(filename: String, bytes: ByteArray): Map<String, Any> {
        val bearer = token() ?: throw UploadException("Not authenticated")
        val base = restApiBaseUrl().trimEnd('/')

        val response: Response = try {
            val primary = transport.upload(
                "$base/chat/upload", "Bearer $bearer", tenantId(), filename, bytes,
            )
            if (primary.statusCode == 404) {
                transport.upload(
                    "$base/api/v1/chat/upload", "Bearer $bearer", tenantId(), filename, bytes,
                )
            } else {
                primary
            }
        } catch (e: UploadException) {
            throw e
        } catch (e: Exception) {
            throw UploadException("Failed to connect to upload server: $e")
        }

        if (response.statusCode == 200) {
            val body = JSONObject(response.body)
            val data = if (body.has("data")) body.get("data") else body
            val obj = data as? JSONObject ?: body
            return obj.keys().asSequence().associateWith { obj.get(it) }
        }

        var detail = "Failed to upload media (${response.statusCode})"
        try {
            val body = JSONObject(response.body)
            detail = body.optString("detail").ifEmpty { body.optString("message").ifEmpty { detail } }
        } catch (_: Exception) {
        }
        throw UploadException(detail)
    }


    /** Device transport over OkHttp, mirroring http.MultipartRequest: one
     *  'file' part, Bearer + x-tenant-id headers, no extra fields. */
    class OkHttpTransport : Transport {
        private val client = okhttp3.OkHttpClient()

        override fun upload(
            url: String,
            authorization: String,
            tenantId: String,
            filename: String,
            fileBytes: ByteArray,
        ): Response {
            val body = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    filename,
                    okhttp3.RequestBody.create(null, fileBytes),
                )
                .build()
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
