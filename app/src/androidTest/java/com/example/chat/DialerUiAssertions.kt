package com.example.chat

import android.graphics.Rect
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiObject2
import org.junit.Assert.assertTrue

/** Invoke only in the synthetic AppRoot fixture. Never taps the Call action. */
internal object DialerUiAssertions {
    fun verify(device: UiDevice, safeWindow: Rect) {
        val scrollable = device.findObject(By.desc("Dialpad keys")) != null
        fun reach(description: String): UiObject2 {
            if (scrollable) {
                UiScrollable(UiSelector().description("Dialpad keys").scrollable(true))
                    .setAsVerticalList().scrollIntoView(UiSelector().description(description))
            }
            return device.wait(Until.findObject(By.desc(description)), 4000)
                ?: throw AssertionError("Missing control $description after scrolling")
        }
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        val bounds = keys.map { digit ->
            val key = reach("Dial $digit")
            key.visibleBounds.also { rect ->
                assertTrue("Key $digit has nonzero bounds", rect.width() > 0 && rect.height() > 0)
                assertTrue("Key $digit remains square: $rect", kotlin.math.abs(rect.width() - rect.height()) <= 2)
                assertTrue("Key $digit inside safe window $safeWindow: $rect", safeWindow.contains(rect))
            }
        }
        // Scrolled keys occupy different viewport positions at different times.
        // Their full bounds are checked above; compare pairs only without scrolling.
        if (!scrollable) bounds.forEachIndexed { index, first ->
            bounds.drop(index + 1).forEach { second ->
                assertTrue("Dial keys must not overlap", !Rect.intersects(first, second))
            }
        }
        // Bring the entire final action row into view together. All three
        // targets must then be contained in the same safe viewport.
        reach("Call number")
        val call = device.findObject(By.desc("Call number"))
            ?: throw AssertionError("Call control not visible after scrolling")
        val callBounds = call.visibleBounds
        assertTrue("Whole call control above system navigation: $callBounds vs $safeWindow", safeWindow.contains(callBounds))
        assertTrue("Call control not clipped", kotlin.math.abs(callBounds.width() - callBounds.height()) <= 2)

        val bottomControls = listOf("Call number", "Hide dialpad", "Delete digit").map { description ->
            val node = device.findObject(By.desc(description)) ?: throw AssertionError("Action row missing $description")
            node.visibleBounds.also { assertTrue("Whole $description clears system bars", safeWindow.contains(it)) }
        }
        bottomControls.forEachIndexed { index, first -> bottomControls.drop(index + 1).forEach { second ->
            assertTrue("Bottom action targets do not overlap", !Rect.intersects(first, second))
        } }

        // Existing synthetic fixture's contact name is Synthetic Peer. Its phone
        // is unrelated, so 796 verifies name-letter matching rather than phones.
        listOf("7", "9", "6").forEach { digit -> reach("Dial $digit").click() }
        assertTrue("Typed digits are displayed", device.wait(Until.hasObject(By.text("796")), 5000))
        assertTrue("T9 keeps Synthetic Peer", device.wait(Until.hasObject(By.text("Synthetic Peer")), 5000))
        val deleteStart = android.os.SystemClock.elapsedRealtime()
        val snapshots = mutableListOf<String>()
        fun snapshot(label: String) {
            val fields = device.findObjects(By.clazz("android.widget.EditText"))
                .map { "text=${it.text} bounds=${it.visibleBounds}" }
            snapshots.add("$label elapsedMs=${android.os.SystemClock.elapsedRealtime() - deleteStart} fields=$fields")
        }
        snapshot("before-delete")
        reach("Delete digit").click()
        val waitStart = android.os.SystemClock.elapsedRealtime()
        // The cached hierarchy can retain old EditText text after its refreshed
        // node exposes the current value. Require the same exact number within
        // the same 5000ms, never a post-deadline snapshot or screenshot.
        val deleteDeadline = waitStart + 5000L
        var deleted = false
        while (android.os.SystemClock.elapsedRealtime() < deleteDeadline) {
            val cached79 = device.hasObject(By.text("79"))
            val fields = device.findObjects(By.clazz("android.widget.EditText"))
            val fresh = fields.map { it.text }
            val sampledAt = android.os.SystemClock.elapsedRealtime()
            snapshots.add("poll elapsedMs=${sampledAt - deleteStart} cached79=$cached79 fresh=$fresh")
            if (sampledAt <= deleteDeadline && fresh.size == 1 && fresh.single() == "79") {
                deleted = true
                break
            }
            val remaining = deleteDeadline - android.os.SystemClock.elapsedRealtime()
            if (remaining > 0) android.os.SystemClock.sleep(minOf(50L, remaining))
        }
        snapshots.add("wait79 result=$deleted durationMs=${android.os.SystemClock.elapsedRealtime() - waitStart}")
        snapshot("after-wait")
        if (!deleted) {
            val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            // Capture immediately, before throwing or any recovery gesture.
            device.takeScreenshot(File(dir, "dialer-delete-failure.png"))
            snapshots.add("screenshot elapsedMs=${android.os.SystemClock.elapsedRealtime() - deleteStart}")
            device.dumpWindowHierarchy(File(dir, "dialer-delete-failure.xml"))
            snapshots.add("xml elapsedMs=${android.os.SystemClock.elapsedRealtime() - deleteStart}")
            val nodes = device.findObjects(By.pkg("com.example.chat"))
                .map { "class=${it.className} text=${it.text} desc=${it.contentDescription} bounds=${it.visibleBounds}" }
            snapshots.add("nodes elapsedMs=${android.os.SystemClock.elapsedRealtime() - deleteStart}")
            File(dir, "dialer-delete-timing.txt").writeText(snapshots.joinToString("\n"))
            File(dir, "dialer-delete-nodes.txt").writeText(nodes.joinToString("\n"))
            android.util.Log.e("DialerProof", "Delete expected79 not found: ${nodes.joinToString(" | ")}")
        }
        val timingDir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(timingDir, "dialer-delete-timing.txt").writeText(snapshots.joinToString("\n"))
        assertTrue("Backspace removes one digit", deleted)
        // Invoke accessibility ACTION_CLICK, not another coordinate tap. This
        // separately verifies the production semantic action and single dispatch.
        fun deleteAccessibilityNode(): android.view.accessibility.AccessibilityNodeInfo {
            val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                ?: throw AssertionError("No accessibility root")
            val queue = java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            queue.add(root)
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (node.contentDescription?.toString() == "Delete digit") return node
                repeat(node.childCount) { index -> node.getChild(index)?.let(queue::add) }
            }
            throw AssertionError("Delete digit accessibility node missing")
        }
        val deleteNode = deleteAccessibilityNode()
        assertTrue("Delete supports semantic click", deleteNode.isClickable)
        assertTrue("Delete supports semantic long click", deleteNode.isLongClickable)
        assertTrue("Semantic delete dispatched", deleteNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        assertTrue("Semantic click deletes exactly one digit", device.wait(Until.hasObject(By.text("7")), 5000))
        // The one-tap clear (X) control was removed per the requested dialer
        // redesign. The meaningful behavior - the entered number is fully
        // clearable - stays covered here through the delete key.
        var clearGuard = 0
        while (device.hasObject(By.text("7")) && clearGuard++ < 10) {
            deleteNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        }
        assertTrue("Delete key clears the entered number", device.wait(Until.gone(By.text("7")), 5000))
        reach("Hide dialpad").click()
        assertTrue("Hide collapses keypad", device.wait(Until.gone(By.desc("Dial 2")), 5000))
        device.findObject(By.desc("Show dialpad")).click()
        assertTrue("Show restores keypad", device.wait(Until.hasObject(By.desc("Dial 2")), 5000))
    }
}
