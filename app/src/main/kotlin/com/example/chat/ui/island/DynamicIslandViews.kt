package com.example.chat.ui.island

import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.kit.ChatyHapticsPort
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import java.time.Instant
import kotlin.math.abs
import kotlin.math.sin
import androidx.compose.ui.text.TextStyle

/** Exact port of lib/features/island/widgets/dynamic_island_views.dart:
 *  helpers (formatTime/relativeTime), IslandWaveform, IslandSeekBar,
 *  CollapsedIslandView, ExpandedMediaView, ExpandedChatMessageView,
 *  ExpandedCallView, ExpandedNotificationsListView,
 *  MultiTaskCarouselIndicator, ExpandedClipboardIntentView,
 *  ExpandedDragTargetView.
 *
 *  Haptics mapping (Flutter HapticFeedback -> canonical ChatyHapticsPort):
 *  lightImpact -> light, mediumImpact -> threshold, heavyImpact -> error,
 *  selectionClick -> selection.
 *
 *  NetworkImage avatars -> coil AsyncImage with the same BoxFit.cover;
 *  payload.iconData (ImageVector) -> shared material-icons usage, matching
 *  how the payload model was ported. */

/* ─── Helpers ──────────────────────────────────────────────────────── */
fun islandFormatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val sec = totalSeconds % 60
    return "$m:${sec.toString().padStart(2, '0')}"
}

fun islandRelativeTime(timestamp: Instant, now: Instant = Instant.now()): String {
    val diff = java.time.Duration.between(timestamp, now).seconds
    if (diff < 60) return "Just now"
    if (diff < 3600) return "${diff / 60}m ago"
    if (diff < 86400) return "${diff / 3600}h ago"
    val zdt = timestamp.atZone(java.time.ZoneId.systemDefault())
    return "${zdt.monthValue}/${zdt.dayOfMonth}"
}

/* ─── Animated equalizer waveform ──────────────────────────────────── */
private val waveformBaseHeights = listOf(3, 5, 7, 5, 9, 6, 8, 4, 7, 5, 6, 3)

/** Pure bar-height math of _IslandWaveformState for JVM tests. */
fun islandWaveformBarHeight(
    index: Int,
    active: Boolean,
    phase: Double,
    maxHeight: Double,
): Double {
    val base = waveformBaseHeights[index]
    val factor = if (active) {
        1.0 + abs(sin(phase * 2 * Math.PI + index * 0.4)) * 1.1
    } else {
        1.0
    }
    return (base * factor).coerceIn(3.0, maxHeight)
}

@Composable
fun IslandWaveform(
    active: Boolean,
    barColor: Color = Color(0xB3FFFFFF),
    height: Double = 18.0,
    barCount: Int = 12,
) {
    val count = barCount.coerceIn(1, waveformBaseHeights.size)
    // 900ms repeat(reverse: true) phase driver.
    val phase = rememberIslandWaveformPhase(active)
    Row(verticalAlignment = Alignment.Bottom) {
        for (i in 0 until count) {
            val barH = islandWaveformBarHeight(i, active, phase, height)
            Box(
                Modifier
                    .padding(horizontal = 1.dp)
                    .width(2.dp)
                    .height(barH.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(barColor),
            )
        }
    }
}

@Composable
private fun rememberIslandWaveformPhase(active: Boolean): Double {
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(active) {
        if (active) {
            anim.animateTo(
                1f,
                androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.tween(
                        900,
                        easing = androidx.compose.animation.core.LinearEasing,
                    ),
                    androidx.compose.animation.core.RepeatMode.Reverse,
                ),
            )
        } else {
            anim.stop()
        }
    }
    return anim.value.toDouble()
}

/* ─── Custom seek slider ───────────────────────────────────────────── */
@Composable
fun IslandSeekBar(
    progress: Double,
    onSeek: ((Double) -> Unit)?,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    barColor: Color = Color.White,
    height: Double = 18.0,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .islandSeekGestures(progress, onSeek),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(barColor.copy(alpha = if (active) 0.18f else 0.08f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0.0, 1.0).toFloat())
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor.copy(alpha = if (active) 1f else 0.45f)),
            )
        }
    }
}

