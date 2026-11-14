package com.example.chat.data.api

import org.json.JSONObject

/** Never infer capability from a version, local source file or successful unrelated request. */
object ApiServerCapabilities {
    fun supports(owner: ApiAccountScope, transport: ApiHttpTransport, feature: String): Boolean = try {
        val uri = java.net.URI(owner.baseUrl)
        val loopback = uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2", "::1")
        require((uri.scheme == "https" || (loopback && uri.scheme == "http")) && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null)
        require(feature.matches(Regex("[a-z][a-z0-9_]{0,63}")))
        val response = transport.execute("GET", "${owner.baseUrl.trimEnd('/')}/chat/capabilities",
            mapOf("Authorization" to "Bearer ${owner.token ?: error("Not authenticated")}", "x-tenant-id" to owner.tenantId), null)
        response.first == 200 && JSONObject(response.second).optJSONObject("data")?.opt(feature) == true
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        false
    }
}
