package com.example.chat
import com.example.chat.core.theme.AppNavigationMode
import com.example.chat.ui.navigation.HomePresetNormalizer
import com.example.chat.core.preferences.HomePresentationPolicy
import org.junit.Assert.*
import org.junit.Test

class HomeNavigationPresentationRegressionTest {
    @Test fun legacyDartResolverContractUnchanged() {
        assertEquals(AppNavigationMode.oneUi, HomePresetNormalizer.navigationMode("Cards"))
        assertEquals(AppNavigationMode.oneUi, HomePresetNormalizer.navigationMode("one ui"))
        assertEquals(AppNavigationMode.bottomNav, HomePresetNormalizer.navigationMode("Chaty Default"))
        assertEquals(AppNavigationMode.bottomNav, HomePresetNormalizer.navigationMode("WhatsApp UI Stock"))
    }
    @Test fun actualAppPresentationRetainsAuthorizedSwap() {
        for (style in listOf("Chaty Default", "chaty", "default"))
            assertEquals(AppNavigationMode.oneUi, HomePresetNormalizer.presentationNavigationMode(style))
        for (style in listOf("ONE UI", "Cards", "one ui"))
            assertEquals(AppNavigationMode.bottomNav, HomePresetNormalizer.presentationNavigationMode(style))
        assertEquals(AppNavigationMode.bottomNav, HomePresetNormalizer.presentationNavigationMode("WhatsApp UI Stock"))
    }
    @Test fun allOtherLegacyCasesAndUnknownRemainTheSame() {
        val labels = listOf("Classic", "whatsapp-style top bar", "Stories First", "Instagram Style", "Telegram Style",
            "Floating Rail", "Modern Side Menu", "Compact", "Productivity", "3D Perspective Drawer", "Curved Radial Menu")
        labels.forEach { assertEquals(it, HomePresetNormalizer.navigationMode(it), HomePresetNormalizer.presentationNavigationMode(it)) }
        assertNull(HomePresetNormalizer.navigationMode("Some Custom Preset"))
        assertNull(HomePresetNormalizer.presentationNavigationMode("Some Custom Preset"))
    }
    @Test fun savedIdsAreNotMigratedByEitherResolver() {
        listOf("Chaty Default", "ONE UI", "WhatsApp UI Stock", "IOS STYLE").forEach {
            assertEquals(it, HomePresentationPolicy.storedStyle(it))
            HomePresetNormalizer.navigationMode(it)
            HomePresetNormalizer.presentationNavigationMode(it)
            assertEquals(it, HomePresentationPolicy.storedStyle(it))
        }
    }
}
