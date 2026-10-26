package com.example.chat.ui.media

import com.example.chat.ui.kit.ChatyHapticsPort
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import com.example.chat.ui.kit.ChatyDropdownMenu as DropdownMenu
import com.example.chat.ui.kit.ChatyDropdownMenuItem as DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.chat.ui.kit.springInteractiveScale
import com.example.chat.ui.media.icons.ChatyIconType
import com.example.chat.ui.media.icons.ChatyMediaIcon
import kotlinx.coroutines.delay
import java.io.File

/** Exact port of lib/features/media/ui/video/chaty_video_player_widget.dart.
 *
 *  video_player (Flutter Texture + ExoPlayer under the hood) -> Media3
 *  ExoPlayer + PlayerView directly. Same behaviors: tap toggles controls,
 *  double-tap left/right seeks -/+10s with feedback pill, controls auto-hide
 *  after 3500ms while playing, speed menu, contain/fill aspect toggle,
 *  fullscreen = landscape + immersive sticky (restored on dispose).
 *
 *  OWNER DEP: androidx.media3:media3-exoplayer + media3-ui (free, androidx).
 */
@Composable
fun ChatyVideoPlayerWidget(
    videoPath: String,
    isNetworkUrl: Boolean = false,
    title: String? = null,
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val view = LocalView.current

    var isInitialized by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var areControlsVisible by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isFillMode by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableDoubleStateOf(1.0) }
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            val item = if (isNetworkUrl) MediaItem.fromUri(videoPath)
            else MediaItem.fromUri(android.net.Uri.fromFile(File(videoPath)))
            setMediaItem(item)
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    isInitialized = true
                    durationMs = player.duration.coerceAtLeast(0)
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }
            override fun onPlayerError(error: PlaybackException) {
                hasError = true
                errorMessage = error.toString()
            }
        }
        player.addListener(listener)
        if (autoPlay) player.play()
        onDispose {
            player.removeListener(listener)
            player.release()
            if (isFullscreen) {
                (context as? Activity)?.let { restoreOrientation(it) }
            }
        }
    }

    // Position polling (video_player value listener equivalent)
    LaunchedEffect(player, isPlaying) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(0)
            delay(if (isPlaying) 250 else 1000)
        }
    }

    // Controls auto-dismiss: 3500ms while playing
    var controlsTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(areControlsVisible, isPlaying, controlsTick) {
        if (areControlsVisible && isPlaying) {
            delay(3500)
            areControlsVisible = false
        }
    }

    fun startControlsTimer() { controlsTick++ }

    // Seek feedback pill: 800ms
    LaunchedEffect(seekFeedbackText) {
        if (seekFeedbackText != null) {
            delay(800)
            seekFeedbackText = null
        }
    }

    fun toggleControls() {
        areControlsVisible = !areControlsVisible
        if (areControlsVisible) startControlsTimer()
    }

    fun togglePlayPause() {
        ChatyHapticsPort.light(view)
        if (player.isPlaying) {
            player.pause()
            areControlsVisible = true
        } else {
            player.play()
            startControlsTimer()
        }
    }

    fun seekRelative(seconds: Int) {
        ChatyHapticsPort.selection(view)
        val newPos = (player.currentPosition + seconds * 1000L)
            .coerceIn(0, player.duration.coerceAtLeast(0))
        player.seekTo(newPos)
        seekFeedbackText = if (seconds > 0) "+${seconds}s" else "${seconds}s"
        startControlsTimer()
    }

    fun toggleFullscreen() {
        ChatyHapticsPort.threshold(view)
        val next = !isFullscreen
        isFullscreen = next
        val activity = context as? Activity ?: return
        if (next) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            activity.window?.let { w ->
                WindowCompat.getInsetsController(w, view).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        } else {
            restoreOrientation(activity)
        }
        startControlsTimer()
    }

    if (hasError) {
        Box(
            modifier = Modifier.height(220.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(8.dp))
                Text("Cannot play this video format", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(errorMessage, color = Color.White.copy(alpha = 0.54f), fontSize = 11.sp,
                    textAlign = TextAlign.Center, maxLines = 2)
            }
        }
        return
    }

    if (!isInitialized) {
        Box(
            modifier = Modifier.height(220.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }

    val aspect = if (videoWidth > 0 && videoHeight > 0) videoWidth.toFloat() / videoHeight else 16f / 9f
    val controlsAlpha by animateFloatAsState(if (areControlsVisible) 1f else 0f, tween(240), label = "controls")

    Box(
        modifier = Modifier
            .then(if (isFullscreen) Modifier.fillMaxSize() else Modifier.aspectRatio(aspect))
            .clip(RoundedCornerShape(if (isFullscreen) 0.dp else 16.dp))
            .background(Color.Black),
    ) {
        // Video Surface (FittedBox contain/cover -> PlayerView resize modes)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = { v ->
                v.resizeMode = if (isFillMode) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Gesture Areas for Double-Tap Seek and Tap Controls
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxSize().pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { toggleControls() },
                        onDoubleTap = { seekRelative(-10) },
                    )
                },
            )
            Box(
                modifier = Modifier.weight(1f).fillMaxSize().pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { toggleControls() },
                        onDoubleTap = { seekRelative(10) },
                    )
                },
            )
        }

        // Seek Ripple Feedback Pill
        seekFeedbackText?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.W800) }
        }

        // Buffering indicator
        if (isBuffering) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        // Controls Overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(controlsAlpha)
                .then(if (controlsAlpha == 0f) Modifier.pointerInput(Unit) {} else Modifier)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.75f),
                            0.5f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
                ),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (title != null) {
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White,
                        fontWeight = FontWeight.W700,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = {
                    isFillMode = !isFillMode
                    startControlsTimer()
                }) {
                    Icon(
                        if (isFillMode) Icons.Rounded.FitScreen else Icons.Rounded.AspectRatio,
                        contentDescription = if (isFillMode) "Fit inside" else "Fill frame",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                var speedMenuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { speedMenuOpen = true }) {
                        Icon(Icons.Rounded.Speed, contentDescription = "Playback Speed", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(shape = com.example.chat.ui.kit.ChatyModalShape, expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                        listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0).forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${s}x") },
                                onClick = {
                                    player.setPlaybackSpeed(s.toFloat())
                                    currentSpeed = s
                                    speedMenuOpen = false
                                    startControlsTimer()
                                },
                            )
                        }
                    }
                }
            }

            // Center Big Play/Pause
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .springInteractiveScale(0.92f)
                        .clickable { togglePlayPause() }
                        .size(58.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    ChatyMediaIcon(
                        icon = if (isPlaying) ChatyIconType.pause else ChatyIconType.play,
                        size = 24.dp,
                        color = Color.White,
                    )
                }
            }

            // Bottom Scrub Bar & Timers
            Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) {
                Slider(
                    value = positionMs.coerceIn(0, durationMs).toFloat(),
                    onValueChange = { v ->
                        player.seekTo(v.toLong())
                        startControlsTimer()
                    },
                    valueRange = 0f..(if (durationMs > 0) durationMs.toFloat() else 1f),
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                        thumbColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.height(20.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(kotlin.time.Duration.parse("${positionMs}ms")),
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.W600)
                    Text(" / ", color = Color.White.copy(alpha = 0.54f), fontSize = 11.sp)
                    Text(formatDuration(kotlin.time.Duration.parse("${durationMs}ms")),
                        color = Color.White.copy(alpha = 0.54f), fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { toggleFullscreen() }) {
                        ChatyMediaIcon(
                            icon = if (isFullscreen) ChatyIconType.exitFullscreen else ChatyIconType.fullscreen,
                            size = 18.dp,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

private fun restoreOrientation(activity: Activity) {
    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    activity.window?.let { w ->
        WindowCompat.getInsetsController(w, w.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }
}
