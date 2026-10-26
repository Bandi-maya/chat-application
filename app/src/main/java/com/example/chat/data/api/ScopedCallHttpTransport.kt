package com.example.chat.data.api

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Registry lock always follows the auth transition lock. */
class CallRequestRegistry {
    private val lock = Any()
    private val cancellations = mutableSetOf<() -> Unit>()
    fun register(cancel: () -> Unit) = synchronized(lock) { cancellations.add(cancel); Unit }
    fun remove(cancel: () -> Unit) = synchronized(lock) { cancellations.remove(cancel); Unit }
    fun cancelAll() = synchronized(lock) { cancellations.toList().forEach { it() }; cancellations.clear() }
}
/** Scope-bound call-only transport. Register under auth guard BEFORE execute.
 * No redirects, automatic retries, shared interceptor or room-sync client. */
class ScopedCallHttpTransport(
    private val expected: ApiAccountScope,
    private val guarded: (ApiAccountScope, () -> Unit) -> Boolean,
    private val registry: CallRequestRegistry,
) : ApiHttpTransport {
    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS)
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    override fun execute(method: String, url: String, headers: Map<String,String>, body: String?): Pair<Int,String> {
        val request = Request.Builder().url(url).apply { headers.forEach { (k,v) -> header(k,v) } }
            .method(method, if (method == "GET") null else (body ?: "").toRequestBody("application/json".toMediaType())).build()
        val call = client.newCall(request)
        val cancel: () -> Unit = { call.cancel() }
        if (!guarded(expected) { registry.register(cancel) }) { call.cancel(); throw java.io.IOException("Call scope expired") }
        try { return call.execute().use { it.code to (it.body?.string() ?: "") } }
        finally { registry.remove(cancel) }
    }
}
