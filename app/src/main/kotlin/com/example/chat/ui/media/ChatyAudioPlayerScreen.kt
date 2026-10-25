package com.example.chat.ui.media

import com.example.chat.ui.kit.ChatyHapticsPort
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.kit.springInteractiveScale
import com.example.chat.ui.media.icons.ChatyIconType
import com.example.chat.ui.media.icons.ChatyMediaIcon
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** Exact port of lib/features/media/ui/audio/chaty_audio_player_screen.dart.
 *
 *  Fullscreen audio player: spinning vinyl disc (14s rotation while playing),
 *  seek bar, transport controls, speed pill selector, queue bottom sheet.
 *  Navigation: Dart popped the fullscreen route via the app bar chevron; the
 *  host supplies [onClose]. Backdrop/queue sheet uses ModalBottomSheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatyAudioPlayerScreen(
    coordinator: MediaPlayerCoordinator,
    onClose: () -> Unit = {},
) {
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(coordinator) {
        val listener = { tick++ }
        coordinator.addListener(listener)
        onDispose { coordinator.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    val state = coordinator.state
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()

    // Spinning vinyl disc animation (Dart: 14s AnimationController, repeat
    // while playing, stop otherwise - Animatable.stop() preserves the angle).
    val discAngle = remember { Animatable(0f) }
    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying) {
            discAngle.animateTo(
                targetValue = discAngle.value + 360f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 14000, easing = LinearEasing)),
            )
        } else {
            discAngle.stop()
        }
    }

    var showQueue by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = if (isDark) Color(0xFF0C1017) else Color(0xFFF8FAFC),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Close",
                            modifier = Modifier.size(30.dp),
                        )
                    }
                },
                title = { Text("Now Playing", fontSize = 15.sp, fontWeight = FontWeight.W700) },
                actions = {
                    IconButton(onClick = { showQueue = true }) {
                        ChatyMediaIcon(
                            icon = ChatyIconType.queue,
                            size = 20.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
    ) { padding ->
        val track = state.activeItem
        if (track == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No audio playing")
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Large Spinning Vinyl Disc
                Box(
                    modifier = Modifier
                        .graphicsLayer { rotationZ = discAngle.value }
                        .size(240.dp)
                        .shadow(
                            elevation = 36.dp,
                            shape = CircleShape,
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        )
                        .background(
                            Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.3f to Color(0xFF1E2530),
                                    0.6f to Color(0xFF11161F),
                                    0.85f to MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    1.0f to Color(0xFF0D1117),
                                ),
                            ),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChatyMediaIcon(icon = ChatyIconType.audio, size = 32.dp, color = Color.White)
                    }
                }

                // Track Title & Details
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        track.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.W800,
                            letterSpacing = (-0.3).sp,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${track.humanReadableSize} • Audio Recording",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.54f),
                        ),
                    )
                }

                // Seek Bar & Timers
                Column {
                    val durationMs = state.duration.inWholeMilliseconds
                    Slider(
                        value = if (durationMs > 0)
                            state.position.inWholeMilliseconds.coerceIn(0, durationMs).toFloat()
                        else 0f,
                        onValueChange = { val -> scope.launch { coordinator.seek(val.toLong().milliseconds) } },
                        valueRange = 0f..(if (durationMs > 0) durationMs.toFloat() else 1f),
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.12f),
                            thumbColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.height(24.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            formatDuration(state.position),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W600,
                            color = if (isDark) Color.White.copy(alpha = 0.54f) else Color.Black.copy(alpha = 0.54f),
                        )
                        Text(
                            "- ${formatDuration(state.duration - state.position)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W600,
                            color = if (isDark) Color.White.copy(alpha = 0.54f) else Color.Black.copy(alpha = 0.54f),
                        )
                    }
                }

                // Main Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { coordinator.toggleShuffle() }) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (state.isShuffle) MaterialTheme.colorScheme.primary
                            else (if (isDark) Color.White.copy(alpha = 0.38f) else Color.Black.copy(alpha = 0.38f)),
                        )
                    }
                    IconButton(
                        onClick = { scope.launch { coordinator.skipPrevious() } },
                        enabled = state.hasPrevious,
                    ) {
                        ChatyMediaIcon(
                            icon = ChatyIconType.seekBackward,
                            size = 24.dp,
                            color = if (isDark) Color.White else Color(0xDD000000),
                        )
                    }
                    Box(
                        Modifier.springInteractiveScale(0.94f).clickable {
                            ChatyHapticsPort.threshold(view)
                            scope.launch { coordinator.togglePlayPause() }
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .shadow(
                                    elevation = 18.dp,
                                    shape = CircleShape,
                                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                )
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            ChatyMediaIcon(
                                icon = if (state.isPlaying) ChatyIconType.pause else ChatyIconType.play,
                                size = 28.dp,
                                color = Color.White,
                            )
                        }
                    }
                    IconButton(
                        onClick = { scope.launch { coordinator.skipNext() } },
                        enabled = state.hasNext,
                    ) {
                        ChatyMediaIcon(
                            icon = ChatyIconType.seekForward,
                            size = 24.dp,
                            color = if (isDark) Color.White else Color(0xDD000000),
                        )
                    }
                    IconButton(onClick = { coordinator.cycleRepeatMode() }) {
                        Icon(
                            if (state.repeatMode == ChatyRepeatMode.one) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Repeat",
                            tint = if (state.repeatMode != ChatyRepeatMode.off) MaterialTheme.colorScheme.primary
                            else (if (isDark) Color.White.copy(alpha = 0.38f) else Color.Black.copy(alpha = 0.38f)),
                        )
                    }
                }

                // Speed Pill Row
                SpeedSelector(state, isDark) { s ->
                    ChatyHapticsPort.selection(view)
                    scope.launch { coordinator.setSpeed(s) }
                }
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            containerColor = if (isDark) Color(0xFF141923) else Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            val sheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.6f
            Column(modifier = Modifier.height(sheetHeight)) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Play Queue",
                    fontWeight = FontWeight.W700,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn {
                    items(state.queue.size) { index ->
                        val item = state.queue[index]
                        val isCurrent = index == state.queueIndex
                        ListItem(
                            leadingContent = {
                                ChatyIcon(
                                    icon = if (isCurrent) ChatyIconId.playArrow else ChatyIconId.musicNote,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else null,
                                )
                            },
                            headlineContent = {
                                Text(
                                    item.name,
                                    fontWeight = if (isCurrent) FontWeight.W700 else FontWeight.W500,
                                )
                            },
                            supportingContent = { Text(item.humanReadableSize) },
                            modifier = androidx.compose.ui.Modifier.clickable {
                                scope.launch { coordinator.playTrack(item, queue = state.queue) }
                                showQueue = false
                            },
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedSelector(state: MediaPlaybackState, isDark: Boolean, onSelect: (Double) -> Unit) {
    val speeds = listOf(0.75, 1.0, 1.25, 1.5, 2.0)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isDark) Color(0xFF161C26) else Color(0xFFEDF2F7))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        speeds.forEach { s ->
            val isSelected = kotlin.math.abs(state.speed - s) < 0.01
            Box(Modifier.springInteractiveScale(0.95f).clickable { onSelect(s) }) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        "${s}x",
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.W800 else FontWeight.W500,
                        color = if (isSelected) Color.White
                        else (if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xDD000000)),
                    )
                }
            }
        }
    }
}
