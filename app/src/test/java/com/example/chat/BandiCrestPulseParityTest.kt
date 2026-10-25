package com.example.chat

import com.example.chat.ui.island.bandiCrestPulseAt
import org.junit.Assert.assertEquals
import org.junit.Test

/** Parity with bandi_kinetic_crest.dart TweenSequence pulse (easeInOutCubic halves). */
class BandiCrestPulseParityTest {
    private fun easeInOutCubic(x: Double): Double =
        if (x < 0.5) 4 * x * x * x else 1 - Math.pow(-2 * x + 2, 3.0) / 2

    @Test
    fun `endpoints and midpoint`() {
        assertEquals(1.0, bandiCrestPulseAt(0.0), 1e-9)
        assertEquals(1.08, bandiCrestPulseAt(0.5), 1e-9)
        assertEquals(1.0, bandiCrestPulseAt(1.0), 1e-9)
    }

    @Test
    fun `quarter points match eased halves`() {
        assertEquals(1.0 + 0.08 * easeInOutCubic(0.5), bandiCrestPulseAt(0.25), 1e-9)
        assertEquals(1.08 - 0.08 * easeInOutCubic(0.5), bandiCrestPulseAt(0.75), 1e-9)
        assertEquals(1.0 + 0.08 * easeInOutCubic(0.2), bandiCrestPulseAt(0.1), 1e-9)
        assertEquals(1.08 - 0.08 * easeInOutCubic(0.6), bandiCrestPulseAt(0.8), 1e-9)
    }
}
