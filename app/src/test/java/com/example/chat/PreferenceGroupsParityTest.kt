package com.example.chat

import com.example.chat.domain.models.ConversationPreferences
import com.example.chat.domain.models.HomePreferences
import com.example.chat.domain.models.NotificationToastPreferences
import com.example.chat.domain.models.PrivacyPreferences
import com.example.chat.domain.models.StatusPreferences
import com.example.chat.domain.models.UniversalPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior tests written from the Dart models' observable fromMap/toMap
 *  behavior in lib/domain/models/preferences.dart. */
class PreferenceGroupsParityTest {

    // ── ConversationPreferences legacy fallbacks ─────────────────────────

    @Test fun `bubbleStyle falls back through bubbleShape then conversation_style_row`() {
        assertEquals("Modern", ConversationPreferences.fromMap(mapOf("bubbleShape" to "Modern")).bubbleStyle)
        assertEquals("IOS", ConversationPreferences.fromMap(mapOf("conversation_style_row" to "IOS")).bubbleStyle)
        assertEquals("Stock", ConversationPreferences.fromMap(emptyMap()).bubbleStyle)
        // canonical key wins over legacy keys
        assertEquals("Stock", ConversationPreferences.fromMap(
            mapOf("bubbleStyle" to "Stock", "bubbleShape" to "Modern")).bubbleStyle)
    }

    @Test fun `legacy abu_saleh quickcontact keys drive sidebar enable and position`() {
        val left = ConversationPreferences.fromMap(mapOf("abu_saleh_quickcontact" to "Left"))
        assertTrue(left.enableQuickContactSidebar)
        assertEquals("Left", left.sidebarPosition)
        val off = ConversationPreferences.fromMap(mapOf("abu_saleh_quickcontact" to "Off"))
        assertFalse(off.enableQuickContactSidebar)
        assertEquals("Right", off.sidebarPosition)
        val unset = ConversationPreferences.fromMap(emptyMap())
        assertFalse(unset.enableQuickContactSidebar)
    }

    @Test fun `legacy abu_saleh quickcontact colors and position key`() {
        val p = ConversationPreferences.fromMap(mapOf(
            "abu_saleh_quickcontact_pos" to "Bottom",
            "abu_saleh_quickcontact_bg" to 0xFF112233L,
            "abu_saleh_quickcontact_txt" to 0xFF445566L,
        ))
        assertEquals("Bottom", p.quickContactSidebarPosition)
        assertEquals(0xFF112233L, p.quickContactBgColor)
        assertEquals(0xFF445566L, p.quickContactTextColor)
        // defaults when nothing set
        val d = ConversationPreferences.fromMap(emptyMap())
        assertEquals("Top", d.quickContactSidebarPosition)
        assertEquals(0xFF000000L, d.quickContactBgColor)
    }

    @Test fun `messageTextSize clamps to 10 to 30`() {
        assertEquals(10.0, ConversationPreferences.fromMap(mapOf("messageTextSize" to 5.0)).messageTextSize, 0.0)
        assertEquals(30.0, ConversationPreferences.fromMap(mapOf("messageTextSize" to 99.0)).messageTextSize, 0.0)
        assertEquals(16.0, ConversationPreferences.fromMap(emptyMap()).messageTextSize, 0.0)
    }

    @Test fun `dual-key color fallbacks resolve secondary keys`() {
        val p = ConversationPreferences.fromMap(mapOf(
            "contactStatusBackgroundColor" to 7L,
            "entryUiBackgroundColor" to 8L,
            "entryMicSendBackgroundCircle" to 9L,
            "infoBalloonsBackgroundColor" to 10L,
            "voiceNotePlayingBar" to 11L,
        ))
        assertEquals(7L, p.contactStatusBgColor)
        assertEquals(8L, p.uiEntryBackgroundColor)
        assertEquals(9L, p.micSendBgCircleColor)
        assertEquals(10L, p.infoBalloonsBgColor)
        assertEquals(11L, p.voiceNotePlayingBarColor)
        assertNull(p.emojiButtonColor)
    }

    // ── HomePreferences placeholder-name coercion ────────────────────────

    @Test fun `placeholder names coerce to empty, real names preserved`() {
        assertEquals("", HomePreferences.fromMap(mapOf("myNameOverride" to "Alex Rivera")).myNameOverride)
        assertEquals("", HomePreferences.fromMap(mapOf("myNameOverride" to "Bandi Maya")).myNameOverride)
        assertEquals("Bandi", HomePreferences.fromMap(mapOf("myNameOverride" to "Bandi")).myNameOverride)
        assertEquals("", HomePreferences.fromMap(emptyMap()).myNameOverride)
    }

    // ── StatusPreferences dual keys ──────────────────────────────────────

    @Test fun `status colors and sound flag fall back to camelCase variants`() {
        val p = StatusPreferences.fromMap(mapOf(
            "statusUnseenColor" to 42L,
            "startStoriesWithSound" to true,
        ))
        assertEquals(42L, p.statusUnSeenColor)
        assertEquals(42L, p.statusUnseenColor) // alias getter
        assertTrue(p.startStoriesDirectlyWithSound)
        assertTrue(p.startStoriesWithSound) // alias getter
        // canonical key wins
        val q = StatusPreferences.fromMap(mapOf("statusUnSeenColor" to 1L, "statusUnseenColor" to 2L))
        assertEquals(1L, q.statusUnSeenColor)
    }

    // ── Toast aliases ────────────────────────────────────────────────────

    @Test fun `toast alias getters mirror Dart`() {
        val t = NotificationToastPreferences.fromMap(mapOf(
            "onlineToastEnabled" to true,
            "storyToastEnabled" to true,
        ))
        assertTrue(t.onlineToast)
        assertTrue(t.viewedStoryToast)
        assertFalse(t.disableContactOnlineToast)
        assertEquals(8.0, t.onlineRadius, 0.0)
        assertEquals("Top of the screen", t.storyPosition)
    }

    // ── Roundtrip + Privacy asymmetry ────────────────────────────────────

    @Test fun `default instances roundtrip through toMap and fromMap`() {
        assertEquals(ConversationPreferences(), ConversationPreferences.fromMap(ConversationPreferences().toMap()))
        assertEquals(HomePreferences(), HomePreferences.fromMap(HomePreferences().toMap()))
        assertEquals(StatusPreferences(), StatusPreferences.fromMap(StatusPreferences().toMap()))
        assertEquals(NotificationToastPreferences(), NotificationToastPreferences.fromMap(NotificationToastPreferences().toMap()))
        assertEquals(UniversalPreferences(), UniversalPreferences.fromMap(UniversalPreferences().toMap()))
    }

    @Test fun `privacy toMap omits antiDisappearingMessages and hideOnlineStatus like Dart`() {
        val m = PrivacyPreferences().toMap()
        assertFalse(m.containsKey("antiDisappearingMessages"))
        assertFalse(m.containsKey("hideOnlineStatus"))
    }
}
