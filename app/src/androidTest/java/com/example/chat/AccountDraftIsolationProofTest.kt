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
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Per-account task draft quarantine proof (synthetic sessions only, loopback
 * base URL; no live backend). An open typed draft belongs to the account that
 * typed it: switching accounts must hide it without deleting it, switching
 * back must restore it, and an activity recreation (rotation/config change)
 * must never lose typed fields.
 */
@RunWith(AndroidJUnit4::class)
class AccountDraftIsolationProofTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val outDir: File
        get() = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }

    private fun capture(device: UiDevice, name: String) {
        device.waitForIdle(3000)
        Thread.sleep(1200)
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("takeScreenshot returned null")
        FileOutputStream(File(outDir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun sh(cmd: String): String {
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(cmd)
        ).use { it.readBytes().toString(Charsets.UTF_8).trim() }
    }

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
            val texts = Regex("text=\"([^\"]*)\"").findAll(out.toString("UTF-8")).map { it.groupValues[1] }.filter { it.isNotBlank() }.take(60).toList()
            android.util.Log.w("DraftProof", "SCREENTEXTS $tag texts=$texts")
            println("SCREENTEXTS $tag texts=$texts")
        } catch (_: Exception) {}
    }

    private fun setInitialized(value: Boolean) {
        val f = ServiceLocator.apiBackend.javaClass.getDeclaredField("initialized")
        f.isAccessible = true
        f.setBoolean(ServiceLocator.apiBackend, value)
    }

    private fun seedSession(userId: String, username: String, displayName: String) {
        val prefs = instrumentation.targetContext.getSharedPreferences("chaty_api_backend", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("api_hub_token", "synthetic-test-token")
            .putString(
                "api_hub_user",
                """{"id":"$userId","username":"$username","displayName":"$displayName","avatarInitials":"T","avatarColorHex":"0xFF6366F1","about":"Synthetic","email":"","phone":"","avatarUrl":null}""",
            )
            .commit()
        setInitialized(false)
        ServiceLocator.apiBackend.initialize()
        assertTrue("synthetic session restored for $userId", ServiceLocator.apiBackend.isAuthenticated)
        assertTrue("current user is $userId", ServiceLocator.apiBackend.currentUser?.id == userId)
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
        )
    }

    private fun installFakeTasksClient() {
        val ctl = ServiceLocator.tasksController
        val fake = com.example.chat.data.api.ApiTasksClient(
            baseUrl = { "http://synthetic.invalid" },
            tenantId = { "synthetic-tenant" },
            token = { "synthetic-test-token" },
            transport = com.example.chat.data.api.ApiHttpTransport { method, url, _, _ ->
                if (method == "GET" && url.endsWith("/tasks")) {
                    val iso = Instant.now().toString()
                    200 to """{"data":[{"uid":"synthetic-task-1","chat_room_id":"synthetic-room-1","title":"Synthetic Task Alpha","description":"Synthetic","created_by_id":"synthetic-user-1","assigned_to_ids":["synthetic-user-1"],"status":"assigned","priority":"medium","due_date":"$iso","created_at":"$iso","updated_at":"$iso"}]}"""
                } else {
                    500 to "{}"
                }
            },
        )
        val f = ctl.javaClass.getDeclaredField("client"); f.isAccessible = true
        f.set(ctl, fake)
    }

    private fun openNewTaskEditor(device: UiDevice) {
        device.findObject(By.desc("Tasks")).click()
        assertTrue("Tasks page reached", device.wait(Until.hasObject(By.desc("Create task")), 15000))
        val createButtons = device.findObjects(By.desc("Create task"))
        assertTrue("task add control visible", createButtons.isNotEmpty())
        createButtons.minByOrNull { it.visibleBounds.top }!!.click()
        assertTrue("New Task editor opened", device.wait(Until.hasObject(By.text("New Task")), 10000))
    }

    private fun editFields(device: UiDevice) =
        device.findObjects(By.clazz("android.widget.EditText")).filter { it.visibleBounds.top > 0 }

    private fun typeDraft(device: UiDevice, title: String, body: String) {
        val fields = editFields(device)
        assertTrue("draft editor exposes title+description fields (found ${fields.size})", fields.size >= 2)
        fields[0].text = title
        fields[1].text = body
        device.waitForIdle(2000)
        Thread.sleep(800) // let the draft-report effect run
    }

    private fun assertDraftFields(device: UiDevice, tag: String, title: String, body: String) {
        val fields = editFields(device)
        assertTrue("$tag: editor fields present (found ${fields.size})", fields.size >= 2)
        assertTrue("$tag: title preserved (got '${fields[0].text}')", fields[0].text == title)
        assertTrue("$tag: description preserved (got '${fields[1].text}')", fields[1].text == body)
    }

    @Test
    fun openTypedDraftIsQuarantinedPerAccount() {
        val ctx = instrumentation.targetContext
        val prefs = ctx.getSharedPreferences("chaty_api_backend", Context.MODE_PRIVATE)
        val device = UiDevice.getInstance(instrumentation)
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            seedSession("synthetic-user-1", "testuser", "Test User")
            seedSyntheticChat()
            installFakeTasksClient()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            dismissOnboardingPrompts(device)
            assertTrue("Home shown", device.wait(Until.hasObject(By.text("Synthetic Peer")), 30000))
            openNewTaskEditor(device)
            typeDraft(device, "Account A draft", "belongs to A only")
            capture(device, "20-account-a-draft-open")

            // Switch to account B WITHOUT recreation: re-seeded session restore
            // fires the chat-plane loads -> apiBackend listeners -> recomposition,
            // which must re-read the account key and drop A's sheet immediately.
            seedSession("synthetic-user-2", "peer", "Synthetic Peer")
            val settle = System.currentTimeMillis() + 10000
            var cleared = false
            while (System.currentTimeMillis() < settle) {
                device.waitForIdle(1000)
                if (!device.hasObject(By.text("New Task")) && !device.hasObject(By.text("Account A draft"))) {
                    cleared = true; break
                }
                Thread.sleep(400)
            }
            capture(device, "21-account-b-after-switch")
            assertTrue(
                "A's open sheet and draft must not render under B (no recreation)",
                cleared,
            )
            // B opens its own editor: it must start empty, not with A's draft.
            openNewTaskEditor(device)
            val bFields = editFields(device)
            assertTrue("B editor fields present", bFields.size >= 2)
            assertTrue("B title starts empty (got '${bFields[0].text}')", bFields[0].text.isEmpty())
            assertTrue("B description starts empty", bFields[1].text.isEmpty())
            capture(device, "22-account-b-empty-editor")
            device.findObject(By.desc("Close")).click()
            Thread.sleep(500)

            // Switch back to A (still no recreation): the sheet reopens with
            // A's unsaved draft intact.
            seedSession("synthetic-user-1", "testuser", "Test User")
            val reopen = System.currentTimeMillis() + 10000
            var back = false
            while (System.currentTimeMillis() < reopen) {
                device.waitForIdle(1000)
                if (device.hasObject(By.text("New Task"))) { back = true; break }
                Thread.sleep(400)
            }
            assertTrue("A's sheet reopens after returning (no recreation)", back)
            assertDraftFields(device, "back-at-A", "Account A draft", "belongs to A only")
            capture(device, "23-account-a-draft-restored")
        } catch (error: Throwable) {
            logScreenTexts(device, "failure")
            try { capture(device, "00-failure-state") } catch (_: Exception) {}
            throw error
        } finally {
            try { scenario?.close() } catch (_: Exception) {}
            try { ServiceLocator.apiBackend.signOut() } catch (_: Exception) {}
            prefs.edit().remove("api_hub_token").remove("api_hub_user").commit()
            try { setInitialized(false) } catch (_: Exception) {}
        }
    }

    @Test
    fun typedDraftSurvivesRecreationRotationAndFont2() {
        val ctx = instrumentation.targetContext
        val prefs = ctx.getSharedPreferences("chaty_api_backend", Context.MODE_PRIVATE)
        val device = UiDevice.getInstance(instrumentation)
        var scenario: ActivityScenario<MainActivity>? = null
        val failures = mutableListOf<String>()
        try {
            seedSession("synthetic-user-1", "testuser", "Test User")
            seedSyntheticChat()
            installFakeTasksClient()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            dismissOnboardingPrompts(device)
            assertTrue("Home shown", device.wait(Until.hasObject(By.text("Synthetic Peer")), 30000))
            openNewTaskEditor(device)
            typeDraft(device, "Rotation draft", "body survives config change")
            val me = device.wait(Until.findObject(By.text("Me (Test)")), 10000)
            assertTrue("Me assignee tile present", me != null)
            me!!.click()
            device.waitForIdle(1000)

            // 1) Executed recreation (save/restore instance state path).
            scenario?.recreate()
            device.waitForIdle(3000); Thread.sleep(1500)
            assertTrue("sheet present after recreate", device.wait(Until.hasObject(By.text("New Task")), 10000))
            assertDraftFields(device, "recreate", "Rotation draft", "body survives config change")
            capture(device, "24-draft-after-recreate")

            // 2) Font scale 2.0 config change.
            val origFont = sh("settings get system font_scale")
            try {
                sh("settings put system font_scale 2.0")
                Thread.sleep(2500)
                var applied = 0f
                scenario?.onActivity { applied = it.resources.configuration.fontScale }
                if (applied < 1.9f) failures += "font2: font scale not applied (fontScale=$applied)"
                assertTrue("sheet present under font2", device.wait(Until.hasObject(By.text("New Task")), 10000))
                assertDraftFields(device, "font2", "Rotation draft", "body survives config change")
                capture(device, "25-draft-font2")

                // 3) Landscape under font2.
                try {
                    device.setOrientationLeft()
                    val deadline = System.currentTimeMillis() + 8000
                    var land = false
                    while (!land && System.currentTimeMillis() < deadline) {
                        scenario?.onActivity { land = it.window.decorView.width > it.window.decorView.height }
                        if (!land) Thread.sleep(250)
                    }
                    if (!land) failures += "landscape-font2: decor never became landscape"
                    assertTrue("sheet present in landscape-font2", device.wait(Until.hasObject(By.text("New Task")), 10000))
                    assertDraftFields(device, "landscape-font2", "Rotation draft", "body survives config change")
                    capture(device, "26-draft-landscape-font2")
                } finally { try { device.setOrientationNatural() } catch (_: Throwable) {} }
            } finally {
                if (origFont == "null" || origFont.isEmpty()) sh("settings delete system font_scale")
                else sh("settings put system font_scale $origFont")
            }
            assertTrue("config-change failures: $failures", failures.isEmpty())
        } catch (error: Throwable) {
            logScreenTexts(device, "failure")
            try { capture(device, "00-failure-state") } catch (_: Exception) {}
            throw error
        } finally {
            try { device.setOrientationNatural() } catch (_: Throwable) {}
            try { scenario?.close() } catch (_: Exception) {}
            try { ServiceLocator.apiBackend.signOut() } catch (_: Exception) {}
            prefs.edit().remove("api_hub_token").remove("api_hub_user").commit()
            try { setInitialized(false) } catch (_: Exception) {}
        }
    }
}
