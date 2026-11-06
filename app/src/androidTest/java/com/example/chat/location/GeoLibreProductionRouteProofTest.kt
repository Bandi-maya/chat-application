package com.example.chat.location

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.ui.chats.location.GeoLibreProductionRouteProofActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Debug-only actual-route evidence. Geometry/load checks do not replace pixel inspection. */
@RunWith(AndroidJUnit4::class)
class GeoLibreProductionRouteProofTest {
    private fun findWeb(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }
        return null
    }
    @Test fun openingProductionViewerRendersWithoutSecondLoadAction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        ActivityScenario.launch(GeoLibreProductionRouteProofActivity::class.java).use { scenario ->
            var ready = false
            var positiveMapGeometry = false
            val records = StringBuilder()
            try {
                val deadline = SystemClock.elapsedRealtime() + 45_000
                while (SystemClock.elapsedRealtime() < deadline) {
                    val latch = CountDownLatch(1)
                    var result: String? = null
                    scenario.onActivity { activity ->
                        ready = activity.mapReady
                        // API 29+ focused emulator diagnostic: includes Compose Dialog's separate window.
                        val web = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { findWeb(it) }
                        if (web == null) { latch.countDown() } else web.evaluateJavascript("""
                            (() => {
                              window.__mapDiagnosticErrors = window.__mapDiagnosticErrors || [];
                              if (!window.__mapDiagnosticInstalled) {
                                window.__mapDiagnosticInstalled = true;
                                addEventListener('error', e => window.__mapDiagnosticErrors.push(String(e.message)));
                                addEventListener('unhandledrejection', e => window.__mapDiagnosticErrors.push(String(e.reason)));
                              }
                              const rect = e => { if (!e) return null; const r=e.getBoundingClientRect(); return {x:r.x,y:r.y,width:r.width,height:r.height}; };
                              const m=document.querySelector('#map'), c=m?.querySelector('canvas');
                              return JSON.stringify({readyState:document.readyState,ua:navigator.userAgent,viewport:{width:innerWidth,height:innerHeight},body:rect(document.body),map:rect(m),canvas:rect(c),canvasWidth:c?.width,canvasHeight:c?.height,canvasCount:document.querySelectorAll('canvas').length,rendered:m?.dataset.rendered,api:typeof window.ChatyGeoLibre,status:document.querySelector('#status')?.textContent,errors:window.__mapDiagnosticErrors});
                            })()
                        """.trimIndent()) { value -> result = value; latch.countDown() }
                    }
                    latch.await(3, TimeUnit.SECONDS)
                    result?.let { raw ->
                        records.append(SystemClock.elapsedRealtime()).append(' ').append(raw).append('\n')
                        runCatching {
                            val decoded = org.json.JSONTokener(raw).nextValue() as? String ?: return@runCatching
                            val json = JSONObject(decoded)
                            val map = json.optJSONObject("map")
                            val canvas = json.optJSONObject("canvas")
                            positiveMapGeometry = map != null && canvas != null && map.optDouble("width") > 100 && map.optDouble("height") > 100 && canvas.optDouble("width") > 100 && canvas.optDouble("height") > 100 && json.optString("rendered") == "true"
                        }
                    }
                    if (ready && positiveMapGeometry) break
                    SystemClock.sleep(250)
                }
            } finally {
                File(directory, "geolibre-production-route-diagnostics.jsonl").writeText(records.toString())
                val image = instrumentation.uiAutomation.takeScreenshot()
                if (image != null) {
                    File(directory, "geolibre-production-route.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    image.recycle()
                }
            }
            assertTrue("Production route did not report map load; see failure-safe diagnostics and screenshot", ready)
            assertTrue("Map/canvas has missing or zero geometry; see diagnostics and screenshot", positiveMapGeometry)
            // Human inspection must confirm actual tiles; these assertions alone are not map-render acceptance.
        }
    }
}
