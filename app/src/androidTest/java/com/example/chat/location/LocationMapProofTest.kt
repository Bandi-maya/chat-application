package com.example.chat.location

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.ui.chats.location.GeoLibreSyntheticMapActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Debug synthetic fixture only. No real GPS/account/backend calls. Must run in reviewed map-only tree. */
@RunWith(AndroidJUnit4::class)
class LocationMapProofTest {
    private fun countMaps(view: View): Int = (if (view is WebView) 1 else 0) +
        if (view is ViewGroup) (0 until view.childCount).sumOf { countMaps(view.getChildAt(it)) } else 0
    private fun button(view: View, label: String): Button? {
        if (view is Button && view.text.toString() == label) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) button(view.getChildAt(i), label)?.let { return it }
        return null
    }
    @Test fun noRendererBeforeNoticeAndEnable() {
        ActivityScenario.launch(GeoLibreSyntheticMapActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertEquals(0, countMaps(root))
                button(root, "Use manual synthetic coordinates")!!.performClick()
                button(root, "Confirm to FAKE sink")!!.performClick()
                assertEquals(1, activity.fakeSends)
                button(root, "Confirm to FAKE sink")!!.performClick()
                assertEquals(1, activity.fakeSends)
                assertEquals(0, countMaps(root))
            }
        }
    }
    @Test fun enableThenCloseRemovesRendererFromHierarchy() {
        ActivityScenario.launch(GeoLibreSyntheticMapActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                button(root, "Load synthetic map")!!.performClick()
                assertEquals(1, countMaps(root))
                button(root, "Close map")!!.performClick()
                assertEquals(0, countMaps(root))
            }
        }
    }
    @Test fun mapReadyWithinBoundAndCaptureSyntheticPixels() {
        ActivityScenario.launch(GeoLibreSyntheticMapActivity::class.java).use { scenario ->
            scenario.onActivity { button(it.window.decorView, "Load synthetic map")!!.performClick() }
            val deadline = SystemClock.elapsedRealtime() + 30_000
            var ready = false
            while (!ready && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity { ready = it.mapReady }
                if (!ready) SystemClock.sleep(100)
            }
            assertTrue("Synthetic map did not become ready within 30 seconds", ready)
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            val path = File(instrumentation.targetContext.getExternalFilesDir(null), "geolibre-synthetic-ready.png")
            path.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(path.length() > 0)
            bitmap.recycle()
        }
    }
    @Test fun interceptBlocksTilesAndManualFakeSinkStillWorks() {
        ActivityScenario.launch(GeoLibreSyntheticMapActivity::class.java).use { scenario ->
            scenario.onActivity {
                button(it.window.decorView, "Block tile requests before load")!!.performClick()
                button(it.window.decorView, "Load synthetic map")!!.performClick()
            }
            val deadline = SystemClock.elapsedRealtime() + 10_000
            var blocked = 0
            while (blocked == 0 && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity { blocked = it.blockedTileRequests }
                if (blocked == 0) SystemClock.sleep(100)
            }
            scenario.onActivity {
                assertTrue("No blocked tile request was observed", it.blockedTileRequests > 0)
                assertEquals(0, it.allowedTileRequests)
                button(it.window.decorView, "Use manual synthetic coordinates")!!.performClick()
                button(it.window.decorView, "Confirm to FAKE sink")!!.performClick()
                button(it.window.decorView, "Confirm to FAKE sink")!!.performClick()
                assertEquals(1, it.fakeSends)
            }
        }
    }

}
