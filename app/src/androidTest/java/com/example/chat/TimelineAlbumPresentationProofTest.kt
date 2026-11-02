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
import com.example.chat.domain.models.*
import com.example.chat.ui.chats.ChatDetailScreen
import com.example.chat.ui.chats.detail.*
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual presentation with synthetic messages/files only. No backend sends. */
@RunWith(AndroidJUnit4::class)
class TimelineAlbumPresentationProofTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(inst)
    private fun capture(name: String) {
        val pixels = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No pixels")
        val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/$name.png")
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { pixels.compress(Bitmap.CompressFormat.PNG, 100, it) }
        pixels.recycle()
    }
    @Test fun existingHistoryOpensAtLatestWithDateChip() {
        val now = Instant.parse("2026-01-01T10:00:00Z")
        val messages = (1..60).map { ChatMessage("m$it", "synthetic", "other", text = "History fixture $it", createdAt = now.plusSeconds(it.toLong())) }
        val conversation = Conversation("synthetic", ConversationType.direct, "Timeline fixture", listOf("me", "other"),
            lastMessageText = "", lastMessageTime = now, lastMessageSenderId = "other")
        ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                ChatyTheme(ThemePresets.getSystemDefaultTheme(false)) {
                    ChatDetailScreen(conversation, messages, "me", {}, {})
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.text("History fixture 60")), 10000))
            assertFalse("Latest rather than first history row", device.hasObject(By.text("History fixture 1")))
            capture("08-open-at-latest")
        }
    }
    @Test fun albumCapsFourButCallbackRetainsSelectedMessage() {
        val photo = File(inst.targetContext.cacheDir, "synthetic-album-proof.png")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val messages = (1..7).map { ChatMessage("a$it", "synthetic", "other", type = MessageType.image,
            text = "", attachment = MessageAttachment("p$it", "image", "synthetic.png", "1KB", photo.toURI().toString()),
            createdAt = Instant.EPOCH) }
        val opened = AtomicReference<String?>(null)
        try {
            ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    ChatyTheme(ThemePresets.getSystemDefaultTheme(false)) {
                        CompactAlbumPreview(messages, null, null, null, onOpen = { message, _ -> opened.set(message.id) }, onMenu = { _, _ -> })
                    }
                } }
                assertTrue(device.wait(Until.hasObject(By.text("+3")), 10000))
                assertTrue(device.hasObject(By.desc("Album photo 4 of 7")))
                assertFalse(device.hasObject(By.desc("Album photo 5 of 7")))
                capture("07-album-four-plus-count")
                device.findObject(By.desc("Album photo 4 of 7")).click()
                inst.waitForIdleSync()
                assertEquals("a4", opened.get())
            }
        } finally { photo.delete() }
    }
}
