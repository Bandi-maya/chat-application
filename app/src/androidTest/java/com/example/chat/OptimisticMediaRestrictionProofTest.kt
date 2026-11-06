package com.example.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.data.api.*
import com.example.chat.data.local.AttachmentCachePolicy
import com.example.chat.domain.models.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Offline service boundary: fake REST only, no initialize, socket, upload or recipient. */
@RunWith(AndroidJUnit4::class)
class OptimisticMediaRestrictionProofTest {
    @Test fun optimisticAttachmentKeepsProtectionWhileFakeTransportIsBlocked() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val service = ApiBackendService(ApplicationProvider.getApplicationContext<Context>(), ApiHttpTransport { _, _, _, _ ->
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS)) { "Synthetic transport timed out" }
            500 to "{}"
        })
        val field = service.javaClass.getDeclaredField("session").apply { isAccessible = true }
        (field.get(service) as ApiAuthSession).adopt(ApiAuthResult("fixture-token", ApiUserProfile("fixture-me", "fixture-me", "Synthetic fixture", "SF")))
        val flags = mutableMapOf<String, Any?>("view_once" to true, "forwarded" to true)
        val sending = async(Dispatchers.Default) {
            runCatching { service.sendMessage("fixture-room", "synthetic", MessageType.image,
                MessageAttachment("fixture", "image", "fixture.jpg", "1 KB", "https://offline.invalid/fixture"), flags) }
        }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val optimistic = service.getMessages("fixture-room").single()
            assertEquals(DeliveryState.sending, optimistic.deliveryState)
            assertEquals(true, optimistic.metadata["view_once"])
            assertEquals(true, optimistic.metadata["forwarded"])
            assertTrue(AttachmentCachePolicy.viewOnceRestricted(optimistic.metadata))
            flags["view_once"] = false
            assertEquals(true, optimistic.metadata["view_once"])
        } finally { release.countDown(); sending.await() }
    }
}
