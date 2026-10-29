package com.example.chat

import com.example.chat.domain.models.NavigationEffectPreferences
import com.example.chat.ui.effects.ListEntranceAnimations
import com.example.chat.ui.settings.home.HomeUiStyles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for the GB-reference additions (v8 PDF section 7b mappings):
 * the 15-value list-animation mapping (0 Standard .. 14 SlideIn) and the
 * 13 pager transformer names added alongside Chaty's existing five.
 * These tests pin the ADDITIONS themselves; they do not prove rendered
 * motion equivalence with the reference app.
 */
class GbReferenceAdditionsTest {

    @Test fun `list animation styles match the reference 0 to 14 mapping order`() {
        assertEquals(
            listOf(
                "Standard", "Grow", "Cards", "Curl", "Wave", "Flip", "Fly", "Reverse Fly",
                "Helix", "Fan", "Tilt", "Zipper", "Fade", "Twirl", "Slide In",
            ),
            ListEntranceAnimations.styles,
        )
    }

    @Test fun `list animation default is Standard meaning no effect`() {
        assertEquals("Standard", NavigationEffectPreferences().homeListAnimation)
        assertEquals("Standard", NavigationEffectPreferences().conversationListAnimation)
        assertEquals("Standard", ListEntranceAnimations.styles[0])
    }

    @Test fun `effects prefs roundtrip carries list animation selections`() {
        val prefs = NavigationEffectPreferences(
            homeListAnimation = "Helix",
            conversationListAnimation = "Slide In",
        )
        val restored = NavigationEffectPreferences.fromMap(prefs.toMap())
        assertEquals("Helix", restored.homeListAnimation)
        assertEquals("Slide In", restored.conversationListAnimation)
    }

    @Test fun `effects fromMap defaults missing list animation keys to Standard`() {
        val restored = NavigationEffectPreferences.fromMap(emptyMap())
        assertEquals("Standard", restored.homeListAnimation)
        assertEquals("Standard", restored.conversationListAnimation)
    }

    @Test fun `pager styles keep Default first, the existing five and the thirteen reference names`() {
        val styles = HomeUiStyles.pager3dStyles
        // GB key_pager_animation 0 = default (plain pager, identity); the corrected app default.
        assertEquals("Default", styles.first())
        // Existing Chaty transitions preserved, unmodified, in original order after Default.
        assertEquals(
            listOf("Cube 3D", "Accordion Fold", "Card Flip", "Depth Zoom", "Stack Rotate"),
            styles.drop(1).take(5),
        )
        // Reference transformer names (mapping 0..13 excluding the default).
        val expectedAdditions = listOf(
            "Accordion", "Background To Foreground", "Cube In", "Depth Page", "Draw From Back",
            "Flip Horizontal", "Flip Vertical", "Foreground To Background", "Tablet",
            "Rotate Down", "Rotate Up", "Stack", "Zoom Out Slide",
        )
        assertTrue(styles.containsAll(expectedAdditions))
        assertEquals(1 + 5 + 13, styles.size)
    }
}
