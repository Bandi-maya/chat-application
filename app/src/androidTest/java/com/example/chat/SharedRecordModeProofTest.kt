package com.example.chat

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.core.templates.*
import com.example.chat.ui.chats.detail.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual composer pointer stream under same-window sibling overlay, NO real camera/mic/backend. */
@RunWith(AndroidJUnit4::class)
class SharedRecordModeProofTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val d = UiDevice.getInstance(ins)
    private fun pointer(action: Int, x: Float, y: Float, down: Long) {
        val e = MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0)
        try { ins.sendPointerSync(e) } finally { e.recycle() }
    }
    private fun runCase(cancel: Boolean) {
        val starts=AtomicInteger();val ends=AtomicInteger();val cancels=AtomicInteger();val audio=AtomicInteger()
        val id=AtomicReference<Long>();val endId=AtomicReference<Long>()
        ActivityScenario.launch(MediaFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { a -> a.setContent { MaterialTheme {
                var overlay by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().align(androidx.compose.ui.Alignment.BottomCenter).navigationBarsPadding()) {
                        ChatEntryBar(STOCK_ENTRY_STYLE,ComposerColors.resolve(ThemePresets.whatsappIosLight),
                            ComposerTemplate(ComposerActionPlacement.integrated),ThemePresets.whatsappIosLight,"",{},
                            onVoiceHoldStart={audio.incrementAndGet()},
                            onVideoHoldStart={ id.set(it);starts.incrementAndGet();overlay=true },
                            onVideoHoldEnd={ endId.set(it);ends.incrementAndGet();overlay=false },
                            onVideoHoldCancel={ endId.set(it);cancels.incrementAndGet();overlay=false })
                    }
                    if(overlay) Box(Modifier.fillMaxSize().background(Color(0xAA224466))) { Text("Synthetic video overlay") }
                }
            } } }
            assertTrue(d.wait(Until.hasObject(By.descStartsWith("Microphone mode.")),5000))
            d.findObject(By.descStartsWith("Microphone mode.")).click()
            assertTrue(d.wait(Until.hasObject(By.descStartsWith("Video mode.")),5000))
            assertEquals("Tap only switches",0,starts.get());assertEquals(0,audio.get())
            val r=d.findObject(By.descStartsWith("Video mode.")).visibleBounds
            val x=r.exactCenterX();val y=r.exactCenterY();val down=SystemClock.uptimeMillis()
            pointer(MotionEvent.ACTION_DOWN,x,y,down)
            try {
                assertTrue(d.wait(Until.hasObject(By.text("Synthetic video overlay")),5000))
                assertEquals(1,starts.get())
            } finally { pointer(if(cancel)MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,x,y,down) }
            ins.waitForIdleSync()
            assertEquals(if(cancel)0 else 1,ends.get());assertEquals(if(cancel)1 else 0,cancels.get())
            assertEquals(id.get(),endId.get());assertEquals(0,audio.get())
        }
    }
    @Test fun releaseStillReachesOriginalComposerAfterSiblingOverlayMounted() { runCase(false) }
    @Test fun systemCancelStillReachesOriginalComposerAfterSiblingOverlayMounted() { runCase(true) }
}
