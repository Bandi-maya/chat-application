package com.example.chat

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.chat.core.preferences.*
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.settings.home.HomeRowsSettingsScreen
import com.example.chat.ui.chats.ChatsHomeScreen
import com.example.chat.ui.theme.ChatyTheme
import kotlinx.coroutines.*
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Additional RTL/lifecycle proof only. Existing seven acceptance tests are untouched. */
@RunWith(AndroidJUnit4::class)
class SwipeRtlAndRowSettingsProofTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(inst)
    private fun refresh() { if (android.os.Build.VERSION.SDK_INT >= 33) assertTrue(inst.uiAutomation.clearCache()) }
    private fun capture(name: String) {
        val image = inst.uiAutomation.takeScreenshot() ?: error("No pixels")
        val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/32-swipe-rtl-$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    private fun drag(bounds: Rect, left: Boolean) {
        val start = if (left) bounds.left + bounds.width()*85/100 else bounds.left + bounds.width()*15/100
        val end = if (left) bounds.left + bounds.width()*15/100 else bounds.left + bounds.width()*85/100
        val time = SystemClock.uptimeMillis()
        fun event(action: Int, x: Int) {
            val e = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, x.toFloat(), bounds.centerY().toFloat(), 0)
            e.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(inst.uiAutomation.injectInputEvent(e, true)) } finally { e.recycle() }
        }
        event(MotionEvent.ACTION_DOWN, start)
        try { for (step in 1..18) { SystemClock.sleep(20); event(MotionEvent.ACTION_MOVE, start+(end-start)*step/18) } }
        finally { event(MotionEvent.ACTION_UP, end) }
        SystemClock.sleep(350)
        refresh()
    }
    @Test fun rtlPhysicalDirectionsCallbacksAndRecreateRemainScoped() {
        ActivityScenario.launch<SwipeRowFixtureActivity>(Intent(inst.targetContext, SwipeRowFixtureActivity::class.java)
            .putExtra("rtl", true)).use { scenario ->
            assertTrue(device.wait(Until.hasObject(By.desc("Synthetic row 0")), 10000))
            var activity: SwipeRowFixtureActivity? = null
            scenario.onActivity { activity = it }
            val initial = Rect(activity!!.measuredRows.getValue(0))
            drag(initial, left = true)
            capture("left-trailing")
            for (name in listOf("Pin", "Mute", "Archive")) assertTrue("RTL left reveals $name", device.hasObject(By.desc(name)))
            device.findObject(By.desc("Pin")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Events: Pin/1; row: 0")), 5000))
            SystemClock.sleep(350)
            drag(initial, left = false)
            capture("right-leading")
            assertTrue(device.hasObject(By.desc("Call")))
            assertTrue(device.hasObject(By.desc("Video")))
            device.findObject(By.desc("Video")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Events: Video/2; row: 0")), 5000))
            SystemClock.sleep(350)
            drag(initial, left = true)
            scenario.recreate()
            assertTrue(device.wait(Until.hasObject(By.desc("Synthetic row 0")), 10000))
            refresh()
            assertFalse("Open offset must not survive recreation", device.hasObject(By.desc("Archive")))
            assertTrue(device.hasObject(By.text("Events: none/0; row: 0")))
            capture("recreated-closed")
        }
    }
    @Test fun productionRowsSettingsToggleSurvivesRealLocalStoreReload() {
        val namespace = "swipe-proof-" + java.util.UUID.randomUUID()
        // Real LocalPreferencesStorage, isolated filename. Never touch live preference buckets.
        val isolatedContext = object : ContextWrapper(inst.targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) =
                baseContext.getSharedPreferences(namespace + "-" + name, mode)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val auth = object : PreferencesAuthSource {
            override fun currentUserId() = "synthetic-row-owner"
            override fun addListener(listener: (String?) -> Unit) {}
            override fun removeListener(listener: (String?) -> Unit) {}
        }
        val lock = object : LockCredentialStore {
            override suspend fun hasCredential(method: String) = false
            override suspend fun setCredential(method: String, secret: String, pinLength: Int?) {}
        }
        val storage = LocalPreferencesStorage(isolatedContext)
        fun newController() = ChatyPreferencesController(AndroidPreferencesBlobStore(storage), null, lock, auth, scope)
        fun awaitLoaded(controller: ChatyPreferencesController) {
            val deadline = SystemClock.uptimeMillis() + 5000
            while (!controller.preferencesReady && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(25)
            assertTrue("Local scoped settings load", controller.preferencesReady)
        }
        var controller: ChatyPreferencesController? = null
        try {
            ActivityScenario.launch<SwipeRowFixtureActivity>(Intent(inst.targetContext, SwipeRowFixtureActivity::class.java)).use { scenario ->
                scenario.onActivity { controller = newController() }
                awaitLoaded(controller!!)
                val first = controller!!
                scenario.onActivity { activity -> activity.setContent {
                    ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                        var settings by remember { mutableStateOf(true) }
                        var revision by remember { mutableIntStateOf(0) }
                        DisposableEffect(first) {
                            val changed: () -> Unit = { revision++ }
                            first.addListener(changed)
                            onDispose { first.removeListener(changed) }
                        }
                        @Suppress("UNUSED_VARIABLE") val observed = revision
                        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                            Button(onClick = { settings = !settings }) { Text(if (settings) "Proof show chat list" else "Proof show row settings") }
                            Box(Modifier.weight(1f)) {
                                if (settings) HomeRowsSettingsScreen(first) { settings = false }
                                else ChatsHomeScreen(
                                    conversations = ChatFixtureActivity.fixtureConversations().take(1).map { it.copy(lastMessageTime = java.time.Instant.now().minusSeconds(300)) },
                                    currentUserId = "me", requiresSubscription = false, subscriptionMessage = null,
                                    onOpenConversation = {}, homePreferences = first.home,
                                )
                            }
                        }
                    }
                } }
                assertTrue(device.wait(Until.hasObject(By.text("Rows")), 10000))
                var elapsed = device.findObject(By.text("Elapsed time"))
                for (attempt in 1..10) {
                    if (elapsed != null) break
                    device.swipe(device.displayWidth/2, device.displayHeight*75/100, device.displayWidth/2, device.displayHeight*35/100, 30)
                    refresh()
                    elapsed = device.findObject(By.text("Elapsed time"))
                }
                val label = elapsed ?: error("Actual Rows screen Elapsed time unreachable")
                label.click()
                val savedDeadline = SystemClock.uptimeMillis() + 5000
                while ((!first.home.elapsedTime || first.saveState.value != SaveState.Saved) && SystemClock.uptimeMillis() < savedDeadline) SystemClock.sleep(25)
                assertTrue("Settings UI toggles elapsed time", first.home.elapsedTime)
                assertEquals("Local disk commit acknowledged", SaveState.Saved, first.saveState.value)
                capture("production-settings-toggled")
                refresh()
                assertTrue("Safe proof navigation control present",
                    device.wait(Until.hasObject(By.text("Proof show chat list")), 5000))
                val navigation = device.findObject(By.text("Proof show chat list"))
                    ?: error("Proof navigation absent after refresh; see settings capture")
                val bounds = navigation.visibleBounds
                var safe: Rect? = null
                scenario.onActivity { activity ->
                    val decor = activity.window.decorView
                    val insets = androidx.core.view.ViewCompat.getRootWindowInsets(decor)
                        ?: error("No actual window insets for proof navigation")
                    val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                        androidx.core.view.WindowInsetsCompat.Type.displayCutout())
                    val origin = IntArray(2); decor.getLocationOnScreen(origin)
                    safe = Rect(origin[0] + bars.left, origin[1] + bars.top,
                        origin[0] + decor.width - bars.right, origin[1] + decor.height - bars.bottom)
                }
                capture("production-settings-before-navigation")
                val hierarchy = File(inst.targetContext.getExternalFilesDir(null),
                    "screenshots/32-swipe-rtl-production-settings-before-navigation.xml")
                device.dumpWindowHierarchy(hierarchy)
                assertTrue("Proof navigation has positive geometry", bounds.width() > 0 && bounds.height() > 0)
                assertTrue("Proof navigation inside actual safe window: $bounds vs $safe", safe!!.contains(bounds))
                navigation.click()
                assertTrue(device.wait(Until.hasObject(By.text("Alice Rao")), 5000))
                assertTrue("Production row applies elapsed setting", device.wait(Until.hasObject(By.text("5m ago")), 5000))
                capture("production-row-applied")
                scenario.onActivity { first.dispose(); controller = newController() }
                awaitLoaded(controller!!)
                assertTrue("Real LocalPreferencesStorage reload retains setting", controller!!.home.elapsedTime)
                val restoredHome = controller!!.home
                scenario.onActivity { activity -> activity.setContent {
                    ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                        ChatsHomeScreen(
                            conversations = ChatFixtureActivity.fixtureConversations().take(1).map { it.copy(lastMessageTime = java.time.Instant.now().minusSeconds(300)) },
                            currentUserId = "me", requiresSubscription = false, subscriptionMessage = null,
                            onOpenConversation = {}, homePreferences = restoredHome,
                        )
                    }
                } }
                refresh()
                assertTrue("Reloaded setting reaches production row", device.wait(Until.hasObject(By.text("5m ago")), 5000))
                capture("production-row-reloaded")
            }
        } finally {
            controller?.dispose()
            scope.cancel()
            // Only the UUID-named synthetic test file is deleted.
            inst.targetContext.deleteSharedPreferences(namespace + "-chaty_local_preferences")
        }
    }
}
