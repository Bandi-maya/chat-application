package com.example.chat.location

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.ui.chats.location.GeoLibreProductionRouteProofActivity
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real MapViewerScreen; synthetic pin and Locate disabled. Never clicks or grants location. */
@RunWith(AndroidJUnit4::class)
class MapNativeControlsProofTest {
    private fun findWeb(v: View): WebView? {
        if (v is WebView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findWeb(v.getChildAt(i))?.let { return it }
        return null
    }
    /** Bounded read-only WebView controls diagnostic (candidate-28 wiring). Returns one JSON string; evidence only, no assertions. */
    private val CONTROLS_DIAG_JS = """
// Bounded one-shot diagnostic for the landscape horizontal-controls question (candidate 26+label/landscape trial).
// Run inside the location-map WebView (same js() bridge the tests use) in BOTH orientations, font 1.0 and 2.0.
// Read-only: no state changes, no navigation. Returns one JSON string.
(function () {
  var el = document.querySelector('.map-controls');
  if (!el) return JSON.stringify({ present: false });
  var cs = getComputedStyle(el);
  var r = el.getBoundingClientRect();
  var buttons = Array.prototype.map.call(el.querySelectorAll('button'), function (b) {
    var br = b.getBoundingClientRect();
    return { id: b.id || null, label: b.getAttribute('aria-label') || null,
      hidden: b.hidden, display: getComputedStyle(b).display,
      rect: [br.x, br.y, br.width, br.height] };
  });
  return JSON.stringify({
    present: true,
    hiddenAttr: el.hasAttribute('hidden'),
    hiddenProp: el.hidden,
    computedDisplay: cs.display,
    matchesLandscapeMedia: window.matchMedia('(min-width:600px) and (max-height:440px)').matches,
    viewport: [window.innerWidth, window.innerHeight],
    rect: [r.x, r.y, r.width, r.height],
    buttons: buttons,
    nativeLayersClassOn: !!document.querySelector('.native-layers'),
    ready: document.querySelector('#map') ? document.querySelector('#map').dataset.rendered || null : null
  });
})()
    """.trimIndent()
    private fun js(code: String): String? {
        val latch = CountDownLatch(1); var result: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val web = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { findWeb(it) }
            if (web == null) latch.countDown() else web.evaluateJavascript(code) { result = it; latch.countDown() }
        }
        assertTrue("JS inspection callback", latch.await(3, TimeUnit.SECONDS)); return result
    }
    @Test fun nativeChevronAndLocateFitPortraitLandscapeAndLargeText() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val dir = File(inst.targetContext.getExternalFilesDir(null), "screenshots/map-native").apply { mkdirs() }
        try {
            for (landscape in listOf(false, true)) for (font in listOf(1f, 2f)) {
                if (landscape) device.setOrientationLeft() else device.setOrientationNatural()
                val intent = Intent(inst.targetContext, GeoLibreProductionRouteProofActivity::class.java)
                    .putExtra("syntheticFontScale", font)
                ActivityScenario.launch<GeoLibreProductionRouteProofActivity>(intent).use { scenario ->
                    scenario.onActivity { it.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                    val orientationDeadline = SystemClock.elapsedRealtime() + 10_000
                    var orientationMatches = false
                    do {
                        scenario.onActivity { orientationMatches = (it.window.decorView.width > it.window.decorView.height) == landscape }
                        if (!orientationMatches) SystemClock.sleep(100)
                    } while (!orientationMatches && SystemClock.elapsedRealtime() < orientationDeadline)
                    val backFound = device.wait(Until.hasObject(By.pkg(inst.targetContext.packageName).clickable(true).desc("Back")), 10_000)
                    val locateFound = device.wait(Until.hasObject(By.pkg(inst.targetContext.packageName).clickable(true).desc("Locate current location")), 10_000)
                    inst.waitForIdleSync()
                    val name = "${if (landscape) "landscape" else "portrait"}-font$font"
                    val image = inst.uiAutomation.takeScreenshot()
                    if (image != null) { File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle() }
                    device.dumpWindowHierarchy(File(dir, "$name.xml"))
                    // Evidence first, even if an assertion below fails.
                    assertTrue("Back chevron accessible", backFound)
                    val layerFound = device.wait(Until.hasObject(By.pkg(inst.targetContext.packageName).clickable(true).desc("Map layers").enabled(true)), 45000)
                    assertTrue("Native layers icon accessible", layerFound)
                    val controls = listOf("Zoom in", "Zoom out", "Toggle 3D buildings", "Map layers")
                    val controlBounds = controls.map { label -> device.findObject(By.pkg(inst.targetContext.packageName).clickable(true).desc(label))?.visibleBounds ?: error("Missing $label") }
                    controlBounds.forEach { rect -> assertTrue("Native control48dp", rect.width() >= 47 * inst.targetContext.resources.displayMetrics.density && rect.height() >= 47 * inst.targetContext.resources.displayMetrics.density) }
                    controlBounds.zipWithNext().forEach { (first, next) -> assertTrue("Vertical distinct targets", first.bottom <= next.top) }
                    device.findObject(By.pkg(inst.targetContext.packageName).clickable(true).desc("Map layers")).click()
                    assertTrue("Shared native menu opens", device.wait(Until.hasObject(By.pkg(inst.targetContext.packageName).text("Fiord")), 5000))
                    inst.uiAutomation.takeScreenshot()?.let { popup -> File(dir, "$name-layers-open.png").outputStream().use { popup.compress(Bitmap.CompressFormat.PNG, 100, it) }; popup.recycle() }
                    device.findObject(By.pkg(inst.targetContext.packageName).text("Dark")).click()
                    assertTrue("Selection dismisses popup", device.wait(Until.gone(By.pkg(inst.targetContext.packageName).text("Fiord")), 5000))
                    val renderDeadline = SystemClock.elapsedRealtime() + 45_000
                    var loaded = false
                    do {
                        loaded = js("document.querySelector('#layers-menu [data-style=dark]')?.getAttribute('aria-checked') === 'true' && document.querySelector('#map')?.dataset.rendered === 'true'") == "true"
                        if (!loaded) SystemClock.sleep(200)
                    } while (!loaded && SystemClock.elapsedRealtime() < renderDeadline)
                    inst.uiAutomation.takeScreenshot()?.let { result -> File(dir, "$name-dark-render.png").outputStream().use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }; result.recycle() }
                    File(dir, "$name-dark-state.json").writeText(js("JSON.stringify({rendered:document.querySelector('#map')?.dataset.rendered,status:document.querySelector('#status')?.textContent,selected:document.querySelector('#layers-menu [aria-checked=true]')?.dataset.style})") ?: "null")
                    assertTrue("Actual native Dark row selects and reaches post-pin rendered gate", loaded)
                    device.findObject(By.pkg(inst.targetContext.packageName).clickable(true).desc("Map layers")).click()
                    assertTrue("Menu reopens after selection", device.wait(Until.hasObject(By.pkg(inst.targetContext.packageName).text("Fiord")), 5000))
                    device.pressBack()
                    assertTrue("Back dismisses popup, not map", device.wait(Until.hasObject(By.pkg(inst.targetContext.packageName).clickable(true).desc("Back")), 5000))
                    assertTrue("Locate icon accessible", locateFound)
                    var width = 0; var height = 0
                    scenario.onActivity { width = it.window.decorView.width; height = it.window.decorView.height }
                    assertEquals("Actual decor orientation", landscape, width > height)
                    val back = device.findObject(By.pkg(inst.targetContext.packageName).clickable(true).desc("Back")) ?: error("Back disappeared")
                    val locate = device.findObject(By.pkg(inst.targetContext.packageName).clickable(true).desc("Locate current location")) ?: error("Locate disappeared")
                    val b = back.visibleBounds; val l = locate.visibleBounds
                    val density = inst.targetContext.resources.displayMetrics.density
                    File(dir, "$name-target-metrics.json").writeText("""{"density":$density,"back":{"width":${b.width()},"height":${b.height()}},"locate":{"width":${l.width()},"height":${l.height()},"enabled":${locate.isEnabled}}}""")
                    File(dir, "$name-controls-diag.json").writeText(js(CONTROLS_DIAG_JS) ?: "null")
                    assertTrue("Back target48dp", b.width() >= 47 * density && b.height() >= 47 * density)
                    assertTrue("Locate target48dp", l.width() >= 47 * density && l.height() >= 47 * density)
                    assertTrue("No header/FAB overlap", !android.graphics.Rect.intersects(b, l))
                    assertFalse("Synthetic Locate stays disabled, no GPS", locate.isEnabled)
                    assertTrue("FAB right half", l.centerX() > device.displayWidth / 2)
                    var safeBottom = 0
                    var safeTop = 0
                    var safeLeft = 0
                    var safeRight = 0
                    scenario.onActivity { activity ->
                        val bars = androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        val decor = activity.window.decorView
                        val origin = IntArray(2); decor.getLocationOnScreen(origin)
                        safeBottom = origin[1] + decor.height - (bars?.bottom ?: 0)
                        safeTop = origin[1] + (bars?.top ?: 0)
                        safeLeft = origin[0] + (bars?.left ?: 0)
                        safeRight = origin[0] + decor.width - (bars?.right ?: 0)
                    }
                    assertTrue("Back clear of status bar", b.top >= safeTop)
                    assertTrue("Locate clear of navigation bar", l.bottom <= safeBottom)
                    assertTrue("Controls inside actual window width", b.left >= safeLeft && b.right <= safeRight && l.left >= safeLeft && l.right <= safeRight)
                }
            }
        } finally { device.setOrientationNatural(); device.unfreezeRotation() }
    }
}
