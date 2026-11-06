package com.example.chat

import com.example.chat.data.api.*
import com.example.chat.data.remote.SupabaseStorageClient
import com.example.chat.data.services.ChatMediaService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

/** Service-level: a real ChatMediaService with a real SupabaseStorageClient whose OkHttp interceptor counts requests. */
class ChatMediaServiceScopeTest {
    private class MemStore : ApiSessionStore {
        override fun loadToken(): String? = null; override fun loadUserJson(): String? = null
        override fun save(token: String, userJson: String) {}; override fun clear() {}
    }
    private val storageHits = java.util.concurrent.atomic.AtomicInteger(0)
    private val storage = SupabaseStorageClient("https://s.invalid", "k", { "t" },
        OkHttpClient.Builder().addInterceptor { storageHits.incrementAndGet(); throw java.io.IOException("storage must not be reached") }.build())
    private fun scope(): ApiAccountScope {
        val lock = Any(); val s = ApiAuthSession(MemStore(), lock); s.adopt(ApiAuthResult("a", ApiUserProfile("u1", "u1", "u1", "U")))
        return ApiAccountScopeOwner(lock, s::snapshot) { ApiRoutingSnapshot("t", "https://a", "wss://a", 1) }.snapshot()
    }
    private fun svc(auth: () -> Boolean, compress: suspend (ByteArray, Int) -> ByteArray?, up: (suspend (ByteArray, String, (Long, Long) -> Unit) -> Map<String, Any>)?, uid: () -> String? = { "u-fresh" }) =
        ChatMediaService(storage, uid, imageCompressor = compress, apiAuthenticated = auth, apiUpload = up)