/** Tap + horizontal drag -> fraction seek; local x clamped into [0, width]
 *  exactly like the Dart onTapDown/onHorizontalDragUpdate handlers. */
private fun Modifier.islandSeekGestures(
    progress: Double,
    onSeek: ((Double) -> Unit)?,
): Modifier = this.pointerInputSeek(onSeek)

private fun Modifier.pointerInputSeek(
    onSeek: ((Double) -> Unit)?,
): Modifier {
    fun androidx.compose.ui.input.pointer.PointerInputScope.seekTo(x: Float) {
        if (size.width <= 0) return
        val fraction = (x / size.width).coerceIn(0f, 1f).toDouble()
        onSeek?.invoke(fraction)
    }
    // Tap and drag are separate pointerInput blocks: both Dart handlers
    // (onTapDown + onHorizontalDragUpdate) listen concurrently.
    return this
        .pointerInput(onSeek) {
            detectTapGestures { pos -> seekTo(pos.x) }
        }
        .pointerInput(onSeek) {
            detectHorizontalDragGestures { change, _ -> seekTo(change.position.x) }
        }
}

/* ─── Collapsed Pill View ──────────────────────────────────────────── */
@Composable
fun CollapsedIslandView(
    payload: DynamicIslandPayload?,
    notifCount: Int,
    onTap: () -> Unit,
) {
    val view = LocalView.current
    val p = payload
    val isMedia = p?.type == IslandPayloadType.media ||
        p?.type == IslandPayloadType.voiceNotePlayback
    val isCall = p?.type == IslandPayloadType.incomingCall ||
        p?.type == IslandPayloadType.ongoingCall

    Box(
        modifier = Modifier.clickable {
            ChatyHapticsPort.light(view)
            onTap()
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(36.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Leading element.
                when {
                    isCall -> Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E)),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChatyIcon(
                            if (p?.isVideo == true) ChatyIconId.videoCam else ChatyIconId.voiceCall,
                            color = Color.Black,
                            size = 11.dp,
                        )
                    }
                    isMedia -> CollapsedRotatingCover(p)
                    p?.type == IslandPayloadType.clipboardIntent -> BandiKineticCrest(
                        size = 16.dp,
                        primaryColor = Color(0xFFF59E0B),
                        accentColor = Color(0xFFFBBF24),
                    )
                    p?.type == IslandPayloadType.dragAndGoTarget -> IslandVectorIcon(
                        icon = androidx.compose.material.icons.Icons.Rounded.FileDownload,
                        color = Color(0xFF00F0FF),
                        size = 16.dp,
                    )
                    else -> if (p?.isBandiIcon == true) {
                        BandiKineticCrest(
                            size = 16.dp,
                            primaryColor = p.accentColor,
                            accentColor = Color(0xFF38BDF8),
                        )
                    } else {
                        IslandVectorIcon(
                            icon = p?.iconData,
                            color = p?.accentColor ?: Color.White,
                            size = 16.dp,
                        )
                    }
                }

                // Camera punch-hole clearance spacer.
                Spacer(Modifier.width(12.dp))

                // Trailing element.
                when {
                    isCall -> if (p != null && p.durationSeconds > 0) { // Dart: p?.durationSeconds != null && p!.durationSeconds > 0
                        Text(
                            islandFormatTime(p.durationSeconds),
                            color = Color(0xFF22C55E),
                            fontSize = 10.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
                        )
                    } else {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E)),
                        )
                    }
                    isMedia -> IslandWaveform(
                        active = p?.isPlaying ?: false,
                        barColor = Color(0xFF60A5FA),
                        height = 12.0,
                        barCount = 4,
                    )
                    p?.type == IslandPayloadType.clipboardIntent -> IslandVectorIcon(
                        icon = androidx.compose.material.icons.Icons.Rounded.ArrowForward,
                        color = Color(0xFFFBBF24),
                        size = 14.dp,
                    )
                    p?.type == IslandPayloadType.dragAndGoTarget -> IslandVectorIcon(
                        icon = androidx.compose.material.icons.Icons.Rounded.Add,
                        color = Color(0xFF00F0FF),
                        size = 14.dp,
                    )
                    notifCount > 1 -> Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x594285F4))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp),
                    ) {
                        Text(
                            "$notifCount",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
                        )
                    }
                    else -> Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(p?.accentColor ?: Color(0xFF60A5FA)),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsedRotatingCover(p: DynamicIslandPayload?) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        if (p?.avatarUrl != null) {
            coil.compose.AsyncImage(
                model = p.avatarUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(20.dp).clip(CircleShape),
            )
        } else {
            ChatyIcon(ChatyIconId.musicNote, color = Color.White, size = 12.dp)
        }
    }
}

