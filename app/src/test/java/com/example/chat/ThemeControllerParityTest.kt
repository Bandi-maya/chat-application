package com.example.chat

import com.example.chat.core.theme.AppNavigationMode
import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.ThemeController
import com.example.chat.core.theme.ThemePresets
import com.example.chat.core.theme.ThemeStateStore
import com.example.chat.core.theme.UILayoutMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeControllerParityTest {

    private class FakeStore(var initial: Map<String, Any?> = emptyMap()) : ThemeStateStore {
        val saves = mutableListOf<Map<String, Any?>>()
        var failOnLoad = false
        override suspend fun loadThemeState(): Map<String, Any?> {
            if (failOnLoad) throw RuntimeException("boom")
            return initial
        }
        override suspend fun saveThemeState(data: Map<String, Any?>): Boolean {
            saves.add(data)
            return true
        }
    }

    private fun controller(store: FakeStore): ThemeController =
        ThemeController(store, CoroutineScope(Dispatchers.Unconfined))

    private fun ThemeController.initSync() = runBlocking { init() }

    @Test
    fun presetsRegistryMatchesDartOrderAndFallback() {
        assertEquals(23, ThemePresets.all.size)
        assertEquals("whatsapp_ios_light", ThemePresets.all[0].id)
        assertEquals("whatsapp_ios_dark", ThemePresets.all[1].id)
        assertEquals("violet_night", ThemePresets.all[22].id)
        assertEquals("monochrome_dark", ThemePresets.getById("does-not-exist").id)
        assertEquals("ocean", ThemePresets.getById("ocean").id)
        // system default pair
        assertEquals("warm_neutral", ThemePresets.getSystemDefaultTheme(true).id)
        assertEquals("warm_neutral_dark", ThemePresets.getSystemDefaultTheme(false).id)
        // whatsapp identity presets carry rcIos11 bubble + tick styles
        assertEquals(BubbleStyleId.rcIos11, ThemePresets.whatsappIosLight.bubbleStyle)
        assertEquals("none", ThemePresets.whatsappIosDark.wallpaperId)
        // high contrast preset
        assertTrue(ThemePresets.highContrast.highContrast)
        assertEquals(1.1, ThemePresets.highContrast.density, 0.0)
        assertEquals(0xFFFFFF00L, ThemePresets.highContrast.accentColor)
    }

    @Test
    fun freshInitKeepsSystemDefaultAndDoesNotPersist() {
        val store = FakeStore()
        val c = controller(store)
        c.initSync()
        assertEquals("warm_neutral", c.globalTheme.id) // provider defaults to light
        assertTrue(c.followSystem)
        assertTrue(store.saves.isEmpty())
    }

    @Test
    fun corruptStateKeepsDefault() {
        val store = FakeStore().apply { failOnLoad = true }
        val c = controller(store)
        c.initSync()
        assertEquals("warm_neutral", c.globalTheme.id)
    }

    @Test
    fun setGlobalThemePreservesDisplayFieldsAndPersists() {
        val store = FakeStore()
        val c = controller(store)
        c.initSync()
        c.setLayoutMode(UILayoutMode.compact)
        c.setNavigationMode(AppNavigationMode.iosStyle)
        c.setFontScale(1.3)
        c.setDensity(0.9)
        val savesBefore = store.saves.size
        c.setGlobalTheme(ThemePresets.ocean)
        assertEquals("ocean", c.globalTheme.id)
        // standalone display fields win over the preset's own values
        assertEquals(UILayoutMode.compact, c.globalTheme.layoutMode)
        assertEquals(AppNavigationMode.iosStyle, c.globalTheme.navigationMode)
        assertEquals(1.3, c.globalTheme.fontScale, 0.0)
        assertEquals(0.9, c.globalTheme.density, 0.0)
        assertFalse(c.followSystem) // ocean is not a default id
        assertTrue(store.saves.size > savesBefore)
        @Suppress("UNCHECKED_CAST")
        val saved = store.saves.last()["globalTheme"] as Map<String, Any?>
        assertEquals("ocean", saved["id"])
        assertEquals(2, store.saves.last()["schemaVersion"])
    }

    @Test
    fun setGlobalThemeWithFollowSystemOnlySticksForDefaultIds() {
        val store = FakeStore()
        val c = controller(store)
        c.initSync()
        c.setGlobalTheme(ThemePresets.warmNeutralDark, followSystem = true)
        assertTrue(c.followSystem)
        c.setGlobalTheme(ThemePresets.midnight, followSystem = true)
        assertFalse(c.followSystem) // not a default id -> follow flag cleared
    }

    @Test
    fun syncWithSystemNoopsWhenNotFollowing() {
        val store = FakeStore()
        val c = controller(store)
        c.initSync()
        c.setGlobalTheme(ThemePresets.midnight)
        c.syncWithSystem(true)
        assertEquals("midnight", c.globalTheme.id)
    }

    @Test
    fun toggleBrightnessSwapsDefaultPairAndCustomThemesInvert() {
        val store = FakeStore()
        val c = controller(store)
        c.initSync()
        // default light -> dark preset
        assertEquals("warm_neutral", c.globalTheme.id)
        c.toggleBrightness()
        assertEquals("warm_neutral_dark", c.globalTheme.id)
        assertFalse(c.followSystem)
        c.toggleBrightness()
        assertEquals("warm_neutral", c.globalTheme.id)
        // registered preset with dark/light counterpart resolves by id
        c.setGlobalTheme(ThemePresets.monochromeDark)
        c.toggleBrightness()
        assertEquals("monochrome_light", c.globalTheme.id)
        // custom theme without counterpart inverts dynamically
        c.setGlobalTheme(ThemePresets.ocean)
        val before = c.globalTheme
        c.toggleBrightness()
        assertFalse(c.globalTheme.isDark)
        assertNotEquals(before.backgroundColor, c.globalTheme.backgroundColor)
        assertEquals("ocean", c.globalTheme.id)
    }

    @Test
    fun updateThemeConfigAbsorbsDisplayFields() {
        val store = FakeStore()
        val c = controller(store)
        c.initSync()
        val custom = ThemePresets.paper.copyWith(
            layoutMode = UILayoutMode.focus,
            navigationMode = AppNavigationMode.gestureTabs,
            fontScale = 1.4,
            density = 0.8,
            animationLevel = 0.0,
        )
        c.updateThemeConfig(custom)
        assertEquals(UILayoutMode.focus, c.layoutMode)
        assertEquals(AppNavigationMode.gestureTabs, c.navigationMode)
        assertEquals(1.4, c.fontScale, 0.0)
        assertEquals(0.8, c.density, 0.0)
        assertTrue(c.useReducedMotion) // animationLevel <= 0
        assertFalse(c.followSystem)
    }

    @Test
    fun perChatThemesOverrideAndClear() {
        val store = FakeStore()
        val c = controller(store)
        c.initSync()
        assertEquals(c.globalTheme, c.getThemeForChat("chat1"))
        c.setChatTheme("chat1", ThemePresets.rose)
        assertEquals("rose", c.getThemeForChat("chat1").id)
        assertNotEquals("rose", c.getThemeForChat("chat2").id)
        c.setChatTheme("chat1", null)
        assertEquals(c.globalTheme, c.getThemeForChat("chat1"))
    }

    @Test
    fun setReducedMotionZeroesAnimationLevel() {
        val store = FakeStore()
        val c = controller(store)
        c.initSync()
        c.setReducedMotion(true)
        assertTrue(c.useReducedMotion)
        assertEquals(0.0, c.globalTheme.animationLevel, 0.0)
        c.setReducedMotion(false)
        assertEquals(1.0, c.globalTheme.animationLevel, 0.0)
    }

    @Test
    fun stateRoundTripsThroughInit() {
        val store = FakeStore()
        val c1 = controller(store)
        c1.initSync()
        c1.setGlobalTheme(ThemePresets.coffee)
        c1.setFontScale(1.2)
        c1.setChatTheme("chat9", ThemePresets.lavender)
        c1.setReducedMotion(true)
        val saved = store.saves.last()

        val store2 = FakeStore(saved)
        val c2 = controller(store2)
        c2.initSync()
        assertEquals("coffee", c2.globalTheme.id)
        assertEquals(1.2, c2.fontScale, 0.0)
        assertEquals("lavender", c2.getThemeForChat("chat9").id)
        assertTrue(c2.useReducedMotion)
        assertEquals(0.0, c2.globalTheme.animationLevel, 0.0)
        assertFalse(c2.followSystem)
    }

    @Test
    fun legacyDefaultIdMigratesToFollowSystem() {
        // state with no followSystem key and a default-id theme -> follows system
        val legacy = mapOf<String, Any?>(
            "globalTheme" to ThemePresets.warmNeutralDark.toMap(),
        )
        val c = controller(FakeStore(legacy))
        c.initSync()
        assertTrue(c.followSystem)
        // followSystem re-applies the system default (light provider) theme
        assertEquals("warm_neutral", c.globalTheme.id)
    }

    @Test
    fun nonDefaultIdWithoutFollowKeyDoesNotFollow() {
        val legacy = mapOf<String, Any?>(
            "globalTheme" to ThemePresets.forest.toMap(),
        )
        val c = controller(FakeStore(legacy))
        c.initSync()
        assertFalse(c.followSystem)
        assertEquals("forest", c.globalTheme.id)
    }

    @Test
    fun corruptPerChatEntrySkipped() {
        val state = mapOf<String, Any?>(
            "followSystem" to false,
            "globalTheme" to ThemePresets.graphite.toMap(),
            "perChatThemes" to mapOf<String, Any?>(
                "good" to ThemePresets.arctic.toMap(),
                "bad" to mapOf("id" to 42, "accentColor" to emptyMap<String, Any?>()),
                "junk" to "not-a-map",
            ),
        )
        val c = controller(FakeStore(state))
        c.initSync()
        assertEquals("graphite", c.globalTheme.id)
        assertEquals("arctic", c.getThemeForChat("good").id)
        // corrupt entry decodes via fallback-tolerant fromMap (never throws)
        assertEquals(c.globalTheme, c.getThemeForChat("junk"))
    }

    @Test
    fun resetToDefaultsRestoresEverything() {
        val store = FakeStore()
        val c = controller(store)
        c.initSync()
        c.setGlobalTheme(ThemePresets.burgundy)
        c.setLayoutMode(UILayoutMode.focus)
        c.setChatTheme("c", ThemePresets.sage)
        c.setReducedMotion(true)
        c.resetToDefaults()
        assertEquals("warm_neutral", c.globalTheme.id)
        assertTrue(c.followSystem)
        assertEquals(UILayoutMode.classic, c.layoutMode)
        assertEquals(AppNavigationMode.bottomNav, c.navigationMode)
        assertFalse(c.useReducedMotion)
        assertEquals(1.0, c.fontScale, 0.0)
        assertEquals(c.globalTheme, c.getThemeForChat("c"))
    }

    @Test
    fun listenersFireOnMutationsAndNotOnInit() {
        val store = FakeStore()
        val c = controller(store)
        var count = 0
        c.addListener { count++ }
        c.initSync()
        assertEquals(0, count)
        c.setFontScale(1.1)
        assertEquals(1, count)
        c.setDensity(0.95)
        assertEquals(2, count)
    }
}
