package com.example.chat

import com.example.chat.ui.icons.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatyIconMotionTest {
    @Test fun `reduced motion is the identity pose for every icon at every progress`() {
        for (id in ChatyIconId.entries) for (k in 0..20) assertEquals("$id", IconPose.Identity, ChatyIconMotion.pose(id, k / 20.0, true))
    }

    @Test fun `only listed icons move and others stay static`() {
        assertFalse(ChatyIconMotion.hasMotion(ChatyIconId.tasks))
        assertEquals(IconPose.Identity, ChatyIconMotion.pose(ChatyIconId.tasks, 0.5, false))
        assertTrue(ChatyIconMotion.hasMotion(ChatyIconId.send))
    }

    @Test fun `send leaves forward and away then arrives from behind and rests at identity`() {
        val mid = ChatyIconMotion.pose(ChatyIconId.send, 0.4, false)
        assertTrue(mid.dx > 0 && mid.dy < 0 && mid.scale < 1.0 && mid.alpha < 1.0)
        val gap = ChatyIconMotion.pose(ChatyIconId.send, 0.55, false)
        assertEquals(0.0, gap.alpha, 1e-9)
        val arriving = ChatyIconMotion.pose(ChatyIconId.send, 0.65, false)
        assertTrue(arriving.dx < 0 && arriving.dy > 0)
        val end = ChatyIconMotion.pose(ChatyIconId.send, 1.0, false)
        assertEquals(0.0, end.dx, 1e-9); assertEquals(0.0, end.dy, 1e-9); assertEquals(1.0, end.scale, 1e-9); assertEquals(1.0, end.alpha, 1e-9)
    }

    @Test fun `lock closes and unlock opens the shackle and both end in their real state`() {
        assertEquals(0.0, ChatyIconMotion.pose(ChatyIconId.lock, 1.0, false).shackleSwing, 1e-9)
        assertTrue(ChatyIconMotion.pose(ChatyIconId.lock, 0.0, false).shackleSwing > 0.5)
        assertTrue(ChatyIconMotion.pose(ChatyIconId.unlock, 0.0, false).shackleSwing < -0.4)
        assertEquals(0.0, ChatyIconMotion.pose(ChatyIconId.unlock, 1.0, false).shackleSwing, 1e-9)
    }

    @Test fun `every pose is the identity at the end so the static icon is the rest state`() {
        for (id in ChatyIconId.entries) assertEquals("$id", IconPose.Identity, ChatyIconMotion.pose(id, 1.0, false).let { p ->
            if (kotlin.math.abs(p.rotation - 2 * Math.PI) < 1e-9) p.copy(rotation = 0.0) else p })
    }

    @Test fun `settings turns a full turn and every animated icon has a duration`() {
        assertEquals(2 * Math.PI, ChatyIconMotion.pose(ChatyIconId.settings, 1.0, false).rotation, 1e-9)
        ChatyIconMotion.animated.forEach { assertTrue("$it", ChatyIconMotion.durationMs(it) > 0) }
    }
    @Test fun `six new whole glyph event poses have exact rest and reduced motion identity`() {
        val added = setOf(ChatyIconId.chats, ChatyIconId.updates, ChatyIconId.calls, ChatyIconId.profile, ChatyIconId.pin, ChatyIconId.unpin)
        val original = setOf(ChatyIconId.send, ChatyIconId.lock, ChatyIconId.unlock, ChatyIconId.settings, ChatyIconId.favourite, ChatyIconId.favouriteFilled, ChatyIconId.sync, ChatyIconId.callEnd)
        assertEquals(original + added, ChatyIconMotion.animated)
        for (id in added) {
            assertTrue("$id", ChatyIconMotion.hasMotion(id))
            assertTrue("$id", ChatyIconMotion.durationMs(id) > 0)
            for (t in listOf(-1.0, 0.0, 1.0, 2.0)) assertEquals("$id/$t", IconPose.Identity, ChatyIconMotion.pose(id, t, false))
            for (k in 0..100) {
                val t = k / 100.0
                assertEquals("$id/$t", IconPose.Identity, ChatyIconMotion.pose(id, t, true))
                val p = ChatyIconMotion.pose(id, t, false)
                for (v in listOf(p.dx, p.dy, p.scale, p.rotation, p.alpha, p.shackleLift, p.shackleSwing)) assertTrue("$id/$t finite", v.isFinite())
                assertTrue("$id/$t displacement", kotlin.math.abs(p.dy) <= 2.0)
                assertTrue("$id/$t scale", p.scale in 1.0..1.08)
                assertTrue("$id/$t rotation", kotlin.math.abs(p.rotation) <= Math.PI / 12.0)
                assertEquals(1.0, p.alpha, 0.0)
            }
        }
        assertEquals(-2.0, ChatyIconMotion.pose(ChatyIconId.chats, 0.35, false).dy, 1e-9)
        assertEquals(-1.0, ChatyIconMotion.pose(ChatyIconId.profile, 0.35, false).dy, 1e-9)
        assertEquals(2.0, ChatyIconMotion.pose(ChatyIconId.pin, 0.35, false).dy, 1e-9)
        assertEquals(1.08, ChatyIconMotion.pose(ChatyIconId.updates, 0.35, false).scale, 1e-9)
        assertTrue(ChatyIconMotion.pose(ChatyIconId.calls, 0.3, false).rotation > 0)
        assertTrue(ChatyIconMotion.pose(ChatyIconId.calls, 0.65, false).rotation < 0)
        assertTrue(ChatyIconMotion.pose(ChatyIconId.unpin, 0.35, false).rotation < 0)
        for (id in listOf(ChatyIconId.tasks, ChatyIconId.microphone)) {
            assertFalse("$id dependency remains static", ChatyIconMotion.hasMotion(id))
            assertEquals(0, ChatyIconMotion.durationMs(id))
        }
    }
}
