package com.example.chat

import com.example.chat.data.api.ApiDeviceTokenClient
import com.example.chat.data.api.FcmTokenRegistrar
import org.junit.Assert.*
import org.junit.Test

/** Parity tests for ApiDeviceTokenClient + FcmTokenRegistrar against
 *  lib/data/services/api_backend_service.dart:1975-2036 and
 *  lib/core/services/firebase_notification_service.dart:120-172. */
class ApiDeviceTokenClientParityTest {

    private class FakeTransport(val statuses: MutableList<Int> = mutableListOf(200)) :
        ApiDeviceTokenClient.Transport {
        val calls = mutableListOf<String>()
        override fun send(method: String, url: String, headers: Map<String, String>, body: String?): Int {
            calls.add("$method $url ${headers["Authorization"]} ${headers["x-tenant-id"]} $body")
            return if (statuses.isEmpty()) 200 else statuses.removeAt(0)
        }
    }

    private fun client(t: ApiDeviceTokenClient.Transport, token: String? = "tok") =
        ApiDeviceTokenClient({ "https://api.example.com/" }, { "tenant-1" }, { token }, t)

    @Test
    fun registerPostsExactBodyHeadersAndUrl() {
        val t = FakeTransport()
        client(t).registerDeviceToken("fcm-abc")
        assertEquals(
            """POST https://api.example.com/me/devices Bearer tok tenant-1 {"fcm_token":"fcm-abc","device_type":"android"}""",
            t.calls.single(),
        )
    }

    @Test
    fun registerFallsBackToAppUsersPathOn404() {
        val t = FakeTransport(mutableListOf(404, 200))
        client(t).registerDeviceToken("fcm-abc")
        assertEquals(2, t.calls.size)
        assertTrue(t.calls[1].startsWith("POST https://api.example.com/app-users/me/devices "))
    }

    @Test
    fun registerSkipsWhenUnauthenticatedOrEmptyToken() {
        val t = FakeTransport()
        client(t, token = null).registerDeviceToken("fcm-abc")
        client(t).registerDeviceToken("")
        assertTrue(t.calls.isEmpty())
    }

    @Test
    fun registerSwallowsTransportFailure() {
        val t = object : ApiDeviceTokenClient.Transport {
            override fun send(method: String, url: String, headers: Map<String, String>, body: String?): Int {
                throw RuntimeException("boom")
            }
        }
        client(t).registerDeviceToken("fcm-abc")
    }

    @Test
    fun unregisterDeletesWith404Fallback() {
        val t = FakeTransport(mutableListOf(404, 200))
        client(t).unregisterDeviceToken("fcm-abc")
        assertEquals(2, t.calls.size)
        assertTrue(t.calls[0].startsWith("DELETE https://api.example.com/me/devices/fcm-abc "))
        assertTrue(t.calls[1].startsWith("DELETE https://api.example.com/app-users/me/devices/fcm-abc "))
    }

    @Test
    fun registrarRegistersWithBothBackends() {
        val t = FakeTransport()
        val supabase = mutableListOf<String>()
        val registrar = FcmTokenRegistrar(client(t), pushRegistry = { supabase.add(it) })
        registrar.onTokenReceived("fcm-1")
        assertEquals(1, t.calls.size)
        assertEquals(listOf("fcm-1"), supabase)
    }

    @Test
    fun registrarSyncOnLoginReusesRememberedToken() {
        val t = FakeTransport()
        val registrar = FcmTokenRegistrar(client(t))
        registrar.syncTokenOnLogin()
        assertTrue(t.calls.isEmpty())
        registrar.onTokenReceived("fcm-2")
        assertEquals(1, t.calls.size)
        registrar.syncTokenOnLogin()
        assertEquals(2, t.calls.size)
    }

    @Test
    fun registrarUnregisterOnLogoutUsesRememberedToken() {
        val t = FakeTransport()
        val registrar = FcmTokenRegistrar(client(t))
        registrar.onTokenReceived("fcm-3")
        registrar.unregisterOnLogout()
        assertEquals(2, t.calls.size)
        assertTrue(t.calls[1].startsWith("DELETE https://api.example.com/me/devices/fcm-3 "))
    }

    @Test
    fun registrarSurvivesMissingSupabaseRegistry() {
        val t = FakeTransport()
        val registrar = FcmTokenRegistrar(client(t), pushRegistry = null)
        registrar.onTokenReceived("fcm-4")
        assertEquals(1, t.calls.size)
    }
}
