package com.example.chat

import com.example.chat.ui.island.DynamicIslandController
import com.example.chat.ui.island.DynamicIslandPayload
import com.example.chat.ui.island.IslandMode
import com.example.chat.ui.island.IslandNativeBridge
import com.example.chat.ui.island.IslandNativeEvent
import com.example.chat.ui.island.IslandPayloadType
import com.example.chat.ui.island.IslandTimerHandle
import com.example.chat.ui.island.IslandTimerScheduler
import java.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior tests written from the Dart controller's observable behavior in
 *  lib/features/island/controllers/dynamic_island_controller.dart and the
 *  payload semantics in lib/features/island/models/dynamic_island_payload.dart. */
@OptIn(ExperimentalCoroutinesApi::class)
class DynamicIslandControllerParityTest {

    /** Deterministic timer: records scheduled actions, fires on demand. */
    private class FakeTimerScheduler : IslandTimerScheduler {
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        override fun schedule(delayMs: Long, action: () -> Unit): IslandTimerHandle {
            scheduled.add(delayMs to action)
            val entry = delayMs to action
            return IslandTimerHandle { scheduled.remove(entry) }
        }
        fun fireLatest() { scheduled.lastOrNull()?.second?.invoke() }
    }

    private class FakeBridge : IslandNativeBridge {
        var handler: ((IslandNativeEvent) -> Unit)? = null
        var clearAllCalls = 0
        override fun setNotificationEventHandler(handler: (IslandNativeEvent) -> Unit) {
            this.handler = handler
        }
        override fun clearAll() { clearAllCalls++ }
        override suspend fun setHideSystemNotifications(hide: Boolean) = hide
        override suspend fun isHideSystemNotificationsEnabled() = false
        override suspend fun setHeaderEnabled(enabled: Boolean) = enabled
        override suspend fun isHeaderEnabled() = false
    }

    private var nowMs = 1_700_000_000_000L
    private fun now(): Instant = Instant.ofEpochMilli(nowMs)

    private fun fixture(): Triple<FakeTimerScheduler, FakeBridge, DynamicIslandController> {
        val timers = FakeTimerScheduler()
        val bridge = FakeBridge()
        val scope = TestScope(UnconfinedTestDispatcher())
        return Triple(timers, bridge, DynamicIslandController(
            nativeBridge = bridge,
            timerScheduler = timers,
            scope = scope,
            now = ::now,
        ))
    }

    private fun payload(
        id: String,
        type: IslandPayloadType = IslandPayloadType.chatMessage,
        isPlaying: Boolean = false,
        timestampOffsetMs: Long = 0,
        onDismiss: (() -> Unit)? = null,
    ) = DynamicIslandPayload(
        id = id,
        type = type,
        title = "t-$id",
        timestamp = now().plusMillis(timestampOffsetMs),
        isPlaying = isPlaying,
        onDismiss = onDismiss,
    )

    // ── payload priority & ongoing activity ────────────────────────────────

    @Test fun `priority hierarchy matches dart`() {
        assertEquals(0, payload("a", IslandPayloadType.incomingCall).priority)
        assertEquals(0, payload("a", IslandPayloadType.dragAndGoTarget).priority)
        assertEquals(1, payload("a", IslandPayloadType.ongoingCall).priority)
        assertEquals(2, payload("a", IslandPayloadType.voiceNotePlayback).priority)
        assertEquals(2, payload("a", IslandPayloadType.media).priority)
        assertEquals(2, payload("a", IslandPayloadType.clipboardIntent).priority)
        assertEquals(3, payload("a", IslandPayloadType.chatMessage).priority)
        assertEquals(4, payload("a", IslandPayloadType.ghostModeStatus).priority)
        assertEquals(4, payload("a", IslandPayloadType.systemAlert).priority)
        assertEquals(4, payload("a", IslandPayloadType.contactOnline).priority)
    }

    @Test fun `ongoing activity rules match dart`() {
        assertTrue(payload("a", IslandPayloadType.ongoingCall).isOngoingActivity)
        assertTrue(payload("a", IslandPayloadType.incomingCall).isOngoingActivity)
        assertTrue(payload("a", IslandPayloadType.dragAndGoTarget).isOngoingActivity)
        assertTrue(payload("a", IslandPayloadType.media, isPlaying = true).isOngoingActivity)
        assertFalse(payload("a", IslandPayloadType.media, isPlaying = false).isOngoingActivity)
        assertTrue(payload("a", IslandPayloadType.voiceNotePlayback, isPlaying = true).isOngoingActivity)
        assertFalse(payload("a", IslandPayloadType.chatMessage).isOngoingActivity)
    }

    // ── postPayload state machine ──────────────────────────────────────────

    @Test fun `first post auto-expands`() {
        val (_, _, c) = fixture()
        c.postPayload(payload("m1"))
        assertEquals(IslandMode.expanded, c.mode)
        assertEquals("m1", c.activePayload?.id)
    }

