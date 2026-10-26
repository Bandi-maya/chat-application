package com.example.chat.data.media

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Plain GET of an already authorized media URL with a hard byte bound. Rejects an oversized
 * Content-Length up front and counts bytes while streaming (a lying/absent length cannot exceed
 * [maxBytes]). The returned stream owns the response and closes it. No retries, no logging of the
 * URL, no cookies/headers added. Call from an IO dispatcher.
 */
class BoundedMediaDownloader(
    private val client: OkHttpClient = OkHttpClient(),
    private val maxBytes: Long,
) {
    fun open(url: String): InputStream {
        val parsed = url.toHttpUrlOrNull()
        // https only, no redirects (a redirect could send the request to an untrusted host); no auth header is added.
        if (parsed == null || !parsed.isHttps) throw IOException("unsupported url")
        val response = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
            .newCall(Request.Builder().url(parsed).get().build()).execute()
        try {
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("empty body")
            val len = body.contentLength()
            if (len > maxBytes) throw IOException("too large")
            return Limited(body.byteStream(), maxBytes) { response.close() }
        } catch (e: Throwable) {
            response.close(); throw e
        }
    }

    private class Limited(src: InputStream, private val max: Long, private val onClose: () -> Unit) : FilterInputStream(src) {
        private var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) bump(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) bump(it) }
        private fun bump(n: Int) { count += n; if (count > max) throw IOException("too large") }
        override fun close() { try { super.close() } finally { onClose() } }
    }
}
