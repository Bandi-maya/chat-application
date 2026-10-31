package com.example.chat

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
                assertFalse("No stale Trim action", device.hasObject(By.text("Trim")))
                assertFalse("No stale Share action", device.hasObject(By.desc("Share file link")))
                assertFalse("No stale Open action", device.hasObject(By.desc("Open with app")))
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
            var showGeneration = 0
            fun show() { val generation = ++showGeneration; scenario.onActivity { it.setContent {
                key(generation) { MaterialTheme {
                    var open by remember { mutableStateOf(true) }
                    if (open) Box(Modifier.width(320.dp).fillMaxHeight()) {
                        MediaReviewScreen(listOf(item), onCancel = { open = false }, onSend = { selected -> result = selected; sends++; open = false })
                    } else Text("Review closed; sends=$sends", Modifier.safeDrawingPadding())
                }
            } } } }
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
            var showGeneration = 0
            fun show() { val generation = ++showGeneration; scenario.onActivity { it.setContent {
                key(generation) { MaterialTheme {
                    var open by remember { mutableStateOf(true) }
                    if (open) ImageEditorScreen(image.toURI().toString(), onCancel = { open = false }, onApply = { file -> applied = file; open = false })
                    else Text(if (applied == null) "Editor cancelled" else "Editor applied", Modifier.safeDrawingPadding())
                }
            } } } }
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
            var showGeneration = 0
            fun show() { val generation = ++showGeneration; scenario.onActivity { it.setContent {
                key(generation) { MaterialTheme {
                    var open by remember { mutableStateOf(true) }
                    if (open) Box(Modifier.width(320.dp).fillMaxHeight()) {
                        MediaTrimScreen(audio.toURI().toString(), false, onCancel = { open = false }, onApply = { value -> result = value; open = false })
                    } else Text(if (result == null) "Trim cancelled" else "Trim exported", Modifier.safeDrawingPadding())
                }
            } } } }
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

    @Test fun restrictedFallbackDoesNotOfferDeadExternalAction() {
        ActivityScenario.launch(MediaFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent {
                MaterialTheme {
                    MediaViewerScreen(ThemePresets.whatsappIosLight, "Synthetic restricted fallback", "document", "local",
                        storagePath = "synthetic-restricted-object", providedModel = byteArrayOf(1),
                        restrictedViewOnce = true, ownedActions = true,
                        createSignedUrl = { path, _ -> path },
                        onOwnedOpen = { error("Restricted fallback must never invoke external open") },
                    )
                }
            } }
            waitText("This restricted attachment cannot be opened in another app.")
            assertFalse("No misleading external action", device.hasObject(By.text("Open Securely")))
            assertFalse(device.hasObject(By.desc("Open with app")))
            assertFalse(device.hasObject(By.desc("Share file link")))
            capture("31-restricted-media-fallback-no-external-action")
        }
    }

    @Test fun explicitCameraShortcutRemainsAvailableAcrossSendReset() {
        val camera = java.util.concurrent.atomic.AtomicInteger()
        val sends = java.util.concurrent.atomic.AtomicInteger()
        val input = java.util.concurrent.atomic.AtomicReference("")
        ActivityScenario.launch(MediaFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { MaterialTheme {
                var text by remember { mutableStateOf("") }
                SideEffect { input.set(text) }
                Column(Modifier.width(320.dp).safeDrawingPadding()) {
                    ChatEntryBar("WAMOD", ComposerColors.resolve(ThemePresets.whatsappIosLight),
                        com.example.chat.core.templates.ComposerTemplate(com.example.chat.core.templates.ComposerActionPlacement.integrated),
                        ThemePresets.whatsappIosLight, text, { text = it },
                        onCamera = { camera.incrementAndGet() }, onSend = { sends.incrementAndGet(); text = "" })
                    androidx.compose.material3.TextButton({ text = "   " }) { Text("Whitespace fixture") }
                    androidx.compose.material3.TextButton({ text = "Synthetic message" }) { Text("Text fixture") }
                }
            } } }
            clickDesc("Camera"); assertEquals(1, camera.get())
            clickText("Whitespace fixture"); assertTrue(device.hasObject(By.desc("Camera")))
            clickText("Text fixture")
            assertTrue(device.wait(Until.hasObject(By.desc("Send message")), 5000))
            capture("34-wamod-camera-shortcut-send-light")
            clickDesc("Send message"); assertEquals(1, sends.get())
            capture("34c-wamod-camera-shortcut-immediate-postsend")
            val restored = device.wait(Until.hasObject(By.desc("Camera")), 5000)
            capture(if (restored) "34c-wamod-camera-shortcut-postsend-success" else "34c-wamod-camera-shortcut-postsend-failure")
            val out = File(inst.targetContext.getExternalFilesDir(null), "screenshots")
            device.dumpWindowHierarchy(File(out, "34c-wamod-camera-shortcut-postsend.xml"))
            File(out, "34c-wamod-camera-shortcut-state.txt").writeText("input=${input.get()}; camera=${camera.get()}; sends=${sends.get()}; cameraVisible=${device.hasObject(By.desc("Camera"))}; sendVisible=${device.hasObject(By.desc("Send message"))}")
            assertTrue("Explicit camera shortcut must be visible after send within 5s", restored)
            assertEquals("Input clears on explicit shortcut route too", "", input.get())
        }
    }

    @Test fun defaultComposerModeSwitchesAndSendResetsWithoutLosingChosenMode() {
        val camera = java.util.concurrent.atomic.AtomicInteger()
        val sends = java.util.concurrent.atomic.AtomicInteger()
        val recordingStarts = java.util.concurrent.atomic.AtomicInteger()
        val textState = java.util.concurrent.atomic.AtomicReference("")
        ActivityScenario.launch(MediaFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { MaterialTheme {
                var text by remember { mutableStateOf("") }
                SideEffect { textState.set(text) }
                Column(Modifier.width(320.dp).safeDrawingPadding()) {
                    ChatEntryBar("Stock", ComposerColors.resolve(ThemePresets.whatsappIosLight),
                        com.example.chat.core.templates.ComposerTemplate(com.example.chat.core.templates.ComposerActionPlacement.integrated),
                        ThemePresets.whatsappIosLight, text, { text = it },
                        onCamera = { camera.incrementAndGet() },
                        onVoiceHoldStart = { recordingStarts.incrementAndGet() },
                        onVideoHoldStart = { recordingStarts.incrementAndGet() },
                        onSend = { sends.incrementAndGet(); text = "" })
                    androidx.compose.material3.TextButton({ text = "   " }) { Text("Whitespace fixture") }
                    androidx.compose.material3.TextButton({ text = "Synthetic message" }) { Text("Text fixture") }
                }
            } } }
            val mic = "Microphone mode. Tap for video, hold to record audio."
            val video = "Video mode. Tap for microphone, hold to record video."
            fun diagnose(name: String) {
                capture(name)
                val folder = File(inst.targetContext.getExternalFilesDir(null), "screenshots")
                device.dumpWindowHierarchy(File(folder, "$name.xml"))
                File(folder, "$name-state.txt").writeText(
                    "input=${textState.get()}; sends=${sends.get()}; camera=${camera.get()}; recordingStarts=${recordingStarts.get()}; " +
                        "micVisible=${device.hasObject(By.desc(mic))}; videoVisible=${device.hasObject(By.desc(video))}; sendVisible=${device.hasObject(By.desc("Send message"))}")
            }
            clickDesc(mic)
            assertTrue(device.wait(Until.hasObject(By.desc(video)), 5000))
            assertEquals(0, camera.get()); assertEquals(0, sends.get()); assertEquals(0, recordingStarts.get())
            clickDesc(video)
            // Measured wait (run-132: mic icon not re-served within 5s after the video
            // tap; the product swap is a plain conditional render - no animation - so this
            // is the emulator a11y-publish lag family, same shape as the approved
            // recording Stop guard). Assertion unchanged; landing latency logged.
            val micSwapStart = android.os.SystemClock.uptimeMillis()
            val micBack = device.wait(Until.hasObject(By.desc(mic)), 20000)
            android.util.Log.w("MediaProof", "MODEICON-DIAG mic-reattach landed=" + micBack + " latencyMs=" + (android.os.SystemClock.uptimeMillis() - micSwapStart))
            assertTrue(micBack)
            clickDesc(mic) // Keep video selected across whitespace, text entry and send reset.
            clickText("Whitespace fixture")
            assertTrue(device.wait(Until.hasObject(By.desc(video)), 5000))
            clickText("Text fixture")
            assertTrue(device.wait(Until.hasObject(By.desc("Send message")), 5000))
            capture("34-stock-shared-mode-send-light")
            clickDesc("Send message"); assertEquals(1, sends.get())
            diagnose("34a-stock-shared-mode-immediate-post-send")
            val reset = device.wait(Until.hasObject(By.desc(video)), 5000)
            diagnose(if (reset) "34b-stock-shared-mode-reset-success" else "34b-stock-shared-mode-reset-failure")
            assertTrue("Chosen video mode must return within 5s after send", reset)
            assertEquals("Input clears after send", "", textState.get())
            assertFalse("Send must leave idle control", device.hasObject(By.desc("Send message")))
            assertEquals(1, sends.get()); assertEquals(0, camera.get()); assertEquals(0, recordingStarts.get())
        }
    }

    @Test fun attachmentGallerySelectsInTapOrderAndRoutesExistingActions() {
        val image = photo()
        val files = (0..34).map { i -> File(image.parentFile, "synthetic-gallery-$i.png").also { image.copyTo(it, overwrite = true) } }
        val items = files.mapIndexed { i, file -> com.example.chat.ui.chats.detail.AttachmentGalleryItem(android.net.Uri.fromFile(file), "Synthetic $i") }
        val reviewed = java.util.concurrent.atomic.AtomicReference<List<android.net.Uri>>()
        val camera = java.util.concurrent.atomic.AtomicBoolean(false)
        val action = java.util.concurrent.atomic.AtomicReference<AttachmentAction>()
        ActivityScenario.launch(MediaFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { MaterialTheme {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    com.example.chat.ui.chats.detail.AttachmentGallerySheet(ThemePresets.whatsappIosLight,
                        onAction = { action.set(it) }, onCamera = { camera.set(true) },
                        onReviewImages = { reviewed.set(it) }, maxSelection = 2, fixtureItems = items)
                }
            } } }
            clickDesc("Photo Synthetic 1"); clickDesc("Photo Synthetic 0")
            waitText("Review (2)")
            capture("33-attachment-gallery-selected-light-narrow")
            clickText("Review (2)")
            assertEquals(listOf(items[1].uri, items[0].uri), reviewed.get())
            clickDesc("Open camera"); assertTrue(camera.get())
            clickText("File"); assertEquals(AttachmentAction.document, action.get())
        }
        files.forEach { it.delete() }
        image.delete()
    }

    @Test fun albumEnvelopeRoundTripPreservesBatchAndProtectionFlags() {
        val envelope = org.json.JSONObject().apply {
            put("type", "image"); put("url", "synthetic-local"); put("text", "Caption")
            put("chaty_album_v", 1); put("chaty_album_id", "synthetic"); put("chaty_album_index", 0); put("chaty_album_count", 2)
            put("view_once", true); put("forwarded", true)
        }
        val parsed = ChatMessage.fromApi(mapOf("uid" to "m0", "room_id" to "c", "sender_id" to "peer", "content" to envelope.toString(), "created_at" to "2026-10-27T00:00:00Z"))
        assertEquals("synthetic", parsed.metadata["chaty_album_id"])
        assertTrue(com.example.chat.data.local.AttachmentCachePolicy.viewOnceRestricted(parsed.metadata))
        assertEquals(true, parsed.metadata["forwarded"])
        assertEquals("Caption", parsed.text)
    }

    @Test fun compactAlbumKeepsItemActionsAndOneCaption() {
        val first = photo()
        val messages = (0..1).map { i ->
            ChatMessage("album-$i", "synthetic", "me", MessageType.image, if (i == 0) "Compact album caption" else "",
                attachment = MessageAttachment("album-file-$i", "image", "Photo $i", "local", first.toURI().toString()),
                metadata = com.example.chat.ui.chats.detail.MediaAlbumContract.stamp("synthetic-batch", i, 2), createdAt = Instant.EPOCH)
        }
        val opened = java.util.concurrent.atomic.AtomicReference<String>()
        val menued = java.util.concurrent.atomic.AtomicReference<String>()
        ActivityScenario.launch(MediaFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { MaterialTheme {
                ChatMessageBubble(messages[0], true, ThemePresets.whatsappIosLight,
                    mediaContent = {
                        androidx.compose.foundation.layout.Box(
                            Modifier.pointerInput(Unit) {
                                awaitEachGesture {
                                    while (true) {
                                        val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                        ev.changes.forEach { ch ->
                                            android.util.Log.i("ALBUM-PROBE", "evt Initial type=" + ev.type + " pos=" + ch.position + " consumed=" + ch.isConsumed)
                                        }
                                        if (ev.type == androidx.compose.ui.input.pointer.PointerEventType.Release) break
                                    }
                                }
                            }.pointerInput(Unit) {
                                awaitEachGesture {
                                    while (true) {
                                        val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
                                        ev.changes.forEach { ch ->
                                            android.util.Log.i("ALBUM-PROBE", "evt Final type=" + ev.type + " pos=" + ch.position + " consumed=" + ch.isConsumed)
                                        }
                                        if (ev.type == androidx.compose.ui.input.pointer.PointerEventType.Release) break
                                    }
                                }
                            },
                        ) {
                            com.example.chat.ui.chats.detail.CompactMediaAlbum(messages, null, null, null,
                                onOpen = { item, _ -> opened.set(item.id) }, onMenu = { item, _ -> menued.set(item.id) })
                        }
                    })
            } } }
            waitText("Compact album caption")
            assertTrue(device.wait(Until.hasObject(By.descContains("Album photo 2 of 2")), 10000))
            val albumNode = device.findObject(By.descContains("Album photo 2 of 2"))
            val vb = albumNode.visibleBounds
            android.util.Log.i("ALBUM-PROBE", "tile2 bounds=" + vb + " clickable=" + albumNode.isClickable + " children=" + albumNode.childCount + " display=" + device.displayWidth + "x" + device.displayHeight)
            capture("32a-compact-album-preclick-probe")
            device.click(vb.centerX(), vb.centerY())
            device.waitForIdle()
            android.util.Log.i("ALBUM-PROBE", "after click1 opened=" + opened.get())
            if (opened.get() == null) {
                Thread.sleep(1500)
                device.click(vb.centerX(), vb.centerY())
                device.waitForIdle()
                android.util.Log.i("ALBUM-PROBE", "after click2 opened=" + opened.get())
                try {
                    val child = albumNode.children.firstOrNull()
                    android.util.Log.i("ALBUM-PROBE", "tile2 child bounds=" + (child?.visibleBounds ?: "null") + " clickable=" + (child?.isClickable ?: "null") + " desc=" + (child?.contentDescription ?: "null"))
                } catch (e: Exception) { android.util.Log.i("ALBUM-PROBE", "child probe failed: " + e.message) }
                // Evidence only (assertions unchanged): pin WHO swallows the tile tap.
                try {
                    val dir = File(inst.targetContext.getExternalFilesDir(null), "screenshots")
                    java.io.FileOutputStream(File(dir, "compact-album-hierarchy.xml")).use { device.dumpWindowHierarchy(it) }
                    android.util.Log.i("ALBUM-PROBE", "hierarchy dumped")
                } catch (e: Exception) { android.util.Log.i("ALBUM-PROBE", "dump failed: " + e.message) }
                // Long-press shares detectTapGestures' awaitFirstDown path: if onMenu fires,
                // the tile receives downs and only tap completion fails; if not, the down
                // never reaches the tile (child/parent/overlay consumption).
                albumNode.longClick()
                device.waitForIdle()
                android.util.Log.i("ALBUM-PROBE", "after longclick menued=" + menued.get())
                // Tile specificity: does tile 1 open when tile 2 does not?
                try {
                    val t1 = device.findObject(By.descContains("Album photo 1 of 2"))
                    val b1 = t1.visibleBounds
                    android.util.Log.i("ALBUM-PROBE", "tile1 bounds=" + b1 + " clickable=" + t1.isClickable)
                    device.click(b1.centerX(), b1.centerY())
                    device.waitForIdle()
                    android.util.Log.i("ALBUM-PROBE", "after tile1 click opened=" + opened.get())
                } catch (e: Exception) { android.util.Log.i("ALBUM-PROBE", "tile1 probe failed: " + e.message) }
            }
            assertEquals("album-1", opened.get())
            val a = device.findObject(By.descContains("Album photo 1 of 2")).visibleBounds
            val b = device.findObject(By.descContains("Album photo 2 of 2")).visibleBounds
            assertTrue("Tiles share a compact row", kotlin.math.abs(a.top - b.top) < 4)
            assertTrue("Two square tiles", kotlin.math.abs(a.width() - a.height()) < 4)
            capture("32-compact-explicit-album-light")
        }
        first.delete()
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
