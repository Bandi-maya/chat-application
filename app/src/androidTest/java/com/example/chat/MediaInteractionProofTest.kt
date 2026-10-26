package com.example.chat

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.chat.domain.models.*
import com.example.chat.ui.chats.detail.*
import java.io.File
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic local files and counters only; never starts the app backend or performs an upload. */
@RunWith(AndroidJUnit4::class)
class MediaInteractionProofTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(inst)
    private fun photo(): File = File(inst.targetContext.cacheDir, "synthetic-media-proof.png").also { file ->
        val bitmap = Bitmap.createBitmap(240, 360, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(AndroidColor.rgb(40, 140, 200))
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawRect(30f, 40f, 190f, 220f, android.graphics.Paint().apply { color = AndroidColor.YELLOW })
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun capture(name: String) {
        device.waitForIdle(); Thread.sleep(500)
        val image = inst.uiAutomation.takeScreenshot() ?: error("No screenshot")
        val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    private fun waitText(text: String) = assertTrue("Visible $text", device.wait(Until.hasObject(By.text(text)), 10000))
    private fun clickText(text: String) { waitText(text); device.findObject(By.text(text)).click() }
    private fun clickDesc(text: String) {
        assertTrue("Visible $text", device.wait(Until.hasObject(By.desc(text)), 10000))
        device.findObject(By.desc(text)).click()
    }

    @Test fun providedVideoModelWaitsForPlayableUriWithoutCrashing() {
        // Regression: providedModel bypassed the spinner, then video used signedUrl!!
        // on the first composition before its resolver effect ran.
        val placeholder = File(inst.targetContext.cacheDir, "synthetic-pending-video.mp4").apply { writeBytes(byteArrayOf(0)) }
        try {
            ActivityScenario.launch(MediaFixtureActivity::class.java).use { scenario ->
                scenario.onActivity { it.setContent {
                    MaterialTheme {
                        MediaViewerScreen(ThemePresets.whatsappIosLight, "Pending synthetic video", "video", "1 B",
                            storagePath = placeholder.toURI().toString(), providedModel = placeholder,
                            ownedActions = true,
                            createSignedUrl = { path, _ -> kotlinx.coroutines.delay(60_000); path },
                            deliverPlayback = { false },
                        )
                    }
                } }
                waitText("Pending synthetic video")
                capture("28-owned-video-pending-uri")
                assertFalse("Cannot play while URI is unresolved", device.hasObject(By.desc("Play")))
            }
        } finally { placeholder.delete() }
    }

    @Test fun boundLeasedVideoPreparesAndStaleEpochDeniesActualPlayback() {
        val context = inst.targetContext
        val unique = java.util.UUID.randomUUID().toString()
        val audio = File(context.cacheDir, "synthetic-bound-$unique.m4a")
        val video = File(context.cacheDir, "synthetic-bound-$unique.mp4")
        val identity = com.example.chat.data.local.SessionEpoch.Identity("synthetic-ui", unique, 1L)
        val store = com.example.chat.data.local.AttachmentFileCache(context, { identity }, { 8L * 1024 * 1024 }, 4L * 1024 * 1024)
        val ref = com.example.chat.data.local.AttachmentRef("synthetic-room", "synthetic-message", "synthetic-attachment")
        val guardOpen = java.util.concurrent.atomic.AtomicBoolean(true)
        val denied = java.util.concurrent.atomic.AtomicInteger(0)
        try {
            val native = MediaFollowOnProofTest()
            native.createSilentAac(audio)
            native.createAvcWithAudio(video, audio)
            val stamp = store.session()!!
            video.inputStream().use {
                assertEquals(com.example.chat.data.local.StorePut.Stored, store.put(ref, it, false, false, stamp))
            }
            val resolver = com.example.chat.data.media.AttachmentSourceResolver(store, { false },
                { candidate -> if (candidate == ref) com.example.chat.data.media.TrustedAttachment("video", false, false) else null },
                { error("Synthetic offline bound fixture must not download") })
            val binding = AttachmentConsumerBinding(stamp, resolver, { store.session() },
                deliverIfCurrent = { action ->
                    if (guardOpen.get() && store.isEpochCurrent(stamp)) { action(); true }
                    else { denied.incrementAndGet(); false }
                }, authorizedUrl = { error("No signed URL in offline leased fixture") })
            val message = ChatMessage(ref.messageId, ref.conversationId, "synthetic-peer", MessageType.video, "",
                createdAt = Instant.EPOCH,
                attachment = MessageAttachment(ref.attachmentId, "video", "Bound synthetic video", "local", "private-object"))
            ActivityScenario.launch(MediaFixtureActivity::class.java).use { scenario ->
                scenario.onActivity { it.setContent { MaterialTheme {
                    BoundAttachmentViewer(ThemePresets.whatsappIosLight, message, binding, false, null, {})
                } } }
                waitText("Bound synthetic video")
                assertTrue("Native leased video prepared", device.wait(Until.hasObject(By.desc("Play")), 15000))
                capture("29-bound-leased-video-ready")
                // Install a new cache epoch without dismissing composition, exercising the final start barrier.
                store.clearCurrentAccount()
                guardOpen.set(false)
                device.findObjects(By.desc("Play")).first().click()
                waitText("Attachment session changed. Reopen it from the chat.")
                assertTrue("Stale playback crossed and was denied by final barrier", denied.get() > 0)
                capture("30-bound-video-stale-playback-denied")
            }
        } finally {
            store.clearCurrentAccount()
            audio.delete(); video.delete()
        }
    }

    @Test fun narrowWhiteIncomingAndLocalPreviewHaveVisiblePixelsAndSafeFooter() {
        val image = photo()
        ActivityScenario.launch(MediaFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent {
                MaterialTheme {
                    val theme = ThemePresets.whatsappIosLight.copy(fontScale = 1.3)
                    Column(Modifier.width(320.dp).fillMaxHeight().safeDrawingPadding().background(Color.White)) {
                        ChatMessageBubble(ChatMessage("in", "c", "peer", MessageType.text, "Incoming bubble", createdAt = Instant.now(), deliveryState = DeliveryState.delivered), false, theme, enableAnimatedEmojis = false)
                        ChatMessageBubble(ChatMessage("photo", "c", "me", MessageType.image, "Caption with time and read receipt", createdAt = Instant.now(), deliveryState = DeliveryState.read,
                            attachment = MessageAttachment("a", "image", "Synthetic photo", "1 KB", image.toURI().toString())), true, theme, enableAnimatedEmojis = false)
                        ChatMessageBubble(ChatMessage("loc", "c", "peer", MessageType.location, "", createdAt = Instant.now(), deliveryState = DeliveryState.delivered,
                            metadata = mapOf("latitude" to 17.3, "longitude" to 78.4)), false, theme)
                    }
                }
            } }
            waitText("Incoming bubble")
            assertTrue(device.wait(Until.hasObject(By.desc("Synthetic photo")), 10000))
            capture("20-media-narrow-bubbles-preview")
            val imageBounds = device.findObject(By.desc("Synthetic photo")).visibleBounds
            assertTrue("Preview has real height", imageBounds.height() > 100)
            assertTrue("Preview stays within screen", imageBounds.left >= 0 && imageBounds.right <= device.displayWidth)
        }
    }

    @Test fun reviewCancelDoesNotSendAndViewOnceIsActualSendState() {
        val image = photo()
        val item = MediaReviewItem(image.toURI().toString(), PickedFile(image.name, path = image.path, mimeType = "image/png"), "image")
        ActivityScenario.launch(MediaFixtureActivity::class.java).use { scenario ->
            var result: ReviewedMedia? = null
            var sends = 0
            fun show() { scenario.onActivity { it.setContent {
                MaterialTheme {
                    var open by remember { mutableStateOf(true) }
                    if (open) Box(Modifier.width(320.dp).fillMaxHeight()) {
                        MediaReviewScreen(listOf(item), onCancel = { open = false }, onSend = { selected -> result = selected; sends++; open = false })
                    } else Text("Review closed; sends=$sends", Modifier.safeDrawingPadding())
                }
            } } }
            show()
            waitText("Review attachment")
            clickDesc("View once off")
            assertTrue(device.wait(Until.hasObject(By.desc("View once on")), 5000))
            capture("21-media-review-viewonce-footer")
            val send = device.findObject(By.desc("Send attachment")).visibleBounds
            assertTrue("Send above gesture inset", send.bottom < device.displayHeight)
            clickText("Cancel")
            waitText("Review closed; sends=0")
            assertEquals(0, sends)
            assertNull(result)
            // A new composition resets state and preserves explicit send-only action.
            scenario.onActivity { it.setContent { Text("Reset") } }
            show()
            clickDesc("View once off")
            clickDesc("Send attachment")
            waitText("Review closed; sends=1")
            assertTrue(result!!.viewOnce)
            assertEquals(1, result!!.items.size)
        }
    }

    @Test fun editorCancelLeavesOriginalApplyProducesNewCopy() {
        val image = photo()
        val original = image.readBytes()
        ActivityScenario.launch(MediaFixtureActivity::class.java).use { scenario ->
            var applied: File? = null
            fun show() { scenario.onActivity { it.setContent {
                MaterialTheme {
                    var open by remember { mutableStateOf(true) }
                    if (open) ImageEditorScreen(image.toURI().toString(), onCancel = { open = false }, onApply = { file -> applied = file; open = false })
                    else Text(if (applied == null) "Editor cancelled" else "Editor applied", Modifier.safeDrawingPadding())
                }
            } } }
            show()
            assertTrue(device.wait(Until.hasObject(By.desc("Image being edited")), 10000))
            clickText("Rotate")
            capture("22-image-editor-rotated")
            clickText("Cancel")
            waitText("Editor cancelled")
            assertNull(applied)
            assertArrayEquals(original, image.readBytes())
            scenario.onActivity { it.setContent { Text("Reset") } }
            show()
            assertTrue(device.wait(Until.hasObject(By.desc("Image being edited")), 10000))
            clickText("Rotate")
            clickText("Apply")
            waitText("Editor applied")
            assertNotNull(applied)
            assertTrue(applied!!.isFile)
            assertTrue(applied!!.path != image.path)
            val exported = android.graphics.BitmapFactory.decodeFile(applied!!.path)
            assertTrue("Rotation changes exported orientation", exported.width > exported.height)
            assertArrayEquals(original, image.readBytes())
            capture("23-image-editor-apply-result")
        }
    }

    @Test fun editorCropApplyExportsSquareCopyResetRestoresPortrait() {
        val image = photo()
        val original = image.readBytes()
        ActivityScenario.launch(MediaFixtureActivity::class.java).use { scenario ->
            var applied: File? = null
            scenario.onActivity { it.setContent {
                MaterialTheme {
                    var open by remember { mutableStateOf(true) }
                    if (open) ImageEditorScreen(image.toURI().toString(), onCancel = { open = false }, onApply = { file -> applied = file; open = false })
                    else Text("Crop applied", Modifier.safeDrawingPadding())
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.desc("Image being edited")), 10000))
            clickText("Square")
            capture("25-image-editor-square-crop")
            val crop = device.findObject(By.desc("Image being edited")).visibleBounds
            assertTrue("Square preview", kotlin.math.abs(crop.width() - crop.height()) <= 3)
            clickText("Reset")
            device.waitForIdle()
            val reset = device.findObject(By.desc("Image being edited")).visibleBounds
            assertTrue("Reset restores portrait", reset.height() > reset.width())
            clickText("Square")
            clickText("Apply")
            waitText("Crop applied")
            val result = android.graphics.BitmapFactory.decodeFile(applied!!.path)
            assertEquals(result.width, result.height)
            assertArrayEquals(original, image.readBytes())
        }
    }

    @Test fun narrowAudioTrimCancelThenApplyPreservesOriginal() {
        val context = inst.targetContext
        val audio = File(context.cacheDir, "synthetic-ui-trim.m4a")
        MediaFollowOnProofTest().createSilentAac(audio)
        val original = audio.readBytes()
        ActivityScenario.launch(MediaFixtureActivity::class.java).use { scenario ->
            var result: MediaTrimResult? = null
            fun show() { scenario.onActivity { it.setContent {
                MaterialTheme {
                    var open by remember { mutableStateOf(true) }
                    if (open) Box(Modifier.width(320.dp).fillMaxHeight()) {
                        MediaTrimScreen(audio.toURI().toString(), false, onCancel = { open = false }, onApply = { value -> result = value; open = false })
                    } else Text(if (result == null) "Trim cancelled" else "Trim exported", Modifier.safeDrawingPadding())
                }
            } } }
            show()
            waitText("Trim a copy")
            assertTrue(device.wait(Until.hasObject(By.textContains("Start:")), 10000))
            capture("26-audio-trim-narrow-range")
            val apply = device.findObject(By.text("Apply")).visibleBounds
            assertTrue(apply.left >= 0 && apply.right <= device.displayWidth)
            clickText("Cancel")
            waitText("Trim cancelled")
            assertNull(result)
            assertArrayEquals(original, audio.readBytes())
            scenario.onActivity { it.setContent { Text("Reset") } }
            show()
            assertTrue(device.wait(Until.hasObject(By.textContains("Start:")), 10000))
            clickText("Apply")
            waitText("Trim exported")
            assertNotNull(result)
            assertTrue(result!!.file.length() > 0)
            assertArrayEquals(original, audio.readBytes())
            capture("27-audio-trim-exported")
            result!!.file.delete()
        }
        audio.delete()
    }

    @Test fun restrictedViewerHasNoShareEditOrExternalOpen() {
        val image = photo()
        ActivityScenario.launch(MediaFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent {
                MaterialTheme { MediaViewerScreen(ThemePresets.whatsappIosLight, "Synthetic once", "image", "1 KB", image.toURI().toString(), restrictedViewOnce = true) }
            } }
            waitText("Synthetic once")
            Thread.sleep(800)
            assertFalse(device.hasObject(By.desc("Share file link")))
            assertFalse(device.hasObject(By.desc("Open with app")))
            assertFalse(device.hasObject(By.desc("Edit a copy")))
            capture("24-view-once-restricted-viewer")
        }
    }
}
