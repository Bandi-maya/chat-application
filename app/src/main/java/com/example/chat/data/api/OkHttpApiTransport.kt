package com.example.chat.data.api

import com.example.chat.data.demo.DemoMode
import com.example.chat.data.demo.DemoModeOfflineException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** OkHttp implementation of the ApiAuthClient transport seam. */
class OkHttpApiTransport(private val allowRedirects: Boolean = true) : ApiHeaderHttpTransport {
    private val client = ChatyOkHttp.shared.newBuilder()
        .followRedirects(allowRedirects).followSslRedirects(allowRedirects)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): Pair<Int, String> = executeWithHeaders(method, url, headers, body).let { it.code to it.body }

    override fun executeWithHeaders(method: String, url: String, headers: Map<String, String>, body: String?): ApiHeaderResponse {
        // Offline demo: while a demo session is active, or a request carries the
        // synthetic demo token, nothing leaves the device; fail closed before
        // any socket is opened (covers unsigned requests too).
        if (DemoMode.active || headers["Authorization"] == "Bearer ${DemoMode.TOKEN}") throw DemoModeOfflineException()
        val builder = Request.Builder().url(url)
        for ((k, v) in headers) builder.addHeader(k, v)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(
                (body ?: "").toRequestBody("application/json".toMediaType()),
            )
            "PUT" -> builder.put(
                (body ?: "").toRequestBody("application/json".toMediaType()),
            )
            "PATCH" -> builder.patch(
                (body ?: "").toRequestBody("application/json".toMediaType()),
            )
            "DELETE" -> if (body == null) builder.delete() else builder.delete(body.toRequestBody("application/json".toMediaType()))
            else -> throw IllegalArgumentException("unsupported method $method")
        }
        client.newCall(builder.build()).execute().use { response ->
            return ApiHeaderResponse(response.code, response.body?.string() ?: "", response.header("ETag"))
        }
    }
}

/** Binary (multipart) transport for ApiAccountClient.uploadProfilePhoto; the caller sets Content-Type. */
class OkHttpApiBinaryTransport : ApiBinaryTransport {
    private val client = ChatyOkHttp.shared.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun execute(method: String, url: String, headers: Map<String, String>, body: ByteArray): Pair<Int, String> {
        // Offline demo: same fail-closed guard for binary (profile photo) uploads.
        if (DemoMode.active || headers["Authorization"] == "Bearer ${DemoMode.TOKEN}") throw DemoModeOfflineException()
        val builder = Request.Builder().url(url)
        var contentType: String? = null
        for ((k, v) in headers) {
            if (k.equals("Content-Type", ignoreCase = true)) contentType = v else builder.addHeader(k, v)
        }
        builder.method(method, body.toRequestBody(contentType?.toMediaType()))
        client.newCall(builder.build()).execute().use { response ->
            return response.code to (response.body?.string() ?: "")
        }
    }
}
