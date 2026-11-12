package com.example.chat.island.engine

import com.example.chat.island.ui.components.IslandIcon as Icon
import com.example.chat.R
import androidx.compose.ui.res.stringResource
import android.os.BatteryManager
import android.os.SystemClock
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.chat.island.core.IslandSettings
import com.example.chat.island.core.KindStyle
import com.example.chat.island.core.SwipeDir
import com.example.chat.island.core.kindOf
import kotlinx.coroutines.delay
import java.text.BreakIterator
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

private val panelInk = Color(0xFF090B10)
private val panelText = Color(0xFFF7F8FA)
private val panelMuted = Color(0xFFA7AFBF)
private val positive = Color(0xFF52D9A0)
private val caution = Color(0xFFFFBE60)
private val negative = Color(0xFFFF6D83)

@Composable
fun IslandTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = positive, onPrimary = panelInk, surface = panelInk, onSurface = panelText,
        onSurfaceVariant = panelMuted, background = panelInk, onBackground = panelText
    ), content = content)
}

private data class Controls(
    val previous: () -> Unit, val play: () -> Unit, val next: () -> Unit, val seek: (Long) -> Unit,
    val answer: () -> Unit, val decline: () -> Unit, val read: () -> Unit, val reply: () -> Unit,
    val send: (String) -> Unit, val cancel: () -> Unit, val openMusic: () -> Unit,
    val editing: Boolean
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DynamicIslandUi(
    mode: IslandMode, level: IslandLevel, scale: Float = 1f, widthScale: Float = 1f,
    heightScale: Float = 1f, cornerFactor: Float = 1f, animSpeed: Float = 1f,
    glow: Boolean = true, haptics: Boolean = true, onClick: () -> Unit,
    onLongPress: () -> Unit = {}, onDismiss: () -> Unit = {}, onPrev: () -> Unit = {},
    onPlayPause: () -> Unit = {}, onNext: () -> Unit = {}, onSeek: (Long) -> Unit = {},
    onAnswer: () -> Unit = {}, onDecline: () -> Unit = {}, onMarkRead: () -> Unit = {},
    onReply: () -> Unit = {}, onSendReply: (String) -> Unit = {}, onCancelReply: () -> Unit = {},
    replying: Boolean = false, onSwipeSide: (Int) -> Unit = {}, appAccent: Color? = null,
    onOpenMedia: () -> Unit = {}, secondary: IslandMode.Media? = null, queued: Int = 0,
    onSwipe: ((SwipeDir) -> Unit)? = null, modifier: Modifier = Modifier, styleOverride: KindStyle? = null
) {
    val customizations by IslandSettings.kindStyles.collectAsState()
    val style = styleOverride ?: customizations[kindOf(mode)?.key] ?: KindStyle()
    val expanded = level == IslandLevel.Expanded || level == IslandLevel.Large
    val effectiveRoundness = (if (style.corner < 0) cornerFactor else style.corner).coerceIn(.2f, 1f)
    val density = LocalDensity.current
    val screen = LocalConfiguration.current.screenWidthDp
    val zoom = scale.coerceIn(.3f, 2.5f)
    val controls = Controls(onPrev, onPlayPause, onNext, onSeek, onAnswer, onDecline,
        onMarkRead, onReply, onSendReply, onCancelReply, onOpenMedia, replying)
    val mini = secondary.takeIf { mode is IslandMode.Timer || mode is IslandMode.Live }
    val accent = appAccent ?: (mode as? IslandMode.Media)?.accent?.let { Color(it) } ?: positive
    val targetWidth = if (expanded) 356f * style.cardW else when (level) {
        IslandLevel.Banner -> 308f * style.w
        else -> (if (mini == null) 224f else 286f) * style.w
    }
    val width = (targetWidth * widthScale).coerceIn(100f, (screen / zoom - 16f).coerceAtLeast(100f))
    val swipeHandler by rememberUpdatedState(onSwipe)
    val sideHandler by rememberUpdatedState(onSwipeSide)
    val dismissHandler by rememberUpdatedState(onDismiss)
    val haptic = LocalHapticFeedback.current
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(level) {
        if (!first && haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        first = false
    }
    val paddingY = (if (expanded) 18f * style.cardH else 10f * style.h) * heightScale
    CompositionLocalProvider(LocalDensity provides Density(density.density * zoom, density.fontScale * style.text)) {
        IslandTheme {
            Column(
                modifier.width(width.dp)
                    .animateContentSize(tween((240 / animSpeed.coerceIn(.5f, 2f)).toInt()))
                    .clip(RoundedCornerShape((if (expanded) 30f else 50f) * effectiveRoundness))
                    .background(panelInk)
                    .border(if (glow) 1.dp else 0.dp, if (glow) accent.copy(alpha = .35f) else Color.Transparent,
                        RoundedCornerShape((if (expanded) 30f else 50f) * effectiveRoundness))
                    .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                    .pointerInput(Unit) {
                        var horizontal = 0f
                        var vertical = 0f
                        detectDragGestures(
                            onDragStart = { horizontal = 0f; vertical = 0f },
                            onDragEnd = {
                                val threshold = 40.dp.toPx()
                                val direction = when {
                                    abs(horizontal) >= threshold && abs(horizontal) > abs(vertical) ->
                                        if (horizontal > 0) SwipeDir.Right else SwipeDir.Left
                                    abs(vertical) >= threshold -> if (vertical > 0) SwipeDir.Down else SwipeDir.Up
                                    else -> null
                                }
                                if (direction != null) {
                                    val action = swipeHandler
                                    if (action != null) action(direction)
                                    else when (direction) {
                                        SwipeDir.Up -> dismissHandler()
                                        SwipeDir.Left -> sideHandler(1)
                                        SwipeDir.Right -> sideHandler(-1)
                                        SwipeDir.Down -> {}
                                    }
                                }
                            }
                        ) { change, distance ->
                            horizontal += distance.x
                            vertical += distance.y
                            change.consume()
                        }
                    }
                    .padding(horizontal = if (expanded) 18.dp else 14.dp, vertical = paddingY.coerceAtLeast(4f).dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (expanded) ExpandedContent(mode, level == IslandLevel.Large, controls, accent)
                else SmallContent(mode, level == IslandLevel.Banner, queued, accent)
                if (expanded && controls.editing) ReplyEditor(controls.send, controls.cancel)
                if (mini != null) {
                    if (expanded) {
                        HorizontalDivider(color = panelMuted.copy(alpha = .25f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = controls.openMusic, modifier = Modifier.weight(1f)) {
                                Text(mini.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            PlaybackButtons(mini.playing, controls, compact = true)
                        }
                    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        SoundBars(mini.playing, accent)
                        Spacer(Modifier.width(8.dp))
                        Caption(mini.title, Modifier.weight(1f), maxLines = 1)
                    }
                }
                if (expanded && queued > 0) Caption(stringResource(R.string.island_waiting, queued))
            }
        }
    }
}

@Composable
private fun toggleName(value: ToggleKind): String = when (value) {
    ToggleKind.Silent -> stringResource(R.string.island_silent)
    ToggleKind.Vibrate -> stringResource(R.string.island_vibrate)
    ToggleKind.Dnd -> stringResource(R.string.island_dnd)
    ToggleKind.Flashlight -> stringResource(R.string.island_flashlight)
}

@Composable
private fun ModeIcon(mode: IslandMode, tint: Color, modifier: Modifier = Modifier) {
    val vector = when (mode) {
        is IslandMode.Notification -> Icons.Rounded.Notifications
        is IslandMode.Charging -> if (mode.connected) Icons.Rounded.Bolt else Icons.Rounded.BatteryStd
        is IslandMode.LowBattery -> Icons.Rounded.BatteryAlert
        is IslandMode.Device -> if (mode.headphones) Icons.Rounded.Headphones else Icons.Rounded.Bluetooth
        is IslandMode.Call -> Icons.Rounded.Call
        is IslandMode.Timer -> Icons.Rounded.Timer
        is IslandMode.Media -> Icons.Rounded.MusicNote
        is IslandMode.Live -> when (mode.kind) {
            LiveKind.Navigation -> Icons.Rounded.Navigation
            LiveKind.Tracking -> Icons.Rounded.DeliveryDining
            LiveKind.Download -> Icons.Rounded.Download
        }
        is IslandMode.Toggle -> when (mode.kind) {
            ToggleKind.Silent -> Icons.Rounded.NotificationsOff
            ToggleKind.Vibrate -> Icons.Rounded.Vibration
            ToggleKind.Dnd -> Icons.Rounded.DoNotDisturbOn
            ToggleKind.Flashlight -> Icons.Rounded.FlashOn
        }
        IslandMode.Idle -> Icons.Rounded.Schedule
    }
    Icon(vector, null, modifier.size(22.dp), tint = tint)
}

@Composable
private fun summary(mode: IslandMode): Pair<String, String> = when (mode) {
    is IslandMode.Notification -> mode.title to mode.text
    is IslandMode.Media -> mode.title to mode.artist
    is IslandMode.Charging -> (if (mode.connected) stringResource(R.string.island_charging) else stringResource(R.string.island_unplugged)) to "${mode.percent}%"
    is IslandMode.LowBattery -> stringResource(R.string.island_low_battery) to stringResource(R.string.island_remaining, mode.percent)
    is IslandMode.Device -> mode.name to (if (mode.headphones) stringResource(R.string.island_headphones_connected) else stringResource(R.string.island_device_connected))
    is IslandMode.Toggle -> toggleName(mode.kind) to (if (mode.on) stringResource(R.string.island_on) else stringResource(R.string.island_off))
    is IslandMode.Call -> mode.name to (if (mode.incoming) stringResource(R.string.island_incoming_call) else stringResource(R.string.island_call_active))
    is IslandMode.Timer -> timeLabel(mode.remainingSec.toLong()) to stringResource(R.string.island_timer)
    is IslandMode.Live -> mode.title to mode.text
    IslandMode.Idle -> "" to ""
}

@Composable
private fun SmallContent(mode: IslandMode, banner: Boolean, waiting: Int, accent: Color) {
    if (mode == IslandMode.Idle) {
        val clock by wallClock()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ModeIcon(mode, accent)
            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(clock)), color = panelText)
        }
        return
    }
    val (title, detail) = summary(mode)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (mode is IslandMode.Media) Artwork(mode, 30) else ModeIcon(mode, when (mode) {
            is IslandMode.LowBattery -> negative
            is IslandMode.Charging -> positive
            else -> accent
        })
        Column(Modifier.weight(1f)) {
            Text(title, color = panelText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge)
            if (banner) Caption(detail, maxLines = 2)
        }
        when (mode) {
            is IslandMode.Timer -> CircularProgressIndicator(
                progress = { (mode.remainingSec.toFloat() / mode.totalSec.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.size(22.dp), color = accent, strokeWidth = 2.dp)
            is IslandMode.Media -> SoundBars(mode.playing, accent)
            is IslandMode.Charging -> Caption("${mode.percent}%")
            is IslandMode.LowBattery -> Caption("${mode.percent}%")
            is IslandMode.Live -> if (mode.progress >= 0) Caption("${mode.progress}%")
            else -> {}
        }
        if (waiting > 0) Text("+$waiting", color = accent, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ExpandedContent(mode: IslandMode, large: Boolean, controls: Controls, accent: Color) {
    if (mode == IslandMode.Idle) {
        IdleDetails()
        return
    }
    val (title, detail) = summary(mode)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when (mode) {
            is IslandMode.Media -> Artwork(mode, if (large) 72 else 52)
            is IslandMode.Notification -> InitialBadge(mode.title)
            is IslandMode.Call -> InitialBadge(mode.name)
            else -> ModeIcon(mode, accent, Modifier.padding(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = panelText, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, maxLines = if (large) 3 else 2, overflow = TextOverflow.Ellipsis)
            if (mode is IslandMode.Notification) Caption(mode.app)
            else if (mode !is IslandMode.Timer) Caption(detail, maxLines = if (large) 6 else 3)
        }
    }
    when (mode) {
        is IslandMode.Notification -> {
            Text(detail, color = panelText, style = MaterialTheme.typography.bodyMedium,
                maxLines = if (large) 12 else 5, overflow = TextOverflow.Ellipsis)
            if (!controls.editing) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode.canMarkRead) OutlinedButton(onClick = controls.read) { Text(stringResource(R.string.island_mark_read)) }
                if (mode.canReply) Button(onClick = controls.reply) { Text(stringResource(R.string.island_reply)) }
            }
        }
        is IslandMode.Media -> {
            MediaProgress(mode, controls.seek, accent)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                PlaybackButtons(mode.playing, controls)
            }
        }
        is IslandMode.Call -> {
            if (!mode.incoming) {
                val elapsed by elapsedSeconds(mode.since)
                Caption(timeLabel(elapsed))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (mode.incoming && mode.canAnswer) Button(onClick = controls.answer,
                    colors = ButtonDefaults.buttonColors(containerColor = positive), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Call, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.island_answer))
                }
                if (mode.canDecline) Button(onClick = controls.decline, colors = ButtonDefaults.buttonColors(containerColor = negative),
                    modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.CallEnd, null); Spacer(Modifier.width(6.dp))
                    Text(if (mode.incoming) stringResource(R.string.island_decline) else stringResource(R.string.island_end_call))
                }
            }
        }
        is IslandMode.Timer -> {
            LinearProgressIndicator(progress = { (mode.remainingSec.toFloat() / mode.totalSec.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(), color = accent)
            OutlinedButton(onClick = { IslandController.stopTimer() }) { Text(if (mode.external) stringResource(R.string.island_hide_timer) else stringResource(R.string.island_stop_timer)) }
        }
        is IslandMode.Charging -> BatteryProgress(mode.percent, positive)
        is IslandMode.LowBattery -> BatteryProgress(mode.percent, negative)
        is IslandMode.Live -> {
            Caption(mode.app)
            if (mode.kind == LiveKind.Download && mode.progress >= 0) {
                LinearProgressIndicator(progress = { (mode.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(), color = accent)
                Caption("${mode.progress}%")
            }
        }
        else -> {}
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier, maxLines: Int = 2) {
    Text(text, modifier, color = panelMuted, style = MaterialTheme.typography.bodySmall,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun PlaybackButtons(playing: Boolean, controls: Controls, compact: Boolean = false) {
    val size = if (compact) 34.dp else 48.dp
    IconButton(onClick = controls.previous, modifier = Modifier.size(size)) {
        Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.island_previous), tint = panelText)
    }
    IconButton(onClick = controls.play, modifier = Modifier.size(size)) {
        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (playing) stringResource(R.string.island_pause) else stringResource(R.string.island_play), tint = positive)
    }
    IconButton(onClick = controls.next, modifier = Modifier.size(size)) {
        Icon(Icons.Rounded.SkipNext, stringResource(R.string.island_next), tint = panelText)
    }
}

@Composable
private fun Artwork(media: IslandMode.Media, size: Int) {
    val bitmap = media.art?.takeUnless { it.isRecycled }
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(positive.copy(alpha = .12f)),
        contentAlignment = Alignment.Center) {
        if (bitmap == null) Icon(Icons.Rounded.MusicNote, null, tint = positive)
        else Image(bitmap.asImageBitmap(), stringResource(R.string.island_artwork), Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    }
}

@Composable
private fun InitialBadge(label: String) {
    val initial = remember(label) {
        if (label.isBlank()) "?" else {
            val iterator = BreakIterator.getCharacterInstance()
            iterator.setText(label)
            label.substring(0, iterator.next().coerceAtLeast(1))
        }
    }
    Box(Modifier.size(44.dp).clip(CircleShape).background(positive.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
        Text(initial, color = positive, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun BatteryProgress(percent: Int, color: Color) {
    LinearProgressIndicator(progress = { percent.coerceIn(0, 100) / 100f }, color = color, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ReplyEditor(send: (String) -> Unit, cancel: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.island_reply)) }, maxLines = 4,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { if (draft.isNotBlank()) send(draft) }))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = cancel) { Text(stringResource(R.string.island_cancel)) }
        Button(onClick = { send(draft) }, enabled = draft.isNotBlank()) {
            Icon(Icons.AutoMirrored.Rounded.Send, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.island_send))
        }
    }
}

@Composable
private fun MediaProgress(media: IslandMode.Media, seek: (Long) -> Unit, accent: Color) {
    val position by produceState(media.positionMs, media) {
        while (true) {
            val elapsed = if (media.playing && media.updatedAt > 0) SystemClock.elapsedRealtime() - media.updatedAt else 0L
            value = (media.positionMs + (elapsed * media.speed).toLong()).coerceIn(0, media.durationMs.coerceAtLeast(0))
            delay(500)
        }
    }
    var scrub by remember(media.title) { mutableStateOf<Float?>(null) }
    if (media.durationMs > 0) {
        Slider(value = scrub ?: position.toFloat(), onValueChange = { scrub = it },
            valueRange = 0f..media.durationMs.toFloat(), onValueChangeFinished = {
                scrub?.let { seek(it.toLong()) }; scrub = null
            }, colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Caption(timeLabel((scrub?.toLong() ?: position) / 1000))
            Caption(timeLabel(media.durationMs / 1000))
        }
    }
}

@Composable
private fun SoundBars(playing: Boolean, accent: Color) {
    val pulse = rememberInfiniteTransition(label = "musicPulse")
    val amount by pulse.animateFloat(.25f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "barHeight")
    Row(Modifier.height(22.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for (index in 0..3) {
            val fraction = if (!playing) .3f else if (index % 2 == 0) amount else 1.25f - amount
            Box(Modifier.width(3.dp).height((22 * fraction).dp).clip(CircleShape).background(accent))
        }
    }
}

@Composable
private fun wallClock(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) { value = System.currentTimeMillis(); delay(1000) }
}

@Composable
private fun elapsedSeconds(since: Long): State<Long> = produceState(0L, since) {
    while (true) { value = ((SystemClock.elapsedRealtime() - since) / 1000).coerceAtLeast(0); delay(1000) }
}

@Composable
private fun IdleDetails() {
    val now by wallClock()
    val context = LocalContext.current
    val battery = remember(now / 60_000) {
        (context.getSystemService(android.content.Context.BATTERY_SERVICE) as? BatteryManager)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)), color = panelText,
        style = MaterialTheme.typography.headlineLarge)
    Caption(DateFormat.getDateInstance(DateFormat.FULL).format(Date(now)))
    if (battery != null && battery in 0..100) BatteryProgress(battery, positive)
}

private fun timeLabel(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    return if (hours == 0L) "%d:%02d".format(safe / 60, safe % 60)
    else "%d:%02d:%02d".format(hours, safe / 60 % 60, safe % 60)
}
