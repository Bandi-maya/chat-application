package com.example.chat

import com.example.chat.data.api.BackendRoutingPolicy
import com.example.chat.data.api.ApiRealtimeClient
import org.junit.Assert.assertEquals
import org.junit.Test

class BackendRoutingPolicyTest {
    @Test fun restDotenvThenBuildThenAndroidDefault() {
        assertEquals("https://local.invalid/api/v1", BackendRoutingPolicy.restBase("https://local.invalid/api/v1", "https://build.invalid"))
        assertEquals("https://build.invalid", BackendRoutingPolicy.restBase("", "https://build.invalid"))
        assertEquals("http://10.0.2.2:8000", BackendRoutingPolicy.restBase("", ""))
    }
    @Test fun tenantSavedThenDotenvUnlessDefaultSelected() {
        assertEquals("saved", BackendRoutingPolicy.tenant("dotenv", " saved ", false, "build"))
        assertEquals("dotenv", BackendRoutingPolicy.tenant("dotenv", null, false, "build"))
        assertEquals("build", BackendRoutingPolicy.tenant("dotenv", "saved", true, "build"))
        assertEquals("chat-application", BackendRoutingPolicy.tenant("", null, false, ""))
    }
    @Test fun websocketOverridePlaceholdersAndLegacyFallback() {
        fun ws(custom: String?, env: String) = ApiRealtimeClient.resolveWebSocketUrl(" user ", custom, env, "https://rest.invalid/api/v1/")
        assertEquals("wss://custom.invalid/user", ws("wss://custom.invalid/{userId}", "wss://build.invalid"))
        assertEquals("wss://build.invalid/user", ws("ws://legacy.invalid/ws/school", "wss://build.invalid/\$userId"))
        assertEquals("wss://rest.invalid/chat/ws/user", ws(null, "ws://legacy.invalid/ws/school"))
    }
}
