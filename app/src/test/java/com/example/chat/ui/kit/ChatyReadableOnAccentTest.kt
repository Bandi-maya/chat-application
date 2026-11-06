package com.example.chat.ui.kit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Default icon/label colour on the two theme accents must reach 3:1 (non-text) and, for the picked colour, 4.5:1. */
class ChatyReadableOnAccentTest {
    private fun ratio(a: Color, b: Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
    }

    @Test fun darkAccentGetsReadableIcon() {
        val accent = Color(0xFF00C49A)
        val c = chatyReadableOn(accent, Color.White, Color.Black)
        assertTrue("dark accent icon ${ratio(c, accent)}", ratio(c, accent) >= 4.5f)
    }

    @Test fun lightAccentGetsReadableIcon() {
        val accent = Color(0xFF00A884)
        val c = chatyReadableOn(accent, Color.White, Color.Black)
        assertTrue("light accent icon ${ratio(c, accent)}", ratio(c, accent) >= 4.5f)
    }
}
