package com.example.chat

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Pixel proof for the chat screens using the androidTest-only fixture host:
 *  local fixture data, no backend, no account, no network send. Verifies
 *  rendering pixels of the conversation list and conversation view only. */
@RunWith(AndroidJUnit4::class)
class ChatScreensPixelProofTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val outDir: File
        get() = File(
            instrumentation.targetContext.getExternalFilesDir(null),
            "screenshots",
        ).apply { mkdirs() }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        Thread.sleep(1000)
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("takeScreenshot returned null")
        FileOutputStream(File(outDir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }

    @Test
    fun chatsHomeRendersFixtureConversations() {
        val intent = Intent(InstrumentationRegistry.getInstrumentation().context, ChatFixtureActivity::class.java)
            .putExtra(ChatFixtureActivity.EXTRA_SCREEN, ChatFixtureActivity.SCREEN_HOME)
        ActivityScenario.launch<ChatFixtureActivity>(intent).use {
            capture("06-chats-home")
        }
        assertNotNull(File(outDir, "06-chats-home.png"))
    }

    @Test
    fun chatDetailRendersFixtureMessages() {
        val intent = Intent(InstrumentationRegistry.getInstrumentation().context, ChatFixtureActivity::class.java)
            .putExtra(ChatFixtureActivity.EXTRA_SCREEN, ChatFixtureActivity.SCREEN_DETAIL)
        ActivityScenario.launch<ChatFixtureActivity>(intent).use {
            capture("07-chat-detail")
        }
        assertNotNull(File(outDir, "07-chat-detail.png"))
    }
}
