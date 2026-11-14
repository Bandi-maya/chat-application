package com.example.chat.data.api

import okhttp3.OkHttpClient

/**
 * Process-wide shared OkHttp base client. OkHttp is designed for exactly one
 * shared instance per app: connection pooling, HTTP/2 multiplexing, the
 * dispatcher thread pool and response caches all live on the client, so every
 * `OkHttpClient()` built from scratch duplicates idle threads and sockets and
 * defeats keep-alive between API, media and translation calls.
 *
 * Call sites keep their own timeouts/options by cloning through
 * `ChatyOkHttp.shared.newBuilder()` - the clone shares the connection pool and
 * dispatcher with every other clone, which is the OkHttp-recommended pattern.
 * Call sites with no custom options use [shared] directly.
 */
object ChatyOkHttp {
    val shared: OkHttpClient by lazy { OkHttpClient.Builder().build() }
}
