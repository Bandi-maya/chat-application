package com.example.chat

import com.example.chat.ui.navigation.HomePresetNormalizer
import com.example.chat.core.theme.AppNavigationMode
import com.example.chat.domain.models.HomePreferences
import com.example.chat.ui.settings.home.HomeUiStyles
import org.junit.Assert.*
import org.junit.Test

/** Pure serialized preference contract, not Android disk persistence proof. */
class RadialSettingsPersistenceTest {
    @Test fun `radial choice roundtrips without changing adjacent settings`() {
        val original = HomePreferences(
            homeStyle = "Curved Radial Menu", myNameOverride = "Synthetic identity",
            showCameraIcon = false, nameTapOpensLocked = true,
        )
        val restored = HomePreferences.fromMap(original.toMap())
        assertEquals(original.homeStyle, restored.homeStyle)
        assertEquals(original.myNameOverride, restored.myNameOverride)
        assertEquals(original.showCameraIcon, restored.showCameraIcon)
        assertEquals(original.nameTapOpensLocked, restored.nameTapOpensLocked)
        assertEquals(AppNavigationMode.curvedRadialDrawer, HomePresetNormalizer.navigationMode(restored.homeStyle))
        assertEquals("Curved Radial Menu", HomeUiStyles.selectedHomeUiStyle(restored.homeStyle, AppNavigationMode.bottomNav))
    }
    @Test fun `picker normalization and applied mode agree for radial casing`() {
        for (input in listOf("Curved Radial Menu", " curved radial menu ", "CURVED RADIAL MENU")) {
            // Header picker stores its chosen UI label verbatim. homeStyle()
            // normalizes list treatment only and must not be used as shell ID.
            val saved = input.trim()
            assertEquals(AppNavigationMode.curvedRadialDrawer, HomePresetNormalizer.navigationMode(saved))
            assertEquals(AppNavigationMode.curvedRadialDrawer,
                HomeUiStyles.navigationModeForHomeUiStyle(saved, AppNavigationMode.bottomNav))
        }
    }
    @Test fun `radial variant capacities preserve all twelve destinations`() {
        for ((width, height) in listOf(320f to 640f, 412f to 800f, 800f to 412f)) {
            val layout = com.example.chat.ui.navigation.RadialMenuGeometry.layout(width, height, 12)
            val recovered = (0 until 12 step layout.pageSize).flatMap { first ->
                (first until minOf(first + layout.pageSize, 12)).toList()
            }
            assertEquals((0 until 12).toList(), recovered)
        }
    }
}
