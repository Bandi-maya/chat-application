package com.example.chat.ui.calls

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.StopScreenShare
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
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
    autoHideControls: Boolean = true,
    bundleId: com.example.chat.ui.calls.bundles.CallBundleId? = null,
    observeNativePip: Boolean = true,
    onCallTools: (() -> Unit)? = null,
    callToolsStatus: String? = null,
    reactionText: String? = null,
    onRequestVideo: (() -> Unit)? = null,
    onReturnToAudio: (() -> Unit)? = null,
    videoUpgradeStatus: String = "Video upgrade pending server and peer support",
) {
    val reduceMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
    val theme = LocalThemeConfig.current
    val bundleTokens = bundleId?.let { themedCallBundle(it) }
    val iosLayout = bundleId == com.example.chat.ui.calls.bundles.CallBundleId.APPLE_IOS ||
        bundleId == com.example.chat.ui.calls.bundles.CallBundleId.CHATY_DEFAULT
    val callAccent = com.example.chat.ui.theme.LocalAppColors.current.accent
    val endCallColor = com.example.chat.ui.theme.LocalAppColors.current.error
    val controlBackground = Color(theme.cardColor)
    var controlsVisible by remember { mutableStateOf(true) }
    var focusMode by remember { mutableStateOf(false) }
    var localPipOffsetX by remember { mutableStateOf(20f) }
    var localPipOffsetY by remember { mutableStateOf(80f) }

    // Dart _pipModeSubscription: system PiP mode drives focus mode and hides controls.
    // Read-only previews render this same production component without subscribing
    // to the user's live call/PiP service. Runtime keeps the existing observer.
    if (observeNativePip) {
        DisposableEffect(Unit) {
            val l: (Boolean) -> Unit = { active -> focusMode = active; controlsVisible = !active }
            com.example.chat.ServiceLocator.nativeCallPip.addModeListener(l)
            onDispose { com.example.chat.ServiceLocator.nativeCallPip.removeModeListener(l) }
        }
    }

    // Dart _startAutoHideTimer: controls hide after 5s unless focus mode.
    LaunchedEffect(controlsVisible, focusMode, autoHideControls, session?.isVideo) {
        if (session?.isVideo == true && autoHideControls && controlsVisible && !focusMode) {
            delay(5000)
            controlsVisible = false
        }
    }

    if (session == null) {
        LaunchedEffect(Unit) { onSessionGone() }
        Scaffold(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing), contentWindowInsets = WindowInsets(0, 0, 0, 0), containerColor = callBackdrop()) { padding ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().padding(padding).padding(28.dp),
            ) {
                Spacer(Modifier.height(140.dp))
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.callEnd,
                    semanticLabel = null,
                    color = Color(theme.dangerColor),
                    modifier = Modifier.size(42.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("Call unavailable", color = callForeground(), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(
                    setupError ?: "This call session is no longer available.",
                    color = callSecondary(),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(14.dp))
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        "Close",
                        color = Color(theme.accentColor),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        return
    }

    LaunchedEffect(session.callId, session.state) {
        if (session.state == CallSessionState.busy || session.state == CallSessionState.failed) {
            delay(2400)
            onSessionGone()
        }
    }
    val isActive = session.isActive
    Scaffold(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing), contentWindowInsets = WindowInsets(0, 0, 0, 0), containerColor = callBackdrop()) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .clickable {
                    if (session.isVideo) controlsVisible = !controlsVisible
                },
        ) {
            if (session.isVideo) {
                RemoteVideoSurface(session, videoSurfaces, callDurationSeconds)
            } else {
                if (iosLayout) {
                    val colors = com.example.chat.ui.theme.LocalAppColors.current
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(colors.background, colors.accent.copy(alpha = 0.22f), colors.surfaceSecondary, colors.background))))
                } else VoiceCallBackdrop(session, callDurationSeconds, bundleTokens)
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
                        .background(callBackdrop(), RoundedCornerShape(16.dp))
                        .pointerInput(screenW, screenH) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                localPipOffsetX = (localPipOffsetX + dragAmount.x / density)
                                    .coerceIn(0f, (screenW - 120f).coerceAtLeast(0f))
                                localPipOffsetY = (localPipOffsetY + dragAmount.y / density)
                                    .coerceIn(0f, (screenH - 160f).coerceAtLeast(0f))
                            }
                        },
                ) {
                    if (!session.isCameraOff && videoSurfaces != null) {
                        LocalVideoSurface(videoSurfaces, mirror = session.isFrontCamera)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.callVideoOff,
                                semanticLabel = null,
                                color = callSecondary(),
                            )
                        }
                    }
                    IconButton(
                        onClick = onSwitchCamera,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(48.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(32.dp).background(callSurface(), CircleShape),
                        ) {
                            Icon(
                                Icons.Rounded.Cameraswitch,
                                contentDescription = "Switch camera",
                                tint = callForeground(),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            if (setupError != null || session.state == CallSessionState.failed || session.state == CallSessionState.busy) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(start = 20.dp, end = 20.dp, top = 96.dp)
                        .background(Color(argbWithAlpha(theme.dangerColor, 0.92)), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Text(
                        if (session.state == CallSessionState.busy) "User is on another call (Busy)" else setupError ?: "The media connection failed.",
                        color = com.example.chat.ui.theme.LocalAppColors.current.onError,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Top panel (Dart AnimatedPositioned slide).
            val topOffset by animateDpAsState(if (controlsVisible) 0.dp else (-110).dp, animationSpec = if (reduceMotion) snap() else tween(220), label = "topPanel")
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = topOffset)
                    .then(if (iosLayout && !session.isVideo) Modifier else topPanelDecoration(template))
                    .then(topPanelPadding(template)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back,
                            semanticLabel = "Back",
                            color = callForeground(),
                        )
                    }
                    BoxWithConstraints(Modifier.weight(1f)) {
                    val headerMaxWidth = maxWidth
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            session.remoteDisplayName,
                            color = callForeground(),
                            // The header middle is ~99dp wide at a 220dp window (run
                            // 41879111168 XML), under the 116.dp compact threshold the
                            // controls already use below. The existing 13.sp secondary
                            // size keeps a long word on one line so maxLines = 2 wraps
                            // at the word boundary instead of breaking inside the word.
                            fontSize = if (iosLayout && !session.isVideo) 26.sp
                                else if (headerMaxWidth < 116.dp) 13.sp else 17.sp,
                            fontWeight = FontWeight.Bold,
                            // Two lines everywhere: narrow widths wrap instead of
                            // truncating to a stub; one line when the name fits.
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            statusLabel(session, callDurationSeconds),
                            color = callSecondary(),
                            fontSize = if (iosLayout && !session.isVideo) 16.sp else 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    }
                    if (onCallTools != null && !(iosLayout && !session.isVideo)) {
                        IconButton(onClick = onCallTools) { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.more, semanticLabel = "Call tools", color = callForeground()) }
                    }
                    if (session.isVideo) {
                        IconButton(
                            onClick = onEnterNativePip,
                            enabled = isActive, // Dart: only isActive gates; the disabled-in-settings case shows a message in the host
                        ) {
                            Icon(
                                Icons.Rounded.PictureInPicture,
                                contentDescription = "Picture-in-picture",
                                tint = callForeground(),
                            )
                        }
                    }
                    if (session.isVideo && effectProvider != null) {
                        IconButton(onClick = { effectProvider.showEffectSheet() }) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.autoAwesome,
                                semanticLabel = "Call filter",
                                color = callForeground(),
                            )
                        }
                    }
                    IconButton(onClick = { focusMode = !focusMode }) {
                        Icon(
                            if (focusMode) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            contentDescription = if (focusMode) "Exit focus mode" else "Focus mode",
                            tint = callForeground(),
                        )
                    }
                }
            }

            if (callToolsStatus != null || reactionText != null) {
                Column(Modifier.align(Alignment.Center).background(callSurface(), RoundedCornerShape(24.dp)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    callToolsStatus?.let { Text(it, color = callForeground()) }
                    reactionText?.let { Text(it, color = callForeground(), fontSize = 24.sp) }
                }
            }
            // Bottom control bar.
            val bottomOffset by animateDpAsState(if (controlsVisible || !session.isVideo) 0.dp else 150.dp, animationSpec = if (reduceMotion) snap() else tween(220), label = "bottomPanel")
            BoxWithConstraints(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * if (session.isVideo) 0.48f else 0.65f).dp)
                    .verticalScroll(rememberScrollState())
                    .offset(y = bottomOffset)
                    .then(if (iosLayout && !session.isVideo) Modifier else bottomPanelDecoration(template))
                    .then(bottomPanelPadding(template)),
            ) {
                var showMore by remember { mutableStateOf(false) }
                val actions = buildList {
                    add(CallControlAction(if (session.isMuted) "Unmute" else "Mute",
                        if (session.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        session.isMuted, hasLocalMedia || session.isDemo, onToggleMute))
                    if (session.isVideo) {
                        if (onReturnToAudio != null) add(CallControlAction("Return to audio", Icons.Rounded.Call, false, true, onReturnToAudio))
                        add(CallControlAction(if (session.isCameraOff) "Camera on" else "Camera off",
                            if (session.isCameraOff) Icons.Rounded.VideocamOff else Icons.Rounded.Videocam,
                            session.isCameraOff, hasLocalMedia || session.isDemo, onToggleCamera))
                        add(CallControlAction("Switch camera", Icons.Rounded.Cameraswitch, false,
                            hasLocalMedia && !session.isCameraOff && !session.isSharingScreen, onSwitchCamera))
                        add(CallControlAction(if (session.isSharingScreen) "Stop sharing" else "Share screen",
                            if (session.isSharingScreen) Icons.Rounded.StopScreenShare else Icons.Rounded.ScreenShare,
                            session.isSharingScreen, hasLocalMedia, onToggleScreenShare))
                    }
                    add(CallControlAction(if (session.audioRoute == AudioRouteType.speaker) "Earpiece" else "Speaker",
                        if (session.audioRoute == AudioRouteType.speaker) Icons.Rounded.VolumeUp else Icons.Rounded.Hearing,
                        session.audioRoute == AudioRouteType.speaker, hasLocalMedia || session.isDemo) {
                        onToggleAudioRoute(if (session.audioRoute == AudioRouteType.speaker) AudioRouteType.earpiece else AudioRouteType.speaker)
                    })
                }
                if (!session.isVideo && iosLayout) {
                    IosSupportedAudioControls(session, hasLocalMedia || session.isDemo,
                        onToggleMute, { onToggleAudioRoute(if (session.audioRoute == AudioRouteType.speaker) AudioRouteType.earpiece else AudioRouteType.speaker) },
                        onRequestVideo, onCallTools, onEndCall)
                } else if (!session.isVideo) {
                    AudioFourControls(session, hasLocalMedia || session.isDemo,
                        onToggleMute, { onToggleAudioRoute(if (session.audioRoute == AudioRouteType.speaker) AudioRouteType.earpiece else AudioRouteType.speaker) },
                        onRequestVideo, videoUpgradeStatus, onEndCall, bundleId)
                } else if (bundleId == com.example.chat.ui.calls.bundles.CallBundleId.GOOGLE_PHONE_LATEST) {
                    GoogleLatestProductionControls(
                        actions = actions, availableWidthDp = maxWidth.value.toInt(),
                        accent = callAccent, surface = controlBackground,
                        endColor = endCallColor, onEndCall = onEndCall,
                    )
                } else if (bundleId != null && bundleId != com.example.chat.ui.calls.bundles.CallBundleId.CHATY_DEFAULT) {
                    BundleProductionControls(
                        bundleId = bundleId, actions = actions,
                        availableWidthDp = maxWidth.value.toInt(), accent = callAccent,
                        surface = controlBackground, endColor = endCallColor, onEndCall = onEndCall,
                    )
                } else {
                val compact = maxWidth < 116.dp
                val shownCount = if (compact) 0 else visibleCallActionCount(maxWidth.value.toInt(), actions.size)
                val controls: @Composable () -> Unit = {
                    actions.take(shownCount).forEach { action ->
                        CallControlButton(action.label, action.active, callAccent, action.enabled, action.onClick, controlBackground) { tint ->
                            Icon(action.icon, null, tint = tint, modifier = Modifier.size(23.dp))
                        }
                    }
                    if (shownCount < actions.size) {
                        Box {
                            CallControlButton("More call controls", false, callAccent, true, { showMore = !showMore }, controlBackground) { tint ->
                                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.more, semanticLabel = null, color = tint)
                            }
                            com.example.chat.ui.kit.ChatyDropdownMenu(showMore, { showMore = false }) {
                                actions.drop(shownCount).forEach { action ->
                                    com.example.chat.ui.kit.ChatyDropdownMenuItem(
                                        text = { Text(action.label) }, enabled = action.enabled,
                                        leadingIcon = { Icon(action.icon, null) },
                                        onClick = { showMore = false; action.onClick() },
                                    )
                                }
                            }
                        }
                    }
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)
                        .clip(CircleShape).background(endCallColor)
                        .semantics {
                            contentDescription = "End call"
                            role = Role.Button
                            onClick(label = "End call") { onEndCall(); true }
                        }) {
                        Box(Modifier.matchParentSize().clip(CircleShape).clickable { onEndCall() })
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.callEnd, semanticLabel = null, color = com.example.chat.ui.theme.LocalAppColors.current.onError, modifier = Modifier.size(28.dp))
                    }
                }
                if (compact) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) { controls() }
                else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) { controls() }
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
        CallSessionState.busy -> "User is on another call"
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
    inactiveBackground: Color = callSurface(),
    icon: @Composable (tint: Color) -> Unit,
) {
    val tint = if (active) com.example.chat.ui.theme.LocalAppColors.current.onAccent else callForeground()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .background(
                if (active) activeColor.copy(alpha = 0.28f) else inactiveBackground,
                CircleShape,
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
            modifier = Modifier.fillMaxSize().background(callBackdrop()),
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
                    color = callSecondary(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(
                    strokeWidth = 2.2.dp,
                    color = callSecondary(),
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
private fun VoiceCallBackdrop(
    session: ChatyCallSession, callDurationSeconds: Int,
    bundleTokens: com.example.chat.ui.calls.bundles.CallBundleThemeTokens? = null,
) {
    val theme = LocalThemeConfig.current
    val bundle = bundleTokens?.bundleId
    val identity = voiceBundleIdentity(bundle)
    Box(
        contentAlignment = if (identity.upperIdentity) Alignment.TopCenter else Alignment.Center,
        modifier = Modifier.fillMaxSize().background(bundleTokens?.surfaceBackground ?: Color(theme.cardColor)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                .padding(top = if (identity.upperIdentity) 116.dp else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CallAvatar(
                initials = session.remoteAvatarInitials
                    ?: (if (session.remoteDisplayName.isNotEmpty()) {
                        session.remoteDisplayName.substring(0, 1).uppercase()
                    } else {
                        "U"
                    }),
                colorHex = session.remoteAvatarColorHex,
                size = identity.avatarDp.dp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                session.remoteDisplayName,
                color = bundleTokens?.primaryText ?: Color(theme.primaryTextColor),
                fontSize = identity.nameSp.sp,
                // Large font scales wrap the name to two lines; default metrics let the
                // lines collide at fontScale 1.5 (run 41870523872 pixel). 1.25em keeps
                // the gap proportional at any scale.
                lineHeight = (identity.nameSp * 1.25f).sp,
                fontWeight = if (identity.lightName) FontWeight.Light else FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                statusLabel(session, callDurationSeconds),
                color = bundleTokens?.secondaryText ?: Color(theme.secondaryTextColor),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── Template decorations (Dart _topPanelDecoration/_bottomPanelDecoration) ──

@Composable
private fun topPanelDecoration(template: CallScreenTemplate): Modifier =
    when (template) {
        CallScreenTemplate.card -> Modifier
            .padding(start = 16.dp, top = 8.dp, end = 16.dp)
            .background(callSurface(), RoundedCornerShape(20.dp))
        CallScreenTemplate.edge -> Modifier
        else -> Modifier.background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(callSurface(), Color.Transparent),
            ),
        )
    }

private fun topPanelPadding(template: CallScreenTemplate): Modifier =
    if (template == CallScreenTemplate.card) {
        Modifier.padding(8.dp)
    } else {
        Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp)
    }

@Composable
private fun bottomPanelDecoration(template: CallScreenTemplate): Modifier =
    when (template) {
        CallScreenTemplate.sheet -> Modifier.background(
            callSurface(),
            RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
        )
        CallScreenTemplate.card -> Modifier
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            .background(callSurface(), RoundedCornerShape(10.dp))
        CallScreenTemplate.edge -> Modifier
        CallScreenTemplate.chatyClassic -> Modifier.background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(Color.Transparent, callSurface()),
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

private data class CallControlAction(
    val label: String,
    val icon: ImageVector,
    val active: Boolean,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/** Google Latest production controls: labelled tonal pills grouped above a separate
 * end-call action. Uses the same capability-checked actions as the media screen. */
@Composable
private fun GoogleLatestProductionControls(
    actions: List<CallControlAction>, availableWidthDp: Int,
    accent: Color, surface: Color, endColor: Color, onEndCall: () -> Unit,
) {
    val columns = googleLatestCallControlColumns(availableWidthDp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        actions.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { action ->
                    Column(
                        Modifier.weight(1f).height(80.dp)
                            .clip(RoundedCornerShape(if (action.active) 20.dp else 40.dp))
                            .background(if (action.active) accent else surface)
                            .semantics { contentDescription = action.label }
                            .clickable(enabled = action.enabled, onClick = action.onClick)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val tint = if (action.active) com.example.chat.ui.theme.LocalAppColors.current.onAccent else callForeground().copy(alpha = if (action.enabled) 1f else 0.4f)
                        Icon(action.icon, null, tint = tint, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(action.label, color = tint, fontSize = 12.sp, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Box(
            Modifier.width(availableWidthDp.coerceAtMost(144).coerceAtLeast(1).dp).height(64.dp).clip(RoundedCornerShape(32.dp))
                .background(endColor)
                .semantics {
                    contentDescription = "End call"
                    role = Role.Button
                    onClick(label = "End call") { onEndCall(); true }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(32.dp)).clickable(onClick = onEndCall))
            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.callEnd, semanticLabel = null, color = com.example.chat.ui.theme.LocalAppColors.current.onError, modifier = Modifier.size(32.dp))
        }
    }
}

/** Per-bundle visual grammar on the production action list, never a second call engine.
 * Unsupported add-call/keypad/hold actions are not manufactured for visual parity. */
@Composable
private fun BundleProductionControls(
    bundleId: com.example.chat.ui.calls.bundles.CallBundleId,
    actions: List<CallControlAction>, availableWidthDp: Int,
    accent: Color, surface: Color, endColor: Color, onEndCall: () -> Unit,
) {
    val layout = bundleControlLayout(bundleId)
    val innerWidth = (availableWidthDp - if (layout.panel) 24 else 0).coerceAtLeast(1)
    val columns = ((innerWidth + 12) / 88).coerceIn(1, layout.columns)
    val sectionShape = RoundedCornerShape(layout.panelRadiusDp.dp)
    val panel = if (layout.panel) Modifier.background(surface, sectionShape).padding(12.dp) else Modifier
    Column(Modifier.fillMaxWidth().then(panel), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(layout.spacingDp.dp)) {
        if (layout.endFirst) BundleEndAction(endColor, onEndCall, layout.wideEnd, innerWidth)
        actions.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { action ->
                    Column(Modifier.weight(1f)
                        .semantics { contentDescription = action.label }
                        .clickable(enabled = action.enabled, onClick = action.onClick)
                        .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        val buttonShape = if (layout.circle) CircleShape else RoundedCornerShape(layout.controlRadiusDp.dp)
                        val reduceMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
                        val fill by androidx.compose.animation.animateColorAsState(
                            targetValue = if (action.active) accent else if (layout.panel) callForeground().copy(alpha = 0.08f) else surface,
                            animationSpec = androidx.compose.animation.core.tween(if (reduceMotion) 0 else 140), label = "call-control-state")
                        Box(Modifier.size(52.dp).clip(buttonShape)
                            .background(fill),
                            contentAlignment = Alignment.Center) {
                            Icon(action.icon, null,
                                tint = callForeground().copy(alpha = if (action.enabled) 1f else 0.4f),
                                modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(action.label, color = callForeground().copy(alpha = if (action.enabled) 1f else 0.4f),
                            fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (!layout.endFirst) BundleEndAction(endColor, onEndCall, layout.wideEnd, innerWidth)
    }
}

@Composable
private fun BundleEndAction(color: Color, onEnd: () -> Unit, wide: Boolean, availableWidthDp: Int) {
    Box(Modifier.width((if (wide) availableWidthDp.coerceAtMost(280) else 64).coerceAtMost(availableWidthDp).coerceAtLeast(1).dp)
        .height(64.dp).clip(RoundedCornerShape(32.dp)).background(color)
        .semantics {
            contentDescription = "End call"
            role = Role.Button
            // One node carries label + role + action; the child layer is pointer-only.
            onClick(label = "End call") { onEnd(); true }
        },
        contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(32.dp)).clickable(onClick = onEnd))
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.callEnd, semanticLabel = null, color = com.example.chat.ui.theme.LocalAppColors.current.onError, modifier = Modifier.size(28.dp))
            if (wide) { Spacer(Modifier.width(8.dp)); Text("End call", color = com.example.chat.ui.theme.LocalAppColors.current.onError, maxLines = 1) }
        }
    }
}

/** iOS composition with real actions only. Empty cells preserve reference alignment,
 * not fake controls. Video appears only when the host provides a working upgrade. */
@Composable
private fun IosSupportedAudioControls(session: ChatyCallSession, ready: Boolean,
    mute: () -> Unit, speaker: () -> Unit, video: (() -> Unit)?,
    more: (() -> Unit)?, end: () -> Unit) {
    val colors = com.example.chat.ui.theme.LocalAppColors.current
    val entries = listOf(
        CallControlAction("Speaker", Icons.Rounded.VolumeUp, session.audioRoute == AudioRouteType.speaker, ready, speaker),
        video?.let { CallControlAction("Video", Icons.Rounded.Videocam, false, true, it) },
        CallControlAction("Mute", if (session.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic, session.isMuted, ready, mute),
        more?.let { CallControlAction("More", Icons.Rounded.MoreHoriz, false, true, it) },
        CallControlAction("End", Icons.Rounded.CallEnd, false, true, end),
        null,
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 160.dp) 1 else if (maxWidth < 260.dp || LocalConfiguration.current.fontScale > 1.5f) 2 else 3
        val rows = if (columns == 3) entries.chunked(3) else entries.filterNotNull().chunked(columns)
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { action ->
                        if (action == null) Spacer(Modifier.weight(1f)) else {
                            Column(Modifier.weight(1f).semantics {
                                contentDescription = action.label
                                role = Role.Button
                                stateDescription = if (action.label == "Mute") {
                                    if (session.isMuted) "Muted" else "Microphone on"
                                } else if (action.label == "Speaker") {
                                    if (session.audioRoute == AudioRouteType.speaker) "Speaker on" else "Speaker off"
                                } else ""
                                // Emulator hierarchy (run 41851643303) showed this labelled
                                // summary node split from the IconButton click node, so no
                                // node carried label + click together and the control was not
                                // actionable by its label. Carry the action on the labelled
                                // node itself; IconButton keeps pointer visuals.
                                if (action.enabled) {
                                    onClick(label = action.label) { action.onClick(); true }
                                } else disabled()
                            }, horizontalAlignment = Alignment.CenterHorizontally) {
                                val isEnd = action.label == "End"
                                IconButton(onClick = action.onClick, enabled = action.enabled,
                                    modifier = Modifier.size(60.dp).clip(CircleShape).background(
                                        if (isEnd) colors.error else if (action.active) colors.accent else colors.foreground.copy(alpha = 0.10f))) {
                                    Icon(action.icon, null, tint = if (isEnd) colors.onError else if (action.active) colors.onAccent else
                                        colors.foreground.copy(alpha = if (action.enabled) 1f else 0.4f), modifier = Modifier.size(26.dp))
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(action.label, color = colors.foreground, fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Exactly four primary audio controls. Wrapping protects labels at narrow/large text. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AudioFourControls(session: ChatyCallSession, mediaReady: Boolean, mute: () -> Unit, speaker: () -> Unit,
    video: (() -> Unit)?, videoStatus: String, end: () -> Unit,
    bundle: com.example.chat.ui.calls.bundles.CallBundleId? = null) {
    val c = com.example.chat.ui.theme.LocalAppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val profile = callBundleVisualProfile(bundle)
    val panelShape = RoundedCornerShape(profile.panelRadiusDp.dp)
    val panelModifier = if (profile.panel) Modifier.background(c.surfaceSecondary, panelShape).padding(12.dp) else Modifier
    val entries = listOf(
        CallControlAction("Mute", if (session.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic, session.isMuted, mediaReady, mute),
        CallControlAction("Speaker", Icons.Rounded.VolumeUp, session.audioRoute == AudioRouteType.speaker, mediaReady, speaker),
        CallControlAction("Video", Icons.Rounded.Videocam, false, true) {
            if (video != null) video() else android.widget.Toast.makeText(context, videoStatus, android.widget.Toast.LENGTH_LONG).show()
        },
        CallControlAction("Hang up", Icons.Rounded.CallEnd, false, true, end),
    )
    BoxWithConstraints(Modifier.fillMaxWidth().then(panelModifier)) {
      val columns = responsiveAudioControlColumns(maxWidth.value.toInt(), profile.columns)
      val cellWidth = maxWidth / columns
      val diameter = minOf(profile.controlDiameterDp.dp, (cellWidth - 8.dp).coerceAtLeast(48.dp))
      androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(minOf(profile.spacingDp, 12).dp), maxItemsInEachRow = columns) {
        entries.forEach { a ->
            Box(Modifier.width(cellWidth).semantics {
                    contentDescription = if (a.label == "Video" && video == null) "Video - $videoStatus" else a.label
                    role = Role.Button
                    stateDescription = when (a.label) {
                        "Mute" -> if (session.isMuted) "Muted" else "Microphone on"
                        "Speaker" -> if (session.audioRoute == AudioRouteType.speaker) "Speaker on" else "Speaker off"
                        else -> ""
                    }
                    // One a11y node carries label + role + action (sibling Modifier.clickable
                    // splits the node - runs 41851643303/41861366491). Pointer input is a
                    // full-cell child layer: run 41870523872 showed an icon-only target lets
                    // a center-tap of this node fall into dead space at narrow/large text.
                    if (a.enabled) {
                        onClick(label = a.label) { a.onClick(); true }
                    } else disabled()
                }) {
                Box(Modifier.matchParentSize().clickable(enabled = a.enabled, onClick = a.onClick))
                Column(Modifier.align(Alignment.TopCenter).padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    val fill = if (a.label == "Hang up") c.error else if (a.active) c.accent else c.surface
                    val outline = if (profile.outlined && a.label != "Hang up") Modifier.border(2.dp, if (a.active) c.accent else c.border, CircleShape) else Modifier
                    Box(Modifier.size(diameter).clip(CircleShape).background(fill).then(outline), contentAlignment = Alignment.Center) {
                        Icon(a.icon, null, tint = if (a.label == "Hang up") c.onError else if (a.active) c.onAccent else if (a.enabled) c.foreground else c.foregroundSecondary, modifier = Modifier.size(24.dp))
                    }
                    Text(a.label, color = c.foreground, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (a.label == "Video" && video == null) Text("Not ready", color = c.foregroundSecondary, fontSize = 10.sp)
                }
            }
        }
    }
    }
}
