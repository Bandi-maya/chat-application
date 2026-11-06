package com.example.chat

import android.content.Intent
import android.graphics.Rect
import android.os.Build
import androidx.activity.compose.setContent
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.HomePreferences
import com.example.chat.ui.chats.ChatsHomeScreen
import com.example.chat.ui.chats.home.ChatListDensity
import com.example.chat.ui.chats.home.defaultHomeTemplates
import com.example.chat.ui.theme.ChatyTheme
import android.graphics.Bitmap
import android.view.InputDevice
import android.view.MotionEvent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No real pin/mute/archive/call actions. Run on 320dp and normal-width profiles. */
@RunWith(AndroidJUnit4::class)
class SwipeRowProofTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(inst)
    private fun capture(name: String) {
        val bitmap = inst.uiAutomation.takeScreenshot() ?: error("No screenshot")
        val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/31-swipe-$name.png")
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private var fixture: SwipeRowFixtureActivity? = null
    private fun measuredBounds(index: Int): Rect = Rect(
        fixture?.measuredRows?.get(index) ?: error("No physical measurement for row $index"),
    )
    private fun refreshAccessibility() {
        // Public API33+, compileSdk36. Older devices retain the normal query path.
        if (Build.VERSION.SDK_INT >= 33) assertTrue(inst.uiAutomation.clearCache())
    }
    private fun fixtureDiagnostics(name: String) {
        diagnostics(name)
        File(inst.targetContext.getExternalFilesDir(null), "screenshots/31-swipe-$name-state.txt")
            .writeText("${fixture?.eventSnapshot}\nrows=${fixture?.measuredRows}")
    }
    private fun diagnostics(name: String) {
        capture(name)
        val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/31-swipe-$name.xml")
        refreshAccessibility()
        device.dumpWindowHierarchy(file)
    }
    /** Interior coordinates avoid system-edge gestures. This is the same input
     * mechanism whose held-progress PNGs showed the actual tray in Next6. */
    private fun interiorDrag(bounds: Rect, left: Boolean = true, y: Int = bounds.centerY()) {
        val inset = (bounds.width() * .15f).toInt()
        val start = if (left) bounds.right - inset else bounds.left + inset
        val end = if (left) bounds.left + inset else bounds.right - inset
        val time = SystemClock.uptimeMillis()
        fun event(action: Int, x: Int) {
            val e = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, x.toFloat(), y.toFloat(), 0)
            e.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(inst.uiAutomation.injectInputEvent(e, true)) } finally { e.recycle() }
        }
        event(MotionEvent.ACTION_DOWN, start)
        try {
            for (step in 1..18) {
                SystemClock.sleep(20)
                event(MotionEvent.ACTION_MOVE, start + (end - start) * step / 18)
            }
        } finally { event(MotionEvent.ACTION_UP, end) }
    }
    private fun runFixture(dark: Boolean, landscape: Boolean) {
        if (landscape) device.setOrientationLeft() else device.setOrientationNatural()
        try {
            ActivityScenario.launch<SwipeRowFixtureActivity>(Intent(inst.targetContext, SwipeRowFixtureActivity::class.java)
                .putExtra("dark", dark)).use {
                it.onActivity { activity -> fixture = activity }
                assertTrue(device.wait(Until.hasObject(By.desc("Synthetic row 0")), 10000))
                val initial = measuredBounds(0)
                val second = measuredBounds(1)
                val suffix = (if (dark) "dark" else "light") + (if (landscape) "-landscape" else "-portrait")
                capture("$suffix-closed")
                val y = initial.centerY()
                // Hold a drag midway so pixels/geometry are checked before settling.
                val start = initial.right - (initial.width() * .15f).toInt()
                val end = initial.left + initial.width() / 3
                val time = SystemClock.uptimeMillis()
                fun inject(action: Int, x: Int) {
                    val e = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, x.toFloat(), y.toFloat(), 0)
                    e.source = InputDevice.SOURCE_TOUCHSCREEN
                    assertTrue(inst.uiAutomation.injectInputEvent(e, true))
                    e.recycle()
                }
                inject(MotionEvent.ACTION_DOWN, start)
                try {
                    for (step in 1..12) {
                        SystemClock.sleep(30)
                        inject(MotionEvent.ACTION_MOVE, start + (end-start)*step/12)
                    }
                    capture("$suffix-progress")
                    val progress = measuredBounds(0)
                    assertEquals(initial.top, progress.top)
                    assertEquals(initial.bottom, progress.bottom)
                    assertEquals(second.top, measuredBounds(1).top)
                } finally { inject(MotionEvent.ACTION_UP, end) }
                Thread.sleep(400)
                diagnostics("$suffix-after-gesture")
                val open = measuredBounds(0)
                assertTrue("Foreground not wholly lost", open.width() >= initial.width() * .39f)
                refreshAccessibility()
                for (name in listOf("Pin", "Mute", "Archive")) {
                    val action = device.findObject(By.desc(name)) ?: error("Missing $name")
                    val b = action.visibleBounds
                    assertTrue("$name inside measured row", b.top >= initial.top && b.bottom <= initial.bottom)
                    assertTrue("$name stays above next row", b.bottom <= second.top)
                }
                capture("$suffix-open")
                // Reverse partially, then Back closes without navigating/callbacks.
                device.pressBack()
                Thread.sleep(400)
                assertEquals(initial, measuredBounds(0))
                assertTrue(device.hasObject(By.text("Events: none/0; row: 0")))
                capture("$suffix-back-closed")
                // Every action dispatches once and closes; foreground tap closes without opening chat.
                for ((index, name) in listOf("Pin", "Mute", "Archive").withIndex()) {
                    interiorDrag(initial, left = true, y = y)
                    Thread.sleep(350)
                    refreshAccessibility()
                    device.findObject(By.desc(name)).click()
                    assertTrue(device.wait(Until.hasObject(By.text("Events: $name/${index+1}; row: 0")), 5000))
                    Thread.sleep(300)
                    assertEquals(initial, measuredBounds(0))
                }
                interiorDrag(initial, left = false, y = y)
                Thread.sleep(350)
                capture("$suffix-leading")
                refreshAccessibility()
                device.findObject(By.desc("Video")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Events: Video/4; row: 0")), 5000))
                Thread.sleep(350)
                interiorDrag(initial, left = true, y = y)
                Thread.sleep(350)
                val shifted = measuredBounds(0)
                device.click(shifted.centerX(), shifted.centerY())
                Thread.sleep(350)
                assertEquals(initial, measuredBounds(0))
                assertTrue(device.hasObject(By.text("Events: Video/4; row: 0")))
                device.click(initial.centerX(), y)
                assertTrue(device.wait(Until.hasObject(By.text("Events: Video/4; row: 1")), 5000))
                capture("$suffix-final")
            }
        } finally { device.setOrientationNatural(); device.unfreezeRotation() }
    }
    @Test fun enteringSelectionClosesTrayAndDisablesActionGestures() {
        ActivityScenario.launch<SwipeRowFixtureActivity>(Intent(inst.targetContext, SwipeRowFixtureActivity::class.java)).use {
            it.onActivity { activity -> fixture = activity }
            assertTrue(device.wait(Until.hasObject(By.desc("Synthetic row 0")), 10000))
            val initial = measuredBounds(0)
            val y = initial.centerY()
            interiorDrag(initial, left = true, y = y)
            Thread.sleep(350)
            diagnostics("selection-after-opening-gesture")
            assertTrue(device.hasObject(By.desc("Archive")))
            capture("selection-open-before")
            device.findObject(By.text("Enter selection")).click()
            assertTrue(device.wait(Until.gone(By.desc("Archive")), 5000))
            assertEquals(initial, measuredBounds(0))
            interiorDrag(initial, left = true, y = y)
            Thread.sleep(350)
            assertEquals(initial, measuredBounds(0))
            assertFalse(device.hasObject(By.desc("Archive")))
            fixtureDiagnostics("selection-after-disabled-drag")
            refreshAccessibility()
            assertTrue("Horizontal drag must not select a chat", device.hasObject(By.text("Events: none/0; row: 0")))
            device.click(initial.centerX(), y)
            inst.waitForIdleSync()
            fixtureDiagnostics("selection-after-row-click")
            refreshAccessibility()
            assertTrue(device.wait(Until.hasObject(By.text("Events: none/0; row: 1")), 5000))
            capture("selection-disabled-row-click")
            device.findObject(By.text("Exit selection")).click()
            assertTrue("Exit selection commits before next gesture",
                device.wait(Until.hasObject(By.text("Enter selection")), 5000))
            fixtureDiagnostics("selection-after-exit-commit")
            interiorDrag(initial, left = true, y = y)
            Thread.sleep(350)
            fixtureDiagnostics("selection-after-reenabled-drag")
            refreshAccessibility()
            assertTrue(device.hasObject(By.desc("Archive")))
            device.pressBack()
            Thread.sleep(350)
            assertTrue(device.hasObject(By.text("Events: none/0; row: 1")))
        }
    }

    @Test fun actualHomeSelectionDisablesSwipeAndKeepsSelectionTaps() {
        ActivityScenario.launch<ChatFixtureActivity>(Intent(inst.targetContext, ChatFixtureActivity::class.java)).use {
            assertTrue(device.wait(Until.hasObject(By.text("Alice Rao")), 10000))
            val alice = device.findObject(By.text("Alice Rao")).visibleBounds
            // Long press a real production row to enter selection.
            device.swipe(alice.centerX(), alice.centerY(), alice.centerX(), alice.centerY(), 120)
            assertTrue(device.wait(Until.hasObject(By.text("1")), 5000))
            Thread.sleep(350) // Selection header AnimatedContent must settle before comparing bounds.
            val trek = device.findObject(By.text("Weekend Trek")).visibleBounds
            interiorDrag(Rect(0, trek.top, device.displayWidth, trek.bottom))
            Thread.sleep(350)
            assertEquals("Selection swipe does not translate row", trek, device.findObject(By.text("Weekend Trek")).visibleBounds)
            device.findObject(By.text("Weekend Trek")).click()
            assertTrue(device.wait(Until.hasObject(By.text("2")), 5000))
            capture("actual-home-selection-two")
            device.pressBack()
            assertTrue("Selection appbar closes, independently of unread badge2", device.wait(Until.gone(By.desc("Clear selection")), 5000))
            diagnostics("actual-home-after-selection-back")
            assertTrue(device.hasObject(By.text("Alice Rao")))
        }
    }

    @Test fun actualHomeRowSwipeKeepsFollowingRowInPlace() {
        ActivityScenario.launch<ChatFixtureActivity>(Intent(inst.targetContext, ChatFixtureActivity::class.java)).use {
            assertTrue(device.wait(Until.hasObject(By.text("Alice Rao")), 10000))
            val title = device.findObject(By.text("Alice Rao"))
            var row = title
            // Use the actual row's full-width combined-clickable ancestor, not
            // the title text bounds, so the drag covers the real action tray.
            while (row.visibleBounds.width() < device.displayWidth * .80f) {
                val parent = row.parent ?: break
                row = parent
            }
            val bounds = row.visibleBounds
            val next = device.findObject(By.text("Weekend Trek")).visibleBounds
            capture("real-home-before")
            interiorDrag(bounds, y = title.visibleBounds.centerY())
            Thread.sleep(400)
            diagnostics("real-home-after-gesture")
            assertEquals("No giant lazy-list item gap", next.top, device.findObject(By.text("Weekend Trek")).visibleBounds.top)
            refreshAccessibility()
            for (name in listOf("Unpin", "Mute", "Archive")) {
                val action = device.findObject(By.desc(name)) ?: error("Missing actual $name")
                assertTrue("Actual $name above next row", action.visibleBounds.bottom <= next.top)
            }
            capture("real-home-open")
            device.pressBack()
            Thread.sleep(400)
            refreshAccessibility()
            assertTrue(device.hasObject(By.text("Alice Rao")))
            assertEquals(next, device.findObject(By.text("Weekend Trek")).visibleBounds)
            capture("real-home-closed")
        }
    }
    /** Applied typed preferences and real production rows, not synthetic fixed heights.
     * Does not prove settings navigation or disk persistence; those remain owner checks. */
    @Test fun productionRowsWithAppliedStylesDensityAndTextSizes() {
        val styles = listOf("Chaty Default", "Cards", "Telegram Style", "Compact", "Expressive")
        ActivityScenario.launch<SwipeRowFixtureActivity>(Intent(inst.targetContext, SwipeRowFixtureActivity::class.java)).use { scenario ->
            for ((index, style) in styles.withIndex()) {
                val dark = index % 2 != 0
                val config = ThemePresets.getSystemDefaultTheme(!dark)
                val prefs = HomePreferences(homeStyle = style, screenTextSize = if (index % 2 == 0) 25.5 else 11.9)
                val density = if (index % 2 == 0) ChatListDensity.compact else ChatListDensity.comfortable
                val rows = ChatFixtureActivity.fixtureConversations().take(2).mapIndexed { i, c ->
                    c.copy(title = "Long fixture contact $i with many words", isPinned = false, unreadCount = 0)
                }
                scenario.onActivity { activity ->
                    activity.setContent {
                        ChatyTheme(config) {
                            ChatsHomeScreen(
                                conversations = rows, currentUserId = "me", requiresSubscription = false,
                                subscriptionMessage = null, onOpenConversation = {}, homePreferences = prefs,
                                templates = defaultHomeTemplates.copy(chatList = defaultHomeTemplates.chatList.copy(density = density)),
                            )
                        }
                    }
                }
                assertTrue(device.wait(Until.hasObject(By.text(rows.first().title)), 10000))
                Thread.sleep(350)
                val title = device.findObject(By.text(rows.first().title)).visibleBounds
                val next = device.findObject(By.text(rows[1].title)).visibleBounds
                val label = "applied-style-$index-" + (if (dark) "dark" else "light")
                capture("$label-closed")
                interiorDrag(Rect(0, title.top, device.displayWidth, title.bottom))
                Thread.sleep(350)
                diagnostics("$label-after-gesture")
                assertEquals("$style keeps next row in place", next, device.findObject(By.text(rows[1].title)).visibleBounds)
                refreshAccessibility()
                for (name in listOf("Pin", "Mute", "Archive")) {
                    val action = device.findObject(By.desc(name)) ?: error("$style missing $name")
                    assertTrue("$style $name must not overlap next row", action.visibleBounds.bottom <= next.top)
                }
                capture("$label-open")
                device.pressBack()
                Thread.sleep(350)
                refreshAccessibility()
                assertEquals(title, device.findObject(By.text(rows.first().title)).visibleBounds)
            }
        }
    }

    @Test fun lightPortrait() = runFixture(false, false)
    @Test fun darkPortrait() = runFixture(true, false)
    @Test fun lightLandscape() = runFixture(false, true)
}
