package com.example.chat.ui.calls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
    @Suppress("UNUSED_EXPRESSION") revision
    val session = service.currentSession ?: return
    val prefs = prefsStore.value

    if (session.state == CallSessionState.incoming) {
        IncomingCallScreen(
            session = session,
            template = CallScreenTemplate.fromName(prefs.incomingCallTemplate),
            onAccept = {
                presentation.showFullScreen()
                scope.launch { service.acceptCall() }
            },
            onDecline = { scope.launch { service.declineCall() } },
        )
        return
    }
    if (!session.isActive) return

    val surfaces = service.activeMedia as? CallVideoSurfaceProvider
    if (presentation.isFullScreen) {
        OngoingCallScreen(
            session = session,
            callDurationSeconds = service.callDurationSeconds,
            hasLocalMedia = service.hasLocalMedia,
            videoSurfaces = surfaces,
            setupError = null,
            template = CallScreenTemplate.fromName(prefs.ongoingCallTemplate),
            pictureInPictureEnabled = prefs.pictureInPictureEnabled,
            onBack = { presentation.minimizeToPipOrIsland() },
            onSessionGone = { presentation.minimizeToPipOrIsland() },
            onEndCall = { service.endCall() },
            onToggleMute = { service.toggleMute() },
            onToggleCamera = { service.toggleCamera() },
            onSwitchCamera = { scope.launch { service.switchCamera() } },
            onToggleScreenShare = { scope.launch { service.toggleScreenShare() } },
            onToggleAudioRoute = { route: AudioRouteType -> scope.launch { service.setAudioRoute(route) } },
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
        InAppCallPip(
            session = session,
            videoSurfaces = surfaces,
            durationSeconds = service.callDurationSeconds,
            onTap = { presentation.expandToFullScreen() },
            onCollapseToIsland = { presentation.collapseToIsland() },
            onEndCall = { service.endCall() },
        )
    } else if (presentation.isInAppIsland) {
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
            ChatyCallIsland(
                session = session,
                durationSeconds = service.callDurationSeconds,
                onTap = { presentation.expandFromIsland() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
