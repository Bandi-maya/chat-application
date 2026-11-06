package com.example.chat

import com.example.chat.ui.chats.detail.ParticipantColorPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticipantColorPolicyTest {

    private val accent = 0xFF00A884L // any theme accent
    private val lightSurface = 0xFFFFFFFFL
    private val darkSurface = 0xFF0B141AL
    private val midSurface = 0xFF808080L

    @Test fun deterministicPerSender() {
        assertEquals(
            ParticipantColorPolicy.colorFor("user-1", accent, lightSurface),
            ParticipantColorPolicy.colorFor("user-1", accent, lightSurface),
        )
    }

    @Test fun distinctSendersGetDistinctColors() {
        val colors = (1..12).map { ParticipantColorPolicy.colorFor("sender-$it", accent, lightSurface) }
        assertTrue("expected mostly distinct colors, got $colors", colors.toSet().size >= 10)
    }

    @Test fun opaqueAndThemeDerived() {
        val c = ParticipantColorPolicy.colorFor("sender-1", accent, darkSurface)
        assertEquals(0xFFL, (c shr 24) and 0xFF)
        // Derived from the accent hue, not a fixed palette: a different accent shifts the result.
        assertNotEquals(
            ParticipantColorPolicy.colorFor("sender-1", accent, darkSurface),
            ParticipantColorPolicy.colorFor("sender-1", 0xFF6750A4L, darkSurface),
        )
    }

    @Test fun darkSurfacesGetBrighterLabelsThanLight() {
        val dark = ParticipantColorPolicy.colorFor("sender-9", accent, darkSurface)
        val light = ParticipantColorPolicy.colorFor("sender-9", accent, lightSurface)
        assertTrue(ParticipantColorPolicy.relativeLuminance(dark) > ParticipantColorPolicy.relativeLuminance(light))
    }

    @Test fun meetsWcagContrastOnLightAndDarkSurfaces() {
        for (i in 1..24) {
            val id = "sender-$i"
            val onLight = ParticipantColorPolicy.colorFor(id, accent, lightSurface)
            val onDark = ParticipantColorPolicy.colorFor(id, accent, darkSurface)
            assertTrue(
                "light surface fails for $id",
                ParticipantColorPolicy.contrastRatio(onLight, lightSurface) >= ParticipantColorPolicy.MIN_CONTRAST,
            )
            assertTrue(
                "dark surface fails for $id",
                ParticipantColorPolicy.contrastRatio(onDark, darkSurface) >= ParticipantColorPolicy.MIN_CONTRAST,
            )
        }
    }

    @Test fun meetsWcagContrastEvenOnMidGray() {
        for (i in 1..24) {
            val id = "sender-$i"
            val c = ParticipantColorPolicy.colorFor(id, accent, midSurface)
            assertTrue(
                "mid-gray surface fails for $id",
                ParticipantColorPolicy.contrastRatio(c, midSurface) >= ParticipantColorPolicy.MIN_CONTRAST,
            )
        }
    }
}
