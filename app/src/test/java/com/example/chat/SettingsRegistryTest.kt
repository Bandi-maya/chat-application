package com.example.chat

import com.example.chat.ui.settings.registry.SettingsCategory
import com.example.chat.ui.settings.registry.SettingsRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRegistryTest {

    @Test
    fun registryContainsUniqueValidatedSettingContracts() {
        assertTrue(SettingsRegistry.validateInvariants())
        assertEquals(41, SettingsRegistry.allSettings.size)
        assertEquals(20, SettingsRegistry.clusters.size)
        assertEquals("account_display_name", SettingsRegistry.allSettings.first().id)
        assertEquals(13, SettingsCategory.values().size)
        assertEquals(SettingsCategory.values().size, SettingsCategory.values().map { it.id }.distinct().size)
        assertTrue(SettingsRegistry.allSettings.all { it.canonicalRoute.matches(Regex("^/settings/[a-z0-9_-]+(/[a-z0-9_-]+)*$")) })
    }

    @Test
    fun everyInteractiveControlHasAValidDefaultType() {
        val invalid = SettingsRegistry.allSettings.filter { setting ->
            when (setting.controlType) {
                com.example.chat.ui.settings.registry.SettingControlType.toggle -> setting.defaultValue !is Boolean
                com.example.chat.ui.settings.registry.SettingControlType.singleChoice -> setting.defaultValue !is String
                com.example.chat.ui.settings.registry.SettingControlType.slider -> setting.defaultValue !is Number
                else -> false
            }
        }
        assertTrue("Invalid settings contracts: ${invalid.map { it.id }}", invalid.isEmpty())
    }
}
