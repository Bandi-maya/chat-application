package com.example.chat.customization

import com.example.chat.core.customization.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Port of test/customization_subsystem_test.dart (api 3bee780d) plus resolver/encode edge cases derived from the Dart source. */
class CustomizationSubsystemParityTest {

    private class MemStore : CustomizationStateStore {
        val data = mutableMapOf<String, String>()
        override suspend fun getString(key: String) = data[key]
        override suspend fun setString(key: String, value: String) { data[key] = value }
    }

    @Test fun registryContainsCatalogedVariants() {
        val r = ChatyComponentRegistry.instance
        assertTrue(r.getVariantsFor(ComponentId.navigation).size >= 8)
        assertTrue(r.getVariantsFor(ComponentId.header).size >= 6)
        assertTrue(r.getVariantsFor(ComponentId.composer).size >= 6)
        assertTrue(r.getVariantsFor(ComponentId.bubble).size >= 6)
        assertTrue(r.getVariantsFor(ComponentId.tick).size >= 4)
        assertTrue(r.getVariantsFor(ComponentId.modal).size >= 4)
    }

    @Test fun catalogMatchesDartRegisterCount() {
        val r = ChatyComponentRegistry.instance
        assertEquals(51, ComponentId.values().sumOf { r.getVariantsFor(it).size })
        assertEquals("classic_label_bar", r.getDefaultVariantId(ComponentId.navigation))
        assertEquals("standard_identity", r.getDefaultVariantId(ComponentId.header))
        assertEquals("chaty_capsule", r.getDefaultVariantId(ComponentId.composer))
        assertEquals("center_dialog", r.getDefaultVariantId(ComponentId.modal))
    }

    @Test fun defaultSpecLookup() {
        val r = ChatyComponentRegistry.instance
        val id = r.getDefaultVariantId(ComponentId.navigation)
        assertTrue(id.isNotEmpty())
        assertEquals(ComponentId.navigation, r.getSpec(ComponentId.navigation, id)!!.componentId)
    }

    private val ctx = VariantContext(390.0, 844.0, Brightness.light)

    @Test fun resolverNavigation() {
        val spec = ComponentVariantResolver().resolve(ComponentId.navigation, CustomizationSnapshot.defaultSnapshot, ctx)
        assertEquals(ComponentId.navigation, spec.componentId)
        assertEquals("classic_label_bar", spec.variantId)
    }

    @Test fun resolverConversationOverrideWins() {
        val snap = CustomizationSnapshot.defaultSnapshot.copyWith(
            conversationOverrides = mapOf("conv_123" to mapOf(ComponentId.bubble.name to "ios_like")),
        )
        val spec = ComponentVariantResolver().resolve(ComponentId.bubble, snap, ctx.copy(brightness = Brightness.dark), ComponentScope.conversation("conv_123"))
        assertEquals("ios_like", spec.variantId)
    }

    @Test fun resolverScreenOverrideAndPreviewAndUnknownFallback() {
        val r = ComponentVariantResolver()
        val snap = CustomizationSnapshot.defaultSnapshot.copyWith(screenOverrides = mapOf("home" to mapOf("navigation" to "orbit")))
        assertEquals("orbit", r.resolve(ComponentId.navigation, snap, ctx, ComponentScope.screen("home")).variantId)
        // other screens / global ignore it
        assertEquals("classic_label_bar", r.resolve(ComponentId.navigation, snap, ctx, ComponentScope.screen("x")).variantId)
        val preview = CustomizationSnapshot.defaultSnapshot.copyWith(navigationVariant = "line_marker")
        assertEquals("line_marker", r.resolve(ComponentId.navigation, snap, ctx, ComponentScope.transientPreview, preview).variantId)
        // unknown id: first compatible variant in family (Dart nearestCompatible), not the default
        val unknown = CustomizationSnapshot.defaultSnapshot.copyWith(navigationVariant = "nope")
        assertEquals(ChatyComponentRegistry.instance.getVariantsFor(ComponentId.navigation).first().variantId,
            r.resolve(ComponentId.navigation, unknown, ctx).variantId)
        // component without a snapshot field resolves via registry default
        assertEquals(ChatyComponentRegistry.instance.getDefaultVariantId(ComponentId.toast), r.resolve(ComponentId.toast, snap, ctx).variantId)
    }

    @Test fun scopePrecedence() {
        assertTrue(ComponentScope.conversation("a").hasHigherPrecedenceThan(ComponentScope.screen("b")))
        assertTrue(ComponentScope.transientPreview.hasHigherPrecedenceThan(ComponentScope.conversation("a")))
        assertFalse(ComponentScope.defaultTheme.hasHigherPrecedenceThan(ComponentScope.globalUser))
        assertEquals(ComponentScope.conversation("a"), ComponentScope.conversation("a"))
        assertNotEquals(ComponentScope.conversation("a"), ComponentScope.screen("a"))
        assertEquals("ComponentScope(screen:home)", ComponentScope.screen("home").toString())
    }

