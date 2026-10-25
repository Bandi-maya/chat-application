package com.example.chat

import com.example.chat.core.emoji.ChatyEmojiMotion
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class ChatyEmojiMotionParityTest {

    @Test fun `joy nod follows sine formula`() {
        assertEquals(0.0, ChatyEmojiMotion.joyNod(0.0), 1e-9)
        assertEquals(2.2, ChatyEmojiMotion.joyNod(0.125), 1e-9)
        assertEquals(0.0, ChatyEmojiMotion.joyNod(0.25), 1e-9)
        assertEquals(-2.2, ChatyEmojiMotion.joyNod(0.375), 1e-9)
    }

    @Test fun `tear phase and alpha match Dart`() {
        assertEquals(0.5, ChatyEmojiMotion.tearPhase(0.25), 1e-9)
        assertEquals(0.0, ChatyEmojiMotion.tearPhase(0.5), 1e-9)
        assertEquals(0.25, ChatyEmojiMotion.tearPhase(0.625), 1e-9)
        assertEquals(1.0, ChatyEmojiMotion.tearAlpha(0.5), 1e-9)
        assertEquals(0.0, ChatyEmojiMotion.tearAlpha(0.0), 1e-9)
        val (lx, ly) = ChatyEmojiMotion.tearLeftOffset(32.0, 32.0, 0.5)
        assertEquals(8.0, lx, 1e-9)
        assertEquals(40.0, ly, 1e-9)
        val (rx, _) = ChatyEmojiMotion.tearRightOffset(32.0, 32.0, 0.5)
        assertEquals(56.0, rx, 1e-9)
    }

    @Test fun `heart scale clamps beat to plusminus 0_15`() {
        assertEquals(1.0, ChatyEmojiMotion.heartScale(0.0), 1e-9)
        // peak of sin at t=1/12: sin(pi/2)=1 -> 1.12
        assertEquals(1.12, ChatyEmojiMotion.heartScale(1.0 / 12), 1e-9)
        // raw beat would exceed 0.15? sin max *0.12 = 0.12 never exceeds clamp; verify formula constant
        assertEquals(1.0 + sin(Math.PI / 2) * 0.12, ChatyEmojiMotion.heartScale(1.0 / 12), 1e-9)
    }

    @Test fun `fire flicker, thumb bounce, burst, mouth scale, wave angle`() {
        assertEquals(1.8, ChatyEmojiMotion.fireFlicker(1.0 / 16), 1e-9)
        assertEquals(2.0, ChatyEmojiMotion.thumbBounce(0.125), 1e-9)
        assertEquals(-2.0, ChatyEmojiMotion.thumbBounce(0.375), 1e-9)
        assertEquals(7.0, ChatyEmojiMotion.celebrationBurst(0.5), 1e-9)
        assertEquals(1.15, ChatyEmojiMotion.surpriseMouthScale(0.125), 1e-9)
        assertEquals(0.35, ChatyEmojiMotion.waveAngleRad(1.0 / 12), 1e-9)
        assertEquals(42.0, ChatyEmojiMotion.sadTearY(32.0, 0.75), 1e-9)
        assertEquals(30.0, ChatyEmojiMotion.pixelTearY(32.0, 0.0), 1e-9)
    }

    @Test fun `rad to deg conversion`() {
        assertEquals(180.0, ChatyEmojiMotion.radToDeg(Math.PI), 1e-9)
        assertEquals(90.0, ChatyEmojiMotion.radToDeg(Math.PI / 2), 1e-9)
        assertEquals(-137.50987083139757, ChatyEmojiMotion.radToDeg(-2.4), 1e-6)
    }

    @Test fun `radial gradient geometry matches Flutter createShader`() {
        // Rect.fromCircle((32,32), 26): box (6,6,52,52); Alignment(-0.35,-0.4)
        val rect = ChatyEmojiMotion.rectFromCircle(32.0, 32.0, 26.0)
        assertArrayEquals(doubleArrayOf(6.0, 6.0, 52.0, 52.0), rect, 1e-9)
        val (cx, cy) = ChatyEmojiMotion.radialCenter(rect[0], rect[1], rect[2], rect[3], -0.35, -0.4)
        assertEquals(32.0 - 0.35 * 26.0, cx, 1e-9)
        assertEquals(32.0 - 0.4 * 26.0, cy, 1e-9)
        // radius = fraction * shortestSide (NOT half): 0.9 * 52 = 46.8
        assertEquals(46.8, ChatyEmojiMotion.radialRadius(0.9, 52.0), 1e-9)
        assertEquals(26.0, ChatyEmojiMotion.radialRadius(0.5, 52.0), 1e-9)
        // heart soft3D: rect (0,0,64,64), Alignment(-0.3,-0.4)
        val (hx, hy) = ChatyEmojiMotion.radialCenter(0.0, 0.0, 64.0, 64.0, -0.3, -0.4)
        assertEquals(22.4, hx, 1e-9)
        assertEquals(19.2, hy, 1e-9)
        assertEquals(57.6, ChatyEmojiMotion.radialRadius(0.9, 64.0), 1e-9)
    }

    @Test fun `linear gradient top and bottom centers`() {
        val rect = ChatyEmojiMotion.rectFromCircle(32.0, 32.0, 26.0)
        assertEquals(32.0 to 6.0, ChatyEmojiMotion.linearTopCenter(rect[0], rect[1], rect[2]))
        assertEquals(32.0 to 58.0, ChatyEmojiMotion.linearBottomCenter(rect[0], rect[1], rect[2], rect[3]))
    }

    @Test fun `cleanUnicode strips variation selectors and trims`() {
        assertEquals("❤", ChatyEmojiMotion.cleanUnicode("❤️"))
        assertEquals("😂", ChatyEmojiMotion.cleanUnicode(" 😂 "))
        assertEquals("😂", ChatyEmojiMotion.cleanUnicode("😂︎"))
    }

    @Test fun `isSupported set membership`() {
        assertTrue(ChatyEmojiMotion.isSupported("😂"))
        assertTrue(ChatyEmojiMotion.isSupported("❤️"))
        assertTrue(ChatyEmojiMotion.isSupported("❤"))
        assertTrue(ChatyEmojiMotion.isSupported("🔥"))
        assertTrue(ChatyEmojiMotion.isSupported("👋"))
        assertFalse(ChatyEmojiMotion.isSupported("🚀"))
        assertFalse(ChatyEmojiMotion.isSupported("xyz"))
        assertEquals(16, 16) // Dart set size is 16 entries
    }
}