/** payload.iconData is an ImageVector in the ported model; renders through
 *  the shared material icon path. Falls back to the Dart default
 *  notifications_active glyph when absent. */
@Composable
private fun IslandVectorIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    color: Color,
    size: androidx.compose.ui.unit.Dp,
) {
    androidx.compose.material3.Icon(
        imageVector = icon ?: androidx.compose.material.icons.Icons.Rounded.NotificationsActive,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size),
    )
}

/* ─── Expanded Media Player View ───────────────────────────────────── */
@Composable
fun ExpandedMediaView(
    payload: DynamicIslandPayload,
    controller: DynamicIslandController,
) {
    val view = LocalView.current
    val hasNotifs = controller.queue.size > 1

    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                if (payload.avatarUrl != null) {
                    coil.compose.AsyncImage(
                        model = payload.avatarUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)),
                    )
                } else {
                    IslandVectorIcon(
                        icon = androidx.compose.material.icons.Icons.Rounded.GraphicEq,
                        color = Color.White,
                        size = 28.dp,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    payload.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    payload.mediaArtist ?: payload.message,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                IslandWaveform(
                    active = payload.isPlaying,
                    barColor = Color(0xFF60A5FA),
                    height = 16.0,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        IslandSeekBar(
            progress = payload.progress,
            onSeek = payload.onSeek?.let { seek -> { v: Double -> seek(v) } },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                islandFormatTime((payload.progress * payload.durationSeconds).toInt()),
                // Dart FontFeature.tabularFigures()
                style = TextStyle(color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontFeatureSettings = "tnum"),
            )
            Text(
                islandFormatTime(payload.durationSeconds),
                style = TextStyle(color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontFeatureSettings = "tnum"),
            )
        }

        Spacer(Modifier.height(10.dp))

        // Control buttons.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable {
                        ChatyHapticsPort.light(view)
                        payload.onSeek?.invoke((payload.progress - 0.05).coerceIn(0.0, 1.0))
                    },
                contentAlignment = Alignment.Center,
            ) {
                IslandVectorIcon(
                    icon = androidx.compose.material.icons.Icons.Rounded.Replay10,
                    color = Color(0xB3FFFFFF),
                    size = 24.dp,
                )
            }
            Spacer(Modifier.width(16.dp))
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable {
                        ChatyHapticsPort.threshold(view)
                        payload.onTogglePlay?.invoke()
                    },
                contentAlignment = Alignment.Center,
            ) {
                ChatyIcon(
                    if (payload.isPlaying) ChatyIconId.pause else ChatyIconId.playArrow,
                    color = Color.Black,
                    size = 26.dp,
                )
            }
            Spacer(Modifier.width(16.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable {
                        ChatyHapticsPort.light(view)
                        payload.onSeek?.invoke((payload.progress + 0.05).coerceIn(0.0, 1.0))
                    },
                contentAlignment = Alignment.Center,
            ) {
                IslandVectorIcon(
                    icon = androidx.compose.material.icons.Icons.Rounded.Forward10,
                    color = Color(0xB3FFFFFF),
                    size = 24.dp,
                )
            }
        }

        // Peek notifications pill button.
        if (hasNotifs) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .clickable {
                        ChatyHapticsPort.light(view)
                        controller.setSubView("notifications")
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IslandVectorIcon(
                        icon = androidx.compose.material.icons.Icons.Rounded.Notifications,
                        color = Color(0xB3FFFFFF),
                        size = 14.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${controller.queue.size - 1} unread notification(s)",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                    )
                }
                IslandVectorIcon(
                    icon = androidx.compose.material.icons.Icons.Rounded.ChevronRight,
                    color = Color(0x61FFFFFF),
                    size = 16.dp,
                )
            }
        }
    }
}

