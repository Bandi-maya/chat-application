package com.example.chat.ui.calls

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.StopScreenShare
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.data.services.AudioRouteType
import com.example.chat.data.services.CallSessionState
import com.example.chat.data.services.CallVideoSurfaceProvider
import com.example.chat.data.services.ChatyCallSession
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.delay
import org.webrtc.SurfaceViewRenderer

/** Live filter hook (Dart EffectEngine/EffectRegistry): viewer-side video
 *  filter picker for video calls. Null while the camera-effects slice is
 *  unported - the filter action is hidden, matching "no effect" parity. */
fun interface CallEffectProvider {
    fun showEffectSheet()
}

/**
 * Port of features/calls/ongoing_call_screen.dart: full-screen voice/video
 * call UI backed by real WebRTC media streams. The call timer only runs after
 * the peer connection reports a connected transport; no placeholder camera
 * surface is rendered as a successful call.
 *
 * GB call color mods (ModCallsBackground / ModCallsTextColor /
 * ModCallsIconColors) ride on the preferences controller, which is owned by
 * the settings/preferences slice; until it lands the screen uses the Dart
 * fallbacks (black background, white text/icons).
 */
@Composable
fun OngoingCallScreen(
    session: ChatyCallSession?,
    callDurationSeconds: Int,
    hasLocalMedia: Boolean,
    videoSurfaces: CallVideoSurfaceProvider?,
    setupError: String?,
    template: CallScreenTemplate,
    pictureInPictureEnabled: Boolean,
    onBack: () -> Unit,
    onSessionGone: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleScreenShare: () -> Unit,
    onToggleAudioRoute: (AudioRouteType) -> Unit,
    onEnterNativePip: () -> Unit,
    effectProvider: CallEffectProvider? = null,
) {
    val theme = LocalThemeConfig.current
    var controlsVisible by remember { mutableStateOf(true) }
    var focusMode by remember { mutableStateOf(false) }
    var localPipOffsetX by remember { mutableStateOf(20f) }
    var localPipOffsetY by remember { mutableStateOf(80f) }

    // Dart _pipModeSubscription: system PiP mode drives focus mode and hides controls.
    DisposableEffect(Unit) {
        val l: (Boolean) -> Unit = { active -> focusMode = active; controlsVisible = !active }
        com.example.chat.ServiceLocator.nativeCallPip.addModeListener(l)
        onDispose { com.example.chat.ServiceLocator.nativeCallPip.removeModeListener(l) }
    }

    // Dart _startAutoHideTimer: controls hide after 5s unless focus mode.
    LaunchedEffect(controlsVisible, focusMode) {
        if (controlsVisible && !focusMode) {
            delay(5000)
            controlsVisible = false
        }
    }

    if (session == null) {
        LaunchedEffect(Unit) { onSessionGone() }
        Scaffold(containerColor = Color.Black) { padding ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().padding(padding).padding(28.dp),
            ) {
                Spacer(Modifier.height(140.dp))
                Icon(
                    Icons.Rounded.CallEnd,
                    contentDescription = null,
                    tint = Color(theme.dangerColor),
                    modifier = Modifier.size(42.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("Call unavailable", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(
                    setupError ?: "This call session is no longer available.",
                    color = Color(0xB3FFFFFF),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Close",
                    color = Color(theme.accentColor),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onBack() },
                )
            }
        }
        return
    }

    val isActive = session.isActive
    Scaffold(containerColor = Color.Black) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .clickable {
                    controlsVisible = !controlsVisible
                },
        ) {
            if (session.isVideo) {
                RemoteVideoSurface(session, videoSurfaces, callDurationSeconds)
            } else {
                VoiceCallBackdrop(session, callDurationSeconds)
            }

            // Draggable local preview PiP (Dart _localPipOffset, 120x160).
            if (session.isVideo && hasLocalMedia && !focusMode) {
                val configuration = LocalConfiguration.current
                val screenW = configuration.screenWidthDp.toFloat()
                val screenH = configuration.screenHeightDp.toFloat()
                Box(
                    modifier = Modifier
                        .offset(localPipOffsetX.dp, localPipOffsetY.dp)
                        .size(120.dp, 160.dp)
                        .background(Color.Black, RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                localPipOffsetX = (localPipOffsetX + dragAmount.x / density)
                                    .coerceIn(16f, screenW - 136f)
                                localPipOffsetY = (localPipOffsetY + dragAmount.y / density)
                                    .coerceIn(60f, screenH - 220f)
                            }
                        },
                ) {
                    if (!session.isCameraOff && videoSurfaces != null) {
                        LocalVideoSurface(videoSurfaces, mirror = session.isFrontCamera)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.VideocamOff,
                                contentDescription = null,
                                tint = Color(0xA6FFFFFF),
                            )
                        }
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(32.dp)
                            .background(Color(0x9E000000), CircleShape)
                            .clickable { onSwitchCamera() },
                    ) {
                        Icon(
                            Icons.Rounded.Cameraswitch,
                            contentDescription = "Switch camera",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            if (setupError != null || session.state == CallSessionState.failed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(start = 20.dp, end = 20.dp, top = 96.dp)
                        .background(Color(argbWithAlpha(theme.dangerColor, 0.92)), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Text(
                        setupError ?: "The media connection failed.",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Top panel (Dart AnimatedPositioned slide).
            val topOffset by animateDpAsState(if (controlsVisible) 0.dp else (-110).dp, label = "topPanel")
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = topOffset)
                    .then(topPanelDecoration(template))
                    .then(topPanelPadding(template)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Rounded.ChevronLeft,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            session.remoteDisplayName,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            statusLabel(session, callDurationSeconds),
                            color = Color(0xB8FFFFFF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (session.isVideo) {
                        IconButton(
                            onClick = onEnterNativePip,
                            enabled = isActive, // Dart: only isActive gates; the disabled-in-settings case shows a message in the host
                        ) {
                            Icon(
                                Icons.Rounded.PictureInPicture,
                                contentDescription = "Picture-in-picture",
                                tint = Color.White,
                            )
                        }
                    }
                    if (session.isVideo && effectProvider != null) {
                        IconButton(onClick = { effectProvider.showEffectSheet() }) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = "Call filter",
                                tint = Color.White,
                            )
                        }
                    }
                    IconButton(onClick = { focusMode = !focusMode }) {
                        Icon(
                            if (focusMode) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            contentDescription = if (focusMode) "Exit focus mode" else "Focus mode",
                            tint = Color.White,
                        )
                    }
                }
            }

            // Bottom control bar.
            val bottomOffset by animateDpAsState(if (controlsVisible) 0.dp else 150.dp, label = "bottomPanel")
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = bottomOffset)
                    .then(bottomPanelDecoration(template))
                    .then(bottomPanelPadding(template)),
            ) {
                CallControlButton(
                    label = if (session.isMuted) "Unmute" else "Mute",
                    active = session.isMuted,
                    activeColor = Color(theme.dangerColor),
                    enabled = hasLocalMedia || session.isDemo,
                    onClick = onToggleMute,
                ) { tint ->
                    Icon(
                        if (session.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(23.dp),
                    )
                }
                if (session.isVideo) {
                    CallControlButton(
                        label = if (session.isCameraOff) "Camera on" else "Camera off",
                        active = session.isCameraOff,
                        activeColor = Color(theme.dangerColor),
                        enabled = hasLocalMedia || session.isDemo,
                        onClick = onToggleCamera,
                    ) { tint ->
                        Icon(
                            if (session.isCameraOff) Icons.Rounded.VideocamOff else Icons.Rounded.Videocam,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                    CallControlButton(
                        label = "Switch camera",
                        active = false,
                        activeColor = Color(theme.accentColor),
                        enabled = hasLocalMedia && !session.isCameraOff && !session.isSharingScreen,
                        onClick = onSwitchCamera,
                    ) { tint ->
                        Icon(
                            Icons.Rounded.Cameraswitch,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                    CallControlButton(
                        label = if (session.isSharingScreen) "Stop sharing" else "Share screen",
                        active = session.isSharingScreen,
                        activeColor = Color(0xFF38BDF8),
                        enabled = hasLocalMedia,
                        onClick = onToggleScreenShare,
                    ) { tint ->
                        Icon(
                            if (session.isSharingScreen) Icons.Rounded.StopScreenShare else Icons.Rounded.ScreenShare,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                }
                CallControlButton(
                    label = if (session.audioRoute == AudioRouteType.speaker) "Earpiece" else "Speaker",
                    active = session.audioRoute == AudioRouteType.speaker,
                    activeColor = Color(theme.accentColor),
                    enabled = hasLocalMedia || session.isDemo,
                    onClick = {
                        onToggleAudioRoute(
                            if (session.audioRoute == AudioRouteType.speaker) {
                                AudioRouteType.earpiece
                            } else {
                                AudioRouteType.speaker
                            },
                        )
                    },
                ) { tint ->
                    Icon(
                        if (session.audioRoute == AudioRouteType.speaker) Icons.Rounded.VolumeUp else Icons.Rounded.Hearing,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(23.dp),
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(58.dp)
                        .background(Color(theme.dangerColor), CircleShape)
                        .clickable { onEndCall() },
                ) {
                    Icon(
                        Icons.Rounded.CallEnd,
                        contentDescription = "End call",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

/** Dart _statusLabel. */
fun statusLabel(session: ChatyCallSession, callDurationSeconds: Int): String =
    when (session.state) {
        CallSessionState.initiating -> "Preparing secure media…"
        CallSessionState.ringing -> "Ringing…"
        CallSessionState.incoming -> "Incoming call"
        CallSessionState.connecting -> "Connecting media…"
        CallSessionState.connected -> formatCallDuration(callDurationSeconds)
        CallSessionState.reconnecting -> "Reconnecting…"
        CallSessionState.busy -> "Busy"
        CallSessionState.missed -> "Missed"
        CallSessionState.failed -> "Connection failed"
        CallSessionState.declined -> "Declined"
        CallSessionState.ended -> "Call ended"
        CallSessionState.idle -> "Preparing call…"
    }

/** Dart _formatDuration: mm:ss. */
fun formatCallDuration(totalSeconds: Int): String {
    val m = (totalSeconds / 60).toString().padStart(2, '0')
    val s = (totalSeconds % 60).toString().padStart(2, '0')
    return "$m:$s"
}

@Composable
private fun CallControlButton(
    label: String,
    active: Boolean,
    activeColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit,
) {
    val tint = if (active) activeColor else Color.White
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(54.dp)
            .background(
                if (active) activeColor.copy(alpha = 0.28f) else Color(0x29FFFFFF),
                RoundedCornerShape(16.dp),
            )
            .clickable(enabled = enabled) { onClick() },
    ) {
        icon(tint)
    }
}

@Composable
private fun RemoteVideoSurface(
    session: ChatyCallSession,
    videoSurfaces: CallVideoSurfaceProvider?,
    callDurationSeconds: Int,
) {
    if (videoSurfaces == null || !videoSurfaces.hasRemoteVideo) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().background(Color(0xFF07090D)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CallAvatar(
                    initials = session.remoteAvatarInitials
                        ?: (if (session.remoteDisplayName.isEmpty()) "U"
                            else session.remoteDisplayName.substring(0, 1).uppercase()),
                    colorHex = session.remoteAvatarColorHex,
                    size = 108.dp,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    statusLabel(session, callDurationSeconds),
                    color = Color(0xB3FFFFFF),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(
                    strokeWidth = 2.2.dp,
                    color = Color(0xB3FFFFFF),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        return
    }
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                videoSurfaces.attachRemoteRenderer(this)
            }
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = { renderer -> videoSurfaces.detachRenderer(renderer) },
    )
}

@Composable
private fun LocalVideoSurface(videoSurfaces: CallVideoSurfaceProvider, mirror: Boolean) {
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                setMirror(mirror)
                videoSurfaces.attachLocalRenderer(this)
            }
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = { renderer -> videoSurfaces.detachRenderer(renderer) },
    )
}

@Composable
private fun VoiceCallBackdrop(session: ChatyCallSession, callDurationSeconds: Int) {
    val theme = LocalThemeConfig.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(Color(theme.cardColor)),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CallAvatar(
                initials = session.remoteAvatarInitials
                    ?: (if (session.remoteDisplayName.isNotEmpty()) {
                        session.remoteDisplayName.substring(0, 1).uppercase()
                    } else {
                        "U"
                    }),
                colorHex = session.remoteAvatarColorHex,
                size = 110.dp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                session.remoteDisplayName,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                statusLabel(session, callDurationSeconds),
                color = Color(0xB8FFFFFF),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── Template decorations (Dart _topPanelDecoration/_bottomPanelDecoration) ──

private fun topPanelDecoration(template: CallScreenTemplate): Modifier =
    when (template) {
        CallScreenTemplate.card -> Modifier
            .padding(start = 16.dp, top = 8.dp, end = 16.dp)
            .background(Color(0x8C000000), RoundedCornerShape(20.dp))
        CallScreenTemplate.edge -> Modifier
        else -> Modifier.background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(Color(0xC2000000), Color.Transparent),
            ),
        )
    }

private fun topPanelPadding(template: CallScreenTemplate): Modifier =
    if (template == CallScreenTemplate.card) {
        Modifier.padding(8.dp)
    } else {
        Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp)
    }

private fun bottomPanelDecoration(template: CallScreenTemplate): Modifier =
    when (template) {
        CallScreenTemplate.sheet -> Modifier.background(
            Color(0xF2141414),
            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        )
        CallScreenTemplate.card -> Modifier
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            .background(Color(0x99000000), RoundedCornerShape(24.dp))
        CallScreenTemplate.edge -> Modifier
        CallScreenTemplate.chatyClassic -> Modifier.background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xD6000000)),
            ),
        )
    }

private fun bottomPanelPadding(template: CallScreenTemplate): Modifier =
    when (template) {
        CallScreenTemplate.card -> Modifier.padding(12.dp)
        CallScreenTemplate.sheet -> Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp)
        CallScreenTemplate.edge -> Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
        CallScreenTemplate.chatyClassic -> Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
    }
