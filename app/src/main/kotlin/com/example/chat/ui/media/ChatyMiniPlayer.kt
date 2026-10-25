package com.example.chat.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.kit.springInteractiveScale
import com.example.chat.ui.media.icons.ChatyIconType
import com.example.chat.ui.media.icons.ChatyMediaIcon
import kotlinx.coroutines.launch
import kotlin.time.Duration

/** Exact port of lib/features/media/ui/audio/chaty_mini_player.dart.
 *
 *  Deviation: Flutter BackdropFilter (blur-behind) has no Compose equivalent;
 *  the translucent container colors from Dart already carry the frosted alpha
 *  (0.92/0.94), so the visual result matches without a backdrop blur.
 *  Navigation: Dart pushed ChatyAudioPlayerScreen fullscreen; here [onExpand].
 */
@Composable
fun ChatyMiniPlayer(
    coordinator: MediaPlayerCoordinator,
    onExpand: () -> Unit = {},
) {
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(coordinator) {
        val listener: () -> Unit = { tick++ }
        coordinator.addListener(listener)
        onDispose { coordinator.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    val state = coordinator.state
    val scope = rememberCoroutineScope()

    if (!state.hasActiveItem || state.status == PlaybackStatus.idle || state.status == PlaybackStatus.stopped) {
        return
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val track = state.activeItem ?: return

    Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
        Box(Modifier.springInteractiveScale(0.98f).clickable(onClick = onExpand)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.08f),
                        spotColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.08f),
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isDark) Color(0xFF1B2232).copy(alpha = 0.92f)
                        else Color.White.copy(alpha = 0.94f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(18.dp),
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChatyMediaIcon(
                            icon = ChatyIconType.audio,
                            size = 18.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            track.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.W700,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${formatDuration(state.position)} / ${formatDuration(state.duration)}",
                            fontSize = 11.sp,
                            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.54f),
                        )
                    }
                    IconButton(onClick = { scope.launch { coordinator.togglePlayPause() } }) {
                        ChatyMediaIcon(
                            icon = if (state.isPlaying) ChatyIconType.pause else ChatyIconType.play,
                            size = 18.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { scope.launch { coordinator.stop() } }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Stop", modifier = Modifier.size(18.dp))
                    }
                }
                LinearProgressIndicator(
                    progress = { state.progress.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(2.5.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.12f),
                )
            }
        }
    }
}

/** Dart _formatDuration: m:ss with 2-digit padding, minutes mod 60. */
internal fun formatDuration(d: Duration): String {
    if (d.isNegative()) return "00:00"
    val m = (d.inWholeMinutes % 60).toString().padStart(2, '0')
    val s = (d.inWholeSeconds % 60).toString().padStart(2, '0')
    return "$m:$s"
}
