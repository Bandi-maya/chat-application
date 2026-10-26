package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.settings.ChatySwitch
import com.example.chat.ui.settings.home.HeaderTabStyleSheet
import com.example.chat.ui.theme.ChatyTheme

/**
 * Debug-only empty host for header selector and switch proofs. No ServiceLocator, session, preferences or
 * network: state is in memory, and the saved values are printed as "Saved: tab|pager|applyCount".
 * EXTRA_DARK selects the theme (ThemePresets takes platformIsLight, so light = !dark).
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class HeaderProofActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra(EXTRA_DARK, false)
        val screen = intent.getStringExtra(EXTRA_SCREEN) ?: SCREEN_SHEET
        val theme = ThemePresets.getSystemDefaultTheme(platformIsLight = !dark)
        setContent {
            ChatyTheme(theme) {
                Box(Modifier.fillMaxSize().background(Color(theme.surfaceColor)), contentAlignment = Alignment.Center) {
                    if (screen == SCREEN_TABS) {
                        val fs = intent.getFloatExtra(EXTRA_FONT_SCALE, 1f)
                        val w = intent.getIntExtra(EXTRA_WIDTH_DP, 320)
                        val base = androidx.compose.ui.platform.LocalDensity.current
                        var sel by remember { mutableStateOf(0) }
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, fs),
                        ) {
                            Box(Modifier.width(w.dp).semantics { testTagsAsResourceId = true }) {
                                com.example.chat.ui.navigation.HomeHeaderTabsBar(
                                    com.example.chat.ui.core.theme.ChatyColors(theme),
                                    listOf("chats", "updates", "calls", "tasks", "settings"), sel, 48f, { sel = it },
                                )
                            }
                        }
                        Text("Tab: $sel", Modifier.padding(top = 120.dp))
                        Text("External select", Modifier.padding(top = 200.dp).clickable { sel = 4 })
                    } else if (screen == SCREEN_SWITCH) {
                        var on by remember { mutableStateOf(false) }
                        var taps by remember { mutableStateOf(0) }
                        // Padding sits OUTSIDE the semantics node, so its bounds are exactly the 52x30dp switch.
                        Box(Modifier.padding(24.dp)) {
                            Box(Modifier.semantics { contentDescription = "synthetic-switch" }) {
                                ChatySwitch(on, { on = it; taps++ })
                            }
                        }
                        Text("Taps: $taps", Modifier.padding(top = 120.dp))
                    } else {
                        var open by remember { mutableStateOf(true) }
                        var tab by remember { mutableStateOf("Capsule Pill") }
                        var pager by remember { mutableStateOf("Cube 3D") }
                        var applies by remember { mutableStateOf(0) }
                        if (open) HeaderTabStyleSheet(
                            tabStyle = tab, pagerStyle = pager, onDismiss = { open = false },
                            onApply = { t, p -> tab = t; pager = p; applies++; open = false },
                        ) else Text("Saved: $tab|$pager|$applies")
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_DARK = "dark"
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_SHEET = "sheet"
        const val SCREEN_SWITCH = "switch"
        const val SCREEN_TABS = "tabs"
        const val EXTRA_FONT_SCALE = "fontScale"
        const val EXTRA_WIDTH_DP = "widthDp"
    }
}
