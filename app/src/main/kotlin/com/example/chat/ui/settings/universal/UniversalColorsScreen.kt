package com.example.chat.ui.settings.universal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.kit.rememberGbPickers
import com.example.chat.ui.settings.rememberPreferencesVersion
import com.example.chat.ui.theme.LocalThemeConfig

/** Port of lib/features/settings/universal/universal_colors_screen.dart: six
 *  colour rows, each opening the GB colour picker modal. Dart _pickColor
 *  seeds the picker with the stored colour or the theme accent when unset;
 *  the picked value is written back through updateUniversal.
 *
 *  UNCOMPILED (Compose) - CI is the compiler check. */
@Composable
fun UniversalColorsScreen(
    preferences: ChatyPreferencesController,
    onBack: () -> Unit,
) {
    rememberPreferencesVersion(preferences)
    val config = LocalThemeConfig.current
    val pickers = rememberGbPickers()
    val prefs = preferences.universal

    // Dart _pickColor: initial = currentColor != null ? Color(currentColor) : theme.accentColor.
    fun pickColor(title: String, current: Long?, onColorSelected: (Long) -> Unit) {
        pickers.showColor(title = title, current = current ?: config.accentColor) { v ->
            onColorSelected(v)
        }
    }

    UniversalScaffold(title = "Colors", onBack = onBack) {
        GbCardContainer {
            GbSettingRow(
                icon = Icons.Rounded.Tv,
                title = "Universal Color",
                subtitle = "Changes Color everywhere, including UI",
                colorValue = prefs.universalColor,
                onColorTap = {
                    pickColor("Universal Color", prefs.universalColor) { v ->
                        preferences.updateUniversal(prefs.copy(universalColor = v))
                    }
                },
            )
            GbSettingRow(
                icon = Icons.Rounded.TextFields,
                title = "Universal ActionBar Text Color",
                subtitle = "Changes the color of the Actionbar on all screens",
                colorValue = prefs.universalActionBarTextColor,
                onColorTap = {
                    pickColor("ActionBar Text Color", prefs.universalActionBarTextColor) { v ->
                        preferences.updateUniversal(prefs.copy(universalActionBarTextColor = v))
                    }
                },
            )
            GbSettingRow(
                icon = Icons.Rounded.FormatColorFill,
                title = "Background",
                subtitle = "Change Color of Background. Default is White/Theme",
                colorValue = prefs.backgroundColor,
                onColorTap = {
                    pickColor("Background Color", prefs.backgroundColor) { v ->
                        preferences.updateUniversal(prefs.copy(backgroundColor = v))
                    }
                },
            )
            GbSettingRow(
                icon = Icons.Rounded.DashboardCustomize,
                title = "List Background (One UI ONLY)",
                subtitle = "Change Background of list Chats in One UI.",
                colorValue = prefs.listBackgroundColor,
                onColorTap = {
                    pickColor("List Background Color", prefs.listBackgroundColor) { v ->
                        preferences.updateUniversal(prefs.copy(listBackgroundColor = v))
                    }
                },
            )
            GbSettingRow(
                icon = Icons.Rounded.PhoneAndroid,
                title = "Status Bar",
                subtitle = "Change Color of StatusBar on Every Screen",
                colorValue = prefs.statusBarColor,
                onColorTap = {
                    pickColor("Status Bar Color", prefs.statusBarColor) { v ->
                        preferences.updateUniversal(prefs.copy(statusBarColor = v))
                    }
                },
            )
            GbSettingRow(
                icon = Icons.Rounded.Navigation,
                title = "Navigation Bar",
                subtitle = "Change Color of NavigationBar on Every Screen",
                colorValue = prefs.navigationBarColor,
                onColorTap = {
                    pickColor("Navigation Bar Color", prefs.navigationBarColor) { v ->
                        preferences.updateUniversal(prefs.copy(navigationBarColor = v))
                    }
                },
            )
        }
    }

    pickers.Host()
}
