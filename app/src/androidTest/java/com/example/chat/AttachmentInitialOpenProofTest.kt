package com.example.chat

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.Direction
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.chats.detail.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Material modal + production gallery layout; unique synthetic local PNGs only. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class AttachmentInitialOpenProofTest {
    @Test fun firstOpenKeepsVoiceAndTypesVisibleWhileOnlyPhotosScroll() {
        val ins = InstrumentationRegistry.getInstrumentation(); val c = ins.targetContext
        val d = UiDevice.getInstance(ins)
        val files = (0..44).map { index -> File(c.cacheDir, "gallery-initial-$index.png").also { file ->
            val b = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
            b.eraseColor(android.graphics.Color.rgb(20+index, 100, 180))
            file.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
        } }
        try {
            val selectedAction = java.util.concurrent.atomic.AtomicReference<AttachmentAction?>()
            for (font in listOf(1f, 2f)) ActivityScenario.launch(MediaFixtureActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, font)) {
                        MaterialTheme {
                            val state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                            ModalBottomSheet(sheetState = state,
                                onDismissRequest = {}, dragHandle = null) {
                                AttachmentGalleryViewport(state) {
                                AttachmentGallerySheet(ThemePresets.whatsappIosLight, onAction = { selectedAction.set(it) }, onCamera = {},
                                    onReviewImages = {}, heightFraction = 1f, fixtureItems = files.mapIndexed { i, f -> AttachmentGalleryItem(android.net.Uri.fromFile(f), "Initial $i") })
                                }
                            }
                        }
                    }
                } }
                assertTrue(d.wait(Until.hasObject(By.text("Record voice note")), 10000))
                val voice = d.findObject(By.text("Record voice note")).visibleBounds
                val types = d.findObject(By.desc("Attachment type options")).visibleBounds
                val panel = d.findObject(By.desc("Attachment gallery sheet")).visibleBounds
                assertTrue("Production opens at partial anchor",panel.top > d.displayHeight/4)
                assertTrue("Voice initial nonzero", voice.width()>0 && voice.height()>0)
                assertTrue("Types initial nonzero", types.width()>0 && types.height()>0)
                assertTrue("Voice above dock", voice.bottom<=types.bottom)
                assertTrue("Dock inside viewport", types.bottom<=d.displayHeight && types.top>=0)
                val out = File(c.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
                fun capture(label: String) {
                    d.dumpWindowHierarchy(File(out,"gallery-$font-$label.xml"))
                    ins.uiAutomation.takeScreenshot()?.let { b -> File(out,"gallery-$font-$label.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }; b.recycle() }
                }
                capture("initial")
                val density = c.resources.displayMetrics.density
                var verifiedScroll = false
                fun reveal(label: String): androidx.test.uiautomator.UiObject2 {
                    val selector = By.desc("Attachment option $label").clickable(true)
                    val requiredWidth = (if (label == "Chaty Direct") 104f * font.coerceAtLeast(1f) else 76f) * density
                    fun usable(node: androidx.test.uiautomator.UiObject2?): Boolean {
                        if (node == null) return false
                        val b = node.visibleBounds
                        val dock = d.findObject(By.desc("Attachment type options")).visibleBounds
                        return b.width() + 2 >= requiredWidth && b.height() >= 48f * density &&
                            b.left >= dock.left && b.right <= dock.right && b.top >= dock.top && b.bottom <= dock.bottom
                    }
                    var node = d.findObject(selector)
                    var tries = 0
                    while (!usable(node) && tries++ < 10) {
                        val dock = d.findObject(By.desc("Attachment type options")).visibleBounds
                        val anchorSelector = By.desc("Attachment option Contact").clickable(true)
                        val anchorBefore = d.findObject(anchorSelector)?.visibleBounds
                        val targetBefore = d.findObject(selector)?.visibleBounds
                        // Finger moves LEFT to reveal content to the RIGHT, inside dock only.
                        d.swipe(dock.right - dock.width()/8, dock.centerY(), dock.left + dock.width()/8, dock.centerY(), 35)
                        d.waitForIdle()
                        val anchorAfter = d.findObject(anchorSelector)?.visibleBounds
                        val targetAfter = d.findObject(selector)?.visibleBounds
                        if ((anchorBefore != null && anchorAfter != null && anchorAfter.centerX() < anchorBefore.centerX()) ||
                            (targetBefore != null && targetAfter != null && targetAfter.centerX() < targetBefore.centerX())) verifiedScroll = true
                        node = d.findObject(selector)
                    }
                    if (!usable(node)) { capture("dock-target-clipped-$label"); fail("Useful full dock target $label not reachable") }
                    return node!!
                }
                for (label in listOf("Gallery", "File", "Location", "Poll", "Contact", "Chaty Direct")) reveal(label)
                val direct = reveal("Chaty Direct")
                assertTrue("Leftward finger swipe must move content left to reveal Direct", verifiedScroll)
                capture("direct-visible-in-existing-dock")
                selectedAction.set(null)
                direct.click()
                ins.waitForIdleSync()
                assertEquals("Real dock invokes Direct action", AttachmentAction.chatyDirect, selectedAction.get())
                val grid = d.findObject(By.desc("Recent attachment photos")).visibleBounds
                d.swipe(grid.centerX(),grid.bottom-12,grid.centerX(),grid.top+12,30)
                assertEquals("Dock stays pinned during photo scroll",types,d.findObject(By.desc("Attachment type options")).visibleBounds)
                assertEquals("Voice stays pinned during photo scroll",voice,d.findObject(By.text("Record voice note")).visibleBounds)
                capture("photos-scrolled")
                // Expand and collapse the REAL modal, not a fully expanded fixture.
                val beforeExpand = d.findObject(By.desc("Attachment gallery sheet")).visibleBounds
                d.swipe(d.displayWidth/2, beforeExpand.top+12, d.displayWidth/2, 100, 40)
                d.waitForIdle()
                val expandedPanel = d.findObject(By.desc("Attachment gallery sheet")).visibleBounds
                assertTrue("Drag expands actual modal",expandedPanel.top < beforeExpand.top)
                val expandedTypes = d.findObject(By.desc("Attachment type options")).visibleBounds
                assertTrue("Expanded dock wholly visible", expandedTypes.height()>0 && expandedTypes.bottom<=d.displayHeight)
                capture("expanded")
                d.swipe(d.displayWidth/2, expandedPanel.top+12, d.displayWidth/2, d.displayHeight/2, 40)
                d.waitForIdle()
                val collapsedTypes = d.findObject(By.desc("Attachment type options")).visibleBounds
                assertTrue("Collapsed dock wholly visible",collapsedTypes.height()>0 && collapsedTypes.bottom<=d.displayHeight)
                capture("collapsed-again")
            }
        } finally { files.forEach { it.delete() } }
    }
}
