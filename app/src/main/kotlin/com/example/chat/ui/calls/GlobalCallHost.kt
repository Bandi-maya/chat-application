package com.example.chat.ui.calls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

    if (session.state == CallSessionState.incoming) {
        IncomingCallScreen(
            session = session,
            template = CallScreenTemplate.fromName(prefs.incomingCallTemplate),
            onAccept = {
                presentation.showFullScreen()
                callAction(session.callId, "Unable to accept the call. Check microphone and camera permissions.") { service.acceptCall() }
            },
            onDecline = { callAction(session.callId, "Unable to decline the call.") { service.declineCall() } },
        )
        return
    }
    val terminalNotice = session.state == CallSessionState.busy || session.state == CallSessionState.failed
    var dismissedNotice by remember(session.callId, session.state) { mutableStateOf(false) }
    if (!session.isActive && (!terminalNotice || dismissedNotice)) return

    val surfaces = service.activeMedia as? CallVideoSurfaceProvider
    if (presentation.isFullScreen || terminalNotice) {
        OngoingCallScreen(
            session = session,
            callDurationSeconds = service.callDurationSeconds,
            hasLocalMedia = service.hasLocalMedia,
            videoSurfaces = surfaces,
            setupError = null,
            template = CallScreenTemplate.fromName(prefs.ongoingCallTemplate),
            pictureInPictureEnabled = prefs.pictureInPictureEnabled,
            onBack = { presentation.minimizeToPipOrIsland() },
            onSessionGone = {
                if (terminalNotice) dismissedNotice = true
                else presentation.minimizeToPipOrIsland()
            },
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
                    val activity = generateSequence(hostContext) { (it as? android.content.ContextWrapper)?.baseContext }
                        .firstOrNull { it is android.app.Activity } as? android.app.Activity
                    val entered = activity != null && ServiceLocator.nativeCallPip.enter(activity)
                    if (!entered) android.widget.Toast.makeText(hostContext, "Picture-in-picture is unavailable.", android.widget.Toast.LENGTH_SHORT).show()
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
