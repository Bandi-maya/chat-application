package com.example.chat

import com.example.chat.data.api.BackendRoutingPolicy
import com.example.chat.data.api.ApiRealtimeClient
import org.junit.Assert.assertEquals
import org.junit.Test

class BackendRoutingPolicyTest {
    @Test fun restDotenvThenBuildThenUserDefault() {
        assertEquals("https://local.invalid/api/v1", BackendRoutingPolicy.restBase("https://local.invalid/api/v1", "https://build.invalid"))
        assertEquals("https://build.invalid", BackendRoutingPolicy.restBase("", "https://build.invalid"))
        assertEquals("https://api.hub.logybyte.in", BackendRoutingPolicy.restBase("", ""))
    }
    @Test fun tenantSavedThenDotenvUnlessDefaultSelected() {
        assertEquals("saved", BackendRoutingPolicy.tenant("dotenv", " saved ", false, "build"))
        assertEquals("dotenv", BackendRoutingPolicy.tenant("dotenv", null, false, "build"))
        assertEquals("build", BackendRoutingPolicy.tenant("dotenv", "saved", true, "build"))
        assertEquals("chat-application", BackendRoutingPolicy.tenant("", null, false, ""))
    }
    @Test fun suppliedDefaultsStayLiteralAndOverridesWin() {
        assertEquals("chat-application", BackendRoutingPolicy.DEFAULT_TENANT_ID)
        val template = BackendRoutingPolicy.websocketTemplate("", "")
        assertEquals("wss://api.hub.logybyte.in/api/chat/ws{userId}", template)
        assertEquals("wss://api.hub.logybyte.in/api/chat/wsu-42", ApiRealtimeClient.resolveWebSocketUrl(
            "u-42", template, "", BackendRoutingPolicy.DEFAULT_REST_API_BASE_URL))
        assertEquals("wss://local.invalid/{userId}", BackendRoutingPolicy.websocketTemplate("wss://local.invalid/{userId}", "wss://build.invalid/{userId}"))
        assertEquals("wss://build.invalid/{userId}", BackendRoutingPolicy.websocketTemplate("", "wss://build.invalid/{userId}"))
    }
    @Test fun websocketOverridePlaceholdersAndLegacyFallback() {
        fun ws(custom: String?, env: String) = ApiRealtimeClient.resolveWebSocketUrl(" user ", custom, env, "https://rest.invalid/api/v1/")
        assertEquals("wss://custom.invalid/user", ws("wss://custom.invalid/{userId}", "wss://build.invalid"))
        assertEquals("wss://build.invalid/user", ws("ws://legacy.invalid/ws/school", "wss://build.invalid/\$userId"))
        assertEquals("wss://rest.invalid/chat/ws/user", ws(null, "ws://legacy.invalid/ws/school"))
    }
}
