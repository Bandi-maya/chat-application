package com.example.chat.data.api

import com.example.chat.core.config.GroupUiConfig
import com.example.chat.core.config.GroupUiConfigPolicy

/** Account-private cache is reusable only after 304 revalidation. Failures use shipped defaults, never stale policy. */
class ApiGroupUiConfigClient(private val owner: ApiAccountScope, private val transport: ApiHttpTransport,
    private val isCurrent: () -> Boolean,
    private val cache: com.example.chat.core.config.GroupUiValidatedCache? = null) {
    fun fetch(): GroupUiConfig {
        if (!isCurrent() || owner.token.isNullOrBlank()) return GroupUiConfigPolicy.defaults
        val uri = runCatching { java.net.URI(owner.baseUrl) }.getOrNull() ?: return GroupUiConfigPolicy.defaults
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) return GroupUiConfigPolicy.defaults
        return try {
            if (!ApiServerCapabilities.supports(owner, transport, "ui_config_v1") || !isCurrent()) return GroupUiConfigPolicy.defaults
            val cached = runCatching { cache?.read() }.getOrNull()?.takeIf {
                GroupUiConfigPolicy.parse(it.body) != null && com.example.chat.core.config.EncryptedGroupUiValidatedCache.validEtag(it.etag)
            }
            val headers = mutableMapOf("Authorization" to "Bearer ${owner.token}", "x-tenant-id" to owner.tenantId)
            if (cached != null && transport is ApiHeaderHttpTransport) headers["If-None-Match"] = cached.etag
            fun request(path: String): ApiHeaderResponse = if (transport is ApiHeaderHttpTransport)
                transport.executeWithHeaders("GET", "${owner.baseUrl.trimEnd('/')}/$path", headers, null)
                else transport.execute("GET", "${owner.baseUrl.trimEnd('/')}/$path", headers, null).let { ApiHeaderResponse(it.first, it.second, null) }
            var response = request("chat/ui-config")
            if (!isCurrent()) return GroupUiConfigPolicy.defaults
            if (response.code == 404) {
                response = request("api/v1/chat/ui-config")
                if (!isCurrent()) return GroupUiConfigPolicy.defaults
            }
            if (response.code == 304) return cached?.let { GroupUiConfigPolicy.parse(it.body)?.group } ?: GroupUiConfigPolicy.defaults
            if (response.code != 200) return GroupUiConfigPolicy.defaults
            val parsed = GroupUiConfigPolicy.parse(response.body) ?: return GroupUiConfigPolicy.defaults
            val etag = response.etag
            if (etag != null && com.example.chat.core.config.EncryptedGroupUiValidatedCache.validEtag(etag) && isCurrent()) {
                runCatching { cache?.write(com.example.chat.core.config.GroupUiValidatedCache.Entry(response.body, etag)) }
            }
            if (isCurrent()) parsed.group else GroupUiConfigPolicy.defaults
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { GroupUiConfigPolicy.defaults }
    }
}
