package com.example.chat

import com.example.chat.data.remote.PostgrestQueryClient
import com.example.chat.data.remote.SupabaseStorageClient
import com.example.chat.data.services.StatusIdentityUnboundException
import com.example.chat.data.services.StatusRecord
import com.example.chat.data.services.StatusService
import com.example.chat.domain.models.PrivacyPreferencesSnapshot
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Offline JVM tests: every StatusService write fails closed while identity is unbound.
 *  Real PostgrestQueryClient and SupabaseStorageClient run over counting fake OkHttp. */
class StatusFailClosedTest {
    private var http = 0
    private var rpcCalls = 0

    private val ok = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        http++
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("x")
            .body("""[{"id":"s1","user_id":"A","created_at":"2026-10-27T01:00:00Z","expires_at":"2026-10-28T01:00:00Z"}]"""
                .toResponseBody("application/json".toMediaType())).build()
    }).build()

    private val rpc = object : com.example.chat.data.messaging.SupabaseRpc {
        override suspend fun rpc(function: String, params: Map<String, Any?>): Any? { rpcCalls++; return null }
    }

    private fun svc(bound: (() -> Boolean)? = null): StatusService {
        val q = PostgrestQueryClient("http://h", "k", { "LIVE" }, ok)
        val s = SupabaseStorageClient("http://h", "k", { "LIVE" }, ok)
        return if (bound == null) StatusService(q, s, rpc, { "A" }, { PrivacyPreferencesSnapshot() })
        else StatusService(q, s, rpc, { "A" }, { PrivacyPreferencesSnapshot() }, identityBound = bound)
    }

    private val rec = StatusRecord("s1", "A", "", "text", null, null, 0, 0, Instant.now(), Instant.now(), null)

    private fun assertUnbound(block: suspend () -> Unit) = runBlocking {
        try { block(); fail("expected StatusIdentityUnboundException") } catch (_: StatusIdentityUnboundException) {}
        assertEquals(0, http); assertEquals(0, rpcCalls)
    }

    @Test fun defaultIsNotAvailable() { assertFalse(svc().isAvailable()) }
    @Test fun publishTextZeroNetwork() = assertUnbound { svc().publishText("hello") }
    @Test fun publishMediaZeroNetwork() = assertUnbound {
        svc().publishMediaBytes(ByteArray(4) { 1 }, "a.jpg", "image", mimeType = "image/jpeg")
    }
    @Test fun audioMediaZeroNetwork() = assertUnbound {
        svc().publishMediaBytes(ByteArray(4) { 1 }, "a.m4a", "audio")
    }
    @Test fun markViewedZeroNetwork() = assertUnbound { svc().markViewed(rec) }
    @Test fun deleteZeroNetwork() = assertUnbound { svc().deleteStatus(rec) }

    @Test fun viewersZeroNetwork() = assertUnbound { svc().viewersFor("s1") }
    @Test fun signedUrlZeroNetwork() = assertUnbound { svc().createSignedUrl("A/x.jpg") }

    @Test fun watchEmitsEmptyAndMakesZeroNetworkCalls() = runBlocking {
        val first = svc().watchActiveStatuses().first()
        assertTrue(first.isEmpty())
        assertEquals(0, http); assertEquals(0, rpcCalls)
    }

    @Test fun messageIsTheAgreedUserText() {
        assertEquals("Status isn't available for this account connection yet. Nothing was sent.",
            StatusIdentityUnboundException().message)
    }

    @Test fun messageLeaksNoIdentity() {
        val m = StatusIdentityUnboundException().message!!
        assertFalse(m.contains("@") || m.contains("Bearer") || m.contains("user_id") || m.contains("supabase", true))
    }

    @Test fun explicitlyBoundStillWorksSoGateIsTheOnlyChange() = runBlocking {
        val s = svc { true }
        assertTrue(s.isAvailable())
        s.publishText("hello")
        assertEquals(1, http)
        s.deleteStatus(rec)
        assertEquals(1, rpcCalls)
        val before = http
        s.watchActiveStatuses().first()
        assertEquals(before + 1, http) // the status_updates query ran
    }
}
