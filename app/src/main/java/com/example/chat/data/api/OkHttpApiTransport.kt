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
            else -> throw IllegalArgumentException("unsupported method $method")
        }
        client.newCall(builder.build()).execute().use { response ->
            return response.code to (response.body?.string() ?: "")
        }
    }
}