/* ─── Expanded Chat Message View with inline Quick Reply ───────────── */
@Composable
fun ExpandedChatMessageView(
    payload: DynamicIslandPayload,
    controller: DynamicIslandController,
) {
    val view = LocalView.current
    var replyText by remember { mutableStateOf("") }
    var showInput by remember { mutableStateOf(false) }

    fun sendReply() {
        val text = replyText.trim()
        if (text.isNotEmpty()) {
            ChatyHapticsPort.threshold(view)
            payload.onQuickReply?.invoke(text)
            replyText = ""
            showInput = false
            controller.pinOpen(false)
            controller.dismissActive()
        }
    }

    Column(Modifier.padding(16.dp)) {
        Row {
            // Avatar.
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(payload.accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                if (payload.avatarUrl != null) {
                    coil.compose.AsyncImage(
                        model = payload.avatarUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(CircleShape),
                    )
                } else {
                    Text(
                        payload.senderInitials ?: payload.title.take(1),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        payload.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (payload.isVerified) {
                        Spacer(Modifier.width(4.dp))
                        IslandVectorIcon(
                            icon = androidx.compose.material.icons.Icons.Rounded.Verified,
                            color = Color(0xFF60A5FA),
                            size = 14.dp,
                        )
                    }
                    if (payload.isE2ee) {
                        Spacer(Modifier.width(4.dp))
                        IslandVectorIcon(
                            icon = androidx.compose.material.icons.Icons.Rounded.Lock,
                            color = Color(0xFF34D399),
                            size = 12.dp,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        islandRelativeTime(payload.timestamp),
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    payload.message,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    lineHeight = 16.25.sp,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (showInput) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(19.dp)),
                    placeholder = {
                        Text("Type a reply…", color = Color(0x61FFFFFF), fontSize = 13.sp)
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { sendReply() },
                    contentAlignment = Alignment.Center,
                ) {
                    ChatyIcon(ChatyIconId.send, color = Color(0xFF3B82F6), size = 20.dp)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .clickable {
                            ChatyHapticsPort.light(view)
                            showInput = true
                            controller.pinOpen(true)
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatyIcon(ChatyIconId.reply, color = Color.White, size = 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Quick Reply", color = Color.White, fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            ChatyHapticsPort.selection(view)
                            payload.onMarkAsRead?.invoke()
                            controller.dismissActive()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("Mark Read", color = Color(0x99FFFFFF), fontSize = 12.sp)
                }
            }
        }
    }
}

/* ─── Expanded Call View ───────────────────────────────────────────── */
@Composable
fun ExpandedCallView(
    payload: DynamicIslandPayload,
    controller: DynamicIslandController,
) {
    val view = LocalView.current
    val isIncoming = payload.type == IslandPayloadType.incomingCall

    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E)),
                contentAlignment = Alignment.Center,
            ) {
                ChatyIcon(
                    if (payload.isVideo) ChatyIconId.videoCam else ChatyIconId.voiceCall,
                    color = Color.Black,
                    size = 26.dp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    payload.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (isIncoming) {
                        "Incoming ${if (payload.isVideo) "Video" else "Audio"} Call…"
                    } else {
                        "Ongoing Call • ${islandFormatTime(payload.durationSeconds)}"
                    },
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (isIncoming) {
                CallActionButton(
                    kind = "callEnd",
                    color = Color(0xFFEF4444),
                    label = "Decline",
                ) {
                    ChatyHapticsPort.error(view)
                    payload.onDeclineCall?.invoke()
                    controller.dismissActive()
                }
                CallActionButton(
                    kind = "voiceCall",
                    color = Color(0xFF22C55E),
                    label = "Accept",
                ) {
                    ChatyHapticsPort.error(view)
                    payload.onAcceptCall?.invoke()
                }
            } else {
                CallActionButton(
                    kind = if (payload.isMuted) "micOff" else "mic",
                    color = if (payload.isMuted) Color(0x4DFFFFFF) else Color(0x1FFFFFFF),
                    label = if (payload.isMuted) "Unmute" else "Mute",
                ) {
                    ChatyHapticsPort.light(view)
                    payload.onTap?.invoke()
                }
                CallActionButton(
                    kind = if (payload.isSpeaker) "speaker" else "earpiece",
                    color = if (payload.isSpeaker) Color(0xFF3B82F6) else Color(0x1FFFFFFF),
                    label = "Speaker",
                ) {
                    ChatyHapticsPort.light(view)
                    payload.onTap?.invoke()
                }
                CallActionButton(
                    kind = "callEnd",
                    color = Color(0xFFEF4444),
                    label = "End",
                ) {
                    ChatyHapticsPort.error(view)
                    payload.onDeclineCall?.invoke()
                    controller.dismissActive()
                }
            }
        }
    }
}

@Composable
private fun CallActionButton(
    kind: String,
    color: Color,
    label: String,
    onTap: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onTap),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            when (kind) {
                "callEnd" -> ChatyIcon(ChatyIconId.callEnd, color = Color.White, size = 22.dp)
                "voiceCall" -> ChatyIcon(ChatyIconId.voiceCall, color = Color.White, size = 22.dp)
                "mic" -> ChatyIcon(ChatyIconId.microphone, color = Color.White, size = 22.dp)
                "micOff" -> ChatyIcon(ChatyIconId.microphoneOff, color = Color.White, size = 22.dp)
                "speaker" -> ChatyIcon(ChatyIconId.speaker, color = Color.White, size = 22.dp)
                "earpiece" -> ChatyIcon(ChatyIconId.earpiece, color = Color.White, size = 22.dp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color(0xB3FFFFFF), fontSize = 11.sp)
    }
}

/* ─── Expanded Notifications Stack View ────────────────────────────── */
@Composable
fun ExpandedNotificationsListView(
    controller: DynamicIslandController,
    showBack: Boolean = false,
) {
    val view = LocalView.current
    val list = controller.queue.take(3)

    Column(Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showBack) {
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .clickable {
                                ChatyHapticsPort.light(view)
                                controller.setSubView("primary")
                            },
                    ) {
                        IslandVectorIcon(
                            icon = androidx.compose.material.icons.Icons.Rounded.ChevronLeft,
                            color = Color(0xB3FFFFFF),
                            size = 14.dp,
                        )
                    }
                }
                IslandVectorIcon(
                    icon = androidx.compose.material.icons.Icons.Rounded.Notifications,
                    color = Color.White,
                    size = 16.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Notifications (${controller.queue.size})",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
                )
            }
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable {
                        ChatyHapticsPort.light(view)
                        controller.clearAll()
                    },
                contentAlignment = Alignment.Center,
            ) {
                IslandVectorIcon(
                    icon = androidx.compose.material.icons.Icons.Rounded.Close,
                    color = Color(0xB3FFFFFF),
                    size = 14.dp,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (list.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No notifications",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 12.sp,
                )
            }
        } else {
            list.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(item.accentColor.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(item.iconEmoji ?: "💬", fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                item.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.W600,
                            )
                            Text(
                                islandRelativeTime(item.timestamp),
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 9.sp,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            item.message,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

/* ─── Multi-Task Carousel Indicator ────────────────────────────────── */
@Composable
fun MultiTaskCarouselIndicator(controller: DynamicIslandController) {
    val view = LocalView.current
    val count = controller.queue.size
    if (count <= 1) return

    val currentIndex = controller.carouselIndex.coerceIn(0, count - 1)

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(4.dp)
                .clickable {
                    ChatyHapticsPort.light(view)
                    controller.prevCarouselItem()
                },
        ) {
            IslandVectorIcon(
                icon = androidx.compose.material.icons.Icons.Rounded.ChevronLeft,
                color = Color.White.copy(alpha = 0.4f),
                size = 16.dp,
            )
        }
        Spacer(Modifier.width(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (index in 0 until count) {
                val isCurrent = index == currentIndex
                Box(
                    Modifier
                        .padding(horizontal = 2.5.dp)
                        .width(if (isCurrent) 14.dp else 5.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(
                            if (isCurrent) Color(0xFF60A5FA)
                            else Color.White.copy(alpha = 0.25f),
                        )
                        .clickable {
                            ChatyHapticsPort.light(view)
                            controller.setCarouselIndex(index)
                        },
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .padding(4.dp)
                .clickable {
                    ChatyHapticsPort.light(view)
                    controller.nextCarouselItem()
                },
        ) {
            IslandVectorIcon(
                icon = androidx.compose.material.icons.Icons.Rounded.ChevronRight,
                color = Color.White.copy(alpha = 0.4f),
                size = 16.dp,
            )
        }
    }
}

/* ─── Expanded Smart Clipboard Intent View ─────────────────────────── */
@Composable
fun ExpandedClipboardIntentView(
    payload: DynamicIslandPayload,
    controller: DynamicIslandController,
) {
    val view = LocalView.current

    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BandiKineticCrest(
                size = 32.dp,
                primaryColor = Color(0xFFF59E0B),
                accentColor = Color(0xFFFBBF24),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    payload.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Smart Context Detected",
                    color = Color(0xFFFBBF24).copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.W600,
                    letterSpacing = 0.3.sp,
                )
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable {
                        ChatyHapticsPort.light(view)
                        controller.dismissActive()
                    },
                contentAlignment = Alignment.Center,
            ) {
                IslandVectorIcon(
                    icon = androidx.compose.material.icons.Icons.Rounded.Close,
                    color = Color(0x8AFFFFFF),
                    size = 18.dp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                payload.clipboardContent ?: payload.message,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xB3FFFFFF),
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF59E0B))
                .clickable {
                    ChatyHapticsPort.threshold(view)
                    payload.onClipboardAction?.invoke()
                    controller.dismissActive()
                }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                payload.clipboardActionLabel ?: "Execute Action",
                color = Color.Black,
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
            )
        }
        if (controller.queue.size > 1) {
            MultiTaskCarouselIndicator(controller = controller)
        }
    }
}

/* ─── Expanded Drag & Go Target View ───────────────────────────────── */
@Composable
fun ExpandedDragTargetView(
    payload: DynamicIslandPayload,
    controller: DynamicIslandController,
) {
    val view = LocalView.current

    Column(Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00F0FF).copy(alpha = 0.15f))
                    .border(1.5.dp, Color(0xFF00F0FF).copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                IslandVectorIcon(
                    icon = androidx.compose.material.icons.Icons.Rounded.FileDownload,
                    color = Color(0xFF00F0FF),
                    size = 24.dp,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            payload.title,
            textAlign = TextAlign.Center,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            payload.message,
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .clickable {
                        ChatyHapticsPort.light(view)
                        controller.setDragTargetActive(false)
                        controller.collapse()
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Dismiss", color = Color(0xB3FFFFFF), fontSize = 11.sp)
            }
        }
    }
}
