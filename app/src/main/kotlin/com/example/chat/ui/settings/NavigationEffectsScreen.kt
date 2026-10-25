package com.example.chat.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.theme.LocalAppColors

private val clickSymbols = listOf("\u2728", "\u2764\uFE0F", "\uD83D\uDD25", "\u26A1", "\u2B50", "\uD83C\uDF38")
private val fallingObjects = listOf("Stars", "Hearts", "Snowflakes", "Leaves")
private val fallingScopes = listOf("Home only", "Chat only", "Both")

/** Port of navigation_effects_page.dart (NavigationEffectsPage). */
@Composable
fun NavigationEffectsScreen(preferences: ChatyPreferencesController, onBack: () -> Unit) {
    rememberPreferencesVersion(preferences)
    val colors = LocalAppColors.current
    val fx = preferences.effects
    SettingsPage("Navigation & Particle Effects", "Page Transitions, Click Particles & Falling Objects", onBack) {
        SettingsSection(title = "Live Effects Preview") {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "Click Particles: ${if (fx.enableClickParticles) "${fx.clickParticleSymbol} Active" else "Off"} \u2022 Falling: ${if (fx.enableFallingParticles) "${fx.fallingParticleObject} Active" else "Off"}",
                    color = colors.foregroundSecondary, fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .background(colors.surfaceSecondary, RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, colors.divider.copy(alpha = 0.2f)), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (fx.enableClickParticles) fx.clickParticleSymbol else "\u2728", fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Interactive Particle FX: ${if (fx.enableClickParticles) "On" else "Disabled"}", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                if (fx.enableClickParticles) "Particles spawn on user interaction" else "Tap anywhere to spawn particle effects",
                                color = colors.foregroundSecondary, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
        SettingsSection(
            title = "Interactive Tap Particles",
            description = "Generates decorative particle splashes whenever you tap the screen.",
        ) {
            SettingsSwitchTile("\u2728", "Enable Click Particles", "Spawn particle splash on screen touch", fx.enableClickParticles, colors.accent) {
                preferences.updateEffects(fx.copy(enableClickParticles = it), logTitle = "Click Particles")
            }
            if (fx.enableClickParticles) {
                SettingsChoiceTile("Particle Symbol", clickSymbols, fx.clickParticleSymbol) {
                    preferences.updateEffects(fx.copy(clickParticleSymbol = it), logTitle = "Particle Symbol")
                }
                SettingsSliderTile("\u00BB", "Particle Speed", fx.clickParticleSpeed, 0.5, 2.0, 6, { "${it}x" }) {
                    preferences.updateEffects(fx.copy(clickParticleSpeed = it), logTitle = "Particle Speed")
                }
            }
        }
        SettingsSection(
            title = "Decorative Falling Particles",
            description = "Ambient particles drifting downwards on application screens.",
        ) {
            SettingsSwitchTile("\u2744", "Enable Falling Particles", "Render drifting ambient background particles", fx.enableFallingParticles, colors.info) {
                preferences.updateEffects(fx.copy(enableFallingParticles = it), logTitle = "Falling Particles")
            }
            if (fx.enableFallingParticles) {
                SettingsChoiceTile("Falling Object", fallingObjects, fx.fallingParticleObject) {
                    preferences.updateEffects(fx.copy(fallingParticleObject = it), logTitle = "Falling Object")
                }
                SettingsChoiceTile("Screen Scope", fallingScopes, fx.fallingParticleScope) {
                    preferences.updateEffects(fx.copy(fallingParticleScope = it), logTitle = "Falling Scope")
                }
            }
        }
    }
}
