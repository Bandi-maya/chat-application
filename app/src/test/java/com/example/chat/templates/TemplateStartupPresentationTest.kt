package com.example.chat.templates

import com.example.chat.core.templates.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Tests the actual controller write boundary, not a duplicate presentation policy. */
class TemplateStartupPresentationTest {
    private class Store(val state: Map<String, Any?> = emptyMap()) : TemplateStateStore {
        override suspend fun load() = state
        override suspend fun save(data: Map<String, Any?>) = true
    }
    private class Sink(var selectedStyle: String, var selectedNavigation: String) : TemplateRuntimeSink {
        var homeWrites = 0
        var navigationWrites = 0
        override fun applyNavigationLayout(layout: NavigationLayoutType) { navigationWrites++; selectedNavigation = layout.name }
        override fun applyConversationTheme(conversation: ConversationTemplate) = Unit
        override suspend fun applyBottomBarStyle(name: String) { navigationWrites++ }
        override fun applyHomePreferences(home: HomeTemplate) { homeWrites++; selectedStyle = home.homeStylePreset }
        override fun applyConversationPreferences(conversation: ConversationTemplate) = Unit
    }
    @Test fun referenceGeometryUsesOneHeaderEnvelope() {
        val metrics = com.example.chat.ui.navigation.ReferenceLayoutMetrics
        assertEquals(250, metrics.expandedHeaderHeightDp)
        assertEquals(metrics.expandedHeaderHeightDp, metrics.expandedIdentityHeightDp + metrics.actionBarHeightDp)
        assertEquals(38, metrics.identityIconSizeDp)
        assertEquals(30, metrics.identityTextSizeSp)
    }
    @Test fun processRestoreKeepsEverySelectedStyleAndNavigation() = runBlocking {
        for (style in listOf("Chaty Default", "WhatsApp OLD UI", "IOS STYLE", "Curved Radial Menu")) {
            val sink = Sink(style, "curvedRadial")
            val controller = TemplateController(Store(mapOf("base" to "community")))
            controller.init(TemplateRuntime(sink, sink, sink), restorePresentation = false)
            assertEquals(ChatyTemplateId.community, controller.baseTemplate)
            assertEquals(style, sink.selectedStyle)
            assertEquals("curvedRadial", sink.selectedNavigation)
            assertEquals(0, sink.homeWrites)
            assertEquals(0, sink.navigationWrites)
        }
    }
    @Test fun explicitFullTemplateApplyStillChangesPresentationAfterRestore() = runBlocking {
        val sink = Sink("IOS STYLE", "iosStyle")
        val controller = TemplateController(Store())
        val runtime = TemplateRuntime(sink, sink, sink)
        controller.init(runtime, restorePresentation = false)
        controller.applyFullTemplate(ChatyTemplateId.community, runtime)
        assertEquals(controller.home.homeStylePreset, sink.selectedStyle)
        assertEquals(1, sink.homeWrites)
        assertEquals(2, sink.navigationWrites)
    }
    @Test fun compatibilityInitializationRetainsExplicitRestoreBehavior() = runBlocking {
        val sink = Sink("IOS STYLE", "iosStyle")
        val controller = TemplateController(Store())
        controller.init(TemplateRuntime(sink, sink, sink))
        assertEquals(controller.home.homeStylePreset, sink.selectedStyle)
        assertEquals(1, sink.homeWrites)
        assertEquals(2, sink.navigationWrites)
    }
}
