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
        android.util.Log.w("PixelProof", "SHOT-REQ " + name + " ms=" + System.currentTimeMillis())
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("takeScreenshot returned null")
        android.util.Log.w("PixelProof", "SHOT-DONE " + name + " ms=" + System.currentTimeMillis())
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
        val formFailures = mutableListOf<String>()
        var sheetLostOnConfigChange = false // separate state-parity finding, never masked by reopening
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
            // ReleaseFeatures.STATUS_UPDATES gates the Updates tab off for this release
            // (approved: hide backend-unsupported destinations, no dummy controls).
            if (!com.example.chat.ReleaseFeatures.STATUS_UPDATES) {
                assertTrue(
                    "Updates tab must stay hidden while STATUS_UPDATES is off",
                    device.findObject(By.desc("Updates")) == null,
                )
            } else {
            val updatesTab = device.findObject(By.desc("Updates"))
                ?: throw AssertionError("Updates destination not accessible")
            updatesTab.click()
            // Page-reached anchor must match this fixture account's actual state: a status-less synthetic
            // account correctly renders the documented unavailable pane (see StatusUnavailablePaneTest), so
            // either "My Status" (status-capable account) or the unavailable pane proves the Updates page.
            // The safe-area inset assertion below (on the "Updates" heading) is unchanged either way.
            val updatesReached = device.wait(Until.hasObject(By.text("My Status")), 10000) ||
                device.hasObject(By.textStartsWith("Status isn't available"))
            assertTrue("Updates page reached (My Status or documented unavailable pane)", updatesReached)
            val updatesTitle = device.findObjects(By.text("Updates")).minByOrNull { it.visibleBounds.top }
                ?: throw AssertionError("Updates heading not shown")
            assertTrue("Updates title below status bar/cutout: top=${updatesTitle.visibleBounds.top}, inset=$topInset",
                updatesTitle.visibleBounds.top >= topInset)
            capture(device, "10b-updates-safe-area")
            }
            device.findObject(By.desc("Calls")).click()
            capture(device, "10c-calls-before-fab-assertion")
            logScreenTexts(device, "calls-before-fab")
            assertTrue("Calls page reached", device.wait(Until.hasObject(By.text("Calls")), 10000))
            val addCall = device.wait(Until.findObject(By.desc("Add call")), 10000)
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
            // Deterministic task source: swap the controller's REST client for one backed by an in-process fake
            // transport, so every loadTasks() (entry, re-entry) returns the same synthetic list. No network.
            run {
                val ctl = ServiceLocator.tasksController
                val fake = com.example.chat.data.api.ApiTasksClient(
                    baseUrl = { "http://synthetic.invalid" },
                    tenantId = { "synthetic-tenant" },
                    token = { "synthetic-test-token" },
                    transport = com.example.chat.data.api.ApiHttpTransport { method, url, _, _ ->
                        if (method == "GET" && url.endsWith("/tasks")) {
                            val iso = Instant.now().toString()
                            200 to """{"data":[{"uid":"synthetic-task-1","chat_room_id":"synthetic-room-1","title":"Synthetic Task Alpha","description":"Synthetic","created_by_id":"synthetic-user-1","assigned_to_ids":["synthetic-user-1"],"status":"assigned","priority":"medium","due_date":"$iso","created_at":"$iso","updated_at":"$iso"}]}"""
                        } else if (method == "PUT" && url.endsWith("/tasks/synthetic-task-1")) {
                            val iso = Instant.now().toString()
                            200 to """{"data":{"uid":"synthetic-task-1","chat_room_id":"synthetic-room-1","title":"Synthetic Task Beta","description":"Synthetic","created_by_id":"synthetic-user-1","assigned_to_ids":["synthetic-user-1"],"status":"assigned","priority":"medium","due_date":"$iso","created_at":"$iso","updated_at":"$iso"}}"""
                        } else {
                            500 to "{}"
                        }
                    },
                )
                val f = ctl.javaClass.getDeclaredField("client"); f.isAccessible = true
                f.set(ctl, fake)
            }
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
            // System Back from Task Detail must return to the task list, not leave the Tasks tab.
            val taskRow = device.wait(Until.findObject(By.text("Synthetic Task Alpha")), 10000)
                ?: throw AssertionError("seeded synthetic task not shown in Tasks list")
            taskRow.click()
            // Next8 run: detail DID open (screen texts had Task Properties/Activity Timeline) but no "Task Details"
            // text node was exposed, so key the proof on body content that is observed to render.
            assertTrue("task detail opens", device.wait(Until.hasObject(By.text("Task Properties")), 10000))
            assertTrue("Task Details title visible", device.wait(Until.hasObject(By.text("Task Details")), 10000))
            capture(device, "10f0-task-detail-open")
            // Detail is a full-screen pushed page (api parity): no shell Create-task FAB over it.
            assertTrue("no Create task FAB on pushed task detail", !device.hasObject(By.desc("Create task")))
            // Edit sheet is a window-level overlay above the pushed page, not hidden beneath it.
            device.findObjects(By.desc("Edit Task")).minByOrNull { it.visibleBounds.top }!!.click()
            assertTrue("edit sheet opens over detail", device.wait(Until.hasObject(By.text("Edit Task")), 10000))
            assertTrue("edit sheet shows the task title field", device.hasObject(By.text("Synthetic Task Alpha")))
            capture(device, "10f1-task-edit-sheet-over-detail")
            // Whole form must be reachable above the OS nav bar: scroll the sheet to its end and check the last control.
            var sheetBottomLimit = device.displayHeight
            scenario?.onActivity { activity ->
                val nav = activity.window.decorView.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.navigationBars())
                if (nav != null) sheetBottomLimit = device.displayHeight - nav.bottom
            }
            repeat(3) {
                device.swipe(device.displayWidth / 2, (device.displayHeight * 0.85).toInt(),
                    device.displayWidth / 2, (device.displayHeight * 0.35).toInt(), 20)
            }
            // CI swiftshader settles the sheet's expansion + a11y exposure slower than the
            // pixels (build 82: hierarchy truncated at Urgent while the capture already showed
            // the full form) - 15s window, the app's end state is verified by 00-failure-state.
            val saveTile = device.wait(Until.findObject(By.text("Save Changes")), 15000)
                ?: throw AssertionError("Save Changes not reachable in edit sheet")
            assertTrue("Save Changes above nav bar: bottom=${saveTile.visibleBounds.bottom}, limit=$sheetBottomLimit",
                saveTile.visibleBounds.bottom <= sheetBottomLimit)
            val urgent = device.findObject(By.text("Urgent"))
            if (urgent != null) assertTrue("Priority row above nav bar: bottom=${urgent.visibleBounds.bottom}, limit=$sheetBottomLimit",
                urgent.visibleBounds.bottom <= sheetBottomLimit)
            capture(device, "10f1b-task-edit-sheet-scrolled-end")
            // Whole-form safety variants. Each variant runs independently: failures are collected, never thrown
            // mid-way, and every capture is taken BEFORE its assertions so pixels exist even for a failing variant.
            fun sh(cmd: String) = device.executeShellCommand(cmd).trim()
            fun reopenEditIfNeeded(tag: String) {
                if (!device.hasObject(By.text("Edit Task"))) {
                    sheetLostOnConfigChange = true
                    formFailures += "$tag: edit sheet was not present after the change (state lost); reopened to continue"
                    device.findObjects(By.desc("Edit Task")).minByOrNull { it.visibleBounds.top }?.click()
                    device.wait(Until.hasObject(By.text("Edit Task")), 10000)
                }
            }
            fun checkForm(tag: String, expectIme: Boolean = false) {
                try {
                    reopenEditIfNeeded(tag)
                    // Swipe inside the sheet area that is actually visible: above the keyboard when it is up, so the
                    // gesture scrolls the sheet instead of landing on the keyboard.
                    var imeTopNow = device.displayHeight
                    scenario?.onActivity { act ->
                        val d = act.window.decorView; val ib = d.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.ime())?.bottom ?: 0
                        val l = IntArray(2); d.getLocationOnScreen(l); imeTopNow = l[1] + d.height - ib
                    }
                    val swipeFrom = (imeTopNow * 0.85).toInt(); val swipeTo = (imeTopNow * 0.30).toInt()
                    repeat(4) { device.swipe(device.displayWidth / 2, swipeFrom, device.displayWidth / 2, swipeTo, 20) }
                    val save = device.wait(Until.findObject(By.text("Save Changes")), 5000)
                    capture(device, "10f1c-edit-form-$tag")
                    if (save == null) { formFailures += "$tag: Save Changes not found"; return }
                    var topInset = 0; var navBottom = 0; var imeBottom = 0
                    var winTop = 0; var winBottom = device.displayHeight; var decorW = 0; var decorH = 0
                    scenario?.onActivity { act ->
                        val decor = act.window.decorView
                        val loc = IntArray(2); decor.getLocationOnScreen(loc)
                        winTop = loc[1]; winBottom = loc[1] + decor.height; decorW = decor.width; decorH = decor.height
                        val i = decor.rootWindowInsets
                        if (i != null) {
                            topInset = i.getInsets(android.view.WindowInsets.Type.statusBars()).top
                            navBottom = i.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
                            imeBottom = i.getInsets(android.view.WindowInsets.Type.ime()).bottom
                        }
                    }
                    if (expectIme) {
                        val shown = sh("dumpsys input_method").contains("mInputShown=true")
                        if (!shown) { formFailures += "$tag: keyboard not actually shown"; return }
                        if (imeBottom <= 0) { formFailures += "$tag: keyboard shown but IME inset reads 0; bounds check would be a silent pass"; return }
                    }
                    if (tag.startsWith("landscape") && decorW <= decorH) { formFailures += "$tag: decor still portrait ($decorW x $decorH)"; return }
                    // insets are relative to the window; translate to screen coordinates via the decor location
                    val usableBottom = winBottom - maxOf(navBottom, imeBottom)
                    val safeTop = winTop + topInset
                    val r = save.visibleBounds
                    if (r.height() <= 0 || r.width() <= 0) formFailures += "$tag: Save has empty bounds $r"
                    if (r.top < safeTop) formFailures += "$tag: Save top ${r.top} above safe top $safeTop"
                    if (r.bottom > usableBottom) formFailures += "$tag: Save bottom ${r.bottom} under usable bottom $usableBottom (nav=$navBottom ime=$imeBottom)"
                } catch (e: Throwable) { formFailures += "$tag: ${e.message}" }
            }
            checkForm("portrait")
            // IME: focus the title field, keyboard up, then the end of the form must still scroll clear.
            try { device.findObject(By.text("Synthetic Task Alpha"))?.click(); Thread.sleep(1000) } catch (_: Throwable) {}
            checkForm("ime", expectIme = true)
            try { sh("input keyevent 111") } catch (_: Throwable) {} // ESC hides the IME without dismissing the sheet
            // Font scale: remember the ORIGINAL value and restore exactly that.
            val origFont = sh("settings get system font_scale")
            try {
                sh("settings put system font_scale 2.0")
                Thread.sleep(2500)
                var applied = 0f
                scenario?.onActivity { applied = it.resources.configuration.fontScale }
                if (applied < 1.9f) formFailures += "font2: font scale not applied (fontScale=$applied)"
                checkForm("font2")
                try {
                    device.setOrientationLeft()
                    val deadline = System.currentTimeMillis() + 8000
                    var land = false
                    while (!land && System.currentTimeMillis() < deadline) {
                        scenario?.onActivity { land = it.window.decorView.width > it.window.decorView.height }
                        if (!land) Thread.sleep(250)
                    }
                    if (!land) formFailures += "landscape-font2: decor never became landscape"
                    checkForm("landscape-font2")
                } finally { try { device.setOrientationNatural() } catch (_: Throwable) {} }
            } finally {
                if (origFont == "null" || origFont.isEmpty()) sh("settings delete system font_scale")
                else sh("settings put system font_scale $origFont")
            }
            // Wait for the restored (portrait, original font scale) configuration to be ACTUALLY applied and the
            // activity settled before touching the sheet; capture the state before Close for evidence.
            run {
                val end = System.currentTimeMillis() + 10000
                var ok = false
                while (!ok && System.currentTimeMillis() < end) {
                    scenario?.onActivity { ok = it.window.decorView.height > it.window.decorView.width }
                    if (!ok) Thread.sleep(250)
                }
                if (!ok) formFailures += "restore: decor did not return to portrait"
                device.waitForIdle(3000); Thread.sleep(1000)
            }
            reopenEditIfNeeded("restore")
            capture(device, "10f1d-edit-sheet-after-mode-restore")
            // A restored modal already showing Close needs no drag. Blind downward swipes
            // can dismiss the modal itself, invalidating the subsequent Close lookup.
            var closeBtn = device.wait(Until.findObject(By.desc("Close")), 5000)
            for (attempt in 0 until 3) {
                if (closeBtn != null) break
                val header = device.findObject(By.text("Edit Task"))
                    ?: throw AssertionError("Edit sheet absent before Close lookup (attempt=$attempt, failures=$formFailures)")
                // Scroll only inside the sheet body, never its draggable top edge.
                device.swipe(device.displayWidth / 2, (device.displayHeight * 0.65).toInt(),
                    device.displayWidth / 2, minOf((device.displayHeight * 0.9).toInt(), device.displayHeight - 120), 20)
                closeBtn = device.wait(Until.findObject(By.desc("Close")), 2000)
            }
            capture(device, "10f1d-edit-before-close")
            val restoredClose = closeBtn
                ?: throw AssertionError("Close not found after mode restore (sheetLostOnConfigChange=$sheetLostOnConfigChange, failures=$formFailures)")
            restoredClose.click()
            assertTrue("edit sheet closes back to detail", device.wait(Until.gone(By.text("Edit Task")), 10000) &&
                device.hasObject(By.text("Task Properties")))
            // Edit refresh: a controller update (fake PUT 200, same path the sheet's Save uses) must show in the
            // open detail without reopening it (api _openEditTask re-reads the task by id).
            // DIAGNOSTIC ONLY: independent test-side listener on the same controller, so the
            // DETAIL-DIAG line can distinguish controller-never-notified from
            // notified-but-UI-stale (Compose listener/recomposition path). Verdict unchanged.
            val diagNotifies = java.util.concurrent.atomic.AtomicInteger(0)
            val diagListener: () -> Unit = { diagNotifies.incrementAndGet() }
            ServiceLocator.tasksController.addListener(diagListener)
            kotlinx.coroutines.runBlocking {
                ServiceLocator.tasksController.updateTask(
                    "synthetic-task-1", "Synthetic Task Beta", "Synthetic", listOf("synthetic-user-1"), "medium",
                    Instant.now().toString(),
                )
            }
            var passiveRefreshOk = false
            val passiveDeadline = System.currentTimeMillis() + 10000
            while (System.currentTimeMillis() < passiveDeadline) {
                // Fresh hierarchy each poll: UiAutomator's cache can hold the pre-update title
                // past the actual recomposition (121's capture showed Beta on screen while the
                // cached a11y tree still reported Alpha), so mirror the incoming-event pattern.
                if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
                if (device.hasObject(By.text("Synthetic Task Beta"))) { passiveRefreshOk = true; break }
                Thread.sleep(250)
            }
            // DIAGNOSTIC ONLY (does not change the original verdict below): at passive-wait
            // expiry, record controller state and the open detail's a11y exposure BEFORE any
            // navigation; screenshot request/completion times come from SHOT-REQ/SHOT-DONE.
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
                val ctlTitle = ServiceLocator.tasksController.tasks.firstOrNull { it.id == "synthetic-task-1" }?.title
                val a11yBeta = device.hasObject(By.text("Synthetic Task Beta"))
                val a11yAlpha = device.hasObject(By.text("Synthetic Task Alpha"))
                val a11yProps = device.hasObject(By.text("Task Properties"))
                ServiceLocator.tasksController.removeListener(diagListener)
                android.util.Log.w("PixelProof", "DETAIL-DIAG at-expiry controllerTitle=" + ctlTitle + " a11yBeta=" + a11yBeta + " a11yAlpha=" + a11yAlpha + " a11yTaskProps=" + a11yProps + " testListenerNotifies=" + diagNotifies.get() + " hasLoadedOnce=" + ServiceLocator.tasksController.hasLoadedOnce)
                logScreenTexts(device, "at-expiry")
                capture(device, "10f2a-open-detail-after-controller-update")
                device.pressBack()
                device.wait(Until.gone(By.text("Task Properties")), 10000)
                val row = device.wait(Until.findObject(By.text("Synthetic Task Beta")), 5000)
                    ?: device.wait(Until.findObject(By.text("Synthetic Task Alpha")), 5000)
                row?.click()
                val reopenedShowsUpdated = device.wait(Until.hasObject(By.text("Synthetic Task Beta")), 10000) &&
                    device.hasObject(By.text("Task Properties"))
                capture(device, "10f2b-reopened-detail-after-controller-update")
                logScreenTexts(device, "detail-refresh passive=$passiveRefreshOk reopened=$reopenedShowsUpdated")
                if (!reopenedShowsUpdated) {
                    // Restore the pre-diagnostic state for the unchanged assertions below.
                    device.pressBack()
                    device.wait(Until.gone(By.text("Task Properties")), 5000)
                    (device.wait(Until.findObject(By.text("Synthetic Task Beta")), 5000)
                        ?: device.wait(Until.findObject(By.text("Synthetic Task Alpha")), 5000))?.click()
                    device.wait(Until.hasObject(By.text("Task Properties")), 10000)
                }
            }
            assertTrue("open detail shows edited title", passiveRefreshOk)
            assertTrue("detail stayed open", device.hasObject(By.text("Task Properties")))
            capture(device, "10f2-task-detail-after-edit")
            device.pressBack()
            assertTrue("system Back from detail returns to task list",
                device.wait(Until.gone(By.text("Task Properties")), 10000) &&
                    device.wait(Until.hasObject(By.text("Synthetic Task Beta")), 10000))
            assertTrue("Create task FAB is back on the list", device.hasObject(By.desc("Create task")))
            capture(device, "10f-task-detail-back-to-list")
            device.findObject(By.desc("Chats")).click()
            // Navigation animates to the Chats page. The tab click is not a completed transition.
            assertTrue("Chats top bar appears after returning from Tasks",
                device.wait(Until.hasObject(By.desc("More options")), 10000))
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
            val incomingNotifications = java.util.concurrent.atomic.AtomicInteger(0)
            val incomingOnMain = java.util.concurrent.atomic.AtomicBoolean(false)
            val incomingProbe: () -> Unit = {
                incomingNotifications.incrementAndGet()
                incomingOnMain.set(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
            }
            val incomingStart = android.os.SystemClock.elapsedRealtime()
            val incomingSamples = mutableListOf<String>()
            var incomingVisible = false
            ServiceLocator.apiBackend.addListener(incomingProbe)
            try {
                realtimeListener.onNewMessage(ChatMessage(
                    id = "incoming-ui-proof", conversationId = "synthetic-room-1", senderId = "synthetic-user-2",
                    text = "Synthetic new incoming", createdAt = Instant.now(),
                ))
                // Same 10-second deadline. Refresh the public accessibility cache before each query;
                // Next9's later failure screenshot showed this bubble despite a stale text hierarchy.
                val incomingDeadline = incomingStart + 10000L
                while (android.os.SystemClock.elapsedRealtime() < incomingDeadline) {
                    if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
                    val found = device.hasObject(By.text("Synthetic new incoming"))
                    val sampledAt = android.os.SystemClock.elapsedRealtime()
                    val ids = ServiceLocator.apiBackend.getMessages("synthetic-room-1").map { it.id }
                    incomingSamples.add("elapsedMs=${sampledAt - incomingStart} visible=$found ids=$ids notifications=${incomingNotifications.get()} onMain=${incomingOnMain.get()}")
                    if (found && sampledAt <= incomingDeadline) { incomingVisible = true; break }
                    val remaining = incomingDeadline - android.os.SystemClock.elapsedRealtime()
                    if (remaining > 0) android.os.SystemClock.sleep(minOf(50L, remaining))
                }
            } finally {
                ServiceLocator.apiBackend.removeListener(incomingProbe)
                File(outDir, "incoming-open-timing.txt").writeText(incomingSamples.joinToString("\n"))
            }
            if (!incomingVisible) {
                device.dumpWindowHierarchy(File(outDir, "incoming-open-deadline.xml"))
                capture(device, "incoming-open-deadline") // Diagnostic only, never a late pass.
            }
            assertTrue("incoming event renders in already-open chat within 10000ms", incomingVisible)
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
            // Aggregated at the END so the rest of the workflow (edit refresh, Back, chat) always executes.
            assertTrue("edit-sheet whole-form variants (sheetLostOnConfigChange=$sheetLostOnConfigChange): $formFailures",
                formFailures.isEmpty())
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
}
