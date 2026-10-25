package com.example.chat.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.chat.ui.theme.LocalThemeConfig

/**
 * Port of lib/core/audio/audio_settings_screen.dart ("Audio & Speakers").
 * Binds the real core/audio controller, capability service and coordinator
 * (listeners merged like Listenable.merge). Device-test rows come verbatim
 * from AudioSettingsController.verifiedDeviceRecords; the status labels are
 * shown as recorded and nothing here claims hardware verification.
 * Hero is SpecularGlassCard (blur 12 NOT rendered: no backdrop blur, see SpecularGlassCard), radio/route rows
 * have springInteractiveScale(0.98) + ChatyHapticsPort.selection (Dart HapticFeedback.selectionClick), Material
 * icons, grouped-card shadow, and a AppBar matching the Dart toThemeData appBarTheme (19*fontScale/w600/-0.3 title, back arrow in a 56dp leading slot).
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
    val themeConfig = LocalThemeConfig.current
    val isDark = themeConfig.isDark
    Column(Modifier.fillMaxSize().background(colors.background)) {
        // Dart AppBar(title: Text('Audio & Speakers'), backgroundColor: colors.background, elevation 0): default BackButton in a
        // 56dp leading slot, title spacing 16, titleTextStyle from toThemeData(): 19*fontScale / w600 / letterSpacing -0.3 / primaryTextColor.
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color(themeConfig.primaryTextColor)) }
            }
            Spacer(Modifier.width(16.dp))
            Text("Audio & Speakers", color = Color(themeConfig.primaryTextColor), fontSize = (19 * themeConfig.fontScale).sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
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
            AudioSpecularGlassCard(Modifier.fillMaxWidth(), blur = 12f, borderRadius = 20.dp, padding = PaddingValues(18.dp)) {
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

/**
 * Port of lib/core/design_system/specular_glass_card.dart: topLeft->bottomRight gradient of baseTint at
 * opacity*1.5 -> opacity*0.6 (baseTint white in dark / black in light), 1dp border (white .12 / black .08),
 * default shadow (black .35 dark / .04 light, blur 16, dy 6), radius 18, padding 16, clickable.
 * Shared by Audio settings and Theme Studio. KNOWN GAP: Dart wraps this in BackdropFilter(blur sigma = [blur]); Compose has no backdrop blur of
 * the content behind a node without RenderEffect on a captured layer, so [blur] is accepted but not rendered.
 * Needs the visual pass. Uncompiled.
 */
@Composable
fun SpecularGlassCard(
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") blur: Float = 16f,
    borderRadius: Dp = 18.dp,
    padding: PaddingValues = PaddingValues(16.dp),
    tintColor: Color? = null,
    opacity: Float = 0.08f,
    borderColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val isDark = LocalThemeConfig.current.isDark
    val baseTint = tintColor ?: if (isDark) Color.White else Color.Black
    val shape = RoundedCornerShape(borderRadius)
    val border = borderColor ?: if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    val shadowColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.04f)
    Box(
        modifier
            .shadow(8.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
            .clip(shape)
            .background(Brush.linearGradient(listOf(baseTint.copy(alpha = (opacity * 1.5f).coerceIn(0f, 1f)), baseTint.copy(alpha = (opacity * 0.6f).coerceIn(0f, 1f)))))
            .border(1.dp, border, shape)
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(padding),
    ) { content() }
}
