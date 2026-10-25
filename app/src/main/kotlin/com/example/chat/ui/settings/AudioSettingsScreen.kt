package com.example.chat.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.audio.AudioCapabilityService
import com.example.chat.core.audio.AudioOutputRoute
import com.example.chat.core.audio.AudioPolicyCoordinator
import com.example.chat.core.audio.AudioPreferenceMode
import com.example.chat.core.audio.AudioSettingsController
import com.example.chat.core.audio.AudioUseCase
import com.example.chat.core.audio.DualOutputStatus
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Port of lib/core/audio/audio_settings_screen.dart ("Audio & Speakers").
 * Binds the real core/audio controller, capability service and coordinator
 * (listeners merged like Listenable.merge). Device-test rows come verbatim
 * from AudioSettingsController.verifiedDeviceRecords; the status labels are
 * shown as recorded and nothing here claims hardware verification.
 * Differences from Dart, pending visual/runtime check: the hero uses a plain
 * surface card instead of SpecularGlassCard (blur 12), no spring-scale press
 * animation and no selectionClick haptic yet.
 * [scope] launches the suspend setPreferredRoute call.
 */
@Composable
fun AudioSettingsScreen(
    settings: AudioSettingsController,
    capability: AudioCapabilityService,
    coordinator: AudioPolicyCoordinator,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(settings, capability, coordinator) {
        val l: () -> Unit = { version += 1 }
        settings.addListener(l); capability.addListener(l); coordinator.addListener(l)
        onDispose { settings.removeListener(l); capability.removeListener(l); coordinator.removeListener(l) }
    }
    @Suppress("UNUSED_VARIABLE") val rebuild = version

    val dualStatus = capability.communicationDualStatus
    val activeRoute = capability.activeCommunicationRoute
    val availableRoutes = capability.availableCommunicationRoutes
    val mode = settings.preferenceMode

    val green = Color(0xFF34C759)
    val orange = Color(0xFFFF9500)
    val red = Color(0xFFFF3B30)
    val blue = Color(0xFF007AFF)
    val gray = Color(0xFF8E8E93)

    SettingsPage("Audio & Speakers", null, onBack) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // 1. Status hero
            val (badgeText, badgeFg) = when (dualStatus) {
                DualOutputStatus.active -> "Active & Verified" to green
                DualOutputStatus.notVerified -> "Available (Standard Route)" to colors.accent
                DualOutputStatus.unavailable -> "Temporarily Unavailable" to orange
                DualOutputStatus.unsupported -> "Unsupported by OS/Hardware" to colors.foregroundSecondary
                DualOutputStatus.failed -> "Activation Failed" to red
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
                    .border(BorderStroke(0.8.dp, colors.borderSubtle), RoundedCornerShape(20.dp)).padding(18.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                        Text("\u266B", color = colors.accent, fontSize = 24.sp)
                        Spacer(Modifier.width(10.dp))
                        Text("Dual-Speaker Engine", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                    Text(
                        badgeText, color = badgeFg, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(badgeFg.copy(alpha = 0.15f))
                            .border(BorderStroke(1.dp, badgeFg.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Smart audio policy coordinates native earpiece and bottom loudspeaker routing across VoIP calls, media, and notification sounds without breaking system behavior.",
                    color = colors.foregroundSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row {
                    Text("Active Output: ", color = colors.foregroundSecondary, fontSize = 13.sp)
                    Text(activeRoute.name, color = colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(20.dp))

            // 2. Routing preference
            AudioSectionHeader("ROUTING PREFERENCE")
            AudioGroupedCard {
                AudioRadioRow("Automatic (Recommended)", "Uses native device stereo and smart VoIP routing",
                    mode == AudioPreferenceMode.automatic, true) { settings.setPreferenceMode(AudioPreferenceMode.automatic) }
                AudioDivider()
                AudioRadioRow("Standard Stereo Playback", "Preserves operating system standard media routes",
                    mode == AudioPreferenceMode.preferStereo, true) { settings.setPreferenceMode(AudioPreferenceMode.preferStereo) }
                AudioDivider()
                AudioRadioRow(
                    "Dual-Speaker Communication",
                    if (dualStatus == DualOutputStatus.unsupported) "Unsupported on this device hardware/OS"
                    else "Engage earpiece + speakerphone on verified devices",
                    mode == AudioPreferenceMode.experimentalDualOutput,
                    dualStatus != DualOutputStatus.unsupported,
                ) { settings.setPreferenceMode(AudioPreferenceMode.experimentalDualOutput) }
            }
            Spacer(Modifier.height(24.dp))

            // 3. Routes
            AudioSectionHeader("ACTIVE & AVAILABLE ROUTES")
            AudioGroupedCard {
                availableRoutes.forEachIndexed { i, route ->
                    if (i > 0) AudioDivider()
                    val (glyph, name) = when (route) {
                        AudioOutputRoute.receiver -> "\u260E" to "Earpiece Receiver"
                        AudioOutputRoute.speaker -> "\u266A" to "Bottom Loudspeaker"
                        AudioOutputRoute.bluetooth -> "\u16D2" to "Bluetooth Device"
                        AudioOutputRoute.wiredHeadset -> "\u266C" to "Wired Headset"
                        AudioOutputRoute.usb -> "\u2301" to "USB Audio Interface"
                        AudioOutputRoute.systemDefault -> "\u25A3" to "System Default"
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { scope.launch { capability.setPreferredRoute(AudioUseCase.communication, route) } }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(glyph, color = colors.accent, fontSize = 18.sp) }
                        Spacer(Modifier.width(14.dp))
                        Text(name, color = colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
                        if (route == activeRoute) {
                            Text(
                                "ACTIVE", color = green, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(green.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            // 4. Evidence dataset (verbatim records; status shown as recorded)
            AudioSectionHeader("VERIFIED DEVICE TEST DATASET")
            AudioGroupedCard {
                val records = AudioSettingsController.verifiedDeviceRecords
                records.forEachIndexed { idx, rec ->
                    if (idx > 0) AudioDivider()
                    val status = rec["testStatus"] as String
                    val (color, glyph) = when (status) {
                        "Verified Dual Output" -> green to "\u2713"
                        "Native Stereo" -> blue to "\u266B"
                        "Standard Single Route" -> gray to "\u25C9"
                        "Unsupported or Failed" -> red to "\u2715"
                        else -> orange to "?"
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(glyph, color = color, fontSize = 18.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(rec["deviceModel"] as String, color = colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                                Spacer(Modifier.width(6.dp))
                                Text("(${rec["osVersion"]})", color = colors.foregroundSecondary, fontSize = 11.5.sp)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(rec["verificationMethod"] as String, color = colors.foregroundSecondary, fontSize = 11.5.sp)
                        }
                        Text(
                            status, color = color, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun AudioSectionHeader(title: String) {
    val colors = LocalAppColors.current
    Text(
        title, color = colors.foregroundSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 14.dp, bottom = 10.dp),
    )
}

@Composable
private fun AudioGroupedCard(content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    val dark = colors.background.red < 0.3f && colors.background.green < 0.3f && colors.background.blue < 0.3f
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (dark) Color(0xFF1C1C1E) else colors.surface)
            .border(BorderStroke(0.8.dp, colors.borderSubtle), RoundedCornerShape(16.dp)),
    ) { content() }
}

@Composable
private fun AudioDivider() {
    val colors = LocalAppColors.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(0.8.dp).background(colors.borderSubtle.copy(alpha = 0.6f)))
}

@Composable
private fun AudioRadioRow(title: String, subtitle: String, selected: Boolean, enabled: Boolean, onTap: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().let { if (enabled) it.clickable(onClick = onTap) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = if (enabled) colors.foreground else colors.foregroundSecondary.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = colors.foregroundSecondary, fontSize = 12.sp)
        }
        Text(
            if (selected) "\u25CF" else "\u25CB", fontSize = 22.sp,
            color = if (selected) colors.accent else colors.foregroundSecondary.copy(alpha = 0.4f),
        )
    }
}
