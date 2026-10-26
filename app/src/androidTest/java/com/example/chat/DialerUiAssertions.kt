package com.example.chat

import android.graphics.Rect
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
        reach("Delete digit").click()
        assertTrue("Backspace removes one digit", device.wait(Until.hasObject(By.text("79")), 5000))
        device.findObject(By.desc("Clear number")).click()
        assertTrue("Clear number removes query", device.wait(Until.gone(By.desc("Clear number")), 5000))
        reach("Hide dialpad").click()
        assertTrue("Hide collapses keypad", device.wait(Until.gone(By.desc("Dial 2")), 5000))
        device.findObject(By.desc("Show dialpad")).click()
        assertTrue("Show restores keypad", device.wait(Until.hasObject(By.desc("Dial 2")), 5000))
    }
}
