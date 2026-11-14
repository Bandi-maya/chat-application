package com.example.chat

import com.example.chat.data.api.*
import com.example.chat.core.config.*
import org.junit.Assert.*
import org.junit.Test

class GroupUiRevalidationTest {
    private val valid = """{"schema_version":1,"revision":"r1","screens":{"group_creation":{"title":"Team","name_label":"Subject","name_max_length":100,"member_selection":{"order":["search","selected","contacts"],"selected_display":"chips","show_selected_count":true},"labels":{"next":"Next","create":"Create","search":"Search"}}}}"""
    private class Cache(var value: GroupUiValidatedCache.Entry? = null) : GroupUiValidatedCache {
        var writes = 0
        override fun read() = value
        override fun write(entry: GroupUiValidatedCache.Entry) { value = entry; writes++ }
    }
    private fun owner() = ApiAccountScope("user", "token", "tenant", "https://example.invalid", "", 1L)
    private class Transport(var answer: ApiHeaderResponse) : ApiHeaderHttpTransport {
        var requestHeaders: Map<String, String> = emptyMap()
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?) =
            200 to """{"data":{"ui_config_v1":true}}"""
        override fun executeWithHeaders(method: String, url: String, headers: Map<String, String>, body: String?): ApiHeaderResponse {
            requestHeaders = headers
            return answer
        }
    }
    @Test fun valid200SavedAndOnly304ReusesIt() {
        val cache = Cache(); val transport = Transport(ApiHeaderResponse(200, valid, "\"r1\""))
        assertEquals("Team", ApiGroupUiConfigClient(owner(), transport, { true }, cache).fetch().title)
        assertEquals(1, cache.writes)
        transport.answer = ApiHeaderResponse(304, "", null)
        assertEquals("Team", ApiGroupUiConfigClient(owner(), transport, { true }, cache).fetch().title)
        assertEquals("\"r1\"", transport.requestHeaders["If-None-Match"])
        transport.answer = ApiHeaderResponse(503, "", null)
        assertEquals(GroupUiConfigPolicy.defaults, ApiGroupUiConfigClient(owner(), transport, { true }, cache).fetch())
        assertEquals(1, cache.writes)
    }
    @Test fun corruptCacheCannotTurn304IntoConfig() {
        val cache = Cache(GroupUiValidatedCache.Entry("{}", "\"r1\""))
        val transport = Transport(ApiHeaderResponse(304, "", null))
        assertEquals(GroupUiConfigPolicy.defaults, ApiGroupUiConfigClient(owner(), transport, { true }, cache).fetch())
        assertFalse(transport.requestHeaders.containsKey("If-None-Match"))
    }
    @Test fun invalidResponseKeepsLastValidatedBytesButDoesNotDisplayThemStale() {
        val cache = Cache(GroupUiValidatedCache.Entry(valid, "\"r1\""))
        val transport = Transport(ApiHeaderResponse(200, valid.replace("schema_version\":1", "schema_version\":99"), "\"r2\""))
        assertEquals(GroupUiConfigPolicy.defaults, ApiGroupUiConfigClient(owner(), transport, { true }, cache).fetch())
        assertEquals(0, cache.writes)
        assertEquals(valid, cache.value?.body)
    }
}
