package com.example.chat

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.UserProfile
import java.time.Instant
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Configured-AppRoot proof (synthetic session only). Seeds a FAKE REST session
 * (token "synthetic-test-token", user "Test User") into the app's own
 * chaty_api_backend preferences, forces the one-shot ApiBackendService
 * initialize() to restore it, and launches the real MainActivity/AppRoot so
 * the authenticated Home route (wired MainNavigationShell: template, prefs,
 * FAB, center action) is rendered. The REST base URL is the emulator loopback
 * (10.0.2.2:8000), so no live backend sees the fake token. Not a login test.
 */
@RunWith(AndroidJUnit4::class)
class AppRootConfiguredProofTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val outDir: File
        get() = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }

    private fun capture(device: UiDevice, name: String) {
        device.waitForIdle(3000)
        Thread.sleep(1500)
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("takeScreenshot returned null")
        FileOutputStream(File(outDir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    /** Diagnostic only: logs the visible UI texts (synthetic app UI) so a failed wait explains itself. */
    /** Reachable first-run onboarding (Dart notification education sheet): answer it with the
     *  real "Not Now" control, exactly as a user would, before asserting on Home. Assertions
     *  below are unchanged; this only clears the prompt that legitimately covers Home. */
    private fun dismissOnboardingPrompts(device: UiDevice) {
        repeat(4) {
            val btn = device.wait(Until.findObject(By.text("Not Now")), 8000) ?: return
            btn.click()
            device.waitForIdle()
            Thread.sleep(800)
        }
    }

    private fun logScreenTexts(device: UiDevice, tag: String) {
        try {
            val out = java.io.ByteArrayOutputStream()
            device.dumpWindowHierarchy(out)
            val xml = out.toString("UTF-8")
            val texts = Regex("text=\"([^\"]*)\"").findAll(xml).map { it.groupValues[1] }.filter { it.isNotBlank() }.take(60).toList()
            val pkgs = Regex("package=\"([^\"]*)\"").findAll(xml).map { it.groupValues[1] }.toSet()
            android.util.Log.w("PixelProof", "SCREENTEXTS $tag pkgs=$pkgs texts=$texts")
            println("SCREENTEXTS $tag pkgs=$pkgs texts=$texts")
        } catch (e: Exception) {
            android.util.Log.w("PixelProof", "SCREENTEXTS $tag failed: $e")
        }
    }

    private fun setInitialized(value: Boolean) {
        val f = ServiceLocator.apiBackend.javaClass.getDeclaredField("initialized")
        f.isAccessible = true
        f.setBoolean(ServiceLocator.apiBackend, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(name: String): T {
        val f = ServiceLocator.apiBackend.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(ServiceLocator.apiBackend) as T
    }

    private fun seedSyntheticChat() {
        val now = Instant.now()
        field<MutableList<UserProfile>>("_users").add(
            UserProfile(
                id = "synthetic-user-2", displayName = "Synthetic Peer", username = "peer",
                avatarInitials = "SP", avatarColorHex = "0xFF10B981", about = "Synthetic",
                lastSeenAt = now,
            ),
        )
        field<MutableList<Conversation>>("_conversations").add(
            Conversation(
                id = "synthetic-room-1", type = ConversationType.direct, title = "Synthetic Peer",
                participantIds = listOf("synthetic-user-1", "synthetic-user-2"),
                avatarInitials = "SP", avatarColorHex = "0xFF10B981",
                lastMessageText = "Synthetic preview", lastMessageTime = now, lastMessageSenderId = "synthetic-user-2",
            ),
        )
        field<MutableMap<String, MutableList<ChatMessage>>>("_messages")["synthetic-room-1"] = mutableListOf(
            ChatMessage(id = "sm1", conversationId = "synthetic-room-1", senderId = "synthetic-user-2", text = "Synthetic bubble one", createdAt = now.minusSeconds(120)),
            ChatMessage(id = "sm2", conversationId = "synthetic-room-1", senderId = "synthetic-user-1", text = "Synthetic bubble two", createdAt = now.minusSeconds(60)),
        )
    }

    @Test
    fun configuredAppRootOpensChatDetailHostAndContactInfo() {
        val ctx = instrumentation.targetContext
        val prefs = ctx.getSharedPreferences("chaty_api_backend", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("api_hub_token", "synthetic-test-token")
            .putString(
                "api_hub_user",
                """{"id":"synthetic-user-1","username":"testuser","displayName":"Test User","avatarInitials":"T","avatarColorHex":"0xFF6366F1","about":"Synthetic","email":"","phone":"","avatarUrl":null}""",
            )
            .commit()
        val device = UiDevice.getInstance(instrumentation)
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            setInitialized(false)
            ServiceLocator.apiBackend.initialize()
            assertTrue("synthetic session restored", ServiceLocator.apiBackend.isAuthenticated)
            seedSyntheticChat()
            ServiceLocator.preferencesController.updateHome(ServiceLocator.preferencesController.home.copy(searchPlacement = "Header action", disableSearchBar = false))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            dismissOnboardingPrompts(device)
            if (!device.wait(Until.hasObject(By.text("Synthetic Peer")), 30000)) {
                logScreenTexts(device, "failure"); try { capture(device, "00-failure-state") } catch (_: Exception) {}
                throw AssertionError("seeded conversation row not shown on Home")
            }
            assertTrue("all destinations visible, not More", device.findObject(By.text("More")) == null)
            assertTrue("Settings visible in dock", device.findObject(By.desc("Settings")) != null || device.findObject(By.text("Settings")) != null)
            capture(device, "10-home-all-destinations")
            val searchButton = device.wait(Until.findObject(By.desc("Search chats")), 5000)
                ?: throw AssertionError("Home search entrypoint not accessible")
            searchButton.click()
            val searchField = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10000)
                ?: throw AssertionError("Search did not open an editable field")
            searchField.text = "Synthetic"
            assertTrue("search text accepted without closing app", device.wait(Until.hasObject(By.text("Synthetic Peer")), 10000))
            capture(device, "10a-home-search-input")
            device.pressBack() // dismiss IME
            device.pressBack() // close search, not exit Home
            assertTrue("search Back leaves app open", device.wait(Until.hasObject(By.text("Synthetic Peer")), 10000))
            var topInset = 0
            scenario?.onActivity { activity ->
                val insets = activity.window.decorView.rootWindowInsets
                if (insets != null) topInset = insets.getInsets(
                    android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.displayCutout(),
                ).top
            }
            val updatesTab = device.findObject(By.desc("Updates"))
                ?: throw AssertionError("Updates destination not accessible")
            updatesTab.click()
            assertTrue("Updates page reached", device.wait(Until.hasObject(By.text("My Status")), 10000))
            val updatesTitle = device.findObjects(By.text("Updates")).minByOrNull { it.visibleBounds.top }
                ?: throw AssertionError("Updates heading not shown")
            assertTrue("Updates title below status bar/cutout: top=${updatesTitle.visibleBounds.top}, inset=$topInset",
                updatesTitle.visibleBounds.top >= topInset)
            capture(device, "10b-updates-safe-area")
            device.findObject(By.desc("Calls")).click()
            val addCall = device.wait(Until.findObject(By.text("Add call")), 10000)
                ?: throw AssertionError("Add call button not visible")
            val callsDock = device.findObject(By.desc("Calls"))
                ?: throw AssertionError("Calls dock item not visible")
            var fullAddCall = addCall
            while (!fullAddCall.isClickable && fullAddCall.parent != null) fullAddCall = fullAddCall.parent
            assertTrue("Add call fully above dock", fullAddCall.visibleBounds.bottom < callsDock.visibleBounds.top)
            capture(device, "10c-calls-fab-above-dock")
            addCall.click()
            assertTrue("dialer opens", device.wait(Until.hasObject(By.desc("Delete digit")), 10000))
            var safeWindow = android.graphics.Rect(0, 0, device.displayWidth, device.displayHeight)
            scenario?.onActivity { activity ->
                val insets = activity.window.decorView.rootWindowInsets?.getInsets(
                    android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout(),
                ) ?: throw AssertionError("Missing system insets for dialer proof")
                safeWindow = android.graphics.Rect(insets.left, insets.top,
                    device.displayWidth - insets.right, device.displayHeight - insets.bottom)
            } ?: throw AssertionError("Missing activity for dialer bounds")
            DialerUiAssertions.verify(device, safeWindow)
            capture(device, "10d-dialer-round-keys-safe-bottom")
            device.pressBack()
            device.pressBack() // Calls -> Updates
            device.pressBack()
            assertTrue("Back from Updates returns Chats", device.wait(Until.hasObject(By.text("Synthetic Peer")), 10000))
            device.findObject(By.desc("Tasks")).click()
            assertTrue("Tasks entry does not open editor", !device.wait(Until.hasObject(By.text("New Task")), 1500))
            val createButtons = device.findObjects(By.desc("Create task"))
            assertTrue("explicit task add control visible", createButtons.isNotEmpty())
            createButtons.minByOrNull { it.visibleBounds.top }!!.click()
            assertTrue("explicit add opens editor", device.wait(Until.hasObject(By.text("New Task")), 10000))
            capture(device, "10e-task-sheet-single-handle")
            device.findObject(By.desc("Close")).click()
            device.findObject(By.desc("Chats")).click()
            device.findObject(By.desc("Tasks")).click()
            assertTrue("Tasks reentry keeps editor closed", !device.wait(Until.hasObject(By.text("New Task")), 1500))
            // Shell-owned FAB is the lower control and uses the consumed hoisted event.
            device.findObjects(By.desc("Create task")).maxByOrNull { it.visibleBounds.top }!!.click()
            assertTrue("shell FAB opens editor once", device.wait(Until.hasObject(By.text("New Task")), 10000))
            device.findObject(By.desc("Close")).click()
            device.findObject(By.desc("Chats")).click()
            device.findObject(By.desc("Tasks")).click()
            assertTrue("consumed FAB event does not reopen editor", !device.wait(Until.hasObject(By.text("New Task")), 1500))
            device.findObject(By.desc("Chats")).click()
            device.findObject(By.desc("More options")).click()
            assertTrue("custom Home menu opens", device.wait(Until.hasObject(By.text("New Group")), 10000))
            capture(device, "10f-home-custom-menu")
            device.pressBack()
            device.findObject(By.text("Synthetic Peer")).click()
            if (!device.wait(Until.hasObject(By.text("Synthetic bubble one")), 20000)) {
                logScreenTexts(device, "failure"); try { capture(device, "00-failure-state") } catch (_: Exception) {}
                throw AssertionError("ChatDetailHost did not render the seeded messages")
            }
            // Drive a new incoming event through the real API listener, not a preseeded
            // list, and prove the already-open Compose timeline updates.
            val realtimeListener = field<com.example.chat.data.api.ApiRealtimeClient.Listener>("realtimeListener")
            realtimeListener.onNewMessage(ChatMessage(
                id = "incoming-ui-proof", conversationId = "synthetic-room-1", senderId = "synthetic-user-2",
                text = "Synthetic new incoming", createdAt = Instant.now(),
            ))
            assertTrue("incoming event renders in already-open chat", device.wait(Until.hasObject(By.text("Synthetic new incoming")), 10000))
            capture(device, "11-chat-detail-host")
            device.findObject(By.desc("Emoji")).click()
            assertTrue("emoji picker opens", device.wait(Until.hasObject(By.desc("Search emoji")), 10000))
            capture(device, "11a-emoji-ten-dp-container")
            device.pressBack()
            // Header tap (Dart InkWell title -> ContactInfoScreen).
            device.findObject(By.text("Synthetic Peer")).click()
            if (!device.wait(Until.hasObject(By.text("Contact Info")), 20000)) {
                logScreenTexts(device, "failure"); try { capture(device, "00-failure-state") } catch (_: Exception) {}
                throw AssertionError("header tap did not open Contact Info")
            }
            capture(device, "12-contact-info")
            device.pressBack()
            assertTrue(
                "back from Contact Info returns to the chat",
                device.wait(Until.hasObject(By.text("Synthetic bubble one")), 10000),
            )
            device.pressBack()
            assertTrue("system Back from chat returns Home without exiting", device.wait(Until.hasObject(By.text("Synthetic Peer")), 10000))
            assertTrue("Home composer closed", device.findObject(By.desc("Send message")) == null)
            capture(device, "13-system-back-home")
        } finally {
            try { scenario?.close() } catch (_: Exception) {}
            try { ServiceLocator.apiBackend.signOut() } catch (_: Exception) {}
            prefs.edit().remove("api_hub_token").remove("api_hub_user").commit()
            try { setInitialized(false) } catch (_: Exception) {}
        }
    }

    @Test
    fun configuredAppRootRendersAuthenticatedShell() {
        val ctx = instrumentation.targetContext
        val prefs = ctx.getSharedPreferences("chaty_api_backend", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("api_hub_token", "synthetic-test-token")
            .putString(
                "api_hub_user",
                """{"id":"synthetic-user-1","username":"testuser","displayName":"Test User","avatarInitials":"T","avatarColorHex":"0xFF6366F1","about":"Synthetic","email":"","phone":"","avatarUrl":null}""",
            )
            .commit()
        val device = UiDevice.getInstance(instrumentation)
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            setInitialized(false)
            ServiceLocator.apiBackend.initialize()
            assertTrue("synthetic session restored", ServiceLocator.apiBackend.isAuthenticated)
            scenario = ActivityScenario.launch(MainActivity::class.java)
            dismissOnboardingPrompts(device)
            val shown = device.wait(Until.hasObject(By.text("Chats")), 30000)
            if (!shown) {
                logScreenTexts(device, "failure"); try { capture(device, "00-failure-state") } catch (_: Exception) {}
                throw AssertionError("authenticated shell did not render a 'Chats' label")
            }
            capture(device, "10-approot-configured-shell")
        } finally {
            try { scenario?.close() } catch (_: Exception) {}
            try { ServiceLocator.apiBackend.signOut() } catch (_: Exception) {}
            prefs.edit().remove("api_hub_token").remove("api_hub_user").commit()
            try { setInitialized(false) } catch (_: Exception) {}
        }
    }
}
