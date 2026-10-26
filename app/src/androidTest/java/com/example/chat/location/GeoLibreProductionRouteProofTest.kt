package com.example.chat.location

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.ui.chats.location.GeoLibreProductionRouteProofActivity
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual MapViewerScreen, not standalone renderer. Captures pixels for separate human inspection. */
@RunWith(AndroidJUnit4::class)
class GeoLibreProductionRouteProofTest {
    @Test fun openingProductionViewerRendersWithoutSecondLoadAction() {
        ActivityScenario.launch(GeoLibreProductionRouteProofActivity::class.java).use { scenario ->
            val deadline = SystemClock.elapsedRealtime() + 45_000
            var ready = false
            while (!ready && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity { ready = it.mapReady }
                if (!ready) SystemClock.sleep(100)
            }
            assertTrue("Production route did not render map automatically within 45 seconds", ready)
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            // readiness proves assets/map load, NOT correct visual placement; inspect captured screenshot.
            val image = instrumentation.uiAutomation.takeScreenshot()
            assertNotNull("Screenshot capture failed", image)
            val file = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots/geolibre-production-route.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
            assertTrue(file.length() > 0)
        }
    }
}
