package com.example.chat

import com.example.chat.data.a11y.ContentDescriptionRegistry
import com.example.chat.data.a11y.LocalizationCatalog
import com.example.chat.data.a11y.PickerFavoritesStore
import com.example.chat.data.a11y.ReduceMotionPolicy
import com.example.chat.data.a11y.TextScalePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityPoliciesTest {

    @Test fun `text scale snaps to steps and clamps combined`() {
        assertEquals(1.1f, TextScalePolicy.nearestStep(1.12f))
        assertEquals(1.6f, TextScalePolicy.combinedScale(1.3f, 1.4f))   // 1.82 clamped
        assertEquals(1.0f, TextScalePolicy.combinedScale(1.0f, 1.0f))
    }

    @Test fun `reduce motion honors app switch and system animator scale`() {
        assertEquals(0.0f, ReduceMotionPolicy.durationScale(appReduceMotion = true, systemAnimatorScale = 1f))
        assertEquals(0.0f, ReduceMotionPolicy.durationScale(appReduceMotion = false, systemAnimatorScale = 0f))
        assertEquals(1.0f, ReduceMotionPolicy.durationScale(false, 1f))
    }

    @Test fun `content description registry flags missing labels`() {
        val r = ContentDescriptionRegistry()
        r.register("send_button", "Send message")
        assertEquals("Send message", r.labelFor("send_button"))
        assertEquals(setOf("mic_button"), r.unlabeled(setOf("send_button", "mic_button")))
    }

    @Test fun `picker row is favourites then recents without dupes`() {
        val s = PickerFavoritesStore()
        s.recordUse("a"); s.recordUse("b"); s.recordUse("c")
        s.toggleFavorite("b")
        assertEquals(listOf("b", "c", "a"), s.row())
        s.toggleFavorite("b")
        assertEquals(listOf("c", "a", "b"), s.row())
    }

    @Test fun `all three locales cover every key`() {
        for (locale in listOf("en", "te", "hi")) {
            assertTrue("$locale missing: ${LocalizationCatalog.missingKeys(locale)}",
                LocalizationCatalog.missingKeys(locale).isEmpty())
        }
        assertEquals("వెతకండి", LocalizationCatalog.lookup("te", "search"))
        assertEquals("Search", LocalizationCatalog.lookup("fr", "search"))  // falls back to en
    }
}
