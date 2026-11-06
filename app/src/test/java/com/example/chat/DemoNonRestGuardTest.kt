package com.example.chat

import com.example.chat.data.api.ApiDeviceTokenClient
import com.example.chat.data.api.FcmTokenRegistrar
import com.example.chat.data.demo.DemoMode
import com.example.chat.data.remote.PostgrestQueryClient
import com.example.chat.data.remote.PostgrestRpcClient
import com.example.chat.data.remote.SupabaseRealtimeChannel
import com.example.chat.data.remote.RealtimeSubscribeStatus
import com.example.chat.data.remote.SupabaseStorageClient
import kotlinx.coroutines.runBlocking
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Offline-demo dispatch boundary for every NON-REST-plane client (peer
 *  review HIGH/MEDIUM): PostgREST query/rpc and Supabase Storage attach the
 *  publishable apikey even with a null bearer, Supabase Realtime owns its own
 *  socket factory, and FCM registration owns its own OkHttp transport - none
 *  of them pass through the guarded REST transport, so each must fail closed
 *  on DemoMode.active / the synthetic demo token on its own. */
class DemoNonRestGuardTest {

    @After fun reset() { DemoMode.active = false }

    private fun queryClient() =
        PostgrestQueryClient("https://x.supabase.co", "anon-key", { null })

    @Test fun postgrestQueryThrowsBeforeDispatchInDemo() {
        DemoMode.active = true
        val e = assertThrows(IllegalStateException::class.java) {
            runBlocking { queryClient().from("messages").select().execute() }
        }
        assertEquals(DemoMode.UNAVAILABLE_MESSAGE, e.message)
    }

    @Test fun postgrestRpcThrowsBeforeDispatchInDemo() {
        DemoMode.active = true
        val e = assertThrows(IllegalStateException::class.java) {
            runBlocking { PostgrestRpcClient("https://x.supabase.co", "anon-key", { null }).rpc("f", emptyMap()) }
        }
        assertEquals(DemoMode.UNAVAILABLE_MESSAGE, e.message)
    }

    @Test fun storageUploadThrowsBeforeDispatchInDemo() {
        DemoMode.active = true
        val e = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                SupabaseStorageClient("https://x.supabase.co", "anon-key", { null })
                    .upload("b", "p", byteArrayOf(1), "text/plain")
            }
        }
        assertEquals(DemoMode.UNAVAILABLE_MESSAGE, e.message)
    }

    private class CountingSocketFactory : WebSocket.Factory {
        @Volatile var calls = 0
        override fun newWebSocket(request: okhttp3.Request, listener: WebSocketListener): WebSocket {
            calls++
            return object : WebSocket {
                override fun request() = request
                override fun queueSize() = 0L
                override fun send(text: String) = false
                override fun send(bytes: ByteString) = false
                override fun close(code: Int, reason: String?) = true
                override fun cancel() {}
            }
        }
    }

    private fun channel(factory: WebSocket.Factory) = SupabaseRealtimeChannel(
        "https://x.supabase.co", "anon-key", { null },
        "topic", emptyList(), {}, { _: RealtimeSubscribeStatus -> },
        okhttp3.OkHttpClient(), factory,
    )

    @Test fun supabaseRealtimeNeverOpensSocketInDemo() {
        val factory = CountingSocketFactory()
        DemoMode.active = true
        val ch = channel(factory)
        ch.start()
        val deadline = System.currentTimeMillis() + 1500
        while (System.currentTimeMillis() < deadline && factory.calls == 0) Thread.sleep(25)
        Thread.sleep(200) // let any queued connect/reconnect fire
        assertEquals("demo must not dispatch the realtime socket", 0, factory.calls)
        ch.close()
    }

    @Test fun supabaseRealtimeStillConnectsForRealSession() {
        val factory = CountingSocketFactory()
        val ch = channel(factory)
        ch.start()
        val deadline = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < deadline && factory.calls == 0) Thread.sleep(25)
        assertTrue("real sessions must keep the socket path", factory.calls > 0)
        ch.close()
    }

    private class CountingTransport : ApiDeviceTokenClient.Transport {
        val calls = mutableListOf<String>()
        override fun send(method: String, url: String, headers: Map<String, String>, body: String?): Int {
            calls.add("$method $url")
            return 200
        }
    }

    @Test fun fcmRegistrationNeverDispatchesInDemo() {
        val transport = CountingTransport()
        var registryCalls = 0
        val devices = ApiDeviceTokenClient(
            { "https://api.example.com/" }, { "tenant-1" }, { DemoMode.TOKEN }, transport,
        )
        val registrar = FcmTokenRegistrar(
            apiDevices = devices,
            pushRegistry = { registryCalls++ },
        )
        DemoMode.active = true
        registrar.onTokenReceived("fcm-x")
        registrar.syncTokenOnLogin()
        registrar.unregisterOnLogout()
        assertTrue("api-hub device registration must not dispatch in demo", transport.calls.isEmpty())
        assertEquals("supabase push registry seam must not fire in demo", 0, registryCalls)
    }

    @Test fun deviceClientTreatsDemoTokenAsUnauthenticatedEvenIfFlagCleared() {
        val transport = CountingTransport()
        val devices = ApiDeviceTokenClient(
            { "https://api.example.com/" }, { "tenant-1" }, { DemoMode.TOKEN }, transport,
        )
        DemoMode.active = false // e.g. cleared by signOut before the hook runs
        devices.registerDeviceToken("fcm-x")
        devices.unregisterDeviceToken("fcm-x")
        assertTrue("synthetic demo token must never be sent", transport.calls.isEmpty())
    }

    @Test fun fcmRegistrationStillWorksForRealSession() {
        val transport = CountingTransport()
        var registryCalls = 0
        val devices = ApiDeviceTokenClient(
            { "https://api.example.com/" }, { "tenant-1" }, { "real-token" }, transport,
        )
        val registrar = FcmTokenRegistrar(apiDevices = devices, pushRegistry = { registryCalls++ })
        registrar.onTokenReceived("fcm-x")
        assertEquals(1, transport.calls.size)
        assertEquals(1, registryCalls)
    }
}
