package com.example.chat

import android.graphics.Rect
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue

/** Invoke only in the synthetic AppRoot fixture. Never taps the Call action. */
internal object DialerUiAssertions {
    fun verify(device: UiDevice, safeWindow: Rect) {
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        val bounds = keys.map { digit ->
            val key = device.wait(Until.findObject(By.desc("Dial $digit")), 5000)
                ?: throw AssertionError("Missing dial key $digit")
            key.visibleBounds.also { rect ->
                assertTrue("Key $digit has nonzero bounds", rect.width() > 0 && rect.height() > 0)
                assertTrue("Key $digit remains square: $rect", kotlin.math.abs(rect.width() - rect.height()) <= 2)
                assertTrue("Key $digit inside safe window $safeWindow: $rect", safeWindow.contains(rect))
            }
        }
        bounds.forEachIndexed { index, first ->
            bounds.drop(index + 1).forEach { second ->
                assertTrue("Dial keys must not overlap", !Rect.intersects(first, second))
            }
        }
        val call = device.findObject(By.desc("Call number"))
            ?: throw AssertionError("Missing full Call number control")
        val callBounds = call.visibleBounds
        assertTrue("Whole call control above system navigation: $callBounds vs $safeWindow", safeWindow.contains(callBounds))
        assertTrue("Call control not clipped", kotlin.math.abs(callBounds.width() - callBounds.height()) <= 2)

        // Existing synthetic fixture's contact name is Synthetic Peer. Its phone
        // is unrelated, so 796 verifies name-letter matching rather than phones.
        listOf("7", "9", "6").forEach { digit -> device.findObject(By.desc("Dial $digit")).click() }
        assertTrue("Typed digits are displayed", device.wait(Until.hasObject(By.text("796")), 5000))
        assertTrue("T9 keeps Synthetic Peer", device.wait(Until.hasObject(By.text("Synthetic Peer")), 5000))
        device.findObject(By.desc("Delete digit")).click()
        assertTrue("Backspace removes one digit", device.wait(Until.hasObject(By.text("79")), 5000))
        device.findObject(By.desc("Clear number")).click()
        assertTrue("Clear number removes query", device.wait(Until.gone(By.desc("Clear number")), 5000))
        device.findObject(By.desc("Hide dialpad")).click()
        assertTrue("Hide collapses keypad", device.wait(Until.gone(By.desc("Dial 2")), 5000))
        device.findObject(By.desc("Show dialpad")).click()
        assertTrue("Show restores keypad", device.wait(Until.hasObject(By.desc("Dial 2")), 5000))
    }
}
