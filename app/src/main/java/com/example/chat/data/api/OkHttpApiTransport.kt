package com.example.chat.data.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** OkHttp implementation of the ApiAuthClient transport seam. */
class OkHttpApiTransport : ApiHttpTransport {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): Pair<Int, String> {
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
            return response.code to (response.body?.string() ?: "")
        }
    }
}

/** Binary (multipart) transport for ApiAccountClient.uploadProfilePhoto; the caller sets Content-Type. */
class OkHttpApiBinaryTransport : ApiBinaryTransport {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun execute(method: String, url: String, headers: Map<String, String>, body: ByteArray): Pair<Int, String> {
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