    @Test fun authFlipsDuringCompressionScopedFlowFailsClosedStorageZero() = runBlocking {
        var authed = true
        val s = svc({ authed }, { b, _ -> authed = false; b }, { _, _, _ -> mapOf("url" to "u") })   // logout/switch happens inside compression
        try { withContext(ApiScopeElement(scope())) { s.uploadBytes("c", "image", ByteArray(100) { 1 }, "a.png", "image/png") }; fail() }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("Account changed")) }
        assertEquals("never fell back to storage", 0, storageHits.get())
    }
    @Test fun scopedFlowWithNoApiUploaderFailsClosed() = runBlocking {
        val s = svc({ true }, { _, _ -> null }, null)
        try { withContext(ApiScopeElement(scope())) { s.uploadBytes("c", "document", ByteArray(10), "a.pdf") }; fail() } catch (_: IllegalStateException) {}
        assertEquals(0, storageHits.get())
    }
    @Test fun scopedFlowUsesApiWhenStillAuthenticated() = runBlocking {
        val s = svc({ true }, { _, _ -> null }, { _, n, _ -> mapOf("url" to "https://x/$n") })
        val a = withContext(ApiScopeElement(scope())) { s.uploadBytes("c", "document", ByteArray(10), "a.pdf") }
        assertEquals("https://x/a.pdf", a.url); assertEquals(0, storageHits.get())
    }
    @Test fun blankServerUrlIsRejectedNotSentAsEmptyAttachment() = runBlocking {
        for (m in listOf(mapOf<String, Any>(), mapOf<String, Any>("url" to ""), mapOf<String, Any>("url" to "  "))) {
            val s = svc({ true }, { _, _ -> null }, { _, _, _ -> m })
            try { withContext(ApiScopeElement(scope())) { s.uploadBytes("c", "document", ByteArray(10), "a.pdf") }; fail("$m") } catch (_: IllegalStateException) {}
        }
    }
    @Test fun unscopedLegacyRoutesPreserved() = runBlocking {
        // not authenticated, no element: legacy storage path is still taken (and hits the counting client)
        val s = svc({ false }, { _, _ -> null }, { _, _, _ -> mapOf("url" to "u") })
        try { s.uploadBytes("c", "document", ByteArray(10), "a.pdf"); fail() } catch (_: java.io.IOException) {} catch (_: Exception) {}
        assertEquals("legacy unscoped route still reaches storage", 1, storageHits.get())
        // unscoped but authenticated: api route as before
        val s2 = svc({ true }, { _, _ -> null }, { _, _, _ -> mapOf("url" to "u2") })
        assertEquals("u2", s2.uploadBytes("c", "document", ByteArray(10), "a.pdf").url)
    }
    @Test fun compressionCancellationNeverDispatches() = runBlocking {
        val cancelled = kotlinx.coroutines.CancellationException("cancelled compression")
        var uploads = 0
        val s = svc({ true }, { _, _ -> throw cancelled }, { _, _, _ -> uploads++; mapOf("url" to "u") })
        try { withContext(ApiScopeElement(scope())) { s.uploadBytes("c", "image", ByteArray(10), "a.png") }; fail() }
        catch (e: kotlinx.coroutines.CancellationException) { assertCancellationPreserved(cancelled, e) }
        assertEquals(0, uploads); assertEquals(0, storageHits.get())
    }
    @Test fun authFlipsAfterUploadDoesNotReturnAttachment() = runBlocking {
        var authed = true
        val s = svc({ authed }, { _, _ -> null }, { _, _, _ -> authed = false; mapOf("url" to "u") })
        try { withContext(ApiScopeElement(scope())) { s.uploadBytes("c", "document", ByteArray(10), "a.pdf") }; fail() }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("Account changed")) }
        assertEquals(0, storageHits.get())
    }
    @Test fun uploadCancellationIsPreserved() = runBlocking {
        val cancelled = kotlinx.coroutines.CancellationException("cancelled transfer")
        val s = svc({ true }, { _, _ -> null }, { _, _, _ -> throw cancelled })
        try { withContext(ApiScopeElement(scope())) { s.uploadBytes("c", "document", ByteArray(10), "a.pdf") }; fail() }
        catch (e: kotlinx.coroutines.CancellationException) { assertCancellationPreserved(cancelled, e) }
        assertEquals(0, storageHits.get())
    }
    /** Coroutine debug stacktrace recovery may copy CancellationException across withContext.
     *  Require exact original or its recovery copy, never a new unrelated cancellation. */
    private fun assertCancellationPreserved(original: kotlinx.coroutines.CancellationException, caught: kotlinx.coroutines.CancellationException) {
        assertEquals(original.javaClass, caught.javaClass)
        assertEquals(original.message, caught.message)
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        assertTrue("original cancellation must survive in the bounded cause chain", generateSequence<Throwable>(caught) { it.cause }.take(8).takeWhile { seen.add(it) }.any { it === original })
    }

    @Test fun serviceRethrowsExactCancellationBeforeCoroutineRecovery() = runBlocking {
        for (duringCompression in listOf(true, false)) {
            val cancelled = kotlinx.coroutines.CancellationException("service cancellation $duringCompression")
            var uploads = 0
            val s = svc({ true }, { _, _ -> if (duringCompression) throw cancelled else null }, { _, _, _ -> uploads++; throw cancelled })
            try {
                // No withContext boundary here: assert the service itself does not replace the exception.
                s.uploadBytes("c", if (duringCompression) "image" else "document", ByteArray(10), "a.bin")
                fail("cancelled service must not return an attachment")
            } catch (e: kotlinx.coroutines.CancellationException) { assertSame(cancelled, e) }
            assertEquals(if (duringCompression) 0 else 1, uploads)
            assertEquals(0, storageHits.get())
        }
    }
    @Test fun cancellationAssertionRejectsUnrelatedCopiesAndCauseCycles() {
        val original = kotlinx.coroutines.CancellationException("same text")
        val unrelated = kotlinx.coroutines.CancellationException("same text")
        assertThrows(AssertionError::class.java) { assertCancellationPreserved(original, unrelated) }
        val cyclic = kotlinx.coroutines.CancellationException("same text")
        val other = kotlinx.coroutines.CancellationException("same text")
        cyclic.initCause(other); other.initCause(cyclic)
        assertThrows(AssertionError::class.java) { assertCancellationPreserved(original, cyclic) }
    }
}
