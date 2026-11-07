package com.example.chat.ui.calls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.chat.ServiceLocator
import com.example.chat.data.services.AudioRouteType
import com.example.chat.data.services.CallSessionState
import com.example.chat.data.services.CallVideoSurfaceProvider
import kotlinx.coroutines.launch

/**
 * Global call layer (Dart main.dart MaterialApp.builder: incoming-call screen,
 * full-screen OngoingCallScreen, in-app video PiP and call island rendered
 * above every route while authenticated). It starts the call lifecycle
 * coordinator and presentation controller, and routes every action to the real
 * CallSignalingService. No call is created here: ringing comes only from the
 * signaling service's remote-signal handling.
 *
 * Known gaps (not faked): native system PiP is wired but device-unverified, the effect
 * provider is unset, and call-record persistence is the sink gap noted in
 * ServiceLocator.callSignaling.
 */
@Composable
fun GlobalCallHost(isAuthenticated: Boolean) {
    if (!isAuthenticated) return
    val service = remember { ServiceLocator.callSignaling }
    val presentation = remember { ServiceLocator.callPresentationController }
    remember { ServiceLocator.callLifecycle }
    val prefsStore = remember { ServiceLocator.callPresentationPrefs }
    val scope = rememberCoroutineScope()
    val hostContext = androidx.compose.ui.platform.LocalContext.current
    fun callAction(callId: String?, failureMessage: String, action: suspend () -> Unit) {
        scope.launch {
            if (callId == null || service.currentSession?.callId != callId) return@launch
            runCallAction(onFailure = {
                android.widget.Toast.makeText(hostContext, failureMessage, android.widget.Toast.LENGTH_SHORT).show()
            }, action = action)
        }
    }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(service, presentation) {
        val l: () -> Unit = { revision++ }
        service.addStateListener(l)
        presentation.addListener(l)
        prefsStore.addListener(l)
        onDispose {
            service.removeStateListener(l)
            presentation.removeListener(l)
            prefsStore.removeListener(l)
        }
    }
    val hostView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(service, hostView) {
        val waiting: () -> Unit = {
            hostView.post { if (hostView.isAttachedToWindow) com.example.chat.ui.kit.ChatyHapticsPort.error(hostView) }
        }
        service.addCallWaitingListener(waiting)
        onDispose { service.removeCallWaitingListener(waiting) }
    }
    @Suppress("UNUSED_EXPRESSION") revision
    val notificationPreviewOwner = remember(service.currentSession?.callId) { IncomingPreviewOwner() }
    DisposableEffect(notificationPreviewOwner) { onDispose { notificationPreviewOwner.close() } }
    // "Answer" tapped on the incoming-call notification: accept as soon as the offer is here
    // (when the app was closed it arrives a moment after the socket reconnects).
    val pendingCallAction by com.example.chat.notifications.ChatyNotifications.pendingCallAction
        .collectAsState()
    androidx.compose.runtime.LaunchedEffect(pendingCallAction, revision) {
        val act = pendingCallAction ?: return@LaunchedEffect
        val cs = service.currentSession
        if (cs == null || cs.callId != act.callId) return@LaunchedEffect
        com.example.chat.notifications.ChatyNotifications.consumeCallAction()
        if (cs.state != CallSessionState.incoming) return@LaunchedEffect
        if (act.answer) {
            notificationPreviewOwner.close()
            presentation.showFullScreen()
            callAction(cs.callId, "Unable to accept the call. Check microphone and camera permissions.") { service.acceptCall() }
        }
    }
    // Dart main.dart:808-830: the DynamicIslandController is always registered, so the call island is a controller
    // payload (id call_<callId>) while the session is active in island presentation, removed otherwise.
    // The standalone ChatyCallIsland is Dart's `showIsland`, which is false whenever the controller exists.
    androidx.compose.runtime.SideEffect {
        val islandCtrl = ServiceLocator.dynamicIslandController
        val cs = service.currentSession
        if (cs != null && cs.isActive && presentation.isInAppIsland) {
            islandCtrl.postPayload(
                com.example.chat.ui.island.DynamicIslandPayload(
                    id = "call_${cs.callId}",
                    type = if (cs.state == CallSessionState.incoming) com.example.chat.ui.island.IslandPayloadType.incomingCall
                    else com.example.chat.ui.island.IslandPayloadType.ongoingCall,
                    title = cs.remoteDisplayName,
                    message = if (cs.isVideo) "Video Call" else "Voice Call",
                    timestamp = java.time.Instant.now(),
                    durationSeconds = service.callDurationSeconds,
                    isVideo = cs.isVideo,
                    isMuted = cs.isMuted,
                    callStatusLabel = compactCallStatus(cs.state, service.callDurationSeconds),
                    callShowsDuration = cs.state == CallSessionState.connected,
                    onToggleCallMute = { callAction(cs.callId, "Unable to change microphone state.") { service.toggleMute() } },
                    onToggleCallSpeaker = { callAction(cs.callId, "Unable to change audio route.") {
                        service.setAudioRoute(if (cs.audioRoute == AudioRouteType.speaker) AudioRouteType.earpiece else AudioRouteType.speaker)
                    } },
                    isSpeaker = cs.audioRoute == AudioRouteType.speaker,
                    onTap = { if (cs.isVideo) presentation.expandFromIsland() else presentation.showFullScreen() },
                    onAcceptCall = { callAction(cs.callId, "Unable to accept the call. Check microphone and camera permissions.") { service.acceptCall() } },
                    onDeclineCall = { callAction(cs.callId, "Unable to decline the call.") {
                        if (service.currentSession?.state == CallSessionState.incoming) service.declineCall() else service.endCall()
                    } },
                ),
                autoExpand = cs.state == CallSessionState.incoming,
            )
        } else {
            islandCtrl.removePayloadsWhere { it.id.startsWith("call_") }
        }
    }
    val session = service.currentSession ?: return
    val prefs = prefsStore.value
    val bundlePresentation = remember(prefs.callExperienceBundleId, session.state) {
        com.example.chat.ui.calls.bundles.resolveCallBundlePresentation(prefs.callExperienceBundleId, session.state)
    }
    val activeBundle = bundlePresentation.bundleId

    val incomingGate = remember(session.callId) { IncomingCallActionGate() }
    var incomingActionPending by remember(session.callId) { mutableStateOf(false) }
    var incomingBanner by remember(session.callId) { mutableStateOf(false) }
    fun incomingAction(action: suspend () -> Unit) {
        if (service.currentSession?.state != CallSessionState.incoming || !incomingGate.tryEnter()) return
        incomingActionPending = true
        scope.launch {
            try {
                if (service.currentSession?.callId != session.callId) return@launch
                runCallAction(onFailure = {
                    android.widget.Toast.makeText(hostContext, "Unable to update the call. Check microphone and camera permissions.", android.widget.Toast.LENGTH_SHORT).show()
                }, action = action)
            } finally { incomingGate.complete(); incomingActionPending = false }
        }
    }
    val incomingPreviewOwner = notificationPreviewOwner
    if (bundlePresentation.incoming) {
        com.example.chat.ui.calls.bundles.BundleIncomingCallView(
            bundleId = activeBundle,
            session = session,
            actionPending = incomingActionPending,
            banner = incomingBanner,
            localPreviewOwner = incomingPreviewOwner,
            onExpand = { incomingBanner = false },
            onMinimize = { incomingPreviewOwner.close(); incomingBanner = true },
            onAccept = { incomingAction {
                incomingPreviewOwner.close()
                presentation.showFullScreen()
                service.acceptCall()
            } },
            onDecline = { incomingPreviewOwner.close(); incomingAction { service.declineCall() } },
        )
        return
    }
    val terminalNotice = session.state == CallSessionState.busy || session.state == CallSessionState.failed
    var dismissedNotice by remember(session.callId, session.state) { mutableStateOf(false) }
    if (!session.isActive && (!terminalNotice || dismissedNotice)) return

    val surfaces = service.activeMedia as? CallVideoSurfaceProvider
    val upgradePhase = service.videoUpgradePhase
    if (upgradePhase == com.example.chat.data.services.VideoUpgradeState.Phase.INCOMING) {
        androidx.compose.material3.AlertDialog(onDismissRequest = { service.declineVideoUpgrade() },
            title = { androidx.compose.material3.Text("Switch to video?") },
            text = { androidx.compose.material3.Text("${session.remoteDisplayName} wants to use video. Your camera will stay off until both sides agree and setup finishes. Audio continues if you decline.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { service.acceptVideoUpgrade() }) { androidx.compose.material3.Text("Accept video") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { service.declineVideoUpgrade() }) { androidx.compose.material3.Text("Keep audio") } })
    } else if (upgradePhase == com.example.chat.data.services.VideoUpgradeState.Phase.OUTGOING || upgradePhase == com.example.chat.data.services.VideoUpgradeState.Phase.NEGOTIATING) {
        androidx.compose.material3.AlertDialog(onDismissRequest = { service.cancelVideoUpgrade() },
            title = { androidx.compose.material3.Text(if (upgradePhase == com.example.chat.data.services.VideoUpgradeState.Phase.OUTGOING) "Waiting for video consent" else "Setting up video") },
            text = { androidx.compose.material3.Text("Audio remains connected. Video starts only when both sides agree and setup succeeds.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { service.cancelVideoUpgrade() }) { androidx.compose.material3.Text("Cancel video") } })
    }
    var toolsOpen by remember(session.callId) { mutableStateOf(false) }
    if (toolsOpen && !terminalNotice) {
        CallToolsDialog(session.isLocallyHeld, service.canHoldLocally, service.canNotifyHold,
            onHold = { service.toggleLocalHold() },
            onReaction = { emoji ->
                if (service.sendLocalCallReaction(emoji)) toolsOpen = false
                else android.widget.Toast.makeText(hostContext, "Wait a moment before another reaction, or reconnect the call.", android.widget.Toast.LENGTH_SHORT).show()
            }, onDismiss = { toolsOpen = false })
    }
    if (presentation.isFullScreen || terminalNotice) {
        OngoingCallScreen(
            session = session,
            callDurationSeconds = service.callDurationSeconds,
            hasLocalMedia = service.hasLocalMedia,
            videoSurfaces = surfaces,
            setupError = null,
            template = bundlePresentation.template,
            bundleId = activeBundle,
            pictureInPictureEnabled = prefs.pictureInPictureEnabled,
            onBack = { presentation.minimizeToPipOrIsland() },
            onSessionGone = {
                if (terminalNotice) dismissedNotice = true
                else presentation.minimizeToPipOrIsland()
            },
            onCallTools = if (session.state == CallSessionState.connected) ({ toolsOpen = true }) else null,
            callToolsStatus = when {
                session.isLocallyHeld -> if (service.canNotifyHold) "On hold" else "On hold locally - peer not notified"
                session.isRemotelyHeld -> "Other person put the call on hold"
                else -> null
            },
            reactionText = service.callReaction?.let { r -> "${r.emoji} " + if (r.fromPeer) "From ${session.remoteDisplayName}" else if (r.deliveredToPeer) "Sent" else "Only on this device" },
            onRequestVideo = if (service.canRequestVideoUpgrade) ({ service.requestVideoUpgrade(); Unit }) else null,
            videoUpgradeStatus = service.videoUpgradeNotice ?: "Video upgrade pending verified server and peer support",
            onReturnToAudio = if (session.isVideo && service.videoUpgradePhase == com.example.chat.data.services.VideoUpgradeState.Phase.VIDEO) ({ service.returnToAudio() }) else null,
            onEndCall = { service.endCall() },
            onToggleMute = { service.toggleMute() },
            onToggleCamera = { service.toggleCamera() },
            onSwitchCamera = { callAction(session.callId, "Unable to switch camera.") { service.switchCamera() } },
            onToggleScreenShare = { callAction(session.callId, "Unable to change screen sharing.") { service.toggleScreenShare() } },
            onToggleAudioRoute = { route: AudioRouteType -> callAction(session.callId, "Choose Bluetooth or a headset through the system audio controls.") { service.setAudioRoute(route) } },
            onEnterNativePip = {
                // Dart _enterNativePip: settings gate, then system PiP, else a message.
                if (!prefs.pictureInPictureEnabled) {
                    android.widget.Toast.makeText(hostContext, "Picture-in-picture is disabled in Call settings.", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    // Whole-Activity PiP is not safe until the captured-account/call
                    // privacy surface is installed and its lifecycle proof passes.
                    // Keep the existing in-app call view rather than exposing routes.
                    presentation.minimizeToPipOrIsland()
                }
            },
        )
        return
    }
    if (presentation.isInAppVideoPip) {
        // GlobalCallHost is no longer inside AppRoot's consumed-inset Box, so the
        // draggable PiP (which clamps to its parent's measured bounds) gets its own
        // safe-drawing bounds here. Full-screen/incoming screens are Scaffolds that
        // paint full-bleed and apply system-bar padding to their own content.
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        InAppCallPip(
            bundleId = activeBundle,
            session = session,
            videoSurfaces = surfaces,
            durationSeconds = service.callDurationSeconds,
            onTap = { presentation.expandToFullScreen() },
            onCollapseToIsland = { presentation.collapseToIsland() },
            onEndCall = { service.endCall() },
        )
        }
    }
}
