package com.example.chat

import com.example.chat.data.api.ApiConfigClient
import com.example.chat.data.api.ApiHttpTransport
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 5 client: ETag revalidation, cache writes on 200, cache kept on
 *  304 and on failures. */
class ApiConfigClientTest {

    private class FakeCache : ApiConfigClient.ConfigCache {
        var stored: ApiConfigClient.CachedConfig? = null
        var pending: ApiConfigClient.CachedConfig? = null
        override fun read() = stored
        override fun write(config: ApiConfigClient.CachedConfig) { stored = config }
        override fun readPending() = pending
        override fun writePending(config: ApiConfigClient.CachedConfig?) { pending = config }
    }

    private fun body(version: Long, etag: String, maxFileMb: Int): String =
        JSONObject().put("success", true).put("data", JSONObject()
            .put("version", version)
            .put("etag", etag)
            .put("config", JSONObject().put("upload_limits", JSONObject().put("max_file_mb", maxFileMb)))
        ).toString()

    @Test fun `200 stores the document and body-carried etag`() {
        val cache = FakeCache()
        var seenIfNoneMatch: String? = null
        val transport = ApiHttpTransport { _, _, headers, _ ->
            seenIfNoneMatch = headers["If-None-Match"]
            200 to body(1_000_000, "\"cfg-a\"", 100)
        }
        val client = ApiConfigClient({ "https://api.example.test" }, { "t" }, { "tok" }, transport, cache)
        val result = client.fetch()
        assertTrue(result is ApiConfigClient.FetchResult.Updated)
        assertEquals(null, seenIfNoneMatch)
        assertEquals("\"cfg-a\"", cache.read()!!.etag)
        assertEquals(100, JSONObject(cache.read()!!.document).getJSONObject("upload_limits").getInt("max_file_mb"))
    }

    @Test fun `304 keeps the cache and reports Unchanged`() {
        val cache = FakeCache()
        cache.write(ApiConfigClient.CachedConfig(1_000_000, "\"cfg-a\"", "{}"))
        var seenIfNoneMatch: String? = null
        val transport = ApiHttpTransport { _, _, headers, _ ->
            seenIfNoneMatch = headers["If-None-Match"]
            304 to ""
        }
        val client = ApiConfigClient({ "https://api.example.test" }, { "t" }, { "tok" }, transport, cache)
        assertEquals(ApiConfigClient.FetchResult.Unchanged, client.fetch())
        assertEquals("\"cfg-a\"", seenIfNoneMatch)
        assertEquals(1_000_000, cache.read()!!.version)
    }

    @Test fun `failure keeps the old cache`() {
        val cache = FakeCache()
        cache.write(ApiConfigClient.CachedConfig(1_000_000, "\"cfg-a\"", "{}"))
        val transport = ApiHttpTransport { _, _, _, _ -> 500 to "boom" }
        val client = ApiConfigClient({ "https://api.example.test" }, { "t" }, { "tok" }, transport, cache)
        assertEquals(ApiConfigClient.FetchResult.Unavailable, client.fetch())
        assertEquals("\"cfg-a\"", cache.read()!!.etag)
    }

    @Test fun `mid-session fetch stages for next start, cold fetch activates immediately`() {
        val cache = FakeCache()
        // Cold start: nothing cached -> activates now.
        var transport = ApiHttpTransport { _, _, _, _ -> 200 to body(1_000_001, "\"cfg-b\"", 100) }
        var client = ApiConfigClient({ "https://api.test" }, { "t" }, { "tok" }, transport, cache)
        assertTrue(client.fetch() is ApiConfigClient.FetchResult.Updated)
        assertEquals("\"cfg-b\"", cache.read()!!.etag)
        // Mid-session: a newer document is staged, not activated.
        transport = ApiHttpTransport { _, _, _, _ -> 200 to body(1_000_002, "\"cfg-c\"", 50) }
        client = ApiConfigClient({ "https://api.test" }, { "t" }, { "tok" }, transport, cache)
        assertTrue(client.fetch() is ApiConfigClient.FetchResult.Updated)
        assertEquals("\"cfg-b\"", cache.read()!!.etag)   // active unchanged
        assertEquals("\"cfg-c\"", cache.readPending()!!.etag)
        // Next app start: promote.
        client.activatePending()
        assertEquals("\"cfg-c\"", cache.read()!!.etag)
        assertEquals(null, cache.readPending())
    }

    @Test fun `newer schema is rejected gracefully`() {
        val cache = FakeCache()
        cache.write(ApiConfigClient.CachedConfig(1_000_000, "\"cfg-a\"", "{}"))
        val body = JSONObject().put("success", true).put("data", JSONObject()
            .put("version", 2_000_001).put("schema_version", 99)
            .put("etag", "\"cfg-z\"").put("config", JSONObject())).toString()
        val client = ApiConfigClient({ "https://api.test" }, { "t" }, { "tok" }, ApiHttpTransport { _, _, _, _ -> 200 to body }, cache)
        assertTrue(client.fetch() is ApiConfigClient.FetchResult.Unchanged)
        assertEquals("\"cfg-a\"", cache.read()!!.etag)
    }

    @Test fun `404 retries under api-v1 mount`() {
        val cache = FakeCache()
        val calls = mutableListOf<String>()
        val transport = object : ApiHttpTransport {
            override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
                calls.add(url)
                return if (calls.size == 1) 404 to "{}" else 200 to body(1_000_000, "\"cfg-b\"", 25)
            }
        }
        val client = ApiConfigClient({ "https://api.example.test" }, { "t" }, { null }, transport, cache)
        client.fetch()
        assertEquals("https://api.example.test/app/configuration", calls[0])
        assertEquals("https://api.example.test/api/v1/app/configuration", calls[1])
        assertEquals(25, JSONObject(cache.read()!!.document).getJSONObject("upload_limits").getInt("max_file_mb"))
    }
}
