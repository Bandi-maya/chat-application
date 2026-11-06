package com.example.chat

import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.HomePreferences
import com.example.chat.ui.chats.home.ConversationRow
import com.example.chat.ui.chats.home.defaultHomeTemplates
import com.example.chat.ui.theme.ChatyTheme
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A-03: the conversation row is ONE accessibility node that announces the
 *  summary AND can be activated (tap + long press) through the a11y layer.
 *  Presentational only: no auth, network, or saved-state mutation. */
@RunWith(AndroidJUnit4::class)
class ConversationRowA11yProofTest {
    @Test fun rowAnnouncesSummaryAndActivatesTapAndLongPress() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val taps = AtomicInteger()
        val longPresses = AtomicInteger()
        val summary = "Priya, On my way, 1 unread message, pinned, muted"
        ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    ChatyTheme(ThemePresets.monochromeDark) {
                        ConversationRow(
                            conversation = Conversation(
                                id = "c1",
                                type = ConversationType.direct,
                                title = "Priya",
                                participantIds = listOf("me", "u1"),
                                lastMessageText = "On my way",
                                lastMessageTime = Instant.now(),
                                lastMessageSenderId = "u1",
                                unreadCount = 1,
                                isPinned = true,
                                isMuted = true,
                            ),
                            theme = ThemePresets.monochromeDark,
                            homePrefs = HomePreferences(),
                            chatList = defaultHomeTemplates.chatList,
                            density = 1.0,
                            isDark = true,
                            cardRows = false,
                            isTelegramRows = false,
                            displayTitle = "Priya",
                            presence = null,
                            currentUserId = "me",
                            lastOwnMessageState = null,
                            selected = false,
                            locked = false,
                            elapsedTime = false,
                            gbOnlineChat = false,
                            gbOnlineDotChat = false,
                            gbModOnlineColor = null,
                            gbModLastSeenColor = null,
                            gbOnlineDotColor = null,
                            gbHomeCounterBk = null,
                            gbHomeCounterText = null,
                            onTap = { taps.incrementAndGet() },
                            onLongPress = { longPresses.incrementAndGet() },
                        )
                    }
                }
            }
            assertTrue("Row summary node missing", device.wait(Until.hasObject(By.desc(summary)), 10000))
            val row = device.findObject(By.desc(summary)) ?: throw AssertionError("Row summary node missing")
            assertTrue("Row node must expose click", row.isClickable)
            assertTrue("Row node must expose long click", row.isLongClickable)
            row.click()
            device.waitForIdle()
            assertTrue("Tap did not reach the row action", taps.get() == 1)
            row.longClick()
            device.waitForIdle()
            assertTrue("Long press did not reach the row action", longPresses.get() == 1)
        }
    }
}