    @Test fun `post without autoExpand from idle goes compact`() {
        val (_, _, c) = fixture()
        c.postPayload(payload("m1"), autoExpand = false)
        assertEquals(IslandMode.compact, c.mode)
    }

    @Test fun `two payloads with ongoing first go split`() {
        val (_, _, c) = fixture()
        c.postPayload(payload("m1", IslandPayloadType.chatMessage), autoExpand = false)
        c.postPayload(payload("call", IslandPayloadType.ongoingCall), autoExpand = false)
        // ongoingCall has priority 1 < chatMessage 3, so it sorts first
        assertEquals(IslandMode.split, c.mode)
        assertEquals("call", c.activePayload?.id)
        assertEquals("m1", c.secondaryPayload?.id)
    }

    @Test fun `queue sorted by priority then newest first`() {
        val (_, _, c) = fixture()
        c.postPayload(payload("old", IslandPayloadType.chatMessage, timestampOffsetMs = 0), autoExpand = false)
        c.postPayload(payload("new", IslandPayloadType.chatMessage, timestampOffsetMs = 1000), autoExpand = false)
        c.postPayload(payload("call", IslandPayloadType.incomingCall, timestampOffsetMs = 500), autoExpand = false)
        assertEquals(listOf("call", "new", "old"), c.queue.map { it.id })
    }

    @Test fun `posting same id updates in place`() {
        val (_, _, c) = fixture()
        c.postPayload(payload("m1"), autoExpand = false)
        c.postPayload(payload("m1").copy(title = "updated"), autoExpand = false)
        assertEquals(1, c.queue.size)
        assertEquals("updated", c.queue[0].title)
    }

    // ── removal / reconcile ────────────────────────────────────────────────

    @Test fun `remove last payload goes idle`() {
        val (_, _, c) = fixture()
        c.postPayload(payload("m1"))
        c.removePayload("m1")
        assertEquals(IslandMode.idle, c.mode)
        assertTrue(c.queue.isEmpty())
    }

    @Test fun `removePayloadsWhere removes matching`() {
        val (_, _, c) = fixture()
        c.postPayload(payload("a"), autoExpand = false)
        c.postPayload(payload("b"), autoExpand = false)
        c.removePayloadsWhere { it.id == "a" }
        assertEquals(listOf("b"), c.queue.map { it.id })
    }

    @Test fun `clearAll goes idle and notifies native`() {
        val (_, bridge, c) = fixture()
        c.postPayload(payload("m1"))
        c.clearAll()
        assertEquals(IslandMode.idle, c.mode)
        assertEquals(1, bridge.clearAllCalls)
    }

    // ── expand / collapse ──────────────────────────────────────────────────

    @Test fun `expand picks player subview for media`() {
        val (_, _, c) = fixture()
        c.postPayload(payload("song", IslandPayloadType.media, isPlaying = true), autoExpand = false)
        c.expand()
        assertEquals("player", c.activeSubView)
    }

    @Test fun `expand picks primary subview otherwise`() {
        val (_, _, c) = fixture()
        c.postPayload(payload("m1"), autoExpand = false)
        c.expand()
        assertEquals("primary", c.activeSubView)
    }

    @Test fun `expand on empty queue is a no-op`() {
        val (_, _, c) = fixture()
        c.expand()
        assertEquals(IslandMode.idle, c.mode)
    }

    @Test fun `collapse with non-ongoing goes compact`() {
        val (_, _, c) = fixture()
        c.postPayload(payload("m1"))
        c.collapse()
        assertEquals(IslandMode.compact, c.mode)
    }

    @Test fun `toggleExpanded flips state`() {
        val (_, _, c) = fixture()
        c.postPayload(payload("m1"), autoExpand = false)
        c.toggleExpanded()
        assertEquals(IslandMode.expanded, c.mode)
        c.toggleExpanded()
        assertEquals(IslandMode.compact, c.mode)
    }

    // ── pin ────────────────────────────────────────────────────────────────

    @Test fun `pinned island does not schedule auto collapse`() {
        val (timers, _, c) = fixture()
        c.postPayload(payload("m1"))
        timers.scheduled.clear()
        c.pinOpen(true)
        assertTrue(timers.scheduled.isEmpty())
        c.expand()
        assertTrue(timers.scheduled.isEmpty())
    }

    // ── dismiss / carousel ─────────────────────────────────────────────────

    @Test fun `dismissActive removes active and invokes onDismiss`() {
        val dismissed = mutableListOf<String>()
        val (_, _, c) = fixture()
        // Queue sorts newest first, so m2 is the active payload.
        c.postPayload(payload("m1"), autoExpand = false)
        c.postPayload(payload("m2", timestampOffsetMs = 1000, onDismiss = { dismissed.add("m2") }), autoExpand = false)
        c.dismissActive()
        assertEquals(listOf("m2"), dismissed)
        assertEquals(listOf("m1"), c.queue.map { it.id })
    }

