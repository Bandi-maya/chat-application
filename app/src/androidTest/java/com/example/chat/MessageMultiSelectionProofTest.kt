package com.example.chat

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.ui.chats.ChatDetailScreen
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Compose interaction with synthetic messages only. No account or network send. */
@RunWith(AndroidJUnit4::class)
class MessageMultiSelectionProofTest {
    @Test fun selectedMessagesForwardAsWholeBatchAndBackExitsSelection() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val now = Instant.parse("2026-01-01T10:00:00Z")
        val messages = listOf(
            ChatMessage("s1", "synthetic", "other", text = "Selection fixture one", createdAt = now),
            ChatMessage("s2", "synthetic", "me", text = "Selection fixture two", createdAt = now.plusSeconds(1)),
        )
        val forwarded = AtomicReference<List<String>>(emptyList())
        val conversation = Conversation("synthetic", ConversationType.direct, "Selection fixture", listOf("me", "other"),
            lastMessageText = "", lastMessageTime = now, lastMessageSenderId = "other")
        val intent = Intent(inst.targetContext, ChatFixtureActivity::class.java)
        ActivityScenario.launch<ChatFixtureActivity>(intent).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                ChatyTheme(ThemePresets.getSystemDefaultTheme(false)) {
                    ChatDetailScreen(conversation, messages, "me", {}, {},
                        onForwardSelection = { batch -> forwarded.set(batch.map { it.id }) })
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.text("Selection fixture one")), 10000))
            device.findObject(By.text("Selection fixture one")).longClick()
            assertTrue(device.wait(Until.hasObject(By.text("Select")), 5000))
            device.findObject(By.text("Select")).click()
            assertTrue(device.wait(Until.hasObject(By.text("1 selected")), 5000))
            device.findObject(By.text("Selection fixture two")).click()
            assertTrue(device.wait(Until.hasObject(By.text("2 selected")), 5000))
            assertTrue(device.hasObject(By.text("Copy")))
            assertFalse("No pretend translation action", device.hasObject(By.text("Translate")))
            val pixels = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No selection pixels")
            val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/28-message-multi-selection.png")
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { pixels.compress(Bitmap.CompressFormat.PNG, 100, it) }; pixels.recycle()
            device.findObject(By.text("Forward")).click()
            inst.waitForIdleSync()
            assertEquals(listOf("s1", "s2"), forwarded.get())
            assertTrue(device.wait(Until.gone(By.text("2 selected")), 5000))
            device.findObject(By.text("Selection fixture one")).longClick()
            assertTrue(device.wait(Until.hasObject(By.text("Select")), 5000))
            device.findObject(By.text("Select")).click()
            assertTrue(device.wait(Until.hasObject(By.text("1 selected")), 5000))
            device.pressBack()
            assertTrue(device.wait(Until.gone(By.text("1 selected")), 5000))
        }
    }
}