    @Test fun snapshotJsonRoundTrip() {
        val s = CustomizationSnapshot(
            schemaVersion = 1, globalThemeId = "emerald_matrix", navigationVariant = "floating_pill_nav",
            composerVariant = "glass_float_composer",
            conversationOverrides = mapOf("conv_123" to mapOf("bubble" to "cyberpunk_neon")),
        )
        val r = CustomizationSnapshot.fromJson(s.toJson())
        assertEquals(1, r.schemaVersion)
        assertEquals("emerald_matrix", r.globalThemeId)
        assertEquals("floating_pill_nav", r.navigationVariant)
        assertEquals("glass_float_composer", r.composerVariant)
        assertEquals("cyberpunk_neon", r.conversationOverrides["conv_123"]?.get("bubble"))
        // string encode/decode round trip through org.json, plus malformed input -> null
        val d = CustomizationSnapshot.decode(s.encode())!!
        assertEquals("cyberpunk_neon", d.conversationOverrides["conv_123"]?.get("bubble"))
        assertNull(CustomizationSnapshot.decode(null)); assertNull(CustomizationSnapshot.decode("")); assertNull(CustomizationSnapshot.decode("{bad"))
        assertNull(CustomizationSnapshot.decode("{\"globalThemeId\": 5}"))
    }

    @Test fun migrationUpgradesLegacy() {
        val m = CustomizationMigration.migrate(mapOf("schemaVersion" to 0, "globalThemeId" to "chaty_emerald"))
        assertEquals(1, m.schemaVersion)
        assertEquals("chaty_emerald", m.globalThemeId)
        assertEquals("classic_label_bar", m.navigationVariant)
    }

    @Test fun historyBoundedAtTenAndUndo() {
        val h = CustomizationHistory()
        assertFalse(h.canUndo)
        val s0 = CustomizationSnapshot.defaultSnapshot
        for (i in 1..15) h.record(ComponentScope.globalUser, s0.copyWith(globalThemeId = "theme_${i - 1}"), s0.copyWith(globalThemeId = "theme_$i"))
        assertTrue(h.canUndo)
        assertEquals(10, h.items.size)
        val p = h.popUndo()!!
        assertEquals("theme_15", p.next.globalThemeId)
        assertEquals("theme_14", p.previous.globalThemeId)
        // same instance is a no-op (Dart identity equality)
        val h2 = CustomizationHistory(); h2.record(ComponentScope.globalUser, s0, s0); assertFalse(h2.canUndo)
    }

    @Test fun controllerStagingCommitUndoAndPersistence() = runBlocking {
        val store = MemStore()
        val c = CustomizationController(store)
        c.load()
        assertTrue(c.isLoaded)
        assertEquals("classic_label_bar", c.snapshot.navigationVariant)

        c.setVariant(ComponentId.navigation, "floating_pill_nav", immediate = false)
        assertTrue(c.isDirty)
        assertEquals("floating_pill_nav", c.previewSnapshot?.navigationVariant)
        assertEquals("classic_label_bar", c.snapshot.navigationVariant)

        c.cancelPreview()
        assertFalse(c.isDirty); assertNull(c.previewSnapshot)
        assertEquals("classic_label_bar", c.snapshot.navigationVariant)

        c.setVariant(ComponentId.navigation, "floating_pill_nav", immediate = true)
        assertFalse(c.isDirty)
        assertEquals("floating_pill_nav", c.snapshot.navigationVariant)
        assertTrue(store.data[CustomizationController.storageKey]!!.contains("floating_pill_nav"))

        // a fresh controller restores the committed snapshot
        val c2 = CustomizationController(store); c2.load()
        assertEquals("floating_pill_nav", c2.snapshot.navigationVariant)

        assertTrue(c.canUndo)
        c.undo()
        assertEquals("classic_label_bar", c.snapshot.navigationVariant)
        assertTrue(store.data[CustomizationController.storageKey]!!.contains("classic_label_bar"))
    }

    @Test fun controllerConversationOverridesAndResetAll() = runBlocking {
        val c = CustomizationController(MemStore()); c.load()
        c.setConversationOverride("c1", ComponentId.bubble, "ios_like")
        assertEquals("ios_like", c.snapshot.conversationOverrides["c1"]!!["bubble"])
        c.resetConversation("c1")
        assertTrue(c.snapshot.conversationOverrides.isEmpty())
        c.setVariant(ComponentId.header, "compact", immediate = true)
        c.resetAll()
        assertEquals("standard_identity", c.snapshot.headerVariant)
        assertTrue(c.canUndo)
        var n = 0; c.addListener { n++ }; c.stage(c.snapshot.copyWith(motionProfile = "calm")); assertEquals(1, n)
        // non-snapshot components are ignored by setVariant
        c.cancelPreview(); c.setVariant(ComponentId.toast, "x"); assertFalse(c.isPreviewing)
    }

    @Test fun capabilityNegotiation() {
        val spec = ComponentSpec(ComponentId.navigation, "x", "X", capabilities = ComponentCapabilities(compactWidth = false, rtl = false))
        assertFalse(spec.supports(VariantContext(390.0, 800.0, Brightness.light)))
        assertTrue(spec.supports(VariantContext(700.0, 800.0, Brightness.light)))
        assertFalse(spec.supports(VariantContext(700.0, 800.0, Brightness.light, isRtl = true)))
        assertTrue(ComponentCapabilities.universal.satisfies(ComponentCapabilities()))
        assertTrue(VariantContext(839.0, 1.0, Brightness.dark).isMedium && VariantContext(840.0, 1.0, Brightness.dark).isExpanded)
    }
}