    @Test fun `carousel wraps around`() {
        val (_, _, c) = fixture()
        c.postPayload(payload("a", timestampOffsetMs = 0), autoExpand = false)
        c.postPayload(payload("b", timestampOffsetMs = 1000), autoExpand = false)
        assertEquals("b", c.activePayload?.id)
        c.nextCarouselItem()
        assertEquals("a", c.activePayload?.id)
        c.nextCarouselItem()
        assertEquals("b", c.activePayload?.id)
        c.prevCarouselItem()
        assertEquals("a", c.activePayload?.id)
    }

    @Test fun `carousel no-op with single item`() {
        val (_, _, c) = fixture()
        c.postPayload(payload("a"), autoExpand = false)
        c.nextCarouselItem()
        assertEquals(0, c.carouselIndex)
    }

    @Test fun `setCarouselIndex ignores out of range`() {
        val (_, _, c) = fixture()
        c.postPayload(payload("a"), autoExpand = false)
        c.setCarouselIndex(5)
        assertEquals(0, c.carouselIndex)
        c.setCarouselIndex(-1)
        assertEquals(0, c.carouselIndex)
    }

    // ── timers ─────────────────────────────────────────────────────────────

    @Test fun `auto collapse fires after 4500ms and collapses`() {
        val (timers, _, c) = fixture()
        c.postPayload(payload("m1"))
        assertEquals(4500L, timers.scheduled.last().first)
        timers.fireLatest()
        assertEquals(IslandMode.compact, c.mode)
    }

    @Test fun `auto collapse not scheduled for ongoing activity`() {
        val (timers, _, c) = fixture()
        c.postPayload(payload("call", IslandPayloadType.ongoingCall))
        assertTrue(timers.scheduled.isEmpty())
        assertEquals(IslandMode.expanded, c.mode)
    }

    @Test fun `dismiss timer clears queue after 7000ms`() {
        val (timers, _, c) = fixture()
        c.postPayload(payload("m1"), autoExpand = false)
        assertEquals(7000L, timers.scheduled.last().first)
        timers.fireLatest()
        assertEquals(IslandMode.idle, c.mode)
        assertTrue(c.queue.isEmpty())
    }

    // ── drag & go / clipboard ──────────────────────────────────────────────

    @Test fun `drag target toggles and drop posts system alert`() {
        val (_, _, c) = fixture()
        c.setDragTargetActive(true)
        assertTrue(c.isDragTargetActive)
        c.handleDroppedData("text content")
        assertFalse(c.isDragTargetActive)
        assertEquals(IslandPayloadType.systemAlert, c.activePayload?.type)
        assertEquals("Content Attached", c.activePayload?.title)
        assertTrue(c.activePayload?.isBandiIcon == true)
    }

    @Test fun `clipboard intent posts without auto expand`() {
        var actionRan = false
        val (_, _, c) = fixture()
        c.postClipboardIntent("Link copied", "https://example.com", "Open") { actionRan = true }
        assertEquals(IslandMode.compact, c.mode)
        assertEquals(IslandPayloadType.clipboardIntent, c.activePayload?.type)
        assertEquals("Open", c.activePayload?.clipboardActionLabel)
        c.activePayload?.onClipboardAction?.invoke()
        assertTrue(actionRan)
    }

    // ── native bridge ──────────────────────────────────────────────────────

    @Test fun `native notification posted becomes chat message payload`() {
        val (_, bridge, c) = fixture()
        bridge.handler?.invoke(IslandNativeEvent.NotificationPosted("k1", false, "Hello", "World"))
        assertEquals("k1", c.activePayload?.id)
        assertEquals(IslandPayloadType.chatMessage, c.activePayload?.type)
        assertEquals("Hello", c.activePayload?.title)
        assertEquals("💬", c.activePayload?.iconEmoji)
    }

    @Test fun `native call notification becomes incoming call`() {
        val (_, bridge, c) = fixture()
        bridge.handler?.invoke(IslandNativeEvent.NotificationPosted("c1", true, "Alice", "Incoming call"))
        assertEquals(IslandPayloadType.incomingCall, c.activePayload?.type)
    }

    @Test fun `native removal removes payload`() {
        val (_, bridge, c) = fixture()
        bridge.handler?.invoke(IslandNativeEvent.NotificationPosted("k1", false, "Hi", "Body"))
        bridge.handler?.invoke(IslandNativeEvent.NotificationRemoved("k1"))
        assertTrue(c.queue.isEmpty())
    }

    @Test fun `native config calls follow dart defaults without bridge`() {
        val scope = TestScope(UnconfinedTestDispatcher())
        val c = DynamicIslandController(
            nativeBridge = null,
            timerScheduler = FakeTimerScheduler(),
            scope = scope,
            now = ::now,
        )
        kotlinx.coroutines.test.runTest {
            assertTrue(c.setHideSystemNotifications(true))
            assertFalse(c.isHideSystemNotificationsEnabled())
            assertTrue(c.setHeaderEnabled(true))
            assertFalse(c.isHeaderEnabled())
        }
    }
}
