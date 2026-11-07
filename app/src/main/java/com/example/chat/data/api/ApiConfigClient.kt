package com.example.chat.data.api

import org.json.JSONObject

/** Versioned server-controlled configuration (Phase 5).
 *
 *  GET /app/configuration with If-None-Match revalidation: 304 means the
 *  cached document is still current (no parse, no write). The document is
 *  cached locally and only re-fetched on login, app init, explicit refresh,
 *  or a version mismatch - never polled. The server enforces every
 *  security-relevant value itself; this cache is for UX decisions only. */
class ApiConfigClient(
    private val baseUrl: () -> String,
    private val tenantId: () -> String,
    private val token: () -> String?,
    private val transport: ApiHttpTransport,
    private val cache: ConfigCache,
) {
    interface ConfigCache {
        fun read(): CachedConfig?
        fun write(config: CachedConfig)
        /** Fetch-for-next-session staging slot (Firebase Remote Config pattern):
         *  fetched updates land here and activate on the next app start, so a
         *  config change never mutates the running session's UX. */
        fun readPending(): CachedConfig?
        fun writePending(config: CachedConfig?)
    }

    /** Highest config schema this client understands. A newer server schema is
     *  kept but NOT activated (graceful rejection) until the app updates. */
    private val knownSchemaVersion: Long = 2

    data class CachedConfig(val version: Long, val etag: String, val document: String)

    sealed interface FetchResult {
        /** Fresh document stored in the cache. */
        data class Updated(val config: CachedConfig) : FetchResult
        /** Server says the cache is current. */
        data object Unchanged : FetchResult
        /** Network/server problem - the old cache stays in force. */
        data object Unavailable : FetchResult
    }

    fun currentCached(): CachedConfig? = cache.read()

    /** Call once at app start, before any fetch: promotes a previously fetched
     *  pending document to active. First-ever launch has no cache, so the
     *  first successful fetch activates immediately instead. */
    fun activatePending() {
        cache.readPending()?.let {
            cache.write(it)
            cache.writePending(null)
        }
    }

    /** Revalidate against the server; updates the cache on change. */
    fun fetch(): FetchResult {
        val cached = cache.read()
        val headers = mutableMapOf(
            "x-tenant-id" to tenantId(),
            "Accept" to "application/json",
        )
        token()?.let { headers["Authorization"] = "Bearer $it" }
        cached?.let { headers["If-None-Match"] = it.etag }

        val base = baseUrl().trimEnd('/')
        var response = try {
            transport.execute("GET", "$base/app/configuration", headers, null)
        } catch (_: Exception) {
            return FetchResult.Unavailable
        }
        if (response.first == 404) {
            response = try {
                transport.execute("GET", "$base/api/v1/app/configuration", headers, null)
            } catch (_: Exception) {
                return FetchResult.Unavailable
            }
        }
        return when (response.first) {
            304 -> FetchResult.Unchanged
            200 -> try {
                val body = JSONObject(response.second)
                val data = body.getJSONObject("data")
                // Graceful schema rejection: a newer schema may carry fields
                // this build would misread; keep the current config instead.
                if (data.optLong("schema_version", 1) > knownSchemaVersion) {
                    return FetchResult.Unchanged
                }
                val fresh = CachedConfig(
                    version = data.getLong("version"),
                    // The transport returns (status, body) only, so the server
                    // mirrors the ETag inside the body for revalidation.
                    etag = data.optString("etag"),
                    document = data.getJSONObject("config").toString(),
                )
                if (cache.read() == null) {
                    cache.write(fresh)  // cold start: activate immediately
                } else {
                    cache.writePending(fresh)  // mid-session: apply next start
                }
                FetchResult.Updated(fresh)
            } catch (_: Exception) {
                FetchResult.Unavailable
            }
            else -> FetchResult.Unavailable
        }
    }
}
