package com.example.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.data.services.ChatyEventNotification
import com.example.chat.data.services.ChatyNotificationService
import com.example.chat.ui.core.notifications.ChatyNotificationIconPresets
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

/**
 * Port of ui/core/widgets/event_toast_overlay.dart ChatyEventToastOverlay.
 * Renders the latest event notification as a setting-driven toast above the
 * content. [concealWhileLocked] (Dart: lock gate showing AND
 * security.hideLockNotificationContent) replaces the body with dots; titles
 * stay visible.
 *
 * Known differences (compile-only, not pixel-checked): the generic icon is a
 * single chat-bubble glyph instead of the per-preset icon key; the Dynamic
 * Island variant ('dynamic_island_notifications') renders the compact pill
 * only (no expand / recent list); no cutout-aware top padding.
 */
@Composable
fun ChatyEventToastOverlay(
    notificationService: ChatyNotificationService,
    preferences: ChatyPreferencesController,
    concealWhileLocked: Boolean,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf<ChatyEventNotification?>(null) }
    var lastId by remember { mutableStateOf<String?>(null) }
    var tick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    DisposableEffect(notificationService) {
        val l: () -> Unit = { tick++ }
        notificationService.addListener(l)
        onDispose { notificationService.removeListener(l) }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    val latest = notificationService.latest
    if (latest != null && latest.id != lastId) {
        // Dart _onNotificationChanged gate.
        lastId = latest.id
        if (preferences.notification.enableGlobalNotifications &&
            !preferences.universal.disableHeadsUpNotification
        ) {
            visible = latest
        }
    }
    val seconds = preferences.gbInt("event_toast_duration_seconds", 3).coerceIn(2, 8)
    LaunchedEffect(visible?.id) {
        val id = visible?.id ?: return@LaunchedEffect
        delay(seconds * 1000L)
        if (visible?.id == id) visible = null
    }

    Box(Modifier.fillMaxSize()) {
        content()
        val n = visible
        val position = preferences.gbString("event_toast_position", "Top").lowercase()
        val islandMode = preferences.gbBool("dynamic_island_notifications", false)
        val align = when (position) {
            "center" -> Alignment.Center
            "bottom" -> Alignment.BottomCenter
            else -> Alignment.TopCenter
        }
        AnimatedVisibility(
            visible = n != null,
            modifier = Modifier.align(if (islandMode) Alignment.TopCenter else align),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val shown = n ?: return@AnimatedVisibility
            val colors = LocalAppColors.current
            val body = if (concealWhileLocked) "••••••" else shown.body
            if (islandMode) {
                Row(
                    Modifier.statusBarsPadding().padding(top = 1.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF08090B))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                        .clickable { visible = null }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF48D597)))
                    Text(shown.title, color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp))
                    Text(body, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp))
                }
            } else {
                val avatarSize = preferences.gbDouble("toast_avatar_size", 40.0).coerceIn(24.0, 56.0).dp
                val corner = preferences.gbDouble("toast_corner_radius", 18.0).coerceIn(4.0, 32.0).dp
                val elevation = preferences.gbDouble("toast_elevation", 10.0).coerceIn(0.0, 24.0).dp
                val bg = preferences.gbColor("event_toast_bg")?.let { Color(it.toInt()) } ?: colors.surface
                val preset = ChatyNotificationIconPresets.forName(preferences.universal.notificationIcon)
                val presetColor = Color(preset.colorArgb.toInt())
                val textTint = shown.textColorArgb?.let { Color(it.toInt()) }
                val pad = when (position) {
                    "bottom" -> Modifier.padding(start = 14.dp, end = 14.dp, bottom = 92.dp)
                    "center" -> Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
                    else -> Modifier.statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 2.dp)
                }
                Box(pad) {
                    Surface(
                        shape = RoundedCornerShape(corner),
                        color = bg,
                        shadowElevation = elevation,
                        modifier = Modifier.widthIn(max = 430.dp)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(corner)),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            val initials = shown.avatarInitials
                            if (preferences.notification.showSenderAvatar && initials != null) {
                                Box(Modifier.size(avatarSize).clip(CircleShape)
                                    .background(avatarColor(shown)),
                                    contentAlignment = Alignment.Center) {
                                    Text(initials, color = colors.onPrimary, fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold)
                                }
                            } else {
                                Box(Modifier.size(avatarSize).clip(CircleShape)
                                    .background(presetColor.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.ChatBubbleOutline, null,
                                        tint = presetColor, modifier = Modifier.size(21.dp))
                                }
                            }
                            Spacer(Modifier.width(11.dp))
                            Column {
                                Text(
                                    if (preferences.notification.showSenderName) shown.title else "Chaty",
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                                    color = textTint ?: colors.foreground,
                                )
                                if (preferences.notification.showMessagePreview) {
                                    Text(body, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        fontSize = 12.sp,
                                        color = textTint ?: colors.foregroundSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun avatarColor(n: ChatyEventNotification): Color {
    val fallback = Color(n.colorArgb.toInt())
    val raw = n.avatarColorHex
    if (raw.isNullOrEmpty()) return fallback
    val norm = raw.removePrefix("0x").removePrefix("#")
    val parsed = norm.toLongOrNull(16) ?: return fallback
    return Color((if (norm.length <= 6) 0xFF000000 or parsed else parsed).toInt())
}
