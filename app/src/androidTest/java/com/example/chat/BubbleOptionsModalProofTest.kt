package com.example.chat

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
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
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.ChatDetailScreen
import com.example.chat.ui.chats.detail.ChatMessageBubble
import com.example.chat.ui.chats.detail.MessageActionRegistry
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proof for the requested "single-tap opens the bubble options" change, driven
 * through the PRODUCTION ChatDetailScreen: a single tap on the bubble body opens
 * the same anchored options popover (reaction rail + message actions) as a
 * long-press, routed by the screen's real openMessageMenu wiring. The menu is
 * asserted as rendered pixels on the emulator (screenshots), anchored adjacent
 * to the tapped bubble; GB-visual parity is NOT claimed beyond that.
 */
@RunWith(AndroidJUnit4::class)
class BubbleOptionsModalProofTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val dev get() = UiDevice.getInstance(ins)

    private fun shot(name: String) {
        val dir = File(ins.targetContext.getExternalFilesDir(null), "screenshots")
        dir.mkdirs()
        dev.waitForIdle(3000)
        val bmp = ins.uiAutomation.takeScreenshot() ?: return
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    private fun msg(id: String) = ChatMessage(
        id = id, conversationId = "conv-bubble-proof", senderId = "them",
        type = MessageType.text, text = "Synthetic bubble $id", createdAt = Instant.EPOCH,
    )

    private fun conversation() = Conversation(
        id = "conv-bubble-proof", type = ConversationType.direct, title = "Asha Verma",
        participantIds = listOf("me", "them"),
        lastMessageText = "Synthetic bubble one", lastMessageTime = Instant.EPOCH,
        lastMessageSenderId = "them",
    )

    private fun longPress(x: Int, y: Int) {
        val down = SystemClock.uptimeMillis()
        ins.sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0))
        SystemClock.sleep(900)
        ins.sendPointerSync(MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0))
    }

    /** Raw gesture routing on the bubble itself: tap -> onBubbleTap(rect),
     *  long-press -> onLongPress*, never both. */
    @Test
    fun bubbleGestureRoutingIsExclusive() {
        val taps = CopyOnWriteArrayList<Boolean>()
        val longPresses = CopyOnWriteArrayList<String>()
        val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                ChatyTheme(ThemePresets.whatsappIosLight) {
                    ChatMessageBubble(
                        message = msg("routing"),
                        isMe = false,
                        theme = ThemePresets.whatsappIosLight,
                        onLongPress = { longPresses.add("plain") },
                        onLongPressWithRect = { longPresses.add("rect") },
                        onBubbleTap = { rect -> taps.add(rect != null) },
                    )
                }
            }
        }
        val text = dev.wait(Until.findObject(By.text("Synthetic bubble routing")), 8000)
        assertNotNull("bubble text not rendered", text)
        val b = text.visibleBounds
        dev.click(b.centerX(), b.centerY())
        Thread.sleep(700)
        assertEquals("single tap must fire onBubbleTap exactly once", 1, taps.size)
        assertTrue("single tap must carry the bubble rect", taps[0])
        assertTrue("single tap must not fire the long-press path", longPresses.isEmpty())
        longPress(b.centerX(), b.centerY())
        Thread.sleep(700)
        assertEquals("long-press must not also fire the tap path", 1, taps.size)
        assertEquals("long-press must fire exactly one long-press callback", 1, longPresses.size)
        scenario.close()
    }

    /** Production path: tap the bubble in the real ChatDetailScreen -> the
     *  anchored options popover with the reaction rail opens next to the bubble. */
    @Test
    fun productionSingleTapOpensAnchoredOptionsMenu() {
        val message = msg("one")
        val actions = MessageActionRegistry.getAvailableActions(message = message, isMe = false)
        assertTrue("registry must yield actions for a received text message", actions.isNotEmpty())
        val reactions = CopyOnWriteArrayList<Pair<String, String>>()
        val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                ChatyTheme(ThemePresets.whatsappIosLight) {
                    ChatDetailScreen(
                        conversation = conversation(),
                        messages = listOf(message),
                        currentUserId = "me",
                        onBack = {},
                        onSend = {},
                        onToggleReaction = { id, emoji -> reactions.add(id to emoji) },
                    )
                }
            }
        }
        val bubble = dev.wait(Until.findObject(By.text("Synthetic bubble one")), 10000)
        assertNotNull("message not rendered in ChatDetailScreen", bubble)
        val bb = bubble.visibleBounds
        dev.click(bb.centerX(), bb.centerY())
        val first = dev.wait(Until.findObject(By.text(actions.first().label)), 6000)
            ?: throw AssertionError("single tap did not open the options menu in the production screen")
        for (a in actions) {
            assertTrue("missing option '${a.label}' after single tap", dev.hasObject(By.text(a.label)))
        }
        // ReleaseFeatures.MESSAGE_REACTIONS gates the rail off for this release
        // (approved: hide backend-unsupported features, no dummy controls).
        if (ReleaseFeatures.MESSAGE_REACTIONS) {
            assertTrue("reaction rail missing after single tap", dev.hasObject(By.text("👍")))
        } else {
            assertFalse("reaction rail must stay hidden while MESSAGE_REACTIONS is off", dev.hasObject(By.text("👍")))
        }
        // Anchored popover: the menu opens adjacent to the tapped bubble, not as
        // a full-width bottom sheet pinned to the screen bottom.
        val mb = first.visibleBounds
        assertTrue(
            "menu must anchor near the bubble (menu top ${mb.top}, bubble ${bb.top}-${bb.bottom})",
            mb.top > bb.top - 500 && mb.top < bb.bottom + 700,
        )
        shot("bubble-options-production-single-tap")
        if (ReleaseFeatures.MESSAGE_REACTIONS) {
            dev.findObject(By.text("👍")).click()
            Thread.sleep(900)
            assertEquals("rail reaction must reach the screen callback", listOf("one" to "👍"), reactions.toList())
        }
        assertTrue(
            "menu must dismiss after a reaction",
            dev.wait(Until.gone(By.text(actions.first().label)), 5000),
        )
        scenario.close()
    }

    /** Production path: long-press opens the same anchored options popover. */
    @Test
    fun productionLongPressOpensSameAnchoredOptionsMenu() {
        val message = msg("one")
        val actions = MessageActionRegistry.getAvailableActions(message = message, isMe = false)
        val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                ChatyTheme(ThemePresets.whatsappIosLight) {
                    ChatDetailScreen(
                        conversation = conversation(),
                        messages = listOf(message),
                        currentUserId = "me",
                        onBack = {},
                        onSend = {},
                    )
                }
            }
        }
        val bubble = dev.wait(Until.findObject(By.text("Synthetic bubble one")), 10000)
        assertNotNull("message not rendered in ChatDetailScreen", bubble)
        val bb = bubble.visibleBounds
        longPress(bb.centerX(), bb.centerY())
        dev.wait(Until.findObject(By.text(actions.first().label)), 6000)
            ?: fail("long-press did not open the options menu in the production screen")
        for (a in actions) {
            assertTrue("missing option '${a.label}' after long-press", dev.hasObject(By.text(a.label)))
        }
        if (ReleaseFeatures.MESSAGE_REACTIONS) {
            assertTrue("reaction rail missing after long-press", dev.hasObject(By.text("👍")))
        } else {
            assertFalse("reaction rail must stay hidden while MESSAGE_REACTIONS is off", dev.hasObject(By.text("👍")))
        }
        shot("bubble-options-production-long-press")
        dev.pressBack()
        assertTrue(
            "menu must dismiss on back",
            dev.wait(Until.gone(By.text(actions.first().label)), 5000),
        )
        scenario.close()
    }
}
