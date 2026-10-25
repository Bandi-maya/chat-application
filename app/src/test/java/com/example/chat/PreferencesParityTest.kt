package com.example.chat

import com.example.chat.core.preferences.GbFeatureCatalog
import com.example.chat.core.preferences.GbFeatureKind
import com.example.chat.core.preferences.PreferenceKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesParityTest {

    // ── preference_keys.dart ─────────────────────────────────────────────

    @Test fun `scoped preferences key namespaces per user`() {
        assertEquals("chaty_preferences_v1.u1", PreferenceKeys.scopedPreferences("u1"))
        assertEquals(2, PreferenceKeys.currentSchemaVersion)
        assertEquals(10, PreferenceKeys.groupKeys.size)
    }

    // ── gb_feature_catalog.dart ──────────────────────────────────────────

    @Test fun `gb defaults resolve exactly like the Dart generator`() {
        val d = GbFeatureCatalog.defaults
        assertEquals(true, d["anti_vw_once"])
        assertEquals(true, d["Conv_call_btn"])
        assertEquals(true, d["statuschat"])
        assertEquals(false, d["yoHideSeen"])
        assertEquals(false, d["yo_want_ghostmode"])
        assertEquals(30.0, d["Img_share_limit"])
        assertEquals(50.0, d["Up_size_limit"])
        assertEquals(50.0, d["abo_saleh_audio_limit_check"])
        assertEquals(15.0, d["text_size_pick"])
        assertEquals(85.0, d["Img_highres_seek"])
        assertEquals("✨", d["tap_emoji"])
        assertEquals("Chaty", d["ui_home_styleV3"])
        assertEquals("Everyone", d["yoCallsPrivacy"])
        assertEquals(0, d["onlineDotchatColor"])
        assertEquals(0, d["BGColor"])
    }

    @Test fun `gb slider max values match the Dart table`() {
        assertEquals(30.0, GbFeatureCatalog.byKey("text_size_pick")!!.max, 0.0)
        assertEquals(100.0, GbFeatureCatalog.byKey("Img_share_limit")!!.max, 0.0)
        assertEquals(50.0, GbFeatureCatalog.byKey("Up_size_limit")!!.max, 0.0)
        assertEquals(100.0, GbFeatureCatalog.byKey("Img_highres_seek")!!.max, 0.0)
    }

    @Test fun `kind inference follows the Dart heuristics`() {
        assertEquals(GbFeatureKind.choice, GbFeatureCatalog.byKey("tap_emoji")!!.kind)
        assertEquals(GbFeatureKind.slider, GbFeatureCatalog.byKey("Up_size_limit")!!.kind)
        assertEquals(GbFeatureKind.color, GbFeatureCatalog.byKey("ModChatColor")!!.kind)
        assertEquals(GbFeatureKind.color, GbFeatureCatalog.byKey("abu_saleh_toast_online_bc")!!.kind)
        assertEquals(GbFeatureKind.toggle, GbFeatureCatalog.byKey("yoHideSeen")!!.kind)
        assertEquals(GbFeatureKind.toggle, GbFeatureCatalog.byKey("always_online")!!.kind)
    }

    @Test fun `humanize matches the Dart title transform`() {
        assertEquals("Hide Seen", GbFeatureCatalog.byKey("yoHideSeen")!!.title)
        assertEquals("Home Counter Background", GbFeatureCatalog.byKey("HomeCounterBK")!!.title)
    }

    @Test fun `duplicate keys across categories appear once, first category wins`() {
        val defs = GbFeatureCatalog.all.filter { it.key == "home_stories_key" }
        assertEquals(1, defs.size)
        assertEquals("Home header", defs[0].category)
        assertEquals(GbFeatureCatalog.all.size, GbFeatureCatalog.defaults.size)
        assertTrue(GbFeatureCatalog.all.isNotEmpty())
    }
}
