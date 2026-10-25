package com.example.chat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.theme.LocalAppColors
import androidx.compose.ui.Modifier

private val clickSymbols = listOf("\u2728", "\u2764\uFE0F", "\uD83D\uDD25", "\u26A1", "\u2B50", "\uD83C\uDF38")
private val fallingObjects = listOf("Stars", "Hearts", "Snowflakes", "Leaves")
private val fallingScopes = listOf("Home only", "Chat only", "Both")

/** Port of navigation_effects_page.dart (NavigationEffectsPage) on the Chaty settings primitives. Uncompiled. */
@Composable
fun NavigationEffectsScreen(preferences: ChatyPreferencesController, onBack: () -> Unit) {
    rememberPreferencesVersion(preferences)
    val colors = LocalAppColors.current
    val fx = preferences.effects
    ChatySettingsPage("Navigation & Particle Effects", "Page Transitions, Click Particles & Falling Objects", onBack = onBack) {
        ChatyPreviewCard("Live Effects Preview") {
            Column {
                Text(
                    "Click Particles: ${if (fx.enableClickParticles) "${fx.clickParticleSymbol} Active" else "Off"} \u2022 Falling: ${if (fx.enableFallingParticles) "${fx.fallingParticleObject} Active" else "Off"}",
                    color = colors.foregroundSecondary, fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .background(colors.surfaceElevated, RoundedCornerShape(12.dp))
                        .border(1.dp, colors.foregroundSecondary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
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
                    Icon(
                        Icons.Rounded.Animation, null, Modifier.size(24.dp),
                        tint = if (fx.enableClickParticles) colors.accent else colors.foregroundTertiary,
                    )
                }
            }
        }
        ChatySettingsSection("Interactive Tap Particles", "Generates decorative particle splashes whenever you tap the screen.") {
            ChatySwitchTile(
                "Enable Click Particles", fx.enableClickParticles,
                { preferences.updateEffects(fx.copy(enableClickParticles = it), logTitle = "Click Particles") },
                Icons.Rounded.AutoAwesome, colors.accent, "Spawn particle splash on screen touch",
            )
            if (fx.enableClickParticles) {
                ChatySettingsRowDivider()
                ChatyChoiceTile("Particle Symbol", clickSymbols, fx.clickParticleSymbol, { it }, {
                    preferences.updateEffects(fx.copy(clickParticleSymbol = it), logTitle = "Particle Symbol")
                })
                ChatySettingsRowDivider()
                ChatySliderTile(
                    title = "Particle Speed", value = fx.clickParticleSpeed.toFloat(),
                    onChanged = { preferences.updateEffects(fx.copy(clickParticleSpeed = it.toDouble()), logTitle = "Particle Speed") },
                    icon = Icons.Rounded.Speed, min = 0.5f, max = 2.0f, divisions = 6,
                    valueFormatter = { "${it.toDouble()}x" },
                )
            }
        }
        ChatySettingsSection("Decorative Falling Particles", "Ambient particles drifting downwards on application screens.") {
            ChatySwitchTile(
                "Enable Falling Particles", fx.enableFallingParticles,
                { preferences.updateEffects(fx.copy(enableFallingParticles = it), logTitle = "Falling Particles") },
                Icons.Rounded.AcUnit, colors.info, "Render drifting ambient background particles",
            )
            if (fx.enableFallingParticles) {
                ChatySettingsRowDivider()
                ChatyChoiceTile("Falling Object", fallingObjects, fx.fallingParticleObject, { it }, {
                    preferences.updateEffects(fx.copy(fallingParticleObject = it), logTitle = "Falling Object")
                })
                ChatySettingsRowDivider()
                ChatyChoiceTile("Screen Scope", fallingScopes, fx.fallingParticleScope, { it }, {
                    preferences.updateEffects(fx.copy(fallingParticleScope = it), logTitle = "Falling Scope")
                })
            }
        }
    }
}
