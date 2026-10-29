package com.example.chat

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.data.local.AttachmentFileCache
import com.example.chat.data.local.SessionEpoch
import com.example.chat.data.media.AttachmentSourceResolver
import com.example.chat.data.media.TrustedAttachment
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.detail.AttachmentConsumerBinding
import com.example.chat.ui.chats.detail.MediaAttachmentPreview
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proof for the requested media download states: each unavailable reason renders
 * its real UI (Waiting for network / Retry download / Not available /
 * Sign in to view), and a retryable tile tap re-resolves through the real
 * resolver + binding chain.
 */
@RunWith(AndroidJUnit4::class)
class MediaDownloadStatesProofTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val dev get() = UiDevice.getInstance(ins)

    private fun shot(name: String) {
        val dir = File(ins.targetContext.getExternalFilesDir(null), "screenshots")
        dir.mkdirs()
        dev.waitForIdle(3000)
        val bmp = dev.uiAutomation.takeScreenshot() ?: return
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    private fun imageMessage(attId: String) = ChatMessage(
        id = "m-$attId", conversationId = "conv-media-states", senderId = "them",
        type = MessageType.image, text = "",
        attachment = MessageAttachment(
            id = attId, type = "image", name = "photo.jpg", size = "12 KB",
            url = "chaty://media/$attId",
        ),
        createdAt = Instant.EPOCH,
    )

    private fun cacheFor(uid: String?): AttachmentFileCache =
        AttachmentFileCache(
            ins.targetContext,
            { uid?.let { SessionEpoch.Identity("synthetic-ui", it, 1L) } },
            { 8L * 1024 * 1024 },
            4L * 1024 * 1024,
        )

    private fun show(message: ChatMessage, binding: AttachmentConsumerBinding) {
        val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                ChatyTheme(ThemePresets.whatsappIosLight) {
                    MediaAttachmentPreview(
                        attachment = message.attachment!!,
                        remoteImage = null,
                        resolveUrl = null,
                        onTap = {},
                        video = false,
                        message = message,
                        binding = binding,
                    )
                }
            }
        }
        scenarioHolder = scenario
    }
    private var scenarioHolder: ActivityScenario<ChatFixtureActivity>? = null

    @Test
    fun offlineShowsWaitingForNetwork() {
        val store = cacheFor("states-offline")
        val stamp = store.session() ?: fail("signed-in fixture must yield a session stamp")
        val resolver = AttachmentSourceResolver(
            store,
            { false },
            { TrustedAttachment("image", false, false) },
            { throw IOException("offline fixture must not download") },
        )
        val binding = AttachmentConsumerBinding(
            stamp, resolver, { store.session() },
            deliverIfCurrent = { action -> action(); true },
            authorizedUrl = { error("offline fixture must not authorize URLs") },
        )
        show(imageMessage("att-offline"), binding)
        assertTrue(
            "OFFLINE must render 'Waiting for network'",
            dev.wait(Until.hasObject(By.text("Waiting for network")), 10000),
        )
        shot("media-state-offline")
        scenarioHolder?.close()
    }

    @Test
    fun retryAfterOfflineSurfacesDownloadFailed() {
        @Volatile var online = false
        val downloadCalls = AtomicInteger(0)
        val store = cacheFor("states-retry")
        val stamp = store.session() ?: fail("signed-in fixture must yield a session stamp")
        val resolver = AttachmentSourceResolver(
            store,
            { online },
            { TrustedAttachment("image", false, false) },
            { downloadCalls.incrementAndGet(); throw IOException("synthetic network failure") },
        )
        val binding = AttachmentConsumerBinding(
            stamp, resolver, { store.session() },
            deliverIfCurrent = { action -> action(); true },
            authorizedUrl = { "https://synthetic.invalid/media/att-retry" },
        )
        show(imageMessage("att-retry"), binding)
        assertTrue(
            "pre-condition: offline tile shows 'Waiting for network'",
            dev.wait(Until.hasObject(By.text("Waiting for network")), 10000),
        )
        online = true
        dev.findObject(By.text("Waiting for network")).click()
        assertTrue(
            "failed re-download must render 'Retry download'",
            dev.wait(Until.hasObject(By.text("Retry download")), 10000),
        )
        assertTrue("user retry must attempt a bounded download", downloadCalls.get() >= 1)
        shot("media-state-download-failed")
        scenarioHolder?.close()
    }

    @Test
    fun unknownAttachmentShowsNotAvailableAndIsStatic() {
        val store = cacheFor("states-unknown")
        val stamp = store.session() ?: fail("signed-in fixture must yield a session stamp")
        val resolver = AttachmentSourceResolver(
            store,
            { true },
            { null },
            { throw IOException("unknown attachment must not download") },
        )
        val binding = AttachmentConsumerBinding(
            stamp, resolver, { store.session() },
            deliverIfCurrent = { action -> action(); true },
            authorizedUrl = { error("unknown attachment must not authorize URLs") },
        )
        show(imageMessage("att-unknown"), binding)
        assertTrue(
            "NOT_ALLOWED must render 'Not available'",
            dev.wait(Until.hasObject(By.text("Not available")), 10000),
        )
        dev.findObject(By.text("Not available")).click()
        Thread.sleep(800)
        assertTrue(
            "'Not available' must be static (no retry loop)",
            dev.hasObject(By.text("Not available")),
        )
        shot("media-state-not-allowed")
        scenarioHolder?.close()
    }

    @Test
    fun signedOutShowsSignInToView() {
        val store = cacheFor(null)
        assertNull("signed-out fixture must have no session", store.session())
        val resolver = AttachmentSourceResolver(
            store,
            { true },
            { TrustedAttachment("image", false, false) },
            { throw IOException("signed-out fixture must not download") },
        )
        val binding = AttachmentConsumerBinding(
            "signed-out-stamp", resolver, { "signed-out-stamp" },
            deliverIfCurrent = { action -> action(); true },
            authorizedUrl = { error("signed-out fixture must not authorize URLs") },
        )
        show(imageMessage("att-signed-out"), binding)
        assertTrue(
            "SIGNED_OUT must render 'Sign in to view'",
            dev.wait(Until.hasObject(By.text("Sign in to view")), 10000),
        )
        shot("media-state-signed-out")
        scenarioHolder?.close()
    }
}
