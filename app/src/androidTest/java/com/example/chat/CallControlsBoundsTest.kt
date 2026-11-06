package com.example.chat

import android.content.Intent
import android.graphics.Rect
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CallControlsBoundsTest {
    @Test fun incomingAndOngoingTemplatesClearSystemBarsIncludingLargeTextLandscape() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        data class Case(val template: String, val incoming: Boolean, val landscape: Boolean = false, val font: Float = 1f)
        val cases = listOf("chatyClassic", "sheet", "card", "edge").flatMap { template ->
            listOf(Case(template, true), Case(template, false))
        } + listOf("chatyClassic", "sheet").flatMap { template ->
            listOf(Case(template, true, true, 2f), Case(template, false, true, 2f))
        }
        val failures = mutableListOf<String>()
        cases.forEachIndexed { index, case ->
            val tag = "${case.template}-${if(case.incoming) "incoming" else "ongoing-ringing"}-${if(case.landscape) "landscape" else "portrait"}-${case.font}"
            val intent = Intent(instrumentation.targetContext, CallControlsFixtureActivity::class.java)
                .putExtra("geometry", true).putExtra("video", true).putExtra("incoming", case.incoming)
                .putExtra("template", case.template).putExtra("landscape", case.landscape)
                .putExtra("fontScale", case.font).putExtra("dark", index % 2 == 1)
            try {
            ActivityScenario.launch<CallControlsFixtureActivity>(intent).use { scenario ->
                val marker = if (case.incoming) "Answer" else "End call"
                assertTrue("Production call screen opened $tag", device.wait(Until.hasObject(By.desc(marker)), 5000) ||
                    device.hasObject(By.text(marker)))
                instrumentation.waitForIdleSync()
                var safe = Rect()
                var coordinates = ""
                scenario.onActivity { activity ->
                    val decor = activity.window.decorView
                    val bars = decor.rootWindowInsets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                    val origin = IntArray(2)
                    decor.getLocationOnScreen(origin)
                    // UiAutomator visibleBounds are SCREEN coordinates. The Activity
                    // decor can have a nonzero origin in landscape compatibility/cutout
                    // letterboxing. Root insets are relative to that decor, not display.
                    safe = Rect(origin[0] + bars.left, origin[1] + bars.top,
                        origin[0] + decor.width - bars.right, origin[1] + decor.height - bars.bottom)
                    coordinates = "origin=${origin.toList()} decor=${decor.width}x${decor.height} bars=$bars display=${device.displayWidth}x${device.displayHeight}"
                }
                val dir = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
                // Capture before assertions; never crop or normalize away clipping.
                device.takeScreenshot(java.io.File(dir, "call-geometry-$tag.png"))
                device.dumpWindowHierarchy(java.io.File(dir, "call-geometry-$tag.xml"))
                val labels = if (case.incoming) listOf("Answer", "Decline") else listOf("Mute", "Camera off", "Switch camera", "Share screen", "Speaker", "Earpiece", "More call controls", "End call")
                val targets = labels.mapNotNull { label ->
                    var node = device.findObject(By.desc(label)) ?: device.findObject(By.text(label)) ?: return@mapNotNull null
                    while (!node.isClickable && node.parent != null) node = node.parent
                    label to node.visibleBounds
                }
                java.io.File(dir, "call-geometry-$tag.txt").writeText("$coordinates safe=$safe targets=$targets sessionState=${if(case.incoming) "incoming" else "ringing"}")
                assertTrue("Required call actions exist $tag", targets.any { it.first == marker })
                if (case.incoming) assertEquals("Both incoming actions exist $tag", setOf("Answer", "Decline"), targets.map { it.first }.toSet())
                val requiredOngoing = setOf("Mute", "Camera off", "Switch camera", "Share screen", "Audio route")
                fun normalized(label: String) = if (label == "Speaker" || label == "Earpiece") "Audio route" else label
                val visibleActions = targets.map { normalized(it.first) }.filter { it in requiredOngoing }.toSet()
                var menuTargets = emptyList<Pair<String, Rect>>()
                if (!case.incoming) {
                    val more = device.findObject(By.desc("More call controls"))
                    if (more != null) {
                        more.click()
                        device.wait(Until.hasObject(By.text("Earpiece")), 1500)
                        menuTargets = listOf("Mute", "Camera off", "Switch camera", "Share screen", "Speaker", "Earpiece").mapNotNull { label ->
                            var node = device.findObject(By.text(label)) ?: return@mapNotNull null
                            while (!node.isClickable && node.parent != null) node = node.parent
                            label to node.visibleBounds
                        }
                        device.takeScreenshot(java.io.File(dir, "call-geometry-$tag-overflow.png"))
                        device.dumpWindowHierarchy(java.io.File(dir, "call-geometry-$tag-overflow.xml"))
                    }
                    val discovered = visibleActions + menuTargets.map { normalized(it.first) }
                    java.io.File(dir, "call-geometry-$tag.txt").appendText(" required=$requiredOngoing visible=$visibleActions menu=$menuTargets")
                    assertEquals("Every ongoing action exists directly or in overflow $tag", requiredOngoing, discovered)
                    menuTargets.forEach { (label, bounds) ->
                        assertTrue("$tag overflow $label clears system bars $bounds/$safe", safe.contains(bounds))
                        val minimumPx = (48 * instrumentation.targetContext.resources.displayMetrics.density).toInt()
                        assertTrue("$tag overflow $label keeps48dp height $bounds", bounds.height() >= minimumPx - 1)
                    }
                }
                targets.forEach { (label, bounds) ->
                    val minimumPx = (48 * instrumentation.targetContext.resources.displayMetrics.density).toInt()
                    assertTrue("$tag $label retains at least48dp target $bounds", bounds.width() >= minimumPx - 1 && bounds.height() >= minimumPx - 1)
                    assertTrue("$tag $label fully clears system bars $bounds/$safe", safe.contains(bounds))
                }
                targets.forEachIndexed { i, first -> targets.drop(i + 1).forEach { second ->
                    assertFalse("$tag actions overlap ${first.first}/${second.first}", Rect.intersects(first.second, second.second))
                } }
            }
            } catch (failure: AssertionError) {
                failures += "$tag: ${failure.message}"
            }
        }
        assertTrue("All geometry cases completed; failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test fun syntheticVoiceAndVideoControlsFitAndMoreInvokesNoop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        for ((video, width) in listOf(false to 320, true to 320, true to 480, true to 100)) {
            val intent = Intent(instrumentation.targetContext, CallControlsFixtureActivity::class.java)
                .putExtra("video", video).putExtra("widthDp", width)
            ActivityScenario.launch<CallControlsFixtureActivity>(intent).use { scenario ->
                var safe = Rect()
                scenario.onActivity { activity ->
                    val bars = activity.window.decorView.rootWindowInsets.getInsets(
                        android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                    safe = Rect(bars.left, bars.top, device.displayWidth - bars.right, device.displayHeight - bars.bottom)
                }
                assertTrue(device.wait(Until.hasObject(By.desc("End call")), 4000))
                val descriptions = listOf("Mute", "Camera off", "Switch camera", "Share screen", "Earpiece", "More call controls", "End call")
                val controls = descriptions.mapNotNull { device.findObject(By.desc(it)) }.map { it.visibleBounds }
                controls.forEach { assertTrue("Full control in safe window $it/$safe", safe.contains(it)) }
                controls.forEachIndexed { index, a -> controls.drop(index + 1).forEach { b -> assertFalse(Rect.intersects(a,b)) } }
                val more = device.findObject(By.desc("More call controls"))
                if (video && width <= 320) {
                    assertNotNull("Compact video exposes More", more)
                    more!!.click()
                    assertTrue("Actual overflow menu opened", device.wait(Until.hasObject(By.text("Earpiece")), 2000))
                    device.findObject(By.text("Earpiece")).click()
                    assertTrue("Noop audio callback ran", device.wait(Until.hasObject(By.text("Fixture callback:audio")), 2000))
                } else if (!video) assertNull("Voice shows all controls", more)
            }
        }
    }
}
