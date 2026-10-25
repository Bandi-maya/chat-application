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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CancelOutlined
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import com.example.chat.ui.kit.ChatyHapticsPort
import com.example.chat.ui.kit.SpecularGlassCard
import com.example.chat.ui.kit.springInteractiveScale
import com.example.chat.ui.theme.LocalThemeConfig
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
 * Hero is SpecularGlassCard (blur 12 NOT rendered: no backdrop blur, see SpecularGlassCard), radio/route rows
 * have springInteractiveScale(0.98) + ChatyHapticsPort.selection (Dart HapticFeedback.selectionClick), Material
 * icons, grouped-card shadow, and a plain AppBar (22sp title: Dart uses the theme default titleLarge, unverified).
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

    val view = LocalView.current
    val isDark = LocalThemeConfig.current.isDark
    Column(Modifier.fillMaxSize().background(colors.background)) {
        // Dart AppBar(title: Text('Audio & Speakers'), backgroundColor: colors.background, elevation 0) with the default back arrow
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colors.foreground) }
            Spacer(Modifier.width(12.dp))
            Text("Audio & Speakers", color = colors.foreground, fontSize = 22.sp)
        }
        Column(
            Modifier.weight(1f).windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // 1. Status hero
            val (badgeText, badgeFg) = when (dualStatus) {
                DualOutputStatus.active -> "Active & Verified" to green
                DualOutputStatus.notVerified -> "Available (Standard Route)" to colors.accent
                DualOutputStatus.unavailable -> "Temporarily Unavailable" to orange
                DualOutputStatus.unsupported -> "Unsupported by OS/Hardware" to colors.foregroundSecondary
                DualOutputStatus.failed -> "Activation Failed" to red
            }
            SpecularGlassCard(Modifier.fillMaxWidth(), blur = 12f, borderRadius = 20.dp, padding = PaddingValues(18.dp)) {
              Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                        Icon(Icons.Rounded.SpeakerGroup, null, Modifier.size(26.dp), tint = colors.accent)
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
                    "Smart audio policy coordinates native earpiece and bottom loudspeaker routing across VoIP calls, media, and notification sounds without breaking Bluetooth or microphone AEC.",
                    color = colors.foregroundSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row {
                    Text("Active Output: ", color = colors.foregroundSecondary, fontSize = 13.sp)
                    Text(activeRoute.name, color = colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
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
                    val (icon, name) = when (route) {
                        AudioOutputRoute.receiver -> Icons.Rounded.PhoneInTalk to "Earpiece Receiver"
                        AudioOutputRoute.speaker -> Icons.Rounded.VolumeUp to "Bottom Loudspeaker"
                        AudioOutputRoute.bluetooth -> Icons.Rounded.BluetoothAudio to "Bluetooth Device"
                        AudioOutputRoute.wiredHeadset -> Icons.Rounded.Headphones to "Wired Headset"
                        AudioOutputRoute.usb -> Icons.Rounded.Usb to "USB Audio Interface"
                        AudioOutputRoute.systemDefault -> Icons.Rounded.DevicesOther to "System Default"
                    }
                    Row(
                        Modifier.fillMaxWidth().springInteractiveScale(0.98f)
                            .clickable {
                                ChatyHapticsPort.selection(view)
                                scope.launch { capability.setPreferredRoute(AudioUseCase.communication, route) }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon, null, Modifier.size(20.dp), tint = colors.accent) }
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
            AudioGroupedCard(withShadow = false) {
                val records = AudioSettingsController.verifiedDeviceRecords
                records.forEachIndexed { idx, rec ->
                    if (idx > 0) AudioDivider()
                    val status = rec["testStatus"] as String
                    val (color, statusIcon) = when (status) {
                        "Verified Dual Output" -> green to Icons.Outlined.CheckCircleOutline
                        "Native Stereo" -> blue to Icons.Rounded.MusicNote
                        "Standard Single Route" -> gray to Icons.Rounded.RadioButtonChecked
                        "Unsupported or Failed" -> red to Icons.Outlined.CancelOutlined
                        else -> orange to Icons.Outlined.HelpOutline
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(statusIcon, null, Modifier.size(20.dp), tint = color)
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
private fun AudioGroupedCard(withShadow: Boolean = true, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    val isDark = LocalThemeConfig.current.isDark
    val shape = RoundedCornerShape(16.dp)
    val shadowColor = colors.shadow.copy(alpha = if (isDark) 0.3f else 0.04f)
    Column(
        Modifier.fillMaxWidth()
            .then(if (withShadow) Modifier.shadow(5.dp, shape, ambientColor = shadowColor, spotColor = shadowColor) else Modifier)
            .clip(shape)
            .background(if (isDark) Color(0xFF1C1C1E) else colors.surface)
            .border(BorderStroke(0.8.dp, colors.borderSubtle), shape),
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
    val view = LocalView.current
    Row(
        Modifier.fillMaxWidth().springInteractiveScale(0.98f)
            .let { if (enabled) it.clickable { ChatyHapticsPort.selection(view); onTap() } else it }
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
        Icon(
            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(22.dp),
            tint = if (selected) colors.accent else colors.foregroundSecondary.copy(alpha = 0.4f),
        )
    }
}
