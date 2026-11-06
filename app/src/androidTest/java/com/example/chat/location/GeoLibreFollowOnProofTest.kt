package com.example.chat.location

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import android.widget.Button
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.ui.chats.location.GeoLibreProductionRouteProofActivity
import com.example.chat.ui.chats.location.GeoLibreSyntheticMapActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic pin only, Locate disabled. No GPS, account, send, or network-privacy changes. API29+ emulator. */
@RunWith(AndroidJUnit4::class)
class GeoLibreFollowOnProofTest {
    private fun findWeb(v: View): WebView? {
        if (v is WebView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findWeb(v.getChildAt(i))?.let { return it }
        return null
    }
    private fun web() = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { findWeb(it) }
    private fun js(code: String): String? {
        val latch = CountDownLatch(1)
        var result: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val w = web()
            if (w == null) latch.countDown() else w.evaluateJavascript(code) { result = it; latch.countDown() }
        }
        assertTrue("WebView JS callback timeout", latch.await(4, TimeUnit.SECONDS))
        return result
    }
    private fun await(label: String, code: String, timeout: Long = 45_000) {
        val end = SystemClock.elapsedRealtime() + timeout
        do { if (js(code) == "true") return; SystemClock.sleep(200) } while (SystemClock.elapsedRealtime() < end)
        fail("$label timed out; evidence=${js(evidence)}")
    }
    private val rendered = "document.querySelector('#map')?.dataset.rendered === 'true'"
    private val evidence = """JSON.stringify({viewport:[innerWidth,innerHeight],map:document.querySelector('#map')?.getBoundingClientRect().toJSON(),footer:document.querySelector('footer')?.getBoundingClientRect().toJSON(),rootHeight:document.documentElement.clientHeight,visualHeight:visualViewport?.height,layer:document.querySelector('#layers-menu [aria-checked=true]')?.dataset.style,view:document.querySelector('#view-mode')?.textContent,status:document.querySelector('#status')?.textContent,rendered:document.querySelector('#map')?.dataset.rendered})"""
    private fun capture(name: String) {
        val i = InstrumentationRegistry.getInstrumentation()
        val dir = File(i.targetContext.getExternalFilesDir(null), "screenshots/map-followon").apply { mkdirs() }
        File(dir, "$name.json").writeText(js(evidence) ?: "null")
        i.uiAutomation.takeScreenshot()?.let { image ->
            File(dir, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        }
    }
    @Test fun actualRouteLayersAndBuildingsRemainRenderable() {
        ActivityScenario.launch(GeoLibreProductionRouteProofActivity::class.java).use {
            try {
                await("Initial post-pin render", rendered)
                for (layer in listOf("positron", "dark", "bright", "fiord", "liberty")) {
                    js("document.querySelector('#layers-trigger').click();document.querySelector('[data-style=$layer]').click();true")
                    await("Layer $layer render", rendered)
                    assertEquals("\"$layer\"", js("document.querySelector('#layers-menu [aria-checked=true]').dataset.style"))
                    capture("layer-$layer")
                }
                js("document.querySelector('#view-mode').click();true")
                await("3D button state", "document.querySelector('#view-mode').textContent === '2D'")
                SystemClock.sleep(1000)
                capture("buildings-3d") // Actual extrusion is a pixel acceptance, not implied by label.
                js("document.querySelector('#layers-trigger').click();document.querySelector('[data-style=positron]').click();true")
                await("Light render after 3D", rendered)
                assertEquals("\"3D\"", js("document.querySelector('#view-mode').textContent"))
                capture("light-returns-2d")
            } finally { capture("layers-final") }
        }
    }
    private fun button(v: View, text: String): Button? {
        if (v is Button && v.text.toString() == text) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) button(v.getChildAt(i), text)?.let { return it }
        return null
    }
    @Test fun blockedStyleShowsErrorAndRestoredNetworkRecovers() {
        ActivityScenario.launch(GeoLibreSyntheticMapActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { button(it.window.decorView, "Load synthetic map")!!.performClick() }
                await("Initial synthetic render", rendered)
                scenario.onActivity { it.setTileRequestsBlockedForTest(true) }
                js("document.querySelector('#layers-trigger').click();document.querySelector('[data-style=dark]').click();true")
                await("Blocked style error", "document.querySelector('#status').textContent === 'Map unavailable. Check your internet connection.'")
                SystemClock.sleep(1000)
                assertEquals("false", js(rendered))
                capture("blocked-style")
                js("document.querySelector('#view-mode').click();true")
                assertEquals("\"Map unavailable. Check your internet connection.\"", js("document.querySelector('#status').textContent"))
                scenario.onActivity { it.setTileRequestsBlockedForTest(false) }
                js("document.querySelector('#layers-trigger').click();document.querySelector('[data-style=liberty]').click();true")
                await("Recovered status", "document.querySelector('#status').textContent !== 'Map unavailable. Check your internet connection.'")
                await("Recovered synthetic render", rendered)
                capture("recovered-style")
            } finally { capture("network-final") }
        }
    }
    @Test fun actualRoutePauseDisposesAndResumeRecreatesRenderer() {
        ActivityScenario.launch(GeoLibreProductionRouteProofActivity::class.java).use { scenario ->
            try {
                await("Initial post-pin render", rendered)
                var initial: WebView? = null
                InstrumentationRegistry.getInstrumentation().runOnMainSync { initial = web() }
                scenario.moveToState(Lifecycle.State.CREATED)
                var remaining = true
                val deadline = SystemClock.elapsedRealtime() + 5_000
                do {
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                    InstrumentationRegistry.getInstrumentation().runOnMainSync { remaining = web() != null }
                    if (remaining) SystemClock.sleep(100)
                } while (remaining && SystemClock.elapsedRealtime() < deadline)
                assertFalse("Renderer still in hierarchy after pause/stop", remaining)
                scenario.moveToState(Lifecycle.State.RESUMED)
                await("Resumed post-pin render", rendered)
                InstrumentationRegistry.getInstrumentation().runOnMainSync { assertNotSame("Renderer must be recreated, not kept alive", initial, web()) }
                capture("resumed-renderer")
            } finally { if (scenario.state == Lifecycle.State.CREATED) scenario.moveToState(Lifecycle.State.RESUMED); capture("lifecycle-final") }
        }
    }
}
