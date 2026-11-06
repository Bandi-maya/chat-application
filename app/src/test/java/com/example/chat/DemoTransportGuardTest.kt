package com.example.chat

import com.example.chat.data.api.OkHttpApiTransport
import com.example.chat.data.demo.DemoMode
import com.example.chat.data.demo.DemoModeOfflineException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Offline demo: an api-plane request carrying the synthetic demo token must
 * fail closed before any socket is opened, on every transport instance.
 */
class DemoTransportGuardTest {

    @Test
    fun demoTokenNeverReachesTheNetwork() {
        val transport = OkHttpApiTransport()
        // An unroutable address proves no socket was opened: the guard throws
        // DemoModeOfflineException before any connect attempt.
        val headers = mapOf(
            "Authorization" to "Bearer ${DemoMode.TOKEN}",
            "x-tenant-id" to "chat-application",
            "Content-Type" to "application/json",
        )
        try {
            transport.execute("GET", "http://127.0.0.1:1/never", headers, null)
            fail("demo token request must be rejected")
        } catch (e: DemoModeOfflineException) {
            assertEquals(DemoMode.UNAVAILABLE_MESSAGE, e.message)
        }
    }

    @Test
    fun activeDemoBlocksEvenUnsignedRequests() {
        val transport = OkHttpApiTransport()
        DemoMode.active = true
        try {
            transport.execute("GET", "http://127.0.0.1:1/never", emptyMap(), null)
            fail("active demo must block all api-plane requests")
        } catch (e: DemoModeOfflineException) {
            assertEquals(DemoMode.UNAVAILABLE_MESSAGE, e.message)
        } finally {
            DemoMode.active = false
        }
    }

    @Test
    fun nonDemoRequestsAreNotGuarded() {
        val transport = OkHttpApiTransport()
        val headers = mapOf("Authorization" to "Bearer real-token", "x-tenant-id" to "chat-application")
        try {
            transport.execute("GET", "http://127.0.0.1:1/never", headers, null)
            fail("unroutable address must fail")
        } catch (e: DemoModeOfflineException) {
            fail("real tokens must not hit the demo guard")
        } catch (_: Exception) {
            // Expected: connection refused fast on the unroutable address.
        }
    }
}
