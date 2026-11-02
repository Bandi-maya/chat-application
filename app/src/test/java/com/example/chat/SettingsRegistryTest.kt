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
        assertEquals(39, SettingsRegistry.allSettings.size)
        assertEquals(18, SettingsRegistry.clusters.size)
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
    /** User removed both centers. Freeze every remaining setting and its real AppRoot search destination. */
    @Test fun removedCentersStayAbsentAndOtherSettingsStayRoutable() {
        val expectedIds = setOf(
            "account_display_name",
            "account_username",
            "account_bio",
            "privacy_freeze_last_seen",
            "privacy_who_can_see_last_seen",
            "privacy_anti_delete_messages",
            "privacy_anti_delete_status",
            "privacy_read_receipts",
            "privacy_blue_ticks_after_reply",
            "privacy_typing_indicators",
            "security_app_lock_enabled",
            "security_lock_method",
            "security_autolock_timeout",
            "security_hide_locked_chats",
            "security_protect_screenshots",
            "chats_bubble_style",
            "chats_tick_style",
            "chats_wallpaper_type",
            "chats_swipe_to_reply",
            "chats_double_tap_emoji",
            "chats_enable_sidebar",
            "chats_enable_animated_emojis",
            "chats_auto_reply_enabled",
            "appearance_ui_template",
            "appearance_theme_preset",
            "appearance_app_icon",
            "appearance_typography_style",
            "appearance_entry_motion",
            "home_style",
            "home_enable_stories_strip",
            "home_separate_chats_groups",
            "notifications_global_enabled",
            "notifications_show_preview",
            "notifications_contact_online",
            "calls_who_can_call_me",
            "storage_high_res_images",
            "devices_qr_scan",
            "effects_touch_particles",
            "permissions_manage",
        )
        val registeredSearchRoutes = setOf(
            "/settings/privacy",
            "/settings/security",
            "/settings/conversation",
            "/settings/message_management",
            "/settings/themes",
            "/settings/templates",
            "/settings/notifications",
            "/settings/calls",
            "/settings/storage",
            "/settings/effects",
            "/settings/permissions",
            "/settings/home",
            "/settings/devices",
            "/settings/account",
            "/settings/universal_appearance",
            "/settings/customization",
            "/settings/app_icon",
        )
        assertEquals(expectedIds, SettingsRegistry.allSettings.map { it.id }.toSet())
        assertTrue(SettingsRegistry.allSettings.all { it.canonicalRoute in registeredSearchRoutes })
        assertTrue(SettingsRegistry.clusters.none { it.id in setOf("cluster_chaty_customization_hub", "cluster_advanced_feature_center") })
        assertTrue(SettingsRegistry.allSettings.none { it.canonicalRoute in setOf("/settings/chaty_customization_hub", "/settings/gb_features") })
    }
}
